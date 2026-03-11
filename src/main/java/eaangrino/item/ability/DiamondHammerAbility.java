package eaangrino.item.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class DiamondHammerAbility implements HammerAbility {
	private static final float PRECISION_STRIKE_CHANCE = 0.20F;
	private static final List<TagKey<Block>> PRECISION_ORE_TAGS = List.of(
			BlockTags.COAL_ORES,
			BlockTags.IRON_ORES,
			BlockTags.GOLD_ORES,
			BlockTags.REDSTONE_ORES,
			BlockTags.LAPIS_ORES,
			BlockTags.DIAMOND_ORES,
			BlockTags.EMERALD_ORES,
			BlockTags.COPPER_ORES
	);

	@Override
	public void onPrimaryBlockMined(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		if (!isPrecisionOre(brokenState) || !stack.isDamageableItem()) {
			return;
		}

		if (level.random.nextFloat() >= PRECISION_STRIKE_CHANCE || stack.getDamageValue() <= 0) {
			return;
		}

		// Compensate one durability point to approximate "durability cost = 0" for this ore break.
		stack.setDamageValue(stack.getDamageValue() - 1);
		level.sendParticles(
				ParticleTypes.CRIT,
				blockPos.getX() + 0.5D,
				blockPos.getY() + 0.5D,
				blockPos.getZ() + 0.5D,
				4,
				0.2D,
				0.2D,
				0.2D,
				0.0D
		);
	}

	@Override
	public boolean allowAreaMiningForPrimaryBlock(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState primaryState, BlockPos primaryPos) {
		// Clean Break: do not area-mine falling blocks to prevent disruptive collapse chains.
		return !isCleanBreakBlock(primaryState);
	}

	private static boolean isPrecisionOre(BlockState state) {
		for (TagKey<Block> oreTag : PRECISION_ORE_TAGS) {
			if (state.is(oreTag)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isCleanBreakBlock(BlockState state) {
		return state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL);
	}
}
