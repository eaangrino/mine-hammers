package eaangrino;

import eaangrino.client.HammerAreaOutlineRenderer;
import net.fabricmc.api.ClientModInitializer;

public class MineHammersClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HammerAreaOutlineRenderer.register();
	}
}
