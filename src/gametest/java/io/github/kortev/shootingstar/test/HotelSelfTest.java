package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.client.ClientHotel;
import io.github.kortev.chainsawman.client.ContractScreen;
import io.github.kortev.chainsawman.contract.Contract;
import io.github.kortev.chainsawman.contract.Contracts;
import io.github.kortev.chainsawman.form.Blood;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.form.Saw;
import io.github.kortev.chainsawman.hotel.EternityDevilEntity;
import io.github.kortev.chainsawman.hotel.HomeBlockEntity;
import io.github.kortev.chainsawman.hotel.Hotel;
import io.github.kortev.chainsawman.hotel.HotelBuilder;
import io.github.kortev.chainsawman.hotel.HotelPlan;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.BiomeKeys;

/**
 * With -Dshootingstar.selftest=hotel: the Eternity Devil's hotel, filmed. A plains site at night, the hotel put down as /chainsaw eternity
 * does (the real structure code, turned the way it faces the camera) and woken by the real night logic; everything that happens is the
 * mod's own (the loop, the laps, the devil's words, the offer, the fight) with real keys and paths; what is staged is said below.
 * <ul>
 * <li>(default) the clip for kortev: outside, the lobby, the climb (one wrong thing a floor), floor 8's loop, the offer, refuse, the devil
 * growing out of the walls, the fight as Chainsaw Man, the kill and the loot (about 36 s).</li>
 * <li>-Ptarget=accept: the other choice: accept, the loop opens, walk out.</li>
 * <li>-Ptarget=look: stills of everything, for checking it (not a clip).</li>
 * </ul>
 * Staged: the site (levelled), the floors climbed one after another in a cut each (you are put on each floor; the stairs are real and
 * were walked in the game tests), the laps and the minute (the loop's counters are taken to the edge so the offer comes when the shot is
 * ready), the devil's health lowered for the fight (so it ends in a clip's time), yours kept up.
 * <pre>./gradlew runSelftest -Pselftest=hotel -Pusername=Kortev -Pcapture=true -Pwidth=1080 -Pheight=1920 "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"</pre>
 */
public class HotelSelfTest implements ClientModInitializer {
	private enum Stage {
		WAIT_WORLD, SETUP, SETTLE, RUN, DONE
	}

	/** The hotel's turn: its front faces north (toward -z), where the camera stands. */
	private static final int ROT = 0;
	/** Where the site is, found once with locateBiome (a plains) and kept, so every take has the same ground. */
	private static final BlockPos SITE = null;

	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static volatile BlockPos origin;
	private static float wantYaw = Float.NaN;
	private static float wantPitch;
	private static float lens;
	/** The player's own legs: walking, sprinting, and the head held where it is told. */
	private static boolean forward;
	private static boolean sprint;

	public static boolean active() {
		return "hotel".equals(System.getProperty("shootingstar.selftest"));
	}

	public static float lens() {
		return active() && stage == Stage.RUN ? lens : 0.0F;
	}

	private static boolean capturing() {
		return "true".equals(System.getProperty("shootingstar.selftest.capture"));
	}

	private static String target() {
		return System.getProperty("shootingstar.selftest.target", "");
	}

	@Override
	public void onInitializeClient() {
		if (!active()) {
			return;
		}
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(30 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[selftest] hotel timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-hotel-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		if (capturing()) {
			ServerTickEvents.START_SERVER_TICK.register(server -> Capture.serverTickStart());
			ServerTickEvents.END_SERVER_TICK.register(server -> Capture.serverTickEnd());
		}
		ServerTickEvents.END_SERVER_TICK.register(HotelSelfTest::keepUp);
		ClientTickEvents.END_CLIENT_TICK.register(HotelSelfTest::tick);
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
		if (stage != Stage.WAIT_WORLD && client.currentScreen != null && !(client.currentScreen instanceof ContractScreen)) {
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
				client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				if (origin != null && ticks > 260) {
					stage = Stage.RUN;
					ticks = 0;
				}
			}
			case RUN -> {
				client.getToastManager().clear();
				client.options.hudHidden = false;
				if ("look".equals(target())) {
					look(client, server, ticks);
				} else {
					ShootingStar.LOGGER.error("[selftest] hotel: no such target {}", target());
					stage = Stage.DONE;
				}
				drive(client);
			}
			case DONE -> {
				Capture.stop();
				ShootingStar.LOGGER.info("[selftest] hotel: done");
				stage = null;
				client.scheduleStop();
			}
			default -> {
			}
		}
	}

	/** Your head where it is told, and the keys of your legs. */
	private static void drive(MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		if (!Float.isNaN(wantYaw)) {
			client.player.setYaw(wantYaw);
			client.player.setHeadYaw(wantYaw);
			client.player.setBodyYaw(wantYaw);
			client.player.setPitch(wantPitch);
		}
		client.options.forwardKey.setPressed(forward);
		client.options.sprintKey.setPressed(forward && sprint);
	}

	/** Your health, hunger and blood kept up (the devil's mouths hurt). */
	private static void keepUp(net.minecraft.server.MinecraftServer server) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (p.isAlive() && p.getHealth() < p.getMaxHealth() && p.age % 4 == 0 && p.age > 20 && p.getHealth() < 12.0F) {
				p.setHealth(p.getMaxHealth());
			}
		}
	}

	// ------------------------------------------------------------------ helpers

	private static Vec3d world(double lx, double ly, double lz) {
		return HotelBuilder.world(origin, ROT, lx, ly, lz);
	}

	/** The yaw of a look along the local x (east, at turn 0) and z. */
	private static float yawTo(Vec3d from, Vec3d to) {
		Vec3d d = to.subtract(from);
		return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
	}

	private static float pitchTo(Vec3d from, Vec3d to) {
		Vec3d d = to.subtract(from);
		return (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
	}

	private static Capture.Pose look(Vec3d from, Vec3d at) {
		return new Capture.Pose(from.x, from.y, from.z, yawTo(from, at), pitchTo(from, at));
	}

	/** A camera at a local point looking at another. */
	private static DoubleFunction<Capture.Pose> cam(double fx, double fy, double fz, double ax, double ay, double az) {
		Vec3d from = world(fx, fy, fz);
		Vec3d at = world(ax, ay, az);
		return t -> look(from, at);
	}

	private static ServerPlayerEntity you(IntegratedServer server) {
		return server.getPlayerManager().getPlayerList().get(0);
	}

	private static HomeBlockEntity home(IntegratedServer server) {
		BlockPos at = HotelBuilder.world(origin, ROT, HotelPlan.HOME_X, HotelPlan.HOME_Y, HotelPlan.HOME_Z);
		return server.getOverworld().getBlockEntity(at) instanceof HomeBlockEntity h ? h : null;
	}

	/** You, put at a local point of the hotel (yaw in the world's degrees). */
	private static void put(IntegratedServer server, double lx, double ly, double lz, float yaw) {
		server.execute(() -> {
			ServerPlayerEntity p = you(server);
			Vec3d w = world(lx, ly, lz);
			p.teleport(server.getOverworld(), w.x, w.y, w.z, yaw, 0.0F);
		});
		wantYaw = yaw;
		wantPitch = 0.0F;
	}

	private static void still(MinecraftClient client, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot hotel_{}", name);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, "hotel_" + name + ".png", client.getFramebuffer(), message -> {
		});
	}

	private static int surface(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
		while (y > world.getBottomY()) {
			BlockState s = world.getBlockState(new BlockPos(x, y, z));
			if (s.isAir() || s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.LOGS) || s.isReplaceable() && s.getFluidState().isEmpty() || s.isOf(Blocks.SNOW)) {
				y--;
			} else {
				break;
			}
		}
		return y;
	}

	/**
	 * The site: a plains (found once), levelled to the grass round its middle over a stretch the hotel and the cameras fill, the hotel put down
	 * (the real structure code) facing north, time fixed at night, you at the plaza's far end.
	 */
	private static void setup(IntegratedServer server) {
		ServerPlayerEntity you = you(server);
		ServerWorld world = server.getOverworld();
		server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_LOOT).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_GRIEFING).set(false, server);
		server.getGameRules().get(GameRules.DO_TILE_DROPS).set(false, server);
		server.setDifficulty(Difficulty.NORMAL, true);
		world.setTimeOfDay(14500);
		world.setWeather(12000, 0, false, false);
		BlockPos near = SITE;
		if (near == null) {
			var found = world.locateBiome(b -> b.matchesKey(BiomeKeys.PLAINS), BlockPos.ORIGIN, 6400, 32, 64);
			near = found == null ? BlockPos.ORIGIN : found.getFirst();
			ShootingStar.LOGGER.info("[selftest] hotel: plains at {}", near);
		}
		int x0 = near.getX() - 8;
		int z0 = near.getZ();
		for (int dx = -64; dx <= 112; dx += 16) {
			for (int dz = -72; dz <= 56; dz += 16) {
				world.getChunk((x0 + dx) >> 4, (z0 + dz) >> 4);
			}
		}
		int ground = surface(world, x0 + 19, z0 + 7);
		origin = new BlockPos(x0, ground, z0);
		for (int cx = (x0 - 64) >> 4; cx <= (x0 + 112) >> 4; cx++) {
			for (int cz = (z0 - 72) >> 4; cz <= (z0 + 56) >> 4; cz++) {
				world.setChunkForced(cx, cz, true);
			}
		}
		for (Entity e : world.getOtherEntities(you, new net.minecraft.util.math.Box(origin).expand(90.0, 60.0, 90.0))) {
			if (!(e instanceof ServerPlayerEntity)) {
				e.discard();
			}
		}
		// Level: the grass at the plaza's height all round (a few blocks of dirt under), the air clear above.
		for (int x = x0 - 60; x <= x0 + 100; x++) {
			for (int z = z0 - 68; z <= z0 + 52; z++) {
				double d = Math.max(0.0, Math.max(Math.max(x0 - 24 - x, x - (x0 + 62)), Math.max(z0 - 40 - z, z - (z0 + 30))));
				if (d > 24.0) {
					continue;
				}
				int natural = surface(world, x, z);
				double w = MathHelper.clamp(d / 24.0, 0.0, 1.0);
				int want = (int) Math.round(MathHelper.lerp(w * w, ground, natural));
				for (int y = Math.max(natural, want) + 1; y <= Math.max(natural, want) + 14; y++) {
					BlockPos p = new BlockPos(x, y, z);
					if (!world.getBlockState(p).isAir()) {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
				if (want > natural) {
					for (int y = natural + 1; y <= want; y++) {
						world.setBlockState(new BlockPos(x, y, z), y == want ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				} else {
					for (int y = natural; y > want; y--) {
						world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
					world.setBlockState(new BlockPos(x, want, z), Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
		// (The hotel's own plaza is a block under the grass: the grass at "ground" is where its floor goes.)
		Hotel.place(world, new BlockPos(x0, ground, z0), ROT);
		if (Form.on(you)) {
			Form.end(you);
		}
		Downed.up(you);
		Heart.take(you);
		Contracts.clear(you);
		you.changeGameMode(GameMode.SURVIVAL);
		you.getInventory().clear();
		you.getInventory().setStack(0, new ItemStack(Items.IRON_SWORD));
		you.getInventory().selectedSlot = 0;
		you.setExperienceLevel(10);
		you.setHealth(you.getMaxHealth());
		you.getHungerManager().setFoodLevel(20);
		Blood.set(you, Blood.MAX);
		Vec3d at = world(19.5, 1.0, -10.5);
		you.teleport(world, at.x, at.y, at.z, 0.0F, 0.0F);
		ShootingStar.LOGGER.info("[selftest] hotel: origin {} (ground {})", origin, ground);
	}

	// ------------------------------------------------------------------ the stills

	/** One still: what to do first, the camera, and how many ticks to wait before the photograph. */
	private record Look(String name, int waitTicks, Consumer<IntegratedServer> setup, DoubleFunction<Capture.Pose> camera, float fov) {
		Look(String name, int waitTicks, Consumer<IntegratedServer> setup, DoubleFunction<Capture.Pose> camera) {
			this(name, waitTicks, setup, camera, 70.0F);
		}
	}

	private static final List<Look> LOOKS = new ArrayList<>();
	private static int lookIndex = -1;
	private static int lookAt;

	private static void onServer(IntegratedServer s, Consumer<ServerWorld> r) {
		s.execute(() -> r.accept(s.getOverworld()));
	}

	private static final Consumer<IntegratedServer> NOTHING = s -> {
	};

	/** Floor {@code i} (0 is the lobby's) walking surface, and a camera eye 1.6 over it. */
	private static double floorY(int i) {
		return 4.0 * i + 1.0;
	}

	private static double eye(int i) {
		return floorY(i) + 1.6;
	}

	private static Consumer<IntegratedServer> at(double lx, double ly, double lz, float yaw) {
		return s -> put(s, lx, ly, lz, yaw);
	}

	private static void looks() {
		if (!LOOKS.isEmpty()) {
			return;
		}
		// By day: dead, dark, shut. By night: lit.
		LOOKS.add(new Look("outside_day", 50, s -> onServer(s, w -> w.setTimeOfDay(3000)), cam(-6.0, 2.2, -13.0, 19.0, 6.0, 0.0)));
		LOOKS.add(new Look("outside_night", 70, s -> onServer(s, w -> w.setTimeOfDay(14500)), cam(-6.0, 2.2, -13.0, 19.0, 6.0, 0.0)));
		LOOKS.add(new Look("outside_night_front", 5, NOTHING, cam(26.0, 2.1, -12.0, 26.0, 3.5, 0.0)));
		LOOKS.add(new Look("outside_night_corner", 5, NOTHING, cam(52.0, 2.4, -6.0, 30.0, 8.0, 3.0)));
		LOOKS.add(new Look("outside_night_high", 5, NOTHING, cam(60.0, 2.4, 12.0, 28.0, 9.0, 7.0)));
		// The lobby.
		LOOKS.add(new Look("lobby_desk", 20, at(24.0, 1.0, 2.0, -90.0F), cam(26.5, 2.6, 1.6, 32.0, 2.3, 4.4)));
		LOOKS.add(new Look("lobby_entrance", 5, NOTHING, cam(33.0, 2.6, 6.2, 27.5, 2.4, 0.5)));
		LOOKS.add(new Look("lobby_corridor", 5, NOTHING, cam(22.0, 2.6, 6.5, 1.0, 2.4, 6.5)));
		// Each floor's wrong thing.
		LOOKS.add(new Look("f2_numbers_wide", 30, at(18.0, floorY(1), 5.6, 90.0F), cam(3.0, eye(1), 6.6, 15.0, eye(1) + 0.4, 7.6)));
		LOOKS.add(new Look("f2_numbers_sign", 5, NOTHING, cam(13.0, eye(1), 5.3, 10.5, eye(1) + 0.95, 7.0), 40.0F));
		LOOKS.add(new Look("f3_paintings", 20, at(30.0, floorY(2), 6.5, 90.0F), cam(14.0, eye(2), 6.5, 32.0, eye(2), 6.5)));
		LOOKS.add(new Look("f4_stain", 20, at(30.0, floorY(3), 6.5, 90.0F), cam(14.0, eye(3), 6.5, 32.0, eye(3) - 0.9, 6.5)));
		LOOKS.add(new Look("f5_eyes", 20, at(30.0, floorY(4), 6.5, 90.0F), cam(11.0, eye(4), 6.5, 32.0, eye(4) + 0.2, 6.5)));
		LOOKS.add(new Look("f6_door_a", 5, at(16.0, floorY(5), 6.5, 0.0F), cam(24.5, eye(5), 5.7, 22.0, eye(5) - 0.2, 8.0)));
		LOOKS.add(new Look("f6_door_b", 36, NOTHING, cam(24.5, eye(5), 5.7, 22.0, eye(5) - 0.2, 8.0)));
		LOOKS.add(new Look("f7_phone_room", 40, at(18.0, floorY(6), 6.5, 0.0F), cam(24.0, eye(6) - 0.2, 1.4, 20.0, eye(6) - 0.5, 2.4)));
		LOOKS.add(new Look("f7_corridor", 5, NOTHING, cam(26.0, eye(6), 6.5, 14.0, eye(6), 6.5)));
		// The stairwell.
		// Floor 8: the corridor (the fog only comes on for someone standing in it), then the laps.
		LOOKS.add(new Look("f8_corridor", 45, at(16.5, floorY(7), 6.5, -90.0F), cam(13.5, eye(7), 6.5, 30.0, eye(7), 6.5)));
		LOOKS.add(new Look("f8_corridor_side", 5, NOTHING, cam(14.0, eye(7), 5.4, 20.0, eye(7) - 0.1, 8.0)));
		LOOKS.add(new Look("f8_lap3_flesh", 50, s -> onServer(s, w -> {
			HomeBlockEntity h = home(s);
			h.run.laps.put(you(s).getUuid(), 2);
			h.run.since.put(you(s).getUuid(), w.getTime());
		}), cam(13.5, eye(7), 6.5, 30.0, eye(7), 6.5)));
		// The devil's words, and the offer.
		LOOKS.add(new Look("offer_words", 50, s -> onServer(s, w -> {
			HomeBlockEntity h = home(s);
			h.run.laps.put(you(s).getUuid(), 3);
		}), cam(13.5, eye(7), 6.5, 30.0, eye(7), 6.5)));
		LOOKS.add(new Look("offer_screen", 150, NOTHING, cam(13.5, eye(7), 6.5, 30.0, eye(7), 6.5)));
		// Refused: the walls bleed and it grows.
		LOOKS.add(new Look("bleed_a", 40, s -> {
			MinecraftClient c = MinecraftClient.getInstance();
			if (c.currentScreen instanceof ContractScreen screen) {
				screen.answer(false);
			}
		}, cam(13.5, eye(7), 6.5, 34.0, eye(7), 6.5)));
		LOOKS.add(new Look("grow_b", 40, NOTHING, cam(21.0, eye(7), 6.5, 34.0, eye(7), 6.5)));
		LOOKS.add(new Look("devil_front", 60, NOTHING, cam(24.0, eye(7) - 0.1, 6.5, 33.0, floorY(7) + 1.7, 6.5)));
		LOOKS.add(new Look("devil_wide", 5, NOTHING, cam(17.0, eye(7) + 0.4, 5.8, 33.0, floorY(7) + 1.8, 6.6)));
		LOOKS.add(new Look("devil_close", 5, NOTHING, cam(28.0, eye(7), 6.2, 34.0, floorY(7) + 1.9, 6.5)));
		LOOKS.add(new Look("devil_mouth", 5, NOTHING, cam(29.0, eye(7) - 0.3, 6.8, 33.5, floorY(7) + 1.4, 6.5)));
		// Its attacks, while you stand in its corridor, and its heart.
		LOOKS.add(new Look("attack_a", 45, at(25.0, floorY(7), 6.5, -90.0F), cam(15.0, eye(7) + 0.3, 6.5, 30.0, floorY(7) + 0.9, 6.5)));
		LOOKS.add(new Look("attack_b", 30, NOTHING, cam(15.0, eye(7) + 0.3, 6.5, 30.0, floorY(7) + 0.9, 6.5)));
		LOOKS.add(new Look("attack_c", 30, NOTHING, cam(15.0, eye(7) + 0.3, 6.5, 30.0, floorY(7) + 0.9, 6.5)));
		LOOKS.add(new Look("attack_d", 30, NOTHING, cam(15.0, eye(7) + 0.3, 6.5, 30.0, floorY(7) + 0.9, 6.5)));
		LOOKS.add(new Look("heart_hidden", 5, s -> onServer(s, w -> {
			for (EternityDevilEntity d : w.getEntitiesByType(io.github.kortev.chainsawman.ChainsawMan.ETERNITY_DEVIL, e -> true)) {
				d.setHealth(120.0F);
			}
		}), cam(23.0, eye(7), 6.5, 33.0, floorY(7) + 1.8, 6.5)));
		LOOKS.add(new Look("heart_open", 60, NOTHING, cam(23.0, eye(7), 6.5, 33.0, floorY(7) + 1.8, 6.5)));
		LOOKS.add(new Look("heart_close", 5, NOTHING, cam(27.0, eye(7) + 0.1, 6.0, 34.5, floorY(7) + 1.8, 6.5)));
		// The kill.
		LOOKS.add(new Look("dying_a", 30, s -> onServer(s, w -> {
			for (EternityDevilEntity d : w.getEntitiesByType(io.github.kortev.chainsawman.ChainsawMan.ETERNITY_DEVIL, e -> true)) {
				d.damage(w.getDamageSources().generic(), 1000.0F);
			}
		}), cam(23.0, eye(7), 6.5, 33.0, floorY(7) + 1.8, 6.5)));
		LOOKS.add(new Look("dying_b", 40, NOTHING, cam(23.0, eye(7), 6.5, 33.0, floorY(7) + 1.4, 6.5)));
		LOOKS.add(new Look("dying_c", 40, NOTHING, cam(23.0, eye(7), 6.5, 33.0, floorY(7) + 1.0, 6.5)));
		LOOKS.add(new Look("loot", 80, NOTHING, cam(26.0, eye(7) - 0.2, 5.4, 31.0, floorY(7) + 0.4, 6.5)));
		LOOKS.add(new Look("plain_corridor", 10, NOTHING, cam(14.0, eye(7), 6.5, 30.0, eye(7), 6.5)));
	}

	private static void look(MinecraftClient client, IntegratedServer server, int f) {
		looks();
		if (client.player == null || client.world == null) {
			return;
		}
		if (lookIndex < 0) {
			lookIndex = 0;
			lookAt = f;
			LOOKS.get(0).setup().accept(server);
			Capture.camera = LOOKS.get(0).camera();
			lens = LOOKS.get(0).fov();
		}
		Look current = LOOKS.get(lookIndex);
		if (f - lookAt >= current.waitTicks()) {
			still(client, String.format("%02d_%s", lookIndex, current.name()));
			lookIndex++;
			lookAt = f;
			if (lookIndex >= LOOKS.size()) {
				stage = Stage.DONE;
				return;
			}
			LOOKS.get(lookIndex).setup().accept(server);
			Capture.camera = LOOKS.get(lookIndex).camera();
			lens = LOOKS.get(lookIndex).fov();
		}
	}
}
