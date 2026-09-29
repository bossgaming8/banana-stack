package dev.bhored.bananastack.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;

/**
 * Drives a real client: right-clicks a player from behind, hops off with sneak, then carries two players and checks
 * the crosshair still reaches the block in front. Screenshots land in build/run/clientGameTest/screenshots.
 */
public final class StackClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (Boolean.getBoolean("bananastack.showcase")) {
			return;
		}
		TestInput input = context.getInput();
		input.resizeWindow(1280, 720);
		context.runOnClient(minecraft -> minecraft.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamemode survival @a");
			input.lookAt(0.0F, 0.0F);
			context.waitTicks(5);

			// Someone to ride, just in front and facing the same way, so we're behind them.
			world.getServer().runOnServer(server -> {
				ServerPlayer me = host(server);
				TestPlayers.join(me.level(), "Sandwich", me.position().add(0.0, 0.0, 1.6), 0.0F);
			});
			context.waitTicks(20);

			input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(10);
			check(context.computeOnClient(minecraft -> minecraft.player.getVehicle() instanceof Interaction seat
				&& seat.getVehicle() instanceof Player carrier && carrier.getPlainTextName().equals("Sandwich")), "right-clicking Sandwich from behind should put us on their head");
			check(context.computeOnClient(minecraft -> !(minecraft.player.getVehicle() instanceof LivingEntity)), "the HUD keeps our own food bar instead of the carrier's hearts");
			context.takeScreenshot("riding-first-person");
			thirdPerson(context, "riding-third-person");

			input.holdKeyFor(options -> options.keyShift, 4);
			context.waitTicks(10);
			check(context.computeOnClient(minecraft -> minecraft.player.getVehicle() == null), "sneaking should hop us off");
			check(context.computeOnClient(minecraft -> minecraft.player.onGround()), "hopping off lands on the ground");

			// Now two players climb on us, and something to look at straight ahead.
			world.getServer().runOnServer(server -> {
				ServerPlayer me = host(server);
				ServerPlayer sandwich = server.getPlayerList().getPlayerByName("Sandwich");
				TestPlayers.moveBehind(sandwich, me, 1.0);
				ServerPlayer toast = TestPlayers.join(me.level(), "Toast", me.position().add(0.0, 0.0, -1.2), 0.0F);
				TestPlayers.click(sandwich, me);
				TestPlayers.click(toast, me);
				me.level().setBlockAndUpdate(me.blockPosition().offset(0, 1, 3), Blocks.STONE.defaultBlockState());
			});
			context.waitTicks(20);
			check(context.computeOnClient(minecraft -> riders(minecraft.player).size()), 2, "two players should be stacked on us");
			check(context.computeOnClient(minecraft -> riders(minecraft.player).getFirst().getBoundingBox().minY > minecraft.player.getEyeY()),
				"the rider's hitbox must be above our eyes on the client");
			check(context.computeOnClient(minecraft -> minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK),
				"with riders on our head the crosshair should still reach the block in front");
			context.takeScreenshot("carrying-first-person");
			thirdPerson(context, "carrying-third-person");

			input.holdKeyFor(options -> options.keyShift, 4);
			context.waitTicks(10);
			check(context.computeOnClient(minecraft -> riders(minecraft.player).isEmpty()), "sneaking at the bottom should shake everyone off");
			check(context.computeOnClient(minecraft -> minecraft.level.getEntitiesOfClass(Interaction.class, minecraft.player.getBoundingBox().inflate(8.0)).isEmpty()),
				"no seats should be left behind on the client");
			thirdPerson(context, "shaken-off");

			world.getServer().runOnServer(server -> {
				TestPlayers.leave(server.getPlayerList().getPlayerByName("Toast"), server.getPlayerList().getPlayerByName("Sandwich"));
			});
			context.waitTicks(5);
		}
	}

	private static ServerPlayer host(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** Players riding above {@code player} as the client sees them, lowest first. */
	private static List<Entity> riders(Entity player) {
		List<Entity> riders = new ArrayList<>();
		Entity carrier = player;
		while (true) {
			Entity next = carrier.getPassengers().stream()
				.filter(Interaction.class::isInstance)
				.flatMap(seat -> seat.getPassengers().stream())
				.filter(Player.class::isInstance)
				.findFirst().orElse(null);
			if (next == null) {
				return riders;
			}
			riders.add(next);
			carrier = next;
		}
	}

	private static void thirdPerson(ClientGameTestContext context, String name) {
		context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(3);
		context.takeScreenshot(name);
		context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(1);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void check(int actual, int expected, String message) {
		if (actual != expected) {
			throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
		}
	}
}
