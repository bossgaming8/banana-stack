package dev.bhored.bananastack.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerEntity.class)
abstract class ServerEntityMixin {
	@Shadow
	@Final
	private Entity entity;

	/**
	 * Passenger changes only go to the players watching an entity, never to the entity itself: in vanilla a player is
	 * never anybody's vehicle. A carrier's own client has to know who sits on them, or it can't place them.
	 */
	@WrapOperation(
		method = "sendChanges",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerEntity$Synchronizer;sendToTrackingPlayers(Lnet/minecraft/network/protocol/Packet;)V")
	)
	private void bananastack$tellCarriersWhoIsOnThem(ServerEntity.Synchronizer synchronizer, Packet<? super ClientGamePacketListener> packet, Operation<Void> original) {
		if (packet instanceof ClientboundSetPassengersPacket && this.entity instanceof ServerPlayer) {
			synchronizer.sendToTrackingPlayersAndSelf(packet);
		} else {
			original.call(synchronizer, packet);
		}
	}
}
