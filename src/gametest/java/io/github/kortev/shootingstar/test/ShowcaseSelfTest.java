package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.form.Blood;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.FormTimeline;
import io.github.kortev.chainsawman.form.Grapple;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.form.Limbs;
import io.github.kortev.chainsawman.form.Saw;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;

/**
 * With -Dshootingstar.selftest=showcase: the mod's showcase reels (kortev, 2026-10-22: a reel per feature, 10-15 s,
 * filmed tall in a village at dusk, "Chainsaw Man mod" over each). -Ptarget= picks the reel; the first, and the default,
 * is {@code transform}: you, surrounded by zombies in the street, pull the cord (the mod's own transformation, its own
 * camera), cut the three of them down in the combo, and turn to the camera as it eases in, "What should I add next?"
 * over it. {@code saws}: already Chainsaw Man, you grind out through the wall of a hut, lunge into the first zombie
 * outside, take three more in the combo (across, back, the overhead finisher) and grind the last one down; it ends on
 * that kill, to loop. Play as kortev (-Pusername=Kortev) to wear their skin. With -Pcapture=true it's recorded with its sounds
 * (Capture) into build/selftest/capture; either way a few frames are saved as pictures (showcase_*.png).
 */
public class ShowcaseSelfTest implements ClientModInitializer {
	private enum Stage {
		WAIT_WORLD, SETUP, SETTLE, RUN, DONE
	}

	private static final String TITLE = "CHAINSAW MAN MOD";
	private static final String ASK = "What should I add next?";

	/** The way you face at the start: east, so every shot toward you has the sunset behind you. */
	private static final float FACING = -90.0F;
	/** The zombies round you: the angle from the way you face (degrees) and how far off; none in front, for the roar. */
	private static final float[] AROUND = {70.0F, -70.0F, 180.0F};
	private static final double OFF = 2.3;
	/** Your swings at them, in order (the combo: across, back, overhead); the cuts to each, a little before. */
	private static final int[] SLASHES = {66, 80, 94};
	/** The last shot: you turning to the camera as it eases in, and the question. */
	private static final int TURN = 104;
	private static final int ASKED = 116;
	private static final int END = 210;
	private static final int[] SCREENS = {12, 30, 40, 48, 58, 68, 76, 84, 96, 102, 112, 124, 200};

	// The saws reel. The hut you're in (east of you its front wall, the way out), the hole cut through it (across, up)
	// a block at a time; where you stand clear of it, and lunge from; where you end up, the zombies round there (angles
	// from east) and when you cut each.
	private static final int HUT_FRONT = 2;
	private static final int[][] HOLE = {{0, 1}, {0, 2}, {1, 1}, {-1, 1}, {0, 0}, {1, 2}, {-1, 2}};
	private static final int CUT_FROM = 4;
	private static final int CUT_EVERY = 5;
	private static final int OUT_SHOT = 30;
	private static final int OUT_FROM = 38;
	private static final int OUT_AT = 54;
	private static final Vec3d OUTSIDE = new Vec3d(3.6, 0.0, 0.0);
	/** The lunge (it carries about six blocks), at the one waiting outside. */
	private static final int LUNGE = 58;
	private static final Vec3d WAITING = new Vec3d(5.8, 0.0, 0.0);
	/**
	 * Where you've landed: the rest round you there, put there as the camera cuts away (anywhere near the lunge's way
	 * it would cut them down too). Spread so each slash takes only its own (the finisher's narrower); the last, ground
	 * down, tougher, in case a slash catches it.
	 */
	private static final int LANDED = 67;
	private static final float[] RING = {55.0F, -55.0F, 180.0F, -120.0F};
	private static final double RING_OFF = 2.9;
	private static final int[] SAW_SLASHES = {80, 94, 108};
	private static final int GRIND_FROM = 122;
	private static final int GRIND_TO = 150;
	private static final int SAW_END = 162;
	private static final int[] SAW_SCREENS = {2, 12, 24, 36, 46, 56, 62, 72, 82, 96, 110, 120, 130, 142, 158};
	// The chain reel. A lookout tower ahead of you (its near face, across, its height), the two on top of it; when the
	// chain's fired at each and when each is cut down; the chain at the tower and you up it, onto the top.
	private static final int TOWER_NEAR = 11;
	private static final int TOWER_HIGH = 5;
	private static final Vec3d[] ON_TOP = {new Vec3d(11.9, TOWER_HIGH, -0.7), new Vec3d(12.9, TOWER_HIGH, 0.8)};
	private static final int[] YANKS = {8, 28};
	private static final int[] YANK_CUTS = {17, 37};
	/** How long one takes to come flying down to you. */
	private static final int FLY = 7;
	private static final int AIM_UP = 44;
	private static final int ZIP = 50;
	private static final int CLIMB = 60;
	private static final int ON_TOWER = 68;
	private static final int CHAIN_END = 118;
	private static final int[] CHAIN_SCREENS = {2, 8, 12, 16, 18, 24, 30, 36, 40, 48, 53, 57, 62, 70, 84, 112};

	// The arms reel. The two with axes (angles from the way you face, how far off); when each swings, and your arm comes
	// off; armless a while; when you cut each down with the head's saw (each kill's blood growing an arm back); the end.
	private static final float[] AXES = {38.0F, -38.0F};
	private static final double AXE_OFF = 2.1;
	private static final int[] CHOPS = {14, 40};
	private static final int ARMLESS = 54;
	private static final int[] HEADBUTTS = {84, 112};
	private static final int ARMS_HERO = 126;
	private static final int ARMS_END = 186;
	private static final int[] ARMS_SCREENS = {6, 14, 18, 26, 40, 44, 58, 70, 80, 86, 92, 100, 114, 120, 130, 180};

	// The Pochita reel (in his fallen tree's own coordinates: along it, across it). Along his blood's trail to him; down
	// by him; your blood into him (twice the game's pace: two feeds a tick); his waking; picked up, the chainsaw; the
	// one that comes for you, cut down with him; the end.
	// (From inside the clearing, levelled to his ground: the trail's first drops are out past its edge, often lower.)
	private static final double[][] TRAIL = {{2.4, -8.4}, {1.8, -7.6}, {1.4, -6.0}, {0.8, -4.4}, {0.4, -2.9}};
	private static final double[] KNEEL = {0.2, -1.9};
	private static final int WALK_TO = 46;
	private static final int FEED_FROM = 52;
	private static final int WOKEN = FEED_FROM + 40;
	private static final int PICKED_UP = WOKEN + 26;
	private static final int ZOMBIE_IN = PICKED_UP + 4;
	private static final Vec3d COMES_FROM = new Vec3d(4.6, 0.0, -4.4);
	private static final int CUT_IT = PICKED_UP + 24;
	private static final int POCHITA_HERO = CUT_IT + 24;
	private static final int POCHITA_END = POCHITA_HERO + 56;
	private static final int[] POCHITA_SCREENS = {4, 24, 44, 56, 70, 84, 94, 104, 116, 124, 134, 142, 150, 158, 170, 210};
	private static int pochitaId = -1;
	/** The husk's way to you. */
	private static Vec3d huskFrom;
	private static Vec3d huskTo;

	/** Where you landed. */
	private static Vec3d landed;
	/** The way you face at the start of the saws reel: whichever way its spot runs. */
	private static float facing = FACING;

	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	/** Where you stand (your feet), and the zombies' ids. */
	private static Vec3d origin;
	private static final List<Integer> ZOMBIES = new ArrayList<>();
	private static int shots;

	public static boolean active() {
		return "showcase".equals(System.getProperty("shootingstar.selftest"));
	}

	private static String target() {
		String target = System.getProperty("shootingstar.selftest.target", "");
		return target.isEmpty() ? "transform" : target;
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
			ShootingStar.LOGGER.error("[selftest] showcase timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-showcase-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		if (capturing()) {
			ServerTickEvents.START_SERVER_TICK.register(server -> Capture.serverTickStart());
			ServerTickEvents.END_SERVER_TICK.register(server -> Capture.serverTickEnd());
		}
		ClientTickEvents.END_CLIENT_TICK.register(ShowcaseSelfTest::tick);
		HudRenderCallback.EVENT.register((context, counter) -> {
			if (stage == Stage.RUN) {
				lettering(context, ticks + counter.getTickDelta(true));
			}
		});
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
				// Long enough for the village round you to be drawn.
				if (ticks > 100 && origin != null) {
					stage = Stage.RUN;
					ticks = 0;
				}
			}
			case RUN -> {
				// No pop-ups over the picture (the first kill's advancement).
				client.getToastManager().clear();
				if (ticks == 1) {
					server.execute(() -> strays(server));
				}
				if ("saws".equals(target())) {
					saws(client, server, ticks);
				} else if ("chain".equals(target())) {
					chain(client, server, ticks);
				} else if ("arms".equals(target())) {
					arms(client, server, ticks);
				} else if ("pochita".equals(target())) {
					pochita(client, server, ticks);
				} else if ("held".equals(target())) {
					held(client, server, ticks);
				} else {
					transform(client, server, ticks);
				}
				head(client);
			}
			case DONE -> {
				Capture.stop();
				ShootingStar.LOGGER.info("[selftest] showcase: done");
				stage = null;
				client.scheduleStop();
			}
			default -> {
			}
		}
	}

	// The transformation reel.

	private static void transform(MinecraftClient client, IntegratedServer server, int f) {
		if (f == 1) {
			client.options.hudHidden = false;
			client.options.setPerspective(Perspective.FIRST_PERSON);
			if (capturing()) {
				Capture.start(client, client.runDirectory.toPath().resolve("capture"));
			}
			// The mod's own camera for the transformation, as anyone pulling the cord sees it.
			Capture.camera = null;
			server.execute(() -> Form.pullCord(player(server)));
		}
		// Then yours: a cut to each zombie as you turn on it, low and to one side, looking past you at it.
		for (int i = 0; i < SLASHES.length; i++) {
			int from = i == 0 ? FormTimeline.ROAR_END : SLASHES[i - 1] + 3;
			if (f == from) {
				Vec3d you = origin.add(0.0, 1.1, 0.0);
				Vec3d it = origin.add(around(i).multiply(OFF)).add(0.0, 1.0, 0.0);
				Vec3d side = around(i).rotateY((float) Math.toRadians(i % 2 == 0 ? -55.0 : 55.0));
				Vec3d at = framed(origin.add(side.multiply(4.3)).add(0.0, 1.3, 0.0), you);
				Capture.Pose pose = look(at, you.lerp(it, 0.45));
				Capture.camera = t -> pose;
			}
			if (f > from && f <= SLASHES[i] - 2) {
				float start = i == 0 ? FACING : FACING + AROUND[i - 1];
				face(client, turn(start, FACING + AROUND[i], ease(span(f, from, SLASHES[i] - 3))));
			}
			if (f == SLASHES[i]) {
				server.execute(() -> Saw.slash(player(server)));
			}
		}
		// The last shot: in front of you and off to one side (head on, the head's saw points at the lens and reads as a
		// spike), a little low, the sunset behind you, easing in as you turn to it.
		if (f >= TURN) {
			face(client, turn(FACING + AROUND[AROUND.length - 1], FACING, ease(span(f, TURN, TURN + 10))));
			Vec3d you = origin.add(0.0, 1.45, 0.0);
			double side = Math.toRadians(FACING - 40.0);
			Vec3d off = new Vec3d(-Math.sin(side), 0.0, Math.cos(side));
			// (Not so low that the grass between is in the way.)
			Vec3d far = framed(origin.add(off.multiply(6.5)).add(0.0, 1.5, 0.0), you);
			Vec3d near = framed(origin.add(off.multiply(3.4)).add(0.0, 1.25, 0.0), you);
			Capture.camera = t -> {
				double k = ease(span(t, TURN, END));
				return look(far.lerp(near, k), you);
			};
		}
		if (shots < SCREENS.length && f == SCREENS[shots]) {
			shot(client, target() + "_" + f);
			shots++;
		}
		if (f >= END) {
			stage = Stage.DONE;
		}
	}

	// The saws reel.

	private static void saws(MinecraftClient client, IntegratedServer server, int f) {
		if (f == 1) {
			client.options.hudHidden = false;
			if (capturing()) {
				Capture.start(client, client.runDirectory.toPath().resolve("capture"));
			}
			// In the dark of the hut over your shoulder, the saws tearing into the wall, the sunset pouring in through it.
			fixed(local(-1.9, 2.3, 0.85), local(HUT_FRONT, 1.2, -0.1));
			server.execute(() -> Saw.grind(player(server), true));
		}
		if (f == OUT_SHOT) {
			// Outside, off to one side, as you come through it.
			fixed(framed(local(7.0, 1.8, 2.4), local(HUT_FRONT + 0.5, 1.3, 0.0)), local(HUT_FRONT, 1.2, 0.0));
		}
		if (client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
			// (Never your own hands: the camera's always the film's.)
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		}
		if (f < LUNGE) {
			face(client, facing, 0.0F);
		}
		// The wall cut through, a block at a time, as fast as the form's saws cut.
		for (int i = 0; i < HOLE.length; i++) {
			if (f == CUT_FROM + i * CUT_EVERY) {
				BlockPos p = cell(BlockPos.ofFloored(origin), HUT_FRONT, HOLE[i][1], HOLE[i][0]);
				server.execute(() -> player(server).getServerWorld().breakBlock(p, false, player(server)));
			}
		}
		if (f == OUT_FROM - 1) {
			server.execute(() -> Saw.grind(player(server), false));
		}
		if (f >= OUT_FROM && f <= OUT_AT) {
			go(client, local(OUTSIDE.x * ease(span(f, OUT_FROM, OUT_AT)), 0.0, 0.0));
		}
		// The lunge, seen from the side, wide.
		if (f == LUNGE - 4) {
			fixed(framed(local(5.5, 2.2, -7.5), local(6.5, 1.0, 0.0)), local(7.0, 1.0, 0.0));
		}
		if (f == LUNGE) {
			server.execute(() -> Saw.lunge(player(server)));
		}
		if (f == LANDED && client.player != null) {
			landed = client.player.getPos();
			Vec3d at = landed;
			server.execute(() -> {
				for (int i = 0; i < RING.length; i++) {
					zombie(server.getOverworld(), at.add(way(RING[i]).multiply(RING_OFF)), facing + RING[i] + 180.0F, i == RING.length - 1 ? 12.0F : 6.0F);
				}
			});
		}
		if (f >= LANDED && landed != null) {
			go(client, landed);
		}
		// Three in the combo, a cut to each as you turn on it.
		for (int i = 0; i < SAW_SLASHES.length; i++) {
			int from = i == 0 ? LANDED : SAW_SLASHES[i - 1] + 3;
			if (f == from && landed != null) {
				victimShot(landed, RING[i], i % 2 == 0 ? -55.0 : 55.0, 4.3, 1.8);
			}
			if (f > from && f <= SAW_SLASHES[i] - 2) {
				float start = i == 0 ? facing : facing + RING[i - 1];
				face(client, turn(start, facing + RING[i], ease(span(f, from, SAW_SLASHES[i] - 3))), 0.0F);
			}
			if (f == SAW_SLASHES[i]) {
				server.execute(() -> Saw.slash(player(server)));
			}
		}
		// The last, ground down: from its other side (closer in, the bar fills the picture and hides it).
		int from = SAW_SLASHES[SAW_SLASHES.length - 1] + 3;
		if (f == from && landed != null) {
			victimShot(landed, RING[3], -60.0, 4.6, 1.9);
		}
		if (f > from && f <= GRIND_FROM - 2) {
			face(client, turn(facing + RING[2], facing + RING[3], ease(span(f, from, GRIND_FROM - 3))), 0.0F);
		}
		if (f == GRIND_FROM) {
			server.execute(() -> Saw.grind(player(server), true));
		}
		if (f == GRIND_TO) {
			server.execute(() -> Saw.grind(player(server), false));
		}
		if (shots < SAW_SCREENS.length && f == SAW_SCREENS[shots]) {
			shot(client, target() + "_" + f);
			shots++;
		}
		if (f >= SAW_END) {
			stage = Stage.DONE;
		}
	}

	// The chain reel.

	private static void chain(MinecraftClient client, IntegratedServer server, int f) {
		if (f == 1) {
			client.options.hudHidden = false;
			if (capturing()) {
				Capture.start(client, client.runDirectory.toPath().resolve("capture"));
			}
			// Low behind you and off to one side: you in front, the tower and the two on it over your shoulder.
			fixed(local(-4.0, 0.9, 3.4), local(7.5, 3.4, -0.3));
		}
		if (client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		}
		for (int i = 0; i < YANKS.length; i++) {
			int from = i == 0 ? 1 : YANK_CUTS[i - 1] + 3;
			// Turned on it, looking up at it; the chain at it; then level again for the cut as it comes flying in.
			if (f >= from && f <= YANKS[i]) {
				Vec3d it = local(ON_TOP[i].x, ON_TOP[i].y + 1.0, ON_TOP[i].z);
				Vec3d eye = client.player != null ? client.player.getEyePos() : origin.add(0.0, 1.6, 0.0);
				Capture.Pose aim = look(eye, it);
				float yaw = i == 0 ? aim.yaw() : turn(facing, aim.yaw(), ease(span(f, from, YANKS[i] - 2)));
				face(client, yaw, aim.pitch());
			}
			if (f == YANKS[i] - 1) {
				// Whatever the chain still holds let go, or firing it again only lets go.
				server.execute(() -> Grapple.release(player(server)));
			}
			if (f == YANKS[i]) {
				server.execute(() -> Grapple.fire(player(server)));
			}
			// Flung off the tower to land in front of you: carried there in an arc (left to the game it might not
			// come off the edge at all, as the yank is only a push).
			if (f > YANKS[i] && f <= YANKS[i] + FLY) {
				int id = ZOMBIES.size() > i ? ZOMBIES.get(i) : -1;
				Vec3d top = local(ON_TOP[i].x, ON_TOP[i].y, ON_TOP[i].z);
				Vec3d flat = new Vec3d(top.x - origin.x, 0.0, top.z - origin.z).normalize();
				Vec3d to = origin.add(flat.multiply(2.0));
				double k = (f - YANKS[i]) / (double) FLY;
				Vec3d at = top.lerp(to, k).add(0.0, Math.sin(k * Math.PI) * 1.5, 0.0);
				server.execute(() -> {
					if (server.getOverworld().getEntityById(id) instanceof ZombieEntity z && z.isAlive()) {
						z.requestTeleport(at.x, at.y, at.z);
						z.setVelocity(Vec3d.ZERO);
						z.fallDistance = 0.0F;
					}
				});
			}
			// Turned on it as it comes flying in (it overshoots, and the chain brings it back), for the cut.
			if (f > YANKS[i] + 1 && f <= YANK_CUTS[i] && client.player != null && client.world != null) {
				Entity it = ZOMBIES.size() > i ? client.world.getEntityById(ZOMBIES.get(i)) : null;
				if (it != null) {
					Capture.Pose aim = look(client.player.getEyePos(), it.getBoundingBox().getCenter());
					face(client, turn(client.player.getYaw(), aim.yaw(), 0.6), MathHelper.clamp(aim.pitch(), -20.0F, 20.0F));
				}
			}
			if (f == YANKS[i] + 4) {
				// Side on, close, as it comes in and is cut down.
				double side = i == 0 ? -4.4 : 4.4;
				fixed(local(1.6, 1.5, side), local(2.0, 1.2, 0.0));
			}
			if (f == YANK_CUTS[i]) {
				int id = ZOMBIES.size() > i ? ZOMBIES.get(i) : -1;
				server.execute(() -> Saw.slash(player(server), id));
			}
			if (f == YANK_CUTS[i] + 4 && i == 0) {
				// Back for the next: the same as the start, a little wider.
				fixed(local(-4.5, 1.0, -3.6), local(7.5, 3.4, 0.3));
			}
		}
		// Up the tower on the chain, seen wide from the side.
		if (f == AIM_UP) {
			fixed(local(5.0, 2.4, -10.0), local(6.5, 3.0, 0.0));
		}
		if (f >= AIM_UP && f <= ZIP) {
			Vec3d at = local(TOWER_NEAR, TOWER_HIGH - 0.7, 0.0);
			Vec3d eye = client.player != null ? client.player.getEyePos() : origin.add(0.0, 1.6, 0.0);
			Capture.Pose aim = look(eye, at);
			face(client, aim.yaw(), aim.pitch());
		}
		if (f == ZIP - 1) {
			server.execute(() -> Grapple.release(player(server)));
		}
		if (f == ZIP) {
			server.execute(() -> Grapple.fire(player(server)));
		}
		// At the top of the chain: over the edge and onto the top.
		if (f >= CLIMB && f < CLIMB + 4) {
			go(client, local(TOWER_NEAR - 0.6, TOWER_HIGH + 0.6, 0.0));
		}
		if (f >= CLIMB + 4 && f < ON_TOWER) {
			go(client, local(TOWER_NEAR + 1.2, TOWER_HIGH, 0.0));
		}
		// On top, turned back to the way you came, the sunset behind you, the camera low on the ground looking up.
		if (f == ON_TOWER) {
			fixed(local(3.6, 2.6, 5.4), local(TOWER_NEAR + 1.0, TOWER_HIGH + 1.2, 0.0));
		}
		if (f >= ON_TOWER) {
			face(client, turn(facing, facing + 150.0F, ease(span(f, ON_TOWER, ON_TOWER + 12))), 10.0F);
		}
		if (shots < CHAIN_SCREENS.length && f == CHAIN_SCREENS[shots]) {
			shot(client, target() + "_" + f);
			shots++;
		}
		if (f >= CHAIN_END) {
			stage = Stage.DONE;
		}
	}

	/**
	 * Any zombie that isn't this reel's taken away: the test world keeps what an earlier take left (one still standing),
	 * and its chunk's entities can load after the set was cleared.
	 */
	private static void strays(IntegratedServer server) {
		ServerWorld world = server.getOverworld();
		for (ZombieEntity z : world.getEntitiesByClass(ZombieEntity.class, new net.minecraft.util.math.Box(BlockPos.ofFloored(origin)).expand(48.0, 24.0, 48.0),
				z -> !ZOMBIES.contains(z.getId()))) {
			z.discard();
		}
	}

	// Looking it over: Pochita held as a chainsaw (photographs; with -Pcapture=true, a video of him in use).

	private static void held(MinecraftClient client, IntegratedServer server, int f) {
		if (capturing()) {
			heldInUse(client, server, f);
			return;
		}
		// From in front three quarters, the side, behind, close on the hands, your own eyes; then cutting a zombie.
		double[][][] shots = {{{2.6, 1.4, -1.6}, {0.0, 1.0, 0.0}}, {{0.0, 1.2, -2.8}, {0.0, 1.0, 0.0}}, {{-2.4, 1.6, 1.4}, {0.0, 1.0, 0.0}},
				{{1.3, 1.2, -0.7}, {0.3, 0.9, 0.0}}, null, {{1.0, 1.6, -3.8}, {1.0, 1.0, 0.0}}};
		String[] names = {"front", "side", "back", "hands", "own_eyes", "cutting"};
		int shot = (f - 1) / 30;
		if (f == 1) {
			// (Shown, or your hands aren't drawn in your own eyes: the game's own HUD is kept off anyway, ReelHudMixin.)
			client.options.hudHidden = false;
		}
		if (shot < shots.length && (f - 1) % 30 == 0) {
			face(client, facing, 0.0F);
			if (shots[shot] == null) {
				Capture.camera = null;
				client.options.setPerspective(Perspective.FIRST_PERSON);
			} else {
				client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				fixed(local(shots[shot][0][0], shots[shot][0][1], shots[shot][0][2]), local(shots[shot][1][0], shots[shot][1][1], shots[shot][1][2]));
			}
			if ("cutting".equals(names[shot])) {
				server.execute(() -> {
					zombie(server.getOverworld(), local(2.0, 0.0, 0.0), facing + 180.0F, 200.0F);
					Saw.grind(player(server), true);
				});
			}
		}
		if (shot < shots.length && (f - 1) % 30 == 25) {
			shot(client, "held_" + names[shot]);
		}
		if (shot >= shots.length) {
			server.execute(() -> Saw.grind(player(server), false));
			stage = Stage.DONE;
		}
	}

	/**
	 * Him in use, played with the real attack key: idling from in front, then in your own eyes; cutting a husk in your own
	 * eyes and from the side; cutting down a tree in your own eyes, a log at a time from the top.
	 */
	private static void heldInUse(MinecraftClient client, IntegratedServer server, int f) {
		if (f == 1) {
			client.options.hudHidden = false;
			Capture.start(client, client.runDirectory.toPath().resolve("capture"));
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			fixed(local(2.6, 1.4, -1.6), local(0.0, 1.0, 0.0));
		}
		if (f < 41) {
			face(client, facing, 0.0F);
		}
		if (f == 41 || f == 81 || f == 191) {
			Capture.camera = null;
			client.options.setPerspective(Perspective.FIRST_PERSON);
		}
		if (f >= 41 && f < 191) {
			face(client, facing, 12.0F);
		}
		if (f == 81 || f == 136) {
			server.execute(() -> zombie(server.getOverworld(), EntityType.HUSK, local(1.8, 0.0, 0.0), facing + 180.0F, 20.0F));
		}
		if (f == 136) {
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			fixed(local(1.0, 1.6, -3.8), local(1.0, 1.0, 0.0));
		}
		if (f == 191) {
			// A tree trunk, three logs high, in front of you.
			server.execute(() -> {
				for (int y = 0; y < 3; y++) {
					player(server).getServerWorld().setBlockState(BlockPos.ofFloored(local(1.6, y + 0.5, 0.0)), Blocks.OAK_LOG.getDefaultState());
				}
			});
		}
		if (f >= 191) {
			// Down the trunk a log at a time.
			float pitch = f < 210 ? -14.0F : f < 224 ? 2.0F : 34.0F;
			face(client, facing, pitch);
		}
		boolean cutting = f >= 88 && f < 130 || f >= 143 && f < 185 || f >= 196 && f < 250;
		client.options.attackKey.setPressed(cutting);
		if (f >= 262) {
			client.options.attackKey.setPressed(false);
			stage = Stage.DONE;
		}
	}

	// The Pochita reel.

	private static void pochita(MinecraftClient client, IntegratedServer server, int f) {
		if (f == 1) {
			client.options.hudHidden = false;
			if (capturing()) {
				Capture.start(client, client.runDirectory.toPath().resolve("capture"));
			}
			// Across the clearing (over the fight's broken branch): the tree, him under it, you coming in along the trail.
			fixed(framed(local(6.5, 2.8, -7.5), local(0.5, 0.6, -2.0)), local(1.0, 0.7, -3.5));
		}
		if (client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		}
		Vec3d him = local(0.5, 0.3, 0.5);
		if ((f == 1 || f == WALK_TO || f == PICKED_UP || f == POCHITA_HERO) && client.player != null) {
			ShootingStar.LOGGER.info("[selftest] showcase: pochita f={} you at {} (origin {})", f, client.player.getPos(), origin);
		}
		// In along the trail, drop to drop.
		if (f <= WALK_TO && client.player != null) {
			double k = span(f, 1, WALK_TO) * TRAIL.length;
			int i = Math.min((int) k, TRAIL.length - 1);
			double[] a = TRAIL[i];
			double[] b = i + 1 < TRAIL.length ? TRAIL[i + 1] : KNEEL;
			Vec3d want = local(MathHelper.lerp(k - i, a[0], b[0]), 0.0, MathHelper.lerp(k - i, a[1], b[1]));
			go(client, new Vec3d(want.x, origin.y, want.z));
			face(client, look(client.player.getEyePos(), him).yaw(), 25.0F);
		}
		// Down by him, giving him your blood: close, across from you under the trunk.
		if (f == WALK_TO + 2) {
			fixed(framed(local(3.6, 1.7, -5.0), local(0.4, 0.6, -1.4)), local(0.3, 0.5, -1.0));
		}
		if (f > WALK_TO && f < PICKED_UP && client.player != null) {
			client.options.sneakKey.setPressed(true);
			face(client, look(client.player.getEyePos(), him).yaw(), 35.0F);
		}
		if (f >= FEED_FROM && f < WOKEN) {
			int id = pochitaId;
			server.execute(() -> {
				ServerPlayerEntity p = player(server);
				if (p.getServerWorld().getEntityById(id) instanceof io.github.kortev.chainsawman.PochitaEntity pochita && !pochita.isTamed()) {
					pochita.feed(p);
					pochita.feed(p);
				}
				if ((f - FEED_FROM) % 8 == 0) {
					p.swingHand(Hand.MAIN_HAND, true);
				}
			});
		}
		if (f == FEED_FROM + 16) {
			// Low on him from the other side of the trunk, your hand at him beyond.
			fixed(framed(local(1.4, 0.8, 3.6), him), local(0.3, 0.45, -0.6));
		}
		// Awake: wider again, him getting up.
		if (f == WOKEN + 2) {
			fixed(framed(local(3.6, 1.3, -3.6), local(0.3, 0.7, -0.8)), local(0.4, 0.6, -0.8));
		}
		// Picked up: he's the chainsaw in your hand.
		if (f == PICKED_UP) {
			int id = pochitaId;
			server.execute(() -> {
				ServerPlayerEntity p = player(server);
				if (p.getServerWorld().getEntityById(id) instanceof io.github.kortev.chainsawman.PochitaEntity pochita) {
					p.setSneaking(true);
					pochita.interactMob(p, Hand.MAIN_HAND);
				}
			});
		}
		if (f == PICKED_UP + 3) {
			client.options.sneakKey.setPressed(false);
		}
		// Something coming for you out of the trees; you turn and cut it down with him.
		if (f == ZOMBIE_IN && client.player != null) {
			Vec3d at = local(COMES_FROM.x, 0.0, COMES_FROM.z);
			Vec3d you = client.player.getPos();
			// Where it'll stop, in front of you; the camera side-on to the two of you, on the open side (away from the
			// tree), so both are in the tall frame.
			Vec3d way = at.subtract(you).multiply(1.0, 0.0, 1.0).normalize();
			huskFrom = new Vec3d(at.x, you.y, at.z);
			huskTo = you.add(way.multiply(1.3));
			Vec3d side = new Vec3d(-way.z, 0.0, way.x);
			Vec3d mid = you.lerp(huskTo, 0.5).add(0.0, 1.0, 0.0);
			Vec3d out = mid.add(side.multiply(4.2)).add(way.multiply(-0.6)).add(0.0, 0.5, 0.0);
			Vec3d other = mid.add(side.multiply(-4.2)).add(way.multiply(-0.6)).add(0.0, 0.5, 0.0);
			Vec3d tree = local(0.0, 0.0, 0.0);
			fixed(framed(out.squaredDistanceTo(tree) > other.squaredDistanceTo(tree) ? out : other, mid), mid);
			float yaw = look(at, you).yaw();
			// A husk: the sun's not down here yet, and a zombie would burn up before it got to you.
			server.execute(() -> zombie(server.getOverworld(), EntityType.HUSK, at, yaw, 12.0F));
		}
		if (f > ZOMBIE_IN && f <= CUT_IT && client.player != null && client.world != null && !ZOMBIES.isEmpty() && huskFrom != null) {
			// It shambles at you, and you turn to it.
			Vec3d next = huskFrom.lerp(huskTo, span(f, ZOMBIE_IN, CUT_IT - 2));
			int id = ZOMBIES.get(0);
			server.execute(() -> {
				if (server.getOverworld().getEntityById(id) instanceof ZombieEntity z && z.isAlive()) {
					z.requestTeleport(next.x, next.y, next.z);
				}
			});
			face(client, look(client.player.getEyePos(), next.add(0.0, 1.0, 0.0)).yaw(), 8.0F);
		}
		// The attack key held as it comes: he revs, and cuts into it as it reaches you.
		client.options.attackKey.setPressed(f >= CUT_IT - 10 && f < CUT_IT + 18);
		// The end: low, three quarters on, him in your hand, the light in the trees behind.
		if (f == POCHITA_HERO && client.player != null) {
			Vec3d you = client.player.getPos().add(0.0, 1.2, 0.0);
			fixed(framed(local(3.4, 1.0, -5.4), you), you);
		}
		if (f > POCHITA_HERO && client.player != null) {
			// Turned to the camera, three quarters on.
			face(client, look(client.player.getEyePos(), local(3.4, 1.0, -5.4)).yaw() + 35.0F, 6.0F);
		}
		if (shots < POCHITA_SCREENS.length && f == POCHITA_SCREENS[shots]) {
			shot(client, target() + "_" + f);
			shots++;
		}
		if (f >= POCHITA_END) {
			client.options.attackKey.setPressed(false);
			stage = Stage.DONE;
		}
	}

	// The arms reel.

	private static void arms(MinecraftClient client, IntegratedServer server, int f) {
		if (f == 1) {
			client.options.hudHidden = false;
			if (capturing()) {
				Capture.start(client, client.runDirectory.toPath().resolve("capture"));
			}
		}
		if (client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		}
		for (int i = 0; i < CHOPS.length; i++) {
			// On you and the one about to strike, from the far side of you: the arm it takes flies toward the camera.
			int from = i == 0 ? 1 : CHOPS[i - 1] + 12;
			if (f == from) {
				Vec3d it = origin.add(way(AXES[i]).multiply(AXE_OFF)).add(0.0, 1.2, 0.0);
				Vec3d chest = origin.add(0.0, 1.3, 0.0);
				Vec3d at = framed(origin.add(way(AXES[i] + (i == 0 ? -110.0F : 110.0F)).multiply(3.6)).add(0.0, 1.9, 0.0), chest);
				fixed(at, chest.lerp(it, 0.4));
			}
			if (f >= from && f < CHOPS[i]) {
				face(client, turn(i == 0 ? facing : facing + AXES[0], facing + AXES[i], ease(span(f, from, CHOPS[i] - 4))), 0.0F);
			}
			if (f == CHOPS[i] - 3) {
				int id = ZOMBIES.get(i);
				server.execute(() -> {
					if (server.getOverworld().getEntityById(id) instanceof VindicatorEntity v) {
						v.swingHand(Hand.MAIN_HAND);
					}
				});
			}
			if (f == CHOPS[i]) {
				server.execute(() -> {
					ServerPlayerEntity p = player(server);
					p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 1.0F, 0.8F);
					Limbs.lose(p);
				});
			}
		}
		// Armless: close and low on you from between the two of them (you turned a little off it), the stubs spurting.
		if (f == ARMLESS) {
			fixed(framed(local(2.7, 1.1, 0.0), origin.add(0.0, 1.4, 0.0)), origin.add(0.0, 1.45, 0.0));
		}
		if (f >= ARMLESS && f < ARMLESS + 20) {
			face(client, turn(facing + AXES[1], facing - 25.0F, ease(span(f, ARMLESS, ARMLESS + 8))), 0.0F);
		}
		// The head's saw: each in turn, its kill's blood growing an arm back.
		for (int i = 0; i < HEADBUTTS.length; i++) {
			int from = i == 0 ? ARMLESS + 20 : HEADBUTTS[i - 1] + 4;
			if (f == from) {
				// Side on to you and it, a little back, so the arm growing out shows.
				Vec3d mid = origin.add(way(AXES[i]).multiply(AXE_OFF * 0.5)).add(0.0, 1.3, 0.0);
				Vec3d at = framed(origin.add(way(AXES[i] + (i == 0 ? 90.0F : -90.0F)).multiply(4.2)).add(0.0, 1.8, 0.0), mid);
				fixed(at, mid);
			}
			if (f > from && f <= HEADBUTTS[i] - 2) {
				face(client, turn(i == 0 ? facing - 25.0F : facing + AXES[0], facing + AXES[i], ease(span(f, from, HEADBUTTS[i] - 4))), 0.0F);
			}
			if (f == HEADBUTTS[i]) {
				int id = ZOMBIES.get(i);
				server.execute(() -> Saw.slash(player(server), id));
			}
		}
		// Whole again: low, three quarters on, the sunset behind.
		if (f == ARMS_HERO) {
			fixed(framed(local(3.2, 0.7, -2.0), origin.add(0.0, 1.3, 0.0)), origin.add(0.0, 1.5, 0.0));
		}
		if (f >= ARMS_HERO) {
			face(client, turn(facing + AXES[1], facing + 15.0F, ease(span(f, ARMS_HERO, ARMS_HERO + 10))), 6.0F);
		}
		if (shots < ARMS_SCREENS.length && f == ARMS_SCREENS[shots]) {
			shot(client, target() + "_" + f);
			shots++;
		}
		if (f >= ARMS_END) {
			stage = Stage.DONE;
		}
	}

	/**
	 * A stone lookout tower {@link #TOWER_NEAR} ahead of {@code feet}, 3 by 3 and {@link #TOWER_HIGH} high: cobblestone
	 * with log corners, a plank top.
	 */
	private static void tower(ServerWorld world, BlockPos feet) {
		for (int x = TOWER_NEAR; x <= TOWER_NEAR + 2; x++) {
			for (int z = -1; z <= 1; z++) {
				boolean corner = x != TOWER_NEAR + 1 && z != 0;
				for (int y = -1; y < TOWER_HIGH; y++) {
					BlockState state = y == TOWER_HIGH - 1 ? Blocks.OAK_PLANKS.getDefaultState()
							: corner ? Blocks.OAK_LOG.getDefaultState() : Blocks.COBBLESTONE.getDefaultState();
					world.setBlockState(cell(feet, x, y, z), state);
				}
			}
		}
	}

	/**
	 * The camera on you turning on the one {@code angle} from east of {@code you}: off to one side ({@code side} degrees
	 * round from it), {@code far} out and {@code high} up, looking past you at it.
	 */
	private static void victimShot(Vec3d you, float angle, double side, double far, double high) {
		Vec3d chest = you.add(0.0, 1.1, 0.0);
		Vec3d it = you.add(way(angle).multiply(RING_OFF)).add(0.0, 1.0, 0.0);
		Vec3d at = framed(you.add(way(angle).rotateY((float) Math.toRadians(side)).multiply(far)).add(0.0, high, 0.0), chest);
		fixed(at, chest.lerp(it, 0.45));
	}

	private static void fixed(Vec3d at, Vec3d look) {
		Capture.Pose pose = look(at, look);
		Capture.camera = t -> pose;
	}

	/** You moved to {@code want} this tick (the client moves you; the server follows). */
	private static void go(MinecraftClient client, Vec3d want) {
		if (client.player != null) {
			client.player.setVelocity(want.subtract(client.player.getPos()));
			client.player.fallDistance = 0.0F;
		}
	}

	/** The way {@code angle} degrees round from the way you first face, flat. */
	private static Vec3d way(float angle) {
		double yaw = Math.toRadians(facing + angle);
		return new Vec3d(-Math.sin(yaw), 0.0, Math.cos(yaw));
	}

	/** A zombie standing still on the ground at {@code at}, facing {@code yaw}, with {@code health}. */
	private static void zombie(ServerWorld world, Vec3d at, float yaw, float health) {
		zombie(world, EntityType.ZOMBIE, at, yaw, health);
	}

	private static void zombie(ServerWorld world, EntityType<? extends ZombieEntity> type, Vec3d at, float yaw, float health) {
		ZombieEntity zombie = type.create(world);
		if (zombie == null) {
			return;
		}
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(at.x), MathHelper.floor(at.z));
		zombie.refreshPositionAndAngles(at.x, y, at.z, yaw, 0.0F);
		zombie.setHeadYaw(yaw);
		zombie.setBodyYaw(yaw);
		zombie.setAiDisabled(true);
		zombie.setPersistent();
		zombie.setHealth(health);
		world.spawnEntity(zombie);
		ZOMBIES.add(zombie.getId());
	}

	/**
	 * A little village hut round {@code feet} (you in it, its front wall {@link #HUT_FRONT} east of you): a cobblestone
	 * course, oak plank walls between log corners, a plank roof.
	 */
	private static void hut(ServerWorld world, BlockPos feet) {
		for (int x = -3; x <= HUT_FRONT; x++) {
			for (int z = -2; z <= 2; z++) {
				boolean edge = x == -3 || x == HUT_FRONT || Math.abs(z) == 2;
				boolean corner = (x == -3 || x == HUT_FRONT) && Math.abs(z) == 2;
				world.setBlockState(cell(feet, x, -1, z), Blocks.COBBLESTONE.getDefaultState());
				for (int y = 0; y <= 3; y++) {
					BlockState state = !edge ? Blocks.AIR.getDefaultState()
							: corner ? Blocks.OAK_LOG.getDefaultState()
							: y == 0 ? Blocks.COBBLESTONE.getDefaultState() : Blocks.OAK_PLANKS.getDefaultState();
					world.setBlockState(cell(feet, x, y, z), state);
				}
				world.setBlockState(cell(feet, x, 4, z), Blocks.OAK_PLANKS.getDefaultState());
			}
		}
		// A window each side, a torch inside.
		world.setBlockState(cell(feet, -1, 2, 2), Blocks.GLASS_PANE.getDefaultState());
		world.setBlockState(cell(feet, -1, 2, -2), Blocks.GLASS_PANE.getDefaultState());
		world.setBlockState(cell(feet, -2, 2, 0), Blocks.WALL_TORCH.getDefaultState().with(net.minecraft.block.WallTorchBlock.FACING,
				net.minecraft.util.math.Direction.fromRotation(facing)));
	}

	/** The way to the {@code i}th zombie from you, flat. */
	private static Vec3d around(int i) {
		double yaw = Math.toRadians(FACING + AROUND[i]);
		return new Vec3d(-Math.sin(yaw), 0.0, Math.cos(yaw));
	}

	/** You facing {@code yaw}, all of you (the server takes it from here as you swing). */
	private static void face(MinecraftClient client, float yaw) {
		face(client, yaw, 5.0F);
	}

	/**
	 * Where you're to look (kortev: the head must move like a real person's, especially in your own eyes): it gets there
	 * over a few ticks, not at once ({@link #head}). (Looking level, the crosshair on no block: its outline would be drawn.)
	 */
	private static void face(MinecraftClient client, float yaw, float pitch) {
		wantYaw = yaw;
		wantPitch = pitch;
	}

	/** Where you're to look, and where your head is on its way there (NaN: not started). */
	private static float wantYaw = Float.NaN;
	private static float wantPitch;
	private static float headYaw = Float.NaN;
	private static float headPitch;
	/** How much of the way there the head goes each tick, and the fastest it turns (degrees a tick). */
	private static final float FOLLOW = 0.4F;
	private static final float FASTEST = 28.0F;

	/**
	 * Your head this tick, as a person's moves: easing toward where you're to look and settling (never snapping), with a
	 * little sway as you breathe and your eyes wander; your body is left to follow it as the game turns any player's.
	 */
	private static void head(MinecraftClient client) {
		if (client.player == null || Float.isNaN(wantYaw)) {
			return;
		}
		if (Float.isNaN(headYaw)) {
			headYaw = client.player.getYaw();
			headPitch = client.player.getPitch();
		}
		headYaw += MathHelper.clamp(MathHelper.wrapDegrees(wantYaw - headYaw) * FOLLOW, -FASTEST, FASTEST);
		headPitch += MathHelper.clamp((wantPitch - headPitch) * FOLLOW, -FASTEST, FASTEST);
		float t = ticks;
		float swayYaw = 0.7F * MathHelper.sin(t * 0.061F) + 0.35F * MathHelper.sin(t * 0.173F + 1.3F);
		float swayPitch = 0.45F * MathHelper.sin(t * 0.083F + 0.6F) + 0.2F * MathHelper.sin(t * 0.211F);
		client.player.setYaw(headYaw + swayYaw);
		client.player.setHeadYaw(headYaw + swayYaw);
		client.player.setPitch(MathHelper.clamp(headPitch + swayPitch, -90.0F, 90.0F));
	}

	// Over the picture: the title all along, the question at the end. Big, outlined, inside where a phone's buttons and
	// captions don't cover it.

	private static void lettering(DrawContext context, double t) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		int w = context.getScaledWindowWidth();
		int h = context.getScaledWindowHeight();
		outlined(context, font, TITLE, w / 2.0, h * 0.14, w * 0.78 / font.getWidth(TITLE), 0xFFFF8A1E);
		if (t >= ASKED && "transform".equals(target())) {
			// Slammed in, a little big, settling.
			double k = span(t, ASKED, ASKED + 4);
			double scale = w * 0.84 / font.getWidth(ASK) * (1.0 + 0.45 * (1.0 - ease(k)));
			outlined(context, font, ASK, w / 2.0, h * 0.68, scale, 0xFFFFFFFF);
		}
	}

	/** {@code text} centred on ({@code x}, {@code y}), {@code scale} times the font's size, in {@code color} with a black edge. */
	private static void outlined(DrawContext context, TextRenderer font, String text, double x, double y, double scale, int color) {
		context.getMatrices().push();
		context.getMatrices().translate(x, y, 0.0);
		context.getMatrices().scale((float) scale, (float) scale, 1.0F);
		int left = -font.getWidth(text) / 2;
		int top = -font.fontHeight / 2;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx != 0 || dy != 0) {
					context.drawText(font, text, left + dx, top + dy, 0xFF000000, false);
				}
			}
		}
		context.drawText(font, text, left, top, color, false);
		context.getMatrices().pop();
	}

	// Helpers.

	private static double span(double t, double from, double to) {
		return MathHelper.clamp((t - from) / (to - from), 0.0, 1.0);
	}

	private static double ease(double k) {
		k = MathHelper.clamp(k, 0.0, 1.0);
		return k * k * (3.0 - 2.0 * k);
	}

	private static float turn(float from, float to, double k) {
		return from + MathHelper.wrapDegrees(to - from) * (float) k;
	}

	private static Capture.Pose look(Vec3d at, Vec3d look) {
		Vec3d to = look.subtract(at);
		float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
		return new Capture.Pose(at.x, at.y, at.z, yaw, pitch);
	}

	/** {@code at}, or if a block (a house) stands between it and {@code subject}, pulled in to this side of it. */
	private static Vec3d framed(Vec3d at, Vec3d subject) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			return at;
		}
		HitResult hit = client.world.raycast(new RaycastContext(subject, at, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE,
				client.player));
		if (hit.getType() == HitResult.Type.MISS) {
			return at;
		}
		Vec3d back = at.subtract(subject);
		// (Never closer than this: any nearer is in first person, your own hands drawn.)
		double reach = Math.max(2.6, subject.distanceTo(hit.getPos()) - 0.3);
		return subject.add(back.multiply(reach / Math.max(back.length(), 1.0E-4)));
	}

	private static void shot(MinecraftClient client, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot showcase_{}", name);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, "showcase_" + name + ".png", client.getFramebuffer(), message -> {
		});
	}

	private static ServerPlayerEntity player(IntegratedServer server) {
		return server.getPlayerManager().getPlayerList().get(0);
	}

	// The set.

	/**
	 * On the server: the nearest village, at dusk (the sun just down, too late for zombies to burn), a level open spot in
	 * it; anything but its villagers cleared away (an iron golem would join in); you there with Pochita your heart, full
	 * of blood, out of the form, facing east; the zombies round you, standing still.
	 */
	private static void setup(IntegratedServer server) {
		ServerPlayerEntity player = player(server);
		ServerWorld world = server.getOverworld();
		world.setTimeOfDay(12650);
		world.setWeather(12000, 0, false, false);
		server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		// No rotten flesh picked up into your empty hand.
		server.getGameRules().get(GameRules.DO_MOB_LOOT).set(false, server);
		if ("pochita".equals(target())) {
			pochitaSet(server, player, world);
			return;
		}
		BlockPos village = world.locateStructure(StructureTags.VILLAGE, player.getBlockPos(), 100, false);
		if (village == null) {
			ShootingStar.LOGGER.error("[selftest] showcase: no village found");
			village = player.getBlockPos();
		}
		boolean saws = "saws".equals(target()) || "chain".equals(target()) || "arms".equals(target());
		facing = FACING;
		BlockPos spot = open(world, village, saws ? ShowcaseSelfTest::runway : ShowcaseSelfTest::roomy);
		ShootingStar.LOGGER.info("[selftest] showcase: village at {}, standing at {}", village, spot);
		Vec3d feet = Vec3d.ofBottomCenter(spot);
		for (Entity e : world.getOtherEntities(player, new net.minecraft.util.math.Box(spot).expand(48.0, 24.0, 48.0))) {
			if (!(e instanceof ServerPlayerEntity) && !(e instanceof VillagerEntity)) {
				e.discard();
			}
		}
		Form.end(player);
		player.changeGameMode(GameMode.SURVIVAL);
		player.getInventory().clear();
		player.setHealth(player.getMaxHealth());
		player.getHungerManager().setFoodLevel(20);
		player.setAttached(Heart.MERGED, true);
		Blood.set(player, Blood.MAX);
		player.teleport(world, feet.x, feet.y, feet.z, facing, 5.0F);
		origin = feet;
		ZOMBIES.clear();
		if (saws) {
			// Already Chainsaw Man (done before the filming starts), in the hut; one waiting outside it.
			level(world, spot);
			Form.pullCord(player);
			if ("chain".equals(target())) {
				// Nothing they do hurts you (a hurt flashes you red).
				player.getAbilities().invulnerable = true;
				player.sendAbilitiesUpdate();
				// The tower, the two on it facing you, free to be flung (their AI on, but going nowhere).
				tower(world, spot);
				for (Vec3d top : ON_TOP) {
					zombie(world, local(top.x, 0.0, top.z), facing + 180.0F, 6.0F);
					if (world.getEntityById(ZOMBIES.get(ZOMBIES.size() - 1)) instanceof ZombieEntity z) {
						z.setAiDisabled(false);
						z.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0.0);
					}
				}
			} else if ("arms".equals(target())) {
				// Hurt only by the script: a hurt flashes you red, and enough of it would take the arms off out of turn.
				player.getAbilities().invulnerable = true;
				player.sendAbilitiesUpdate();
				Limbs.whole(player);
				// The two with axes in front of you, either side, raised to strike.
				for (float angle : AXES) {
					VindicatorEntity v = EntityType.VINDICATOR.create(world);
					if (v == null) {
						continue;
					}
					Vec3d at = origin.add(way(angle).multiply(AXE_OFF));
					float yaw = facing + angle + 180.0F;
					v.refreshPositionAndAngles(at.x, origin.y, at.z, yaw, 0.0F);
					v.setHeadYaw(yaw);
					v.setBodyYaw(yaw);
					v.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
					v.setAiDisabled(true);
					v.setPersistent();
					v.setAttacking(true);
					v.setHealth(6.0F);
					world.spawnEntity(v);
					ZOMBIES.add(v.getId());
				}
			} else {
				hut(world, spot);
				zombie(world, local(WAITING.x, 0.0, WAITING.z), facing + 180.0F, 6.0F);
			}
		} else if ("held".equals(target())) {
			// Pochita in your hands, yourself (not your heart yet).
			player.removeAttached(Heart.MERGED);
			player.getAbilities().invulnerable = true;
			player.sendAbilitiesUpdate();
			player.getInventory().selectedSlot = 0;
			player.getInventory().setStack(0, new ItemStack(io.github.kortev.chainsawman.ChainsawMan.POCHITA));
		} else {
			for (int i = 0; i < AROUND.length; i++) {
				// One cut each, whatever the combo's step.
				zombie(world, feet.add(around(i).multiply(OFF)), FACING + AROUND[i] + 180.0F, 6.0F);
			}
		}
		origin = feet;
	}

	/**
	 * The Pochita reel's set: his own fallen tree (PochitaPlace's), built in the woods nearest, a little earlier in the
	 * dusk, him fainted under it; you at the far end of his blood's trail, yourself (no
	 * heart, not in the form), nothing in your hands.
	 */
	private static void pochitaSet(IntegratedServer server, ServerPlayerEntity player, ServerWorld world) {
		world.setTimeOfDay(12200);
		facing = FACING;
		BlockPos spot = io.github.kortev.chainsawman.PochitaPlace.woods(world, player.getBlockPos().add(40, 0, 12),
				net.minecraft.util.math.random.Random.create(7));
		if (spot == null) {
			spot = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, player.getBlockPos().add(40, 0, 12));
		}
		origin = Vec3d.ofBottomCenter(spot);
		// (The tree lies east-west, as FACING: so its own coordinates, along, up, across, are local's.)
		for (Entity e : world.getOtherEntities(player, new net.minecraft.util.math.Box(spot).expand(32.0, 16.0, 32.0))) {
			if (!(e instanceof ServerPlayerEntity)) {
				e.discard();
			}
		}
		io.github.kortev.chainsawman.PochitaPlace.waiting(world, spot, net.minecraft.util.math.Direction.Axis.X);
		// The one it laid there taken away, and one of ours put in his place, so we know him.
		for (Entity e : world.getEntitiesByClass(io.github.kortev.chainsawman.PochitaEntity.class, new net.minecraft.util.math.Box(spot).expand(8.0), p -> true)) {
			e.discard();
		}
		io.github.kortev.chainsawman.PochitaEntity pochita = io.github.kortev.chainsawman.ChainsawMan.POCHITA_ENTITY.create(world);
		if (pochita != null) {
			pochita.refreshPositionAndAngles(origin.x, origin.y, origin.z, facing + 160.0F, 0.0F);
			pochita.initialize(world, world.getLocalDifficulty(spot), net.minecraft.entity.SpawnReason.COMMAND, null);
			pochita.setPersistent();
			world.spawnEntity(pochita);
			pochitaId = pochita.getId();
		}
		// The way in clear (a plant or a block of the fight's mess in the path stops you short of him), and the
		// clearing's plants gone (a camera low in them sees nothing else); his blood's drops left.
		for (int i = -6; i <= 7; i++) {
			for (int j = -9; j <= 6; j++) {
				for (int k = 0; k <= 1; k++) {
					BlockPos p = cell(spot, i, k, j);
					BlockState state = world.getBlockState(p);
					// (His side of the tree, short of the trunk: the fight's broken branches there stand between every camera
					// and you.)
					boolean path = j <= -2 && j >= -9 && i >= -5 && i <= 7;
					if (!state.isOf(Blocks.REDSTONE_WIRE) && !state.isOf(Blocks.VINE) && !state.isAir() && (state.isReplaceable() || path)) {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
					}
				}
			}
		}
		Form.end(player);
		player.removeAttached(Heart.MERGED);
		player.changeGameMode(GameMode.SURVIVAL);
		// (Giving him blood costs you some: a hurt flashes you red.)
		player.getAbilities().invulnerable = true;
		player.sendAbilitiesUpdate();
		player.getInventory().clear();
		player.getInventory().selectedSlot = 0;
		player.setHealth(player.getMaxHealth());
		Vec3d start = local(TRAIL[0][0], 0.0, TRAIL[0][1]);
		Vec3d to = local(KNEEL[0], 0.0, KNEEL[1]);
		player.teleport(world, start.x, origin.y, start.z, look(start, to).yaw(), 10.0F);
		ZOMBIES.clear();
		ShootingStar.LOGGER.info("[selftest] showcase: Pochita's tree at {}, Pochita {}", spot, pochitaId);
	}

	/**
	 * The nearest place to {@code near} to stand in the open with room round you: on the ground (path, grass, dirt,
	 * sand), under the sky, and room enough as {@code room} judges it.
	 */
	private static BlockPos open(ServerWorld world, BlockPos near, java.util.function.BiPredicate<ServerWorld, BlockPos> room) {
		for (int r = 0; r <= 40; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					BlockPos spot = standing(world, near.getX() + dx, near.getZ() + dz);
					if (spot != null && room.test(world, spot)) {
						return spot;
					}
				}
			}
		}
		return world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, near);
	}

	private static BlockPos standing(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
		BlockState ground = world.getBlockState(top.down());
		boolean earth = ground.isOf(Blocks.DIRT_PATH) || ground.isOf(Blocks.GRASS_BLOCK) || ground.isOf(Blocks.DIRT)
				|| ground.isOf(Blocks.SAND) || ground.isOf(Blocks.COARSE_DIRT) || ground.isOf(Blocks.PODZOL);
		return earth && world.isSkyVisible(top) ? top : null;
	}

	/**
	 * For the hut, the run out of it and the cameras round it: open ground within two blocks of level (it's levelled
	 * after) from 3 behind to 13 ahead, 5 either side, whichever way that holds (west first, so the hole is cut toward the
	 * sunset; it sets {@link #facing}).
	 */
	private static boolean runway(ServerWorld world, BlockPos spot) {
		for (float way : new float[] {FACING + 180.0F, FACING, FACING + 90.0F, FACING - 90.0F}) {
			facing = way;
			if (runs(world, spot)) {
				return true;
			}
		}
		facing = FACING;
		return false;
	}

	private static boolean runs(ServerWorld world, BlockPos spot) {
		for (int x = -3; x <= 13; x++) {
			for (int z = -5; z <= 5; z++) {
				BlockPos c = cell(spot, x, 0, z);
				BlockPos p = standing(world, c.getX(), c.getZ());
				if (p == null || Math.abs(p.getY() - spot.getY()) > 2) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * The ground over the run and round it made level with {@code spot}: the earth and plants standing above it taken
	 * away, the dips under it filled, grass on top (paths left paths). Only earth and plants: nothing built is touched.
	 */
	private static void level(ServerWorld world, BlockPos spot) {
		for (int x = -4; x <= 14; x++) {
			for (int z = -7; z <= 7; z++) {
				BlockPos c = cell(spot, x, 0, z);
				for (int y = 0; y <= 4; y++) {
					BlockPos p = c.up(y);
					BlockState state = world.getBlockState(p);
					if (state.isIn(net.minecraft.registry.tag.BlockTags.DIRT) || state.isReplaceable() && state.getFluidState().isEmpty()
							|| state.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS)) {
						world.setBlockState(p, Blocks.AIR.getDefaultState());
					}
				}
				for (int y = -3; y <= -1; y++) {
					BlockPos p = c.up(y);
					BlockState state = world.getBlockState(p);
					if (state.isAir() || state.isReplaceable() && state.getFluidState().isEmpty()) {
						world.setBlockState(p, (y == -1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).getDefaultState());
					}
				}
				BlockState top = world.getBlockState(c.down());
				if (top.isOf(Blocks.DIRT) || top.isOf(Blocks.COARSE_DIRT)) {
					world.setBlockState(c.down(), Blocks.GRASS_BLOCK.getDefaultState());
				}
			}
		}
	}

	/** The block {@code x} ahead of {@code base} the way you face, {@code y} up and {@code z} to the side. */
	private static BlockPos cell(BlockPos base, int x, int y, int z) {
		int fx = (int) Math.round(-Math.sin(Math.toRadians(facing)));
		int fz = (int) Math.round(Math.cos(Math.toRadians(facing)));
		// The side: a quarter turn round from ahead (east ahead, south to the side).
		return base.add(fx * x - fz * z, y, fz * x + fx * z);
	}

	/** The point {@code x} ahead of where you start the way you face, {@code y} up and {@code z} to the side. */
	private static Vec3d local(double x, double y, double z) {
		double fx = -Math.sin(Math.toRadians(facing));
		double fz = Math.cos(Math.toRadians(facing));
		return origin.add(fx * x - fz * z, y, fz * x + fx * z);
	}

	/** The ground within a block of level for 4 blocks round. */
	private static boolean roomy(ServerWorld world, BlockPos spot) {
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				if (dx * dx + dz * dz > 16) {
					continue;
				}
				BlockPos p = standing(world, spot.getX() + dx, spot.getZ() + dz);
				if (p == null || Math.abs(p.getY() - spot.getY()) > 1) {
					return false;
				}
			}
		}
		return true;
	}
}
