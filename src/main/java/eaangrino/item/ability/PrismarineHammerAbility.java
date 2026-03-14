package eaangrino.item.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public final class PrismarineHammerAbility implements HammerAbility {
	private static final String DURABILITY_CREDIT_TAG = "mine_hammers_prismarine_durability_credit";
	private static final int OCEAN_SENSE_RADIUS = 8;
	private static final float OCEAN_SENSE_CHANCE = 0.18F;
	private static final int GUARDIAN_ECHO_INTERVAL_TICKS = 80;
	private static final int GUARDIAN_ECHO_RANGE_CHUNKS = 5;
	private static final float AQUA_RESONANCE_BONUS = 1.10F;
	private static final float PRESSURE_STABILITY_BONUS = 1.30F;
	private static final float DEPTH_AWARENESS_BONUS = 1.10F;
	private static final float HYDRO_STABILITY_EXHAUSTION_MULTIPLIER = 0.90F;
	private static final float WET_MINING_DURABILITY_CREDIT = 0.10F;
	private static final float PRISMARINE_ECHO_DURABILITY_CREDIT = 0.20F;

	@Override
	public void onInventoryTick(ItemStack stack, Level level, Entity entity, boolean isSelected) {
		if (level.isClientSide() || !isSelected || !(entity instanceof ServerPlayer player) || level.getGameTime() % GUARDIAN_ECHO_INTERVAL_TICKS != 0L) {
			return;
		}

		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		BlockPos monumentPos = serverLevel.findNearestMapStructure(StructureTags.ON_OCEAN_EXPLORER_MAPS, player.blockPosition(), GUARDIAN_ECHO_RANGE_CHUNKS, false);
		if (monumentPos == null || monumentPos.closerThan(player.blockPosition(), 96.0D)) {
			serverLevel.sendParticles(
					ParticleTypes.NAUTILUS,
					player.getX(),
					player.getY() + 1.0D,
					player.getZ(),
					6,
					0.35D,
					0.45D,
					0.35D,
					0.02D
			);
			serverLevel.sendParticles(
					ParticleTypes.GLOW,
					player.getX(),
					player.getY() + 1.1D,
					player.getZ(),
					3,
					0.25D,
					0.35D,
					0.25D,
					0.0D
			);
			serverLevel.playSound(null, player.blockPosition(), SoundEvents.GUARDIAN_AMBIENT, SoundSource.PLAYERS, 0.2F, 1.5F);
		}
	}

	@Override
	public void onPrimaryBlockMined(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		applyDurabilityConservation(stack, brokenState);
		tryOceanSense(level, blockPos, brokenState);
	}

	@Override
	public void onExtraBlockMined(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		applyDurabilityConservation(stack, brokenState);
	}

	@Override
	public float modifyDestroySpeed(ItemStack stack, Level level, LivingEntity entity, BlockState state, float currentSpeed) {
		if (currentSpeed <= 1.0F) {
			return currentSpeed;
		}

		float modifiedSpeed = currentSpeed;
		if (entity.isInWaterOrRain()) {
			modifiedSpeed *= AQUA_RESONANCE_BONUS;
		}
		if (entity.isUnderWater()) {
			modifiedSpeed *= PRESSURE_STABILITY_BONUS;
			if (entity.blockPosition().getY() < 50) {
				modifiedSpeed *= DEPTH_AWARENESS_BONUS;
			}
		}
		return modifiedSpeed;
	}

	@Override
	public float getExtraBlockExhaustionMultiplier(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		return player.isUnderWater() ? HYDRO_STABILITY_EXHAUSTION_MULTIPLIER : 1.0F;
	}

	private static void applyDurabilityConservation(ItemStack stack, BlockState brokenState) {
		if (!stack.isDamageableItem()) {
			return;
		}

		float credit = 0.0F;
		if (isWetMiningBlock(brokenState)) {
			credit += WET_MINING_DURABILITY_CREDIT;
		}
		if (isPrismarineFamilyBlock(brokenState)) {
			credit += PRISMARINE_ECHO_DURABILITY_CREDIT;
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

	private static void tryOceanSense(ServerLevel level, BlockPos origin, BlockState brokenState) {
		if (!isOceanSenseTriggerBlock(brokenState) || level.random.nextFloat() >= OCEAN_SENSE_CHANCE) {
			return;
		}

		BlockPos waterTarget = findNearestWaterTarget(level, origin);
		if (waterTarget == null) {
			return;
		}

		Vec3 start = Vec3.atCenterOf(origin);
		Vec3 end = Vec3.atCenterOf(waterTarget);
		Vec3 delta = end.subtract(start);
		double length = delta.length();
		if (length < 1.0D) {
			return;
		}

		Vec3 direction = delta.scale(1.0D / length);
		for (int step = 1; step <= 4; step++) {
			Vec3 point = start.add(direction.scale(step * 1.1D));
			level.sendParticles(ParticleTypes.NAUTILUS, point.x, point.y, point.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
		}

		level.sendParticles(
				ParticleTypes.BUBBLE,
				end.x,
				end.y,
				end.z,
				6,
				0.2D,
				0.2D,
				0.2D,
				0.01D
		);
	}

	private static BlockPos findNearestWaterTarget(ServerLevel level, BlockPos origin) {
		BlockPos nearestPos = null;
		double nearestDistanceSq = Double.MAX_VALUE;

		for (BlockPos scanPos : BlockPos.betweenClosed(
				origin.offset(-OCEAN_SENSE_RADIUS, -OCEAN_SENSE_RADIUS, -OCEAN_SENSE_RADIUS),
				origin.offset(OCEAN_SENSE_RADIUS, OCEAN_SENSE_RADIUS, OCEAN_SENSE_RADIUS))) {
			BlockState scanState = level.getBlockState(scanPos);
			if (!scanState.getFluidState().is(FluidTags.WATER) || !hasOpenWaterSpace(level, scanPos)) {
				continue;
			}

			double distanceSq = scanPos.distSqr(origin);
			if (distanceSq < nearestDistanceSq) {
				nearestDistanceSq = distanceSq;
				nearestPos = scanPos.immutable();
			}
		}

		return nearestPos;
	}

	private static boolean hasOpenWaterSpace(ServerLevel level, BlockPos waterPos) {
		for (Direction direction : Direction.values()) {
			BlockPos adjacentPos = waterPos.relative(direction);
			BlockState adjacentState = level.getBlockState(adjacentPos);
			if (adjacentState.isAir() || !adjacentState.isSolidRender(level, adjacentPos)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isWetMiningBlock(BlockState state) {
		return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
	}

	private static boolean isPrismarineFamilyBlock(BlockState state) {
		return state.is(Blocks.PRISMARINE)
				|| state.is(Blocks.PRISMARINE_BRICKS)
				|| state.is(Blocks.DARK_PRISMARINE);
	}

	private static boolean isOceanSenseTriggerBlock(BlockState state) {
		return state.is(Blocks.STONE)
				|| state.is(Blocks.DEEPSLATE)
				|| state.is(Blocks.DIRT)
				|| state.is(Blocks.COARSE_DIRT)
				|| state.is(Blocks.ROOTED_DIRT)
				|| state.is(Blocks.SAND)
				|| state.is(Blocks.RED_SAND);
	}
}
