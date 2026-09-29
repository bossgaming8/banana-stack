package dev.bhored.bananastack;

import dev.bhored.bananastack.compat.Vanish;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/** Right-clicking a player: hop on them, or on top of the stack they're part of. */
final class Mounting {
	/** A held right-click repeats every few ticks; don't hop straight on to someone else. */
	private static final int HOP_COOLDOWN_TICKS = 10;
	/** Holding right-click on someone who can't be ridden would repeat the reason over and over. */
	private static final int REFUSAL_COOLDOWN_TICKS = 30;

	private static final Map<UUID, Long> lastHop = new HashMap<>();
	private static final Map<UUID, Long> lastRefusal = new HashMap<>();
	/** Players who have already been told this session how to shake riders off or turn riding off. */
	private static final Set<UUID> toldHowToStop = new HashSet<>();

	enum Refusal {
		TURNED_OFF,
		NOT_ALLOWED,
		NOT_BEHIND,
		BUSY,
		FULL,
		NO_ROOM
	}

	private Mounting() {
	}

	static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		// The client always passes, so the click reaches the server whether or not the client has the mod.
		if (level.isClientSide() || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer rider) || !(entity instanceof ServerPlayer clicked) || rider == clicked) {
			return InteractionResult.PASS;
		}
		StackConfig config = StackConfig.get();
		if (!config.enabled || rider.isSpectator() || rider.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		if (config.requireEmptyHand && !rider.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!Stacks.canCarry(rider) || rider.isFallFlying() || !Stacks.canCarry(clicked) || recently(lastHop, rider, HOP_COOLDOWN_TICKS)) {
			return InteractionResult.PASS;
		}

		ServerPlayer bottom = Stacks.bottomOf(clicked);
		if (bottom == Stacks.bottomOf(rider)) {
			// Already in this stack.
			return InteractionResult.PASS;
		}
		ServerPlayer top = Stacks.topOf(bottom);
		Refusal refusal = check(rider, clicked, bottom, top, config);
		if (refusal == null && !Stacks.mount(rider, top)) {
			refusal = Refusal.NO_ROOM;
		}
		if (refusal != null) {
			if (!recently(lastRefusal, rider, REFUSAL_COOLDOWN_TICKS)) {
				lastRefusal.put(rider.getUUID(), rider.level().getGameTime());
				Messages.refused(rider, refusal, refusal == Refusal.TURNED_OFF ? top : clicked, config.maxStackSize);
			}
			return InteractionResult.PASS;
		}

		lastHop.put(rider.getUUID(), rider.level().getGameTime());
		int size = Stacks.size(bottom);
		Messages.mounted(rider, top, bottom, size);
		if (toldHowToStop.add(top.getUUID())) {
			Messages.howToStop(top);
		}
		if (config.sounds) {
			// The pop gets higher as the stack grows.
			float pitch = Math.min(2.0F, 0.9F + 0.12F * size);
			rider.level().playSound(null, rider.getX(), rider.getY() + 1.0, rider.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.7F, pitch);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	private static @Nullable Refusal check(ServerPlayer rider, ServerPlayer clicked, ServerPlayer bottom, ServerPlayer top, StackConfig config) {
		if (Vanish.isVanished(rider) || Vanish.isVanished(clicked) || Vanish.isVanished(bottom)) {
			return Refusal.BUSY;
		}
		if (!Permissions.check(rider, "bananastack.ride", true)) {
			return Refusal.NOT_ALLOWED;
		}
		if (!RidingPreference.allows(top) || !Permissions.check(top, "bananastack.carry", true)) {
			return Refusal.TURNED_OFF;
		}
		// Holding sneak is how the bottom player shakes people off, so it also means "not now".
		if (bottom.isShiftKeyDown() || clicked.isShiftKeyDown() || bottom.isFallFlying() || !Stacks.canCarry(bottom) || !Stacks.canCarry(top)) {
			return Refusal.BUSY;
		}
		if (config.behindAngle < 180.0F && Mth.degreesDifferenceAbs(rider.getYRot(), clicked.getYRot()) > config.behindAngle) {
			return Refusal.NOT_BEHIND;
		}
		// Anyone the rider carries comes along, so they count too.
		int size = Stacks.size(bottom) + 1 + Stacks.ridersAbove(rider).size();
		if (config.maxStackSize > 0 && size > config.maxStackSize) {
			return Refusal.FULL;
		}
		return null;
	}

	private static boolean recently(Map<UUID, Long> times, ServerPlayer player, int ticks) {
		Long last = times.get(player.getUUID());
		return last != null && player.level().getGameTime() - last < ticks && player.level().getGameTime() >= last;
	}

	static void tick(MinecraftServer server) {
		if (server.getTickCount() % 1200 == 0) {
			// Forget about players who left.
			lastHop.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
			lastRefusal.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
			toldHowToStop.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		}
	}
}
