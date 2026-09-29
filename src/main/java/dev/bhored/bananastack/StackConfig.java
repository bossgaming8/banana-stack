package dev.bhored.bananastack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

/** Server settings, stored in config/bananastack.json. */
public final class StackConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("bananastack.json");

	private static StackConfig current = new StackConfig();

	/** Turns riding on or off for the whole server. */
	public boolean enabled = true;
	/** Riders need an empty main hand, so right-clicking someone with a sword, food or a block never picks them up by accident. */
	public boolean requireEmptyHand = true;
	/**
	 * How closely a rider has to face the same way as the player they hop onto, in degrees. The default means
	 * you walk up behind someone; 180 lets people hop on from any side.
	 */
	public float behindAngle = 60.0F;
	/** Most players in one stack, counting the one at the bottom. 0 means no limit. */
	public int maxStackSize = 0;
	public boolean messages = true;
	public boolean sounds = true;
	public boolean particles = true;

	public static StackConfig get() {
		return current;
	}

	/** Reads the file, keeping the defaults for anything missing, and writes it back so new options show up. */
	public static boolean load() {
		StackConfig loaded = new StackConfig();
		if (Files.isRegularFile(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				StackConfig read = GSON.fromJson(reader, StackConfig.class);
				if (read != null) {
					loaded = read;
				}
			} catch (IOException | JsonParseException e) {
				BananaStack.LOGGER.warn("Could not read {}, using the defaults until it is fixed", FILE, e);
				current = new StackConfig();
				return false;
			}
		}
		loaded.behindAngle = Mth.clamp(loaded.behindAngle, 0.0F, 180.0F);
		loaded.maxStackSize = Math.max(0, loaded.maxStackSize);
		current = loaded;
		save();
		return true;
	}

	private static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Path temp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(temp)) {
				GSON.toJson(current, writer);
			}
			Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			BananaStack.LOGGER.warn("Could not save {}", FILE, e);
		}
	}
}
