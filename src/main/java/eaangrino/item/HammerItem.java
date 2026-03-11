package eaangrino.item;

import eaangrino.config.MineHammersConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class HammerItem extends DiggerItem {
	private static final ThreadLocal<Boolean> AREA_MINING_ACTIVE = ThreadLocal.withInitial(() -> false);
	private static final String MAGMA_SMELTED_ENTITY_TAG = "mine_hammers_magma_smelted";
	private static final String EMERALD_HAMMER_ITEM_PATH = "emerald_hammer";
	private static final int EMERALD_ORE_SCAN_RADIUS = 6;
	private static final int EMERALD_ORE_HIGHLIGHT_PARTICLES = 5;
	private static final List<TagKey<Block>> ORE_SENSE_TARGET_TAGS = List.of(
			BlockTags.COAL_ORES,
			BlockTags.IRON_ORES,
			BlockTags.GOLD_ORES,
			BlockTags.REDSTONE_ORES,
			BlockTags.LAPIS_ORES,
			BlockTags.DIAMOND_ORES,
			BlockTags.EMERALD_ORES
	);
	private static final Map<Item, Item> MAGMA_COOK_RESULTS = createMagmaCookResults();
	private static final Map<Block, Item> MAGMA_BLOCK_COOK_RESULTS = createMagmaBlockCookResults();
	private final boolean smelts;

	public HammerItem(Tier material, float attackDamage, float attackSpeed, float extraKnockback, boolean smelts, Item.Properties properties) {
		super(
				material,
				BlockTags.MINEABLE_WITH_PICKAXE,
				properties.attributes(createHammerAttributes(material, attackDamage, attackSpeed, extraKnockback))
		);
		this.smelts = smelts;
	}

	public boolean smeltsBlocks() {
		return smelts;
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity livingEntity) {
		boolean mined = super.mineBlock(stack, level, state, pos, livingEntity);

		if (level.isClientSide() || !(livingEntity instanceof ServerPlayer player) || AREA_MINING_ACTIVE.get()) {
			return mined;
		}

		boolean shouldSmelt = smeltsBlocks();
		Map<Item, Integer> inventoryBefore = shouldSmelt ? snapshotInventoryCounts(player.getInventory()) : Map.of();
		Map<Item, Integer> convertedOutputs = shouldSmelt ? smeltDropsForBrokenBlock(level, pos, state) : Map.of();
		triggerEmeraldOreSense(level, state, pos);

		MineHammersConfig.ConfigData config = MineHammersConfig.get();
		if (!config.areaMiningEnabled || config.radius <= 0) {
			if (shouldSmelt) {
				smeltNewlyCollectedInventoryItems(player, level, inventoryBefore, convertedOutputs);
			}
			return mined;
		}

		if (config.disableWhenSneaking && player.isShiftKeyDown()) {
			if (shouldSmelt) {
				smeltNewlyCollectedInventoryItems(player, level, inventoryBefore, convertedOutputs);
			}
			return mined;
		}

		Direction.Axis axis = getMiningPlaneAxis(player);
		AREA_MINING_ACTIVE.set(true);
		try {
			breakArea(stack, player, level, pos, axis, config, shouldSmelt, convertedOutputs);
		} finally {
			AREA_MINING_ACTIVE.set(false);
		}

		if (shouldSmelt) {
			smeltNewlyCollectedInventoryItems(player, level, inventoryBefore, convertedOutputs);
		}

		return mined;
	}

	private void triggerEmeraldOreSense(Level level, BlockState brokenState, BlockPos origin) {
		if (!(level instanceof ServerLevel serverLevel) || !isEmeraldHammer()) {
			return;
		}

		if (!isOreSenseTriggerBlock(brokenState)) {
			return;
		}

		serverLevel.sendParticles(
				ParticleTypes.HAPPY_VILLAGER,
				origin.getX() + 0.5D,
				origin.getY() + 0.5D,
				origin.getZ() + 0.5D,
				8,
				0.35D,
				0.35D,
				0.35D,
				0.01D
		);

		BlockPos.betweenClosedStream(
				origin.offset(-EMERALD_ORE_SCAN_RADIUS, -EMERALD_ORE_SCAN_RADIUS, -EMERALD_ORE_SCAN_RADIUS),
				origin.offset(EMERALD_ORE_SCAN_RADIUS, EMERALD_ORE_SCAN_RADIUS, EMERALD_ORE_SCAN_RADIUS)
		).forEach(scanPos -> {
			BlockState scanState = serverLevel.getBlockState(scanPos);
			if (isOreSenseTarget(scanState)) {
				spawnOreSenseParticles(serverLevel, scanPos.immutable());
			}
		});
	}

	private boolean isEmeraldHammer() {
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(this);
		return itemId != null && EMERALD_HAMMER_ITEM_PATH.equals(itemId.getPath());
	}

	private static boolean isOreSenseTarget(BlockState state) {
		for (TagKey<Block> tag : ORE_SENSE_TARGET_TAGS) {
			if (state.is(tag)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isOreSenseTriggerBlock(BlockState state) {
		return state.is(Blocks.STONE)
				|| state.is(Blocks.DEEPSLATE)
				|| state.is(BlockTags.BASE_STONE_OVERWORLD)
				|| state.is(BlockTags.STONE_ORE_REPLACEABLES)
				|| state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
	}

	private static void spawnOreSenseParticles(ServerLevel level, BlockPos orePos) {
		boolean spawnedNearAir = false;
		for (Direction direction : Direction.values()) {
			BlockPos adjacentPos = orePos.relative(direction);
			if (!level.getBlockState(adjacentPos).isAir()) {
				continue;
			}

			spawnedNearAir = true;
			level.sendParticles(
					ParticleTypes.HAPPY_VILLAGER,
					adjacentPos.getX() + 0.5D,
					adjacentPos.getY() + 0.5D,
					adjacentPos.getZ() + 0.5D,
					2,
					0.15D,
					0.15D,
					0.15D,
					0.01D
			);
		}

		if (!spawnedNearAir) {
			level.sendParticles(
					ParticleTypes.HAPPY_VILLAGER,
					orePos.getX() + 0.5D,
					orePos.getY() + 0.5D,
					orePos.getZ() + 0.5D,
					EMERALD_ORE_HIGHLIGHT_PARTICLES,
					0.2D,
					0.2D,
					0.2D,
					0.01D
			);
		}
	}

	private static Direction.Axis getMiningPlaneAxis(ServerPlayer player) {
		// Looking mostly up/down mines a horizontal 3x3, otherwise mines a vertical 3x3 in front of the player.
		if (Math.abs(player.getXRot()) > 45.0F) {
			return Direction.Axis.Y;
		}

		return player.getDirection().getAxis();
	}

	private static void breakArea(
			ItemStack stack,
			ServerPlayer player,
			Level level,
			BlockPos origin,
			Direction.Axis axis,
			MineHammersConfig.ConfigData config,
			boolean shouldSmelt,
			Map<Item, Integer> convertedOutputs
	) {
		int radius = config.radius;
		for (int first = -radius; first <= radius; first++) {
			for (int second = -radius; second <= radius; second++) {
				if (first == 0 && second == 0) {
					continue;
				}

				BlockPos targetPos = switch (axis) {
					case X -> origin.offset(0, first, second);
					case Y -> origin.offset(first, 0, second);
					case Z -> origin.offset(first, second, 0);
				};

				tryBreakExtraBlock(stack, player, level, targetPos, config, shouldSmelt, convertedOutputs);
			}
		}
	}

	private static void tryBreakExtraBlock(
			ItemStack stack,
			ServerPlayer player,
			Level level,
			BlockPos targetPos,
			MineHammersConfig.ConfigData config,
			boolean shouldSmelt,
			Map<Item, Integer> convertedOutputs
	) {
		if (!player.canInteractWithBlock(targetPos, 1.0D) || !player.mayUseItemAt(targetPos, Direction.UP, stack)) {
			return;
		}

		BlockState targetState = level.getBlockState(targetPos);
		if (targetState.isAir() || targetState.getDestroySpeed(level, targetPos) < 0.0F) {
			return;
		}

		if (config.onlyPickaxeMineable && !targetState.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return;
		}

		if (config.requireCorrectToolForDrops && !player.hasCorrectToolForDrops(targetState)) {
			return;
		}

		if (player.gameMode.destroyBlock(targetPos)) {
			if (shouldSmelt) {
				mergeCounts(convertedOutputs, smeltDropsForBrokenBlock(level, targetPos, targetState));
			}

			if (!player.getAbilities().instabuild) {
				player.causeFoodExhaustion(config.hungerExhaustionPerExtraBlock);
			}
		}
	}

	private static Map<Item, Integer> smeltDropsForBrokenBlock(Level level, BlockPos blockPos, BlockState brokenState) {
		return smeltDropsInBox(level, new AABB(blockPos).inflate(1.5D), brokenState);
	}

	private static Map<Item, Integer> smeltDropsInBox(Level level, AABB searchArea, BlockState brokenState) {
		Map<Item, Integer> convertedOutputs = smeltDropsInBox(level, searchArea, true, brokenState);
		return convertedOutputs == null ? Map.of() : convertedOutputs;
	}

	private static Map<Item, Integer> smeltDropsInBox(Level level, AABB searchArea, boolean scheduleFollowUpPass, BlockState brokenState) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return null;
		}

		Map<Item, Integer> convertedOutputs = new HashMap<>();
		for (ItemEntity itemEntity : serverLevel.getEntitiesOfClass(ItemEntity.class, searchArea, entity -> entity.isAlive() && !entity.getItem().isEmpty() && !entity.getTags().contains(MAGMA_SMELTED_ENTITY_TAG))) {
			ItemStack smelted = smeltStackForBrokenBlock(itemEntity.getItem(), brokenState);
			if (!smelted.isEmpty()) {
				convertedOutputs.merge(smelted.getItem(), smelted.getCount(), Integer::sum);
				itemEntity.setItem(smelted);
				itemEntity.addTag(MAGMA_SMELTED_ENTITY_TAG);
			}
		}

		// Some drops can spawn just after block break processing; a second pass next tick catches late entities.
		if (scheduleFollowUpPass) {
			serverLevel.getServer().execute(() -> smeltDropsInBox(serverLevel, searchArea, false, brokenState));
		}

		return convertedOutputs;
	}

	private static ItemStack smeltStackForBrokenBlock(ItemStack input, BlockState brokenState) {
		if (brokenState != null) {
			Item blockBasedOutput = getBlockBasedSmeltResult(brokenState.getBlock(), input.getItem());
			if (blockBasedOutput != null) {
				return new ItemStack(blockBasedOutput, input.getCount());
			}
		}

		return smeltStack(input);
	}

	private static Item getBlockBasedSmeltResult(Block brokenBlock, Item droppedItem) {
		Item output = MAGMA_BLOCK_COOK_RESULTS.get(brokenBlock);
		if (output == null) {
			return null;
		}

		if (brokenBlock == Blocks.STONE) {
			return droppedItem == Items.COBBLESTONE || droppedItem == Items.STONE ? output : null;
		}

		if (brokenBlock == Blocks.CLAY) {
			return droppedItem == Items.CLAY ? output : null;
		}

		return droppedItem == brokenBlock.asItem() ? output : null;
	}

	private static void mergeCounts(Map<Item, Integer> destination, Map<Item, Integer> source) {
		for (Map.Entry<Item, Integer> entry : source.entrySet()) {
			destination.merge(entry.getKey(), entry.getValue(), Integer::sum);
		}
	}

	private static ItemStack smeltStack(ItemStack input) {
		Item output = MAGMA_COOK_RESULTS.get(input.getItem());
		if (output == null) {
			return ItemStack.EMPTY;
		}

		return new ItemStack(output, input.getCount());
	}

	private static Map<Item, Integer> snapshotInventoryCounts(Inventory inventory) {
		Map<Item, Integer> counts = new HashMap<>();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return counts;
	}

	private static void smeltNewlyCollectedInventoryItems(ServerPlayer player, Level level, Map<Item, Integer> beforeCounts, Map<Item, Integer> convertedOutputCounts) {
		if (!(level instanceof ServerLevel) || beforeCounts.isEmpty()) {
			return;
		}

		Inventory inventory = player.getInventory();
		Map<Item, Integer> afterCounts = snapshotInventoryCounts(inventory);
		for (Map.Entry<Item, Integer> entry : afterCounts.entrySet()) {
			int gainedCount = entry.getValue() - beforeCounts.getOrDefault(entry.getKey(), 0);
			if (gainedCount <= 0) {
				continue;
			}

			int alreadyConvertedCount = convertedOutputCounts.getOrDefault(entry.getKey(), 0);
			int smeltableCount = Math.max(0, gainedCount - alreadyConvertedCount);
			if (smeltableCount <= 0) {
				continue;
			}

			int removedCount = removeItemsFromInventory(inventory, entry.getKey(), smeltableCount);
			if (removedCount <= 0) {
				continue;
			}

			ItemStack smelted = smeltStack(new ItemStack(entry.getKey(), removedCount));
			if (smelted.isEmpty()) {
				addOrDrop(player, new ItemStack(entry.getKey(), removedCount));
				continue;
			}

			addOrDrop(player, smelted);
		}
	}

	private static int removeItemsFromInventory(Inventory inventory, Item item, int countToRemove) {
		int removed = 0;
		for (int slot = 0; slot < inventory.getContainerSize() && removed < countToRemove; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || stack.getItem() != item) {
				continue;
			}

			int amount = Math.min(countToRemove - removed, stack.getCount());
			stack.shrink(amount);
			removed += amount;

			if (stack.isEmpty()) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
		return removed;
	}

	private static void addOrDrop(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}

		Inventory inventory = player.getInventory();
		if (!inventory.add(stack) && !stack.isEmpty()) {
			player.drop(stack, false);
			return;
		}

		if (!stack.isEmpty()) {
			player.drop(stack, false);
		}
	}

	private static Map<Item, Item> createMagmaCookResults() {
		Map<Item, Item> map = new HashMap<>();
		map.put(Items.COBBLESTONE, Items.STONE);
		map.put(Items.STONE, Items.SMOOTH_STONE);
		map.put(Items.COBBLED_DEEPSLATE, Items.DEEPSLATE);
		map.put(Items.SANDSTONE, Items.SMOOTH_SANDSTONE);
		map.put(Items.RED_SANDSTONE, Items.SMOOTH_RED_SANDSTONE);
		map.put(Items.QUARTZ_BLOCK, Items.SMOOTH_QUARTZ);
		map.put(Items.STONE_BRICKS, Items.CRACKED_STONE_BRICKS);
		map.put(Items.DEEPSLATE_BRICKS, Items.CRACKED_DEEPSLATE_BRICKS);
		map.put(Items.DEEPSLATE_TILES, Items.CRACKED_DEEPSLATE_TILES);
		map.put(Items.NETHER_BRICKS, Items.CRACKED_NETHER_BRICKS);
		map.put(Items.BASALT, Items.SMOOTH_BASALT);
		map.put(Items.CLAY, Items.TERRACOTTA);
		map.put(Items.NETHERRACK, Items.NETHER_BRICK);
		map.put(Items.CLAY_BALL, Items.BRICK);
		map.put(Items.WET_SPONGE, Items.SPONGE);

		map.put(Items.WHITE_TERRACOTTA, Items.WHITE_GLAZED_TERRACOTTA);
		map.put(Items.ORANGE_TERRACOTTA, Items.ORANGE_GLAZED_TERRACOTTA);
		map.put(Items.MAGENTA_TERRACOTTA, Items.MAGENTA_GLAZED_TERRACOTTA);
		map.put(Items.LIGHT_BLUE_TERRACOTTA, Items.LIGHT_BLUE_GLAZED_TERRACOTTA);
		map.put(Items.YELLOW_TERRACOTTA, Items.YELLOW_GLAZED_TERRACOTTA);
		map.put(Items.LIME_TERRACOTTA, Items.LIME_GLAZED_TERRACOTTA);
		map.put(Items.PINK_TERRACOTTA, Items.PINK_GLAZED_TERRACOTTA);
		map.put(Items.GRAY_TERRACOTTA, Items.GRAY_GLAZED_TERRACOTTA);
		map.put(Items.LIGHT_GRAY_TERRACOTTA, Items.LIGHT_GRAY_GLAZED_TERRACOTTA);
		map.put(Items.CYAN_TERRACOTTA, Items.CYAN_GLAZED_TERRACOTTA);
		map.put(Items.PURPLE_TERRACOTTA, Items.PURPLE_GLAZED_TERRACOTTA);
		map.put(Items.BLUE_TERRACOTTA, Items.BLUE_GLAZED_TERRACOTTA);
		map.put(Items.BROWN_TERRACOTTA, Items.BROWN_GLAZED_TERRACOTTA);
		map.put(Items.GREEN_TERRACOTTA, Items.GREEN_GLAZED_TERRACOTTA);
		map.put(Items.RED_TERRACOTTA, Items.RED_GLAZED_TERRACOTTA);
		map.put(Items.BLACK_TERRACOTTA, Items.BLACK_GLAZED_TERRACOTTA);
		return map;
	}

	private static Map<Block, Item> createMagmaBlockCookResults() {
		Map<Block, Item> map = new HashMap<>();
		map.put(Blocks.COBBLESTONE, Items.STONE);
		map.put(Blocks.STONE, Items.SMOOTH_STONE);
		map.put(Blocks.COBBLED_DEEPSLATE, Items.DEEPSLATE);
		map.put(Blocks.SANDSTONE, Items.SMOOTH_SANDSTONE);
		map.put(Blocks.RED_SANDSTONE, Items.SMOOTH_RED_SANDSTONE);
		map.put(Blocks.QUARTZ_BLOCK, Items.SMOOTH_QUARTZ);
		map.put(Blocks.STONE_BRICKS, Items.CRACKED_STONE_BRICKS);
		map.put(Blocks.DEEPSLATE_BRICKS, Items.CRACKED_DEEPSLATE_BRICKS);
		map.put(Blocks.DEEPSLATE_TILES, Items.CRACKED_DEEPSLATE_TILES);
		map.put(Blocks.NETHER_BRICKS, Items.CRACKED_NETHER_BRICKS);
		map.put(Blocks.BASALT, Items.SMOOTH_BASALT);
		map.put(Blocks.CLAY, Items.TERRACOTTA);
		map.put(Blocks.NETHERRACK, Items.NETHER_BRICK);
		map.put(Blocks.WET_SPONGE, Items.SPONGE);

		map.put(Blocks.WHITE_TERRACOTTA, Items.WHITE_GLAZED_TERRACOTTA);
		map.put(Blocks.ORANGE_TERRACOTTA, Items.ORANGE_GLAZED_TERRACOTTA);
		map.put(Blocks.MAGENTA_TERRACOTTA, Items.MAGENTA_GLAZED_TERRACOTTA);
		map.put(Blocks.LIGHT_BLUE_TERRACOTTA, Items.LIGHT_BLUE_GLAZED_TERRACOTTA);
		map.put(Blocks.YELLOW_TERRACOTTA, Items.YELLOW_GLAZED_TERRACOTTA);
		map.put(Blocks.LIME_TERRACOTTA, Items.LIME_GLAZED_TERRACOTTA);
		map.put(Blocks.PINK_TERRACOTTA, Items.PINK_GLAZED_TERRACOTTA);
		map.put(Blocks.GRAY_TERRACOTTA, Items.GRAY_GLAZED_TERRACOTTA);
		map.put(Blocks.LIGHT_GRAY_TERRACOTTA, Items.LIGHT_GRAY_GLAZED_TERRACOTTA);
		map.put(Blocks.CYAN_TERRACOTTA, Items.CYAN_GLAZED_TERRACOTTA);
		map.put(Blocks.PURPLE_TERRACOTTA, Items.PURPLE_GLAZED_TERRACOTTA);
		map.put(Blocks.BLUE_TERRACOTTA, Items.BLUE_GLAZED_TERRACOTTA);
		map.put(Blocks.BROWN_TERRACOTTA, Items.BROWN_GLAZED_TERRACOTTA);
		map.put(Blocks.GREEN_TERRACOTTA, Items.GREEN_GLAZED_TERRACOTTA);
		map.put(Blocks.RED_TERRACOTTA, Items.RED_GLAZED_TERRACOTTA);
		map.put(Blocks.BLACK_TERRACOTTA, Items.BLACK_GLAZED_TERRACOTTA);
		return map;
	}

	private static ItemAttributeModifiers createHammerAttributes(Tier material, float attackDamage, float attackSpeed, float extraKnockback) {
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		builder.add(
				Attributes.ATTACK_DAMAGE,
				new AttributeModifier(
						ResourceLocation.withDefaultNamespace("base_attack_damage"),
						attackDamage + material.getAttackDamageBonus(),
						AttributeModifier.Operation.ADD_VALUE
				),
				EquipmentSlotGroup.MAINHAND
		);
		builder.add(
				Attributes.ATTACK_SPEED,
				new AttributeModifier(
						ResourceLocation.withDefaultNamespace("base_attack_speed"),
						attackSpeed,
						AttributeModifier.Operation.ADD_VALUE
				),
				EquipmentSlotGroup.MAINHAND
		);

		if (extraKnockback > 0.0F) {
			builder.add(
					Attributes.ATTACK_KNOCKBACK,
					new AttributeModifier(
							ResourceLocation.fromNamespaceAndPath("mine-hammers", "hammer_bonus_knockback"),
							extraKnockback,
							AttributeModifier.Operation.ADD_VALUE
					),
					EquipmentSlotGroup.MAINHAND
			);
		}

		return builder.build();
	}
}
