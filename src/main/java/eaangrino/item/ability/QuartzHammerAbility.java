package eaangrino.item.ability;

import eaangrino.mixin.FoodDataAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class QuartzHammerAbility implements HammerAbility {
	private static final String DURABILITY_CREDIT_TAG = "mine_hammers_quartz_durability_credit";
	private static final int RESONANCE_RADIUS = 6;
	private static final int REDSTONE_SENSITIVITY_RADIUS = 4;
	private static final int LIGHT_REFLECTION_RADIUS = 4;
	private static final float CRYSTAL_RESONANCE_CHANCE = 0.18F;
	private static final float QUARTZ_DETECTION_CHANCE = 0.22F;
	private static final float NETHER_AFFINITY_CREDIT = 0.10F;
	private static final float QUARTZ_PRECISION_CREDIT = 0.10F;
	private static final float CRYSTAL_STABILITY_CREDIT = 0.20F;
	private static final float CRYSTAL_ALIGNMENT_EXHAUSTION_MULTIPLIER = 0.90F;
	private static final float CRYSTAL_ALIGNMENT_EXHAUSTION_RECOVERY = 0.15F;
	private static final int MAX_RESONANCE_TARGETS = 8;
	private static final int MAX_QUARTZ_HINT_TARGETS = 5;

	private static final List<TagKey<Block>> RESONANCE_ORE_TAGS = List.of(
			BlockTags.COAL_ORES,
			BlockTags.IRON_ORES,
			BlockTags.GOLD_ORES,
			BlockTags.COPPER_ORES,
			BlockTags.LAPIS_ORES,
			BlockTags.DIAMOND_ORES,
			BlockTags.EMERALD_ORES,
			BlockTags.REDSTONE_ORES
	);

	@Override
	public void onPrimaryBlockMined(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		applyDurabilityConservation(stack, brokenState);
		applyCrystalAlignmentExhaustionRelief(player, brokenState);
		tryCrystalResonance(level, blockPos, brokenState);
		tryQuartzDetection(level, blockPos, brokenState);
		tryRedstoneSensitivity(level, blockPos);
		tryReflectiveMining(level, blockPos);
	}

	@Override
	public void onExtraBlockMined(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		applyDurabilityConservation(stack, brokenState);
		applyCrystalAlignmentExhaustionRelief(player, brokenState);
	}

	@Override
	public float getExtraBlockExhaustionMultiplier(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		return isCrystalAlignmentBlock(brokenState) ? CRYSTAL_ALIGNMENT_EXHAUSTION_MULTIPLIER : 1.0F;
	}

	private static void applyDurabilityConservation(ItemStack stack, BlockState brokenState) {
		if (!stack.isDamageableItem()) {
			return;
		}

		float credit = 0.0F;
		if (isNetherAffinityBlock(brokenState)) {
			credit += NETHER_AFFINITY_CREDIT;
		}
		if (isQuartzPrecisionBlock(brokenState)) {
			credit += QUARTZ_PRECISION_CREDIT;
		}
		if (isCrystalStabilityBlock(brokenState)) {
			credit += CRYSTAL_STABILITY_CREDIT;
		}
		if (credit <= 0.0F) {
			return;
		}

		CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		CompoundTag tag = customData.copyTag();
		float storedCredit = tag.getFloat(DURABILITY_CREDIT_TAG) + credit;
		while (storedCredit >= 1.0F && stack.getDamageValue() > 0) {
			stack.setDamageValue(stack.getDamageValue() - 1);
			storedCredit -= 1.0F;
		}

		tag.putFloat(DURABILITY_CREDIT_TAG, storedCredit);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	private static void applyCrystalAlignmentExhaustionRelief(ServerPlayer player, BlockState brokenState) {
		if (!isCrystalAlignmentBlock(brokenState)) {
			return;
		}

		FoodData foodData = player.getFoodData();
		if (!(foodData instanceof FoodDataAccessor accessor)) {
			return;
		}

		float currentExhaustion = accessor.mineHammers$getExhaustionLevel();
		accessor.mineHammers$setExhaustionLevel(Math.max(0.0F, currentExhaustion - CRYSTAL_ALIGNMENT_EXHAUSTION_RECOVERY));
	}

	private static void tryCrystalResonance(ServerLevel level, BlockPos origin, BlockState brokenState) {
		if (!isCrystalResonanceTriggerBlock(brokenState) || level.random.nextFloat() >= CRYSTAL_RESONANCE_CHANCE) {
			return;
		}

		List<BlockPos> targets = new ArrayList<>();
		for (BlockPos scanPos : BlockPos.betweenClosed(
				origin.offset(-RESONANCE_RADIUS, -RESONANCE_RADIUS, -RESONANCE_RADIUS),
				origin.offset(RESONANCE_RADIUS, RESONANCE_RADIUS, RESONANCE_RADIUS))) {
			BlockState scanState = level.getBlockState(scanPos);
			if (!isCrystalResonanceTarget(scanState)) {
				continue;
			}

			targets.add(scanPos.immutable());
		}

		targets.stream()
				.sorted(Comparator.comparingDouble(pos -> pos.distSqr(origin)))
				.limit(MAX_RESONANCE_TARGETS)
				.forEach(pos -> spawnSubtleSparkle(level, pos, ParticleTypes.END_ROD, 3));
	}

	private static void tryQuartzDetection(ServerLevel level, BlockPos origin, BlockState brokenState) {
		if (!brokenState.is(Blocks.NETHERRACK) || level.random.nextFloat() >= QUARTZ_DETECTION_CHANCE) {
			return;
		}

		List<BlockPos> quartzTargets = new ArrayList<>();
		for (BlockPos scanPos : BlockPos.betweenClosed(
				origin.offset(-RESONANCE_RADIUS, -RESONANCE_RADIUS, -RESONANCE_RADIUS),
				origin.offset(RESONANCE_RADIUS, RESONANCE_RADIUS, RESONANCE_RADIUS))) {
			if (level.getBlockState(scanPos).is(Blocks.NETHER_QUARTZ_ORE)) {
				quartzTargets.add(scanPos.immutable());
			}
		}

		quartzTargets.stream()
				.sorted(Comparator.comparingDouble(pos -> pos.distSqr(origin)))
				.limit(MAX_QUARTZ_HINT_TARGETS)
				.forEach(pos -> spawnSubtleSparkle(level, pos, ParticleTypes.END_ROD, 4));
	}

	private static void tryRedstoneSensitivity(ServerLevel level, BlockPos origin) {
		for (BlockPos scanPos : BlockPos.betweenClosed(
				origin.offset(-REDSTONE_SENSITIVITY_RADIUS, -REDSTONE_SENSITIVITY_RADIUS, -REDSTONE_SENSITIVITY_RADIUS),
				origin.offset(REDSTONE_SENSITIVITY_RADIUS, REDSTONE_SENSITIVITY_RADIUS, REDSTONE_SENSITIVITY_RADIUS))) {
			if (!isRedstoneSensitiveTarget(level.getBlockState(scanPos))) {
				continue;
			}

			spawnSubtleSparkle(level, scanPos, ParticleTypes.ELECTRIC_SPARK, 2);
			return;
		}
	}

	private static void tryReflectiveMining(ServerLevel level, BlockPos origin) {
		for (BlockPos scanPos : BlockPos.betweenClosed(
				origin.offset(-LIGHT_REFLECTION_RADIUS, -LIGHT_REFLECTION_RADIUS, -LIGHT_REFLECTION_RADIUS),
				origin.offset(LIGHT_REFLECTION_RADIUS, LIGHT_REFLECTION_RADIUS, LIGHT_REFLECTION_RADIUS))) {
			BlockState scanState = level.getBlockState(scanPos);
			if (!scanState.is(Blocks.LAVA) && !scanState.is(Blocks.GLOWSTONE)) {
				continue;
			}

			level.sendParticles(
					ParticleTypes.GLOW,
					origin.getX() + 0.5D,
					origin.getY() + 0.5D,
					origin.getZ() + 0.5D,
					4,
					0.25D,
					0.25D,
					0.25D,
					0.0D
			);
			return;
		}
	}

	private static void spawnSubtleSparkle(ServerLevel level, BlockPos targetPos, net.minecraft.core.particles.ParticleOptions particle, int count) {
		boolean spawnedNearAir = false;
		for (Direction direction : Direction.values()) {
			BlockPos adjacentPos = targetPos.relative(direction);
			if (!level.getBlockState(adjacentPos).isAir()) {
				continue;
			}

			spawnedNearAir = true;
			level.sendParticles(
					particle,
					adjacentPos.getX() + 0.5D,
					adjacentPos.getY() + 0.5D,
					adjacentPos.getZ() + 0.5D,
					1,
					0.08D,
					0.08D,
					0.08D,
					0.0D
			);
		}

		if (!spawnedNearAir) {
			level.sendParticles(
					particle,
					targetPos.getX() + 0.5D,
					targetPos.getY() + 0.5D,
					targetPos.getZ() + 0.5D,
					count,
					0.18D,
					0.18D,
					0.18D,
					0.0D
			);
		}
	}

	private static boolean isCrystalResonanceTriggerBlock(BlockState state) {
		return state.is(Blocks.STONE)
				|| state.is(Blocks.DEEPSLATE)
				|| state.is(Blocks.NETHERRACK)
				|| state.is(Blocks.BLACKSTONE);
	}

	private static boolean isCrystalResonanceTarget(BlockState state) {
		for (TagKey<Block> oreTag : RESONANCE_ORE_TAGS) {
			if (state.is(oreTag)) {
				return true;
			}
		}

		return state.is(Blocks.AMETHYST_BLOCK)
				|| state.is(Blocks.BUDDING_AMETHYST)
				|| state.is(Blocks.SMALL_AMETHYST_BUD)
				|| state.is(Blocks.MEDIUM_AMETHYST_BUD)
				|| state.is(Blocks.LARGE_AMETHYST_BUD)
				|| state.is(Blocks.AMETHYST_CLUSTER)
				|| state.is(Blocks.CALCITE)
				|| state.is(Blocks.NETHER_QUARTZ_ORE);
	}

	private static boolean isRedstoneSensitiveTarget(BlockState state) {
		return state.is(Blocks.REDSTONE_ORE)
				|| state.is(Blocks.DEEPSLATE_REDSTONE_ORE)
				|| state.is(Blocks.REDSTONE_WIRE)
				|| state.is(Blocks.REPEATER)
				|| state.is(Blocks.COMPARATOR)
				|| state.is(Blocks.REDSTONE_TORCH)
				|| state.is(Blocks.REDSTONE_WALL_TORCH)
				|| state.is(Blocks.OBSERVER);
	}

	private static boolean isNetherAffinityBlock(BlockState state) {
		return state.is(Blocks.NETHERRACK)
				|| state.is(Blocks.BLACKSTONE)
				|| state.is(Blocks.BASALT)
				|| state.is(Blocks.SMOOTH_BASALT);
	}

	private static boolean isQuartzPrecisionBlock(BlockState state) {
		return state.is(Blocks.GLASS)
				|| state.is(Blocks.GLASS_PANE)
				|| state.is(Blocks.GLOWSTONE)
				|| state.is(Blocks.AMETHYST_BLOCK)
				|| state.is(Blocks.BUDDING_AMETHYST)
				|| state.is(Blocks.SMALL_AMETHYST_BUD)
				|| state.is(Blocks.MEDIUM_AMETHYST_BUD)
				|| state.is(Blocks.LARGE_AMETHYST_BUD)
				|| state.is(Blocks.AMETHYST_CLUSTER);
	}

	private static boolean isCrystalAlignmentBlock(BlockState state) {
		return state.is(Blocks.NETHER_QUARTZ_ORE)
				|| state.is(Blocks.QUARTZ_BLOCK)
				|| state.is(Blocks.QUARTZ_PILLAR)
				|| state.is(Blocks.SMOOTH_QUARTZ);
	}

	private static boolean isCrystalStabilityBlock(BlockState state) {
		return state.is(Blocks.QUARTZ_BLOCK)
				|| state.is(Blocks.QUARTZ_PILLAR)
				|| state.is(Blocks.SMOOTH_QUARTZ);
	}
}
