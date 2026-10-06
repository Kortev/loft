package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.client.gap.ClientGap;
import io.github.kortev.shootingstar.client.gap.ClientGaps;
import io.github.kortev.shootingstar.gap.GapManager;
import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.registry.ModGameRules;
import io.github.kortev.shootingstar.registry.ModItems;
import io.github.kortev.shootingstar.strike.Targeting;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.DoubleFunction;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import net.minecraft.world.LightType;
import net.minecraft.world.World;

/**
 * With -Dshootingstar.selftest=gap: joins the quick-play world, turns the Genesis Key on flat ground a little
 * way off, records the whole Ginnungagap event as a video (see {@link Capture}), lets reality back in and
 * photographs what is left of the zone.
 */
public class GapSelfTest implements ClientModInitializer {
	private static final double RANGE = 64.0;

	private enum Stage { WAIT_WORLD, SETUP, SETTLE, FIRE, WATCH, DOMAIN, AFTER, DONE, FINISHED }

	private record Still(int age, String name) {
	}

	private static final Deque<Still> STILLS = new ArrayDeque<>();
	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static BlockPos target;
	private static boolean fallbackFired;
	/** Where the shooter stood in the black, and when the release was asked for. */
	private static Vec3d domainFeet;
	private static int releasedAt = -1;

	@Override
	public void onInitializeClient() {
		if (!"gap".equals(System.getProperty("shootingstar.selftest"))) {
			return;
		}
		for (int age = 10; age < GapTimeline.END + 30; age += 14) {
			STILLS.add(new Still(age, String.format("%02d_age%03d_%s.png", STILLS.size() + 1, age, phase(age))));
		}
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(55 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[selftest] timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-gap-selftest-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		ServerTickEvents.START_SERVER_TICK.register(server -> Capture.serverTickStart());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Capture.serverTickEnd();
			lightProbe(server.getOverworld(), "server");
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			tick(client);
			if (client.world != null) {
				lightProbe(client.world, "client");
			}
		});
	}

	private static int serverProbe;
	private static int clientProbe;

	/**
	 * Every two seconds of the capture, how much sky light there is down the hole on each side (it should all be 15, the
	 * shaft being open to the sky), so a hole whose walls come back black can be told from one whose light is late.
	 */
	private static void lightProbe(World world, String side) {
		if (target == null || !Capture.active() || ((world.isClient() ? ++clientProbe : ++serverProbe) % 40) != 0) {
			return;
		}
		int r = world.getGameRules().getInt(ModGameRules.GAP_RADIUS) - 10;
		StringBuilder s = new StringBuilder();
		for (int[] d : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
			s.append('[');
			for (int depth : new int[] {2, 12, 40, 100, 160}) {
				s.append(world.getLightLevel(LightType.SKY, target.add(d[0], -depth, d[1]))).append(' ');
			}
			s.setLength(s.length() - 1);
			s.append("] ");
		}
		ShootingStar.LOGGER.info("[probe] {} light at {}s: updates pending {}, sky light down the hole {}", side,
				String.format(java.util.Locale.ROOT, "%.1f", Capture.time() / 20.0), world.getLightingProvider().hasUpdates(), s);
	}

	private static String phase(int age) {
		if (age < GapTimeline.RISE) return "key";
		if (age < GapTimeline.FEED) return "rise";
		if (age < GapTimeline.GATE) return "orbit";
		if (age < GapTimeline.OPEN) return "gate";
		if (age < GapTimeline.MAP) return "open";
		if (age < GapTimeline.CUT) return "map";
		if (age < GapTimeline.SEND) return "cut";
		if (age < GapTimeline.FALL) return "send";
		if (age < GapTimeline.INBOUND) return "fall";
		if (age < GapTimeline.CONTACT) return "inbound";
		if (age < GapTimeline.BLAST) return "frames";
		if (age < GapTimeline.ERASURE) return "blast";
		if (age < GapTimeline.NOTHING) return "erasure";
		if (age < GapTimeline.END) return "nothing";
		return "hold";
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
					shot(client, "00_holding_key.png");
					stage = Stage.FIRE;
					ticks = 0;
				}
			}
			case FIRE -> {
				if (ticks == 1) {
					Capture.start(client, client.runDirectory.toPath().resolve("capture"));
				}
				if (ticks == 30) {
					ShootingStar.LOGGER.info("[selftest] turning the Genesis Key");
					client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
				}
				if (ClientGaps.mine() != null) {
					ShootingStar.LOGGER.info("[selftest] Ginnungagap open");
					stage = Stage.WATCH;
					ticks = 0;
				} else if (ticks == 90 && !fallbackFired) {
					ShootingStar.LOGGER.warn("[selftest] the key found no target, opening the gap directly");
					fallbackFired = true;
					server.execute(() -> {
						ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
						GapManager.launch(player.getServerWorld(), target, player);
					});
				} else if (ticks > 200) {
					ShootingStar.LOGGER.error("[selftest] no Ginnungagap event ever started");
					stage = Stage.DONE;
				}
			}
			case WATCH -> {
				ClientGap gap = ClientGaps.mine();
				int age = gap != null ? gap.age : Integer.MAX_VALUE;
				while (!STILLS.isEmpty() && age >= STILLS.peek().age()) {
					shot(client, STILLS.poll().name());
				}
				if (gap != null && age >= GapTimeline.RETURN + 10) {
					// Alone in the black: walk about in it for a while before letting reality back in.
					stage = Stage.DOMAIN;
					ticks = 0;
					domainFeet = client.player.getPos();
					client.options.hudHidden = true;
					Capture.camera = domainCamera(client, Capture.time());
				} else if (gap == null && ticks > 1200) {
					Capture.stop();
					stage = Stage.AFTER;
					ticks = 0;
				}
			}
			case DOMAIN -> {
				walk(client);
				// The key only lets reality back in once the event is over (GapTimeline.END).
				if (ticks == 150) {
					ShootingStar.LOGGER.info("[selftest] letting reality back in");
					shot(client, "88_domain.png");
					client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
					releasedAt = ticks;
					// The rebuild has cameras of its own: the walkabout's must not stand in front of them.
					Capture.camera = null;
				}
				// As soon as the black lifts, the fly-over takes the camera up over what is left.
				if (releasedAt >= 0 && ClientGaps.mine() == null) {
					ShootingStar.LOGGER.info("[selftest] reality is back; flying over the zone");
					Capture.camera = flyover(client, Capture.time());
					// A clear look at the zone for the closing shot.
					client.options.getCloudRenderMode().setValue(CloudRenderMode.OFF);
					stage = Stage.AFTER;
					ticks = 0;
				} else if (releasedAt >= 0 && ticks > releasedAt && (ticks - releasedAt) % 30 == 0 && ticks - releasedAt <= 630) {
					// Yggdrasil grown in the hole, the light going out through it, the world coming back.
					shot(client, String.format("89_rebuild_%03d.png", ticks - releasedAt));
				} else if (releasedAt >= 0 && ticks > releasedAt + GapTimeline.REBUILD_END + 100) {
					ShootingStar.LOGGER.error("[selftest] the key did not let reality back in");
					Capture.stop();
					stage = Stage.DONE;
				}
			}
			case AFTER -> {
				int r = 96;
				if (Capture.active()) {
					if (ticks == 150) {
						shot(client, "90_zone_above.png");
					}
					if (ticks == 285) {
						shot(client, "91_zone_edge.png");
					}
					if (ticks >= 295) {
						Capture.stop();
						stage = Stage.DONE;
					}
					return;
				}
				if (ticks == 10) {
					ShootingStar.LOGGER.info("[selftest] letting reality back in");
					client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
				}
				if (ticks == 50) {
					server.execute(() -> {
						ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
						player.changeGameMode(GameMode.CREATIVE);
						player.getAbilities().flying = true;
						player.sendAbilitiesUpdate();
					});
				}
				if (ticks == 60) {
					server.execute(() -> lookFrom(server, target.getX(), target.getZ() - r * 3 / 2, 70, Vec3d.ofCenter(target)));
				}
				if (ticks == 200) {
					shot(client, "90_zone_above.png");
					server.execute(() -> lookFrom(server, target.getX() - r - 12, target.getZ() + 6, 12, Vec3d.ofCenter(target.down(30))));
				}
				if (ticks == 320) {
					shot(client, "91_zone_edge.png");
				}
				if (ticks >= 340) {
					stage = Stage.DONE;
				}
			}
			case DONE -> {
				if (CameraProbe.bad() > 0) {
					ShootingStar.LOGGER.error("[selftest] FAILED: {} frames with a camera shot in or against the world", CameraProbe.bad());
					Runtime.getRuntime().halt(5);
				}
				ShootingStar.LOGGER.info("[selftest] finished");
				stage = Stage.FINISHED;
				client.scheduleStop();
			}
			case FINISHED -> {
			}
		}
	}

	/**
	 * The shooter walks about in the black on the floor the event left them: off along a slow curve, a hop to show
	 * there is something to stand on, then still, looking out at nothing.
	 */
	private static void walk(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}
		boolean walking = ticks >= 4 && ticks < 96;
		client.options.forwardKey.setPressed(walking);
		client.options.jumpKey.setPressed(ticks == 34 || ticks == 35 || ticks == 70 || ticks == 71);
		// A tight curve, so the walk stays well inside the floor laid under them.
		if (walking && ticks >= 14) {
			player.setYaw(player.getYaw() + 3.0F);
		}
		float settle = MathHelper.clamp((ticks - 96) / 20.0F, 0.0F, 1.0F);
		player.setPitch(MathHelper.lerp(settle, 8.0F, 35.0F));
		// Then turned back to face the hole, as anyone would be, waiting for the world.
		if (ticks >= 96 && target != null) {
			float face = (float) (MathHelper.atan2(target.getZ() + 0.5 - player.getZ(), target.getX() + 0.5 - player.getX())
					* MathHelper.DEGREES_PER_RADIAN) - 90.0F;
			player.setYaw(player.getYaw() + MathHelper.wrapDegrees(face - player.getYaw()) * 0.12F);
		}
	}

	/** Beside the walking shooter, turning slowly round them, then craning up until they are a speck standing on nothing. */
	private static DoubleFunction<Capture.Pose> domainCamera(MinecraftClient client, double start) {
		return time -> {
			ClientPlayerEntity player = client.player;
			float delta = client.getRenderTickCounter().getTickDelta(true);
			Vec3d p = player != null ? player.getLerpedPos(delta) : domainFeet;
			double s = time - start;
			double a = 0.9 + s * 0.012;
			double crane = smooth((s - 100.0) / 40.0);
			double r = MathHelper.lerp(crane, 5.5, 10.0);
			double h = MathHelper.lerp(crane, 1.9, 16.0);
			Vec3d eye = p.add(Math.cos(a) * r, h, Math.sin(a) * r);
			return pose(eye, p.add(0, 1.0, 0));
		};
	}

	/**
	 * From the shooter's eyes at the rim, up and out over the edge of the hole, looking down it, its walls dropping away
	 * into the void; then round along the rim and a little lower, looking across at the far wall. Kept near the shooter,
	 * where everything is loaded and inside the fog, and always clear of the ground.
	 */
	private static DoubleFunction<Capture.Pose> flyover(MinecraftClient client, double start) {
		int r = 96;
		Vec3d c = new Vec3d(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5);
		// From where the shooter is now: carried home to the rim when the world came back.
		Vec3d from = client.player != null ? client.player.getEyePos() : c.add(r + 10, 1.6, 0);
		double a0 = Math.atan2(from.z - c.z, from.x - c.x);
		double out0 = Math.hypot(from.x - c.x, from.z - c.z);
		return time -> {
			double s = time - start;
			double u = smooth((s - 20.0) / 120.0);
			double v = smooth((s - 150.0) / 130.0);
			double a = a0 - Math.toRadians(35.0) * v;
			double out = MathHelper.lerp(u, out0, r * 0.9);
			double up = MathHelper.lerp(u, 0.0, 40.0) - 14.0 * v;
			Vec3d eye = new Vec3d(c.x + Math.cos(a) * out, from.y + up, c.z + Math.sin(a) * out);
			// Down the near wall at first; then across at the far one.
			Vec3d down = new Vec3d(c.x + Math.cos(a) * r * 0.2, c.y - 60.0, c.z + Math.sin(a) * r * 0.2);
			Vec3d across = new Vec3d(c.x - Math.cos(a) * r * 0.55, c.y - 30.0, c.z - Math.sin(a) * r * 0.55);
			Vec3d at = from.add(Math.cos(a0 + Math.PI) * 20.0, -4.0, Math.sin(a0 + Math.PI) * 20.0).lerp(down, u).lerp(across, v);
			return pose(clear(client, eye), at);
		};
	}

	/** {@code eye} raised, if need be, to stand clear of the highest ground round it. */
	private static Vec3d clear(MinecraftClient client, Vec3d eye) {
		if (client.world == null) {
			return eye;
		}
		int top = Integer.MIN_VALUE;
		for (int dx = -3; dx <= 3; dx += 3) {
			for (int dz = -3; dz <= 3; dz += 3) {
				top = Math.max(top, client.world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(eye.x) + dx, MathHelper.floor(eye.z) + dz));
			}
		}
		return eye.y < top + 3.0 ? new Vec3d(eye.x, top + 3.0, eye.z) : eye;
	}

	private static Capture.Pose pose(Vec3d eye, Vec3d at) {
		Vec3d d = at.subtract(eye);
		double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) (MathHelper.atan2(d.z, d.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(d.y, horizontal) * MathHelper.DEGREES_PER_RADIAN);
		return new Capture.Pose(eye.x, eye.y, eye.z, yaw, pitch);
	}

	private static double smooth(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}

	private static void setUpPlayer(IntegratedServer server) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = player.getServerWorld();
		world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		// Morning: at noon the moon would be straight down, and seen through the bottom of the hole.
		world.setTimeOfDay(2000);
		world.setWeather(12000, 0, false, false);
		// On foot and in survival, so the hotbar, hearts, hunger and experience are all there at the end.
		player.changeGameMode(GameMode.SURVIVAL);
		player.getInventory().setStack(0, new ItemStack(ModItems.GENESIS_KEY));
		player.getInventory().setStack(1, new ItemStack(Items.DIAMOND_SWORD));
		player.getInventory().setStack(2, new ItemStack(Items.DIAMOND_PICKAXE));
		player.getInventory().setStack(3, new ItemStack(Items.ENDER_PEARL, 16));
		player.getInventory().setStack(4, new ItemStack(Items.BREAD, 12));
		player.getInventory().setStack(5, new ItemStack(Items.GRASS_BLOCK, 64));
		player.getInventory().setStack(6, new ItemStack(Items.TORCH, 32));
		player.getInventory().setStack(7, new ItemStack(Items.WATER_BUCKET));
		player.setExperienceLevel(30);

		BlockPos spawn = world.getSpawnPos();
		double bestScore = Double.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI * 2 * i / 8;
			int x = spawn.getX() + (int) Math.round(Math.cos(angle) * RANGE);
			int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * RANGE);
			double score = roughness(world, x, z);
			if (score < bestScore) {
				bestScore = score;
				target = new BlockPos(x, top(world, x, z) - 1, z);
			}
		}
		double x = spawn.getX() + 0.5;
		double z = spawn.getZ() + 0.5;
		// Stand on the ground; if the target cannot be seen from there, stand on a pillar tall enough to see it.
		int ground = top(world, spawn.getX(), spawn.getZ());
		int feet = ground;
		while (feet < ground + 60 && !keyReaches(world, new Vec3d(x, feet + player.getStandingEyeHeight(), z), target)) {
			feet++;
		}
		for (int y = ground; y < feet; y++) {
			world.setBlockState(new BlockPos(spawn.getX(), y, spawn.getZ()), Blocks.STONE.getDefaultState());
		}
		double eye = feet + player.getStandingEyeHeight();
		look(server, x, feet, z, Vec3d.ofCenter(target));
		ShootingStar.LOGGER.info("[selftest] eye at {} {} {} aiming at {}", x, eye, z, target);
	}

	private static boolean keyReaches(ServerWorld world, Vec3d eye, BlockPos target) {
		BlockPos hit = Targeting.findTarget(world, eye, Vec3d.ofCenter(target).subtract(eye).normalize(), Targeting.MAX_RANGE);
		return hit != null && hit.getManhattanDistance(target) <= 2;
	}

	private static void lookFrom(IntegratedServer server, int x, int z, int height, Vec3d at) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = player.getServerWorld();
		Vec3d from = new Vec3d(x + 0.5, Math.max(top(world, x, z), at.y) + height, z + 0.5);
		look(server, from.x, from.y - player.getStandingEyeHeight(), from.z, at);
	}

	private static int top(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		return world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
	}

	private static double roughness(ServerWorld world, int cx, int cz) {
		double sum = 0;
		double sumSq = 0;
		int n = 0;
		for (int dx = -24; dx <= 24; dx += 6) {
			for (int dz = -24; dz <= 24; dz += 6) {
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
