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
	private static final BlockPos SITE = new BlockPos(288, 62, -256);
	/** The natural ground there (the hotel's floor goes at this height: looked up each run it would be the roof of the last take's hotel). */
	private static final int GROUND = 62;

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
				} else if (target().isEmpty() || "accept".equals(target()) || "fight".equals(target())) {
					clip(client, server, ticks);
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
		int ground = GROUND;
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

	// ------------------------------------------------------------------ the clip

	/** One shot: its length (ticks), what starts it, what runs every tick of it, its camera (local capture time in ticks), and the lens. */
	private static final class Shot {
		final String name;
		final int ticks;
		Runnable start = () -> {
		};
		Consumer<Integer> each = t -> {
		};
		DoubleFunction<Capture.Pose> cam;
		float fov = 70.0F;
		boolean firstPerson;
		/** Slow motion for the shot (1 none). */
		double slow = 1.0;

		Shot(String name, int ticks) {
			this.name = name;
			this.ticks = ticks;
		}
	}

	private static final List<Shot> SHOTS = new ArrayList<>();
	private static int shotIndex = -1;
	private static int shotAt;
	private static double shotCaptureAt;
	private static int clipF0 = -1;
	private static IntegratedServer serverRef;
	private static Vec3d glance;
	private static boolean formOn;
	private static int slashedAt = -100;
	private static int devilDiedAt = -1;
	/** A shot that ends early sets this; the clip's clock then jumps over what was left of it. */
	private static boolean endShot;
	private static int skip;

	private static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}

	private static Shot shot(String name, int ticks) {
		Shot s = new Shot(name, ticks);
		SHOTS.add(s);
		return s;
	}

	/** Where you are, smoothed over the frame (as the capture draws it). */
	private static Vec3d you(double t) {
		float part = (float) (t - Math.floor(t));
		return mc().player.getLerpedPos(part);
	}

	/** The local point {@code p} in a shot's own frame. */
	private static Vec3d L(double x, double y, double z) {
		return world(x, y, z);
	}

	/** A camera that stays {@code behind} blocks behind you (along the way you face), {@code up} over your feet and {@code side} to your right, looking at a point {@code lead} ahead of you at {@code at} over your feet. */
	private static DoubleFunction<Capture.Pose> follow(double behind, double up, double side, double lead, double at) {
		return t -> {
			Vec3d p = you(Capture.time());
			float yaw = wantYaw;
			double rad = Math.toRadians(yaw);
			Vec3d dir = new Vec3d(-Math.sin(rad), 0.0, Math.cos(rad));
			Vec3d right = new Vec3d(-dir.z, 0.0, dir.x).multiply(-1.0);
			Vec3d from = p.subtract(dir.multiply(behind)).add(right.multiply(side)).add(0.0, up, 0.0);
			Vec3d to = p.add(dir.multiply(lead)).add(0.0, at, 0.0);
			return look(from, to);
		};
	}

	/** The same, but {@code ahead} of you, looking back at you. */
	private static DoubleFunction<Capture.Pose> facing(double ahead, double up, double side, double at) {
		return t -> {
			Vec3d p = you(Capture.time());
			double rad = Math.toRadians(wantYaw);
			Vec3d dir = new Vec3d(-Math.sin(rad), 0.0, Math.cos(rad));
			Vec3d right = new Vec3d(-dir.z, 0.0, dir.x).multiply(-1.0);
			Vec3d from = p.add(dir.multiply(ahead)).add(right.multiply(side)).add(0.0, up, 0.0);
			return look(from, p.add(0.0, at, 0.0));
		};
	}

	/** Through your own eyes, the head turning to {@link #glance} when there is a point to look at. */
	private static final DoubleFunction<Capture.Pose> EYES = t -> {
		Vec3d p = you(Capture.time());
		Vec3d eye = p.add(0.0, 1.62, 0.0);
		float yaw = wantYaw;
		float pitch = wantPitch;
		if (glance != null) {
			float gy = yawTo(eye, glance);
			float gp = pitchTo(eye, glance);
			yaw += MathHelper.clamp(MathHelper.wrapDegrees(gy - yaw), -50.0F, 50.0F);
			pitch = MathHelper.clamp(gp, -22.0F, 22.0F);
		}
		return new Capture.Pose(eye.x, eye.y, eye.z, yaw, pitch);
	};

	private static void keys(boolean fwd, boolean run) {
		forward = fwd;
		sprint = run;
	}

	private static void server(Consumer<ServerWorld> r) {
		serverRef.execute(() -> r.accept(serverRef.getOverworld()));
	}

	private static void tp(double lx, double ly, double lz, float yaw) {
		put(serverRef, lx, ly, lz, yaw);
	}

	private static void pressG() {
		KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(io.github.kortev.chainsawman.client.ChainsawManClient.PULL_CORD.getBoundKeyTranslationKey()));
	}

	private static void shots() {
		if (!SHOTS.isEmpty()) {
			return;
		}
		if ("accept".equals(target())) {
			shotsAccept();
			return;
		}
		if ("fight".equals(target())) {
			fightShots();
			return;
		}
		final float EAST = -90.0F;
		// ---- 1. Outside, lit: kortev walks up the plaza to the doors.
		Shot s = shot("outside", 44);
		s.start = () -> {
			tp(24.5, 1.0, -9.5, 0.0F);
			wantYaw = 0.0F;
			keys(true, false);
		};
		s.each = t -> {
			Vec3d eye = world(27.5, 2.6, 4.0);
			wantYaw = MathHelper.clamp(yawTo(you(0.0), eye), -40.0F, 40.0F);
		};
		// (The camera dollies in toward the lit doors as he walks up to them.)
		s.cam = t -> look(world(17.0 + 8.0 * Math.min(1.0, t / 44.0), 2.0, -11.5 + 5.0 * Math.min(1.0, t / 44.0)), world(26.5, 3.4, -1.0));
		// ---- 2. The lobby: in at the doors, past the desk and its bell, along to the stairwell.
		s = shot("lobby", 52);
		s.start = () -> {
			tp(30.8, 1.0, 2.6, EAST);
			keys(true, false);
		};
		s.each = t -> wantYaw = yawTo(you(0.0), world(14.0, 1.0, 6.3));
		s.cam = follow(4.0, 2.0, 0.8, 3.0, 1.2);
		// ---- 3. The climb: one wrong thing a floor (you are put on each floor, at the stairwell door, walking out of it).
		climb(shot("f2_a", 15), 1, 11.3, 5.15, 10.5, 7.7, 40.0F, 6.0, 7.2);
		climb(shot("f2_b", 11), 1, 17.2, 5.15, 16.5, 7.7, 40.0F, 12.0, 7.2);
		s = shot("f3_paintings", 18);
		s.start = () -> {
			tp(15.0, floorY(2), 6.5, EAST);
			wantYaw = EAST;
			keys(true, false);
		};
		s.cam = follow(4.0, 1.5, 0.5, 8.0, 1.0);
		s = shot("f4_stain", 20);
		s.start = () -> {
			tp(15.0, floorY(3), 7.3, EAST);
			wantYaw = EAST;
			keys(true, false);
		};
		s.cam = cam(12.5, floorY(3) + 1.6, 6.0, 30.0, floorY(3) + 0.3, 6.5);
		s = shot("f5_eyes", 18);
		s.start = () -> {
			tp(14.0, floorY(4), 6.5, EAST);
			wantYaw = EAST;
			keys(true, false);
		};
		s.cam = follow(3.4, 1.7, 0.0, 8.0, 0.8);
		s = shot("f6_door", 26);
		s.start = () -> {
			tp(19.5, floorY(5), 6.5, 0.0F);
			wantYaw = EAST;
			keys(false, false);
			server(w -> home(serverRef).run.breathAt = -100000L);
		};
		s.each = t -> {
			wantYaw = EAST;
		};
		s.cam = cam(25.8, eye(5), 5.4, 22.0, eye(5) - 0.2, 8.0);
		s = shot("f7_phone", 24);
		s.start = () -> {
			tp(17.0, floorY(6), 6.5, EAST);
			wantYaw = EAST;
			keys(true, false);
			server(w -> home(serverRef).run.phoneAt = -100000L);
		};
		s.cam = cam(24.0, eye(6) - 0.2, 1.4, 20.0, eye(6) - 0.5, 2.4);
		// ---- 4. Floor 8: the loop. Out of the stairwell door and along; the corridor comes round again.
		s = shot("f8_run", 44);
		s.start = () -> {
			tp(14.2, floorY(7), 6.5, EAST);
			wantYaw = EAST;
			wantPitch = 0.0F;
			keys(true, true);
		};
		s.cam = follow(3.2, 1.9, 0.0, 9.0, 1.1);
		s = shot("f8_eyes", 60);
		s.firstPerson = true;
		s.each = t -> {
			double x = world(0, 0, 0).x;
			Vec3d here = you(0.0);
			Vec3d sign = world(16.0, floorY(7) + 2.3, 7.0);
			Vec3d p = HotelBuilder.local(origin, ROT, here);
			glance = Math.abs(p.x - 16.0) < 5.0 && p.x < 17.5 ? sign : null;
		};
		s.cam = EYES;
		s.fov = 74.0F;
		s = shot("f8_flesh", 42);
		s.start = () -> glance = null;
		s.cam = facing(3.4, 1.8, 0.0, 1.1);
		// ---- 5. The offer: its words, the contract held long enough to read, the cursor going to REFUSE and clicking.
		s = shot("offer_words", 90);
		s.start = () -> {
			keys(false, false);
			ContractScreen.showCursor = true;
		};
		s.each = t -> {
			wantYaw = EAST;
			if (mc().currentScreen instanceof ContractScreen && t > 4) {
				endShot = true;
			}
		};
		s.cam = follow(3.4, 1.7, 0.4, 5.0, 1.0);
		s = shot("offer_screen", 76);
		s.each = t -> cursorTo(false, t);
		s.cam = follow(3.4, 1.7, 0.4, 5.0, 1.0);
		// ---- 6. The cord, seen from in front (the transformation), the walls bleeding behind.
		s = shot("bleed", 66);
		s.start = () -> {
			ContractScreen.showCursor = false;
			pressG();
		};
		s.each = t -> {
			wantYaw = EAST;
		};
		s.cam = facing(3.0, 1.7, 0.5, 1.2);
		s = shot("grow", 36);
		s.cam = cam(25.5, floorY(7) + 1.4, 7.6, 32.5, floorY(7) + 1.7, 6.4);
		fightShots();
	}


	private static void fightShots() {
		final float EAST = -90.0F;
		Shot s;
		// A low three-quarter view from the side: its face (eyes, mouths) and the saws both in it, each beat nearer the heart.
		s = shot("fight_a", 70);
		s.start = () -> {
			server(w -> {
				for (EternityDevilEntity d : w.getEntitiesByType(io.github.kortev.chainsawman.ChainsawMan.ETERNITY_DEVIL, e -> true)) {
					d.setHealth(235.0F);
				}
			});
			// He runs at it along the north side, so the camera on the south one is never near him.
			Vec3d here = HotelBuilder.local(origin, ROT, mc().player.getPos());
			tp(here.x, floorY(7), 5.7, EAST);
			keys(true, true);
		};
		s.each = HotelSelfTest::fightTick;
		s.cam = cam(26.0, floorY(7) + 1.25, 7.65, 32.6, floorY(7) + 1.7, 6.3);
		s = shot("fight_b", 60);
		s.each = HotelSelfTest::fightTick;
		s.cam = cam(27.4, floorY(7) + 1.2, 7.7, 32.4, floorY(7) + 1.6, 6.4);
		s = shot("fight_c", 120);
		s.each = t -> {
			fightTick(t);
			for (Entity e : mc().world.getEntities()) {
				if (e instanceof EternityDevilEntity d && d.isDead() && t > 4) {
					endShot = true;
				}
			}
		};
		s.cam = cam(29.4, floorY(7) + 1.25, 7.8, 32.4, floorY(7) + 1.45, 6.2);
		// The collapse, from the front and the side: it sinks, the eyes shut, the mouths go slack.
		s = shot("dying", 80);
		s.slow = 0.8;
		s.start = () -> keys(false, false);
		s.each = t -> {
			for (Entity e : mc().world.getEntities()) {
				if (e instanceof net.minecraft.entity.ItemEntity item && t > 6
						&& (item.getStack().getItem() == io.github.kortev.chainsawman.ChainsawMan.ETERNITY_FLESH || item.getStack().getItem() == io.github.kortev.chainsawman.ChainsawMan.ETERNITY_BLOOD)) {
					endShot = true;
				}
			}
		};
		s.cam = cam(26.2, floorY(7) + 1.25, 7.7, 33.4, floorY(7) + 1.6, 6.2);
		// The loot, low and close.
		s = shot("loot", 44);
		s.start = () -> keys(false, false);
		s.cam = cam(30.5, floorY(7) + 0.95, 7.4, 31.7, floorY(7) + 0.25, 6.4);
		// The lights dying, along the corridor.
		s = shot("lights", 56);
		s.cam = cam(34.0, floorY(7) + 1.6, 6.5, 12.0, floorY(7) + 1.6, 6.5);
	}

	/** The scripted mouse on the contract screen: from the top, onto ACCEPT or REFUSE over {@code t} 14 to 46, holding, and the click at 62. */
	private static void cursorTo(boolean accept, int t) {
		if (!(mc().currentScreen instanceof ContractScreen screen)) {
			return;
		}
		double[] to = screen.centre(accept);
		double fromX = mc().getWindow().getScaledWidth() * 0.5;
		double fromY = mc().getWindow().getScaledHeight() * 0.22;
		double k = MathHelper.clamp((t - 14) / 32.0, 0.0, 1.0);
		k = k * k * (3.0 - 2.0 * k);
		screen.mouseMoved(MathHelper.lerp(k, fromX, to[0]), MathHelper.lerp(k, fromY, to[1]));
		if (t == 62) {
			screen.mouseClicked(to[0], to[1], 0);
		}
	}

	/**
	 * -Ptarget=accept: the other choice. Three laps, the devil speaks, the contract is accepted (the real click on ACCEPT); the loop opens,
	 * you walk on past where it would have put you back, to the stairwell door (opened with the real use) and down; staged: you are put at
	 * the stairwell door, then at the lobby, as in the main clip. The hotel goes dark behind you as you walk out (its own logic).
	 */
	private static void shotsAccept() {
		final float EAST = -90.0F;
		Shot s = shot("a_run", 40);
		s.start = () -> {
			tp(14.2, floorY(7), 6.5, EAST);
			wantYaw = EAST;
			wantPitch = 0.0F;
			keys(true, true);
			// (The first laps are real; the time on the floor is taken to the edge so the offer comes after two.)
		};
		s.cam = follow(3.2, 1.9, 0.0, 9.0, 1.1);
		s = shot("a_laps", 70);
		s.firstPerson = true;
		s.each = t -> {
			Vec3d p = HotelBuilder.local(origin, ROT, you(0.0));
			Vec3d sign = world(16.0, floorY(7) + 2.3, 7.0);
			glance = Math.abs(p.x - 16.0) < 5.0 && p.x < 17.5 ? sign : null;
		};
		s.cam = EYES;
		s.fov = 74.0F;
		s = shot("a_flesh", 40);
		s.start = () -> glance = null;
		s.cam = facing(3.4, 1.8, 0.0, 1.1);
		s = shot("a_words", 90);
		s.start = () -> {
			keys(false, false);
			ContractScreen.showCursor = true;
		};
		s.each = t -> {
			wantYaw = EAST;
			if (mc().currentScreen instanceof ContractScreen && t > 4) {
				endShot = true;
			}
		};
		s.cam = follow(3.4, 1.7, 0.4, 5.0, 1.0);
		s = shot("a_screen", 76);
		s.each = t -> cursorTo(true, t);
		s.cam = follow(3.4, 1.7, 0.4, 5.0, 1.0);
		// The loop opens: walk on past where it would have put you back.
		s = shot("a_open", 40);
		s.start = () -> {
			ContractScreen.showCursor = false;
			keys(true, true);
		};
		s.each = t -> wantYaw = EAST;
		s.cam = follow(3.4, 1.9, 0.0, 9.0, 1.1);
		s = shot("a_turn", 64);
		s.start = () -> {
			tp(19.0, floorY(7), 6.2, 90.0F);
			wantYaw = 90.0F;
			keys(true, false);
		};
		s.each = t -> {
			Vec3d p = HotelBuilder.local(origin, ROT, you(0.0));
			if (t < 34) {
				// Along to the stairwell door, and stop at it.
				wantYaw = yawTo(you(0.0), world(13.5, floorY(7) + 0.9, 5.2));
				if (p.x < 14.4) {
					keys(false, false);
				}
				if (t == 30) {
					MinecraftClient c = mc();
					BlockPos pos = HotelBuilder.world(origin, ROT, 13, (int) floorY(7), 4);
					if (c.interactionManager != null) {
						c.interactionManager.interactBlock(c.player, net.minecraft.util.Hand.MAIN_HAND,
								new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(pos).add(0.0, 0.0, 0.45), net.minecraft.util.math.Direction.SOUTH, pos, false));
					}
				}
			} else {
				// Through it, into the stairwell.
				wantYaw = 180.0F;
				keys(true, false);
			}
		};
		s.cam = cam(19.5, floorY(7) + 1.6, 6.6, 13.6, floorY(7) + 1.0, 4.8);
		s = shot("a_lobby", 40);
		s.start = () -> {
			tp(24.0, 1.0, 5.6, 0.0F);
			wantYaw = 0.0F;
			keys(true, false);
		};
		s.each = t -> {
			Vec3d p = you(0.0);
			wantYaw = yawTo(p, world(27.5, 1.0, -2.0));
		};
		s.cam = follow(4.2, 2.1, 0.8, 3.0, 1.2);
		// Out of the doors; the windows go from lit to dark as he leaves.
		s = shot("a_out", 62);
		s.start = () -> {
			tp(27.5, 1.0, 0.8, 180.0F);
			wantYaw = 180.0F;
			keys(true, false);
		};
		s.each = t -> wantYaw = 180.0F;
		s.cam = cam(32.0, 2.0, -13.5, 21.0, 4.8, 0.0);
	}

	/** A climb shot: you walk out at the stairwell end; the camera is fixed. */
	private static void climb(Shot s, int floor, double cx, double cz, double ax, double az, float fov, double fromX, double zLane) {
		s.start = () -> {
			tp(fromX, floorY(floor), zLane, -90.0F);
			wantYaw = -90.0F;
			keys(true, false);
		};
		s.cam = cam(cx, floorY(floor) + 1.55, cz, ax, floorY(floor) + 2.2, az);
		s.fov = fov;
	}

	/** Every tick of the fight: close on the devil's front, looking at its heart (or its mouth), slashing as the saws reach. */
	private static void fightTick(int t) {
		MinecraftClient c = mc();
		if (c.player == null) {
			return;
		}
		Vec3d p = HotelBuilder.local(origin, ROT, c.player.getPos());
		Vec3d eye = c.player.getEyePos();
		Vec3d heart = world(EternityDevilEntity.HEART_X, EternityDevilEntity.HEART_Y, EternityDevilEntity.LOCAL_Z);
		wantYaw = yawTo(eye, heart);
		wantPitch = MathHelper.clamp(pitchTo(eye, heart), -20.0F, 20.0F);
		boolean near = p.x > 28.7;
		keys(!near, !near);
		if (t % 10 == 0) {
			ShootingStar.LOGGER.info("[selftest] hotel fight t={} you at local {} form {} near {}", t, p, formOn, near);
		}
		if (formOn && near && ticks - slashedAt >= 13) {
			slashedAt = ticks;
			Entity devil = null;
			for (Entity e : c.world.getEntities()) {
				if (e instanceof EternityDevilEntity) {
					devil = e;
				}
			}
			if (devil == null || !devil.isAlive()) {
				ShootingStar.LOGGER.info("[selftest] hotel fight: no devil to slash ({})", devil);
			}
			if (devil != null && devil.isAlive()) {
				int id = devil.getId();
				float hp = ((EternityDevilEntity) devil).getHealth();
				serverRef.execute(() -> {
					ServerPlayerEntity you = you(serverRef);
					boolean did = Saw.slash(you, id);
					ShootingStar.LOGGER.info("[selftest] hotel slash {} (devil {} heart {}, you at {})", did, hp, ((EternityDevilEntity) serverRef.getOverworld().getEntityById(id)).heartOut(), you.getPos());
				});
				c.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
			}
		}
	}

	private static void clip(MinecraftClient client, IntegratedServer server, int f) {
		if (client.player == null || client.world == null) {
			return;
		}
		serverRef = server;
		if (f == 1) {
			shots();
			client.options.hudHidden = false;
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			boolean accepting = "accept".equals(target());
			boolean fightOnly = "fight".equals(target());
			server.execute(() -> {
				ServerPlayerEntity you = you(server);
				if (!accepting) {
					// (In the main clip kortev is Denji, with Pochita's heart: he refuses, and pulls the cord.)
					Heart.give(you);
				}
				Blood.set(you, Blood.MAX);
				if (fightOnly) {
					// (-Ptarget=fight: straight to the fight, to look at it: in the form, on the loop floor, the devil growing.)
					Form.start(you);
					Vec3d w = world(21.0, 29.0, 6.5);
					you.teleport(server.getOverworld(), w.x, w.y, w.z, -90.0F, 0.0F);
					Hotel.startFight(server.getOverworld(), home(server), server.getOverworld().getTime());
				}
			});
			clipF0 = 100;
		}
		if (f < clipF0) {
			// Warm-up: the first shot's view held where it will be, so the chunks under it are built before the recording starts.
			Capture.camera = cam(17.0, 2.0, -11.5, 26.0, 4.2, -2.0);
			lens = 70.0F;
			return;
		}
		if (f == clipF0 && capturing()) {
			Capture.start(client, client.runDirectory.toPath().resolve("capture"));
		}
		formOn = io.github.kortev.chainsawman.client.ClientForms.on(client.player);
		int e = f - clipF0 + skip;
		int starts = 0;
		for (int i = 0; i < SHOTS.size(); i++) {
			Shot sh = SHOTS.get(i);
			if (e >= starts && e < starts + sh.ticks) {
				if (shotIndex != i) {
					shotIndex = i;
					shotAt = f;
					shotCaptureAt = Capture.active() ? Capture.time() : 0.0;
					ShootingStar.LOGGER.info("[selftest] hotel shot {} at clip tick {}", sh.name, e);
					client.options.setPerspective(sh.firstPerson ? Perspective.FIRST_PERSON : Perspective.THIRD_PERSON_BACK);
					lens = sh.fov;
					if (sh.slow < 1.0 && Capture.active()) {
						Capture.slow(sh.slow, (int) (sh.ticks * 1.5 * sh.slow));
					}
					sh.start.run();
					final Shot current = sh;
					final double at = shotCaptureAt;
					Capture.camera = tt -> current.cam.apply(Math.max(0.0, Capture.time() - at));
				}
				int local = f - shotAt;
				sh.each.accept(local);
				if (endShot) {
					endShot = false;
					skip += Math.max(0, sh.ticks - local - 1);
				}
				if (local == 2) {
					still(client, String.format("clip_%02d_%s_a", i, sh.name));
				} else if (local == sh.ticks / 2) {
					still(client, String.format("clip_%02d_%s_b", i, sh.name));
				} else if (local == sh.ticks - 2) {
					still(client, String.format("clip_%02d_%s_c", i, sh.name));
				}
				break;
			}
			starts += sh.ticks;
		}
		int total = 0;
		for (Shot sh : SHOTS) {
			total += sh.ticks;
		}
		if (e >= total + 6) {
			stage = Stage.DONE;
		}
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
