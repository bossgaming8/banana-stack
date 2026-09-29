package dev.bhored.bananastack.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.impl.client.gametest.context.TestSingleplayerContextImpl;
import net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Films the screenshots and the clip on the Modrinth page, in a copy of the world in showcase/world (the Banana
 * Sandwich hub) with Sodium, Iris and Complementary. Only runs with {@code -Pshowcase}; tools/make_showcase.py turns
 * the frames into the page media.
 */
public final class ShowcaseRecorder implements FabricClientGameTest {
	private static final Path NOTES = Path.of("showcase-notes");
	/** Minecraft picks one of 18 default skins from the UUID's hash; these are the slots of the ones used here. */
	private static final int[] SKINS = {15, 0, 17, 7, 5, 12, 2, 13, 1, 16, 11};
	private static final String[] NAMES = {"Banana", "Peel", "Toast", "Crust", "Honey", "Jam", "Butter", "Crumb", "Bread", "Sugar", "Split"};

	private static int joined;

	/** Spots on the Banana Sandwich hub's plaza (see the "scout" shot), clear of buildings, with the banana to the west. */
	private static final Vec3 TOWER = new Vec3(-28.5, 61.0, 2.5);
	private static final Vec3 PAIR = new Vec3(-24.5, 61.0, 8.5);
	private static final Vec3 WALK_FROM = new Vec3(-28.5, 61.0, -4.5);
	private static final Vec3 WALK_TO = new Vec3(-28.5, 61.0, 6.5);
	private static final Vec3 CLIP_CAMERA = new Vec3(-22.0, 63.2, 0.5);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Boolean.getBoolean("bananastack.showcase")) {
			return;
		}
		context.getInput().resizeWindow(1920, 1080);
		context.runOnClient(minecraft -> {
			minecraft.options.renderDistance().set(12);
			minecraft.options.bobView().set(false);
			minecraft.options.fov().set(60);
			minecraft.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
		});
		context.waitTicks(20);

		TestSingleplayerContext world = openSave(context, "showcase");
		world.getServer().runCommand("gamerule advance_time false");
		world.getServer().runCommand("gamerule advance_weather false");
		world.getServer().runCommand("weather clear");
		world.getServer().runCommand("time set 6000");
		world.getServer().runCommand("gamemode spectator @a");
		world.getServer().runCommand("team add showcase");
		world.getServer().runCommand("team modify showcase nametagVisibility never");
		context.runOnClient(minecraft -> {
			if (!minecraft.gui.hud.isHidden()) {
				minecraft.gui.hud.toggle();
			}
		});
		context.waitTicks(200);
		Vec3 plaza = findPlaza(context);
		note("plaza.txt", plaza.toString());
		camera(world, plaza.add(0.0, 30.0, 40.0), plaza);
		context.waitTicks(200);
		enableShaders(context);

		String shots = System.getProperty("bananastack.shots", "tower,tall,piggyback,shake,clip,icon,keyed");
		if (shots.contains("scout")) {
			scout(context, world, plaza);
		}
		// Morning light comes from behind the cameras, which all look west towards the hub's banana.
		world.getServer().runCommand("time set 3500");
		if (shots.contains("tower")) {
			List<ServerPlayer> tower = stack(world, TOWER, -90.0F, 4);
			context.waitTicks(60);
			for (int angle = 240; angle <= 300; angle += 15) {
				orbitShot(context, world, TOWER, 3.3, angle, 9.0, 2.4, "tower-a" + angle);
			}
			leave(world, tower);
		}
		if (shots.contains("tall")) {
			List<ServerPlayer> tall = stack(world, TOWER, -90.0F, 7);
			context.waitTicks(60);
			for (int angle = 250; angle <= 290; angle += 20) {
				orbitShot(context, world, TOWER, 5.4, angle, 12.0, 2.8, "tall-a" + angle);
			}
			leave(world, tall);
		}
		if (shots.contains("piggyback")) {
			List<ServerPlayer> pair = stack(world, PAIR, -110.0F, 2);
			context.waitTicks(40);
			for (int angle = 245; angle <= 305; angle += 15) {
				orbitShot(context, world, PAIR, 1.2, angle, 4.4, 1.8, "piggyback-a" + angle);
			}
			leave(world, pair);
		}
		if (shots.contains("shake")) {
			// The bottom player sneaks and everyone pops off.
			List<ServerPlayer> tower = stack(world, TOWER, -90.0F, 5);
			orbitCamera(world, TOWER, 3.2, 260.0F, 10.0, 2.4);
			context.waitTicks(60);
			shot(context, "shake-before");
			world.getServer().runOnServer(server -> crouch(tower.getFirst(), true));
			for (int i = 0; i < 12; i++) {
				context.waitTick();
				shot(context, "shake-after-" + i);
			}
			world.getServer().runOnServer(server -> crouch(tower.getFirst(), false));
			leave(world, tower);
		}
		if (shots.contains("clip")) {
			clip(context, world);
		}
		if (shots.contains("icon")) {
			List<ServerPlayer> three = stack(world, TOWER, -90.0F, 3);
			context.waitTicks(60);
			for (int angle = 255; angle <= 285; angle += 15) {
				// Low and looking up, so the sky is the background.
				orbitShot(context, world, TOWER, 2.4, angle, 4.4, 0.3, "icon-a" + angle);
				orbitShot(context, world, TOWER, 2.2, angle, 5.5, 1.0, "icon-far-a" + angle);
			}
			leave(world, three);
		}
		if (shots.contains("keyed")) {
			// Flat light in front of a magenta wall, so tools/make_showcase.py can cut the players out for the icon.
			setShaders(context, false);
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("fill -32 61 -6 -32 75 11 minecraft:magenta_concrete");
			context.runOnClient(minecraft -> minecraft.options.fov().set(30));
			for (int height : new int[] {2, 3}) {
				List<ServerPlayer> keyed = stack(world, TOWER, -90.0F, height);
				context.waitTicks(40);
				for (int angle : new int[] {250, 270, 290}) {
					orbitShot(context, world, TOWER, height == 2 ? 0.9 : 1.6, angle, height == 2 ? 11.0 : 14.0, 1.6, "keyed-" + height + "-a" + angle);
				}
				leave(world, keyed);
			}
			context.runOnClient(minecraft -> minecraft.options.fov().set(60));
		}
		context.waitTicks(20);

		note("done.txt", "done");
		// Closing a singleplayer world deadlocks the game test threading, and nothing here needs saving.
		Runtime.getRuntime().halt(0);
	}

	private static void clip(ClientGameTestContext context, TestSingleplayerContext world) {
		try {
			Files.createDirectories(Path.of("screenshots", "clip"));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		Vec3 path = WALK_TO.subtract(WALK_FROM);
		float heading = (float) (Mth.atan2(-path.x, path.z) * Mth.RAD_TO_DEG);
		Vec3 back = direction(heading).scale(-1.0);
		Vec3 side = direction(heading + 90.0F);
		// Everyone waits in a little queue behind the first player, then hops on one at a time.
		List<ServerPlayer> crew = new ArrayList<>();
		world.getServer().runOnServer(server -> {
			for (int i = 0; i < 4; i++) {
				Vec3 spot = i == 0 ? WALK_FROM : WALK_FROM.add(back.scale(1.2 + 0.9 * i)).add(side.scale(i % 2 == 0 ? 0.7 : -0.7));
				crew.add(joinShowcase(server, spot, heading));
			}
		});
		ServerPlayer bottom = crew.getFirst();
		int frame = 0;
		for (int i = 1; i <= crew.size(); i++) {
			for (int wait = 0; wait < 12; wait++) {
				followCamera(world, bottom, 2.4);
				context.waitTick();
				frame(context, frame++);
			}
			if (i < crew.size()) {
				ServerPlayer rider = crew.get(i);
				world.getServer().runOnServer(server -> TestPlayers.click(rider, bottom));
			}
		}
		int walkTicks = 80;
		for (int tick = 0; tick < walkTicks; tick++) {
			// Ease in and out, so the stack starts and stops like someone walking.
			double t = 0.5 - 0.5 * Math.cos(Math.PI * (tick + 1) / walkTicks);
			Vec3 at = WALK_FROM.add(path.scale(t));
			world.getServer().runOnServer(server -> walk(bottom, at, heading));
			followCamera(world, bottom, 3.0);
			context.waitTick();
			frame(context, frame++);
		}
		for (int tick = 0; tick < 12; tick++) {
			context.waitTick();
			frame(context, frame++);
		}
		world.getServer().runOnServer(server -> crouch(bottom, true));
		for (int tick = 0; tick < 36; tick++) {
			context.waitTick();
			frame(context, frame++);
		}
		world.getServer().runOnServer(server -> crouch(bottom, false));
		leave(world, crew);
		context.waitTicks(10);
	}

	/** Keeps the camera at CLIP_CAMERA, turned towards {@code subject}. */
	private static void followCamera(TestSingleplayerContext world, ServerPlayer subject, double height) {
		world.getServer().runOnServer(server -> camera(server, CLIP_CAMERA, subject.position().add(0.0, height, 0.0)));
	}

	/** A text map of the ground around the plaza and a top-down shot, for planning camera spots. */
	private static void scout(ClientGameTestContext context, TestSingleplayerContext world, Vec3 plaza) {
		String map = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			int floor = (int) Math.floor(plaza.y);
			StringBuilder out = new StringBuilder("x from " + ((int) plaza.x - 24) + ", z from " + ((int) plaza.z - 24) + ", floor y " + floor + System.lineSeparator());
			for (int z = (int) plaza.z - 24; z <= (int) plaza.z + 24; z++) {
				for (int x = (int) plaza.x - 24; x <= (int) plaza.x + 24; x++) {
					int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
					int clear = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					int rise = top - floor;
					String block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, top - 1, z)).getBlock()).getPath();
					char c;
					if (x == (int) Math.floor(plaza.x) && z == (int) Math.floor(plaza.z)) {
						c = '@';
					} else if (block.contains("yellow")) {
						c = 'Y';
					} else if (rise == 0) {
						c = block.contains("grass") || block.contains("flower") || block.contains("fern") || block.contains("bush") ? 'g' : '.';
					} else if (rise < 0) {
						c = '_';
					} else if (top != clear) {
						c = 'T';
					} else {
						c = rise >= 10 ? '#' : (char) ('0' + rise);
					}
					out.append(c);
				}
				out.append(System.lineSeparator());
			}
			return out.toString();
		});
		note("map.txt", map);
		world.getServer().runCommand("time set 6000");
		world.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.teleportTo(server.overworld(), plaza.x, plaza.y + 48.0, plaza.z, Set.of(), 180.0F, 90.0F, true);
			}
		});
		context.waitTicks(80);
		shot(context, "scout-top");
	}

	// ---- stacks -------------------------------------------------------------------------------------------------

	/** {@code height} players stacked at {@code at}, the bottom one facing {@code yaw}. */
	private static List<ServerPlayer> stack(TestSingleplayerContext world, Vec3 at, float yaw, int height) {
		List<ServerPlayer> players = new ArrayList<>();
		world.getServer().runOnServer(server -> {
			for (int i = 0; i < height; i++) {
				Vec3 spot = i == 0 ? at : at.subtract(direction(yaw).scale(1.0));
				ServerPlayer player = joinShowcase(server, spot, yaw);
				if (i > 0) {
					TestPlayers.click(player, players.getFirst());
				}
				players.add(player);
			}
		});
		return players;
	}

	/** The next showcase player, with its own default skin and no name tag. */
	private static ServerPlayer joinShowcase(MinecraftServer server, Vec3 at, float yaw) {
		int n = joined++ % NAMES.length;
		ServerPlayer player = TestPlayers.join(server.overworld(), NAMES[n], skinnedUuid(NAMES[n], SKINS[n]), at, yaw);
		player.setGameMode(GameType.ADVENTURE);
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "team join showcase " + NAMES[n]);
		return player;
	}

	private static void walk(ServerPlayer bottom, Vec3 to, float yaw) {
		bottom.snapTo(to.x, to.y, to.z, yaw, 0.0F);
		bottom.setYHeadRot(yaw);
		bottom.setYBodyRot(yaw);
	}

	private static void crouch(ServerPlayer player, boolean crouching) {
		TestPlayers.sneak(player, crouching);
		player.setPose(crouching ? Pose.CROUCHING : Pose.STANDING);
	}

	private static void leave(TestSingleplayerContext world, List<ServerPlayer> players) {
		world.getServer().runOnServer(server -> TestPlayers.leave(players.reversed().toArray(ServerPlayer[]::new)));
	}

	/** A UUID whose default skin is slot {@code skin}. */
	private static UUID skinnedUuid(String name, int skin) {
		for (int salt = 0; ; salt++) {
			UUID id = UUID.nameUUIDFromBytes(("bananastack-showcase:" + name + ":" + salt).getBytes(StandardCharsets.UTF_8));
			if (Math.floorMod(id.hashCode(), 18) == skin) {
				return id;
			}
		}
	}

	// ---- camera -------------------------------------------------------------------------------------------------

	private static void orbitShot(ClientGameTestContext context, TestSingleplayerContext world, Vec3 focus, double lookHeight, float angle, double radius, double height, String name) {
		if (orbitCamera(world, focus, lookHeight, angle, radius, height)) {
			context.waitTicks(30);
			shot(context, name);
		}
	}

	/** Puts the spectator camera {@code radius} blocks from {@code focus} at {@code angle}, looking at it. False if that spot is inside a block. */
	private static boolean orbitCamera(TestSingleplayerContext world, Vec3 focus, double lookHeight, float angle, double radius, double height) {
		Vec3 eye = focus.add(direction(angle).scale(radius)).add(0.0, height, 0.0);
		Vec3 target = focus.add(0.0, lookHeight + 1.2, 0.0);
		return world.getServer().computeOnServer(server -> {
			if (!server.overworld().getBlockState(BlockPos.containing(eye)).isAir()) {
				return false;
			}
			camera(server, eye, target);
			return true;
		});
	}

	private static void camera(TestSingleplayerContext world, Vec3 eye, Vec3 target) {
		world.getServer().runOnServer(server -> camera(server, eye, target));
	}

	private static void camera(MinecraftServer server, Vec3 eye, Vec3 target) {
		Vec3 delta = target.subtract(eye);
		float yaw = (float) (Mth.atan2(-delta.x, delta.z) * Mth.RAD_TO_DEG);
		float pitch = (float) (-Mth.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * Mth.RAD_TO_DEG);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.getTeam() == null) {
				// The camera's feet, so its eyes end up at eye.
				player.teleportTo(server.overworld(), eye.x, eye.y - player.getEyeHeight(), eye.z, Set.of(), yaw, pitch, true);
			}
		}
	}

	private static Vec3 direction(float yaw) {
		float radians = yaw * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(radians), 0.0, Mth.cos(radians));
	}

	/** The flattest open 11x11 floor near spawn: the hub's plaza. */
	private static Vec3 findPlaza(ClientGameTestContext context) {
		return context.computeOnClient(minecraft -> {
			BlockPos spawn = minecraft.player.blockPosition();
			double bestScore = Double.MAX_VALUE;
			Vec3 best = Vec3.atBottomCenterOf(spawn);
			for (int dx = -40; dx <= 40; dx++) {
				for (int dz = -40; dz <= 40; dz++) {
					int cx = spawn.getX() + dx;
					int cz = spawn.getZ() + dz;
					int min = Integer.MAX_VALUE;
					int max = Integer.MIN_VALUE;
					for (int ox = -5; ox <= 5; ox++) {
						for (int oz = -5; oz <= 5; oz++) {
							int height = top(minecraft, cx + ox, cz + oz);
							min = Math.min(min, height);
							max = Math.max(max, height);
						}
					}
					if (min < minecraft.level.getMinY() + 5) {
						continue;
					}
					double score = (max - min) * 100.0 + Math.sqrt(dx * dx + dz * dz);
					if (score < bestScore) {
						bestScore = score;
						best = new Vec3(cx + 0.5, top(minecraft, cx, cz), cz + 0.5);
					}
				}
			}
			return best;
		});
	}

	private static int top(Minecraft minecraft, int x, int z) {
		return minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
	}

	// ---- plumbing -----------------------------------------------------------------------------------------------

	/** Opens a world from the saves folder, answering the "older version" prompt if it comes up. */
	private static TestSingleplayerContext openSave(ClientGameTestContext context, String folder) {
		context.runOnClient(minecraft -> minecraft.createWorldOpenFlows().openWorld(folder, () -> {
		}));
		for (int i = 0; i < 2400; i++) {
			context.waitTick();
			boolean loaded = context.computeOnClient(minecraft -> minecraft.level != null && minecraft.player != null && minecraft.gui.screen() == null);
			if (loaded) {
				context.waitTicks(40);
				MinecraftServer server = context.computeOnClient(Minecraft::getSingleplayerServer);
				Path save = context.computeOnClient(minecraft -> minecraft.getLevelSource().getBaseDir().resolve(folder));
				return new TestSingleplayerContextImpl(context, new TestWorldSaveImpl(context, save), server);
			}
			clickIfPresent(context, widget -> {
				String text = widget.getMessage().getString();
				return text.equals("Load") || text.equals("Yes") || text.startsWith("Proceed") || text.startsWith("I know what");
			});
		}
		throw new AssertionError("World " + folder + " did not open");
	}

	private static void clickIfPresent(ClientGameTestContext context, Predicate<AbstractWidget> match) {
		double[] at = context.computeOnClient(minecraft -> {
			Screen screen = minecraft.gui.screen();
			if (screen == null) {
				return null;
			}
			for (GuiEventListener child : screen.children()) {
				if (child instanceof AbstractWidget widget && widget.active && match.test(widget)) {
					double scale = minecraft.getWindow().getGuiScale();
					return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
				}
			}
			return null;
		});
		if (at != null) {
			context.getInput().setCursorPos(at[0], at[1]);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		}
	}

	private static void enableShaders(ClientGameTestContext context) {
		setShaders(context, true);
	}

	private static void setShaders(ClientGameTestContext context, boolean enabled) {
		context.runOnClient(minecraft -> {
			try {
				Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
				Object config = iris.getMethod("getIrisConfig").invoke(null);
				config.getClass().getMethod("setShaderPackName", String.class).invoke(config, "ComplementaryUnbound_r5.9.3.zip");
				config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, enabled);
				config.getClass().getMethod("save").invoke(config);
				iris.getMethod("reload").invoke(null);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		});
		context.waitTicks(100);
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of("showcase-" + name).disableCounterPrefix());
	}

	private static void frame(ClientGameTestContext context, int tick) {
		context.takeScreenshot(TestScreenshotOptions.of("clip/frame-" + String.format("%03d", tick)).disableCounterPrefix());
	}

	private static void note(String name, String text) {
		try {
			Files.createDirectories(NOTES);
			Files.writeString(NOTES.resolve(name), text + System.lineSeparator(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
