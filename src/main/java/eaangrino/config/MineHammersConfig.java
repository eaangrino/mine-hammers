package eaangrino.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import eaangrino.MineHammers;
import eaangrino.mining.MiningShapes;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MineHammersConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(MineHammers.MOD_ID + ".json");

	private static ConfigData config = ConfigData.defaults();

	private MineHammersConfig() {
	}

	public static void load() {
		ConfigData defaults = ConfigData.defaults();

		if (Files.notExists(CONFIG_PATH)) {
			config = defaults;
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
			ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
			config = sanitize(loaded, defaults);
			save(); // persist sanitized values
		} catch (IOException | JsonParseException e) {
			MineHammers.LOGGER.error("Failed to load config {}, using defaults", CONFIG_PATH, e);
			config = defaults;
			save();
		}
	}

	public static ConfigData get() {
		return config;
	}

	public static synchronized void set(ConfigData updatedConfig) {
		config = sanitize(updatedConfig, ConfigData.defaults());
		save();
	}

	public static synchronized String cycleMiningShape(int step) {
		ConfigData current = get();
		current.miningShape = MiningShapes.cycle(current.miningShape, step);
		save();
		return current.miningShape;
	}

	private static ConfigData sanitize(ConfigData loaded, ConfigData defaults) {
		if (loaded == null) {
			return defaults;
		}

		int radius = Math.max(0, Math.min(4, loaded.radius));
		float hungerExhaustionPerExtraBlock = Math.max(0.0F, Math.min(1.0F, loaded.hungerExhaustionPerExtraBlock));
		String miningShape = MiningShapes.sanitize(loaded.miningShape, radius);
		boolean shiftScrollShapeSwitchEnabled = loaded.miningShape == null
				? defaults.shiftScrollShapeSwitchEnabled
				: loaded.shiftScrollShapeSwitchEnabled;
		return new ConfigData(
				loaded.areaMiningEnabled,
				radius,
				loaded.disableWhenSneaking,
				loaded.onlyPickaxeMineable,
				loaded.requireCorrectToolForDrops,
				hungerExhaustionPerExtraBlock,
				miningShape,
				shiftScrollShapeSwitchEnabled
		);
	}

	private static void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			MineHammers.LOGGER.error("Failed to save config {}", CONFIG_PATH, e);
		}
	}

	public static final class ConfigData {
		public boolean areaMiningEnabled;
		public int radius; // 1 => 3x3x1
		public boolean disableWhenSneaking;
		public boolean onlyPickaxeMineable;
		public boolean requireCorrectToolForDrops;
		public float hungerExhaustionPerExtraBlock;
		public String miningShape;
		public boolean shiftScrollShapeSwitchEnabled;

		public ConfigData() {
			this(true, 1, true, true, true, 0.0125F, MiningShapes.THREE_BY_THREE, true);
		}

		public ConfigData(
				boolean areaMiningEnabled,
				int radius,
				boolean disableWhenSneaking,
				boolean onlyPickaxeMineable,
				boolean requireCorrectToolForDrops,
				float hungerExhaustionPerExtraBlock,
				String miningShape,
				boolean shiftScrollShapeSwitchEnabled
		) {
			this.areaMiningEnabled = areaMiningEnabled;
			this.radius = radius;
			this.disableWhenSneaking = disableWhenSneaking;
			this.onlyPickaxeMineable = onlyPickaxeMineable;
			this.requireCorrectToolForDrops = requireCorrectToolForDrops;
			this.hungerExhaustionPerExtraBlock = hungerExhaustionPerExtraBlock;
			this.miningShape = miningShape;
			this.shiftScrollShapeSwitchEnabled = shiftScrollShapeSwitchEnabled;
		}

		public static ConfigData defaults() {
			return new ConfigData();
		}
	}
}
