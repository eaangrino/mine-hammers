package eaangrino.item.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SlimeHammerAbility implements HammerAbility {
	private static final String DURABILITY_CREDIT_TAG = "mine_hammers_slime_durability_credit";
	private static final float SHOCK_ABSORPTION_EXHAUSTION_MULTIPLIER = 0.90F;
	private static final float STICKY_MINING_DURABILITY_CREDIT = 0.25F;
	private static final float SOFT_LANDING_CHANCE = 0.25F;
	private static final float SOFT_LANDING_FALL_MULTIPLIER = 0.90F;
	private static final float STICKY_DROPS_CHANCE = 0.25F;
	private static final float STICKY_PULL_STRENGTH = 0.10F;
	private static final float STICKY_STABILITY_HORIZONTAL_DAMPING = 0.55F;
	private static final float STICKY_STABILITY_VERTICAL_DAMPING = 0.85F;
	private static final int STICKY_MINING_RADIUS = 5;
	private static final int SLIME_CHUNK_SALT = 987234911;

	@Override
	public void onPrimaryBlockMined(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		applyStickyMiningDurability(stack, level, blockPos);
		trySlimeEcho(level, blockPos);
	}

	@Override
	public void onExtraBlockMined(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		applyStickyMiningDurability(stack, level, blockPos);
	}

	@Override
	public float getExtraBlockExhaustionMultiplier(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		return SHOCK_ABSORPTION_EXHAUSTION_MULTIPLIER;
	}

	@Override
	public float modifyFallDistance(ItemStack stack, Level level, LivingEntity entity, float fallDistance) {
		if (!(entity instanceof ServerPlayer player) || fallDistance <= 3.0F) {
			return fallDistance;
		}

		return player.getRandom().nextFloat() < SOFT_LANDING_CHANCE
				? fallDistance * SOFT_LANDING_FALL_MULTIPLIER
				: fallDistance;
	}

	@Override
	public void onBlockMiningFinished(ItemStack stack, ServerLevel level, ServerPlayer player, BlockPos blockPos, int totalBrokenBlocks) {
		stabilizeNearbyDrops(level, player, blockPos, totalBrokenBlocks);
		level.getServer().execute(() -> stabilizeNearbyDrops(level, player, blockPos, totalBrokenBlocks));
	}

	private static void applyStickyMiningDurability(ItemStack stack, ServerLevel level, BlockPos origin) {
		if (!stack.isDamageableItem() || !hasStickySupportNearby(level, origin)) {
			return;
		}

		CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		CompoundTag tag = customData.copyTag();
		float storedCredit = tag.getFloat(DURABILITY_CREDIT_TAG) + STICKY_MINING_DURABILITY_CREDIT;
		while (storedCredit >= 1.0F && stack.getDamageValue() > 0) {
			stack.setDamageValue(stack.getDamageValue() - 1);
			storedCredit -= 1.0F;
		}

		tag.putFloat(DURABILITY_CREDIT_TAG, storedCredit);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	private static boolean hasStickySupportNearby(ServerLevel level, BlockPos origin) {
		for (BlockPos scanPos : BlockPos.betweenClosed(
				origin.offset(-STICKY_MINING_RADIUS, -STICKY_MINING_RADIUS, -STICKY_MINING_RADIUS),
				origin.offset(STICKY_MINING_RADIUS, STICKY_MINING_RADIUS, STICKY_MINING_RADIUS))) {
			if (level.getBlockState(scanPos).is(Blocks.SLIME_BLOCK)) {
				return true;
			}
		}

		return !level.getEntitiesOfClass(
				Slime.class,
				new AABB(origin).inflate(STICKY_MINING_RADIUS),
				slime -> slime.isAlive()
		).isEmpty();
	}

	private static void stabilizeNearbyDrops(ServerLevel level, ServerPlayer player, BlockPos origin, int totalBrokenBlocks) {
		double radius = 3.0D + Math.min(1.5D, totalBrokenBlocks * 0.15D);
		AABB searchBox = new AABB(origin).inflate(radius);
		boolean pullDrops = level.random.nextFloat() < STICKY_DROPS_CHANCE;

		for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, searchBox, SlimeHammerAbility::isValidDrop)) {
			Vec3 movement = itemEntity.getDeltaMovement();
			Vec3 stabilized = new Vec3(
					movement.x * STICKY_STABILITY_HORIZONTAL_DAMPING,
					movement.y * STICKY_STABILITY_VERTICAL_DAMPING,
					movement.z * STICKY_STABILITY_HORIZONTAL_DAMPING
			);

			if (pullDrops) {
				Vec3 toPlayer = player.position().add(0.0D, 0.25D, 0.0D).subtract(itemEntity.position());
				if (toPlayer.lengthSqr() > 0.001D) {
					stabilized = stabilized.add(toPlayer.normalize().scale(STICKY_PULL_STRENGTH));
				}
			}

			itemEntity.setDeltaMovement(stabilized);
			itemEntity.hasImpulse = true;
		}
	}

	private static boolean isValidDrop(ItemEntity itemEntity) {
		return itemEntity.isAlive() && !itemEntity.getItem().isEmpty();
	}

	private static void trySlimeEcho(ServerLevel level, BlockPos origin) {
		if (!isInOrNearSlimeChunk(level, origin)) {
			return;
		}

		level.sendParticles(
				ParticleTypes.ITEM_SLIME,
				origin.getX() + 0.5D,
				origin.getY() + 0.6D,
				origin.getZ() + 0.5D,
				4,
				0.25D,
				0.2D,
				0.25D,
				0.01D
		);
	}

	private static boolean isInOrNearSlimeChunk(ServerLevel level, BlockPos origin) {
		int chunkX = origin.getX() >> 4;
		int chunkZ = origin.getZ() >> 4;
		for (int xOffset = -1; xOffset <= 1; xOffset++) {
			for (int zOffset = -1; zOffset <= 1; zOffset++) {
				if (isSlimeChunk(level, chunkX + xOffset, chunkZ + zOffset)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isSlimeChunk(ServerLevel level, int chunkX, int chunkZ) {
		return WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, level.getSeed(), SLIME_CHUNK_SALT).nextInt(10) == 0;
	}
}
