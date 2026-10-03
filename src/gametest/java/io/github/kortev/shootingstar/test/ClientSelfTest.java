package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.client.ClientStrike;
import io.github.kortev.shootingstar.client.ClientStrikes;
import io.github.kortev.shootingstar.registry.ModItems;
import io.github.kortev.shootingstar.strike.StrikeManager;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import io.github.kortev.shootingstar.strike.Targeting;
import java.util.ArrayDeque;
import java.util.Deque;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

/**
 * With -Dshootingstar.selftest=true: joins the quick-play world, fires the uplink at a point just
 * outside danger-close range and saves a screenshot at every phase of the strike, then quits.
 */
public class ClientSelfTest implements ClientModInitializer {
	private enum Stage { WAIT_WORLD, SETUP, SETTLE, FIRE, WATCH, AFTER, DONE, FINISHED }

	private record Capture(int age, String name) {
	}

	private static final Deque<Capture> CAPTURES = new ArrayDeque<>();
	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static BlockPos target;
	private static boolean fallbackFired;

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("shootingstar.selftest")) {
			return;
		}
		int[] ages = {6, 20, 38, 53, 62, 74, 82, 87, 90, 95, 100, 108, 120, 134, 143, 150, 160, 165, 180, 196, 210, 224, 236,
				245, 254, 262, 267, 272, 282, 296, 304, 310, 314, 324, 336, 342, 346, 352, 364, 400, 436, 470, 520};
		for (int age : ages) {
			CAPTURES.add(new Capture(age, String.format("%02d_age%03d_%s.png", CAPTURES.size() + 1, age, phase(age))));
		}
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(15 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[selftest] timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-selftest-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		ClientTickEvents.END_CLIENT_TICK.register(ClientSelfTest::tick);
	}

	private static String phase(int age) {
		if (age < StrikeTimeline.RISE) return "lock";
		if (age < StrikeTimeline.ORBIT) return "rise";
		if (age < StrikeTimeline.RELAY) return "orbit";
		if (age < StrikeTimeline.WAKE) return "relay";
		if (age < StrikeTimeline.LOADING) return "wake";
		if (age < StrikeTimeline.LAPS) return "loading";
		if (age < StrikeTimeline.DEBRIS) return "laps";
		if (age < StrikeTimeline.TERMINAL) return "debris";
		if (age < StrikeTimeline.INBOUND) return "terminal";
		if (age < StrikeTimeline.IMPACT) return "inbound";
		if (age < StrikeTimeline.IMPACT_FRAME_END) return "impact";
		if (age < StrikeTimeline.WIDE_END) return "wide";
		return "aftermath";
	}

	private static void shot(MinecraftClient client, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot {}", name);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {
		});
	}

	private static void tick(MinecraftClient client) {
		ticks++;
		IntegratedServer server = client.getServer();
		ClientPlayerEntity self = client.player;
		if (self != null && stage.ordinal() >= Stage.SETTLE.ordinal() && self.getAbilities().allowFlying
				&& !self.getAbilities().flying) {
			// Vanilla stops creative flight whenever the player touches the ground. Keep hovering where the test puts us.
			self.getAbilities().flying = true;
			self.sendAbilitiesUpdate();
		}
		switch (stage) {
			case WAIT_WORLD -> {
				if (client.world != null && client.player != null && server != null) {
					ShootingStar.LOGGER.info("[selftest] joined world");
					stage = Stage.SETUP;
					ticks = 0;
				}
			}
			case SETUP -> {
				if (ticks < 40) {
					return;
				}
				server.execute(() -> setUpPlayer(server));
				stage = Stage.SETTLE;
				ticks = 0;
			}
			case SETTLE -> {
				if (ticks == 460) {
					client.player.getInventory().selectedSlot = 0;
				}
				if (ticks >= 500) {
					shot(client, "00_holding_uplink.png");
					stage = Stage.FIRE;
					ticks = 0;
				}
			}
			case FIRE -> {
				if (ticks == 5) {
					ShootingStar.LOGGER.info("[selftest] using the uplink");
					client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
				}
				if (ClientStrikes.mine() != null) {
					ShootingStar.LOGGER.info("[selftest] strike locked");
					stage = Stage.WATCH;
					ticks = 0;
				} else if (ticks == 60 && !fallbackFired) {
					ShootingStar.LOGGER.warn("[selftest] the uplink found no target, calling the strike in directly");
					fallbackFired = true;
					server.execute(() -> {
						ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
						StrikeManager.launch(player.getServerWorld(), target, player);
					});
				} else if (ticks > 200) {
					ShootingStar.LOGGER.error("[selftest] no strike ever started");
					stage = Stage.DONE;
				}
			}
			case WATCH -> {
				ClientStrike strike = ClientStrikes.mine();
				if (strike != null) {
					target = strike.target;
				}
				int age = strike != null ? strike.age : Integer.MAX_VALUE;
				while (!CAPTURES.isEmpty() && age >= CAPTURES.peek().age()) {
					shot(client, CAPTURES.poll().name());
				}
				if (CAPTURES.isEmpty() || strike == null && ticks > 700) {
					stage = Stage.AFTER;
					ticks = 0;
				}
			}
			case AFTER -> {
				int r = Targeting.DEFAULT_RADIUS;
				if (ticks == 20) {
					server.execute(() -> lookFromAbove(server, target.getX(), target.getZ() - r * 13 / 10, r * 9 / 10,
							Vec3d.ofCenter(target)));
				}
				if (ticks == 140) {
					shot(client, "90_crater_above.png");
					server.execute(() -> lookFromAbove(server, target.getX() - r * 17 / 10, target.getZ() + 10, 16,
							Vec3d.ofCenter(target.up(r / 2))));
				}
				if (ticks == 260) {
					shot(client, "91_crater_side.png");
					server.execute(() -> lookFromAbove(server, target.getX() + r / 3, target.getZ() + r / 4, 8,
							Vec3d.ofCenter(target.up(2))));
				}
				if (ticks == 380) {
					shot(client, "92_crater_close.png");
				}
				if (ticks >= 420) {
					stage = Stage.DONE;
				}
			}
			case DONE -> {
				ShootingStar.LOGGER.info("[selftest] finished");
				stage = Stage.FINISHED;
				client.scheduleStop();
			}
			case FINISHED -> {
			}
		}
	}

	private static void setUpPlayer(IntegratedServer server) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = player.getServerWorld();
		world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		world.setTimeOfDay(5000);
		world.setWeather(12000, 0, false, false);
		player.changeGameMode(GameMode.CREATIVE);
		player.getAbilities().flying = true;
		player.sendAbilitiesUpdate();
		player.getInventory().setStack(0, new ItemStack(ModItems.GUNGNIR_UPLINK));

		BlockPos spawn = world.getSpawnPos();
		// Aim at the flattest spot just outside danger-close range so the crater and the camera shots are not
		// hidden by hills.
		double range = Targeting.minRange(Targeting.DEFAULT_RADIUS) + 8.0;
		double bestScore = Double.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI * 2 * i / 8;
			int x = spawn.getX() + (int) Math.round(Math.cos(angle) * range);
			int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * range);
			double score = roughness(world, x, z);
			if (score < bestScore) {
				bestScore = score;
				target = new BlockPos(x, top(world, x, z) - 1, z);
			}
		}
		// Climb until the uplink's own raycast reaches the target, so the first shot is a clean firing solution.
		double x = spawn.getX() + 0.5;
		double z = spawn.getZ() + 0.5;
		double eye = Math.max(top(world, spawn.getX(), spawn.getZ()) + 2.0, target.getY() + 6.0);
		for (int i = 0; i < 100 && !uplinkReaches(world, new Vec3d(x, eye, z), target); i++) {
			eye += 2.0;
		}
		look(server, x, eye - player.getStandingEyeHeight(), z, Vec3d.ofCenter(target));
		ShootingStar.LOGGER.info("[selftest] eye at {} {} {} aiming at {}", x, eye, z, target);
	}

	private static boolean uplinkReaches(ServerWorld world, Vec3d eye, BlockPos target) {
		BlockPos hit = Targeting.findTarget(world, eye, Vec3d.ofCenter(target).subtract(eye).normalize(), Targeting.MAX_RANGE);
		return hit != null && hit.getManhattanDistance(target) <= 2;
	}

	/** Moves the player at least {@code height} blocks above the ground at x/z, high enough to see {@code at}. */
	private static void lookFromAbove(IntegratedServer server, int x, int z, int height, Vec3d at) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = player.getServerWorld();
		Vec3d from = new Vec3d(x + 0.5, top(world, x, z) + height, z + 0.5);
		for (int i = 0; i < 100 && !canSee(world, from, at); i++) {
			from = from.add(0, 2, 0);
		}
		look(server, from.x, from.y - player.getStandingEyeHeight(), from.z, at);
	}

	/** True when the line of sight from {@code eye} reaches {@code at} or stops within a few blocks of it. */
	private static boolean canSee(ServerWorld world, Vec3d eye, Vec3d at) {
		Vec3d delta = at.subtract(eye);
		BlockPos hit = Targeting.findTarget(world, eye, delta.normalize(), delta.length() + 4.0);
		return hit == null || Vec3d.ofCenter(hit).distanceTo(at) <= 6.0;
	}

	private static int top(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		return world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
	}

	/** Spread of ground heights over the middle of the crater, with water counted as rough. */
	private static double roughness(ServerWorld world, int cx, int cz) {
		double sum = 0;
		double sumSq = 0;
		int n = 0;
		int reach = Targeting.DEFAULT_RADIUS / 2;
		for (int dx = -reach; dx <= reach; dx += 8) {
			for (int dz = -reach; dz <= reach; dz += 8) {
				int h = top(world, cx + dx, cz + dz);
				sum += h;
				sumSq += (double) h * h;
				n++;
			}
		}
		double mean = sum / n;
		double variance = sumSq / n - mean * mean;
		boolean water = !world.getFluidState(new BlockPos(cx, top(world, cx, cz) - 1, cz)).isEmpty();
		return variance + (water ? 400 : 0);
	}

	private static void look(IntegratedServer server, double x, double feet, double z, Vec3d at) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		double dx = at.x - x;
		double dy = at.y - (feet + player.getStandingEyeHeight());
		double dz = at.z - z;
		float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * MathHelper.DEGREES_PER_RADIAN);
		player.networkHandler.requestTeleport(x, feet, z, yaw, pitch);
	}
}
