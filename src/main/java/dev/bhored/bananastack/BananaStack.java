package dev.bhored.bananastack;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BananaStack implements ModInitializer {
	public static final String MOD_ID = "bananastack";
	public static final Logger LOGGER = LoggerFactory.getLogger("Banana Stack");

	@Override
	public void onInitialize() {
		StackConfig.load();
		RidingPreference.register();

		UseEntityCallback.EVENT.register(Mounting::onUseEntity);
		ServerTickEvents.END_SERVER_TICK.register(Stacks::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				Stacks.leave(player, Stacks.Reason.DIED);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> StackCommand.register(dispatcher));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
