package eaangrino.mixin.client;

import eaangrino.config.MineHammersConfig;
import eaangrino.item.HammerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void mineHammers$onScroll(long windowPointer, double horizontalScroll, double verticalScroll, CallbackInfo callbackInfo) {
		if (verticalScroll == 0.0D || minecraft.player == null || minecraft.screen != null) {
			return;
		}

		MineHammersConfig.ConfigData config = MineHammersConfig.get();
		if (!config.shiftScrollShapeSwitchEnabled || !minecraft.player.isShiftKeyDown()) {
			return;
		}

		ItemStack heldItem = minecraft.player.getMainHandItem();
		if (!(heldItem.getItem() instanceof HammerItem)) {
			return;
		}

		String newShape = MineHammersConfig.cycleMiningShape(verticalScroll > 0.0D ? 1 : -1);
		minecraft.player.displayClientMessage(
				Component.translatable("mine-hammers.config.shape.changed", Component.translatable("mine-hammers.config.shape." + newShape)),
				true
		);
		callbackInfo.cancel();
	}
}
