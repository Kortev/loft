package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.form.Blood;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.FormTimeline;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.form.Saw;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
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
				if ("saws".equals(target())) {
					saws(client, server, ticks);
				} else {
					transform(client, server, ticks);
				}
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
		ZombieEntity zombie = EntityType.ZOMBIE.create(world);
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

	/** (Looking level, the crosshair on no block: its outline would be drawn.) */
	private static void face(MinecraftClient client, float yaw, float pitch) {
		if (client.player != null) {
			client.player.setYaw(yaw);
			client.player.setHeadYaw(yaw);
			client.player.setBodyYaw(yaw);
			client.player.setPitch(pitch);
		}
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
		BlockPos village = world.locateStructure(StructureTags.VILLAGE, player.getBlockPos(), 100, false);
		if (village == null) {
			ShootingStar.LOGGER.error("[selftest] showcase: no village found");
			village = player.getBlockPos();
		}
		boolean saws = "saws".equals(target());
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
			hut(world, spot);
			Form.pullCord(player);
			zombie(world, local(WAITING.x, 0.0, WAITING.z), facing + 180.0F, 6.0F);
		} else {
			for (int i = 0; i < AROUND.length; i++) {
				// One cut each, whatever the combo's step.
				zombie(world, feet.add(around(i).multiply(OFF)), FACING + AROUND[i] + 180.0F, 6.0F);
			}
		}
		origin = feet;
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
