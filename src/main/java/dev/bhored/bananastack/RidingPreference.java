package dev.bhored.bananastack;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;

/** Whether other players may ride someone. Saved with the player, so it survives logging out and restarts. */
public final class RidingPreference {
	/** Only stored for players who turned riding off; everyone else can be ridden. */
	private static final AttachmentType<Boolean> RIDING_OFF = AttachmentRegistry.create(
		BananaStack.id("riding_off"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath()
	);

	private RidingPreference() {
	}

	static void register() {
		// The attachment type is created when this class loads.
	}

	public static boolean allows(Player player) {
		return !Boolean.TRUE.equals(player.getAttached(RIDING_OFF));
	}

	public static void set(Player player, boolean allowed) {
		if (allowed) {
			player.removeAttached(RIDING_OFF);
		} else {
			player.setAttached(RIDING_OFF, true);
		}
	}
}
