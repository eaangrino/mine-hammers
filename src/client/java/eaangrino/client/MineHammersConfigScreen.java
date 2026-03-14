package eaangrino.client;

import eaangrino.config.MineHammersConfig;
import eaangrino.mining.MiningShapes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MineHammersConfigScreen extends Screen {
	private final Screen parent;
	private final MineHammersConfig.ConfigData draftConfig;
	private Button areaMiningButton;
	private Button miningShapeButton;
	private Button sneakingButton;
	private Button abilitiesButton;
	private Button shiftScrollButton;

	public MineHammersConfigScreen(Screen parent) {
		super(Component.translatable("mine-hammers.config.title"));
		this.parent = parent;
		MineHammersConfig.ConfigData current = MineHammersConfig.get();
		this.draftConfig = new MineHammersConfig.ConfigData(
				current.areaMiningEnabled,
				current.radius,
				current.disableWhenSneaking,
				current.enableHammerAbilities,
				current.onlyPickaxeMineable,
				current.requireCorrectToolForDrops,
				current.hungerExhaustionPerExtraBlock,
				current.miningShape,
				current.shiftScrollShapeSwitchEnabled
		);
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int top = this.height / 4;
		int width = 260;
		int height = 20;
		int step = 24;

		areaMiningButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
			draftConfig.areaMiningEnabled = !draftConfig.areaMiningEnabled;
			refreshButtons();
		}).bounds(centerX - width / 2, top, width, height).build());

		miningShapeButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
			draftConfig.miningShape = MiningShapes.cycle(draftConfig.miningShape, 1);
			refreshButtons();
		}).bounds(centerX - width / 2, top + step, width, height).build());

		sneakingButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
			draftConfig.disableWhenSneaking = !draftConfig.disableWhenSneaking;
			refreshButtons();
		}).bounds(centerX - width / 2, top + step * 2, width, height).build());

		abilitiesButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
			draftConfig.enableHammerAbilities = !draftConfig.enableHammerAbilities;
			refreshButtons();
		}).bounds(centerX - width / 2, top + step * 3, width, height).build());

		shiftScrollButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
			draftConfig.shiftScrollShapeSwitchEnabled = !draftConfig.shiftScrollShapeSwitchEnabled;
			refreshButtons();
		}).bounds(centerX - width / 2, top + step * 4, width, height).build());

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
			MineHammersConfig.set(draftConfig);
			if (minecraft != null) {
				minecraft.setScreen(parent);
			}
		}).bounds(centerX - 102, top + step * 6, 100, height).build());

		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> {
			if (minecraft != null) {
				minecraft.setScreen(parent);
			}
		}).bounds(centerX + 2, top + step * 6, 100, height).build());

		refreshButtons();
	}

	private void refreshButtons() {
		areaMiningButton.setMessage(toggleLabel("mine-hammers.config.area_mining", draftConfig.areaMiningEnabled));
		miningShapeButton.setMessage(
				Component.translatable("mine-hammers.config.mining_shape", Component.translatable("mine-hammers.config.shape." + MiningShapes.sanitize(draftConfig.miningShape, draftConfig.radius)))
		);
		sneakingButton.setMessage(toggleLabel("mine-hammers.config.disable_when_sneaking", draftConfig.disableWhenSneaking));
		abilitiesButton.setMessage(toggleLabel("mine-hammers.config.enable_hammer_abilities", draftConfig.enableHammerAbilities));
		shiftScrollButton.setMessage(toggleLabel("mine-hammers.config.shift_scroll_switch", draftConfig.shiftScrollShapeSwitchEnabled));
		miningShapeButton.active = draftConfig.areaMiningEnabled;
	}

	private static Component toggleLabel(String key, boolean enabled) {
		return Component.translatable(key, Component.translatable(enabled ? "options.on" : "options.off"));
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
		guiGraphics.drawCenteredString(font, Component.translatable("mine-hammers.config.shift_scroll_hint"), width / 2, height / 4 + 129, 0xA0A0A0);
	}
}
