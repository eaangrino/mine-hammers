package eaangrino.mixin;

import eaangrino.item.HammerItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyVariable(method = "knockback", at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private double mineHammers$modifyKnockbackStrength(double strength) {
		LivingEntity entity = (LivingEntity) (Object) this;
		if (!(entity instanceof Player player)) {
			return strength;
		}

		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof HammerItem)) {
			return strength;
		}

		return HammerItem.getModifiedKnockbackReceived(stack, player.level(), player, strength);
	}
}
