package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chainsawman.client.ChainsawManClient;
import io.github.kortev.chainsawman.client.ClientForever;
import io.github.kortev.chainsawman.contract.Contract;
import io.github.kortev.chainsawman.contract.Contracts;
import io.github.kortev.chainsawman.contract.Forever;
import io.github.kortev.chainsawman.contract.ForeverCopyEntity;
import io.github.kortev.chainsawman.contract.ForeverHubEntity;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.shootingstar.ShootingStar;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.biome.BiomeKeys;

/**
 * With -Dshootingstar.selftest=forever: the clip and the stills of Forever, the Eternity contract's power (a test clip for kortev,
 * not yet a reel: no captions). A dark forest's edge at dusk, uneven natural ground with a clearing taken out of the trees. A
 * vindicator charges at you off a ledge; you press the real key (U) while it is in the air; the Eternity Devil's flesh comes up round
 * it, eyes opening, the world draining, the line of copies spreading out; you run along the line slashing the copies (the real attack
 * call), a second player (a real server-side player, the same attack path) hits a copy at the far end; the release (the bite, the
 * line snapping in, the burst); then a ravager charges, is locked for the shorter boss time, hit and released. Mobs have their own AI
 * and nothing is damaged by the test. Cameras change about every 1.5 s. After the clip: what a locked player sees (the second player
 * locks you).
 * <pre>./gradlew runSelftest -Pselftest=forever -Pusername=Kortev -Pcapture=true -Pwidth=1080 -Pheight=1920</pre>
 * With -Ptarget=look: only the devil, still, from all round (stills forever_look_*.png).
 */
public class ForeverSelfTest implements ClientModInitializer {
	private enum Stage {
		WAIT_WORLD, SETUP, SETTLE, RUN, DONE
	}

	private static final int RADIUS = 24;
	/** The ledge the vindicator charges off: from this far east of you to that, and how high over the middle's ground. */
	private static final int LEDGE_FROM = 9;
	private static final int LEDGE_TO = 17;
	private static final int LEDGE_HIGH = 4;

	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static volatile BlockPos centre;
	private static volatile Vec3d middle = Vec3d.ZERO;
	private static volatile int vindId = -1;
	private static volatile int ravId = -1;
	/** The mobs of the group (the one in the middle, the one looked at, is {@link #vindId}). */
	private static volatile int[] groupIds = new int[0];
	private static volatile Vec3d groupFrom = Vec3d.ZERO;
	private static int jitterPhase;
	private static int jitterAt = -1;
	private static int jitterFrames;
	private static volatile ServerPlayerEntity friend;
	private static volatile int friendId = -1;
	private static float headYaw = Float.NaN;
	private static float headPitch;
	private static float lens;

	/** The clip's beats, in clip ticks (-1 until they happen). */
	private static int lock1At = -1;
	private static int end1At = -1;
	private static int lock2At = -1;
	private static int end2At = -1;
	private static int press1At = -1;
	private static int press2At = -1;
	private static int ravSpawned = -1;
	private static int lastAttack = -100;
	private static final Map<Integer, Integer> HITS = new HashMap<>();
	private static final List<String> STILLS_DONE = new ArrayList<>();
	private static double shotT0;
	private static String shotKey = "";
	private static Vec3d lastCamera = Vec3d.ZERO;
	private static int hitsTotal;

	public static boolean active() {
		return "forever".equals(System.getProperty("shootingstar.selftest"));
	}

	public static float lens() {
		return active() && stage == Stage.RUN ? lens : 0.0F;
	}

	private static boolean capturing() {
		return "true".equals(System.getProperty("shootingstar.selftest.capture"));
	}

	private static boolean jitterOnly() {
		return "jitter".equals(System.getProperty("shootingstar.selftest.target", ""));
	}

	private static boolean lookOnly() {
		return "look".equals(System.getProperty("shootingstar.selftest.target", ""));
	}

	@Override
	public void onInitializeClient() {
		if (!active()) {
			return;
		}
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(25 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[selftest] forever timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-forever-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		if (capturing()) {
			ServerTickEvents.START_SERVER_TICK.register(server -> Capture.serverTickStart());
			ServerTickEvents.END_SERVER_TICK.register(server -> Capture.serverTickEnd());
		}
		ServerTickEvents.END_SERVER_TICK.register(ForeverSelfTest::friendTick);
		ClientTickEvents.END_CLIENT_TICK.register(ForeverSelfTest::tick);
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
				if (client.player != null && !client.player.isAlive() && ticks % 20 == 0) {
					// (An earlier take left the world with you dead.)
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
				if (centre != null && ticks > 300) {
					stage = Stage.RUN;
					ticks = 0;
				}
			}
			case RUN -> {
				client.getToastManager().clear();
				if (lookOnly()) {
					look(client, server, ticks);
				} else if (jitterOnly()) {
					jitter(client, server, ticks);
				} else {
					run(client, server, ticks);
				}
			}
			case DONE -> {
				Capture.stop();
				ShootingStar.LOGGER.info("[selftest] forever: done");
				stage = null;
				client.scheduleStop();
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ the devil alone

	/** -Ptarget=look: a husk-like vindicator held still, the devil photographed from all round and close. */
	private static void look(MinecraftClient client, IntegratedServer server, int f) {
		if (client.player == null || client.world == null) {
			return;
		}
		if (f == 1) {
			client.options.hudHidden = false;
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			server.execute(() -> {
				ServerWorld world = server.getOverworld();
				MobEntity v = EntityType.VINDICATOR.create(world);
				Vec3d at = middle.add(6.0, 0.0, 0.0);
				v.refreshPositionAndAngles(at.x, groundAt(world, at), at.z, 90.0F, 0.0F);
				v.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
				v.setPersistent();
				v.setAiDisabled(true);
				world.spawnEntity(v);
				vindId = v.getId();
			});
		}
		Entity v = client.world.getEntityById(vindId);
		if (f % 40 == 0) {
			ShootingStar.LOGGER.info("[selftest] forever look f={} vind {} hub {} player {} keyFlag {}", f, v == null ? null : v.getPos(), hubFor(vindId) != null,
					client.player.getPos(), io.github.kortev.chainsawman.contract.ForeverFlags.CLIENT);
		}
		if (v == null) {
			return;
		}
		Vec3d eye = client.player.getEyePos();
		Vec3d to = v.getPos().add(0.0, 1.3, 0.0).subtract(eye);
		client.player.setYaw((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
		client.player.setHeadYaw(client.player.getYaw());
		client.player.setPitch((float) -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z))));
		if (f == 10) {
			KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(ChainsawManClient.USE_CONTRACT.getBoundKeyTranslationKey()));
		}
		if (f == 14) {
			server.execute(() -> Forever.extend(server.getOverworld().getEntityById(vindId), 600));
		}
		ForeverHubEntity hub = hubFor(vindId);
		lookFailsafe(f);
		if (hub == null || f < 14) {
			return;
		}
		int since = f - 14;
		Vec3d t = hub.getPos();
		// Stills: all round at a distance, then each part close.
		int[] at = {6, 14, 24, 36, 50, 64, 80, 96, 112, 128};
		for (int i = 0; i < at.length; i++) {
			if (since == at[i]) {
				double ang = Math.toRadians(110.0 + 18.0 * i);
				double dist = i < 5 ? 8.5 : 4.0;
				double high = i < 5 ? 2.4 : 0.8;
				final Vec3d aimAt = t.add(0.0, i < 5 ? 1.0 : 0.5, 0.0);
				final Vec3d from = t.add(Math.cos(ang) * dist, high, Math.sin(ang) * dist);
				Capture.camera = tt -> look(clearTo(client, aimAt, from), aimAt);
				lens = i < 5 ? 62.0F : 78.0F;
			}
			if (since == at[i] + 3) {
				still(client, "look_" + i);
			}
		}
		if (since > 150) {
			stage = Stage.DONE;
		}
	}

	private static void lookFailsafe(int f) {
		if (f > 400) {
			ShootingStar.LOGGER.error("[selftest] forever look: no moment was held, giving up");
			stage = Stage.DONE;
		}
	}

	// ------------------------------------------------------------------ the clip

	/**
	 * -Ptarget=jitter: a group charges, one press freezes it, and with the camera fixed a few blocks off a frozen mob (then a frozen player)
	 * is filmed at the full frame rate for a second, to be stepped through frame by frame: nothing may move.
	 */
	private static void jitter(MinecraftClient client, IntegratedServer server, int f) {
		if (client.player == null || client.world == null) {
			return;
		}
		if (f == 1) {
			client.options.hudHidden = true;
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			headYaw = client.player.getYaw();
			server.execute(() -> spawnGroup(server));
		}
		Entity centreMob = client.world.getEntityById(vindId);
		ForeverHubEntity hub = hubFor(vindId);
		if (hub == null && !ClientForever.hubs().isEmpty()) {
			hub = ClientForever.hubs().iterator().next();
		}
		if (centreMob != null && press1At < 0 && (flat(centreMob.getPos().subtract(client.player.getPos())).length() < 9.5 && f > 8 || f > 160)) {
			press1At = f;
			pressU();
			server.execute(() -> {
				ServerPlayerEntity you = player(server);
				Entity v = server.getOverworld().getEntityById(vindId);
				ShootingStar.LOGGER.info("[selftest] forever jitter: pressed U at f={}; held {} you yaw {} pitch {} eye {} mob {}", press1At, Forever.lockedBy(you).size(), you.getYaw(), you.getPitch(),
						you.getEyePos(), v == null ? null : v.getBoundingBox());
			});
		}
		if (press1At >= 0 && lock1At < 0 && f - press1At >= 3 && f % 3 == 0 && f - press1At < 60) {
			pressU();
		}
		if (hub != null && lock1At < 0) {
			lock1At = f;
			server.execute(() -> Forever.extend(server.getOverworld().getEntityById(vindId), 200));
		}
		// You stand and look at the mob in the middle.
		if (centreMob != null && lock1At < 0) {
			Vec3d moving = centreMob.getPos().subtract(new Vec3d(centreMob.prevX, centreMob.prevY, centreMob.prevZ)).multiply(2.5);
			Vec3d to = centreMob.getPos().add(moving).add(0.0, 1.0, 0.0).subtract(client.player.getEyePos());
			client.player.setYaw((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
			client.player.setHeadYaw(client.player.getYaw());
			client.player.setPitch((float) -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z))));
		}
		if (lock1At < 0) {
			if (f > 400) {
				ShootingStar.LOGGER.error("[selftest] forever jitter: no cube was made, giving up");
				stage = Stage.DONE;
			}
			return;
		}
		int since = f - lock1At;
		Entity subject = client.world.getEntityById(friendId);
		if (jitterPhase == 0) {
			subject = client.world.getEntityById(groupIds[0]);
			for (int id : groupIds) {
				if (id != friendId && io.github.kortev.chainsawman.contract.ForeverFlags.CLIENT.contains(id)) {
					subject = client.world.getEntityById(id);
					break;
				}
			}
		}
		if (subject == null) {
			return;
		}
		if (since >= 40 && jitterAt < 0) {
			jitterAt = f;
			Vec3d aimAt = subject.getPos().add(0.0, subject.getHeight() * 0.5, 0.0);
			Vec3d dir = flat(client.player.getPos().subtract(subject.getPos())).normalize();
			Vec3d side = new Vec3d(-dir.z, 0.0, dir.x);
			Vec3d at = subject.getPos().add(dir.multiply(5.2)).add(side.multiply(1.8)).add(0.0, 1.7, 0.0);
			ForeverHubEntity jh = hubFor(subject.getId());
			Capture.Pose pose = frame(client, jh, jh == null ? List.of() : ClientForever.copiesOf(jh), subject, subject.getY(), at, aimAt);
			Capture.camera = t -> pose;
			lens = 60.0F;
			jitterFrames = 0;
			if (capturing()) {
				Capture.start(client, client.runDirectory.toPath().resolve(jitterPhase == 0 ? "capture" : "capture_player"));
			}
		}
		if (jitterAt >= 0 && f - jitterAt >= 32) {
			Capture.stop();
			Capture.camera = null;
			jitterAt = -1;
			if (jitterPhase == 0) {
				jitterPhase = 1;
				server.execute(() -> Forever.extend(server.getOverworld().getEntityById(vindId), 60));
			} else {
				stage = Stage.DONE;
			}
		}
		if (f > 700) {
			stage = Stage.DONE;
		}
	}

	private static ForeverHubEntity hubFor(int target) {
		for (ForeverHubEntity hub : ClientForever.hubs()) {
			if (hub.targetId() == target) {
				return hub;
			}
		}
		return null;
	}

	private static void run(MinecraftClient client, IntegratedServer server, int f) {
		if (client.player == null || client.world == null) {
			return;
		}
		if (f == 1) {
			client.options.hudHidden = false;
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			headYaw = client.player.getYaw();
			headPitch = client.player.getPitch();
			server.execute(() -> spawnGroup(server));
		}
		Entity mid = client.world.getEntityById(vindId);
		ForeverHubEntity hub1 = hubFor(vindId);
		if (hub1 == null && !ClientForever.hubs().isEmpty()) {
			hub1 = ClientForever.hubs().iterator().next();
		}
		// The video begins once the group is really running at you (it takes a moment to get going after it is made).
		if (capturing() && !Capture.active() && captureF0 < 0 && mid != null && (flat(mid.getPos().subtract(groupFrom)).length() > 0.5 || f > 30)) {
			captureF0 = f;
			Capture.start(client, client.runDirectory.toPath().resolve("capture"));
		}
		if (hub1 != null && lock1At < 0) {
			lock1At = f;
			// The flesh coming up and the eyes opening, slowed a little so they can be seen.
			Capture.slow(0.45, 150);
			ShootingStar.LOGGER.info("[selftest] forever: the cube at f={}", f);
		}
		if (hub1 != null && hub1.phase() >= ForeverHubEntity.ENDING && end1At < 0) {
			end1At = f;
			// The bite, the line snapping in and the burst, slowed so they can be seen.
			Capture.slow(0.34, 34);
		}
		// The key, pressed for real when the group is nine blocks off (aim sighted first, pressed again if nothing was in the sights).
		if (press1At < 0 && mid != null && mid.isAlive()) {
			double d = flat(mid.getPos().subtract(client.player.getPos())).length();
			if (d < 9.5 && f > 6 || f > 150) {
				press1At = f;
				pressU();
				server.execute(() -> {
					ServerPlayerEntity you = player(server);
					ShootingStar.LOGGER.info("[selftest] forever: pressed U at f={}; held {} levels {}", press1At, Forever.lockedBy(you).size(), you.experienceLevel);
				});
			}
		}
		if (press1At >= 0 && lock1At < 0 && f - press1At >= 3 && f - press1At < 24 && f % 3 == 0 && mid != null && mid.isAlive()) {
			pressU();
		}
		control(client, server, f);
		cameras(client, f);
		stills(client, f);
		int end = end1At < 0 ? Integer.MAX_VALUE : end1At + 4 + 44;
		if (f % 20 == 0) {
			ShootingStar.LOGGER.info("[selftest] forever f={} mid {} lock {} end {} hits {} you {} hp {}", f, mid == null ? null : mid.getPos(), lock1At, end1At, hitsTotal,
					client.player.getPos(), client.player.getHealth());
		}
		if (f >= end) {
			afterClip(client, server, f);
		} else if (f > 600) {
			ShootingStar.LOGGER.error("[selftest] forever: gave up at f={} (lock {})", f, lock1At);
			Capture.stop();
			stage = Stage.DONE;
		}
	}

	private static int afterAt = -1;
	/** The clip tick the recording began at (the clock of the cameras is counted from there). */
	private static int captureF0 = -1;
	private static Vec3d lastSubject = Vec3d.ZERO;

	/** After the clip: the second player locks you, and your own screen is photographed (what a locked player sees). */
	private static void afterClip(MinecraftClient client, IntegratedServer server, int f) {
		if (afterAt < 0) {
			afterAt = f;
			Capture.stop();
			Capture.camera = null;
			lens = 0.0F;
			headYaw = Float.NaN;
			server.execute(() -> lockYou(server));
		}
		int k = f - afterAt;
		client.options.setPerspective(k < 40 ? Perspective.FIRST_PERSON : Perspective.THIRD_PERSON_BACK);
		client.options.forwardKey.setPressed(false);
		client.options.sprintKey.setPressed(false);
		if (k == 22 || k == 30 || k == 44) {
			still(client, k == 44 ? "locked_player_third" : "locked_player_" + k);
		}
		if (k == 54) {
			still(client, "locked_player_third_late");
		}
		if (k > 76) {
			stage = Stage.DONE;
		}
	}

	private static void pressU() {
		KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(ChainsawManClient.USE_CONTRACT.getBoundKeyTranslationKey()));
	}

	private static Vec3d flat(Vec3d v) {
		return new Vec3d(v.x, 0.0, v.z);
	}

	private static double groundBelow(MinecraftClient client, Entity e) {
		BlockPos p = e.getBlockPos();
		for (int i = 0; i < 20; i++) {
			BlockPos q = p.down(i);
			if (!client.world.getBlockState(q).getCollisionShape(client.world, q).isEmpty()) {
				return q.getY() + 1.0;
			}
		}
		return e.getY();
	}

	/**
	 * What you do, every client tick: look at what you are after (eased, as a person's head turns); while a cube is held, run at the
	 * nearest thing in it (a held one or a copy of one) that you have not hit twice, and cut it when you are close, the call the attack key
	 * makes; otherwise watch the group coming.
	 */
	private static void control(MinecraftClient client, IntegratedServer server, int f) {
		Entity goal = null;
		boolean held = false;
		double best = Double.MAX_VALUE;
		for (ForeverHubEntity hub : ClientForever.hubs()) {
			if (hub.phase() != ForeverHubEntity.HELD) {
				continue;
			}
			held = true;
			List<Entity> options = new ArrayList<>(ClientForever.copiesOf(hub));
			Entity target = client.world.getEntityById(hub.targetId());
			if (target != null && target != client.player) {
				options.add(target);
			}
			for (Entity e : options) {
				if (HITS.getOrDefault(e.getId(), 0) >= 2) {
					continue;
				}
				double d = flat(e.getPos().subtract(client.player.getPos())).lengthSquared();
				if (d < best) {
					best = d;
					goal = e;
				}
			}
		}
		Entity watch = client.world.getEntityById(vindId);
		Entity aim = goal != null ? goal : watch;
		if (Float.isNaN(headYaw)) {
			headYaw = client.player.getYaw();
			headPitch = client.player.getPitch();
		}
		if (aim != null) {
			Vec3d moving = aim.getPos().subtract(new Vec3d(aim.prevX, aim.prevY, aim.prevZ)).multiply(2.5);
			Vec3d to = aim.getPos().add(moving).add(0.0, aim.getHeight() * 0.5, 0.0).subtract(client.player.getEyePos());
			float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z)));
			// Waiting to press the key you are looking right at what you will hold (so the server's view of you has it in its sights).
			boolean sighting = press1At < 0 && aim == watch && f >= 6;
			float ease = sighting ? 1.0F : 0.45F;
			headYaw += MathHelper.clamp(MathHelper.wrapDegrees(yaw - headYaw) * ease, -60.0F, 60.0F);
			headPitch += MathHelper.clamp((pitch - headPitch) * ease, -45.0F, 45.0F);
		}
		client.player.setYaw(headYaw);
		client.player.setHeadYaw(headYaw);
		client.player.setPitch(MathHelper.clamp(headPitch, -80.0F, 80.0F));
		boolean run = false;
		if (goal != null && held) {
			double d = flat(goal.getPos().subtract(client.player.getPos())).length() - goal.getWidth() / 2.0;
			run = d > 2.2;
			if (d <= 2.9 && f - lastAttack >= 6) {
				float off = Math.abs(MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(-(goal.getX() - client.player.getX()), goal.getZ() - client.player.getZ())) - client.player.getYaw()));
				if (off < 25.0F && client.interactionManager != null) {
					lastAttack = f;
					client.interactionManager.attackEntity(client.player, goal);
					client.player.swingHand(Hand.MAIN_HAND);
					hitsTotal++;
					if (HITS.merge(goal.getId(), 1, Integer::sum) > 3) {
						HITS.put(goal.getId(), 3);
					}
				}
			}
		}
		client.options.forwardKey.setPressed(run);
		client.options.sprintKey.setPressed(run);
	}

	private static void still(MinecraftClient client, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot forever_{}", name);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, "forever_" + name + ".png", client.getFramebuffer(), message -> {
		});
	}

	private static void once(MinecraftClient client, String name) {
		if (!STILLS_DONE.contains(name)) {
			STILLS_DONE.add(name);
			still(client, name);
		}
	}

	/** Stills at the beats the review wants. */
	private static void stills(MinecraftClient client, int f) {
		if (lock1At >= 0) {
			int r = f - lock1At;
			int[] at = {3, 10, 20, 30, 38, 46, 56};
			String[] names = {"1_lock_rising", "2_eyes_open", "3_hero_down_the_line", "3b_line_and_far_copies", "3c_the_cube_from_outside", "3d_line_slashing", "4_marks_piled"};
			for (int i = 0; i < at.length; i++) {
				if (r == at[i]) {
					once(client, names[i]);
				}
			}
			if (end1At >= 0 && f == end1At + 2) {
				once(client, "5_bite_snap");
			}
			if (end1At >= 0 && f == end1At + 5) {
				once(client, "6_release_burst");
			}
			if (end1At >= 0 && f == end1At + 16) {
				once(client, "7_flung");
			}
		}
	}

	// ------------------------------------------------------------------ the cameras

	private static boolean installs(String key, double t) {
		if (key.equals(shotKey)) {
			return false;
		}
		shotKey = key;
		shotT0 = t;
		return true;
	}

	/** The camera for the capture time {@code t}: one shot after another, about a second and a half each. */
	private static void cameras(MinecraftClient client, int f) {
		if (!capturing() && Capture.camera == null) {
			// Not recording: the shots are still set, for the stills.
		}
		Capture.camera = t -> {
			MinecraftClient m = MinecraftClient.getInstance();
			if (m.world == null || m.player == null) {
				return new Capture.Pose(0, 0, 0, 0, 0);
			}
			return shot(m, t);
		};
	}

	private static Capture.Pose shot(MinecraftClient m, double captureT) {
		float part = (float) (captureT - Math.floor(captureT));
		double t = captureT + Math.max(0, captureF0);
		Entity mid = m.world.getEntityById(vindId);
		Vec3d you = m.player.getLerpedPos(part);
		ForeverHubEntity hub = hubFor(vindId);
		if (hub == null && !ClientForever.hubs().isEmpty()) {
			hub = ClientForever.hubs().iterator().next();
		}
		double rel = lock1At < 0 ? -1000 : t - lock1At;
		double endRel = end1At < 0 ? -1000 : t - end1At;
		// The group (the middle of everything held or charging) and the cube.
		Vec3d groupAt = Vec3d.ZERO;
		int n = 0;
		for (int id : groupIds) {
			Entity e = m.world.getEntityById(id);
			if (e != null && e.isAlive()) {
				groupAt = groupAt.add(e.getLerpedPos(part));
				n++;
			}
		}
		if (n == 0) {
			groupAt = lastSubject;
		} else {
			groupAt = groupAt.multiply(1.0 / n);
			lastSubject = groupAt;
		}
		Vec3d cubeAt = ClientForever.cubes().isEmpty() ? groupAt : ClientForever.cubes().get(0).getPos();
		Entity subject = mid != null ? mid : m.player;
		Vec3d tp = hub != null ? hub.getPos() : mid != null ? mid.getLerpedPos(part) : groupAt;
		double ground = tp.y;
		List<ForeverCopyEntity> copies = hub != null ? ClientForever.copiesOf(hub) : List.of();
		Vec3d axis = flat(tp.subtract(you));
		if (copies.size() >= 2) {
			Vec3d lo = null;
			Vec3d hi = null;
			int loS = 9;
			int hiS = -9;
			for (ForeverCopyEntity c : copies) {
				if (c.slot() < loS) {
					loS = c.slot();
					lo = c.getPos();
				}
				if (c.slot() > hiS) {
					hiS = c.slot();
					hi = c.getPos();
				}
			}
			Vec3d a2 = flat(hi.subtract(lo));
			if (a2.lengthSquared() > 0.04) {
				axis = a2;
			}
		}
		axis = axis.lengthSquared() < 1.0E-4 ? new Vec3d(1.0, 0.0, 0.0) : axis.normalize();
		Vec3d across = new Vec3d(-axis.z, 0.0, axis.x);
		Vec3d youSide = across.dotProduct(you.subtract(tp)) >= 0.0 ? across : across.negate();
		Vec3d nearEnd = axis.dotProduct(you.subtract(tp)) >= 0.0 ? axis : axis.negate();
		Vec3d at;
		Vec3d aim;
		if (rel < -3.0 && endRel < 0.0) {
			// The charge: beside you and a little ahead, low, the group running at the lens.
			if (installs("0", t)) {
				lens = 62.0F;
			}
			Vec3d toMob = flat(groupAt.subtract(you));
			Vec3d dirMob = toMob.lengthSquared() < 1.0E-4 ? new Vec3d(1.0, 0.0, 0.0) : toMob.normalize();
			Vec3d side = new Vec3d(-dirMob.z, 0.0, dirMob.x);
			at = you.lerp(groupAt, 0.55).add(side.multiply(4.4)).add(0.0, 1.5, 0.0);
			aim = groupAt.add(0.0, 1.0, 0.0);
		} else if (endRel < 0.0 && rel < 9.0) {
			// The lock: three quarters on from a little way off, level with the eyes, the low flesh coming up at the feet of each moment.
			if (installs("1", t)) {
				lens = 70.0F;
			}
			double k = MathHelper.clamp((t - shotT0) / 14.0, 0.0, 1.0);
			Vec3d way = youSide.multiply(0.75).add(nearEnd.multiply(0.65)).normalize();
			at = new Vec3d(tp.x, ground + 1.6, tp.z).add(way.multiply(7.4 + 0.6 * k));
			aim = tp.add(0.0, 0.7, 0.0);
		} else if (endRel < 0.0 && rel < 30.0) {
			// The hero shot: three quarters down the line from just beyond its far end (away from you), a couple of blocks to one side, at
			// eye height; the near copies are big and the line runs away into the distance, on past the near copies as pale render-only ones.
			if (installs("2", t)) {
				lens = 68.0F;
			}
			double k = MathHelper.clamp((t - shotT0) / 24.0, 0.0, 1.0);
			Vec3d farEnd = nearEnd.negate();
			double reach = 4.6 + 2.3 - 0.3 * k;
			Vec3d baseAt = new Vec3d(tp.x, ground + 1.75, tp.z).add(farEnd.multiply(reach));
			Vec3d one = baseAt.add(across.multiply(3.0 + 0.4 * k));
			Vec3d two = baseAt.add(across.negate().multiply(3.0 + 0.4 * k));
			aim = tp.add(nearEnd.multiply(0.8)).add(0.0, 0.8, 0.0);
			at = clearTo(m, aim, one).distanceTo(aim) >= clearTo(m, aim, two).distanceTo(aim) ? one : two;
		} else if (endRel < 0.0 && rel < 44.0) {
			// From outside the cube: the faint shimmering edge where colour meets grey, and the frozen group inside.
			if (installs("3", t)) {
				lens = 60.0F;
			}
			double k = MathHelper.clamp((t - shotT0) / 14.0, 0.0, 1.0);
			Vec3d away = flat(cubeAt.subtract(you));
			Vec3d out = away.lengthSquared() < 1.0E-4 ? new Vec3d(1.0, 0.0, 0.0) : away.normalize();
			Vec3d sideDir = new Vec3d(-out.z, 0.0, out.x);
			Vec3d dir1 = out.multiply(0.55).add(sideDir.multiply(0.83)).normalize();
			Vec3d dir2 = out.multiply(0.55).add(sideDir.multiply(-0.83)).normalize();
			double d = 4.5 + 8.0 - 1.0 * k;
			Vec3d one = new Vec3d(cubeAt.x, ground + 2.2, cubeAt.z).add(dir1.multiply(d));
			Vec3d two = new Vec3d(cubeAt.x, ground + 2.2, cubeAt.z).add(dir2.multiply(d));
			aim = new Vec3d(cubeAt.x, ground + 0.9, cubeAt.z);
			at = clearTo(m, aim, one).distanceTo(aim) >= clearTo(m, aim, two).distanceTo(aim) ? one : two;
		} else if (endRel < 0.0 && rel < 52.0) {
			// You, running through the line and cutting: from behind and beside, three or four blocks off, the swing and the marks in view.
			if (installs("S", t)) {
				lens = 68.0F;
			}
			float yaw = m.player.getYaw(part);
			Vec3d fwd = new Vec3d(-Math.sin(Math.toRadians(yaw)), 0.0, Math.cos(Math.toRadians(yaw)));
			Vec3d sideways = new Vec3d(-fwd.z, 0.0, fwd.x);
			at = you.add(fwd.multiply(-3.3)).add(sideways.multiply(1.7)).add(0.0, 1.9, 0.0);
			aim = you.add(fwd.multiply(2.6)).add(0.0, 1.1, 0.0);
		} else if (endRel < 14.0) {
			// The release: about five blocks off, three quarters on to the line, so the copies can be seen streaking in, and the burst.
			if (installs("4", t)) {
				lens = 74.0F;
			}
			Vec3d way = across.multiply(0.8).add(nearEnd.multiply(0.6)).normalize();
			Vec3d one = new Vec3d(tp.x, ground + 1.7, tp.z).add(way.multiply(5.6));
			Vec3d two = new Vec3d(tp.x, ground + 1.7, tp.z).add(new Vec3d(-across.x * 0.8 + nearEnd.x * 0.6, 0.0, -across.z * 0.8 + nearEnd.z * 0.6).normalize().multiply(5.6));
			aim = tp.add(0.0, 0.8, 0.0);
			at = one.squaredDistanceTo(you) >= two.squaredDistanceTo(you) ? one : two;
		} else {
			// After: pulled back and a little up, the flung group, the flesh sinking, the colour coming back.
			if (installs("5", t)) {
				lens = 70.0F;
			}
			double k = MathHelper.clamp((t - shotT0) / 40.0, 0.0, 1.0);
			at = new Vec3d(tp.x, ground + 2.2, tp.z).add(youSide.multiply(8.0 + 1.5 * k)).add(nearEnd.multiply(2.0));
			aim = groupAt.add(0.0, 1.1, 0.0);
		}
		return frame(m, hub, copies, subject, ground, at, aim);
	}

	/**
	 * The rules for every shot: no closer than a block and a half to a lump, a copy, any body or you; between 1.2 and 2.5 blocks over the
	 * ground; nothing solid in the way; and never looking more than about 25 degrees up or down.
	 */
	private static Capture.Pose frame(MinecraftClient m, ForeverHubEntity hub, List<ForeverCopyEntity> copies, Entity subject, double ground, Vec3d at, Vec3d aim) {
		List<double[]> keepOut = new ArrayList<>();
		for (ForeverHubEntity h : ClientForever.hubs()) {
			keepOut.addAll(ClientForever.lumps(h));
			for (ForeverCopyEntity c : ClientForever.copiesOf(h)) {
				keepOut.add(new double[] {c.getX(), c.getY() + c.getHeight() * 0.5, c.getZ(), 0.5 + c.getWidth()});
			}
		}
		keepOut.add(new double[] {m.player.getX(), m.player.getY() + 0.9, m.player.getZ(), 1.0});
		double y = MathHelper.clamp(at.y, ground + 1.2, ground + 2.5);
		Vec3d c = new Vec3d(at.x, y, at.z);
		for (int pass = 0; pass < 3; pass++) {
			for (double[] k : keepOut) {
				Vec3d centre = new Vec3d(k[0], k[1], k[2]);
				double need = k[3] + 1.5;
				Vec3d off = c.subtract(centre);
				if (off.length() < need) {
					Vec3d out = off.lengthSquared() < 1.0E-6 ? new Vec3d(1.0, 0.0, 0.0) : off.normalize();
					c = centre.add(out.multiply(need));
					c = new Vec3d(c.x, MathHelper.clamp(c.y, ground + 1.2, ground + 2.5), c.z);
				}
			}
		}
		// And out of anything standing about (a mob, a player, a copy): a block and a half from every body.
		for (int pass = 0; pass < 3; pass++) {
			for (Entity e : m.world.getEntities()) {
				if (!(e instanceof LivingEntity) && !(e instanceof ForeverCopyEntity) || e.squaredDistanceTo(c) > 100.0) {
					continue;
				}
				Vec3d centre = e.getPos().add(0.0, e.getHeight() * 0.5, 0.0);
				double need = Math.max(e.getWidth(), e.getHeight()) * 0.5 + 1.7;
				Vec3d off = c.subtract(centre);
				if (off.length() < need) {
					Vec3d out = off.lengthSquared() < 1.0E-6 ? new Vec3d(1.0, 0.0, 0.0) : off.normalize();
					c = centre.add(out.multiply(need));
				}
			}
			c = new Vec3d(c.x, MathHelper.clamp(c.y, ground + 1.2, ground + 2.5), c.z);
		}
		c = clearTo(m, aim, c);
		Vec3d to = aim.subtract(c);
		double flat = Math.hypot(to.x, to.z);
		double limit = Math.tan(Math.toRadians(25.0)) * Math.max(flat, 0.5);
		Vec3d aimed = new Vec3d(aim.x, c.y + MathHelper.clamp(to.y, -limit, limit), aim.z);
		lastCamera = c;
		return look(c, aimed);
	}

	private static Vec3d[] eyePick;

	/** The lump with an eye (and a mouth) nearest {@code from}, as {@code [mouth, which way it faces, eye]} in the world. */
	private static Vec3d[] pickEye(ForeverHubEntity hub, Vec3d from) {
		if (eyePick == null) {
			double best = Double.MAX_VALUE;
			for (Vec3d[] mo : ClientForever.mouthsWithEyes(hub)) {
				double d = mo[0].add(mo[1].multiply(2.0)).squaredDistanceTo(from);
				if (d < best) {
					best = d;
					eyePick = mo;
				}
			}
		}
		return eyePick;
	}

	/** {@code at} pulled toward {@code aim} until nothing solid is between. */
	private static Vec3d clearTo(MinecraftClient m, Vec3d aim, Vec3d at) {
		HitResult hit = m.world.raycast(new RaycastContext(aim, at, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, m.player));
		if (hit.getType() == HitResult.Type.MISS) {
			return at;
		}
		Vec3d to = at.subtract(aim);
		double len = hit.getPos().distanceTo(aim);
		return aim.add(to.normalize().multiply(Math.max(0.6, len - 0.35)));
	}

	private static Capture.Pose look(Vec3d at, Vec3d look) {
		Vec3d to = look.subtract(at);
		float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
		return new Capture.Pose(at.x, at.y, at.z, yaw, pitch);
	}

	// ------------------------------------------------------------------ the server's side

	private static ServerPlayerEntity player(IntegratedServer server) {
		return server.getPlayerManager().getPlayerList().get(0);
	}

	private static double groundAt(ServerWorld world, Vec3d at) {
		return surface(world, MathHelper.floor(at.x), MathHelper.floor(at.z)) + 1.0;
	}

	/** The group, each with its own AI after you: four vindicators and the second player among them, ten blocks off and charging. */
	private static void spawnGroup(IntegratedServer server) {
		ServerWorld world = server.getOverworld();
		ServerPlayerEntity you = player(server);
		Vec3d base = middle.add(11.0, 0.0, 0.0);
		double[][] offsets = {{0.0, 0.0}, {1.3, -1.9}, {-0.5, 2.0}, {2.0, 1.5}};
		List<Integer> ids = new ArrayList<>();
		for (int i = 0; i < offsets.length; i++) {
			MobEntity v = EntityType.VINDICATOR.create(world);
			Vec3d at = base.add(offsets[i][0], 0.0, offsets[i][1]);
			v.refreshPositionAndAngles(at.x, groundAt(world, at), at.z, 90.0F, 0.0F);
			v.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
			v.setPersistent();
			world.spawnEntity(v);
			v.setTarget(you);
			ids.add(v.getId());
		}
		Vec3d fat = base.add(2.4, 0.0, -0.7);
		if (friend == null) {
			friend = makeFriend(server, world, fat);
			friendId = friend.getId();
		}
		friend.refreshPositionAndAngles(fat.x, groundAt(world, fat), fat.z, 90.0F, 0.0F);
		ids.add(friend.getId());
		groupIds = ids.stream().mapToInt(Integer::intValue).toArray();
		vindId = ids.get(0);
		groupFrom = base;
	}

	/** The second player, a real player on the server, as in the revive reel's friend. */
	private static ServerPlayerEntity makeFriend(MinecraftServer server, ServerWorld world, Vec3d at) {
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), "Yakuza1"), false);
		ServerPlayerEntity p = new ServerPlayerEntity(server, world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerManager().onPlayerConnect(connection, p, data);
		p.changeGameMode(GameMode.SURVIVAL);
		p.teleport(world, at.x, at.y, at.z, 0.0F, 0.0F);
		p.onTeleportationDone();
		p.setNoGravity(true);
		p.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(80.0);
		p.setHealth(80.0F);
		p.getInventory().setStack(0, new ItemStack(Items.IRON_SWORD));
		p.getInventory().selectedSlot = 0;
		return p;
	}

	/** The second player's script, every server tick: he runs at you with the group until the cube holds him; your health is kept up. */
	private static void friendTick(MinecraftServer server) {
		if (stage != Stage.RUN || server.getPlayerManager().getPlayerList().isEmpty() || lookOnly()) {
			return;
		}
		ServerWorld world = server.getOverworld();
		ServerPlayerEntity you = server.getPlayerManager().getPlayerList().get(0);
		if (you.isAlive() && you.getHealth() < 12.0F) {
			// (You are not left to die before the end of the take: the mobs are after you for real, only the last blow is spared.)
			you.setHealth(you.getMaxHealth());
		}
		if (friend == null || afterAt >= 0 || Forever.locked(friend) || !friend.isAlive()) {
			return;
		}
		// Running at you with the others (moved along, sprinting: he is a real player, only his feet are the test's).
		Vec3d to = you.getPos().subtract(friend.getPos()).multiply(1.0, 0.0, 1.0);
		if (to.length() > 3.0) {
			Vec3d step = friend.getPos().add(to.normalize().multiply(0.2));
			float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
			friend.refreshPositionAndAngles(step.x, groundAt(world, step), step.z, yaw, 0.0F);
			friend.setHeadYaw(yaw);
			friend.setBodyYaw(yaw);
			friend.setSprinting(true);
			friend.limbAnimator.updateLimbs(0.9F, 0.6F);
		}
	}

	/** After the clip: the friend holds the contract and locks you where you stand. */
	private static void lockYou(IntegratedServer server) {
		ServerWorld world = server.getOverworld();
		ServerPlayerEntity you = player(server);
		for (Entity e : world.getOtherEntities(you, new Box(you.getBlockPos()).expand(60.0, 30.0, 60.0))) {
			if (e instanceof MobEntity) {
				e.discard();
			}
		}
		you.setHealth(you.getMaxHealth());
		Vec3d at = you.getPos().add(-5.5, 0.0, 0.0);
		if (friend == null) {
			friend = makeFriend(server, world, at);
			friendId = friend.getId();
		}
		friend.refreshPositionAndAngles(at.x, groundAt(world, at), at.z, 90.0F, 0.0F);
		Contracts.grant(friend, Contract.ETERNITY);
		friend.setExperienceLevel(10);
		Forever.ready(friend);
		Vec3d to = you.getPos().add(0.0, 1.2, 0.0).subtract(friend.getEyePos());
		friend.setYaw((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
		friend.setHeadYaw(friend.getYaw());
		friend.setPitch((float) -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z))));
		you.setYaw(friend.getYaw() + 180.0F);
		Forever.use(friend);
		Forever.extend(you, 30);
		ShootingStar.LOGGER.info("[selftest] forever: the friend locks you: {} (levels {}, contract {})", Forever.locked(you), friend.experienceLevel, Contracts.get(friend));
	}

	// ------------------------------------------------------------------ the set

	private static int surface(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
		while (y > world.getBottomY()) {
			BlockState s = world.getBlockState(new BlockPos(x, y, z));
			if (s.isAir() || s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.LOGS) || s.isReplaceable() && s.getFluidState().isEmpty() || s.isOf(Blocks.SNOW)
					|| s.isOf(Blocks.BROWN_MUSHROOM_BLOCK) || s.isOf(Blocks.RED_MUSHROOM_BLOCK) || s.isOf(Blocks.MUSHROOM_STEM)) {
				y--;
			} else {
				break;
			}
		}
		return y;
	}

	private static boolean growth(BlockState s) {
		return s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.LOGS) || s.isOf(Blocks.VINE) || s.isOf(Blocks.BROWN_MUSHROOM_BLOCK) || s.isOf(Blocks.RED_MUSHROOM_BLOCK)
				|| s.isOf(Blocks.MUSHROOM_STEM) || s.isOf(Blocks.COCOA) || s.isOf(Blocks.BEE_NEST);
	}

	/**
	 * The set: a dark forest (or any forest the world has), at dusk; a clearing of {@link #RADIUS} taken out of the trees (the ground
	 * left as it grew: uneven), the trees standing round it; a dirt-and-grass ledge to the east; you with the contract, an iron sword and
	 * levels, facing east.
	 */
	private static void setup(IntegratedServer server) {
		ServerPlayerEntity you = player(server);
		ServerWorld world = server.getOverworld();
		world.setTimeOfDay(12150);
		world.setWeather(12000, 0, false, false);
		server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_LOOT).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_GRIEFING).set(false, server);
		server.getGameRules().get(GameRules.DO_TILE_DROPS).set(false, server);
		server.setDifficulty(Difficulty.NORMAL, true);
		// A dark forest edge in the test world (found once with locateBiome from the spawn and kept: the same ground every take).
		BlockPos near = new BlockPos(-453, 70, 2601);
		ShootingStar.LOGGER.info("[selftest] forever: forest at {}", near);
		for (int dx = -RADIUS - 16; dx <= RADIUS + 16; dx += 16) {
			for (int dz = -RADIUS - 16; dz <= RADIUS + 16; dz += 16) {
				world.getChunk((near.getX() + dx) >> 4, (near.getZ() + dz) >> 4);
			}
		}
		int base = surface(world, near.getX(), near.getZ()) + 1;
		centre = new BlockPos(near.getX(), base, near.getZ());
		middle = Vec3d.ofBottomCenter(centre);
		for (int cx = (centre.getX() - 40) >> 4; cx <= (centre.getX() + 40) >> 4; cx++) {
			for (int cz = (centre.getZ() - 40) >> 4; cz <= (centre.getZ() + 40) >> 4; cz++) {
				world.setChunkForced(cx, cz, true);
			}
		}
		for (Entity e : world.getOtherEntities(you, new Box(centre).expand(80.0, 50.0, 80.0))) {
			if (!(e instanceof ServerPlayerEntity)) {
				e.discard();
			}
		}
		// The clearing: the trees (and what hangs off them) taken out, the ground left alone.
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (Math.hypot(dx, dz) > RADIUS) {
					continue;
				}
				int x = centre.getX() + dx;
				int z = centre.getZ() + dz;
				world.getChunk(x >> 4, z >> 4);
				int top = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
				for (int y = top; y >= centre.getY() - 8; y--) {
					BlockPos p = new BlockPos(x, y, z);
					if (growth(world.getBlockState(p))) {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
		// The ground: levelled round the middle with a few gentle swells (still uneven, and natural at the rim), blending into the hillside.
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				double d = Math.hypot(dx, dz);
				if (d > RADIUS) {
					continue;
				}
				int x = centre.getX() + dx;
				int z = centre.getZ() + dz;
				int natural = surface(world, x, z);
				double swell = 0.55 * Math.sin(dx * 0.37 + 1.3) * Math.cos(dz * 0.31 - 0.4) + 0.25 * Math.sin(dx * 0.9 + dz * 0.7);
				double target = centre.getY() - 1 + swell * MathHelper.clamp((d - 3.0) / 3.0, 0.0, 1.0) - 0.0;
				double w = MathHelper.clamp((d - 11.0) / 13.0, 0.0, 1.0);
				int want = (int) Math.round(MathHelper.lerp(w * w, target, natural));
				if (want == natural) {
					continue;
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
				for (int y = Math.max(want, natural) + 1; y <= Math.max(want, natural) + 3; y++) {
					BlockPos q = new BlockPos(x, y, z);
					if (!world.getBlockState(q).isAir() && world.getBlockState(q).getCollisionShape(world, q).isEmpty()) {
						world.setBlockState(q, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
		if (Form.on(you)) {
			Form.end(you);
		}
		Downed.up(you);
		Heart.take(you);
		you.changeGameMode(GameMode.SURVIVAL);
		you.getInventory().clear();
		you.getInventory().setStack(0, new ItemStack(Items.IRON_SWORD));
		you.getInventory().selectedSlot = 0;
		Contracts.grant(you, Contract.ETERNITY);
		you.setExperienceLevel(10);
		Forever.ready(you);
		you.setHealth(you.getMaxHealth());
		you.getHungerManager().setFoodLevel(20);
		you.teleport(world, middle.x, middle.y, middle.z, -90.0F, 4.0F);
		ShootingStar.LOGGER.info("[selftest] forever: clearing at {}", centre);
	}
}
