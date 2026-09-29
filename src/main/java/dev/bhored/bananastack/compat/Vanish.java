package dev.bhored.bananastack.compat;

import dev.bhored.bananastack.BananaStack;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Drex's Vanish mod, when it's installed. A vanished player can't ride or be ridden: the riders would float on
 * nothing for everyone else, and the chat hints would give away that someone is there.
 */
public final class Vanish {
	private static final @Nullable MethodHandle IS_VANISHED = find();

	private Vanish() {
	}

	public static boolean isVanished(Entity entity) {
		if (IS_VANISHED == null) {
			return false;
		}
		try {
			return (boolean) IS_VANISHED.invokeExact(entity);
		} catch (Throwable e) {
			return false;
		}
	}

	private static @Nullable MethodHandle find() {
		if (!FabricLoader.getInstance().isModLoaded("vanish")) {
			return null;
		}
		try {
			Class<?> api = Class.forName("me.drex.vanish.api.VanishAPI");
			return MethodHandles.publicLookup().findStatic(api, "isVanished", MethodType.methodType(boolean.class, Entity.class));
		} catch (ReflectiveOperationException e) {
			BananaStack.LOGGER.warn("Vanish is installed but its API has changed; vanished players won't be treated specially", e);
			return null;
		}
	}
}
