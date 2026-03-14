package eaangrino.item.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;

public interface HammerAbility {
	default void onInventoryTick(ItemStack stack, Level level, Entity entity, boolean isSelected) {
	}

	default void onPrimaryBlockMined(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
	}

	default void onExtraBlockMined(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
	}

	default boolean shouldSkipAreaBlock(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState targetState, BlockPos targetPos) {
		return false;
	}

	default boolean allowAreaMiningForPrimaryBlock(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState primaryState, BlockPos primaryPos) {
		return true;
	}

	default float modifyDestroySpeed(ItemStack stack, Level level, LivingEntity entity, BlockState state, float currentSpeed) {
		return currentSpeed;
	}

	default float getExtraBlockExhaustionMultiplier(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		return 1.0F;
	}

	default double modifyKnockbackReceived(ItemStack stack, Level level, LivingEntity entity, double strength) {
		return strength;
	}

	default void onBlockMiningFinished(ItemStack stack, ServerLevel level, ServerPlayer player, BlockPos blockPos, int totalBrokenBlocks) {
	}
}
