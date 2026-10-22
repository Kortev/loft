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
 * over it. Play as kortev (-Pusername=Kortev) to wear their skin. With -Pcapture=true it's recorded with its sounds
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
				transform(client, server, ticks);
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

	/** The way to the {@code i}th zombie from you, flat. */
	private static Vec3d around(int i) {
		double yaw = Math.toRadians(FACING + AROUND[i]);
		return new Vec3d(-Math.sin(yaw), 0.0, Math.cos(yaw));
	}

	/** You facing {@code yaw}, all of you (the server takes it from here as you swing). */
	private static void face(MinecraftClient client, float yaw) {
		if (client.player != null) {
			client.player.setYaw(yaw);
			client.player.setHeadYaw(yaw);
			client.player.setBodyYaw(yaw);
			client.player.setPitch(5.0F);
		}
	}

	// Over the picture: the title all along, the question at the end. Big, outlined, inside where a phone's buttons and
	// captions don't cover it.

	private static void lettering(DrawContext context, double t) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		int w = context.getScaledWindowWidth();
		int h = context.getScaledWindowHeight();
		outlined(context, font, TITLE, w / 2.0, h * 0.14, w * 0.78 / font.getWidth(TITLE), 0xFFFF8A1E);
		if (t >= ASKED) {
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
		double reach = Math.max(1.2, subject.distanceTo(hit.getPos()) - 0.3);
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
		BlockPos spot = open(world, village);
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
		player.teleport(world, feet.x, feet.y, feet.z, FACING, 5.0F);
		ZOMBIES.clear();
		for (int i = 0; i < AROUND.length; i++) {
			ZombieEntity zombie = EntityType.ZOMBIE.create(world);
			if (zombie == null) {
				continue;
			}
			Vec3d at = feet.add(around(i).multiply(OFF));
			int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(at.x), MathHelper.floor(at.z));
			float toYou = FACING + AROUND[i] + 180.0F;
			zombie.refreshPositionAndAngles(at.x, y, at.z, toYou, 0.0F);
			zombie.setHeadYaw(toYou);
			zombie.setBodyYaw(toYou);
			zombie.setAiDisabled(true);
			zombie.setPersistent();
			// One cut each, whatever the combo's step.
			zombie.setHealth(6.0F);
			world.spawnEntity(zombie);
			ZOMBIES.add(zombie.getId());
		}
		origin = feet;
	}

	/**
	 * The nearest place to {@code near} to stand in the open with room round you: on the ground (path, grass, dirt,
	 * sand), under the sky, the ground within a block of level for 4 blocks round.
	 */
	private static BlockPos open(ServerWorld world, BlockPos near) {
		for (int r = 0; r <= 40; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					BlockPos spot = standing(world, near.getX() + dx, near.getZ() + dz);
					if (spot != null && roomy(world, spot)) {
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
