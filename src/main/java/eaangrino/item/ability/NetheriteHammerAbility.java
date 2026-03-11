package eaangrino.item.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class NetheriteHammerAbility implements HammerAbility {
	private static final int THERMAL_RADIUS = 6;
	private static final int THERMAL_HASTE_TICKS = 10;

	@Override
	public void onInventoryTick(ItemStack stack, Level level, Entity entity, boolean isSelected) {
		if (level.isClientSide() || !isSelected || !(entity instanceof ServerPlayer player)) {
			return;
		}

		if (hasNearbyThermalSource(level, player.blockPosition())) {
			// Haste provides stable mining speed amplification while thermal bonus is active.
			player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, THERMAL_HASTE_TICKS, 0, true, false, false));
		}
	}

	@Override
	public void onPrimaryBlockMined(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		if (!hasNearbyThermalSource(level, blockPos)) {
			return;
		}

		level.sendParticles(
				ParticleTypes.SMALL_FLAME,
				blockPos.getX() + 0.5D,
				blockPos.getY() + 0.6D,
				blockPos.getZ() + 0.5D,
				4,
				0.2D,
				0.2D,
				0.2D,
				0.01D
		);
		level.sendParticles(
				ParticleTypes.SMOKE,
				blockPos.getX() + 0.5D,
				blockPos.getY() + 0.7D,
				blockPos.getZ() + 0.5D,
				3,
				0.2D,
				0.2D,
				0.2D,
				0.01D
		);

		if (level.random.nextFloat() < 0.15F) {
			level.playSound(null, blockPos, SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.25F, 1.2F);
		}
	}

	private static boolean hasNearbyThermalSource(Level level, BlockPos origin) {
		for (BlockPos scanPos : BlockPos.betweenClosed(
				origin.offset(-THERMAL_RADIUS, -THERMAL_RADIUS, -THERMAL_RADIUS),
				origin.offset(THERMAL_RADIUS, THERMAL_RADIUS, THERMAL_RADIUS))) {
			if (isThermalSource(level.getBlockState(scanPos))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isThermalSource(BlockState state) {
		return state.is(Blocks.LAVA)
				|| state.is(Blocks.MAGMA_BLOCK)
				|| state.is(Blocks.FIRE)
				|| state.is(Blocks.SOUL_FIRE)
				|| state.is(Blocks.LAVA_CAULDRON);
	}
}
