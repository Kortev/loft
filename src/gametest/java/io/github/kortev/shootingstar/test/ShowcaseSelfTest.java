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
 * over it. {@code saws}: a forest in daylight, a diamond axe against the saws: you chop one log out of a tree, then (a cut) as Chainsaw
 * Man tear through the tree and the front of a log cabin with the attack key held on each block, and end low among the wreckage.
 * {@code beamlook}: photographs of Beam (the ult) from several sides, riding and leaping, and a bite on a
 * zombie (beamlook_*.png; no reel). Play as kortev (-Pusername=Kortev) to wear their skin. With -Pcapture=true it's recorded with its sounds
 * (Capture) into build/selftest/capture; either way a few frames are saved as pictures (showcase_*.png).
 */
public class ShowcaseSelfTest implements ClientModInitializer {
	private enum Stage {
		WAIT_WORLD, SETUP, SETTLE, RUN, DONE
	}

	private static final String TITLE = "CHAINSAW MAN MOD";

	/**
	 * Each reel's hook over its first moments, and the question it ends on (for comments). kortev (2026-10-23, from a friend):
	 * the reels can't all be the same, so each has its own kind of hook (a comparison, a challenge, a POV, the action first
	 * and the caption after it) and its own question; each still says plainly what the feature is.
	 */
	private static String[] hook() {
		return switch (target()) {
			case "saws" -> new String[] {"Diamond axe vs", "Chainsaw Man"};
			case "chain" -> new String[] {"Grab mobs", "with your chain"};
			case "arms" -> new String[] {"Can Chainsaw Man", "survive the Warden?"};
			case "pochita" -> new String[] {"Find and tame", "Pochita"};
			case "merge" -> new String[] {"Die holding Pochita to", "become Chainsaw Man"};
			case "beam" -> new String[] {"POV: you", "summon Beam"};
			default -> new String[] {"Pull the cord", "to transform"};
		};
	}

	private static String ask() {
		return switch (target()) {
			case "saws" -> "Should I release this mod?";
			case "chain" -> "Which devil should I add next?";
			case "beam" -> "Who wins: Beam or the Warden?";
			default -> "What should I add next?";
		};
	}

	/** From when the hook is up (the chain reel shows the grab first and names it after), until when, and from when the question's there (reel ticks). */
	private static int hookFrom() {
		return "chain".equals(target()) ? CHAIN_HOOK_FROM : 0;
	}

	/** (Retuned by the chain reel to just after its first grab lands.) */
	private static final int CHAIN_HOOK_FROM = 20;

	private static int hookUntil() {
		if ("saws".equals(target())) {
			// (Over the axe's half only: it goes at the hard cut.)
			return AXE_CUT;
		}
		return "merge".equals(target()) ? MERGE_AT + 12 : hookFrom() + 44;
	}

	private static int askedAt() {
		return switch (target()) {
			case "saws" -> sawsAsk;
			case "chain" -> CHAIN_ASK;
			case "arms" -> armsAsk;
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
	/** When (the settling tick) the set was seen to be built, and the place you stand at: a long search for it must not eat the time the world needs to be drawn. */
	private static int originSeen = -1;

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
				if (origin != null && originSeen < 0) {
					originSeen = ticks;
				}
				if (ticks > 100 && originSeen >= 0 && ticks - originSeen > 100) {
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
					sawsInWoods(client, server, ticks);
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

	// The saws reel, in a forest in full daylight (kortev, 2026-10-23: a comparison, "Diamond axe vs Chainsaw Man"). First half:
	// you, yourself, chop one log out of a tree with a diamond axe (the attack key held on it, the game's own mining), close, the
	// crack growing, and that's all you get. A hard cut: in the form, the attack key held on one block after another, the saws
	// tear through two tree trunks in a row and straight through the front of a log cabin, the camera low and close, going with
	// you. Then from inside the cabin, you stood in the hole you made, the daylight behind you.

	/** Across, the line you walk the trees and the cabin along (the axe's tree is off it, its trunk hanging in the way). */
	private static final int LINE = 3;
	/** Across, where the axe's tree stands, away from the line (its crown and its hanging trunk out of the saws' shots). */
	private static final int AXE_Z = 9;
	/** Where the trees stand ahead of you (along, across, logs high): the first is the axe's, the next two the saws', the rest scenery. */
	private static final int[][] TREES = {{2, AXE_Z, 4}, {5, LINE, 6}, {12, LINE, 6}, {4, 10, 5}, {15, 11, 5}, {22, 10, 4}};
	/** The cabin's front wall (along), and its depth. */
	private static final int CABIN = 19;
	/** The clearing: an oval, its middle along, its half length and half width. */
	private static final double CLEAR_CX = 11.0;
	private static final double CLEAR_RX = 16.0;
	private static final double CLEAR_RZ = 12.0;
	private static final int CABIN_DEEP = 7;
	/** Where you stand to cut each (along): the two trunks, the wall, and in its hole. */
	private static final double[] STANDS = {3.0, 10.0, 16.9, 19.0};
	/** Where you start, along. */
	private static final double START = 0.5;
	/** The cut to the form; how long each walk on between them takes. */
	private static final int AXE_CUT = 46;
	/** How long the camera lingers on each tree coming down before you walk on (ticks). */
	private static final int[] LINGER = {26, 30, 0};
	private static final int WALK = 12;
	private static final int SAWS_CAP = 600;
	private static final int SAWS_HOLD = 38;
	/** The camera going with you: where it is from you (along, up, across) and what it looks at, for each part of the saws. */
	private static final double[][] SAW_CAMS = {
			// close on the cut at a tree's foot
			{1.0, 0.9, -3.4, 1.4, 1.2, 0.0},
			// wide on it coming down, side on
			{1.6, 2.4, -10.5, 1.6, 4.2, 0.0},
			// behind you and to the side at the wall
			{-1.5, 1.1, -4.6, 2.2, 1.3, 0.4}};
	/** When the question goes up (the shot from inside the cabin), and the reel ends: set when you're in the hole. */
	private static int sawsAsk = Integer.MAX_VALUE;
	private static int sawsEnd = Integer.MAX_VALUE;
	private static final int[] sawsGoneAt = {-1, -1, -1};
	private static List<List<BlockPos>> sawTargets;
	private static int sawsPhase = -1;
	private static int sawsPhaseAt;
	private static int sawsTarget = -1;
	private static int sawsTargetAt;
	/** The camera going with you, eased: where it is and what it looks at, from you. */
	private static Vec3d camEye;
	private static Vec3d camLook;
	private static double[] camWant = SAW_CAMS[0];

	private static void sawsInWoods(MinecraftClient client, IntegratedServer server, int f) {
		BlockPos base = BlockPos.ofFloored(origin);
		if (f == 1) {
			client.options.hudHidden = false;
			if (capturing()) {
				Capture.start(client, client.runDirectory.toPath().resolve("capture"));
			}
			sawTargets = new ArrayList<>();
			for (int tree = 1; tree <= 2; tree++) {
				List<BlockPos> logs = new ArrayList<>();
				// (One cut at the foot: the game's own felling brings the rest of the tree down.)
				logs.add(cell(base, TREES[tree][0], 0, TREES[tree][1]));
				sawTargets.add(logs);
			}
			List<BlockPos> wall = new ArrayList<>();
			for (int y = 0; y <= 1; y++) {
				for (int z = 0; z <= 1; z++) {
					wall.add(cell(base, CABIN, y, LINE + z));
				}
			}
			sawTargets.add(wall);
			sawsPhase = -1;
			java.util.Arrays.fill(sawsGoneAt, -1);
			sawsAsk = Integer.MAX_VALUE;
			sawsEnd = Integer.MAX_VALUE;
			// You at the first tree with the diamond axe, close from the side: the log, the crack growing in it.
			fixed(framed(local(1.4, 1.15, AXE_Z - 3.0), local(1.4, 1.0, AXE_Z)), local(1.4, 1.0, AXE_Z));
		}
		// (The held attack key mines only with the mouse captured, as far as the game knows.)
		TestWindow.mining(client, true);
		if (client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		}
		if (client.player == null || client.world == null) {
			return;
		}
		if (f % 10 == 3) {
			// The litter of a tree's bursting, not left lying (it would be picked up, and bury the shot).
			Vec3d at = origin;
			server.execute(() -> {
				for (Entity e : server.getOverworld().getOtherEntities(player(server), new net.minecraft.util.math.Box(at, at).expand(50.0, 30.0, 50.0),
						e -> e instanceof net.minecraft.entity.ItemEntity item && item.getItemAge() > 25)) {
					e.discard();
				}
			});
		}
		if (f % 4 == 1) {
			// Nothing alive but you in the woods (the animals of the chunks that load in after the set was cleared).
			Vec3d at = origin;
			server.execute(() -> {
				for (Entity e : server.getOverworld().getOtherEntities(player(server), new net.minecraft.util.math.Box(at, at).expand(48.0, 30.0, 48.0),
						e -> e instanceof net.minecraft.entity.LivingEntity)) {
					e.discard();
				}
			});
		}
		Vec3d eye = client.player.getEyePos();
		if (f < AXE_CUT) {
			// The axe: the log at the foot of the tree, the attack key held on it; when it's out, a look up the stump's trunk,
			// then to the camera, as flat as a man can be about it.
			BlockPos log = cell(base, TREES[0][0], 0, TREES[0][1]);
			if (!client.world.getBlockState(log).isAir()) {
				Capture.Pose aim = look(eye, log.toCenterPos());
				face(client, aim.yaw(), aim.pitch());
				client.options.attackKey.setPressed(f >= 6 && aimedAt(aim));
				sawsAxedAt = f;
			} else {
				client.options.attackKey.setPressed(false);
				int since = f - sawsAxedAt;
				if (since < 10) {
					face(client, facing, -24.0F);
				} else {
					Capture.Pose toCamera = look(eye, local(1.4, 1.15, AXE_Z - 3.0));
					face(client, toCamera.yaw(), 6.0F);
				}
			}
			shotAt(client, f);
			return;
		}
		if (f == AXE_CUT) {
			client.options.attackKey.setPressed(false);
			Vec3d at = local(START, 0.0, LINE);
			server.execute(() -> {
				ServerPlayerEntity p = player(server);
				// (The tree's drops are left; the wall's are not.)
				server.getGameRules().get(GameRules.DO_TILE_DROPS).set(false, server);
								p.getInventory().clear();
				// (Off to the line of trees, out of the camera's sight: the axe's trunk hangs where you stood.)
				Vec3d line = at;
				p.teleport(p.getServerWorld(), line.x, line.y, line.z, facing, 0.0F);
				Form.play(p, io.github.kortev.chainsawman.ChainsawSounds.START, 1.0F);
				Form.start(p);
				// (The saws' own speed through wood and stone, and a faster hand: a tree trunk in a second, as he tears through a building.)
				p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.HASTE, 600, 6, true, false,
						false));
			});
		}
		if (f < AXE_CUT + 3) {
			// The hard cut: the same spot from the front, you in the form, standing among the trees.
			if (f == AXE_CUT) {
				camEye = null;
				camWant = SAW_CAMS[0];
			}
			shotAt(client, f);
			Capture.camera = trackingCamera(client);
			face(client, facing, 0.0F);
			return;
		}
		// The saws: one trunk after the next and the wall, the attack key held on each block in turn.
		int phase = 0;
		while (phase < sawTargets.size()) {
			if (!allGone(client, sawTargets.get(phase))) {
				break;
			}
			if (sawsGoneAt[phase] < 0) {
				sawsGoneAt[phase] = f;
			}
			if (f - sawsGoneAt[phase] < LINGER[phase]) {
				break;
			}
			phase++;
		}
		if (phase < 2) {
			// Close on the cut; once it's through, wide on the tree coming down.
			camWant = allGone(client, sawTargets.get(phase)) ? SAW_CAMS[1] : SAW_CAMS[0];
		} else {
			camWant = SAW_CAMS[2];
		}
		if (phase != sawsPhase) {
			sawsPhase = phase;
			sawsPhaseAt = f;
			sawsTarget = -1;
			if (phase == 2) {
				// To the wall: another angle, behind you and to the side, so the hole can be seen going through.
				camEye = null;
			}
		}
		if (phase < sawTargets.size()) {
			double from = phase == 0 ? START : STANDS[phase - 1];
			double along = MathHelper.lerp(ease(span(f, sawsPhaseAt, sawsPhaseAt + WALK)), from, STANDS[phase]);
			// (Held a little down, or you are never on the ground, and the game digs a fifth as fast.)
			go(client, local(along, -0.08, phase == 2 ? LINE + 0.5 : LINE));
			BlockPos next = null;
			for (BlockPos p : sawTargets.get(phase)) {
				if (!client.world.getBlockState(p).isAir()) {
					next = p;
					break;
				}
			}
			if (next == null) {
				return;
			}
			int index = next.hashCode();
			if (index != sawsTarget) {
				sawsTarget = index;
				sawsTargetAt = f;
				ShootingStar.LOGGER.info("[selftest] showcase: saws on {} at {}", next, f);
			}
			Capture.Pose aim = look(eye, next.toCenterPos());
			face(client, aim.yaw(), aim.pitch());
			boolean there = f >= sawsPhaseAt + WALK - 4;
			client.options.attackKey.setPressed(there && aimedAt(aim));
			if (f - sawsTargetAt > 70) {
				// (Stuck on it: the block goes, so the reel goes on; said, so the take can be judged.)
				ShootingStar.LOGGER.warn("[selftest] showcase: the saws did not cut {} in time", next);
				BlockPos stuck = next;
				server.execute(() -> player(server).getServerWorld().breakBlock(stuck, false, player(server)));
			}
			shotAt(client, f);
			return;
		}
		// Through: on into the hole, the saws revving. Then from inside the cabin, looking out at you in it, the daylight behind.
		client.options.attackKey.setPressed(false);
		if (sawsEnd == Integer.MAX_VALUE) {
			sawsPhaseAt = f;
			sawsEnd = f + WALK + 12 + SAWS_HOLD;
			sawsAsk = f + WALK + 12;
			ShootingStar.LOGGER.info("[selftest] showcase: saws done at {}", f);
			// Straight to the shot from inside the cabin (the tracking camera never goes through the logs): you walk into the hole in front of it.
			Vec3d at = local(STANDS[3] + 3.4, 1.25, LINE + 0.5);
			fixed(framed(at, local(STANDS[3], 1.2, LINE + 0.5)), local(STANDS[3] - 0.5, 1.15, LINE + 0.5));
		}
		if (f < sawsPhaseAt + WALK + 8) {
			double along = MathHelper.lerp(ease(span(f, sawsPhaseAt, sawsPhaseAt + WALK)), STANDS[2], STANDS[3]);
			go(client, local(along, -0.08, LINE + 0.5));
			face(client, facing, 0.0F);
		} else {
			go(client, local(STANDS[3], -0.08, LINE + 0.5));
			face(client, facing, -2.0F);
		}
		if (f == sawsPhaseAt + WALK + 8) {
			server.execute(() -> Saw.grind(player(server), true));
		}
		shotAt(client, f);
		if (f >= sawsEnd || f >= SAWS_CAP) {
			server.execute(() -> Saw.grind(player(server), false));
			stage = Stage.DONE;
		}
	}

	/** When the log of the axe's tree went (reel ticks). */
	private static int sawsAxedAt;

	/**
	 * A camera low and close that goes with you: held {@link #camWant} (along, up, across from you; what it looks at) and eased
	 * toward it, so it keeps up with a walk and only its framing drifts; never inside a block.
	 */
	private static java.util.function.DoubleFunction<Capture.Pose> trackingCamera(MinecraftClient client) {
		return time -> {
			if (client.player == null) {
				return new Capture.Pose(0, 0, 0, 0, 0);
			}
			float delta = client.getRenderTickCounter().getTickDelta(true);
			Vec3d you = client.player.getLerpedPos(delta);
			double fx = -Math.sin(Math.toRadians(facing));
			double fz = Math.cos(Math.toRadians(facing));
			Vec3d eyeWant = new Vec3d(fx * camWant[0] - fz * camWant[2], camWant[1], fz * camWant[0] + fx * camWant[2]);
			Vec3d lookWant = new Vec3d(fx * camWant[3] - fz * camWant[5], camWant[4], fz * camWant[3] + fx * camWant[5]);
			boolean fresh = camEye == null || camLook == null;
			camEye = fresh ? eyeWant : camEye.lerp(eyeWant, 0.1);
			camLook = fresh ? lookWant : camLook.lerp(lookWant, 0.1);
			Vec3d at = framed(you.add(camEye), you.add(0.0, 1.0, 0.0));
			return look(at, you.add(camLook));
		};
	}

	/** Whether the crosshair is on {@code aim}, near enough that the attack key held now lands on it. */
	private static boolean aimedAt(Capture.Pose aim) {
		return !Float.isNaN(headYaw) && Math.abs(MathHelper.wrapDegrees(aim.yaw() - headYaw)) < 7.0F && Math.abs(aim.pitch() - headPitch) < 11.0F;
	}

	private static boolean allGone(MinecraftClient client, List<BlockPos> blocks) {
		for (BlockPos p : blocks) {
			if (!client.world.getBlockState(p).isAir()) {
				return false;
			}
		}
		return true;
	}

	/** A still every so often, to judge the take by. */
	private static void shotAt(MinecraftClient client, int f) {
		if (f % 15 == 0) {
			shot(client, target() + "_" + f);
		}
	}

	/**
	 * The saws reel's set: a forest in daylight, a clearing cut in it with two oak trees and a log cabin, you at the first tree
	 * with a diamond axe, yourself (Pochita your heart, not in the form).
	 */
	private static void sawsSet(IntegratedServer server, ServerPlayerEntity player, ServerWorld world) {
		world.setTimeOfDay(6000);
		server.getGameRules().get(GameRules.DO_TILE_DROPS).set(true, server);
		server.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(3, server);
		facing = FACING;
		BlockPos spot = forestClearing(world, BlockPos.ORIGIN);
		Vec3d feet = Vec3d.ofBottomCenter(spot);
		ShootingStar.LOGGER.info("[selftest] showcase: the saws' forest, standing at {}, biome {}", spot, world.getBiome(spot).getIdAsString());
		for (Entity e : world.getOtherEntities(player, new net.minecraft.util.math.Box(spot).expand(40.0, 30.0, 40.0))) {
			if (!(e instanceof ServerPlayerEntity)) {
				e.discard();
			}
		}
		clearing(world, spot);
		for (int[] tree : TREES) {
			oak(world, cell(spot, tree[0], 0, tree[1]), tree[2]);
		}
		cabin(world, cell(spot, 0, 0, LINE));
		Form.end(player);
		player.changeGameMode(GameMode.SURVIVAL);
		player.getAbilities().invulnerable = true;
		player.sendAbilitiesUpdate();
		player.getInventory().clear();
		player.getInventory().setStack(0, new ItemStack(Items.DIAMOND_AXE));
		player.getInventory().selectedSlot = 0;
		player.setHealth(player.getMaxHealth());
		player.getHungerManager().setFoodLevel(20);
		player.setAttached(Heart.MERGED, true);
		Blood.set(player, Blood.MAX);
		player.teleport(world, feet.x + START, feet.y, feet.z + AXE_Z, facing, 25.0F);
		ZOMBIES.clear();
		origin = feet;
	}

	/** The top block of the ground at ({@code x}, {@code z}), under whatever grows on it. */
	private static int groundY(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
		while (y > world.getBottomY()) {
			BlockState s = world.getBlockState(new BlockPos(x, y, z));
			if (s.isAir() || s.isIn(net.minecraft.registry.tag.BlockTags.LEAVES) || s.isIn(net.minecraft.registry.tag.BlockTags.LOGS)
					|| s.isReplaceable() && s.getFluidState().isEmpty() || s.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS)
					|| s.isOf(Blocks.MOSS_CARPET) || s.isOf(Blocks.VINE) || s.isOf(Blocks.BROWN_MUSHROOM) || s.isOf(Blocks.RED_MUSHROOM)) {
				y--;
			} else {
				break;
			}
		}
		return y;
	}

	private static boolean earthy(BlockState ground) {
		return ground.isOf(Blocks.GRASS_BLOCK) || ground.isOf(Blocks.DIRT) || ground.isOf(Blocks.PODZOL) || ground.isOf(Blocks.COARSE_DIRT);
	}

	/**
	 * How thick the forest is round a clearing's place (columns whose top is leaves, in the ring outside it), or -1 if its ground
	 * is wet or too hilly (its coarse grid: every third block).
	 */
	private static int forestScore(ServerWorld world, int x, int z) {
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		for (int dx = -5; dx <= 28; dx += 3) {
			for (int dz = -13; dz <= 13; dz += 3) {
				double e = Math.pow((dx - CLEAR_CX) / CLEAR_RX, 2) + Math.pow(dz / CLEAR_RZ, 2);
				if (e > 1.0) {
					continue;
				}
				int y = groundY(world, x + dx, z + dz);
				if (!earthy(world.getBlockState(new BlockPos(x + dx, y, z + dz))) || !world.getFluidState(new BlockPos(x + dx, y + 1, z + dz)).isEmpty()) {
					return -1;
				}
				low = Math.min(low, y);
				high = Math.max(high, y);
			}
		}
		if (high - low > 4) {
			return -1;
		}
		int trees = 0;
		for (int dx = -14; dx <= 40; dx += 2) {
			for (int dz = -22; dz <= 22; dz += 2) {
				double e = Math.pow((dx - CLEAR_CX) / CLEAR_RX, 2) + Math.pow(dz / CLEAR_RZ, 2);
				if (e <= 1.3) {
					continue;
				}
				world.getChunk((x + dx) >> 4, (z + dz) >> 4);
				BlockPos top = new BlockPos(x + dx, world.getTopY(Heightmap.Type.WORLD_SURFACE, x + dx, z + dz) - 1, z + dz);
				if (world.getBlockState(top).isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
					trees++;
				}
			}
		}
		// (Level ground first: a clearing cut into a hillside has a bank of bare earth for a backdrop.)
		return trees - 40 * (high - low);
	}

	/**
	 * Where to stand: the middle of a forest (its biome a good way round in every direction), nearest {@code from}, with level
	 * dry ground for the clearing and trees thick round it.
	 */
	private static BlockPos forestClearing(ServerWorld world, BlockPos from) {
		List<BlockPos> inside = new ArrayList<>();
		for (int x = -FOREST_SEARCH; x <= FOREST_SEARCH; x += 16) {
			for (int z = -FOREST_SEARCH; z <= FOREST_SEARCH; z += 16) {
				boolean all = wooded(world, x, z);
				for (int k = 0; all && k < 8; k++) {
					double a = k * Math.PI / 4.0;
					all = wooded(world, x + (int) Math.round(Math.cos(a) * 40.0), z + (int) Math.round(Math.sin(a) * 40.0));
				}
				if (all) {
					inside.add(new BlockPos(x, 70, z));
				}
			}
		}
		inside.sort(java.util.Comparator.comparingDouble(p -> p.getSquaredDistance(from)));
		ShootingStar.LOGGER.info("[selftest] showcase: {} places inside a forest", inside.size());
		int bestScore = -1;
		BlockPos best = null;
		int tried = 0;
		for (BlockPos p : inside) {
			if (tried++ >= 120 || bestScore >= 190) {
				break;
			}
			for (int[] off : new int[][] {{0, 0}, {-10, 0}, {10, 0}, {0, -10}, {0, 10}}) {
				int x = p.getX() + off[0];
				int z = p.getZ() + off[1];
				int score = forestScore(world, x, z);
				if (score > bestScore) {
					bestScore = score;
					best = new BlockPos(x, groundY(world, x, z) + 1, z);
				}
			}
		}
		ShootingStar.LOGGER.info("[selftest] showcase: clearing at {}, {} tree columns round it (tried {})", best, bestScore, tried);
		return best != null ? best : world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, from);
	}

	private static final int FOREST_SEARCH = 1600;

	private static boolean wooded(ServerWorld world, int x, int z) {
		var biome = world.getBiome(new BlockPos(x, 70, z));
		return biome.matchesKey(net.minecraft.world.biome.BiomeKeys.FOREST) || biome.matchesKey(net.minecraft.world.biome.BiomeKeys.DARK_FOREST);
	}

	/**
	 * An oval of the forest cleared to the level of {@code spot} and grassed, the sky open over it; round it the ground blends back into
	 * the hillside by a gentle slope (a cut face of bare earth would be the backdrop of every shot).
	 */
	private static void clearing(ServerWorld world, BlockPos spot) {
		int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
		int ground = spot.getY() - 1;
		for (int x = -11; x <= 35; x++) {
			for (int z = -20; z <= 20; z++) {
				double e = Math.pow((x - CLEAR_CX) / CLEAR_RX, 2) + Math.pow(z / CLEAR_RZ, 2);
				if (e > 1.9) {
					continue;
				}
				BlockPos c = cell(spot, x, 0, z);
				int natural = groundY(world, c.getX(), c.getZ());
				double blend = e <= 1.0 ? 0.0 : Math.min(1.0, (e - 1.0) / 0.9);
				int target = (int) Math.round(MathHelper.lerp(blend * blend * (3.0 - 2.0 * blend), ground, natural));
				BlockPos top = new BlockPos(c.getX(), target, c.getZ());
				for (int y = target + 1; y <= Math.max(natural, target) + 30; y++) {
					BlockPos p = new BlockPos(c.getX(), y, c.getZ());
					if (!world.getBlockState(p).isAir()) {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), flags);
					}
				}
				for (int y = target; y >= target - 8; y--) {
					BlockPos p = new BlockPos(c.getX(), y, c.getZ());
					BlockState state = world.getBlockState(p);
					boolean solid = state.isOpaqueFullCube(world, p) && !state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)
							&& !state.isIn(net.minecraft.registry.tag.BlockTags.LOGS);
					if (y == target) {
						world.setBlockState(p, Blocks.GRASS_BLOCK.getDefaultState(), flags);
					} else if (!solid) {
						world.setBlockState(p, Blocks.DIRT.getDefaultState(), flags);
					} else if (y < target - 2) {
						break;
					}
				}
			}
		}
	}

	/** An oak {@code logs} high, its crown of leaves (natural ones: they fall away with the last log). */
	private static void oak(ServerWorld world, BlockPos foot, int logs) {
		int top = logs - 1;
		for (int y = Math.max(2, top - 2); y <= top + 2; y++) {
			int dy = y - top;
			int r = dy == -2 ? 2 : dy <= 0 ? 3 : dy == 1 ? 2 : 1;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
					if (corner && r >= 2) {
						continue;
					}
					// (How far from a log it is, as the game works it out, or it would fall away at the first tick of the cut.)
					int distance = MathHelper.clamp(Math.abs(dx) + Math.abs(dz) + Math.max(0, y - top), 1, 7);
					world.setBlockState(foot.add(dx, y, dz), Blocks.OAK_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.DISTANCE, distance));
				}
			}
		}
		for (int y = 0; y < logs; y++) {
			world.setBlockState(foot.up(y), Blocks.OAK_LOG.getDefaultState());
		}
	}

	/** A small log cabin: its front wall {@link #CABIN} ahead, {@link #CABIN_DEEP} deep and seven wide, three high, spruce logs and planks, a stepped roof. */
	private static void cabin(ServerWorld world, BlockPos spot) {
		net.minecraft.block.BlockState planks = Blocks.SPRUCE_PLANKS.getDefaultState();
		int back = CABIN + CABIN_DEEP - 1;
		for (int x = CABIN; x <= back; x++) {
			for (int z = -3; z <= 3; z++) {
				boolean front = x == CABIN || x == back;
				boolean edge = front || Math.abs(z) == 3;
				boolean corner = front && Math.abs(z) == 3;
				world.setBlockState(cell(spot, x, -1, z), planks);
				for (int y = 0; y <= 2; y++) {
					BlockState s;
					if (!edge) {
						s = Blocks.AIR.getDefaultState();
					} else if (corner) {
						s = Blocks.STRIPPED_SPRUCE_LOG.getDefaultState();
					} else if (front && y == 1) {
						s = Math.abs(z) == 2 && x == back ? Blocks.GLASS_PANE.getDefaultState() : planks;
					} else {
						s = Blocks.SPRUCE_LOG.getDefaultState().with(net.minecraft.block.PillarBlock.AXIS,
								front ? net.minecraft.util.math.Direction.Axis.Z : net.minecraft.util.math.Direction.Axis.X);
					}
					world.setBlockState(cell(spot, x, y, z), s);
				}
				world.setBlockState(cell(spot, x, 3, z), planks);
				if (x >= CABIN + 1 && x <= back - 1) {
					world.setBlockState(cell(spot, x, 4, z), planks);
				}
				if (x >= CABIN + 2 && x <= back - 2) {
					world.setBlockState(cell(spot, x, 5, z), planks);
				}
				if (x == CABIN + 3) {
					world.setBlockState(cell(spot, x, 6, z), planks);
				}
			}
		}
		// A door in the side, a table and a barrel, lanterns.
		BlockPos door = cell(spot, CABIN + 3, 0, -3);
		world.setBlockState(door, Blocks.SPRUCE_DOOR.getDefaultState().with(net.minecraft.block.DoorBlock.FACING, net.minecraft.util.math.Direction.NORTH));
		world.setBlockState(door.up(), Blocks.SPRUCE_DOOR.getDefaultState().with(net.minecraft.block.DoorBlock.FACING, net.minecraft.util.math.Direction.NORTH)
				.with(net.minecraft.block.DoorBlock.HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER));
		world.setBlockState(cell(spot, back - 1, 0, 2), Blocks.CRAFTING_TABLE.getDefaultState());
		world.setBlockState(cell(spot, back - 1, 0, -2), Blocks.BARREL.getDefaultState());
		world.setBlockState(cell(spot, back - 1, 0, 0), Blocks.CHEST.getDefaultState());
		for (int[] at : new int[][] {{CABIN + 2, 1}, {CABIN + 2, -1}, {CABIN + 4, 0}}) {
			world.setBlockState(cell(spot, at[0], 2, at[1]), Blocks.LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true));
		}
	}

	// The chain reel, on a mountain cliff at night (kortev, 2026-10-23: the action first, then "Grab mobs with your chain"). Facing
	// south (so "ahead" is south, and across is west): the rock of the cliff ahead with a ledge six up and a skeleton on its lip,
	// you on a shelf of the mountain, east of you a drop with a pillar standing in it and a creeper on top, a phantom in the sky.
	// The chain's fired at each in turn, it's flung to you and cut down; then low in front of you, the moon over your shoulder.

	private static final int LEDGE = 6;
	/** Where each stands (along, up, across), and what it is. */
	private static final Vec3d SKELETON_AT = new Vec3d(4.4, LEDGE + 1.0, 0.5);
	private static final Vec3d PHANTOM_AT = new Vec3d(-0.5, 5.2, 2.0);
	private static final Vec3d CREEPER_AT = new Vec3d(3.0, 0.0, -7.0);
	/** When the chain's fired at each, how long one takes to fly in, and the arc's height. */
	private static final int[] FIRES = {6, 48, 86};
	private static final int FLY = 8;
	private static final double[] ARC = {1.4, 1.0, 2.2};
	private static final int HERO_FROM = 112;
	private static final int CHAIN_ASK = HERO_FROM + 8;
	private static final int CHAIN_END = HERO_FROM + 56;
	private static final List<Integer> CHAIN_IDS = new ArrayList<>();
	/** Where each was when the chain was fired at it, and where it is flung to. */
	private static final Vec3d[] FLUNG_FROM = new Vec3d[3];
	private static final Vec3d[] FLUNG_TO = new Vec3d[3];

	private static int cutAt(int i) {
		return FIRES[i] + FLY + 3;
	}

	private static void chain(MinecraftClient client, IntegratedServer server, int f) {
		if (f == 1) {
			client.options.hudHidden = false;
			client.options.getGamma().setValue(1.0);
			if (capturing()) {
				Capture.start(client, client.runDirectory.toPath().resolve("capture"));
			}
			// The action at once: you at the foot of the cliff, the skeleton on its ledge over you, the camera beside you looking up.
			fixed(framed(local(1.0, 1.0, 6.4), local(1.9, 3.1, 0.0)), local(1.9, 3.1, 0.2));
		}
		if (client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		}
		if (client.player == null || client.world == null) {
			return;
		}
		Vec3d eye = client.player.getEyePos();
		for (int i = 0; i < FIRES.length; i++) {
			int id = CHAIN_IDS.size() > i ? CHAIN_IDS.get(i) : -1;
			int from = i == 0 ? 1 : cutAt(i - 1) + 2;
			Entity mob = id < 0 ? null : client.world.getEntityById(id);
			// Turned on it (looking at it), the chain at it; then level again for the cut as it comes flying in.
			if (f >= from && f <= FIRES[i]) {
				Vec3d it = mobAt(i);
				Capture.Pose aim = look(eye, it);
				face(client, aim.yaw(), aim.pitch());
			}
			if (f == FIRES[i] - 1) {
				// Whatever the chain still holds let go, or firing it again only lets go.
				server.execute(() -> Grapple.release(player(server)));
			}
			if (f == FIRES[i]) {
				Vec3d you = client.player.getPos();
				Vec3d start = mobAt(i);
				FLUNG_FROM[i] = start;
				Vec3d flat = new Vec3d(start.x - you.x, 0.0, start.z - you.z).normalize();
				FLUNG_TO[i] = you.add(flat.multiply(2.0));
				server.execute(() -> Grapple.fire(player(server)));
			}
			// Flung to you in an arc (left to the game the yank is only a push, and a mob's own AI would fight it).
			if (f > FIRES[i] && f <= FIRES[i] + FLY && FLUNG_FROM[i] != null) {
				double k = (f - FIRES[i]) / (double) FLY;
				Vec3d at = FLUNG_FROM[i].lerp(FLUNG_TO[i], k).add(0.0, Math.sin(k * Math.PI) * ARC[i], 0.0);
				server.execute(() -> {
					if (server.getOverworld().getEntityById(id) instanceof net.minecraft.entity.LivingEntity m && m.isAlive()) {
						m.requestTeleport(at.x, at.y, at.z);
						m.setVelocity(Vec3d.ZERO);
						m.fallDistance = 0.0F;
					}
				});
			}
			// Turned on it as it comes in, for the cut.
			if (f > FIRES[i] + 1 && f <= cutAt(i) && mob != null) {
				Capture.Pose aim = look(eye, mob.getBoundingBox().getCenter());
				face(client, turn(client.player.getYaw(), aim.yaw(), 0.6), MathHelper.clamp(aim.pitch(), -20.0F, 20.0F));
			}
			if (f == cutAt(i)) {
				server.execute(() -> Saw.slash(player(server), id));
			}
		}
		// The cameras: each grab's own, then close on the cut.
		if (f == 13) {
			fixed(framed(local(1.0, 1.2, 4.2), local(1.4, 1.2, 0.0)), local(1.4, 1.2, 0.0));
		}
		if (f == 30) {
			// The sky: low, looking up, you and the phantom over you.
			fixed(framed(local(-0.5, 0.45, 4.8), local(0.0, 3.6, 0.3)), local(-0.4, 3.4, 0.8));
		}
		if (f == FIRES[1] + 4) {
			fixed(framed(local(0.2, 1.1, 4.0), local(0.2, 1.3, 0.0)), local(0.2, 1.4, 0.0));
		}
		if (f == cutAt(1) + 8) {
			// Across the drop: from behind your shoulder, the creeper on its pillar ahead, the moon beyond.
			fixed(framed(local(-2.0, 1.8, 2.4), local(0.0, 1.3, 0.0)), local(0.3, 1.2, -3.2));
		}
		if (f == FIRES[2] + 4) {
			fixed(framed(local(-2.2, 1.2, 2.6), local(0.0, 1.3, -1.0)), local(0.0, 1.4, -1.6));
		}
		if (f == HERO_FROM) {
			// Low in front of you, the moon over your shoulder.
			fixed(framed(local(0.6, 0.5, 4.0), local(0.0, 1.4, 0.0)), local(0.0, 1.9, 0.0));
			server.execute(() -> Saw.grind(player(server), true));
		}
		if (f > cutAt(2) + 4 && f < HERO_FROM) {
			face(client, turn(client.player.getYaw(), look(eye, local(0.0, 1.5, 4.0)).yaw(), 0.2), 4.0F);
		}
		if (f >= HERO_FROM) {
			face(client, look(eye, local(0.6, 1.5, 4.0)).yaw() + 15.0F, -2.0F);
		}
		if (shots < CHAIN_SCREENS.length && f == CHAIN_SCREENS[shots]) {
			shot(client, target() + "_" + f);
			shots++;
		}
		if (f >= CHAIN_END) {
			server.execute(() -> Saw.grind(player(server), false));
			stage = Stage.DONE;
		}
	}

	private static final int[] CHAIN_SCREENS = {2, 8, 12, 16, 22, 34, 46, 52, 58, 64, 80, 90, 96, 102, 108, 120, 150};

	/** The middle of what the chain is fired at, the ith. */
	private static Vec3d mobAt(int i) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null && CHAIN_IDS.size() > i && client.world.getEntityById(CHAIN_IDS.get(i)) != null) {
			return client.world.getEntityById(CHAIN_IDS.get(i)).getBoundingBox().getCenter();
		}
		Vec3d at = i == 0 ? SKELETON_AT : i == 1 ? PHANTOM_AT : CREEPER_AT;
		return local(at.x, at.y + 0.9, at.z);
	}

	/** A mob standing still where it is put, nothing it does hurting you: the chain's to move it. */
	private static void chainMob(ServerWorld world, EntityType<? extends net.minecraft.entity.mob.MobEntity> type, Vec3d at, float yaw, float health) {
		net.minecraft.entity.mob.MobEntity mob = type.create(world);
		if (mob == null) {
			return;
		}
		mob.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0F);
		mob.setHeadYaw(yaw);
		mob.setBodyYaw(yaw);
		mob.setAiDisabled(true);
		mob.setPersistent();
		mob.setHealth(health);
		world.spawnEntity(mob);
		CHAIN_IDS.add(mob.getId());
	}

	private static int rockHash(int x, int z) {
		return ((x * 73856093) ^ (z * 19349663)) & 0x7fffffff;
	}

	private static BlockState rock(int x, int y, int z) {
		return switch (rockHash(x + y * 7, z + y * 13) % 11) {
			case 0, 1 -> Blocks.ANDESITE.getDefaultState();
			case 2 -> Blocks.COBBLESTONE.getDefaultState();
			case 3 -> Blocks.DIORITE.getDefaultState();
			case 4 -> Blocks.CALCITE.getDefaultState();
			default -> Blocks.STONE.getDefaultState();
		};
	}

	private static final java.util.List<net.minecraft.registry.RegistryKey<net.minecraft.world.biome.Biome>> MOUNTAINS = java.util.List.of(
			net.minecraft.world.biome.BiomeKeys.JAGGED_PEAKS, net.minecraft.world.biome.BiomeKeys.FROZEN_PEAKS,
			net.minecraft.world.biome.BiomeKeys.STONY_PEAKS, net.minecraft.world.biome.BiomeKeys.WINDSWEPT_HILLS,
			net.minecraft.world.biome.BiomeKeys.WINDSWEPT_GRAVELLY_HILLS);

	private static boolean mountain(ServerWorld world, int x, int z) {
		var biome = world.getBiome(new BlockPos(x, 120, z));
		for (var key : MOUNTAINS) {
			if (biome.matchesKey(key)) {
				return true;
			}
		}
		return false;
	}

	/** The highest ground among the middles of the mountains nearest the world's origin. */
	private static BlockPos peak(ServerWorld world) {
		if (mountain(world, PEAK_X, PEAK_Z)) {
			// The one found for the test world (the same place every take: its shelf stays where it was built, its ground the same).
			return new BlockPos(PEAK_X, groundY(world, PEAK_X, PEAK_Z) + 1, PEAK_Z);
		}
		List<BlockPos> inside = new ArrayList<>();
		for (int x = -MOUNTAIN_SEARCH; x <= MOUNTAIN_SEARCH; x += 24) {
			for (int z = -MOUNTAIN_SEARCH; z <= MOUNTAIN_SEARCH; z += 24) {
				boolean all = mountain(world, x, z);
				for (int k = 0; all && k < 8; k++) {
					double a = k * Math.PI / 4.0;
					all = mountain(world, x + (int) Math.round(Math.cos(a) * 24.0), z + (int) Math.round(Math.sin(a) * 24.0));
				}
				if (all) {
					inside.add(new BlockPos(x, 120, z));
				}
			}
		}
		inside.sort(java.util.Comparator.comparingDouble(p -> p.getSquaredDistance(BlockPos.ORIGIN)));
		ShootingStar.LOGGER.info("[selftest] showcase: {} places inside the mountains", inside.size());
		BlockPos best = null;
		int bestHeight = -1;
		for (int i = 0; i < Math.min(40, inside.size()); i++) {
			BlockPos p = inside.get(i);

			for (int[] off : new int[][] {{0, 0}, {-12, 0}, {12, 0}, {0, -12}, {0, 12}}) {
				int h = groundY(world, p.getX() + off[0], p.getZ() + off[1]);
				if (h > bestHeight) {
					bestHeight = h;
					best = new BlockPos(p.getX() + off[0], h + 1, p.getZ() + off[1]);
				}
			}
		}
		ShootingStar.LOGGER.info("[selftest] showcase: the peak at {}", best);
		return best != null ? best : world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ORIGIN);
	}

	private static final int MOUNTAIN_SEARCH = 3200;
	/** A shelf in the jagged peaks of the test world (seed "shootingstar"): found by {@link #peak}'s search, kept. */
	private static final int PEAK_X = 1384;
	private static final int PEAK_Z = 2188;

	/**
	 * A shelf of rock levelled to {@code spot}'s ground, a cliff ahead of it (south) with a ledge {@link #LEDGE} up and the peak rising
	 * behind, a drop to the east of it with a pillar standing in the drop at the creeper's place; the sky open over all.
	 */
	private static void cliff(ServerWorld world, BlockPos spot) {
		int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
		int ground = spot.getY() - 1;
		for (int x = -16; x <= 28; x++) {
			for (int z = -48; z <= 18; z++) {
				BlockPos c = cell(spot, x, 0, z);
				world.getChunk(c.getX() >> 4, c.getZ() >> 4);
				boolean drop = z <= -5 - rockHash(x, 3) % 2;
				boolean pillar = x >= 2 && x <= 4 && z >= -8 && z <= -6;
				int top;
				if (drop && !pillar) {
					top = ground - 70;
				} else if (x < 5) {
					top = ground;
				} else if (z >= -3 && z <= 4 && x <= 9) {
					top = ground + LEDGE;
				} else if (x <= 9) {
					top = ground + 2 + rockHash(x, z) % 6;
				} else {
					top = ground + LEDGE + (int) ((x - 9) * 1.1) + rockHash(x, z) % 2;
				}
				if (x < -4 && !drop) {
					top = ground - 1 - (-4 - x) / 3;
				}
				int natural = world.getTopY(Heightmap.Type.WORLD_SURFACE, c.getX(), c.getZ());
				int clearTo = Math.max(natural, Math.max(top, ground) + 6) + 30;
				for (int y = Math.max(top, ground - 70) + 1; y <= clearTo; y++) {
					BlockPos p = new BlockPos(c.getX(), y, c.getZ());
					if (!world.getBlockState(p).isAir()) {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), flags);
					}
				}
				if (top >= ground - 40) {
					// (Down to the hillside it stands on, or a shelf over a slope would hang in the air.)
					for (int y = top; y >= Math.min(top - 8, natural - 1) && y > world.getBottomY(); y--) {
						world.setBlockState(new BlockPos(c.getX(), y, c.getZ()), rock(c.getX(), y, c.getZ()), flags);
					}
				}
				if (pillar) {
					for (int y = ground; y >= ground - 60 && y > world.getBottomY(); y--) {
						world.setBlockState(new BlockPos(c.getX(), y, c.getZ()), rock(c.getX(), y, c.getZ()), flags);
					}
				}
			}
		}
	}

	/** The chain reel's set. */
	private static void chainSet(IntegratedServer server, ServerPlayerEntity player, ServerWorld world) {
		// (Full moon, a little after it's risen over the east.)
		world.setTimeOfDay(8 * 24000L + 14000L);
		server.getGameRules().get(GameRules.DO_TILE_DROPS).set(false, server);
		facing = 0.0F;
		BlockPos spot = peak(world);
		Vec3d feet = Vec3d.ofBottomCenter(spot);
		ShootingStar.LOGGER.info("[selftest] showcase: the chain's cliff, standing at {}, biome {}", spot, world.getBiome(spot).getIdAsString());
		for (Entity e : world.getOtherEntities(player, new net.minecraft.util.math.Box(spot).expand(50.0, 60.0, 50.0))) {
			if (!(e instanceof ServerPlayerEntity)) {
				e.discard();
			}
		}
		cliff(world, spot);
		origin = feet;
		Form.end(player);
		player.changeGameMode(GameMode.SURVIVAL);
		player.getAbilities().invulnerable = true;
		player.sendAbilitiesUpdate();
		player.getInventory().clear();
		player.setHealth(player.getMaxHealth());
		player.getHungerManager().setFoodLevel(20);
		player.setAttached(Heart.MERGED, true);
		Blood.set(player, Blood.MAX);
		Capture.Pose aim = look(feet.add(0.0, 1.6, 0.0), local(SKELETON_AT.x, SKELETON_AT.y + 0.9, SKELETON_AT.z));
		player.teleport(world, feet.x, feet.y, feet.z, aim.yaw(), aim.pitch());
		Form.start(player);
		// (So the rock and the mobs can be seen at night.)
		player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.NIGHT_VISION, 20 * 600, 0, true,
				false, false));
		ZOMBIES.clear();
		CHAIN_IDS.clear();
		chainMob(world, EntityType.SKELETON, local(SKELETON_AT.x, SKELETON_AT.y, SKELETON_AT.z), 180.0F, 6.0F);
		chainMob(world, EntityType.PHANTOM, local(PHANTOM_AT.x, PHANTOM_AT.y, PHANTOM_AT.z), 0.0F, 6.0F);
		chainMob(world, EntityType.CREEPER, local(CREEPER_AT.x, CREEPER_AT.y, CREEPER_AT.z), 90.0F, 6.0F);
		origin = feet;
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
	private static final int BEAM_FLAT = 38;
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
	private static final double BEAM_HUSK_AHEAD = 5.2;
	private static Vec3d beamFrom = Vec3d.ZERO;
	/** The lens (vertical field of view, degrees) the beamlook shot is taken through: narrow from far off for the flat side and front views (so they are near enough orthographic to lay over the drawings), 0 for the game's own. */
	private static float beamLookLens;
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
			// Exactly from his side, 48 blocks off through a narrow lens (22.6 degrees tall: the window is a tall one, so about 11 blocks wide), level with the
			// middle of the drawing (which has no rider: he is made invisible for this and the front shot).
			server.execute(() -> player(server).setInvisible(true));
			beamLookLens = 22.6F;
			fixed(rel(origin, ahead, side, 0.25, 2.4, -48.0), rel(origin, ahead, side, 0.25, 2.4, 0.0));
		}
		if (f == BEAM_SIDE) {
			shot(client, "beamlook_side");
		}
		if (f == BEAM_TAIL - 1) {
			beamLookLens = 0.0F;
			server.execute(() -> player(server).setInvisible(false));
			// From behind and the side, low: the tail, the back legs, the spikes down his back.
			fixed(rel(origin, ahead, side, -11.0, 2.4, -8.0), rel(origin, ahead, side, -2.5, 2.2, 0.0));
		}
		if (f == BEAM_TAIL) {
			shot(client, "beamlook_tail");
		}
		if (f == BEAM_LOW - 1) {
			// Low in front, looking up at him, as kortev's still of the movie is framed.
			fixed(rel(origin, ahead, side, 11.5, 1.3, -8.0), rel(origin, ahead, side, 0.3, 2.7, 0.0));
		}
		if (f == BEAM_LOW) {
			shot(client, "beamlook_low");
		}
		if (f == BEAM_FRONT - 1) {
			// Three quarters from the front, close, as the blueprint's render and the statue's head are framed: the eyes, the
			// mouth, the teeth, the white jaw.
			fixed(rel(origin, ahead, side, 10.0, 3.4, -6.5), rel(origin, ahead, side, 2.0, 2.4, 0.0));
		}
		if (f == BEAM_FRONT) {
			shot(client, "beamlook_front");
		}
		if (f == BEAM_FLAT - 1) {
			// Exactly from the front, far off through the same narrow lens, level with the middle of the front drawing.
			server.execute(() -> player(server).setInvisible(true));
			beamLookLens = 22.6F;
			fixed(rel(origin, ahead, side, 48.0, 2.4, 0.0), rel(origin, ahead, side, 0.0, 2.4, 0.0));
		}
		if (f == BEAM_FLAT) {
			shot(client, "beamlook_front_flat");
			beamLookLens = 0.0F;
			server.execute(() -> player(server).setInvisible(false));
		}
		if (f == BEAM_BEHIND - 1) {
			// From behind, riding: Chainsaw Man on his back.
			fixed(rel(origin, ahead, side, -13.0, 6.0, 3.0), rel(origin, ahead, side, 0.5, 4.0, 0.0));
		}
		if (f == BEAM_BEHIND) {
			shot(client, "beamlook_behind");
		}
		if (f == BEAM_HUSK - 1) {
			// A husk in front of his mouth (the jaw hanging open), from three quarters in front.
			server.execute(() -> beamHusk(server.getOverworld(), origin, ahead, side, BEAM_HUSK_AHEAD, 0.0));
			fixed(rel(origin, ahead, side, 9.8, 2.9, -5.6), rel(origin, ahead, side, 4.2, 2.0, 0.0));
		}
		if (f == BEAM_HUSK + 6) {
			shot(client, "beamlook_mouth");
		}
		if (f == BEAM_BITE - 1) {
			// The bite itself, from his side: his head, the jaw, and what he bites.
			fixed(rel(origin, ahead, side, 3.5, 2.4, -14.0), rel(origin, ahead, side, 3.5, 2.1, 0.0));
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
			fixed(rel(origin, ahead, side, 11.5, 2.8, -5.5), rel(origin, ahead, side, 4.0, 2.0, 0.0));
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
			fixed(rel(origin, ahead, side, 6.0, 3.0, -16.0), rel(origin, ahead, side, 6.0, 2.4, 0.0));
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
			fixed(rel(here, back, across, 2.6, 3.6, -19.0), rel(here, back, across, 2.8, 3.0, 0.0));
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
			fixed(rel(beam.getPos(), ahead, side, 6.0, 3.0, -16.0), rel(beam.getPos(), ahead, side, 6.0, 2.4, 0.0));
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
		if (!active() || stage != Stage.RUN) {
			return 0.0F;
		}
		if ("beam".equals(target())) {
			return beamLens;
		}
		return "beamlook".equals(target()) ? beamLookLens : 0.0F;
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
		// Only him: an earlier take's Pochita, under its own tree nearby, loads in after the set was cleared.
		if (f % 5 == 0) {
			int keep = pochitaId;
			Vec3d at = origin;
			server.execute(() -> {
				for (io.github.kortev.chainsawman.PochitaEntity e : server.getOverworld().getEntitiesByClass(io.github.kortev.chainsawman.PochitaEntity.class,
						new net.minecraft.util.math.Box(at, at).expand(80.0), e -> e.getId() != keep)) {
					e.discard();
				}
			});
		}
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

	// The arms reel, in the Deep Dark against the Warden (kortev, 2026-10-23: "Can Chainsaw Man survive the Warden?"). A hall of
	// sculk and deepslate lit blue by soul lanterns, you in the form facing the Warden three blocks off; its hit takes your
	// right arm off, its next your left; then you grind into it with the head's saw, and each bite's blood grows an arm back (the
	// game's own), till you have both; and a low shot of you facing it, the question over it.

	/** Where the Warden stands (along, up, across). */
	private static final Vec3d WARDEN_AT = new Vec3d(3.0, 0.0, 0.0);
	/** When each blow lands, how long before it its swing starts. */
	private static final int[] WARDEN_BLOWS = {28, 58};
	private static final int WARDEN_WINDUP = 7;
	private static final int ARMS_GRIND = 70;
	/** How many slashes into the Warden, how far apart (ticks): far enough that its hurt flash wears off between. */
	private static final int ARMS_SLASHES = 7;
	private static final int ARMS_EVERY = 16;
	private static final int ARMS_CAP = 330;
	private static final int ARMS_HOLD = 52;
	private static int armsAsk = Integer.MAX_VALUE;
	private static int armsEnd = Integer.MAX_VALUE;
	private static int wardenId = -1;
	private static final int[] ARMS_SCREENS = {4, 10, 16, 22, 26, 30, 36, 44, 54, 60, 66, 72, 80, 90, 100, 110, 120, 130, 140, 150, 160, 180, 200};
	/** The hall, along the way you face: its two ends, half its width, its height; the floor level in the world. */
	private static final BlockPos ARMS_SPOT = new BlockPos(2000, -43, 2000);

	private static void arms(MinecraftClient client, IntegratedServer server, int f) {
		if (f == 1) {
			client.options.hudHidden = false;
			client.options.getGamma().setValue(1.0);
			if (capturing()) {
				Capture.start(client, client.runDirectory.toPath().resolve("capture"));
			}
			armsAsk = Integer.MAX_VALUE;
			armsEnd = Integer.MAX_VALUE;
			// Both of you from the side, close: you and the Warden over you.
			fixed(framed(local(1.7, 1.6, -6.0), local(1.7, 1.6, 0.0)), local(1.7, 1.7, 0.0));
		}
		if (client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
			client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		}
		if (client.player == null || client.world == null) {
			return;
		}
		if (f % 5 == 0) {
			// (Its darkness, should it come: the hall is to be seen.)
			server.execute(() -> player(server).removeStatusEffect(net.minecraft.entity.effect.StatusEffects.DARKNESS));
		}
		// Facing it, the head turned on it.
		if (armsEnd == Integer.MAX_VALUE) {
			face(client, facing, 6.0F);
		}
		for (int i = 0; i < WARDEN_BLOWS.length; i++) {
			if (f == WARDEN_BLOWS[i] - WARDEN_WINDUP) {
				// Its arm comes up.
				int id = wardenId;
				server.execute(() -> {
					if (server.getOverworld().getEntityById(id) instanceof net.minecraft.entity.mob.WardenEntity w) {
						w.getWorld().sendEntityStatus(w, (byte) 4);
					}
				});
			}
			if (f == WARDEN_BLOWS[i]) {
				// The blow: its own damage, its own knock, the game's rule for an arm.
				int id = wardenId;
				server.execute(() -> {
					ServerPlayerEntity p = player(server);
					if (server.getOverworld().getEntityById(id) instanceof net.minecraft.entity.mob.WardenEntity w) {
						p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.HOSTILE, 5.0F, 1.0F);
						p.damage(w.getDamageSources().mobAttack(w), (float) w.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE));
						Vec3d push = p.getPos().subtract(w.getPos()).multiply(1.0, 0.0, 1.0).normalize();
						p.takeKnockback(1.2, -push.x, -push.z);
						p.velocityModified = true;
					}
				});
			}
			// Staggered by it, then back at it.
			if (f > WARDEN_BLOWS[i] + 6 && f <= WARDEN_BLOWS[i] + 20) {
				Vec3d now = client.player.getPos();
				Vec3d want = local(0.0, -0.08, 0.0);
				go(client, now.lerp(want, 0.22));
			}
		}
		// The cameras: the arm's side for each blow, then back to both.
		if (f == WARDEN_BLOWS[0] - WARDEN_WINDUP - 2) {
			fixed(framed(local(1.0, 1.5, 5.4), local(1.7, 1.5, 0.0)), local(1.7, 1.6, 0.0));
		}
		if (f == WARDEN_BLOWS[0] + 14) {
			fixed(framed(local(1.7, 1.6, -6.0), local(1.7, 1.6, 0.0)), local(1.7, 1.7, 0.0));
		}
		if (f == WARDEN_BLOWS[1] - WARDEN_WINDUP - 2) {
			fixed(framed(local(1.0, 1.5, -5.4), local(1.7, 1.5, 0.0)), local(1.7, 1.6, 0.0));
		}
		if (f == WARDEN_BLOWS[1] + 14) {
			// Armless: close and low on you from the Warden's side, the stumps spurting.
			fixed(framed(local(2.4, 1.0, 4.8), local(0.0, 1.4, 0.0)), local(0.8, 1.5, 0.0));
		}
		// The head's saw into it, held: bite by bite the blood grows the arms back.
		// (Not a long grind: that hurts it every few ticks and it never stops flashing red. A slash at a time, the Warden's own look coming
		// back between; each hit's blood counts toward the arms, the game's own rule.)
		for (int k = 0; k < ARMS_SLASHES; k++) {
			if (f == ARMS_GRIND + k * ARMS_EVERY) {
				server.execute(() -> Saw.slash(player(server)));
			}
		}
		if (f == ARMS_GRIND + 6) {
			fixed(framed(local(1.7, 1.2, -5.0), local(1.7, 1.6, 0.0)), local(1.7, 1.6, 0.0));
		}
		boolean whole = Limbs.has(client.player, Limbs.RIGHT) && Limbs.has(client.player, Limbs.LEFT);
		if (f >= ARMS_GRIND + (ARMS_SLASHES - 1) * ARMS_EVERY + 22 && armsEnd == Integer.MAX_VALUE) {
			armsAsk = f + 18;
			armsEnd = f + 18 + ARMS_HOLD;
			ShootingStar.LOGGER.info("[selftest] showcase: slashes done at {}, both arms back: {}", f, whole);
		}
		if (armsEnd != Integer.MAX_VALUE) {
			// Low behind your shoulder, facing it: both saws out and revving, the Warden over you.
			if (f == armsAsk - 16) {
				fixed(framed(local(-3.0, 0.55, -3.2), local(0.0, 1.4, 0.0)), local(2.0, 1.7, 0.2));
				face(client, facing, 4.0F);
			}
			face(client, facing, 4.0F);
		}
		if (shots < ARMS_SCREENS.length && f == ARMS_SCREENS[shots]) {
			shot(client, target() + "_" + f);
			shots++;
		}
		if (f >= armsEnd || f >= ARMS_CAP) {
			server.execute(() -> Saw.grind(player(server), false));
			stage = Stage.DONE;
		}
	}

	/** The hall: sculk floor, deepslate walls and ceiling (a shell, so no water or lava can get in), soul lanterns on low posts, catalysts and sensors. */
	private static void hall(ServerWorld world, BlockPos spot) {
		int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
		for (int x = -10; x <= 24; x++) {
			for (int z = -12; z <= 12; z++) {
				BlockPos column = cell(spot, x, 0, z);
				world.getChunk(column.getX() >> 4, column.getZ() >> 4);
				for (int y = -2; y <= 16; y++) {
					boolean inside = x >= -9 && x <= 23 && Math.abs(z) <= 11 && y >= 0 && y <= 15;
					BlockState state;
					if (inside) {
						state = Blocks.AIR.getDefaultState();
					} else if (y == -1 && x >= -9 && x <= 23 && Math.abs(z) <= 11) {
						state = rockHash(x, z) % 9 == 0 ? Blocks.DEEPSLATE_TILES.getDefaultState() : Blocks.SCULK.getDefaultState();
					} else {
						state = switch (rockHash(x + y, z) % 6) {
							case 0, 1 -> Blocks.SCULK.getDefaultState();
							case 2 -> Blocks.DEEPSLATE.getDefaultState();
							case 3 -> Blocks.DEEPSLATE_TILES.getDefaultState();
							case 4 -> Blocks.COBBLED_DEEPSLATE.getDefaultState();
							default -> Blocks.POLISHED_DEEPSLATE.getDefaultState();
						};
					}
					world.setBlockState(cell(spot, x, y, z), state, flags);
				}
			}
		}
		// Low posts with a soul lantern on each, round the floor; sculk catalysts and sensors among the sculk; lanterns hanging overhead.
		for (int[] at : new int[][] {{-4, 6}, {-4, -6}, {4, 7}, {4, -7}, {10, 6}, {10, -6}, {16, 7}, {16, -7}, {21, 5}, {21, -5}}) {
			world.setBlockState(cell(spot, at[0], 0, at[1]), Blocks.DEEPSLATE_BRICK_WALL.getDefaultState(), flags);
			world.setBlockState(cell(spot, at[0], 1, at[1]), Blocks.DEEPSLATE_BRICK_WALL.getDefaultState(), flags);
			world.setBlockState(cell(spot, at[0], 2, at[1]), Blocks.SOUL_LANTERN.getDefaultState(), flags);
		}
		for (int[] at : new int[][] {{7, 9}, {13, -9}, {19, 9}, {-6, -9}}) {
			world.setBlockState(cell(spot, at[0], 0, at[1]), Blocks.SCULK_CATALYST.getDefaultState(), flags);
		}
		for (int[] at : new int[][] {{-2, 4}, {8, -4}, {12, 4}, {18, -3}, {1, -8}}) {
			world.setBlockState(cell(spot, at[0], 0, at[1]), Blocks.SCULK_SENSOR.getDefaultState(), flags);
		}
		for (int[] at : new int[][] {{3, 0}, {9, 0}, {15, 0}}) {
			world.setBlockState(cell(spot, at[0], 14, at[1]), Blocks.SOUL_LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true), flags);
		}
	}

	/** The arms reel's set: the hall, you in the form facing the Warden. */
	private static void armsSet(IntegratedServer server, ServerPlayerEntity player, ServerWorld world) {
		world.setTimeOfDay(6000);
		server.getGameRules().get(GameRules.DO_TILE_DROPS).set(false, server);
		// (Easy: a blow of the Warden's takes one arm, not both.)
		server.setDifficulty(net.minecraft.world.Difficulty.EASY, true);
		facing = FACING;
		BlockPos spot = ARMS_SPOT;
		Vec3d feet = Vec3d.ofBottomCenter(spot);
		for (Entity e : world.getOtherEntities(player, new net.minecraft.util.math.Box(spot).expand(40.0, 30.0, 40.0))) {
			if (!(e instanceof ServerPlayerEntity)) {
				e.discard();
			}
		}
		origin = feet;
		hall(world, spot);
		Form.end(player);
		player.changeGameMode(GameMode.SURVIVAL);
		// (Hurt only by the script, and able to take a blow of the Warden's.)
		player.getAbilities().invulnerable = false;
		player.sendAbilitiesUpdate();
		var health = player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(400.0);
		}
		player.getInventory().clear();
		player.setHealth(player.getMaxHealth());
		player.getHungerManager().setFoodLevel(20);
		player.setAttached(Heart.MERGED, true);
		Blood.set(player, Blood.MAX);
		player.teleport(world, feet.x, feet.y, feet.z, facing, 6.0F);
		Form.start(player);
		Limbs.whole(player);
		// (So the hall can be seen.)
		player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.NIGHT_VISION, 20 * 600, 0, true,
				false, false));
		ZOMBIES.clear();
		net.minecraft.entity.mob.WardenEntity warden = EntityType.WARDEN.create(world);
		if (warden != null) {
			Vec3d at = local(WARDEN_AT.x, 0.0, WARDEN_AT.z);
			warden.refreshPositionAndAngles(at.x, at.y, at.z, facing + 180.0F, 0.0F);
			warden.setHeadYaw(facing + 180.0F);
			warden.setBodyYaw(facing + 180.0F);
			warden.setAiDisabled(true);
			warden.setPersistent();
			// (A little harder than the game's, so the blow, once the form's armour has taken its share, still takes an arm.)
			warden.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(34.0);
			world.spawnEntity(warden);
			wardenId = warden.getId();
		}
		origin = feet;
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
		if (t >= hookFrom() && t < hookUntil() && !"held".equals(target())) {
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
		if ("saws".equals(target())) {
			sawsSet(server, player, world);
			return;
		}
		if ("chain".equals(target())) {
			chainSet(server, player, world);
			return;
		}
		if ("arms".equals(target())) {
			armsSet(server, player, world);
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
			if ("beamlook".equals(target()) || "beam".equals(target())) {
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
