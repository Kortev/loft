package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

/**
 * With -Dshootingstar.selftest=reel1: the first reel, episode 1 recreated in the mod (kortev's storyboard, 2026-10-20):
 * Denji and Pochita at home, the yakuza's job, the trap made with the Zombie Devil, the yakuza turning, Denji cut down
 * and thrown in a dumpster, and the merge. The actors are stand-ins drawn only here (each in their own skin from
 * build/selftest/skins, seeded by gradle's seedReelSkins from tools/skins/reel); Denji in kortev's own onesie.
 * -Ptarget=cast photographs the cast and the Zombie Devil side by side, to look them over.
 */
public class ReelSelfTest implements ClientModInitializer {
	private enum Stage {
		WAIT_WORLD, SETUP, SETTLE, RUN, DONE
	}

	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	/** Where everything is built: the middle of the set's floor. */
	private static Vec3d origin;
	/** The stand-ins, by the name whose skin they wear. */
	private static final Map<String, OtherClientPlayerEntity> ACTORS = new HashMap<>();
	private static int nextId = -6000;
	/** The Zombie Devil: where the base of its neck is (null: not there), the way it faces, how big. */
	private static Vec3d devil;
	private static float devilYaw;
	private static final float DEVIL_SCALE = 1.35F;

	public static boolean active() {
		return "reel1".equals(System.getProperty("shootingstar.selftest"));
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
			ShootingStar.LOGGER.error("[selftest] reel1 timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-reel-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		ClientTickEvents.END_CLIENT_TICK.register(ReelSelfTest::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			if (devil == null || context.consumers() == null) {
				return;
			}
			MatrixStack matrices = context.matrixStack() != null ? context.matrixStack() : new MatrixStack();
			VertexConsumerProvider consumers = context.consumers();
			float t = ticks + context.tickCounter().getTickDelta(false);
			ReelZombieDevil.render(matrices, consumers, devil, context.camera().getPos(), devilYaw, DEVIL_SCALE, t,
					LightmapTextureManager.pack(12, 0));
		});
	}

	private static void tick(MinecraftClient client) {
		TestWindow.keepToItself(client);
		ticks++;
		IntegratedServer server = client.getServer();
		client.options.pauseOnLostFocus = false;
		if (stage != Stage.WAIT_WORLD && stage != null && client.currentScreen != null) {
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
				if (ticks > 60 && origin != null) {
					stage = Stage.RUN;
					ticks = 0;
				}
			}
			case RUN -> {
				if ("cast".equals(target())) {
					cast(client, ticks);
				}
			}
			case DONE -> {
				ShootingStar.LOGGER.info("[selftest] reel1: done");
				stage = null;
				client.scheduleStop();
			}
			default -> {
			}
		}
	}

	/** The cast in a row, the Zombie Devil behind them: photographed from the front, from the side, and close on it. */
	private static void cast(MinecraftClient client, int f) {
		String[] names = {"Denji", "Boss", "Yakuza1", "Yakuza2", "Yakuza3", "Yakuza4", "ZBoss", "ZYakuza1", "ZYakuza2", "ZYakuza3", "ZYakuza4"};
		if (f == 1) {
			client.options.hudHidden = true;
			for (int i = 0; i < names.length; i++) {
				actor(client, names[i], origin.add(-7.5 + i * 1.5, 0.0, 0.0), 180.0F);
			}
			devil = origin.add(0.0, 3.5, 4.5);
			devilYaw = 180.0F;
		}
		if (f == 5) {
			look(client, origin.add(0.0, 2.2, -11.0), origin.add(0.0, 1.6, 2.0));
		}
		if (f == 30) {
			shot(client, "cast_front");
			look(client, origin.add(-9.0, 2.0, -5.0), origin.add(-3.0, 1.2, 1.0));
		}
		if (f == 50) {
			shot(client, "cast_side");
			look(client, origin.add(1.8, 2.6, -1.2), devil.add(0.0, 0.4, 0.0));
		}
		if (f == 70) {
			shot(client, "cast_devil_close");
			look(client, origin.add(-3.5, 1.4, 1.2), devil.add(0.0, -0.4, 0.0));
		}
		if (f == 90) {
			shot(client, "cast_devil_low");
			stage = Stage.DONE;
		}
	}

	/** A stand-in wearing {@code name}'s skin, at {@code at}, facing {@code yaw}. */
	private static OtherClientPlayerEntity actor(MinecraftClient client, String name, Vec3d at, float yaw) {
		OtherClientPlayerEntity actor = new OtherClientPlayerEntity(client.world, new GameProfile(UUID.randomUUID(), name));
		actor.setId(nextId--);
		actor.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0F);
		actor.setHeadYaw(yaw);
		actor.setBodyYaw(yaw);
		client.world.addEntity(actor);
		ACTORS.put(name, actor);
		return actor;
	}

	/** Your own camera (you're a spectator, not seen) at {@code at}, looking at {@code look}. */
	private static void look(MinecraftClient client, Vec3d at, Vec3d look) {
		Vec3d to = look.subtract(at);
		float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
		IntegratedServer server = client.getServer();
		Vec3d feet = at.subtract(0.0, client.player.getStandingEyeHeight(), 0.0);
		server.execute(() -> {
			ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
			p.teleport(p.getServerWorld(), feet.x, feet.y, feet.z, yaw, pitch);
		});
		client.player.refreshPositionAndAngles(feet.x, feet.y, feet.z, yaw, pitch);
	}

	private static void shot(MinecraftClient client, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot reel_{}", name);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, "reel_" + name + ".png", client.getFramebuffer(), message -> {
		});
	}

	/** On the server: a stone floor high in the open air, daylight, the player a spectator (unseen) to be the camera. */
	private static void setup(IntegratedServer server) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = server.getOverworld();
		world.setTimeOfDay(6000);
		world.setWeather(12000, 0, false, false);
		server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		// High in the open air, over the clouds (the same height every run), so the light is the sky's.
		BlockPos ground = player.getBlockPos().withY(220);
		for (BlockPos p : BlockPos.iterate(ground.add(-16, -1, -16), ground.add(16, 8, 16))) {
			world.setBlockState(p, p.getY() == ground.getY() - 1 ? Blocks.SMOOTH_STONE.getDefaultState() : Blocks.AIR.getDefaultState());
		}
		for (Entity e : world.getOtherEntities(player, player.getBoundingBox().expand(64.0))) {
			if (!(e instanceof ServerPlayerEntity)) {
				e.discard();
			}
		}
		player.changeGameMode(GameMode.SPECTATOR);
		Vec3d feet = Vec3d.ofBottomCenter(ground);
		player.teleport(world, feet.x, feet.y + 2.0, feet.z - 10.0, 0.0F, 0.0F);
		origin = feet;
		ShootingStar.LOGGER.info("[selftest] reel1: set at {}", feet);
	}

	static List<OtherClientPlayerEntity> actors() {
		return new ArrayList<>(ACTORS.values());
	}
}
