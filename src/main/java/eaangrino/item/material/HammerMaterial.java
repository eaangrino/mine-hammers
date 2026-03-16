package eaangrino.item.material;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;

public final class HammerMaterial {
	private HammerMaterial() {
	}

	public static ToolMaterial create(
			TagKey<Block> incorrectBlocksForDrops,
			int durability,
			float speed,
			float attackDamageBonus,
			int enchantmentValue,
			TagKey<Item> repairItems
	) {
		return new ToolMaterial(
				incorrectBlocksForDrops,
				durability,
				speed,
				attackDamageBonus,
				enchantmentValue,
				repairItems
		);
	}
}
