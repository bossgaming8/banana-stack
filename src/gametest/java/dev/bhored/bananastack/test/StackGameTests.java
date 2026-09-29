package dev.bhored.bananastack.test;

import static dev.bhored.bananastack.test.TestPlayers.click;
import static dev.bhored.bananastack.test.TestPlayers.join;
import static dev.bhored.bananastack.test.TestPlayers.leave;
import static dev.bhored.bananastack.test.TestPlayers.sneak;

import dev.bhored.bananastack.RidingPreference;
import dev.bhored.bananastack.Seat;
import dev.bhored.bananastack.StackConfig;
import dev.bhored.bananastack.Stacks;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side behaviour, driven through the real packet handlers. Players face +z (yaw 0); "behind" someone at z = 4 is
 * z = 3.
 */
public final class StackGameTests {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static void assertRiding(GameTestHelper helper, ServerPlayer rider, ServerPlayer carrier) {
		helper.assertTrue(Stacks.carrierOf(rider) == carrier, rider.getPlainTextName() + " should be riding " + carrier.getPlainTextName()
			+ " but rides " + (rider.getVehicle() == null ? "nothing" : rider.getVehicle().getVehicle()));
	}

	private static void assertOnFoot(GameTestHelper helper, ServerPlayer player) {
		helper.assertFalse(player.isPassenger(), player.getPlainTextName() + " should be off the stack");
	}

	private static void assertStack(GameTestHelper helper, ServerPlayer bottom, ServerPlayer... riders) {
		helper.assertValueEqual(Stacks.ridersAbove(bottom), List.of(riders), "riders above " + bottom.getPlainTextName());
	}

	private static void assertNoSeatsLeft(GameTestHelper helper) {
		helper.assertTrue(helper.getLevel().getEntities((Entity) null, helper.getBounds().inflate(8.0), entity -> entity instanceof Seat && !entity.isRemoved()).isEmpty(),
			"no seats should be left in the world");
	}

	/** Lands on the floor (y = 1 above the test origin), not somewhere up in the air. */
	private static void assertOnTheGround(GameTestHelper helper, ServerPlayer player) {
		double y = helper.relativeVec(player.position()).y;
		helper.assertTrue(Math.abs(y - 1.0) < 0.01, player.getPlainTextName() + " should land on the floor, but is at relative y " + y);
	}

	@GameTest(skyAccess = true)
	public void ridesFromBehind(GameTestHelper helper) {
		floor(helper);
		ServerPlayer carrier = join(helper, "ridesCarrier", 4.5, 4.5, 0.0F);
		ServerPlayer rider = join(helper, "ridesRider", 4.5, 3.5, 0.0F);
		click(rider, carrier);
		assertRiding(helper, rider, carrier);
		helper.assertTrue(rider.getVehicle() instanceof Seat, "the rider sits on a seat");
		// The whole point of the seat: the carrier's eyes are outside the rider's hitbox, so the carrier can still aim.
		helper.assertTrue(rider.getBoundingBox().minY > carrier.getEyeY(), "the rider's hitbox must start above the carrier's eyes, but starts at "
			+ (rider.getBoundingBox().minY - carrier.getY()) + " for eyes at " + carrier.getEyeHeight());
		helper.assertTrue(rider.getBoundingBox().minY - carrier.getEyeY() < 0.1, "the rider shouldn't float far above the carrier");
		leave(rider, carrier);
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void refusesFromTheFront(GameTestHelper helper) {
		floor(helper);
		ServerPlayer carrier = join(helper, "frontCarrier", 4.5, 4.5, 0.0F);
		ServerPlayer rider = join(helper, "frontRider", 4.5, 5.5, 180.0F);
		click(rider, carrier);
		assertOnFoot(helper, rider);

		// A server that sets the angle to 180 lets people hop on from any side.
		StackConfig config = StackConfig.get();
		float before = config.behindAngle;
		config.behindAngle = 180.0F;
		try {
			click(rider, carrier);
		} finally {
			config.behindAngle = before;
		}
		assertRiding(helper, rider, carrier);
		leave(rider, carrier);
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void needsAnEmptyHand(GameTestHelper helper) {
		floor(helper);
		ServerPlayer carrier = join(helper, "handCarrier", 4.5, 4.5, 0.0F);
		ServerPlayer rider = join(helper, "handRider", 4.5, 3.5, 0.0F);
		rider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
		click(rider, carrier);
		assertOnFoot(helper, rider);
		rider.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		rider.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		click(rider, carrier);
		assertRiding(helper, rider, carrier);
		leave(rider, carrier);
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void sneakClickDoesNotMount(GameTestHelper helper) {
		floor(helper);
		ServerPlayer carrier = join(helper, "sneakClickCarrier", 4.5, 4.5, 0.0F);
		ServerPlayer rider = join(helper, "sneakClickRider", 4.5, 3.5, 0.0F);
		sneak(rider, true);
		click(rider, carrier);
		assertOnFoot(helper, rider);
		leave(rider, carrier);
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void clickingTheBottomClimbsToTheTop(GameTestHelper helper) {
		floor(helper);
		ServerPlayer bottom = join(helper, "climbBottom", 4.5, 4.5, 0.0F);
		ServerPlayer first = join(helper, "climbFirst", 4.5, 3.5, 0.0F);
		ServerPlayer second = join(helper, "climbSecond", 4.5, 3.2, 0.0F);
		ServerPlayer third = join(helper, "climbThird", 4.2, 3.4, 10.0F);
		click(first, bottom);
		click(second, bottom);
		click(third, bottom);
		assertStack(helper, bottom, first, second, third);
		helper.assertValueEqual(Stacks.size(third), 4, "stack size");
		// Clicking someone in your own stack does nothing.
		click(bottom, first);
		helper.assertFalse(bottom.isPassenger(), "the bottom player can't climb their own stack");
		// Every rider can still aim past the one above.
		List<ServerPlayer> column = List.of(bottom, first, second, third);
		for (int i = 1; i < column.size(); i++) {
			helper.assertTrue(column.get(i).getBoundingBox().minY > column.get(i - 1).getEyeY(), column.get(i - 1).getPlainTextName() + "'s eyes are inside the rider's hitbox");
		}
		leave(third, second, first, bottom);
		helper.succeed();
	}

	@GameTest(skyAccess = true, maxTicks = 60)
	public void bottomSneakingShakesEveryoneOff(GameTestHelper helper) {
		floor(helper);
		ServerPlayer bottom = join(helper, "shakeBottom", 4.5, 4.5, 0.0F);
		ServerPlayer first = join(helper, "shakeFirst", 4.5, 3.5, 0.0F);
		ServerPlayer second = join(helper, "shakeSecond", 4.5, 3.5, 0.0F);
		click(first, bottom);
		click(second, bottom);
		assertStack(helper, bottom, first, second);
		sneak(bottom, true);
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				assertOnFoot(helper, first);
				assertOnFoot(helper, second);
				assertOnTheGround(helper, first);
				assertOnTheGround(helper, second);
				helper.assertTrue(first.position().distanceTo(second.position()) > 0.3, "riders dropped together land in different spots");
				helper.assertTrue(first.position().distanceTo(bottom.position()) < 1.6, "riders land next to the bottom player");
				assertNoSeatsLeft(helper);
			})
			// Holding sneak also means nobody can climb back on (after the short hop cooldown).
			.thenExecuteAfter(12, () -> {
				click(first, bottom);
				assertOnFoot(helper, first);
				sneak(bottom, false);
				click(first, bottom);
				assertRiding(helper, first, bottom);
				leave(second, first, bottom);
			})
			.thenSucceed();
	}

	@GameTest(skyAccess = true, maxTicks = 40)
	public void middleRiderLeavingClosesTheGap(GameTestHelper helper) {
		floor(helper);
		ServerPlayer bottom = join(helper, "gapBottom", 4.5, 4.5, 0.0F);
		ServerPlayer middle = join(helper, "gapMiddle", 4.5, 3.5, 0.0F);
		ServerPlayer top = join(helper, "gapTop", 4.5, 3.5, 0.0F);
		click(middle, bottom);
		click(top, bottom);
		sneak(middle, true);
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				assertOnFoot(helper, middle);
				assertOnTheGround(helper, middle);
				assertStack(helper, bottom, top);
				helper.assertTrue(top.getBoundingBox().minY > bottom.getEyeY(), "the rider that slid down sits at the normal height");
				leave(top, middle, bottom);
			})
			.thenExecuteAfter(2, () -> assertNoSeatsLeft(helper))
			.thenSucceed();
	}

	@GameTest(skyAccess = true, maxTicks = 40)
	public void topRiderHopsOff(GameTestHelper helper) {
		floor(helper);
		ServerPlayer bottom = join(helper, "hopBottom", 4.5, 4.5, 0.0F);
		ServerPlayer middle = join(helper, "hopMiddle", 4.5, 3.5, 0.0F);
		ServerPlayer top = join(helper, "hopTop", 4.5, 3.5, 0.0F);
		click(middle, bottom);
		click(top, bottom);
		sneak(top, true);
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				assertOnFoot(helper, top);
				// Hopping off the top of a tall stack lands on the ground, not a long fall from where they sat.
				assertOnTheGround(helper, top);
				assertStack(helper, bottom, middle);
				leave(top, middle, bottom);
			})
			.thenSucceed();
	}

	@GameTest(skyAccess = true, maxTicks = 40)
	public void riderLoggingOutLeavesTheCarrierAlone(GameTestHelper helper) {
		floor(helper);
		ServerPlayer carrier = join(helper, "logoutCarrier", 4.5, 4.5, 0.0F);
		ServerPlayer rider = join(helper, "logoutRider", 4.5, 3.5, 0.0F);
		click(rider, carrier);
		// Vanilla would treat the carrier as the rider's mount and take it out of the world with them.
		leave(rider);
		helper.assertFalse(carrier.isRemoved(), "the carrier must stay in the world when their rider logs out");
		helper.assertTrue(helper.getLevel().getServer().getPlayerList().getPlayer(carrier.getUUID()) == carrier, "the carrier must stay online");
		helper.assertFalse(Stacks.isCarrying(carrier), "the carrier no longer carries anyone");
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				assertNoSeatsLeft(helper);
				helper.assertFalse(carrier.isRemoved(), "the carrier is still in the world a tick later");
				leave(carrier);
			})
			.thenSucceed();
	}

	@GameTest(skyAccess = true)
	public void bottomLoggingOutDropsEveryone(GameTestHelper helper) {
		floor(helper);
		ServerPlayer bottom = join(helper, "logoutBottom", 4.5, 4.5, 0.0F);
		ServerPlayer first = join(helper, "logoutFirst", 4.5, 3.5, 0.0F);
		ServerPlayer second = join(helper, "logoutSecond", 4.5, 3.5, 0.0F);
		click(first, bottom);
		click(second, bottom);
		leave(bottom);
		assertOnFoot(helper, first);
		assertOnFoot(helper, second);
		assertOnTheGround(helper, first);
		assertOnTheGround(helper, second);
		leave(second, first);
		helper.succeed();
	}

	@GameTest(skyAccess = true, maxTicks = 40)
	public void teleportingCarrierGoesAlone(GameTestHelper helper) {
		floor(helper);
		ServerPlayer carrier = join(helper, "tpCarrier", 4.5, 4.5, 0.0F);
		ServerPlayer rider = join(helper, "tpRider", 4.5, 3.5, 0.0F);
		click(rider, carrier);
		Vec3 start = carrier.position();
		carrier.teleportTo(helper.getLevel(), start.x + 40.0, start.y, start.z, Set.of(), 0.0F, 0.0F, true);
		assertOnFoot(helper, rider);
		helper.assertTrue(rider.position().distanceTo(start) < 2.0, "the rider stays behind instead of being carried off");
		carrier.teleportTo(helper.getLevel(), start.x, start.y, start.z, Set.of(), 0.0F, 0.0F, true);
		TestPlayers.moveTo(helper, rider, 4.5, 3.5, 0.0F);
		helper.startSequence()
			.thenExecuteAfter(12, () -> {
				// A short hop doesn't count: some mods nudge players back into place with a tiny teleport.
				click(rider, carrier);
				assertRiding(helper, rider, carrier);
				carrier.teleportTo(start.x + 0.2, start.y, start.z);
				assertRiding(helper, rider, carrier);
				leave(rider, carrier);
			})
			.thenSucceed();
	}

	@GameTest(skyAccess = true)
	public void teleportingRiderLeavesTheStack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer bottom = join(helper, "tpBottom", 4.5, 4.5, 0.0F);
		ServerPlayer middle = join(helper, "tpMiddle", 4.5, 3.5, 0.0F);
		ServerPlayer top = join(helper, "tpTop", 4.5, 3.5, 0.0F);
		click(middle, bottom);
		click(top, bottom);
		Vec3 start = middle.position();
		middle.teleportTo(helper.getLevel(), start.x + 30.0, start.y, start.z, Set.of(), 0.0F, 0.0F, true);
		assertOnFoot(helper, middle);
		assertStack(helper, bottom, top);
		leave(top, middle, bottom);
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void ridingCanBeTurnedOff(GameTestHelper helper) {
		floor(helper);
		ServerPlayer carrier = join(helper, "offCarrier", 4.5, 4.5, 0.0F);
		ServerPlayer rider = join(helper, "offRider", 4.5, 3.5, 0.0F);
		RidingPreference.set(carrier, false);
		click(rider, carrier);
		assertOnFoot(helper, rider);
		RidingPreference.set(carrier, true);
		click(rider, carrier);
		assertRiding(helper, rider, carrier);

		// Turning it off with the command drops whoever is already up there.
		helper.getLevel().getServer().getCommands().performPrefixedCommand(carrier.createCommandSourceStack(), "stack off");
		assertOnFoot(helper, rider);
		helper.assertFalse(RidingPreference.allows(carrier), "the preference is saved");
		helper.getLevel().getServer().getCommands().performPrefixedCommand(carrier.createCommandSourceStack(), "bananastack toggle");
		helper.assertTrue(RidingPreference.allows(carrier), "toggle turns it back on");
		leave(rider, carrier);
		helper.succeed();
	}

	@GameTest(skyAccess = true, maxTicks = 40)
	public void seatsDuckUnderLowCeilings(GameTestHelper helper) {
		floor(helper);
		// A three-block-high room: one rider fits sitting right on the head, but not at the usual height.
		for (int x = 3; x <= 5; x++) {
			for (int z = 3; z <= 5; z++) {
				helper.setBlock(x, 4, z, Blocks.OAK_PLANKS);
			}
		}
		ServerPlayer carrier = join(helper, "ceilingCarrier", 4.5, 4.5, 0.0F);
		ServerPlayer rider = join(helper, "ceilingRider", 4.5, 3.5, 0.0F);
		click(rider, carrier);
		assertRiding(helper, rider, carrier);
		Seat seat = (Seat) rider.getVehicle();
		helper.assertTrue(seat.lift() < 0.01F, "the seat drops down under the ceiling, lift " + seat.lift());
		helper.assertTrue(helper.getLevel().noBlockCollision(rider, rider.getBoundingBox().deflate(1.0E-3)), "the rider fits under the ceiling");

		// Out in the open the seat rises back to its normal height over a few ticks.
		TestPlayers.moveTo(helper, carrier, 1.5, 1.5, 0.0F);
		helper.startSequence()
			.thenExecuteAfter(8, () -> {
				helper.assertTrue(seat.lift() > 0.4F, "the seat rises again in the open, lift " + seat.lift());
				// Walking under a block at head height knocks the rider off.
				helper.setBlock(1, 3, 1, Blocks.OAK_PLANKS);
			})
			.thenExecuteAfter(2, () -> {
				assertOnFoot(helper, rider);
				leave(rider, carrier);
			})
			.thenSucceed();
	}

	@GameTest(skyAccess = true)
	public void noRoomMeansNoRide(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(4, 3, 4, Blocks.OAK_PLANKS);
		ServerPlayer carrier = join(helper, "roomCarrier", 4.5, 4.5, 0.0F);
		ServerPlayer rider = join(helper, "roomRider", 4.5, 3.5, 0.0F);
		click(rider, carrier);
		assertOnFoot(helper, rider);
		assertNoSeatsLeft(helper);
		leave(rider, carrier);
		helper.succeed();
	}

	@GameTest(skyAccess = true, maxTicks = 40)
	public void carrierDyingOrSpectatingDropsRiders(GameTestHelper helper) {
		floor(helper);
		ServerPlayer carrier = join(helper, "deathCarrier", 4.5, 4.5, 0.0F);
		ServerPlayer rider = join(helper, "deathRider", 4.5, 3.5, 0.0F);
		ServerPlayer other = join(helper, "spectateCarrier", 1.5, 4.5, 0.0F);
		ServerPlayer otherRider = join(helper, "spectateRider", 1.5, 3.5, 0.0F);
		click(rider, carrier);
		click(otherRider, other);
		carrier.kill(helper.getLevel());
		other.setGameMode(GameType.SPECTATOR);
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				assertOnFoot(helper, rider);
				assertOnFoot(helper, otherRider);
				leave(rider, carrier, other, otherRider);
			})
			.thenSucceed();
	}

	@GameTest(skyAccess = true)
	public void sneakingOrFullStacksCantBeClimbed(GameTestHelper helper) {
		floor(helper);
		ServerPlayer carrier = join(helper, "fullCarrier", 4.5, 4.5, 0.0F);
		ServerPlayer first = join(helper, "fullFirst", 4.5, 3.5, 0.0F);
		ServerPlayer second = join(helper, "fullSecond", 4.5, 3.5, 0.0F);
		sneak(carrier, true);
		click(first, carrier);
		assertOnFoot(helper, first);
		sneak(carrier, false);

		StackConfig config = StackConfig.get();
		int before = config.maxStackSize;
		config.maxStackSize = 2;
		try {
			click(first, carrier);
			click(second, carrier);
		} finally {
			config.maxStackSize = before;
		}
		assertStack(helper, carrier, first);
		assertOnFoot(helper, second);
		leave(second, first, carrier);
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void stacksMerge(GameTestHelper helper) {
		floor(helper);
		ServerPlayer bottom = join(helper, "mergeBottom", 4.5, 4.5, 0.0F);
		ServerPlayer rider = join(helper, "mergeRider", 4.5, 3.5, 0.0F);
		ServerPlayer carrier = join(helper, "mergeCarrier", 4.5, 3.0, 0.0F);
		ServerPlayer passenger = join(helper, "mergePassenger", 4.5, 2.0, 0.0F);
		click(rider, bottom);
		click(passenger, carrier);
		// Someone already carrying a rider climbs another stack and brings their rider along.
		click(carrier, bottom);
		assertStack(helper, bottom, rider, carrier, passenger);
		leave(passenger, carrier, rider, bottom);
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void ridersAreSavedOnTheGround(GameTestHelper helper) {
		floor(helper);
		ServerPlayer carrier = join(helper, "saveCarrier", 4.5, 4.5, 0.0F);
		ServerPlayer rider = join(helper, "saveRider", 4.5, 3.5, 0.0F);
		click(rider, carrier);
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		rider.saveWithoutId(output);
		Vec3 saved = output.buildResult().read("Pos", Vec3.CODEC).orElseThrow();
		helper.assertTrue(saved.distanceTo(carrier.position()) < 0.01, "the rider is saved where the stack stands, not in mid-air: " + saved);
		helper.assertFalse(output.buildResult().contains("RootVehicle"), "the carrier must never be saved as the rider's mount");
		helper.assertFalse(carrier.hasExactlyOnePlayerPassenger(), "a stack is not a mount");
		leave(rider, carrier);
		helper.succeed();
	}

	@GameTest(skyAccess = true, maxTicks = 40)
	public void leftoverSeatsCleanThemselvesUp(GameTestHelper helper) {
		floor(helper);
		ServerPlayer carrier = join(helper, "orphanCarrier", 4.5, 4.5, 0.0F);
		ServerPlayer rider = join(helper, "orphanRider", 4.5, 3.5, 0.0F);
		click(rider, carrier);
		Seat seat = (Seat) rider.getVehicle();
		helper.assertFalse(seat.shouldBeSaved(), "seats are never written to disk");
		// Something outside Banana Stack takes the rider off without going through it.
		rider.removeVehicle();
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				helper.assertTrue(seat.isRemoved(), "an empty seat removes itself");
				leave(rider, carrier);
			})
			.thenSucceed();
	}
}
