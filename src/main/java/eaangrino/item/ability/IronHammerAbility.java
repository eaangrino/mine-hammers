package eaangrino.item.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class IronHammerAbility implements HammerAbility {
	private static final String DURABILITY_CREDIT_TAG = "mine_hammers_iron_efficiency_credit";
	private static final float DURABILITY_REDUCTION_PER_BLOCK = 0.1F;

	@Override
	public void onPrimaryBlockMined(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		applyToolEfficiency(stack, level, brokenState, blockPos);
	}

	@Override
	public void onExtraBlockMined(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		applyToolEfficiency(stack, level, brokenState, blockPos);
	}

	@Override
	public boolean shouldSkipAreaBlock(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState targetState, BlockPos targetPos) {
		return targetState.is(Blocks.CHEST)
				|| targetState.is(Blocks.TRAPPED_CHEST)
				|| targetState.is(Blocks.BARREL)
				|| targetState.is(Blocks.SPAWNER);
	}

	private static void applyToolEfficiency(ItemStack stack, ServerLevel level, BlockState brokenState, BlockPos blockPos) {
		if (!isEfficiencyBlock(brokenState) || !stack.isDamageableItem()) {
			return;
		}

		CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		CompoundTag tag = customData.copyTag();
		float credit = tag.getFloat(DURABILITY_CREDIT_TAG) + DURABILITY_REDUCTION_PER_BLOCK;
		while (credit >= 1.0F && stack.getDamageValue() > 0) {
			stack.setDamageValue(stack.getDamageValue() - 1);
			credit -= 1.0F;
		}

		tag.putFloat(DURABILITY_CREDIT_TAG, credit);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

		if (level.random.nextFloat() < 0.35F) {
			level.sendParticles(
					ParticleTypes.CRIT,
					blockPos.getX() + 0.5D,
					blockPos.getY() + 0.5D,
					blockPos.getZ() + 0.5D,
					2,
					0.15D,
					0.15D,
					0.15D,
					0.0D
			);
		}
	}

	private static boolean isEfficiencyBlock(BlockState state) {
		return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE);
	}
}
