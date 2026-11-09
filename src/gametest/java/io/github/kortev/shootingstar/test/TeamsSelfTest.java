package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chainsawman.PochitaPlace;
import io.github.kortev.chainsawman.form.Blood;
import io.github.kortev.chainsawman.form.Buddies;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Fallen;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.form.Hearts;
import io.github.kortev.chainsawman.form.TeamRules;
import io.github.kortev.shootingstar.ShootingStar;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

/**
 * With -Dshootingstar.selftest=teams: six stills of teams and the heart's rules, for checking them (not a clip). A flat grass platform in
 * daylight, you (Kortev) and a friend (a second player on the server, as in the game tests); everything shown is the mod's own (the
 * down, the pick-up, the arrow, the marker, the heart, the den) through its real server paths, the friend's use repeated every 4 ticks
 * as a held use is. Staged: the platform, where everyone stands, how far through each thing the shot waits.
 * <ol>
 * <li>a human down with the timer, the key to give up, the red edges;</li>
 * <li>a friend picking them up, the arm reaching down, the bar filling;</li>
 * <li>the arrow to a buddy who is down;</li>
 * <li>the marker on another player's screen when Chainsaw Man pulls the cord;</li>
 * <li>the heart being torn out of a downed hybrid (heartTheft on);</li>
 * <li>the new den, far from the old one, after a heart was given up.</li>
 * </ol>
 * <pre>./gradlew runSelftest -Pselftest=teams -Pusername=Kortev -Pwidth=1080 -Pheight=1920 "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"</pre>
 */
public class TeamsSelfTest implements ClientModInitializer {
	private enum Stage {
		WAIT_WORLD, SETUP, SETTLE, RUN, DONE
	}

	/** Where the platform is: its middle, the height you stand at, and how far it goes (blocks). */
	private static volatile BlockPos middle;
	private static final int RADIUS = 46;
	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static volatile ServerPlayerEntity friend;
	/** What the friend does each server tick: 0 nothing, 1 lifts, 2 tears the heart out. */
	private static volatile int friendMode;
	private static float wantYaw = Float.NaN;
	private static float wantPitch;

	public static boolean active() {
		return "teams".equals(System.getProperty("shootingstar.selftest"));
	}

	@Override
	public void onInitializeClient() {
		if (!active()) {
			return;
		}
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(20 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[selftest] teams timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-teams-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		ServerTickEvents.END_SERVER_TICK.register(TeamsSelfTest::friendTick);
		ClientTickEvents.END_CLIENT_TICK.register(TeamsSelfTest::tick);
	}

	// ------------------------------------------------------------------ the loop of the test

	private static void tick(MinecraftClient client) {
		TestWindow.keepToItself(client);
		ticks++;
		IntegratedServer server = client.getServer();
		client.options.pauseOnLostFocus = false;
		if (stage == null) {
			return;
		}
		if (stage != Stage.WAIT_WORLD && client.currentScreen != null) {
			client.setScreen(null);
		}
		switch (stage) {
			case WAIT_WORLD -> {
				if (client.player != null && !client.player.isAlive() && ticks % 20 == 0) {
					client.player.requestRespawn();
				}
				if (client.world != null && client.player != null && client.player.isAlive() && server != null && ticks > 60) {
					stage = Stage.SETUP;
				}
			}
			case SETUP -> {
				server.execute(() -> setup(server));
				stage = Stage.SETTLE;
				ticks = 0;
			}
			case SETTLE -> {
				if (middle != null && ticks > 100) {
					stage = Stage.RUN;
					ticks = 0;
				}
			}
			case RUN -> {
				client.getToastManager().clear();
				client.options.hudHidden = false;
				run(client, server, ticks);
				if (!Float.isNaN(wantYaw) && client.player != null) {
					client.player.setYaw(wantYaw);
					client.player.setHeadYaw(wantYaw);
					client.player.setBodyYaw(wantYaw);
					client.player.setPitch(wantPitch);
				}
			}
			case DONE -> {
				ShootingStar.LOGGER.info("[selftest] teams: done");
				stage = null;
				client.scheduleStop();
			}
			default -> {
			}
		}
	}

	private static ServerPlayerEntity you(MinecraftServer server) {
		return server.getPlayerManager().getPlayerList().get(0);
	}

	private static Vec3d at(double dx, double dy, double dz) {
		BlockPos m = middle;
		return new Vec3d(m.getX() + 0.5 + dx, m.getY() + dy, m.getZ() + 0.5 + dz);
	}

	private static float yawTo(Vec3d from, Vec3d to) {
		Vec3d d = to.subtract(from);
		return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
	}

	private static float pitchTo(Vec3d from, Vec3d to) {
		Vec3d d = to.subtract(from);
		return (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
	}

	private static DoubleFunction<Capture.Pose> cam(Vec3d from, Vec3d look) {
		Capture.Pose pose = new Capture.Pose(from.x, from.y, from.z, yawTo(from, look), pitchTo(from, look));
		return t -> pose;
	}

	private static void still(MinecraftClient client, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot teams_{}", name);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, "teams_" + name + ".png", client.getFramebuffer(), message -> {
		});
	}

	// ------------------------------------------------------------------ the stage

	private static int surface(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		return world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
	}

	/** A flat grass platform round you, daylight, clear skies; you at its middle; the friend beside you (he is moved about by the shots). */
	private static void setup(IntegratedServer server) {
		ServerPlayerEntity you = you(server);
		ServerWorld world = server.getOverworld();
		server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_GRIEFING).set(false, server);
		server.setDifficulty(Difficulty.NORMAL, true);
		world.setTimeOfDay(5000);
		world.setWeather(12000, 0, false, false);
		BlockPos here = you.getBlockPos();
		for (int cx = (here.getX() - RADIUS) >> 4; cx <= (here.getX() + RADIUS) >> 4; cx++) {
			for (int cz = (here.getZ() - RADIUS) >> 4; cz <= (here.getZ() + RADIUS) >> 4; cz++) {
				world.setChunkForced(cx, cz, true);
			}
		}
		int ground = surface(world, here.getX(), here.getZ());
		for (Entity e : world.getOtherEntities(you, new net.minecraft.util.math.Box(here).expand(RADIUS + 10.0, 60.0, RADIUS + 10.0))) {
			if (!(e instanceof ServerPlayerEntity)) {
				e.discard();
			}
		}
		for (int x = here.getX() - RADIUS; x <= here.getX() + RADIUS; x++) {
			for (int z = here.getZ() - RADIUS; z <= here.getZ() + RADIUS; z++) {
				int natural = surface(world, x, z);
				for (int y = Math.max(natural, ground) + 1; y <= Math.max(natural, ground) + 14; y++) {
					BlockPos p = new BlockPos(x, y, z);
					if (!world.getBlockState(p).isAir()) {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
				if (natural < ground) {
					for (int y = natural + 1; y <= ground; y++) {
						world.setBlockState(new BlockPos(x, y, z), y == ground ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				} else {
					for (int y = natural; y > ground; y--) {
						world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
					world.setBlockState(new BlockPos(x, ground, z), Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
		middle = new BlockPos(here.getX(), ground + 1, here.getZ());
		reset(server);
		friend = makeFriend(server, world, at(3.0, 0.0, 0.0));
		ShootingStar.LOGGER.info("[selftest] teams: middle {}", middle);
	}

	/** Everyone up and as they were: no heart, no buddy, no form, no down. */
	private static void reset(MinecraftServer server) {
		ServerPlayerEntity you = you(server);
		friendMode = 0;
		ServerPlayerEntity f = friend;
		for (ServerPlayerEntity p : f == null ? List.of(you) : List.of(you, f)) {
			Form.end(p);
			Downed.up(p);
			Fallen.up(p, null);
			if (Buddies.of(p) != null) {
				Buddies.end(p);
			}
			p.removeAttached(Heart.MERGED);
			p.setHealth(p.getMaxHealth());
			p.setSneaking(false);
		}
		you.changeGameMode(GameMode.SURVIVAL);
		you.getInventory().clear();
		Blood.set(you, Blood.MAX);
		Vec3d at = at(0.0, 0.0, 0.0);
		you.teleport(you.getServerWorld(), at.x, at.y, at.z, 0.0F, 0.0F);
		if (f != null) {
			Vec3d to = at(3.0, 0.0, 0.0);
			f.teleport(f.getServerWorld(), to.x, to.y, to.z, 90.0F, 0.0F);
		}
		server.getGameRules().get(TeamRules.HEART_THEFT).set(false, server);
	}

	private static ServerPlayerEntity makeFriend(MinecraftServer server, ServerWorld world, Vec3d at) {
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), "Yakuza1"), false);
		ServerPlayerEntity p = new ServerPlayerEntity(server, world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerManager().onPlayerConnect(connection, p, data);
		p.changeGameMode(GameMode.SURVIVAL);
		p.teleport(world, at.x, at.y, at.z, 90.0F, 0.0F);
		p.onTeleportationDone();
		p.setNoGravity(true);
		return p;
	}

	/** The friend, every server tick: the held use of a pick-up (2: the heart), as a real client's repeats every 4 ticks. */
	private static void friendTick(MinecraftServer server) {
		ServerPlayerEntity f = friend;
		if (f == null || server.getPlayerManager().getPlayerList().size() < 2 || friendMode == 0 || stage != Stage.RUN) {
			return;
		}
		ServerPlayerEntity you = you(server);
		f.setSneaking(true);
		f.getHungerManager().setFoodLevel(20);
		if (server.getTicks() % 4 == 0) {
			if (friendMode == 1) {
				Fallen.lifting(f, you);
			} else {
				Hearts.theftHold(f, you);
			}
		}
	}

	// ------------------------------------------------------------------ the shots

	/** One still: what starts it, how long it waits, its camera (null: your own eyes, looking along {@code yaw}), and your yaw. */
	private record Shot(String name, int waits, Consumer<IntegratedServer> start, DoubleFunction<Capture.Pose> camera, float yaw, float pitch, boolean firstPerson) {
	}

	private static final List<Shot> SHOTS = new ArrayList<>();
	private static int shot = -1;
	private static int shotAt;

	private static void shots() {
		if (!SHOTS.isEmpty()) {
			return;
		}
		// 1. A human down with the timer: Kortev on the ground, 21 s left, seen from the side and a little above.
		SHOTS.add(new Shot("1_downed_timer", 50, s -> s.execute(() -> {
			ServerPlayerEntity you = you(s);
			reset(s);
			Fallen.fall(you);
			you.setAttached(Fallen.LEFT, 21 * 20);
			Vec3d to = friendAway();
			friend.teleport(friend.getServerWorld(), to.x, to.y, to.z, 0.0F, 0.0F);
		}), cam(at(3.4, 1.6, -3.7), at(0.0, 0.3, 0.0)), 0.0F, 0.0F, false));
		// 2. The friend crouched beside them, the arm reaching down, half way through the three seconds.
		SHOTS.add(new Shot("2_pick_up", 34, s -> s.execute(() -> {
			Vec3d to = at(1.15, 0.0, 0.0);
			friend.teleport(friend.getServerWorld(), to.x, to.y, to.z, 90.0F, 0.0F);
			friend.setSneaking(true);
			friendMode = 1;
		}), cam(at(3.9, 1.8, -4.7), at(0.6, 0.35, 0.0)), 0.0F, 0.0F, false));
		// 3. The arrow to a buddy who is down, 38 blocks off to the right: your own eyes.
		SHOTS.add(new Shot("3_buddy_arrow", 45, s -> s.execute(() -> {
			ServerPlayerEntity you = you(s);
			reset(s);
			Buddies.pair(you, friend);
			Vec3d to = at(-30.0, 0.0, 22.0);
			friend.teleport(friend.getServerWorld(), to.x, to.y, to.z, 0.0F, 0.0F);
			Fallen.fall(friend);
		}), null, 0.0F, 6.0F, true));
		// 4. The legend's marker on your screen: the friend, a hybrid, pulls the cord 40 blocks off to your left, the engine roars.
		SHOTS.add(new Shot("4_legend_marker", 30, s -> s.execute(() -> {
			ServerPlayerEntity you = you(s);
			reset(s);
			Heart.give(friend);
			Blood.set(friend, Blood.MAX);
			Vec3d to = at(9.0, 0.0, 26.0);
			friend.teleport(friend.getServerWorld(), to.x, to.y, to.z, 180.0F, 0.0F);
			Form.pullCord(friend);
		}), null, 0.0F, 4.0F, true));
		// 5. The heart being torn out of Kortev, a downed hybrid: the friend's arm in the chest, blood, the bar and the hint.
		SHOTS.add(new Shot("5_heart_torn", 68, s -> s.execute(() -> {
			ServerPlayerEntity you = you(s);
			reset(s);
			s.getGameRules().get(TeamRules.HEART_THEFT).set(true, s);
			Heart.give(you);
			Vec3d to = at(1.15, 0.0, 0.0);
			friend.teleport(friend.getServerWorld(), to.x, to.y, to.z, 90.0F, 0.0F);
			Downed.down(you, friend.getDamageSources().playerAttack(friend));
			friend.setSneaking(true);
			friendMode = 2;
		}), cam(at(3.9, 1.8, -4.7), at(0.6, 0.35, 0.0)), 0.0F, 0.0F, false));
		// 6. The heart given up (Kortev, a hybrid, gives it up): a new den, thousands of blocks off; the camera is there.
		SHOTS.add(new Shot("6_new_den", 150, s -> s.execute(() -> {
			ServerPlayerEntity you = you(s);
			reset(s);
			s.getGameRules().get(TeamRules.HEART_THEFT).set(false, s);
			Heart.give(you);
			Hearts.update(s);
			List<BlockPos> before = PochitaPlace.allDens(s);
			Hearts.giveUp(you);
			BlockPos den = null;
			for (BlockPos p : PochitaPlace.allDens(s)) {
				if (!before.contains(p)) {
					den = p;
				}
			}
			if (den != null) {
				denAt = den;
				ShootingStar.LOGGER.info("[selftest] teams: the new den is at {}, {} blocks from the platform", den, Math.hypot(den.getX() - middle.getX(), den.getZ() - middle.getZ()));
				s.getOverworld().setChunkForced(den.getX() >> 4, den.getZ() >> 4, true);
				// (Behind where the camera stands, so that they are not in the picture.)
				you.teleport(s.getOverworld(), den.getX() + 9.5, den.getY() + 1.0, den.getZ() + 9.5, 0.0F, 0.0F);
				you.setNoGravity(true);
			}
		}), t -> {
			BlockPos d = denAt;
			// (In the clearing the den clears round the tree, whichever way it lies.)
			Vec3d from = d == null ? Vec3d.ZERO : new Vec3d(d.getX() + 7.0, d.getY() + 3.4, d.getZ() + 7.0);
			Vec3d look = d == null ? Vec3d.ZERO : new Vec3d(d.getX() + 0.5, d.getY() + 1.1, d.getZ() + 0.5);
			return new Capture.Pose(from.x, from.y, from.z, yawTo(from, look), pitchTo(from, look));
		}, 0.0F, 0.0F, false));
	}

	private static volatile BlockPos denAt;

	/** Somewhere the friend stands out of the first shot. */
	private static Vec3d friendAway() {
		return at(12.0, 0.0, 12.0);
	}

	private static void run(MinecraftClient client, IntegratedServer server, int f) {
		shots();
		if (client.player == null || client.world == null) {
			return;
		}
		if (shot < 0) {
			begin(client, server, 0, f);
			return;
		}
		Shot current = SHOTS.get(shot);
		if (current.name().startsWith("6")) {
			// (The world is still finding the heart a place, and the client is still loading it: the shot waits for both.)
			BlockPos d = denAt;
			if (d == null || !client.world.getChunkManager().isChunkLoaded(d.getX() >> 4, d.getZ() >> 4)) {
				shotAt = f;
				return;
			}
		}
		if (f - shotAt >= current.waits()) {
			still(client, current.name());
			shot++;
			if (shot >= SHOTS.size()) {
				stage = Stage.DONE;
				return;
			}
			begin(client, server, shot, f);
		}
	}

	private static void begin(MinecraftClient client, IntegratedServer server, int index, int f) {
		shot = index;
		shotAt = f;
		Shot next = SHOTS.get(index);
		if (index >= 4) {
			// (The legend's marker of the shot before lasts a minute: it is not in these.)
			io.github.kortev.chainsawman.client.ClientTeams.clear();
		}
		next.start().accept(server);
		Capture.camera = next.camera();
		client.options.setPerspective(next.firstPerson() ? Perspective.FIRST_PERSON : Perspective.THIRD_PERSON_BACK);
		if (next.firstPerson()) {
			wantYaw = next.yaw();
			wantPitch = next.pitch();
		} else {
			wantYaw = Float.NaN;
		}
	}
}
