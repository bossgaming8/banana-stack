package dev.bhored.bananastack.mixin;

import dev.bhored.bananastack.Stacks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	@Inject(method = "disconnect", at = @At("HEAD"))
	private void bananastack$leaveStack(CallbackInfo ci) {
		Stacks.leave((ServerPlayer) (Object) this, Stacks.Reason.LEFT_GAME);
	}

	/** Covers /tp, portals, ender pearls and other mods' teleports: riders are never carried off to someone else's home. */
	@Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"))
	private void bananastack$dropRidersBeforeTeleport(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		Vec3 to = transition.position();
		Vec3 target = new Vec3(
			transition.relatives().contains(Relative.X) ? self.getX() + to.x : to.x,
			transition.relatives().contains(Relative.Y) ? self.getY() + to.y : to.y,
			transition.relatives().contains(Relative.Z) ? self.getZ() + to.z : to.z
		);
		Stacks.beforeTeleport(self, transition.newLevel(), target);
	}

	@Inject(method = "teleportTo(DDD)V", at = @At("HEAD"))
	private void bananastack$dropRidersBeforeTeleportTo(double x, double y, double z, CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		Stacks.beforeTeleport(self, self.level(), new Vec3(x, y, z));
	}

	@Inject(method = "teleportRelative(DDD)V", at = @At("HEAD"))
	private void bananastack$dropRidersBeforeTeleportRelative(double dx, double dy, double dz, CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		Stacks.beforeTeleport(self, self.level(), self.position().add(dx, dy, dz));
	}
}
