package dev.bhored.bananastack.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * Players with a real server connection (over an in-memory channel), so every click and key press goes through the
 * same packet handlers a real client's would.
 */
public final class TestPlayers {
	private TestPlayers() {
	}

	/** Joins a player standing at {@code (x, z)} on the test floor, facing {@code yaw} (0 faces +z). */
	static ServerPlayer join(GameTestHelper helper, String name, double x, double z, float yaw) {
		ServerPlayer player = join(helper.getLevel(), name);
		moveTo(helper, player, x, z, yaw);
		return player;
	}

	static ServerPlayer join(ServerLevel level, String name, Vec3 at, float yaw) {
		return join(level, name, nameUuid(name), at, yaw);
	}

	/** {@code id} decides the default skin the clients show. */
	static ServerPlayer join(ServerLevel level, String name, UUID id, Vec3 at, float yaw) {
		ServerPlayer player = join(level, name, id);
		player.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		player.setYHeadRot(yaw);
		player.setYBodyRot(yaw);
		player.connection.resetPosition();
		return player;
	}

	private static UUID nameUuid(String name) {
		return UUID.nameUUIDFromBytes(("bananastack-test:" + name).getBytes(StandardCharsets.UTF_8));
	}

	private static ServerPlayer join(ServerLevel level, String name) {
		return join(level, name, nameUuid(name));
	}

	private static ServerPlayer join(ServerLevel level, String name, UUID id) {
		MinecraftServer server = level.getServer();
		GameProfile profile = new GameProfile(id, name);
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		ServerPlayer player = new ServerPlayer(server, level, profile, cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		return player;
	}

	static void moveTo(GameTestHelper helper, ServerPlayer player, double x, double z, float yaw) {
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		player.setYHeadRot(yaw);
		player.setYBodyRot(yaw);
		player.connection.resetPosition();
	}

	/** Puts {@code player} {@code distance} blocks behind {@code target}, facing the same way. */
	static void moveBehind(ServerPlayer player, ServerPlayer target, double distance) {
		Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(distance));
		player.snapTo(behind.x, target.getY(), behind.z, target.getYRot(), 0.0F);
		player.setYHeadRot(target.getYRot());
		player.setYBodyRot(target.getYRot());
		player.connection.resetPosition();
	}

	/** A right-click on {@code target} with the main hand, as the client sends it. */
	static void click(ServerPlayer player, ServerPlayer target) {
		player.connection.handleInteract(new ServerboundInteractPacket(target.getId(), InteractionHand.MAIN_HAND, new Vec3(0.0, 1.0, 0.0), player.isShiftKeyDown()));
		// The vanilla client follows up with the off hand whenever the main hand didn't do anything on its side.
		player.connection.handleInteract(new ServerboundInteractPacket(target.getId(), InteractionHand.OFF_HAND, new Vec3(0.0, 1.0, 0.0), player.isShiftKeyDown()));
	}

	static void sneak(ServerPlayer player, boolean sneaking) {
		player.connection.handlePlayerInput(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, sneaking, false)));
	}

	static void leave(ServerPlayer player) {
		player.connection.onDisconnect(new DisconnectionDetails(Component.literal("Test finished")));
	}

	static void leave(ServerPlayer... players) {
		for (ServerPlayer player : players) {
			if (!player.hasDisconnected()) {
				leave(player);
			}
		}
	}
}
