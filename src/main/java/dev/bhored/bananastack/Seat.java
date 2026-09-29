package dev.bhored.bananastack;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The invisible seat between two players in a stack. It rides the player below and the player above rides it.
 *
 * <p>Vanilla would put a rider's feet 0.6 blocks below the top of the carrier's head, which leaves the carrier's eyes
 * inside the rider's hitbox: every click would land on the rider, so the carrier couldn't mine, build or fight. The
 * seat is a zero-width interaction entity whose height lifts the rider just clear of the carrier's eyes. Clients see a
 * plain interaction entity, so nothing needs to be installed on them.
 */
public final class Seat extends Interaction {
	/** How far the rider's feet stay above the carrier's eyes. */
	private static final double EYE_CLEARANCE = 0.03;
	/** Lowering happens at once (a ceiling is coming); rising back up is eased over a few ticks. */
	private static final float RISE_PER_TICK = 0.12F;

	private float lift;

	public Seat(Level level) {
		super(EntityTypes.INTERACTION, level);
		this.setWidth(0.0F);
		this.setHeight(0.0F);
	}

	public float lift() {
		return this.lift;
	}

	void setLift(float lift) {
		this.lift = lift;
		if (Math.abs(this.getHeight() - lift) > 1.0E-4F) {
			this.setHeight(lift);
		}
	}

	void easeLiftTo(float target) {
		this.setLift(target < this.lift ? target : Math.min(target, this.lift + RISE_PER_TICK));
	}

	/** Height of the seat's base above the carrier's feet. */
	double baseOffset(Entity carrier) {
		return carrier.getPassengerRidingPosition(this).y - carrier.getY() - this.getVehicleAttachmentPoint(carrier).y;
	}

	/** Feet of the rider above the carrier's feet when the seat has no lift at all. */
	double riderFeetOffset(Entity carrier, Entity rider) {
		return this.baseOffset(carrier) - rider.getVehicleAttachmentPoint(this).y;
	}

	/** The lift that keeps the carrier's eyes out of the rider's hitbox. */
	double openLift(Entity carrier, Entity rider) {
		return Mth.clamp(carrier.getEyeHeight() + EYE_CLEARANCE - this.riderFeetOffset(carrier, rider), 0.0, 1.5);
	}

	@Override
	public void tick() {
		// A seat nobody sits on, or one that fell off its carrier, has no reason to exist.
		if (!this.level().isClientSide() && (!(this.getVehicle() instanceof Player) || Stacks.riderOf(this) == null)) {
			this.discard();
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!this.level().isClientSide() && passenger instanceof ServerPlayer rider) {
			Stacks.onRiderLeft(rider, this);
		}
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return Landing.find(passenger, this.getRootVehicle(), 0);
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean hasExactlyOnePlayerPassenger() {
		return false;
	}
}
