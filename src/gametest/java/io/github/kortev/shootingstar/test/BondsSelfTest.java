package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.contract.Bond;
import io.github.kortev.chainsawman.contract.Contract;
import io.github.kortev.chainsawman.contract.Contracts;
import io.github.kortev.chainsawman.contract.Forever;
import io.github.kortev.chainsawman.form.Blood;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Fallen;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.form.HeartLevel;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

/**
 * With -Dshootingstar.selftest=bonds: six stills of contract bonds and the heart's levels, for checking them (not a clip). A flat grass
 * platform in daylight and you (Kortev); everything shown is the mod's own through its real server paths. Staged: the platform, the husks
 * that are held, where everyone stands, how far through each thing the shot waits.
 * <ol>
 * <li>Forever at bond 1 (a 9-block cube, the line receding) from a spot high up to one side, over the whole line;</li>
 * <li>Forever at bond 3 from the same spot (the 13-block cube holding the two husks beside, the line running further);</li>
 * <li>"Not yet": you grey, running, with your copies trailing behind;</li>
 * <li>a bond-up moment on your own screen: the devil's line, the grey, the contract HUD;</li>
 * <li>Chainsaw Man at heart level 1;</li>
 * <li>and at level 3 (more teeth on the saws), from the same spot.</li>
 * </ol>
 * <pre>./gradlew runSelftest -Pselftest=bonds -Pusername=Kortev -Pwidth=1080 -Pheight=1920 "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"</pre>
 */
public class BondsSelfTest implements ClientModInitializer {
	private enum Stage {
		WAIT_WORLD, SETUP, SETTLE, RUN, DONE
	}

	private static volatile BlockPos middle;
	private static final int RADIUS = 46;
	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static float wantYaw = Float.NaN;
	private static float wantPitch;
	/** Whether you run along +x each server tick (the "Not yet" shot). */
	private static volatile boolean running;
	private static final List<HuskEntity> HUSKS = new ArrayList<>();

	public static boolean active() {
		return "bonds".equals(System.getProperty("shootingstar.selftest"));
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
			ShootingStar.LOGGER.error("[selftest] bonds timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-bonds-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		ServerTickEvents.END_SERVER_TICK.register(BondsSelfTest::runTick);
		ClientTickEvents.END_CLIENT_TICK.register(BondsSelfTest::tick);
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
				ShootingStar.LOGGER.info("[selftest] bonds: done");
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
		ShootingStar.LOGGER.info("[selftest] screenshot bonds_{} camera {} player {} middle {}", name, client.gameRenderer.getCamera().getPos(), client.player.getPos(), middle);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, "bonds_" + name + ".png", client.getFramebuffer(), message -> {
		});
	}

	// ------------------------------------------------------------------ the stage

	private static int surface(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		return world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
	}

	/** A flat grass platform round you, daylight, clear skies; you at its middle. */
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
		ShootingStar.LOGGER.info("[selftest] bonds: middle {}", middle);
	}

	/** You as new: no heart, no contract, no bond, no form, no husks. */
	private static void reset(MinecraftServer server) {
		ServerPlayerEntity you = you(server);
		running = false;
		Form.end(you);
		Downed.up(you);
		Fallen.up(you, null);
		you.removeAttached(Heart.MERGED);
		you.removeAttached(HeartLevel.XP);
		Contracts.clear(you);
		Bond.set(you, Contract.ETERNITY, 0);
		Forever.ready(you);
		Forever.endNotYet(you);
		for (HuskEntity h : HUSKS) {
			h.discard();
		}
		HUSKS.clear();
		you.setHealth(you.getMaxHealth());
		you.getHungerManager().setFoodLevel(20);
		you.changeGameMode(GameMode.SURVIVAL);
		you.getInventory().clear();
		Blood.set(you, Blood.MAX);
		Vec3d at = at(0.0, 0.0, 0.0);
		you.teleport(you.getServerWorld(), at.x, at.y, at.z, 0.0F, 0.0F);
	}

	/** A husk standing still, facing south (+z), with all its health and no mind. */
	private static HuskEntity husk(ServerWorld world, double dx, double dz) {
		Vec3d p = at(dx, 0.0, dz);
		HuskEntity husk = new HuskEntity(EntityType.HUSK, world);
		husk.refreshPositionAndAngles(p.x, p.y, p.z, 0.0F, 0.0F);
		husk.setBodyYaw(0.0F);
		husk.setHeadYaw(0.0F);
		husk.setAiDisabled(true);
		husk.setPersistent();
		husk.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200.0);
		husk.setHealth(200.0F);
		world.spawnEntity(husk);
		HUSKS.add(husk);
		return husk;
	}

	/** Three husks side by side 14 blocks south, you off to the side 22 south looking at the middle one, and Forever called on it. */
	private static void line(MinecraftServer server, int bond) {
		reset(server);
		ServerPlayerEntity you = you(server);
		ServerWorld world = you.getServerWorld();
		HuskEntity a = husk(world, 0.0, 14.0);
		husk(world, 5.5, 14.0);
		husk(world, -5.5, 14.0);
		Contracts.grant(you, Contract.ETERNITY);
		Bond.set(you, Contract.ETERNITY, bond);
		Vec3d p = at(3.0, 0.0, 28.5);
		you.teleport(world, p.x, p.y, p.z, yawTo(p, a.getPos()), pitchTo(p.add(0.0, 1.6, 0.0), a.getPos().add(0.0, 1.0, 0.0)));
		you.setNoGravity(true);
	}

	private static void call(MinecraftServer server) {
		ServerPlayerEntity you = you(server);
		// (The last shot's cube has ended with its husks, and its cooldown is skipped.)
		Forever.ready(you);
		you.getHungerManager().setFoodLevel(20);
		Forever.use(you);
		for (HuskEntity h : HUSKS) {
			Forever.extend(h, 400);
		}
	}

	/** Each server tick: you run along +x while out of time ("Not yet" shot). */
	private static void runTick(MinecraftServer server) {
		if (!running || stage != Stage.RUN || server.getPlayerManager().getPlayerList().isEmpty()) {
			return;
		}
		ServerPlayerEntity you = you(server);
		you.teleport(you.getServerWorld(), you.getX() + 0.3, you.getY(), you.getZ(), -90.0F, 0.0F);
		you.setNoGravity(true);
	}

	// ------------------------------------------------------------------ the shots

	/** One still: what starts it, the delayed steps (ticks after the start), how long it waits, its camera (null: your own eyes), and your look. */
	private record Shot(String name, int waits, Consumer<IntegratedServer> start, java.util.Map<Integer, Consumer<IntegratedServer>> later, DoubleFunction<Capture.Pose> camera,
			float yaw, float pitch, boolean firstPerson) {
	}

	private static final List<Shot> SHOTS = new ArrayList<>();
	private static int shot = -1;
	private static int shotAt;

	private static void shots() {
		if (!SHOTS.isEmpty()) {
			return;
		}
		DoubleFunction<Capture.Pose> behind = cam(at(-11.0, 15.0, -9.0), at(0.5, 0.0, 17.0));
		// 1 and 2. Forever at bond 1 and at bond 3, the same husks, the same spot.
		SHOTS.add(new Shot("1_forever_bond1", 30, s -> s.execute(() -> line(s, 0)), java.util.Map.of(8, s -> s.execute(() -> call(s))), behind, 0.0F, 0.0F, false));
		SHOTS.add(new Shot("2_forever_bond3", 30, s -> s.execute(() -> line(s, Bond.THREE)), java.util.Map.of(10, s -> s.execute(() -> call(s))), behind, 0.0F, 0.0F, false));
		// 3. Not yet: you running along +x out of time, grey, your copies trailing behind; the camera runs with you.
		SHOTS.add(new Shot("3_not_yet", 17, s -> s.execute(() -> {
			reset(s);
			ServerPlayerEntity you = you(s);
			Contracts.grant(you, Contract.ETERNITY);
			Bond.set(you, Contract.ETERNITY, Bond.THREE);
			Vec3d p = at(-6.0, 0.0, 0.0);
			you.teleport(you.getServerWorld(), p.x, p.y, p.z, -90.0F, 0.0F);
		}), java.util.Map.of(6, s -> s.execute(() -> {
			Forever.stepOut(you(s));
			running = true;
		})), t -> {
			net.minecraft.client.network.ClientPlayerEntity me = MinecraftClient.getInstance().player;
			Vec3d p = me == null ? Vec3d.ZERO : me.getPos();
			Vec3d from = p.add(-5.5, 1.5, -8.5);
			Vec3d look = p.add(-1.8, 1.0, 0.0);
			return new Capture.Pose(from.x, from.y, from.z, yawTo(from, look), pitchTo(from, look));
		}, 0.0F, 0.0F, false));
		// 4. A bond-up on your own screen: the 40th use, the devil's line, the grey, the HUD.
		SHOTS.add(new Shot("4_bond_up", 13, s -> s.execute(() -> {
			reset(s);
			ServerPlayerEntity you = you(s);
			Contracts.grant(you, Contract.ETERNITY);
			Bond.set(you, Contract.ETERNITY, Bond.THREE - 1);
			ServerWorld world = you.getServerWorld();
			husk(world, -3.0, 9.0);
			husk(world, 3.0, 11.0);
		}), java.util.Map.of(5, s -> s.execute(() -> Bond.record(you(s), Contract.ETERNITY))), null, 0.0F, 4.0F, true));
		// 5 and 6. Chainsaw Man at heart level 1 and at level 3, the same spot (the saws, the teeth on them).
		DoubleFunction<Capture.Pose> front = cam(at(1.9, 2.2, 3.2), at(0.0, 1.75, 0.0));
		SHOTS.add(new Shot("5_heart_level1", 40, s -> s.execute(() -> {
			reset(s);
			ServerPlayerEntity you = you(s);
			Heart.give(you);
			Form.start(you);
			you.teleport(you.getServerWorld(), at(0.0, 0.0, 0.0).x, at(0.0, 0.0, 0.0).y, at(0.0, 0.0, 0.0).z, 0.0F, 0.0F);
		}), java.util.Map.of(), front, 0.0F, 0.0F, false));
		SHOTS.add(new Shot("6_heart_level3", 12, s -> s.execute(() -> HeartLevel.set(you(s), HeartLevel.LEVEL_3)), java.util.Map.of(), front, 0.0F, 0.0F, false));
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
		for (java.util.Map.Entry<Integer, Consumer<IntegratedServer>> e : current.later().entrySet()) {
			if (f - shotAt == e.getKey()) {
				e.getValue().accept(server);
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
		// (The last shot's bond-up line is gone from the screen.)
		io.github.kortev.chainsawman.client.ClientBonds.clear();
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
