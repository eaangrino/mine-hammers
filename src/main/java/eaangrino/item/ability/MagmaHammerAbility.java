package eaangrino.item.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class MagmaHammerAbility implements HammerAbility {
	private static final String HEAT_TAG = "mine_hammers_magma_heat";
	private static final String LAST_TICK_TAG = "mine_hammers_magma_last_tick";
	private static final float MAX_HEAT = 100.0F;
	private static final float HEAT_PER_BLOCK = 4.0F;
	private static final float COOLDOWN_PER_SECOND = 3.0F;
	private static final float HOT_THRESHOLD = 80.0F;
	private static final int OVERHEAT_FATIGUE_TICKS = 60;
	private static final int OVERHEAT_SOUND_COOLDOWN_TICKS = 20;

	@Override
	public void onBlockMiningFinished(ItemStack stack, ServerLevel level, ServerPlayer player, BlockPos blockPos, int totalBrokenBlocks) {
		CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		CompoundTag tag = customData.copyTag();
		long gameTime = level.getGameTime();
		long lastTick = tag.getLong(LAST_TICK_TAG);
		float heat = tag.getFloat(HEAT_TAG);

		if (player.isInWaterOrRain()) {
			heat = 0.0F;
		} else if (lastTick > 0L && gameTime > lastTick) {
			float cooldown = (gameTime - lastTick) * (COOLDOWN_PER_SECOND / 20.0F);
			heat = Math.max(0.0F, heat - cooldown);
		}

		heat = Math.min(MAX_HEAT, heat + (HEAT_PER_BLOCK * Math.max(1, totalBrokenBlocks)));
		tag.putFloat(HEAT_TAG, heat);
		tag.putLong(LAST_TICK_TAG, gameTime);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

		if (heat >= MAX_HEAT) {
			player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, OVERHEAT_FATIGUE_TICKS, 1, true, true, true));
			if (gameTime - lastTick >= OVERHEAT_SOUND_COOLDOWN_TICKS) {
				level.playSound(null, blockPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.1F);
			}
			level.sendParticles(
					ParticleTypes.LARGE_SMOKE,
					blockPos.getX() + 0.5D,
					blockPos.getY() + 0.8D,
					blockPos.getZ() + 0.5D,
					14,
					0.35D,
					0.35D,
					0.35D,
					0.02D
			);
			return;
		}

		if (heat >= HOT_THRESHOLD) {
			level.sendParticles(
					ParticleTypes.SMOKE,
					blockPos.getX() + 0.5D,
					blockPos.getY() + 0.7D,
					blockPos.getZ() + 0.5D,
					4,
					0.2D,
					0.2D,
					0.2D,
					0.01D
			);
		}
	}
}
