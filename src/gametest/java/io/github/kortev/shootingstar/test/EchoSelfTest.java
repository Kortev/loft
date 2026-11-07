package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.client.ChainsawManClient;
import io.github.kortev.chainsawman.contract.Contract;
import io.github.kortev.chainsawman.contract.Contracts;
import io.github.kortev.chainsawman.contract.Echo;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.shootingstar.ShootingStar;
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
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

/**
 * With -Dshootingstar.selftest=echo: the clip and the stills of Echo, the Eternity contract's power (kortev: "the burst, the ring
 * at different heights, you walking in so it closes, jumping (they jump), a combo (the ripple), the finale"). On a hillside of
 * uneven ground (a slope, ledges, pillars, a pit) high over the world, you face an iron golem set on you; you press the real key
 * (U), walk in, jump, swing (a client swing that goes to the server as a real one), and the finale comes by itself. Cameras:
 * three quarters on and pulled back to see the whole ring, then one close shot of the finale. With -Pcapture=true it is recorded
 * with its sounds (Capture) into build/selftest/capture; either way stills are saved as echo_*.png.
 * <pre>./gradlew runSelftest -Pselftest=echo -Pusername=Kortev -Pcapture=true</pre>
 */
public class EchoSelfTest implements ClientModInitializer {
	private enum Stage {
		WAIT_WORLD, SETUP, SETTLE, RUN, DONE
	}

	/** The arena: where, how high the level ground is, and the golem and you on it (arena coordinates, along x and z). */
	private static final int CX = 2048;
	private static final int CZ = 2048;
	private static final int BASE = 150;
	private static final double OWNER_AT = -9.5;
	/** The golem's walking speed (the game's own is 0.25): it plods. */
	private static final double GOLEM_SPEED = 0.035;

	/** The clip's beats (ticks since the clip began). */
	private static final int PRESS = 24;
	private static final int WALK_IN = 80;
	private static final int WALK_IN_END = 92;
	private static final int JUMP_1 = 150;
	private static final int JUMP_2 = 176;
	private static final int[] SWINGS = {206, 213, 220, 227, 234, 241};
	private static final int STEP_BACK = 262;
	private static final int STEP_BACK_END = 273;
	/** The finale begins (the press and the echo's length) and the clip ends a little after its blow lands. */
	private static final int FINALE = PRESS + 1 + Echo.LASTS;
	private static final int END = FINALE + Echo.HOLD + 5 + 42;
	private static final int[] STILLS = {10, PRESS + 6, PRESS + 22, 70, 118, 158, 224, 300, FINALE + 8, FINALE + Echo.HOLD + 7, FINALE + Echo.HOLD + 20};
	private static final String[] STILL_NAMES = {"0_before", "1_burst", "2_ring", "3_wide", "4_closing", "5_jump", "6_combo", "7_late", "8_finale_turn", "9_finale_boom", "9b_after"};

	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static int golemId = -1;
	private static BlockPos centre;
	private static Vec3d ownerStart;
	private static float wantYaw = Float.NaN;
	private static float headYaw;
	private static float headPitch;
	private static int shots;

	/** The lens (degrees of view) the clip wants now, 0 for the player's own. */
	private static float lens;

	public static float lens() {
		return active() && stage == Stage.RUN ? lens : 0.0F;
	}

	public static boolean active() {
		return "echo".equals(System.getProperty("shootingstar.selftest"));
	}

	private static boolean capturing() {
		return "true".equals(System.getProperty("shootingstar.selftest.capture"));
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
			ShootingStar.LOGGER.error("[selftest] echo timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-echo-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		if (capturing()) {
			ServerTickEvents.START_SERVER_TICK.register(server -> Capture.serverTickStart());
			ServerTickEvents.END_SERVER_TICK.register(server -> Capture.serverTickEnd());
		}
		// The golem really is after you; what it does to you is nothing (it must be able to target you, so you are not invulnerable).
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(stage != null && entity instanceof ServerPlayerEntity));
		ClientTickEvents.END_CLIENT_TICK.register(EchoSelfTest::tick);
	}

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
				if (ticks > 260 && centre != null) {
					stage = Stage.RUN;
					ticks = 0;
				}
			}
			case RUN -> {
				client.getToastManager().clear();
				run(client, server, ticks);
			}
			case DONE -> {
				Capture.stop();
				ShootingStar.LOGGER.info("[selftest] echo: done");
				stage = null;
				client.scheduleStop();
			}
			default -> {
			}
		}
	}

	// ---- The clip.

	private static void run(MinecraftClient client, IntegratedServer server, int f) {
		if (client.player == null || client.world == null) {
			return;
		}
		Entity golem = client.world.getEntityById(golemId);
		Vec3d g = golem != null ? golem.getLerpedPos(1.0F) : Vec3d.ofBottomCenter(centre.up());
		if (f == 1) {
			client.options.hudHidden = false;
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			headYaw = client.player.getYaw();
			headPitch = client.player.getPitch();
			server.execute(() -> golem(server));
			if (capturing()) {
				Capture.start(client, client.runDirectory.toPath().resolve("capture"));
			}
		}
		// You look at the golem all through, as a person's head turns (eased); the camera is the clip's own.
		Vec3d eye = client.player.getEyePos();
		Vec3d to = g.add(0.0, 1.3, 0.0).subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z)));
		headYaw += MathHelper.clamp(MathHelper.wrapDegrees(yaw - headYaw) * 0.35F, -25.0F, 25.0F);
		headPitch += (pitch - headPitch) * 0.35F;
		client.player.setYaw(headYaw);
		client.player.setHeadYaw(headYaw);
		client.player.setPitch(headPitch);
		// The keys: the real ones.
		if (f == PRESS) {
			KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(ChainsawManClient.USE_CONTRACT.getBoundKeyTranslationKey()));
		}
		client.options.forwardKey.setPressed(f >= WALK_IN && f < WALK_IN_END);
		client.options.backKey.setPressed(f >= STEP_BACK && f < STEP_BACK_END);
		client.options.jumpKey.setPressed(f == JUMP_1 || f == JUMP_1 + 1 || f == JUMP_2 || f == JUMP_2 + 1);
		for (int swing : SWINGS) {
			if (f == swing) {
				client.player.swingHand(Hand.MAIN_HAND);
			}
		}
		lens = f >= FINALE + 3 && f < END ? 100.0F : 0.0F;
		if (f < END) {
			camera(client, f);
		}
		if (f % 20 == 0 && server != null) {
			server.execute(() -> {
				ServerPlayerEntity you = server.getPlayerManager().getPlayerList().get(0);
				net.minecraft.entity.LivingEntity aim = Echo.target(you);
				double sum = 0.0;
				double max = 0.0;
				int n = 0;
				for (var e : Echo.copies(you)) {
					double d = aim == null ? 0.0 : Math.hypot(e.getX() - aim.getX(), e.getZ() - aim.getZ());
					sum += d;
					max = Math.max(max, d);
					n++;
				}
				ShootingStar.LOGGER.info("[selftest] echo f={} copies={} mean {} max {} owner-to-target {} golem at {}", f, n, String.format("%.1f", sum / Math.max(1, n)), String.format("%.1f", max),
						aim == null ? -1 : String.format("%.1f", Math.hypot(you.getX() - aim.getX(), you.getZ() - aim.getZ())), aim == null ? null : aim.getBlockPos());
			});
		}
		for (int i = 0; i < STILLS.length; i++) {
			if (f == STILLS[i]) {
				still(client, STILL_NAMES[i]);
			}
		}
		// After the clip (recording stopped): the same again, to photograph what others see of you (drawn as one of the copies).
		if (f == END) {
			Capture.stop();
			Capture.camera = null;
			server.execute(() -> Echo.ready(server.getPlayerManager().getPlayerList().get(0)));
		}
		if (f == END + 4) {
			KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(ChainsawManClient.USE_CONTRACT.getBoundKeyTranslationKey()));
		}
		if (f == END + 30) {
			io.github.kortev.chainsawman.client.ClientEcho.seeOwnDisguise = true;
		}
		if (f == END + 32) {
			still(client, "disguise_seen_by_others");
		}
		if (f == END + 34) {
			io.github.kortev.chainsawman.client.ClientEcho.seeOwnDisguise = false;
		}
		if (f == END + 36) {
			still(client, "disguise_you_see_yourself");
		}
		if (f >= END + 40) {
			stage = Stage.DONE;
		}
	}

	/** The cameras: three quarters on and pulled back to see the whole ring, slowly circling; then the finale up close. */
	private static void camera(MinecraftClient client, int f) {
		Capture.camera = t -> {
			Entity golem = client.world == null ? null : client.world.getEntityById(golemId);
			float partial = (float) (t - Math.floor(t));
			Vec3d g = golem != null ? golem.getLerpedPos(partial) : Vec3d.ofBottomCenter(centre.up());
			Vec3d you = client.player.getLerpedPos(partial);
			if (t < PRESS + 2) {
				// Before it: close behind you, over your shoulder, the golem ahead.
				Vec3d at = you.add(-1.6, 2.3, -3.2);
				return look(at, g.add(0.0, 1.4, 0.0));
			}
			if (t >= FINALE + 3 && t < END) {
				// The finale, close: behind you and low, the crowd streaking in from beyond.
				double k = span(t, FINALE + 3, END);
				Vec3d at = you.add(-3.6 + 0.8 * k, 1.5 + 0.2 * k, -0.4 + 0.3 * k);
				return look(at, you.add(0.1, 1.1, 1.8));
			}
			// The wide, circling a little: the whole ring in the picture.
			double angle = Math.toRadians(-52.0 + 0.16 * (t - PRESS));
			double dist = t < WALK_IN - 12 ? 17.0 : 14.0;
			Vec3d off = new Vec3d(Math.sin(angle) * -dist, 9.0, Math.cos(angle) * -dist);
			Vec3d at = g.add(off);
			return look(at, g.add(0.0, 0.8, 0.0).lerp(you.add(0.0, 0.8, 0.0), 0.18));
		};
	}

	private static double span(double t, double from, double to) {
		return MathHelper.clamp((t - from) / (to - from), 0.0, 1.0);
	}

	private static Capture.Pose look(Vec3d at, Vec3d look) {
		Vec3d to = look.subtract(at);
		float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
		return new Capture.Pose(at.x, at.y, at.z, yaw, pitch);
	}

	private static void still(MinecraftClient client, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot echo_{}", name);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, "echo_" + name + ".png", client.getFramebuffer(), message -> {
		});
		shots++;
	}

	// ---- The set.

	/** How high the ground is above the level plateau at {@code (dx, dz)} from the middle: two hills and a hollow, a slope, ledges, a pillar and a pit. */
	private static int rise(int dx, int dz) {
		double r = Math.hypot(dx, dz);
		double hills = 3.6 * Math.exp(-(Math.pow(dx - 7.0, 2) + Math.pow(dz - 1.0, 2)) / 20.0)
				+ 2.7 * Math.exp(-(Math.pow(dx + 6.5, 2) + Math.pow(dz + 4.0, 2)) / 13.0)
				+ 2.2 * Math.exp(-(Math.pow(dx + 2.0, 2) + Math.pow(dz - 8.5, 2)) / 12.0)
				- 1.4 * Math.exp(-(Math.pow(dx - 1.0, 2) + Math.pow(dz + 5.0, 2)) / 5.0);
		int h = (int) Math.round(hills * MathHelper.clamp((r - 2.0) / 3.0, 0.0, 1.0));
		// A ledge to the north-east with a block on it, a pillar two wide, a step with a step on it to the south-west.
		if (dx >= 3 && dx <= 7 && dz >= 6 && dz <= 10) {
			h = Math.max(h, 2);
		}
		if (dx >= 4 && dx <= 5 && dz >= 7 && dz <= 8) {
			h = 3;
		}
		if (dx >= -1 && dx <= 0 && dz >= 5 && dz <= 6) {
			h = 3;
		}
		if (dx >= -8 && dx <= -4 && dz >= -9 && dz <= -5) {
			h = Math.max(h, 1);
		}
		if (dx >= -7 && dx <= -6 && dz >= -8 && dz <= -7) {
			h = 2;
		}
		// A pit.
		if (dx >= 3 && dx <= 4 && dz >= -4 && dz <= -3) {
			h = -1;
		}
		return h;
	}

	/**
	 * On the server, as the clip begins: the iron golem, set on you, in the middle of the hillside, twelve blocks off. Its own AI, but
	 * slow (the ring is measured against the target, and a golem that was on top of you would close it to a huddle): it plods at you
	 * all through, and the copies and its anger make it turn on them.
	 */
	private static void golem(IntegratedServer server) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = server.getOverworld();
		IronGolemEntity golem = EntityType.IRON_GOLEM.create(world);
		golem.refreshPositionAndAngles(CX + 0.5, BASE + 1.0, CZ + 0.5, 0.0F, 0.0F);
		golem.setPersistent();
		golem.setPlayerCreated(false);
		golem.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(GOLEM_SPEED);
		golem.setAngryAt(player.getUuid());
		golem.chooseRandomAngerTime();
		world.spawnEntity(golem);
		golem.setTarget(player);
		golemId = golem.getId();
	}

	/** On the server: the hillside, the golem set on you, you with the contract and no heart, out of the form. */
	private static void setup(IntegratedServer server) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = server.getOverworld();
		world.setTimeOfDay(11800);
		world.setWeather(12000, 0, false, false);
		server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_LOOT).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_GRIEFING).set(false, server);
		for (int cx = (CX - 40) >> 4; cx <= (CX + 40) >> 4; cx++) {
			for (int cz = (CZ - 40) >> 4; cz <= (CZ + 40) >> 4; cz++) {
				world.setChunkForced(cx, cz, true);
			}
		}
		// What an earlier take left here.
		for (Entity e : world.getOtherEntities(player, new net.minecraft.util.math.Box(CX - 60, BASE - 30, CZ - 60, CX + 60, BASE + 40, CZ + 60))) {
			if (!(e instanceof ServerPlayerEntity)) {
				e.discard();
			}
		}
		BlockState grass = Blocks.GRASS_BLOCK.getDefaultState();
		BlockState dirt = Blocks.DIRT.getDefaultState();
		BlockState stone = Blocks.STONE.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		int r = 36;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.hypot(dx, dz);
				if (d > r) {
					continue;
				}
				int top = BASE + (d <= 24 ? rise(dx, dz) : -(int) ((d - 24) * 1.1) - 1);
				for (int y = BASE - 26; y <= BASE + 16; y++) {
					BlockState state = y > top ? air : y == top ? grass : y >= top - 3 ? dirt : stone;
					if (y == top && d > 30) {
						state = stone;
					}
					world.setBlockState(new BlockPos(CX + dx, y, CZ + dz), state, Block.NOTIFY_LISTENERS);
				}
			}
		}
		centre = new BlockPos(CX, BASE, CZ);
		if (Form.on(player)) {
			Form.end(player);
		}
		Downed.up(player);
		Heart.take(player);
		Contracts.grant(player, Contract.ETERNITY);
		Echo.ready(player);
		player.changeGameMode(GameMode.SURVIVAL);
		player.setHealth(player.getMaxHealth());
		int oy = BASE + rise(0, (int) OWNER_AT) + 1;
		ownerStart = new Vec3d(CX + 0.5, oy, CZ + 0.5 + OWNER_AT);
		player.teleport(world, ownerStart.x, ownerStart.y, ownerStart.z, 0.0F, 4.0F);
		ShootingStar.LOGGER.info("[selftest] echo: arena at {}, you at {}", centre, ownerStart);
	}
}
