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
	private static int vindHits;
	private static int ravHits;

	public static boolean active() {
		return "forever".equals(System.getProperty("shootingstar.selftest"));
	}

	public static float lens() {
		return active() && stage == Stage.RUN ? lens : 0.0F;
	}

	private static boolean capturing() {
		return "true".equals(System.getProperty("shootingstar.selftest.capture"));
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
			server.execute(() -> spawnVindicator(server));
		}
		Entity vind = client.world.getEntityById(vindId);
		// The video begins once the vindicator is really running at you (it takes a moment to get going after it is made).
		if (capturing() && !Capture.active() && captureF0 < 0 && vind != null && (flat(vind.getPos().subtract(new Vec3d(middle.x + 10.6, vind.getY(), middle.z))).length() > 1.0 || f > 80)) {
			captureF0 = f;
			Capture.start(client, client.runDirectory.toPath().resolve("capture"));
		}
		Entity rav = client.world.getEntityById(ravId);
		ForeverHubEntity hub1 = hubFor(vindId);
		ForeverHubEntity hub2 = hubFor(ravId);
		if (hub1 != null && lock1At < 0) {
			lock1At = f;
			// The flesh coming up and the eyes opening, slowed a little so they can be seen.
			Capture.slow(0.5, 36);
			ShootingStar.LOGGER.info("[selftest] forever: first lock at f={}", f);
		}
		if (hub1 != null && hub1.phase() >= ForeverHubEntity.ENDING && end1At < 0) {
			end1At = f;
			// The bite, the line snapping in and the burst, slowed so they can be seen.
			Capture.slow(0.34, 34);
		}
		if (hub2 != null && lock2At < 0) {
			lock2At = f;
			Capture.slow(0.5, 24);
			ShootingStar.LOGGER.info("[selftest] forever: second lock at f={}", f);
		}
		if (hub2 != null && hub2.phase() >= ForeverHubEntity.ENDING && end2At < 0) {
			end2At = f;
			Capture.slow(0.34, 30);
		}
		// The ravager comes once the first moment has ended.
		if (end1At >= 0 && ravSpawned < 0 && f >= end1At + 2) {
			ravSpawned = f;
			server.execute(() -> {
				Forever.ready(server.getPlayerManager().getPlayerList().get(0));
				spawnRavager(server);
			});
		}
		// The key, pressed for real while the vindicator is in the air, and again when the ravager is charging at you.
		if (press1At < 0 && vind != null && vind.isAlive()) {
			double d = flat(vind.getPos().subtract(client.player.getPos())).length();
			double air = 0.0;
			if (f >= 36 && f <= 90) {
				ShootingStar.LOGGER.info("[selftest] forever f={} vind d {}", f, d);
			}
			if (d < 9.0 && f > 6 || f > 150) {
				press1At = f;
				pressU();
				server.execute(() -> {
					ServerPlayerEntity you = player(server);
					Entity v = server.getOverworld().getEntityById(vindId);
					ShootingStar.LOGGER.info("[selftest] forever: pressed U at f={}; contract {} levels {} active {} cooldown {} you yaw {} pitch {} eye {} vind {}", press1At, Contracts.get(you),
							you.experienceLevel, Forever.active(you), Forever.cooldownLeft(you), you.getYaw(), you.getPitch(), you.getEyePos(), v == null ? null : v.getBoundingBox());
				});
			}
		}
		if (press2At < 0 && ravSpawned >= 0 && rav != null && rav.isAlive() && f > ravSpawned + 6) {
			double d = flat(rav.getPos().subtract(client.player.getPos())).length();
			if (d < 13.0 || f > ravSpawned + 200) {
				press2At = f;
				server.execute(() -> Forever.ready(player(server)));
				pressU();
				server.execute(() -> {
					ServerPlayerEntity you = player(server);
					ShootingStar.LOGGER.info("[selftest] forever: pressed U (ravager) at f={}; levels {} active {} cooldown {} health {}", press2At, you.experienceLevel,
							Forever.active(you), Forever.cooldownLeft(you), you.getHealth());
				});
			}
		}
		// The key again if the first press found nothing in its sights (it is a person pressing it: they would).
		if (press1At >= 0 && lock1At < 0 && f - press1At >= 3 && f - press1At < 24 && f % 3 == 0 && vind != null && vind.isAlive()) {
			pressU();
		}
		if (press2At >= 0 && lock2At < 0 && f - press2At >= 3 && f - press2At < 40 && f % 3 == 0 && rav != null && rav.isAlive()) {
			server.execute(() -> Forever.ready(player(server)));
			pressU();
		}
		// The ravager is brought in off the hillside if it has got stuck out of reach (the terrain is natural; the charge is its own).
		if (ravSpawned >= 0 && press2At < 0 && rav != null && f == ravSpawned + 80 && flat(rav.getPos().subtract(client.player.getPos())).length() > 15.0) {
			server.execute(() -> relocateRavager(server));
		}
		control(client, server, f, vind, rav, hub1, hub2);
		cameras(client, f);
		stills(client, f);
		int end = end2At < 0 ? Integer.MAX_VALUE : end2At + 4 + 96;
		if (f % 20 == 0) {
			ShootingStar.LOGGER.info("[selftest] forever f={} vind {} rav {} lock1 {} end1 {} lock2 {} end2 {} hits {} / {} you {} hp {}", f, vind == null ? null : vind.getPos(),
					rav == null ? null : rav.getPos(), lock1At, end1At, lock2At, end2At, vindHits, ravHits, client.player.getPos(), client.player.getHealth());
		}
		if (f >= end) {
			afterClip(client, server, f);
		} else if (f > 900) {
			ShootingStar.LOGGER.error("[selftest] forever: gave up at f={} (lock1 {}, lock2 {})", f, lock1At, lock2At);
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
	 * What you do, every client tick: look at what you are after (eased, as a person's head turns); while a moment is held, run at the
	 * nearest thing of it (a copy or the held one) that you have not hit twice, and cut it when you are close, the call the attack key
	 * makes; otherwise watch the mob that is coming.
	 */
	private static void control(MinecraftClient client, IntegratedServer server, int f, Entity vind, Entity rav, ForeverHubEntity hub1, ForeverHubEntity hub2) {
		ForeverHubEntity hub = hub2 != null && hub2.phase() == ForeverHubEntity.HELD ? hub2 : hub1 != null && hub1.phase() == ForeverHubEntity.HELD ? hub1 : null;
		Entity watch = rav != null && rav.isAlive() && ravSpawned >= 0 ? rav : vind;
		Entity goal = null;
		if (hub != null) {
			Entity held = client.world.getEntityById(hub.targetId());
			double best = Double.MAX_VALUE;
			List<Entity> options = new ArrayList<>(ClientForever.copiesOf(hub));
			if (held != null) {
				options.add(held);
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
		Entity aim = goal != null ? goal : watch;
		if (Float.isNaN(headYaw)) {
			headYaw = client.player.getYaw();
			headPitch = client.player.getPitch();
		}
		if (aim != null) {
			// (A little ahead of where it is: the server sees you a tick late.)
			Vec3d moving = aim.getPos().subtract(new Vec3d(aim.prevX, aim.prevY, aim.prevZ)).multiply(2.5);
			Vec3d to = aim.getPos().add(moving).add(0.0, aim.getHeight() * 0.5, 0.0).subtract(client.player.getEyePos());
			float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z)));
			// Waiting to press the key you are looking right at what you will hold (so the server's view of you has it in its sights).
			boolean sighting = press1At < 0 && aim == vind && f >= 6 || press2At < 0 && aim == rav && ravSpawned >= 0;
			float ease = sighting ? 1.0F : 0.45F;
			headYaw += MathHelper.clamp(MathHelper.wrapDegrees(yaw - headYaw) * ease, -60.0F, 60.0F);
			headPitch += MathHelper.clamp((pitch - headPitch) * ease, -45.0F, 45.0F);
		}
		client.player.setYaw(headYaw);
		client.player.setHeadYaw(headYaw);
		client.player.setPitch(MathHelper.clamp(headPitch, -80.0F, 80.0F));
		boolean run = false;
		if (goal != null) {
			double d = flat(goal.getPos().subtract(client.player.getPos())).length() - goal.getWidth() / 2.0;
			run = d > 2.2;
			if (d <= 2.9 && f - lastAttack >= 6) {
				// Close and looking at it: the cut.
				float off = Math.abs(MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(-(goal.getX() - client.player.getX()), goal.getZ() - client.player.getZ())) - client.player.getYaw()));
				if (off < 25.0F && client.interactionManager != null) {
					lastAttack = f;
					client.interactionManager.attackEntity(client.player, goal);
					client.player.swingHand(Hand.MAIN_HAND);
					int n = HITS.merge(goal.getId(), 1, Integer::sum);
					if (goal == vind || goal instanceof ForeverCopyEntity c && c.targetId() == vindId) {
						vindHits++;
					} else {
						ravHits++;
					}
					if (n > 3) {
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
			if (r == 3) {
				once(client, "1_lock_rising");
			}
			if (r == 12) {
				once(client, "2_eyes_open");
			}
			if (r == 26) {
				once(client, "3_line_from_the_side");
			}
			if (r == 36) {
				once(client, "3b_line_slashing");
			}
			if (r == 50) {
				once(client, "4_mouth_grin");
			}
			if (r == 58) {
				once(client, "4b_marks_piled");
			}
			if (end1At >= 0 && f == end1At + 2) {
				once(client, "5_bite_snap");
			}
			if (end1At >= 0 && f == end1At + 5) {
				once(client, "6_release_burst");
			}
			if (end1At >= 0 && f == end1At + 14) {
				once(client, "7_flung");
			}
		}
		if (lock2At >= 0) {
			int r = f - lock2At;
			if (r == 8) {
				once(client, "8_boss_lock");
			}
			if (end2At >= 0 && f == end2At + 5) {
				once(client, "9_boss_release");
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
		Entity vind = m.world.getEntityById(vindId);
		Entity rav = m.world.getEntityById(ravId);
		Vec3d you = m.player.getLerpedPos(part);
		boolean boss = ravSpawned >= 0 && (end1At >= 0 && t >= end1At + 30 || lock2At >= 0);
		Entity subject = boss ? rav : vind;
		ForeverHubEntity hub = hubFor(boss ? ravId : vindId);
		double rel = boss ? (lock2At < 0 ? -1000 : t - lock2At) : (lock1At < 0 ? -1000 : t - lock1At);
		double endRel = boss ? (end2At < 0 ? -1000 : t - end2At) : (end1At < 0 ? -1000 : t - end1At);
		double v = boss ? 0.5 : 1.0;
		double big = boss ? 1.3 : 1.0;
		String phase = boss ? "R" : "V";
		if (subject == null) {
			// (Nothing of it left to follow: the last place it was.)
			subject = rav != null ? rav : m.player;
		} else {
			lastSubject = subject.getPos();
		}
		Vec3d tp = hub != null ? hub.getPos() : subject.getLerpedPos(part);
		double ground = tp.y;
		List<ForeverCopyEntity> copies = hub != null ? ClientForever.copiesOf(hub) : List.of();
		// The line's way (from the farthest past copy to the farthest future one), and the side of it away from you.
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
			// The charge: beside you and a little ahead, low, the mob running at the lens.
			if (installs(phase + "0", t)) {
				lens = 62.0F;
			}
			Vec3d toMob = flat(subject.getPos().subtract(you));
			Vec3d dirMob = toMob.lengthSquared() < 1.0E-4 ? new Vec3d(1.0, 0.0, 0.0) : toMob.normalize();
			Vec3d side = new Vec3d(-dirMob.z, 0.0, dirMob.x);
			Vec3d sub = subject.getLerpedPos(part);
			at = you.lerp(sub, 0.55).add(side.multiply(4.2 * big)).add(0.0, 1.5, 0.0);
			aim = sub.add(0.0, 1.0 * big, 0.0);
		} else if (endRel < 0.0 && rel < 14.0 * v) {
			// The lock: three quarters on from a little way off, level with the eyes, the ring of flesh round the held one.
			if (installs(phase + "1", t)) {
				lens = 72.0F;
			}
			double k = MathHelper.clamp((t - shotT0) / 20.0, 0.0, 1.0);
			Vec3d way = youSide.multiply(0.8).add(nearEnd.multiply(0.6)).normalize();
			at = new Vec3d(tp.x, ground + 1.55, tp.z).add(way.multiply((7.4 + 0.8 * k) * big));
			aim = tp.add(0.0, 0.95 * big, 0.0);
		} else if (endRel < 0.0 && rel < 44.0 * v) {
			// The hero shot: level with the eyes, square on to the line, far enough off for the whole line (the held one in the middle, four
			// copies each way fading out, the lumps and eyes along the ground) to be in the picture.
			if (installs(phase + "2", t)) {
				lens = 96.0F;
			}
			double k = MathHelper.clamp((t - shotT0) / 30.0, 0.0, 1.0);
			double d = (8.3 - 0.5 * k) * (boss ? 1.2 : 1.0);
			Vec3d one = new Vec3d(tp.x, ground + 2.4, tp.z).add(across.multiply(d));
			Vec3d two = new Vec3d(tp.x, ground + 2.4, tp.z).add(across.negate().multiply(d));
			aim = tp.add(0.0, 0.7 * big, 0.0);
			at = clearTo(m, aim, one).distanceTo(aim) >= clearTo(m, aim, two).distanceTo(aim) ? one : two;
		} else if (endRel < 0.0 && rel < 58.0 * v) {
			// You, running along the line and cutting: from behind and beside, three or four blocks off, the swing and the marks in view.
			if (installs(phase + "S", t)) {
				lens = 68.0F;
			}
			Entity ahead = null;
			double bestD = Double.MAX_VALUE;
			if (hub != null) {
				List<Entity> things = new ArrayList<>(copies);
				things.add(subject);
				for (Entity e : things) {
					double d = flat(e.getPos().subtract(you)).lengthSquared();
					if (d > 2.0 && d < bestD && HITS.getOrDefault(e.getId(), 0) < 2) {
						bestD = d;
						ahead = e;
					}
				}
			}
			float yaw = m.player.getYaw(part);
			Vec3d fwd = new Vec3d(-Math.sin(Math.toRadians(yaw)), 0.0, Math.cos(Math.toRadians(yaw)));
			Vec3d sideways = new Vec3d(-fwd.z, 0.0, fwd.x);
			at = you.add(fwd.multiply(-3.3)).add(sideways.multiply(1.7)).add(0.0, 1.9, 0.0);
			aim = you.add(fwd.multiply(2.6)).add(0.0, 1.1, 0.0);
		} else if (endRel < 14.0) {
			// The release: wide, from the side away from you, level, so the copies can be seen streaking in and the burst.
			if (installs(phase + "4", t)) {
				lens = 84.0F;
			}
			double d = 9.0 * (boss ? 1.25 : 1.0);
			Vec3d one = new Vec3d(tp.x, ground + 2.4, tp.z).add(across.multiply(d));
			Vec3d two = new Vec3d(tp.x, ground + 2.4, tp.z).add(across.negate().multiply(d));
			aim = tp.add(0.0, 0.5 * big, 0.0);
			at = one.squaredDistanceTo(you) >= two.squaredDistanceTo(you) ? one : two;
		} else {
			// After: pulled back and a little up, the flung mob, the flesh sinking, the colour coming back.
			if (installs(phase + "5", t)) {
				lens = 70.0F;
			}
			double k = MathHelper.clamp((t - shotT0) / 40.0, 0.0, 1.0);
			Vec3d sub = subject.getLerpedPos(part);
			at = new Vec3d(tp.x, ground + 2.2, tp.z).add(youSide.multiply((7.5 + 1.5 * k) * big)).add(nearEnd.multiply(2.0));
			aim = tp.lerp(sub, 0.25).add(0.0, 1.1, 0.0);
		}
		return frame(m, hub, copies, subject, ground, at, aim);
	}

	/**
	 * The rules for every shot: no closer than a block and a half to a lump, a copy, the held one or you; between 1.2 and 2.5 blocks over the ground
	 * (the charge excepted a little); nothing solid in the way; and never looking more than about 25 degrees up or down.
	 */
	private static Capture.Pose frame(MinecraftClient m, ForeverHubEntity hub, List<ForeverCopyEntity> copies, Entity subject, double ground, Vec3d at, Vec3d aim) {
		List<double[]> keepOut = new ArrayList<>();
		if (hub != null) {
			keepOut.addAll(ClientForever.lumps(hub));
			for (ForeverCopyEntity c : copies) {
				keepOut.add(new double[] {c.getX(), c.getY() + c.getHeight() * 0.5, c.getZ(), 0.5 + c.getWidth()});
			}
		}
		keepOut.add(new double[] {subject.getX(), subject.getY() + subject.getHeight() * 0.5, subject.getZ(), 0.6 + subject.getWidth()});
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
		// And out of anything standing about (a flung mob, the second player, a copy): a block and a half from every body.
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
		// The pitch: the aim is lowered or raised until it is within 25 degrees.
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

	/** The vindicator, its own AI after you: it is already running at you when the clip begins. */
	private static void spawnVindicator(IntegratedServer server) {
		ServerWorld world = server.getOverworld();
		MobEntity v = EntityType.VINDICATOR.create(world);
		Vec3d at = middle.add(10.6, 0.0, 0.0);
		v.refreshPositionAndAngles(at.x, groundAt(world, at), at.z, 90.0F, 0.0F);
		v.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		v.setPersistent();
		world.spawnEntity(v);
		v.setTarget(player(server));
		vindId = v.getId();
	}

	/** The ravager, charging in from the north across the clearing, its own AI after you. */
	private static void spawnRavager(IntegratedServer server) {
		ServerWorld world = server.getOverworld();
		ServerPlayerEntity you = player(server);
		MobEntity r = EntityType.RAVAGER.create(world);
		// Across the clearing, thirteen blocks off on level open ground, the way it will charge.
		Vec3d at = null;
		double bestDy = Double.MAX_VALUE;
		for (int k = 0; k < 36; k++) {
			double ang = Math.toRadians(110.0 + k * 4.0);
			Vec3d c = you.getPos().add(Math.cos(ang) * 18.0, 0.0, Math.sin(ang) * 18.0);
			double dy = Math.abs(groundAt(world, c) - you.getY());
			if (dy < bestDy) {
				bestDy = dy;
				at = c;
			}
		}
		r.refreshPositionAndAngles(at.x, groundAt(world, at), at.z, 0.0F, 0.0F);
		r.setPersistent();
		world.spawnEntity(r);
		r.setTarget(you);
		ravId = r.getId();
	}

	/** Puts the ravager on level ground about thirteen blocks out, where it will charge from. */
	private static void relocateRavager(IntegratedServer server) {
		ServerWorld world = server.getOverworld();
		ServerPlayerEntity you = player(server);
		Entity r = world.getEntityById(ravId);
		if (r == null) {
			return;
		}
		Vec3d best = null;
		double bestDy = Double.MAX_VALUE;
		for (int k = 0; k < 72; k++) {
			double ang = Math.toRadians(100.0 + k * 2.5);
			for (double dist = 11.0; dist <= 12.0; dist += 1.0) {
				Vec3d c = you.getPos().add(Math.cos(ang) * dist, 0.0, Math.sin(ang) * dist);
				double dy = Math.abs(groundAt(world, c) - you.getY());
				if (dy < bestDy) {
					bestDy = dy;
					best = c;
				}
			}
		}
		if (best != null) {
			r.refreshPositionAndAngles(best.x, groundAt(world, best), best.z, 0.0F, 0.0F);
			r.setVelocity(Vec3d.ZERO);
			ShootingStar.LOGGER.info("[selftest] forever: the ravager brought in to {} (height off {})", best, bestDy);
		}
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
		p.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000.0);
		p.setHealth(1000.0F);
		p.getInventory().setStack(0, new ItemStack(Items.IRON_SWORD));
		p.getInventory().selectedSlot = 0;
		return p;
	}

	/** The friend's script, every server tick: when a moment is held on the vindicator he stands at the far end of its line and cuts a copy there. */
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
		if (afterAt >= 0 && ticks > afterAt + 1) {
			return;
		}
		Entity vind = world.getEntityById(vindId);
		if (!Forever.active(you) || vind == null || !(vind instanceof LivingEntity target) || !Forever.locked(target) || Forever.hub(target) == null) {
			return;
		}
		long since = world.getTime() - Forever.hub(target).startedAt();
		List<ForeverCopyEntity> copies = Forever.copies(target);
		ForeverCopyEntity far = null;
		for (ForeverCopyEntity c : copies) {
			if (far == null || c.slot() < far.slot()) {
				far = c;
			}
		}
		if (far == null || ravSpawned >= 0 && ravId >= 0 && Forever.locked(world.getEntityById(ravId))) {
			return;
		}
		if (friend == null) {
			friend = makeFriend(server, world, far.getPos().add(1.4, 0.0, 0.0));
			friendId = friend.getId();
		}
		Vec3d spot = far.getPos().add(1.1, 0.0, 0.6);
		if (since < 6) {
			return;
		}
		friend.refreshPositionAndAngles(spot.x, far.getY(), spot.z, (float) Math.toDegrees(Math.atan2(-(far.getX() - spot.x), far.getZ() - spot.z)), 0.0F);
		friend.setHeadYaw(friend.getYaw());
		friend.setBodyYaw(friend.getYaw());
		if (since == 20 || since == 31 || since == 42) {
			// Cut: his cooldown caught up first (a connection would have ticked it).
			for (int i = 0; i < 14; i++) {
				friend.playerTick();
			}
			friend.swingHand(Hand.MAIN_HAND, true);
			friend.attack(far);
			ShootingStar.LOGGER.info("[selftest] forever: the friend cuts a copy at since={}", since);
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
