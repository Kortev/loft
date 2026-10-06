package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.chitty.Chitty;
import io.github.kortev.shootingstar.chitty.ChittyEntity;
import io.github.kortev.shootingstar.client.chitty.ChittySound;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleFunction;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Resource;
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
 * With -Dshootingstar.selftest=chitty: on a runway built beside a lake, puts Chitty down with the item, gets in and
 * drives her off: down the runway, the lever pulled at speed, wings out and climbing, a banked turn, a dive onto the
 * lake and away on her floats. Everything is filmed (see {@link Capture}) from chase, side and orbiting cameras with a
 * still at each stage. The running sounds are loops whose level and pitch follow the car, which the capture's sound log
 * cannot hold, so they are logged here tick by tick (loops.json) and rendered into the soundtrack by the workflow.
 */
public class ChittySelfTest implements ClientModInitializer {
	private enum Stage { WAIT_WORLD, SETUP, SETTLE, PLACE, BOARD, INTRO, DRIVE, TAKEOFF, CLIMB, TURN, CRUISE, DIVE, SPLASH, AFLOAT, DONE, FINISHED }

	/** The course, relative to where it is built: the runway runs south (+z), the lake lies east of it. */
	private static final int WEST = -26;
	private static final int EAST = 70;
	private static final int NORTH = -30;
	private static final int SOUTH = 150;
	private static final int LAKE_WEST = 16;
	private static final int LAKE_EAST = 62;
	private static final int LAKE_NORTH = -20;
	private static final int LAKE_SOUTH = 110;
	private static final int SKY = 48;

	private static Stage stage = Stage.WAIT_WORLD;
	/** Set by the server once the course is built: that takes it a good while, and the client does not wait for it. */
	private static volatile boolean built;
	private static int ticks;
	private static int total;
	private static BlockPos base;
	private static int ground;
	private static float turnFrom;
	private static ChittyEntity car;
	/** The camera's eased offsets from the car (or, for a fixed camera, where it is looking). */
	private static Vec3d camEye;
	private static Vec3d camAt;
	/** Whether those are a fixed camera's: moving between two following cameras eases from one to the other instead of cutting. */
	private static boolean camFixed = true;
	private static final List<double[]> ENGINE = new ArrayList<>();
	private static final List<double[]> FLIGHT = new ArrayList<>();

	@Override
	public void onInitializeClient() {
		if (!"chitty".equals(System.getProperty("shootingstar.selftest"))) {
			return;
		}
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(40 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[selftest] timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-chitty-selftest-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		ServerTickEvents.START_SERVER_TICK.register(server -> Capture.serverTickStart());
		ServerTickEvents.END_SERVER_TICK.register(server -> Capture.serverTickEnd());
		ClientTickEvents.END_CLIENT_TICK.register(ChittySelfTest::tick);
	}

	private static void shot(MinecraftClient client, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot {}", name);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {
		});
	}

	private static void next(Stage to) {
		ShootingStar.LOGGER.info("[selftest] {} -> {} after {} ticks{}", stage, to, ticks, car != null ? " " + describe(car) : "");
		stage = to;
		ticks = 0;
	}

	private static String describe(ChittyEntity c) {
		return String.format(Locale.ROOT, "(car at %.1f %.1f %.1f yaw %.0f speed %.2f flying %s ground %s water %.2f wings %.2f floats %.2f)",
				c.getX() - base.getX(), c.getY() - ground, c.getZ() - base.getZ(), c.getYaw(), c.getSpeed(), c.isFlying(),
				c.isOnGround(), c.getFluidHeight(net.minecraft.registry.tag.FluidTags.WATER), c.getWingOpen(1.0F), c.getFloatOpen(1.0F));
	}

	private static void keys(MinecraftClient client, boolean forward, boolean left, boolean right, boolean jump, boolean sprint) {
		GameOptions o = client.options;
		o.forwardKey.setPressed(forward);
		o.backKey.setPressed(false);
		o.leftKey.setPressed(left);
		o.rightKey.setPressed(right);
		o.jumpKey.setPressed(jump);
		o.sprintKey.setPressed(sprint);
		o.sneakKey.setPressed(false);
	}

	private static void tick(MinecraftClient client) {
		ticks++;
		total++;
		IntegratedServer server = client.getServer();
		ClientPlayerEntity self = client.player;
		if (self != null && self.getVehicle() instanceof ChittyEntity c) {
			car = c;
		}
		if (car != null && Capture.active()) {
			sample(client);
		}
		if (car != null && total % 20 == 0 && base != null) {
			ShootingStar.LOGGER.info("[selftest] {} t{} {}", stage, ticks, describe(car));
		}
		switch (stage) {
			case WAIT_WORLD -> {
				if (client.world != null && self != null && server != null) {
					ShootingStar.LOGGER.info("[selftest] joined world");
					next(Stage.SETUP);
				}
			}
			case SETUP -> {
				if (ticks == 40) {
					server.execute(() -> build(server));
					next(Stage.SETTLE);
				}
			}
			case SETTLE -> {
				// Wait for the server to finish building, then let the rebuilt chunks reach the client and be meshed.
				if (!built) {
					ticks = 0;
					if (total > 6000) {
						ShootingStar.LOGGER.error("[selftest] the course was never built");
						next(Stage.DONE);
					}
					return;
				}
				if (ticks >= 260) {
					client.options.hudHidden = true;
					self.getInventory().selectedSlot = 0;
					next(Stage.PLACE);
				}
			}
			case PLACE -> {
				if (ticks == 10) {
					ShootingStar.LOGGER.info("[selftest] putting Chitty down");
					client.interactionManager.interactItem(self, Hand.MAIN_HAND);
				}
				if (ticks == 30) {
					ChittyEntity placed = client.world.getEntitiesByClass(ChittyEntity.class, self.getBoundingBox().expand(12.0), e -> true)
							.stream().findFirst().orElse(null);
					if (placed == null) {
						ShootingStar.LOGGER.error("[selftest] the item put no car down; spawning one");
						server.execute(() -> spawnCar(server));
					} else {
						car = placed;
					}
				}
				if (ticks == 40) {
					if (car == null) {
						car = client.world.getEntitiesByClass(ChittyEntity.class, self.getBoundingBox().expand(12.0), e -> true)
								.stream().findFirst().orElse(null);
					}
					if (car == null) {
						ShootingStar.LOGGER.error("[selftest] no car at all");
						next(Stage.DONE);
						return;
					}
					shot(client, "00_parked.png");
					Capture.start(client, client.runDirectory.toPath().resolve("capture"));
					Capture.camera = orbit(client, Capture.time(), 7.5, 2.0, 0.0);
					next(Stage.BOARD);
				}
			}
			case BOARD -> {
				if (ticks == 50) {
					shot(client, "01_orbit.png");
				}
				if (ticks == 60) {
					ShootingStar.LOGGER.info("[selftest] getting in");
					client.interactionManager.interactEntity(self, car, Hand.MAIN_HAND);
				}
				if (ticks > 60 && self.getVehicle() == car) {
					next(Stage.INTRO);
				} else if (ticks > 120) {
					ShootingStar.LOGGER.error("[selftest] could not get in; seating the player directly");
					server.execute(() -> {
						ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
						ChittyEntity serverCar = (ChittyEntity) player.getServerWorld().getEntity(car.getUuid());
						if (serverCar != null) {
							player.startRiding(serverCar, true);
						}
					});
					ticks = 50;
				}
			}
			case INTRO -> {
				// She starts: chitty chitty bang bang, smoke out of the exhaust.
				if (ticks == 18) {
					shot(client, "02_start.png");
				}
				if (ticks == 60) {
					shot(client, "03_ready.png");
				}
				if (ticks >= 80) {
					Capture.camera = chase(client, -3.2, 2.0, -8.5, 0.6, 2.5);
					next(Stage.DRIVE);
				}
			}
			case DRIVE -> {
				keys(client, true, false, false, false, false);
				if (ticks == 40) {
					shot(client, "04_driving.png");
				}
				if (car.getSpeed() > 0.6 || ticks > 200) {
					Capture.camera = beside(client, car.getPos().add(9.0, 1.0, 14.0));
					next(Stage.TAKEOFF);
				}
			}
			case TAKEOFF -> {
				keys(client, true, false, false, true, false);
				if (ticks == 12) {
					shot(client, "05_wings.png");
				}
				if (ticks == 20) {
					shot(client, "06_liftoff.png");
				}
				if (car.getY() > ground + 7 || ticks > 160) {
					Capture.camera = chase(client, -4.5, 3.0, -9.0, 0.0, 2.0);
					next(Stage.CLIMB);
				}
			}
			case CLIMB -> {
				keys(client, true, false, false, true, false);
				if (ticks == 30) {
					shot(client, "07_climbing.png");
				}
				if (car.getY() > ground + 18 || ticks > 200) {
					turnFrom = car.getYaw();
					// Outside the turn (she turns left, so her right), to see her bank and her wings.
					Capture.camera = chase(client, -9.0, 2.5, -2.5, 0.0, 1.0);
					next(Stage.TURN);
				}
			}
			case TURN -> {
				keys(client, true, true, false, false, false);
				if (ticks == 25) {
					shot(client, "08_banking.png");
				}
				if (Math.abs(MathHelper.wrapDegrees(car.getYaw() - turnFrom)) > 170.0F || ticks > 160) {
					Capture.camera = chase(client, -6.5, 1.2, 3.5, 0.0, 0.0);
					next(Stage.CRUISE);
				}
			}
			case CRUISE -> {
				keys(client, true, false, false, false, false);
				if (ticks == 12) {
					shot(client, "09_cruising.png");
				}
				if (ticks >= 24) {
					// Ahead and to her right, looking back at her as she comes down.
					Capture.camera = chase(client, -7.0, 0.5, 7.0, 0.0, 0.0);
					next(Stage.DIVE);
				}
			}
			case DIVE -> {
				// Nose down and throttle back, so she comes down onto the middle of the lake instead of its far end.
				keys(client, false, false, false, false, true);
				client.options.backKey.setPressed(true);
				if (ticks == 30) {
					shot(client, "10_diving.png");
				}
				if (car.getFluidHeight(net.minecraft.registry.tag.FluidTags.WATER) > 0.05 || car.isOnGround() || ticks > 260) {
					// Off to her right a little ahead, so she slides up alongside it as she slows.
					Capture.camera = beside(client, local(car.getPos(), car.getYaw(), -6.0, 2.0, 7.0));
					next(Stage.SPLASH);
				}
			}
			case SPLASH -> {
				keys(client, false, false, false, false, false);
				if (ticks == 6) {
					shot(client, "11_splashdown.png");
				}
				if (ticks == 58) {
					shot(client, "12_floats.png");
				}
				if (ticks >= 60) {
					Capture.camera = orbit(client, Capture.time(), 8.5, 2.6, 2.4);
					next(Stage.AFLOAT);
				}
			}
			case AFLOAT -> {
				// Round in a slow circle on the lake (to her left, away from the near bank): on the water she turns tight
				// enough to stay well off the banks.
				keys(client, ticks > 20 && ticks < 150, ticks > 30 && ticks < 150, false, false, false);
				if (ticks == 70) {
					shot(client, "13_afloat.png");
				}
				if (ticks == 140) {
					shot(client, "14_afloat_wide.png");
				}
				if (ticks >= 160) {
					keys(client, false, false, false, false, false);
					next(Stage.DONE);
				}
			}
			case DONE -> {
				if (Capture.active()) {
					Capture.stop();
					writeLoops(client);
				}
				ShootingStar.LOGGER.info("[selftest] finished");
				stage = Stage.FINISHED;
				client.scheduleStop();
			}
			case FINISHED -> {
			}
		}
	}

	// --- the course ---------------------------------------------------------------------------------

	/** A flat grass field with a dirt runway down the middle and a lake beside it, the sky cleared above. */
	private static void build(IntegratedServer server) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = player.getServerWorld();
		world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		world.setTimeOfDay(3000);
		world.setWeather(12000, 0, false, false);
		player.changeGameMode(GameMode.CREATIVE);
		player.getAbilities().flying = false;
		player.sendAbilitiesUpdate();
		player.getInventory().setStack(0, new ItemStack(Chitty.ITEM));
		BlockPos spawn = world.getSpawnPos();
		world.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
		ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()) - 1;
		base = new BlockPos(spawn.getX(), ground, spawn.getZ());
		int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState grass = Blocks.GRASS_BLOCK.getDefaultState();
		BlockState dirt = Blocks.DIRT.getDefaultState();
		BlockState path = Blocks.DIRT_PATH.getDefaultState();
		BlockState water = Blocks.WATER.getDefaultState();
		BlockState sand = Blocks.SAND.getDefaultState();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int dx = WEST; dx <= EAST; dx++) {
			for (int dz = NORTH; dz <= SOUTH; dz++) {
				int x = base.getX() + dx;
				int z = base.getZ() + dz;
				world.getChunk(x >> 4, z >> 4);
				boolean lake = dx >= LAKE_WEST && dx <= LAKE_EAST && dz >= LAKE_NORTH && dz <= LAKE_SOUTH;
				boolean shore = !lake && dx >= LAKE_WEST - 2 && dx <= LAKE_EAST + 2 && dz >= LAKE_NORTH - 2 && dz <= LAKE_SOUTH + 2;
				boolean runway = Math.abs(dx) <= 2 && dz >= -8 && dz <= 60;
				for (int y = ground - 5; y <= ground + SKY; y++) {
					BlockState state;
					if (y > ground) {
						state = air;
					} else if (lake) {
						state = y >= ground - 4 ? water : sand;
					} else if (y == ground) {
						state = runway ? path : shore ? sand : grass;
					} else {
						state = dirt;
					}
					world.setBlockState(pos.set(x, y, z), state, flags);
				}
			}
		}
		// Stand behind where she will go, looking down the runway at the ground just ahead.
		player.networkHandler.requestTeleport(base.getX() + 0.5, ground + 1.0, base.getZ() - 4.5, 0.0F, 35.0F);
		ShootingStar.LOGGER.info("[selftest] course built at {} (ground {})", base, ground);
		built = true;
	}

	private static void spawnCar(IntegratedServer server) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ChittyEntity c = new ChittyEntity(Chitty.ENTITY, player.getServerWorld());
		c.refreshPositionAndAngles(base.getX() + 0.5, ground + 1.0, base.getZ() + 0.5, 0.0F, 0.0F);
		player.getServerWorld().spawnEntity(c);
	}

	// --- cameras ------------------------------------------------------------------------------------

	private static Vec3d carPos(MinecraftClient client) {
		float delta = client.getRenderTickCounter().getTickDelta(true);
		return car != null ? car.getLerpedPos(delta) : Vec3d.ofCenter(base);
	}

	private static float carYaw(MinecraftClient client) {
		float delta = client.getRenderTickCounter().getTickDelta(true);
		return car != null ? car.getYaw(delta) : 0.0F;
	}

	/** A point in the car's own frame (x to her left, y up, z forward) in the world. */
	private static Vec3d local(Vec3d origin, float yaw, double x, double y, double z) {
		return origin.add(new Vec3d(x, y, z).rotateY(-yaw * MathHelper.RADIANS_PER_DEGREE));
	}

	/** Circling her slowly, looking at the middle of the car. */
	private static DoubleFunction<Capture.Pose> orbit(MinecraftClient client, double start, double radius, double height, double from) {
		cut(false);
		return time -> {
			Vec3d c = carPos(client);
			double a = from + (time - start) * 0.012;
			return follow(c, c.add(Math.cos(a) * radius, height, Math.sin(a) * radius), c.add(0.0, 0.8, 0.0), 0.15);
		};
	}

	/** Following her from a point in her own frame, looking at a point a little ahead of her. */
	private static DoubleFunction<Capture.Pose> chase(MinecraftClient client, double x, double y, double z, double atY, double atZ) {
		cut(false);
		return time -> {
			Vec3d c = carPos(client);
			float yaw = carYaw(client);
			return follow(c, local(c, yaw, x, y, z), local(c, yaw, 0.0, 0.8 + atY, atZ), 0.06);
		};
	}

	/** Standing still at a place, turning to watch her go by. */
	private static DoubleFunction<Capture.Pose> beside(MinecraftClient client, Vec3d at) {
		cut(true);
		return time -> {
			Vec3d target = carPos(client).add(0.0, 0.8, 0.0);
			camAt = camAt == null ? target : camAt.lerp(target, 0.35);
			return pose(at, camAt);
		};
	}

	/** Starts a new camera, cutting to it unless it follows her as the last one did. */
	private static void cut(boolean fixed) {
		if (fixed || camFixed) {
			camEye = null;
			camAt = null;
		}
		camFixed = fixed;
	}

	/**
	 * Eases the camera's offsets from the car rather than its place in the world, so it keeps up with her however fast
	 * she goes and only its framing drifts.
	 */
	private static Capture.Pose follow(Vec3d anchor, Vec3d eye, Vec3d at, double rate) {
		Vec3d e = eye.subtract(anchor);
		Vec3d a = at.subtract(anchor);
		camEye = camEye == null ? e : camEye.lerp(e, rate);
		camAt = camAt == null ? a : camAt.lerp(a, Math.min(1.0, rate * 2.5));
		return pose(anchor.add(camEye), anchor.add(camAt));
	}

	private static Capture.Pose pose(Vec3d eye, Vec3d at) {
		Vec3d d = at.subtract(eye);
		double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) (MathHelper.atan2(d.z, d.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(d.y, horizontal) * MathHelper.DEGREES_PER_RADIAN);
		return new Capture.Pose(eye.x, eye.y, eye.z, yaw, pitch);
	}

	// --- the running sounds for the soundtrack ------------------------------------------------------

	/** The engine and propeller loops' level and pitch now, as heard from the camera. */
	private static void sample(MinecraftClient client) {
		double time = Capture.time() / 20.0;
		Vec3d listener = client.gameRenderer.getCamera().getPos();
		double distance = listener.distanceTo(car.getPos().add(0.0, 0.8, 0.0));
		for (boolean flight : new boolean[] {false, true}) {
			float volume = ChittySound.targetVolume(car, flight);
			double range = 32.0 * Math.max(1.0F, volume);
			double gain = MathHelper.clamp(volume, 0.0F, 1.0F) * MathHelper.clamp(1.0 - distance / range, 0.0, 1.0);
			(flight ? FLIGHT : ENGINE).add(new double[] {time, gain, ChittySound.targetPitch(car, flight)});
		}
	}

	private static void writeLoops(MinecraftClient client) {
		Path dir = client.runDirectory.toPath().resolve("capture");
		StringBuilder json = new StringBuilder("{\"tracks\": [\n");
		String[][] tracks = {{"chitty_engine", "ENGINE"}, {"chitty_flight", "FLIGHT"}};
		for (int t = 0; t < tracks.length; t++) {
			String name = tracks[t][0];
			List<double[]> points = t == 0 ? ENGINE : FLIGHT;
			try {
				Resource resource = client.getResourceManager().getResource(ShootingStar.id("sounds/" + name + ".ogg")).orElseThrow();
				try (InputStream in = resource.getInputStream()) {
					Files.copy(in, dir.resolve("sounds").resolve(name + "_loop.ogg"), StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException | RuntimeException e) {
				ShootingStar.LOGGER.warn("[selftest] could not extract {}", name, e);
				continue;
			}
			json.append("  {\"loop\": \"").append(name).append("_loop.ogg\", \"points\": [");
			for (int i = 0; i < points.size(); i++) {
				double[] p = points.get(i);
				json.append(String.format(Locale.ROOT, "%s[%.3f, %.4f, %.4f]", i == 0 ? "" : ", ", p[0], p[1], p[2]));
			}
			json.append("]}").append(t + 1 < tracks.length ? ",\n" : "\n");
		}
		json.append("]}\n");
		try {
			Files.writeString(dir.resolve("loops.json"), json.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ShootingStar.LOGGER.warn("[selftest] could not write loops.json", e);
		}
	}
}
