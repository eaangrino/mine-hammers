package eaangrino.item.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class EmeraldHammerAbility implements HammerAbility {
	private static final int ORE_SCAN_RADIUS = 6;
	private static final int ORE_HIGHLIGHT_PARTICLES = 5;
	private static final List<TagKey<Block>> ORE_TARGET_TAGS = List.of(
			BlockTags.COAL_ORES,
			BlockTags.IRON_ORES,
			BlockTags.GOLD_ORES,
			BlockTags.REDSTONE_ORES,
			BlockTags.LAPIS_ORES,
			BlockTags.DIAMOND_ORES,
			BlockTags.EMERALD_ORES
	);

	@Override
	public void onPrimaryBlockMined(ItemStack stack, ServerLevel level, ServerPlayer player, BlockState brokenState, BlockPos blockPos) {
		if (!isOreSenseTriggerBlock(brokenState)) {
			return;
		}

		level.sendParticles(
				ParticleTypes.HAPPY_VILLAGER,
				blockPos.getX() + 0.5D,
				blockPos.getY() + 0.5D,
				blockPos.getZ() + 0.5D,
				8,
				0.35D,
				0.35D,
				0.35D,
				0.01D
		);

		BlockPos.betweenClosedStream(
				blockPos.offset(-ORE_SCAN_RADIUS, -ORE_SCAN_RADIUS, -ORE_SCAN_RADIUS),
				blockPos.offset(ORE_SCAN_RADIUS, ORE_SCAN_RADIUS, ORE_SCAN_RADIUS)
		).forEach(scanPos -> {
			BlockState scanState = level.getBlockState(scanPos);
			if (isOreTarget(scanState)) {
				spawnOreSenseParticles(level, scanPos.immutable());
			}
		});
	}

	private static boolean isOreTarget(BlockState state) {
		for (TagKey<Block> tag : ORE_TARGET_TAGS) {
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
					ORE_HIGHLIGHT_PARTICLES,
					0.2D,
					0.2D,
					0.2D,
					0.01D
			);
		}
	}
}
