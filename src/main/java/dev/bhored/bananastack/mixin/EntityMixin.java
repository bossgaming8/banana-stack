package dev.bhored.bananastack.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import dev.bhored.bananastack.Seat;
import dev.bhored.bananastack.Stacks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class EntityMixin {
	/** The server refuses to let anything ride a player, because players aren't saved like other entities. Seats never are either. */
	@ModifyExpressionValue(
		method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z")
	)
	private boolean bananastack$letSeatsRidePlayers(boolean serializable) {
		return serializable || (Object) this instanceof Seat;
	}

	/**
	 * Vanilla treats a player's vehicle with exactly one player on it as that player's mount: it saves it with them and
	 * removes it from the world when they log out. For a stack, that "mount" would be the player at the bottom.
	 */
	@Inject(method = "hasExactlyOnePlayerPassenger", at = @At("HEAD"), cancellable = true)
	private void bananastack$stacksAreNotMounts(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player player && player.hasPassenger(passenger -> passenger instanceof Seat)) {
			cir.setReturnValue(false);
		}
	}

	/** If the server stops without warning, riders come back on the ground next to the stack instead of in mid-air. */
	@SuppressWarnings("unchecked")
	@WrapOperation(
		method = "saveWithoutId",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/ValueOutput;store(Ljava/lang/String;Lcom/mojang/serialization/Codec;Ljava/lang/Object;)V")
	)
	private <T> void bananastack$saveRidersOnTheGround(ValueOutput output, String name, Codec<T> codec, T value, Operation<Void> original) {
		if ("Pos".equals(name) && (Object) this instanceof ServerPlayer player && Stacks.isRiding(player)) {
			value = (T) Stacks.groundPosition(player);
		}
		original.call(output, name, codec, value);
	}
}
