package dev.bhored.bananastack.mixin;

import dev.bhored.bananastack.Stacks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
abstract class PlayerListMixin {
	/** Normally done in ServerPlayer#disconnect already; this also catches anything that removes a player some other way. */
	@Inject(method = "remove", at = @At("HEAD"))
	private void bananastack$leaveStack(ServerPlayer player, CallbackInfo ci) {
		Stacks.leave(player, Stacks.Reason.LEFT_GAME);
	}
}
