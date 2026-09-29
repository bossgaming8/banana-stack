package dev.bhored.bananastack;

import dev.bhored.bananastack.compat.Vanish;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A stack is a column of players: the one at the bottom carries a {@link Seat}, a player rides that seat, carries the
 * next seat, and so on. The entities themselves are the only state, so nothing can drift out of sync with the world.
 */
public final class Stacks {
	/** Guards against loops; no real stack gets anywhere near this tall. */
	private static final int MAX_HEIGHT = 256;

	/** Why riders came off, which decides what they're told. */
	public enum Reason {
		SHAKEN_OFF,
		BUMPED,
		TURNED_OFF,
		TELEPORTED,
		LEFT_GAME,
		DIED,
		UNAVAILABLE
	}

	/** Set while this class rearranges a stack, so the seat callbacks it triggers don't rearrange it again. */
	private static int rearranging;

	private Stacks() {
	}

	// ---------------------------------------------------------------- structure

	/** The seat on top of {@code carrier} that someone is sitting on. */
	public static @Nullable Seat seatOn(Entity carrier) {
		for (Entity passenger : carrier.getPassengers()) {
			if (passenger instanceof Seat seat && riderOf(seat) != null) {
				return seat;
			}
		}
		return null;
	}

	public static @Nullable ServerPlayer riderOf(Seat seat) {
		for (Entity passenger : seat.getPassengers()) {
			if (passenger instanceof ServerPlayer player) {
				return player;
			}
		}
		return null;
	}

	/** The player directly above {@code carrier}. */
	public static @Nullable ServerPlayer riderOn(Entity carrier) {
		Seat seat = seatOn(carrier);
		return seat == null ? null : riderOf(seat);
	}

	/** The player directly below {@code rider}. */
	public static @Nullable ServerPlayer carrierOf(Entity rider) {
		return rider.getVehicle() instanceof Seat seat && seat.getVehicle() instanceof ServerPlayer carrier ? carrier : null;
	}

	public static boolean isRiding(Entity player) {
		return carrierOf(player) != null;
	}

	public static boolean isCarrying(Entity player) {
		return riderOn(player) != null;
	}

	public static ServerPlayer bottomOf(ServerPlayer member) {
		ServerPlayer bottom = member;
		for (int i = 0; i < MAX_HEIGHT; i++) {
			ServerPlayer below = carrierOf(bottom);
			if (below == null) {
				break;
			}
			bottom = below;
		}
		return bottom;
	}

	/** Everyone riding above {@code base}, lowest first. */
	public static List<ServerPlayer> ridersAbove(Entity base) {
		List<ServerPlayer> riders = new ArrayList<>();
		Entity carrier = base;
		for (int i = 0; i < MAX_HEIGHT; i++) {
			ServerPlayer rider = riderOn(carrier);
			if (rider == null) {
				break;
			}
			riders.add(rider);
			carrier = rider;
		}
		return riders;
	}

	public static ServerPlayer topOf(ServerPlayer member) {
		List<ServerPlayer> riders = ridersAbove(member);
		return riders.isEmpty() ? member : riders.getLast();
	}

	/** Players in the whole stack {@code member} belongs to, including the bottom one. */
	public static int size(ServerPlayer member) {
		return 1 + ridersAbove(bottomOf(member)).size();
	}

	/** Where a rider should be written to disk: on the ground with the stack rather than up in the air. */
	public static Vec3 groundPosition(ServerPlayer rider) {
		return bottomOf(rider).getRootVehicle().position();
	}

	/** Whether {@code player} is in a state to hold people up. */
	static boolean canCarry(ServerPlayer player) {
		return player.isAlive()
			&& !player.isRemoved()
			&& !player.hasDisconnected()
			&& !player.isSpectator()
			&& !player.isSleeping()
			&& !Vanish.isVanished(player);
	}

	// ---------------------------------------------------------------- changes

	/** Puts {@code rider} on top of {@code top}. Returns false when there is no room above {@code top}'s head. */
	static boolean mount(ServerPlayer rider, ServerPlayer top) {
		ServerLevel level = top.level();
		Seat seat = new Seat(level);
		double feetWithoutLift = top.getY() + seat.riderFeetOffset(top, rider);
		double lift = Landing.headroom(rider, top.getX(), feetWithoutLift, top.getZ(), seat.openLift(top, rider));
		if (lift < 0.0) {
			return false;
		}
		seat.setLift((float) lift);
		seat.snapTo(top.getX(), top.getY() + seat.baseOffset(top), top.getZ(), top.getYRot(), 0.0F);
		if (!level.addFreshEntity(seat)) {
			return false;
		}

		// Leaving a boat, or another stack, goes through the normal path so that stack closes up behind them.
		if (rider.isPassenger()) {
			rider.stopRiding();
		}

		rearranging++;
		try {
			if (seat.startRiding(top, true, false) && rider.startRiding(seat, true, true)) {
				return true;
			}
			if (rider.getVehicle() == seat) {
				rider.removeVehicle();
			}
			seat.discard();
			return false;
		} finally {
			rearranging--;
		}
	}

	/**
	 * Called whenever someone gets off a seat, whether they sneaked, teleported, logged out or were dropped. Anyone
	 * sitting above them slides down onto the player below, so leaving the middle of a stack never drags people
	 * along or strands them in the air.
	 */
	static void onRiderLeft(ServerPlayer rider, Seat seat) {
		if (rearranging > 0) {
			return;
		}
		Seat above = seatOn(rider);
		if (above == null) {
			return;
		}
		rearranging++;
		try {
			if (seat.getVehicle() instanceof ServerPlayer carrier && canCarry(carrier) && carrier.level() == above.level()) {
				above.stopRiding();
				if (above.startRiding(carrier, true, false)) {
					return;
				}
				above.startRiding(rider, true, false);
			}
			dropEveryoneAbove(rider, Reason.UNAVAILABLE);
		} finally {
			rearranging--;
		}
	}

	/** {@code player} leaves whatever stack they are in: they get off, and anyone they carry comes down too. */
	public static void leave(ServerPlayer player, Reason reason) {
		if (isRiding(player)) {
			player.stopRiding();
		}
		if (isCarrying(player)) {
			dropEveryoneAbove(player, reason);
		}
	}

	/**
	 * {@code player} is about to teleport somewhere else. They go alone: anyone they carry stays behind, and if they
	 * were riding, the players above them slide down like they would if they had hopped off.
	 */
	public static void beforeTeleport(ServerPlayer player, Level destination, Vec3 target) {
		if ((isRiding(player) || isCarrying(player)) && (destination != player.level() || target.distanceToSqr(player.position()) > 1.0)) {
			leave(player, Reason.TELEPORTED);
		}
	}

	/** Everyone above {@code base} gets off and lands around the bottom of the stack. */
	public static void dropEveryoneAbove(ServerPlayer base, Reason reason) {
		drop(ridersAbove(base), 0, base, reason);
	}

	/**
	 * Takes {@code riders[from..]} off the stack, top first so the stack they land around stays put, and places each
	 * one on the ground next to it, spread around so they don't all land in the same spot.
	 */
	private static void drop(List<ServerPlayer> riders, int from, ServerPlayer cause, Reason reason) {
		if (from >= riders.size()) {
			return;
		}
		Entity anchor = bottomOf(riders.get(from)).getRootVehicle();
		List<ServerPlayer> dropped = new ArrayList<>();
		rearranging++;
		try {
			for (int i = riders.size() - 1; i >= from; i--) {
				ServerPlayer rider = riders.get(i);
				Entity seat = rider.getVehicle();
				Vec3 wasAt = rider.position();
				rider.removeVehicle();
				Vec3 landing = Landing.find(rider, anchor, i - from);
				rider.dismountTo(landing.x, landing.y, landing.z);
				if (seat instanceof Seat) {
					seat.discard();
				}
				puff(rider.level(), wasAt.add(0.0, rider.getBbHeight() / 2.0, 0.0));
				dropped.add(rider);
			}
		} finally {
			rearranging--;
		}
		Messages.dropped(dropped, cause, reason);
		if (StackConfig.get().sounds) {
			anchor.level().playSound(null, anchor.getX(), anchor.getY() + 1.0, anchor.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 0.6F);
		}
	}

	private static void puff(Level level, Vec3 at) {
		if (StackConfig.get().particles && level instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 6, 0.25, 0.35, 0.25, 0.02);
		}
	}

	// ---------------------------------------------------------------- every tick

	static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!isRiding(player) && isCarrying(player)) {
				tickStack(player);
			}
		}
		Mounting.tick(server);
	}

	private static void tickStack(ServerPlayer bottom) {
		if (bottom.isShiftKeyDown()) {
			dropEveryoneAbove(bottom, Reason.SHAKEN_OFF);
			return;
		}
		// Also catches riding being switched off in the config while people are stacked.
		if (!canCarry(bottom) || !StackConfig.get().enabled) {
			dropEveryoneAbove(bottom, Reason.UNAVAILABLE);
			return;
		}

		List<ServerPlayer> riders = ridersAbove(bottom);
		for (ServerPlayer rider : riders) {
			if (!canCarry(rider)) {
				// Someone in the stack can't take part any more (vanished, for instance); the rest closes up.
				rider.stopRiding();
				return;
			}
		}

		// Walk up the stack working out where each rider will sit, lowering seats under ceilings and raising them
		// again in the open. A rider with no room even right on the head below bumps off, with everyone above.
		Entity carrier = bottom;
		double carrierFeet = bottom.getY();
		for (int i = 0; i < riders.size(); i++) {
			ServerPlayer rider = riders.get(i);
			if (!(rider.getVehicle() instanceof Seat seat)) {
				return;
			}
			double feetWithoutLift = carrierFeet + seat.riderFeetOffset(carrier, rider);
			double room = Landing.headroom(rider, bottom.getX(), feetWithoutLift, bottom.getZ(), seat.openLift(carrier, rider));
			if (room < 0.0) {
				drop(riders, i, bottom, Reason.BUMPED);
				return;
			}
			seat.easeLiftTo((float) room);
			carrierFeet = feetWithoutLift + seat.lift();
			carrier = rider;
		}
	}
}
