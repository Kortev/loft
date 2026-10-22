package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.BeamEntity;
import io.github.kortev.chainsawman.form.Beam;
import io.github.kortev.chainsawman.form.Blood;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.FormTimeline;
import io.github.kortev.chainsawman.form.Grapple;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.form.Limbs;
import io.github.kortev.chainsawman.form.MergeTimeline;
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
 * that kill, to loop. {@code beamlook}: photographs of Beam (the ult) from several sides, riding and leaping, and a bite on a
 * zombie (beamlook_*.png; no reel). Play as kortev (-Pusername=Kortev) to wear their skin. With -Pcapture=true it's recorded with its sounds
 * (Capture) into build/selftest/capture; either way a few frames are saved as pictures (showcase_*.png).
 */
public class ShowcaseSelfTest implements ClientModInitializer {
	private enum Stage {
		WAIT_WORLD, SETUP, SETTLE, RUN, DONE
	}

	private static final String TITLE = "CHAINSAW MAN MOD";

	/** Each reel's hook over its first moments (kortev: a question hook works), and the question it ends on (for comments). */
	private static String[] hook() {
		return switch (target()) {
			// (kortev: a showcase, so each says plainly what the feature is.)
			case "saws" -> new String[] {"Saws that cut", "through walls"};
			case "chain" -> new String[] {"Grab mobs", "with your chain"};
			case "arms" -> new String[] {"Regrow lost arms", "with blood"};
			case "pochita" -> new String[] {"Find and tame", "Pochita"};
			case "merge" -> new String[] {"Die holding Pochita to", "become Chainsaw Man"};
			case "beam" -> new String[] {"Summon Beam", "and ride him"};
			default -> new String[] {"Pull the cord", "to transform"};
		};
	}

	/** The same question on every reel: the series' catchphrase (kortev, 2026-10-22). */
	private static String ask() {
		return "What should I add next?";
	}

	/** Until when the hook stays up, and from when the question's there (reel ticks). */
	private static int hookUntil() {
		return "merge".equals(target()) ? MERGE_AT + 12 : 44;
	}

	private static int askedAt() {
		return switch (target()) {
			case "saws" -> GRIND_TO - 4;
			case "chain" -> THIRD_CUT + 6;
			case "arms" -> ARMS_HERO + 16;
			case "pochita" -> POCHITA_HERO + 10;
			case "merge" -> REVENGE_CUTS[REVENGE_CUTS.length - 1] + 6;
			case "beam" -> beamAsked;
			default -> ASKED;
		};
	}

	/** The way you face at the start: east, so every shot toward you has the sunset behind you. */
	private static final float FACING = -90.0F;
	/** The zombies round you: the angle from the way you face (degrees) and how far off; none in front, for the roar. */
	private static final float[] AROUND = {70.0F, -70.0F, 180.0F};
	private static final double OFF = 2.3;
	/** Your swings at them, in order (the combo: across, back, overhead), once the transformation's done. */
	private static final int[] SLASHES = {78, 92, 106};
	/** Back the way you faced; the question. */
	private static final int TURN = 110;
	private static final int ASKED = 118;
	private static final int END = 180;
	private static final int[] SCREENS = {12, 30, 40, 48, 58, 70, 79, 93, 107, 120, 170};

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
	private static final float[] RING = {-150.0F};
	private static final double RING_OFF = 2.2;
	private static final int GRIND_FROM = LANDED + 16;
	private static final int GRIND_TO = GRIND_FROM + 28;
	private static final int SAW_END = GRIND_TO + 56;
	private static final int[] SAW_SCREENS = {2, 12, 24, 36, 46, 56, 62, 72, 84, 96, 108, 120, 150};
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
	/** From the top: one on the ground below, chained, flung up to you and cut down at the edge. */
	private static final Vec3d BELOW = new Vec3d(5.0, 0.0, 2.2);
	private static final int THIRD_YANK = 88;
	private static final int THIRD_CUT = THIRD_YANK + FLY + 3;
	private static final int CHAIN_END = THIRD_CUT + 64;
	private static final int[] CHAIN_SCREENS = {2, 8, 12, 16, 18, 24, 30, 36, 40, 48, 53, 57, 62, 70, 84, 90, 94, 98, 104, 150};

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
	// (Only the last of it: a long walk in is a slow start.)
	private static final double[][] TRAIL = {{1.4, -6.0}, {0.8, -4.4}, {0.4, -2.9}};
	private static final double[] KNEEL = {0.2, -1.9};
	private static final int WALK_TO = 22;
	private static final int FEED_FROM = 28;
	private static final int WOKEN = FEED_FROM + 40;
	private static final int PICKED_UP = WOKEN + 26;
	private static final int ZOMBIE_IN = PICKED_UP + 4;
	private static final Vec3d COMES_FROM = new Vec3d(4.6, 0.0, -4.4);
	private static final int CUT_IT = PICKED_UP + 24;
	private static final int POCHITA_HERO = CUT_IT + 24;
	private static final int POCHITA_END = POCHITA_HERO + 56;
	private static final int[] POCHITA_SCREENS = {4, 14, 24, 36, 50, 70, 80, 92, 100, 110, 118, 126, 134, 146, 186};
	private static int pochitaId = -1;
	/** The husk's way to you. */
	private static Vec3d huskFrom;
	private static Vec3d huskTo;

	// The merge reel, filmed whole and cut down after (kortev: a question hook, an emotional beat, a payoff). The question
	// over you in the horde; their blows (you hurt); the merge, its own camera all through; the cord pulled the moment you
	// can; the transformation, its own camera; then you cut the horde down.
	private static final float[] HORDE = {60.0F, -60.0F, 160.0F, -160.0F};
	private static final double HORDE_OFF = 2.2;
	private static final int[] BLOWS = {12, 20, 28};
	private static final int MERGE_AT = 34;
	private static final int PULL_AT = MERGE_AT + MergeTimeline.END + 14;
	private static final int REVENGE = PULL_AT + FormTimeline.ROAR_END;
	private static final int[] REVENGE_CUTS = {REVENGE + 12, REVENGE + 24, REVENGE + 36, REVENGE + 48};
	private static final int MERGE_END = REVENGE + 104;

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
				} else if ("beamlook".equals(target())) {
					beamlook(client, server, ticks);
				} else if ("beam".equals(target())) {
					beam(client, server, ticks);
				} else if ("merge".equals(target())) {
					merge(client, server, ticks);
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
			// (Shown, or your hands aren't drawn in your own eyes; the game's own HUD stays off, ReelHudMixin.)
			client.options.hudHidden = false;
			client.options.setPerspective(Perspective.FIRST_PERSON);
			if (capturing()) {
				Capture.start(client, client.runDirectory.toPath().resolve("capture"));
			}
			// Your own transformation, as you see it pulling the cord: the mod's own camera, then your own eyes.
			Capture.camera = null;
			server.execute(() -> Form.pullCord(player(server)));
		}
		if (f < SLASHES[0] - 12) {
			face(client, FACING, 0.0F);
		}
		// Then on them, each in turn, as you'd play it: turned on it, the crosshair on it, the attack key.
		for (int i = 0; i < SLASHES.length; i++) {
			int from = i == 0 ? SLASHES[0] - 12 : SLASHES[i - 1] + 2;
			if (f > from && f <= SLASHES[i] && client.player != null) {
				Vec3d it = origin.add(around(i).multiply(OFF)).add(0.0, 1.0, 0.0);
				Capture.Pose aim = look(client.player.getEyePos(), it);
				face(client, aim.yaw(), aim.pitch());
			}
			if (f == SLASHES[i]) {
				io.github.kortev.chainsawman.client.ClientCombat.attack(client);
			}
		}
		// The last: back the way you faced, both saws held out in front of you, revving (the grind, at nothing).
		if (f > TURN) {
			face(client, FACING + 20.0F, 8.0F);
		}
		if (f == TURN + 2) {
			server.execute(() -> Saw.grind(player(server), true));
		}
		if (f == END - 2) {
			server.execute(() -> Saw.grind(player(server), false));
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
					zombie(server.getOverworld(), at.add(way(RING[i]).multiply(RING_OFF)), facing + RING[i] + 180.0F, 16.0F);
				}
			});
		}
		if (f >= LANDED && landed != null) {
			go(client, landed);
		}
		// The one waiting where you land, ground down: from its other side (closer in, the bar fills the picture).
		if (f == LANDED + 2 && landed != null) {
			victimShot(landed, RING[0], -60.0, 4.6, 1.9);
		}
		if (f > LANDED && f <= GRIND_FROM - 2) {
			face(client, facing + RING[0], 0.0F);
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
			go(client, local(TOWER_NEAR + 1.2, TOWER_HIGH, 0.0));
		}
		if (f >= ON_TOWER && f < THIRD_YANK - 8) {
			face(client, facing + 150.0F, 10.0F);
		}
		// The one below: aimed down at it, the chain, and up it comes in an arc to the edge in front of you.
		Vec3d below = local(BELOW.x, 0.0, BELOW.z);
		Vec3d edge = local(TOWER_NEAR + 0.2, TOWER_HIGH, -0.1);
		if (f >= THIRD_YANK - 8 && f <= THIRD_YANK && client.player != null) {
			Capture.Pose aim = look(client.player.getEyePos(), below.add(0.0, 1.0, 0.0));
			face(client, aim.yaw(), aim.pitch());
		}
		if (f == THIRD_YANK - 1) {
			server.execute(() -> Grapple.release(player(server)));
		}
		if (f == THIRD_YANK) {
			server.execute(() -> Grapple.fire(player(server)));
		}
		if (f > THIRD_YANK && f <= THIRD_YANK + FLY && ZOMBIES.size() > 2) {
			int id = ZOMBIES.get(2);
			double k = (f - THIRD_YANK) / (double) FLY;
			Vec3d at = below.lerp(edge, k).add(0.0, Math.sin(k * Math.PI) * 2.0, 0.0);
			server.execute(() -> {
				if (server.getOverworld().getEntityById(id) instanceof ZombieEntity z && z.isAlive()) {
					z.requestTeleport(at.x, at.y, at.z);
					z.setVelocity(Vec3d.ZERO);
					z.fallDistance = 0.0F;
				}
			});
		}
		if (f > THIRD_YANK && f <= THIRD_CUT && client.player != null) {
			Capture.Pose aim = look(client.player.getEyePos(), edge.add(0.0, 1.0, 0.0));
			face(client, aim.yaw(), MathHelper.clamp(aim.pitch(), -20.0F, 20.0F));
		}
		if (f == THIRD_CUT && ZOMBIES.size() > 2) {
			int id = ZOMBIES.get(2);
			server.execute(() -> Saw.slash(player(server), id));
		}
		if (f > THIRD_CUT + 4) {
			face(client, facing + 150.0F, 10.0F);
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

	// Looking Beam over (kortev, 2026-10-22: they judge his look from photographs): already Chainsaw Man in the open on the
	// village's level ground, Beam called up in front of the camera; side on, three quarters from the front, from behind
	// riding, a husk in front of his open mouth, a bite, a charge through three husks, a leap from the side, one walking, and
	// your own eyes as he walks. Each shot's frame; the screenshots are beamlook_*.

	private static final int BEAM_SUMMON = 2;
	private static final int BEAM_SIDE = 24;
	private static final int BEAM_TAIL = 26;
	private static final int BEAM_LOW = 30;
	private static final int BEAM_FRONT = 34;
	private static final int BEAM_BEHIND = 44;
	private static final int BEAM_HUSK = 54;
	private static final int BEAM_BITE = 70;
	private static final int BEAM_BITE_AGAIN = 90;
	private static final int BEAM_CHARGE = 112;
	private static final int BEAM_TURN = 134;
	private static final int BEAM_LEAP = 162;
	private static final int BEAM_ROUND = 190;
	private static final int BEAM_OWN_EYES = 222;
	private static final int BEAM_WALK = 222 + 16 + 6;
	private static final int BEAM_END = 222 + 50;
	/** How far ahead of Beam the husk stands for the bite (blocks from his middle: just in front of his mouth). */
	private static final double BEAM_HUSK_AHEAD = 4.3;
	private static Vec3d beamFrom = Vec3d.ZERO;
	private static boolean bitShot;
	private static boolean chargeShot;
	private static boolean leapShot;
	private static boolean bitAgainShot;

	/** A husk (a zombie that does not burn: it is day) standing {@code far} ahead of {@code from}, with a lot of health. */
	private static void beamHusk(ServerWorld world, Vec3d from, Vec3d ahead, Vec3d side, double far, double across) {
		Vec3d at = from.add(ahead.multiply(far)).add(side.multiply(across));
		zombie(world, EntityType.HUSK, at, facing + 180.0F, 20.0F);
		if (world.getEntityById(ZOMBIES.get(ZOMBIES.size() - 1)) instanceof ZombieEntity z) {
			z.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(4000.0);
			z.setHealth(4000.0F);
			z.setFireTicks(0);
		}
	}

	private static void beamClear(IntegratedServer server) {
		server.execute(() -> {
			for (int id : ZOMBIES) {
				Entity z = server.getOverworld().getEntityById(id);
				if (z != null) {
					z.discard();
				}
			}
			ZOMBIES.clear();
		});
	}

	private static void beamlook(MinecraftClient client, IntegratedServer server, int f) {
		if (f == 1) {
			client.options.hudHidden = false;
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			face(client, facing, 0.0F);
			bitShot = false;
			chargeShot = false;
			leapShot = false;
			bitAgainShot = false;
		}
		BeamEntity beam = client.world == null ? null : client.world.getEntitiesByClass(BeamEntity.class, new net.minecraft.util.math.Box(origin, origin).expand(80.0), b -> !b.diving())
				.stream().findFirst().orElse(null);
		if (f == BEAM_SUMMON) {
			server.execute(() -> {
				Blood.set(player(server), Blood.MAX);
				Beam.summon(player(server));
			});
		}
		Vec3d ahead = Vec3d.fromPolar(0.0F, facing);
		Vec3d side = new Vec3d(ahead.z, 0.0, -ahead.x);
		if (f == BEAM_SIDE - 1) {
			// From the front and the side, a little above his middle, as the blueprint's side render is framed: the whole of him,
			// the legs, the rider with the reins.
			fixed(rel(origin, ahead, side, 5.4, 3.0, -9.0), rel(origin, ahead, side, 0.3, 2.2, 0.0));
		}
		if (f == BEAM_SIDE) {
			shot(client, "beamlook_side");
		}
		if (f == BEAM_TAIL - 1) {
			// From behind and the side, low: the tail, the back legs, the spikes down his back.
			fixed(rel(origin, ahead, side, -6.5, 2.2, -6.0), rel(origin, ahead, side, -1.2, 1.9, 0.0));
		}
		if (f == BEAM_TAIL) {
			shot(client, "beamlook_tail");
		}
		if (f == BEAM_LOW - 1) {
			// Low in front, looking up at him, as kortev's still of the movie is framed.
			fixed(rel(origin, ahead, side, 6.5, 1.3, -6.0), rel(origin, ahead, side, 0.3, 2.7, 0.0));
		}
		if (f == BEAM_LOW) {
			shot(client, "beamlook_low");
		}
		if (f == BEAM_FRONT - 1) {
			// Three quarters from the front, close, as the blueprint's render and the statue's head are framed: the eyes, the
			// mouth, the teeth, the white jaw.
			fixed(rel(origin, ahead, side, 5.6, 3.0, -4.0), rel(origin, ahead, side, 1.0, 2.4, 0.0));
		}
		if (f == BEAM_FRONT) {
			shot(client, "beamlook_front");
		}
		if (f == BEAM_BEHIND - 1) {
			// From behind, riding: Chainsaw Man on his back.
			fixed(rel(origin, ahead, side, -9.5, 5.4, 2.2), rel(origin, ahead, side, -0.5, 3.8, 0.0));
		}
		if (f == BEAM_BEHIND) {
			shot(client, "beamlook_behind");
		}
		if (f == BEAM_HUSK - 1) {
			// A husk in front of his mouth (the jaw hanging open), from three quarters in front.
			server.execute(() -> beamHusk(server.getOverworld(), origin, ahead, side, BEAM_HUSK_AHEAD, 0.0));
			fixed(rel(origin, ahead, side, 5.8, 2.9, -4.6), rel(origin, ahead, side, 2.6, 2.1, 0.0));
		}
		if (f == BEAM_HUSK + 6) {
			shot(client, "beamlook_mouth");
		}
		if (f == BEAM_BITE - 1) {
			// The bite itself, from his side: his head, the jaw, and what he bites.
			fixed(rel(origin, ahead, side, 3.0, 2.0, -9.0), rel(origin, ahead, side, 3.0, 1.8, 0.0));
		}
		if (f == BEAM_BITE) {
			server.execute(() -> Saw.slash(player(server)));
		}
		if (!bitShot && f > BEAM_BITE && beam != null && beam.sinceBite(0.0F) >= 1.0F && beam.sinceBite(0.0F) < 2.6F) {
			shot(client, "beamlook_bite_side");
			bitShot = true;
		}
		if (f == BEAM_BITE_AGAIN - 2) {
			// Another husk, and the bite from three quarters in front.
			server.execute(() -> {
				beamClear(server);
				beamHusk(server.getOverworld(), origin, ahead, side, BEAM_HUSK_AHEAD, 0.0);
			});
			fixed(rel(origin, ahead, side, 8.6, 2.6, -4.0), rel(origin, ahead, side, 2.4, 2.0, 0.0));
		}
		if (f == BEAM_BITE_AGAIN) {
			server.execute(() -> Saw.slash(player(server)));
		}
		if (!bitAgainShot && f > BEAM_BITE_AGAIN && beam != null && beam.sinceBite(0.0F) >= 1.0F && beam.sinceBite(0.0F) < 2.6F) {
			shot(client, "beamlook_bite_front");
			bitAgainShot = true;
		}
		if (f == BEAM_CHARGE - 12) {
			beamClear(server);
		}
		if (f == BEAM_CHARGE - 2) {
			// Three husks ahead of him, spread; the camera by the middle of his path.
			server.execute(() -> {
				beamHusk(server.getOverworld(), origin, ahead, side, 7.0, 0.0);
				beamHusk(server.getOverworld(), origin, ahead, side, 8.5, 1.2);
				beamHusk(server.getOverworld(), origin, ahead, side, 9.0, -1.0);
			});
			fixed(rel(origin, ahead, side, 4.4, 2.6, -10.0), rel(origin, ahead, side, 4.4, 2.4, 0.0));
		}
		if (f == BEAM_CHARGE) {
			server.execute(() -> Saw.lunge(player(server)));
		}
		if (!chargeShot && f > BEAM_CHARGE && beam != null && beam.sinceCharge(0.0F) >= 4.0F && beam.sinceCharge(0.0F) < 5.5F) {
			shot(client, "beamlook_charge");
			chargeShot = true;
		}
		if (f == BEAM_TURN) {
			beamClear(server);
			// Turned round, to leap back the way he came (the clear ground is only so long).
			face(client, facing + 180.0F, 0.0F);
			Capture.camera = null;
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		}
		if (f == BEAM_LEAP - 2 && beam != null) {
			Vec3d here = beam.getPos();
			Vec3d back = ahead.negate();
			Vec3d across = side.negate();
			// By the middle of the leap, a little in front, level with the top of it.
			fixed(rel(here, back, across, 2.6, 3.4, -12.5), rel(here, back, across, 2.8, 3.0, 0.0));
		}
		if (f == BEAM_LEAP) {
			client.options.jumpKey.setPressed(true);
		}
		if (f == BEAM_LEAP + 2) {
			client.options.jumpKey.setPressed(false);
		}
		if (!leapShot && f > BEAM_LEAP && beam != null && beam.sinceLeap(0.0F) >= 6.5F && beam.sinceLeap(0.0F) < 8.0F) {
			shot(client, "beamlook_leap_side");
			leapShot = true;
		}
		if (f == BEAM_ROUND) {
			// Round again, the way he first faced: for the walk.
			face(client, facing, 0.0F);
			Capture.camera = null;
		}
		if (f == BEAM_OWN_EYES) {
			// Your own eyes, the forward key held: his head and the reins ahead of you.
			Capture.camera = null;
			face(client, facing, 32.0F);
			client.options.setPerspective(Perspective.FIRST_PERSON);
			client.options.forwardKey.setPressed(true);
		}
		if (f == BEAM_OWN_EYES + 14) {
			shot(client, "beamlook_own_eyes");
		}
		if (f == BEAM_OWN_EYES + 15 && beam != null) {
			beamFrom = beam.getPos();
		}
		if (f == BEAM_OWN_EYES + 16 && beam != null) {
			// (The real keys reach the server as a rider's input: he has gone the way you look.)
			ShootingStar.LOGGER.info("[selftest] beamlook: riding with the forward key held, he went {} blocks in 1 tick, rider on him: {}",
					beam.getPos().distanceTo(beamFrom), client.player.getVehicle() == beam);
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			face(client, facing, 4.0F);
			// Walking, from his side: a camera beside the path a little ahead of him, taking him as he passes.
			fixed(rel(beam.getPos(), ahead, side, 4.4, 2.6, -10.0), rel(beam.getPos(), ahead, side, 4.4, 2.4, 0.0));
		}
		if (f == BEAM_WALK) {
			shot(client, "beamlook_walk");
		}
		if (f == BEAM_END - 12) {
			client.options.forwardKey.setPressed(false);
			client.options.sneakKey.setPressed(true);
		}
		if (f >= BEAM_END) {
			client.options.sneakKey.setPressed(false);
			ShootingStar.LOGGER.info("[selftest] beamlook: after sneaking, the player is still on a vehicle: {}; Beam about: {}", client.player.hasVehicle(),
					client.world.getEntitiesByClass(BeamEntity.class, new net.minecraft.util.math.Box(origin, origin).expand(80.0), b -> true).size());
			stage = Stage.DONE;
		}
	}

	// The Beam reel (kortev, 2026-10-22: "He rides a shark through the ground"). Already Chainsaw Man in the open on level
	// ground, a horde ahead; he calls Beam, who bursts up under him; he rides at them (the real forward key), Beam biting a
	// stray and then the front of the clump; he leaps (the real jump key) and crashes down on the rest; he gets off (sneak)
	// and Beam dives; the hero shot in the blood. Beam is big in every shot he is in: the cameras are close, each cut with
	// its own lens, and the one alongside goes along with him at most of his speed.

	/** The horde ahead of him (blocks ahead, blocks across): one out in front, which his first bite takes; three, which his bite two bites later takes (his bites come every eighth tick and reach about two blocks either side of his mouth, so the bite between finds nothing); and four he crashes down on. */
	private static final double[][] BEAM_FIRST = {{7.0, 0.2}};
	private static final double[][] BEAM_REST = {{14.6, 0.5}, {15.3, -1.1}, {15.8, 1.2}, {18.6, 0.2}, {19.5, -1.4}, {20.2, 1.0}, {21.0, -0.4}};
	/** How much health they have: one bite kills. */
	private static final float BEAM_FRAIL = 8.0F;
	/** When he is called (the reel's tick), when the forward key goes down, and how far along he leaps if the bites have not come by then. */
	private static final int BEAM_CALL = 6;
	private static final int BEAM_RIDES = 48;
	private static final double BEAM_LEAP_BY = 11.0;
	/** How long after the leap begins he lands, how long after that the forward key is let go and the sneak key goes down, how long the question is held. */
	private static final int BEAM_LANDS = 15;
	private static final int BEAM_LETS_GO = 8;
	private static final int BEAM_GET_OFF = 26;
	/** The hero shot: the question comes in this long into it, and the reel ends this long after the question. */
	private static final int BEAM_ASKED_AT = 10;
	private static final int BEAM_ASKED_FOR = 50;
	/** The lens (vertical field of view, degrees) the reel is shot through now: 0 is the game's own. */
	private static float beamLens;
	private static int beamAsked = 100000;
	private static int beamReelEnd = 100000;
	/** The reel's own state: when he leapt, when the sneak key went down, the frame the hero shot began, bites seen and when the last was, where the hero camera is. */
	private static int beamLeapAt;
	private static int beamOffAt;
	private static int beamHeroAt;
	private static int beamBites;
	private static int beamLastBite;
	private static Vec3d beamHeroCam = Vec3d.ZERO;

	/** The lens the reel is shot through at the moment (read by the lens mixin), or 0. */
	public static float lens() {
		return active() && stage == Stage.RUN && "beam".equals(target()) ? beamLens : 0.0F;
	}

	private static void beam(MinecraftClient client, IntegratedServer server, int f) {
		Vec3d ahead = Vec3d.fromPolar(0.0F, facing);
		Vec3d side = new Vec3d(ahead.z, 0.0, -ahead.x);
		if (f == 1) {
			client.options.hudHidden = false;
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			beamLeapAt = 0;
			beamOffAt = 0;
			beamHeroAt = 0;
			beamBites = 0;
			beamLastBite = -10;
			beamAsked = 100000;
			beamReelEnd = 100000;
			if (capturing()) {
				Capture.start(client, client.runDirectory.toPath().resolve("capture"));
			}
			// Close, low, three quarters on from in front, at the height of his back: where he will burst up, his head, his
			// teeth and his fin, and you on him.
			beamLens = 66.0F;
			Vec3d centre = rel(origin, ahead, side, 0.3, 0.9, 0.0);
			// Slowly round him, from three quarters on toward side on: his head and teeth, then his back and fin.
			Capture.camera = t -> {
				double k = ease(span(t, 0.0, BEAM_RIDES - 2));
				double angle = Math.toRadians(MathHelper.lerp(k, 35.0, 85.0));
				double radius = MathHelper.lerp(k, 4.5, 4.0);
				Vec3d out = ahead.multiply(Math.cos(angle)).add(side.multiply(-Math.sin(angle))).multiply(radius);
				return look(centre.add(out).add(0.0, MathHelper.lerp(k, -0.2, 0.0), 0.0), centre);
			};
		}
		BeamEntity beam = client.world == null ? null : client.world.getEntitiesByClass(BeamEntity.class, new net.minecraft.util.math.Box(origin, origin).expand(80.0), b -> !b.diving())
				.stream().findFirst().orElse(null);
		if (f < BEAM_RIDES) {
			face(client, facing, 0.0F);
		}
		if (f == BEAM_CALL) {
			// The key's own path (Beam.summon: the blood, the burst, you on his back).
			server.execute(() -> Beam.summon(player(server)));
		}
		if (f == BEAM_RIDES - 2) {
			// Cut to alongside him, low and close, as he goes: his back, his fin and the ground he throws up fill the lower third.
			beamLens = 74.0F;
			alongside(client, ahead, side, -4.4, 0.8, 0.2, 0.8, 0.8);
		}
		if (f == BEAM_RIDES) {
			client.options.forwardKey.setPressed(true);
		}
		if (f > BEAM_RIDES && f < BEAM_RIDES + 90) {
			face(client, facing, 0.0F);
		}
		// His bites, as the clients see them (each one that takes something).
		if (beam != null && beam.sinceBite(0.0F) < 3.0F && f > beamLastBite + 3) {
			beamLastBite = f;
			beamBites++;
			StringBuilder left = new StringBuilder();
			for (int id : ZOMBIES) {
				Entity z = client.world.getEntityById(id);
				if (z != null && z.isAlive()) {
					left.append(String.format(" %.1f", z.getPos().subtract(origin).dotProduct(ahead)));
				}
			}
			ShootingStar.LOGGER.info("[selftest] beam reel: bite {} at f={}, his mouth is {} ahead; still standing at:{}", beamBites, f,
					beam.getPos().subtract(origin).dotProduct(ahead) + Beam.MOUTH, left);
		}
		double along = beam == null ? 0.0 : beam.getPos().subtract(origin).dotProduct(ahead);
		// The leap, once he has bitten twice (or has come far enough for it), seen from the side.
		if (beamLeapAt == 0 && beam != null && f > BEAM_RIDES + 6 && (beamBites >= 2 && f > beamLastBite + 2 || along > BEAM_LEAP_BY)) {
			beamLeapAt = f + 2;
			ShootingStar.LOGGER.info("[selftest] beam reel: leaping at f={}, he is {} ahead, bites {}", beamLeapAt, along, beamBites);
		}
		if (beamLeapAt != 0 && f == beamLeapAt - 1) {
			// Side on, low, so that he arcs up across the sky; the camera stays and turns with him.
			Vec3d from = beam != null ? beam.getPos() : origin;
			beamLens = 72.0F;
			Vec3d at = framed(rel(from, ahead, side, 3.4, 0.4, -6.2), from.add(0.0, 1.0, 0.0));
			Capture.camera = t -> {
				Vec3d now = client.player.getLerpedPos((float) (t - Math.floor(t)));
				return look(at, now.add(0.0, 0.6, 0.0));
			};
		}
		if (beamLeapAt != 0 && f == beamLeapAt) {
			client.options.jumpKey.setPressed(true);
		}
		if (beamLeapAt != 0 && f == beamLeapAt + 2) {
			client.options.jumpKey.setPressed(false);
		}
		if (beamLeapAt != 0 && f == beamLeapAt + BEAM_LANDS + 1) {
			// Close again, alongside him, as he comes down in the clump and rides on.
			beamLens = 74.0F;
			alongside(client, ahead, side, -4.4, 0.8, 0.2, 0.8, 0.5);
		}
		if (beamLeapAt != 0 && f == beamLeapAt + BEAM_LANDS + BEAM_LETS_GO) {
			client.options.forwardKey.setPressed(false);
		}
		if (beamLeapAt != 0 && f == beamLeapAt + BEAM_LANDS + BEAM_GET_OFF) {
			beamOffAt = f;
			client.options.sneakKey.setPressed(true);
		}
		if (beamOffAt != 0 && f == beamOffAt + 3) {
			client.options.sneakKey.setPressed(false);
		}
		// The hero shot (about three seconds): low, three quarters on, standing where he got off, in the blood.
		if (beamOffAt != 0 && f == beamOffAt + 3 && client.player != null) {
			beamHeroAt = f;
			beamAsked = f + BEAM_ASKED_AT;
			beamReelEnd = beamAsked + BEAM_ASKED_FOR;
			Vec3d you = client.player.getPos();
			beamLens = 50.0F;
			beamHeroCam = framed(rel(you, ahead, side, 5.2, 0.5, -3.4), you.add(0.0, 1.0, 0.0));
			fixed(beamHeroCam, you.add(0.0, 1.05, 0.0));
		}
		if (beamHeroAt != 0 && client.player != null) {
			// Turned to the camera, three quarters on.
			face(client, look(client.player.getEyePos(), beamHeroCam).yaw() + 32.0F, 6.0F);
		}
		if (f >= beamReelEnd) {
			client.options.forwardKey.setPressed(false);
			client.options.sneakKey.setPressed(false);
			ShootingStar.LOGGER.info("[selftest] beam reel: the end at f={}", f);
			stage = Stage.DONE;
		}
		if (f > 3000) {
			// (Something went wrong: do not run for ever.)
			stage = Stage.DONE;
		}
		if (f % 10 == 0) {
			ShootingStar.LOGGER.info("[selftest] beam reel f={} he is {} ahead, y {}; you {}", f, along, beam == null ? Double.NaN : beam.getY(),
					client.player == null ? null : client.player.getPos());
		}
	}

	/**
	 * The camera alongside you as you ride: {@code sideBy} to the side of where you were when it began, {@code high} up,
	 * {@code lead} ahead of you, looking at the point {@code lookAhead} ahead of where you are. It goes along with you at
	 * only {@code follow} of your speed (all of it and you would seem to stand still on the empty ground), so you cross its
	 * picture, and is pulled in if a block stands in its way.
	 */
	private static void alongside(MinecraftClient client, Vec3d ahead, Vec3d side, double sideBy, double high, double lead, double lookAhead,
			double follow) {
		Vec3d[] start = new Vec3d[1];
		Capture.camera = t -> {
			Vec3d now = client.player.getLerpedPos((float) (t - Math.floor(t)));
			if (start[0] == null) {
				start[0] = now;
			}
			Vec3d base = start[0].add(now.subtract(start[0]).multiply(follow));
			Vec3d at = base.add(ahead.multiply(lead)).add(side.multiply(sideBy)).add(0.0, high, 0.0);
			Vec3d to = now.add(ahead.multiply(lookAhead)).add(0.0, 0.7, 0.0);
			return look(framed(at, base.add(0.0, 1.0, 0.0)), to);
		};
	}

	/** The point {@code x} ahead of {@code from} the way you face, {@code y} up and {@code z} to the side. */
	private static Vec3d rel(Vec3d from, Vec3d ahead, Vec3d side, double x, double y, double z) {
		return from.add(ahead.multiply(x)).add(side.multiply(z)).add(0.0, y, 0.0);
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
					// (Hands empty, or he goes in the next slot: the moss off his tree can drop and be picked up on the way in.)
					p.getInventory().clear();
					p.getInventory().selectedSlot = 0;
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

	// The merge reel.

	private static void merge(MinecraftClient client, IntegratedServer server, int f) {
		if (f == 1) {
			client.options.hudHidden = false;
			io.github.kortev.chainsawman.client.MergeCinematic.prompt = false;
			if (capturing()) {
				Capture.start(client, client.runDirectory.toPath().resolve("capture"));
			}
			// Close, three quarters on, the horde round you, the sunset behind.
			fixed(framed(local(3.4, 1.9, -2.0), origin.add(0.0, 1.2, 0.0)), origin.add(0.0, 1.2, 0.0));
		}
		if (f < MERGE_AT && client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		}
		if (f < MERGE_AT) {
			face(client, facing + 20.0F, 0.0F);
			// (Their blows would knock you out of the shot.)
			go(client, origin);
		}
		for (int i = 0; i < BLOWS.length; i++) {
			if (f == BLOWS[i] - 3) {
				int id = ZOMBIES.get(i % ZOMBIES.size());
				server.execute(() -> {
					if (server.getOverworld().getEntityById(id) instanceof ZombieEntity z) {
						z.swingHand(Hand.MAIN_HAND);
					}
				});
			}
			if (f == BLOWS[i]) {
				int id = ZOMBIES.get(i % ZOMBIES.size());
				server.execute(() -> {
					ServerPlayerEntity p = player(server);
					if (server.getOverworld().getEntityById(id) instanceof ZombieEntity z) {
						p.damage(p.getDamageSources().mobAttack(z), 3.0F);
					}
				});
			}
		}
		if (f == MERGE_AT) {
			// Death, but not: the mod's own merge, its own camera.
			Capture.camera = null;
			server.execute(() -> io.github.kortev.chainsawman.form.Merge.start(player(server)));
		}
		if (f == PULL_AT) {
			server.execute(() -> Form.pullCord(player(server)));
		}
		// Chainsaw Man: back on the horde, a cut to each as you turn on it.
		for (int i = 0; i < REVENGE_CUTS.length; i++) {
			int from = i == 0 ? REVENGE : REVENGE_CUTS[i - 1] + 2;
			if (f == from) {
				client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				Vec3d chest = origin.add(0.0, 1.1, 0.0);
				Vec3d it = origin.add(way(HORDE[i]).multiply(HORDE_OFF)).add(0.0, 1.0, 0.0);
				Vec3d at = framed(origin.add(way(HORDE[i] + (i % 2 == 0 ? -110.0F : 110.0F)).multiply(4.0)).add(0.0, 1.7, 0.0), chest);
				fixed(at, chest.lerp(it, 0.45));
			}
			if (f > from && f < REVENGE_CUTS[i]) {
				face(client, facing + HORDE[i], 0.0F);
			}
			if (f == REVENGE_CUTS[i]) {
				int id = ZOMBIES.get(i);
				server.execute(() -> Saw.slash(player(server), id));
			}
		}
		if (f >= REVENGE) {
			// Kept where you stood (each slash steps you in after it, out of the shot).
			go(client, origin);
		}
		// The last: low, three quarters on, in their blood.
		int last = REVENGE_CUTS[REVENGE_CUTS.length - 1] + 6;
		if (f == last) {
			fixed(framed(local(3.4, 1.0, -2.2), origin.add(0.0, 1.3, 0.0)), origin.add(0.0, 1.4, 0.0));
		}
		if (f > last) {
			face(client, facing + 15.0F, 6.0F);
		}
		if (f == 6 || f == MERGE_AT + 20 || f == MERGE_AT + MergeTimeline.COMING + 20 || f == MERGE_AT + MergeTimeline.LINES[3] + 20
				|| f == MERGE_AT + MergeTimeline.SINK + 4 || f == MERGE_AT + MergeTimeline.PLUNGE + 4 || f == MERGE_AT + MergeTimeline.BEATS[1]
				|| f == MERGE_AT + MergeTimeline.EYES + 6 || f == MERGE_AT + MergeTimeline.RISE + 12 || f == PULL_AT + 30 || f == PULL_AT + 40
				|| f == REVENGE_CUTS[0] + 3 || f == REVENGE_CUTS[3] + 3 || f == MERGE_END - 4) {
			shot(client, target() + "_" + f);
		}
		if (f >= MERGE_END) {
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
			face(client, facing + 15.0F, 6.0F);
		}
		// Both arms back: both saws held out, revving, shaking (the grind, at nothing).
		if (f == ARMS_HERO + 8) {
			server.execute(() -> Saw.grind(player(server), true));
		}
		if (f == ARMS_END - 2) {
			server.execute(() -> Saw.grind(player(server), false));
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
		if ("beamlook".equals(target())) {
			// (Photographs of Beam, nothing written over them.)
			return;
		}
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		int w = context.getScaledWindowWidth();
		int h = context.getScaledWindowHeight();
		outlined(context, font, TITLE, w / 2.0, h * 0.14, w * 0.78 / font.getWidth(TITLE), 0xFFFF8A1E);
		if (t < hookUntil() && !"held".equals(target())) {
			// The hook, from the first frame: its first line white, its second orange, both the width of the longer.
			String[] hook = hook();
			double scale = w * 0.86 / Math.max(font.getWidth(hook[0]), font.getWidth(hook[1]));
			outlined(context, font, hook[0], w / 2.0, h * 0.62, scale, 0xFFFFFFFF);
			outlined(context, font, hook[1], w / 2.0, h * 0.62 + scale * font.fontHeight * 1.3, scale, 0xFFFF8A1E);
		}
		if (t >= askedAt() && !"held".equals(target())) {
			// The question, slammed in, a little big, settling; over two lines, broken at the space nearest its middle, so
			// it's as big as the hook.
			String ask = ask();
			int cut = -1;
			for (int i = 0; i < ask.length(); i++) {
				if (ask.charAt(i) == ' ' && (cut < 0 || Math.abs(i - ask.length() / 2) < Math.abs(cut - ask.length() / 2))) {
					cut = i;
				}
			}
			String[] lines = cut < 0 ? new String[] {ask} : new String[] {ask.substring(0, cut), ask.substring(cut + 1)};
			double k = span(t, askedAt(), askedAt() + 4);
			int widest = 0;
			for (String line : lines) {
				widest = Math.max(widest, font.getWidth(line));
			}
			double scale = w * 0.84 / widest * (1.0 + 0.45 * (1.0 - ease(k)));
			for (int i = 0; i < lines.length; i++) {
				outlined(context, font, lines[i], w / 2.0, h * 0.62 + i * scale * font.fontHeight * 1.3, scale, 0xFFFFFFFF);
			}
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
		if ("beamlook".equals(target())) {
			// (Broad daylight, to judge his colours by: a husk, not a zombie, takes his bites, as it does not burn.)
			world.setTimeOfDay(6000);
		}
		if ("pochita".equals(target())) {
			pochitaSet(server, player, world);
			return;
		}
		BlockPos village = world.locateStructure(StructureTags.VILLAGE, player.getBlockPos(), 100, false);
		if (village == null) {
			ShootingStar.LOGGER.error("[selftest] showcase: no village found");
			village = player.getBlockPos();
		}
		boolean saws = "saws".equals(target()) || "chain".equals(target()) || "arms".equals(target()) || "beamlook".equals(target())
				|| "beam".equals(target());
		facing = FACING;
		BlockPos spot = open(world, village, "beam".equals(target()) || "beamlook".equals(target()) ? ShowcaseSelfTest::longRunway : saws ? ShowcaseSelfTest::runway : ShowcaseSelfTest::roomy);
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
			if ("beamlook".equals(target()) || "beam".equals(target())) {
				// (The test world keeps what an earlier reel built here: Beam would swim up its tower.)
				builtAway(world, spot);
			}
			if ("beam".equals(target()) || "beamlook".equals(target())) {
				level(world, spot, BEAM_FAR + 10, BEAM_WIDE + 2);
			} else {
				level(world, spot);
			}
			Form.pullCord(player);
			if ("chain".equals(target())) {
				// Nothing they do hurts you (a hurt flashes you red).
				player.getAbilities().invulnerable = true;
				player.sendAbilitiesUpdate();
				// The tower, the two on it facing you, free to be flung (their AI on, but going nowhere).
				tower(world, spot);
				// (And one on the ground below, for the last.)
				for (Vec3d top : new Vec3d[] {ON_TOP[0], ON_TOP[1], BELOW}) {
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
			} else if ("beamlook".equals(target()) || "beam".equals(target())) {
				// Just the open, level ground (Beam is called up by the script, in front of the camera).
				player.getAbilities().invulnerable = true;
				player.sendAbilitiesUpdate();
				if ("beam".equals(target())) {
					// The horde ahead of you, standing, facing you; one bite kills each.
					for (double[][] group : new double[][][] {BEAM_FIRST, BEAM_REST}) {
						for (double[] at : group) {
							zombie(world, local(at[0], 0.0, at[1]), facing + 180.0F + (float) (at[1] * 9.0), BEAM_FRAIL);
						}
					}
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
		} else if ("merge".equals(target())) {
			// Yourself, not merged yet, Pochita carried (not held: it's your death he comes to), the horde round you.
			player.removeAttached(Heart.MERGED);
			player.getInventory().setStack(9, new ItemStack(io.github.kortev.chainsawman.ChainsawMan.POCHITA));
			for (int i = 0; i < HORDE.length; i++) {
				zombie(world, feet.add(way(HORDE[i]).multiply(HORDE_OFF)), FACING + HORDE[i] + 180.0F, 6.0F);
			}
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
		// (Nothing dropped to be picked up on the way in.)
		server.getGameRules().get(GameRules.DO_TILE_DROPS).set(false, server);
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
		return runway(world, spot, 13, 5);
	}

	/** The Beam reel's: a long run, a wide one (the horde, the camera alongside), and the ground round level for it. */
	private static final int BEAM_FAR = 26;
	private static final int BEAM_WIDE = 10;

	private static boolean longRunway(ServerWorld world, BlockPos spot) {
		return runway(world, spot, BEAM_FAR - 2, BEAM_WIDE - 3);
	}

	private static boolean runway(ServerWorld world, BlockPos spot, int far, int wide) {
		for (float way : new float[] {FACING + 180.0F, FACING, FACING + 90.0F, FACING - 90.0F}) {
			facing = way;
			if (runs(world, spot, far, wide)) {
				return true;
			}
		}
		facing = FACING;
		return false;
	}

	private static boolean runs(ServerWorld world, BlockPos spot, int far, int wide) {
		for (int x = -3; x <= far; x++) {
			for (int z = -wide; z <= wide; z++) {
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
		level(world, spot, 14, 7);
	}

	private static void level(ServerWorld world, BlockPos spot, int far, int wide) {
		for (int x = -4; x <= far; x++) {
			for (int z = -wide; z <= wide; z++) {
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

	/**
	 * What the other reels built over the run (the hut, the lookout tower: cobblestone, oak logs and planks, glass, a
	 * torch) taken away again, the ground under it grass.
	 */
	private static void builtAway(ServerWorld world, BlockPos spot) {
		for (int x = -4; x <= 14; x++) {
			for (int z = -7; z <= 7; z++) {
				for (int y = 5; y >= -1; y--) {
					BlockPos p = cell(spot, x, y, z);
					BlockState state = world.getBlockState(p);
					if (state.isOf(Blocks.OAK_PLANKS) || state.isOf(Blocks.OAK_LOG) || state.isOf(Blocks.GLASS_PANE) || state.isOf(Blocks.WALL_TORCH)
							|| state.isOf(Blocks.COBBLESTONE) && y >= 0) {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
					} else if (state.isOf(Blocks.COBBLESTONE)) {
						world.setBlockState(p, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
					}
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
