package eaangrino.item.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class EnderHammerAbility implements HammerAbility {
	private static final String DURABILITY_CREDIT_TAG = "mine_hammers_ender_durability_credit";
	private static final int SPATIAL_ECHO_RADIUS = 6;
	private static final float SPATIAL_ECHO_CHANCE = 0.18F;
	private static final int SPATIAL_ECHO_PARTICLE_TARGETS = 8;
	private static final float ENDER_PULL_STRENGTH = 0.14F;
	private static final float DIMENSIONAL_FATIGUE_MULTIPLIER = 0.90F;
	private static final float END_STONE_AFFINITY_CREDIT = 0.20F;
	private static final double VOID_STABILITY_KNOCKBACK_MULTIPLIER = 0.80D;
	private static final int VOID_SCAN_RADIUS = 8;
	private static final int VOID_SCAN_DEPTH = 18;

	@Override
	public void onPrimaryBlockMined(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		applyDurabilityConservation(stack, brokenState);
		trySpatialEcho(level, blockPos, brokenState);
		tryVoidAwareness(level, blockPos);
	}

	@Override
	public void onExtraBlockMined(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		applyDurabilityConservation(stack, brokenState);
	}

	@Override
	public float getExtraBlockExhaustionMultiplier(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		return DIMENSIONAL_FATIGUE_MULTIPLIER;
	}

	@Override
	public double modifyKnockbackReceived(ItemStack stack, Level level, LivingEntity entity, double strength) {
		if (!isNearVoid(level, entity.blockPosition())) {
			return strength;
		}

		return strength * VOID_STABILITY_KNOCKBACK_MULTIPLIER;
	}

	@Override
	public void onBlockMiningFinished(ItemStack stack, ServerLevel level, ServerPlayer player, BlockPos blockPos, int totalBrokenBlocks) {
		pullNearbyDrops(level, player, blockPos, totalBrokenBlocks);
		level.getServer().execute(() -> pullNearbyDrops(level, player, blockPos, totalBrokenBlocks));
	}

	private static void applyDurabilityConservation(ItemStack stack, BlockState brokenState) {
		if (!stack.isDamageableItem() || !isEndAffinityBlock(brokenState)) {
			return;
		}

		CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		CompoundTag tag = customData.copyTag();
		float storedCredit = tag.getFloat(DURABILITY_CREDIT_TAG) + END_STONE_AFFINITY_CREDIT;
		while (storedCredit >= 1.0F && stack.getDamageValue() > 0) {
			stack.setDamageValue(stack.getDamageValue() - 1);
			storedCredit -= 1.0F;
		}

		tag.putFloat(DURABILITY_CREDIT_TAG, storedCredit);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	private static void trySpatialEcho(ServerLevel level, BlockPos origin, BlockState brokenState) {
		if (!isSpatialEchoTriggerBlock(brokenState) || level.random.nextFloat() >= SPATIAL_ECHO_CHANCE) {
			return;
		}

		List<BlockPos> cavityMarkers = new ArrayList<>();
		for (BlockPos scanPos : BlockPos.betweenClosed(
				origin.offset(-SPATIAL_ECHO_RADIUS, -SPATIAL_ECHO_RADIUS, -SPATIAL_ECHO_RADIUS),
				origin.offset(SPATIAL_ECHO_RADIUS, SPATIAL_ECHO_RADIUS, SPATIAL_ECHO_RADIUS))) {
			if (!level.getBlockState(scanPos).isAir()) {
				continue;
			}
			if (!hasSolidNeighbor(level, scanPos)) {
				continue;
			}

			cavityMarkers.add(scanPos.immutable());
		}

		cavityMarkers.stream()
				.sorted(Comparator.comparingDouble(pos -> pos.distSqr(origin)))
				.limit(SPATIAL_ECHO_PARTICLE_TARGETS)
				.forEach(pos -> level.sendParticles(
						ParticleTypes.PORTAL,
						pos.getX() + 0.5D,
						pos.getY() + 0.5D,
						pos.getZ() + 0.5D,
						5,
						0.25D,
						0.25D,
						0.25D,
						0.01D
				));
	}

	private static boolean hasSolidNeighbor(ServerLevel level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockPos adjacentPos = pos.relative(direction);
			BlockState adjacentState = level.getBlockState(adjacentPos);
			if (!adjacentState.isAir() && adjacentState.isSolidRender(level, adjacentPos)) {
				return true;
			}
		}
		return false;
	}

	private static void tryVoidAwareness(ServerLevel level, BlockPos origin) {
		Vec3 voidDirection = findNearestVoidDirection(level, origin);
		if (voidDirection == null) {
			return;
		}

		Vec3 start = Vec3.atCenterOf(origin);
		for (int step = 1; step <= 4; step++) {
			Vec3 point = start.add(voidDirection.scale(step * 1.2D));
			level.sendParticles(
					ParticleTypes.PORTAL,
					point.x,
					point.y,
					point.z,
					2,
					0.08D,
					0.08D,
					0.08D,
					0.0D
			);
		}
	}

	private static void pullNearbyDrops(ServerLevel level, ServerPlayer player, BlockPos origin, int totalBrokenBlocks) {
		double pullRadius = 3.5D + Math.min(1.5D, totalBrokenBlocks * 0.15D);
		AABB searchBox = new AABB(origin).inflate(pullRadius);
		for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, searchBox, EntitySelector::canPull)) {
			Vec3 toPlayer = player.position().add(0.0D, 0.35D, 0.0D).subtract(itemEntity.position());
			double distance = toPlayer.length();
			if (distance < 0.001D) {
				continue;
			}

			Vec3 impulse = toPlayer.normalize().scale(ENDER_PULL_STRENGTH);
			itemEntity.setDeltaMovement(itemEntity.getDeltaMovement().add(impulse));
			itemEntity.hasImpulse = true;
		}
	}

	private static Vec3 findNearestVoidDirection(Level level, BlockPos origin) {
		if (!isEndDimension(level)) {
			return null;
		}

		BlockPos nearestVoidPos = null;
		double nearestDistanceSq = Double.MAX_VALUE;
		for (int xOffset = -VOID_SCAN_RADIUS; xOffset <= VOID_SCAN_RADIUS; xOffset++) {
			for (int zOffset = -VOID_SCAN_RADIUS; zOffset <= VOID_SCAN_RADIUS; zOffset++) {
				if (xOffset == 0 && zOffset == 0) {
					continue;
				}

				BlockPos scanPos = origin.offset(xOffset, 0, zOffset);
				if (!isVoidColumn(level, scanPos)) {
					continue;
				}

				double distanceSq = scanPos.distSqr(origin);
				if (distanceSq < nearestDistanceSq) {
					nearestDistanceSq = distanceSq;
					nearestVoidPos = scanPos.immutable();
				}
			}
		}

		if (nearestVoidPos == null) {
			return null;
		}

		Vec3 direction = Vec3.atCenterOf(nearestVoidPos).subtract(Vec3.atCenterOf(origin));
		return direction.lengthSqr() < 0.001D ? null : direction.normalize();
	}

	private static boolean isNearVoid(Level level, BlockPos origin) {
		return findNearestVoidDirection(level, origin) != null;
	}

	private static boolean isVoidColumn(Level level, BlockPos pos) {
		if (!isEndDimension(level)) {
			return false;
		}

		int minY = Math.max(level.getMinBuildHeight(), pos.getY() - VOID_SCAN_DEPTH);
		for (int y = pos.getY() - 1; y >= minY; y--) {
			BlockPos scanPos = new BlockPos(pos.getX(), y, pos.getZ());
			if (!level.getBlockState(scanPos).isAir()) {
				return false;
			}
		}

		return true;
	}

	private static boolean isEndDimension(Level level) {
		return level.dimension() == Level.END;
	}

	private static boolean isSpatialEchoTriggerBlock(BlockState state) {
		return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.END_STONE);
	}

	private static boolean isEndAffinityBlock(BlockState state) {
		return state.is(Blocks.END_STONE)
				|| state.is(Blocks.END_STONE_BRICKS)
				|| state.is(Blocks.PURPUR_BLOCK)
				|| state.is(Blocks.PURPUR_PILLAR);
	}

	private static final class EntitySelector {
		private EntitySelector() {
		}

		private static boolean canPull(ItemEntity itemEntity) {
			return itemEntity.isAlive() && !itemEntity.getItem().isEmpty();
		}
	}
}
