package io.github.kortev.shootingstar.test;

import com.mojang.datafixers.util.Pair;
import io.github.kortev.gojolander.Gojolander;
import io.github.kortev.gojolander.GojoRules;
import io.github.kortev.gojolander.Owner;
import io.github.kortev.gojolander.client.ClientPurple;
import io.github.kortev.gojolander.client.ClientPurples;
import io.github.kortev.gojolander.client.GojolanderClient;
import io.github.kortev.gojolander.purple.PurpleTimeline;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.jetbrains.annotations.Nullable;

/**
 * With -Dshootingstar.selftest=film-purple: a test clip of Hollow Purple, tall (1080x1920, {@link Capture}), daylight,
 * no captions: the friend (a stand-in's skin) on a hill facing a mountain, with a forest and then a village between
 * them and villagers, animals and an iron golem in the way. He presses the key (the real one: the client's own key
 * binding, the packet, the server) and it plays as it plays in the game: Blue, Red, the merge, the sphere carving
 * a tunnel straight through the forest, the village and the mountain (its radius is a game rule: here 5), the high
 * shot of the trench it leaves; then a few seconds more over what is left.
 *
 * <p>The scene is built here, in a line east of where the friend stands (+x ahead, +z to his right): a hill under
 * him, meadow, forest, village, mountain.
 */
public class FilmPurple implements ClientModInitializer {
	private enum Stage { WAIT_WORLD, SETUP, SETTLE, RUN, DONE, FINISHED }

	static final float FACING = -90.0F;
	/** A shade downward, so the sphere comes down to the ground in the forest and goes on into the mountain. */
	static final float PITCH = 1.2F;
	static final int RADIUS = 6;
	/** How long the scene is left once it is built before the recording starts: its chunks drawn, the toasts gone. */
	static final int SETTLE = 700;
	/** How long the chunks are let go of, to be sent again once the light is done. */
	static final int RELOAD = 120;
	/** The recording ends this long after the cast, in ticks. */
	static final int LENGTH = 292;
	/** The cinematic's own last tick, after the cast: then the closing shots take the camera. */
	static final int CINEMA = PurpleTimeline.RELEASE + PurpleTimeline.CINEMA;
	/** Where the scene lies, in blocks east of him and either side, and how high the mountain stands. */
	private static final int FROM = -26;
	private static final int TO = 262;
	private static final int SIDE = 44;
	private static final double MOUNTAIN_X = 182.0;
	private static final double MOUNTAIN_Z = -2.0;
	private static final double PEAK = 58.0;

	/** -Dshootingstar.film.quick=true: no scene (the plain as it is), to try the cast itself in a minute. */
	private static final boolean QUICK = Boolean.getBoolean("shootingstar.film.quick");
	/**
	 * -Dshootingstar.film.reuse=true: the scene as the last full take left it (build/film/purple_world, saved before the cast;
	 * the script copies it in), not built again: the building and the light take minutes.
	 */
	private static final boolean REUSE = Boolean.getBoolean("shootingstar.film.reuse");
	private static boolean on;
	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static volatile boolean ready;
	private static volatile ServerWorld building;
	private static int quiet;
	private static int waited;
	private static volatile BlockPos spot;
	private static int clientTick = -1;
	private static int castAt = -1;
	private static double castClock = -1.0;
	private static boolean pressed;
	private static boolean aerial;
	private static final java.util.Set<String> SHOT = new java.util.HashSet<>();
	private static final Random RANDOM = Random.create(88L);

	public static boolean active() {
		return on;
	}

	@Override
	public void onInitializeClient() {
		if (!"film-purple".equals(System.getProperty("shootingstar.selftest", ""))) {
			return;
		}
		on = true;
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(40 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[film] timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-film-purple-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		ServerTickEvents.START_SERVER_TICK.register(server -> Capture.serverTickStart());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Capture.serverTickEnd();
			lit(server);
		});
		ClientTickEvents.END_CLIENT_TICK.register(FilmPurple::clientTick);
	}

	// =============================================================================================
	// Client
	// =============================================================================================

	private static void clientTick(MinecraftClient client) {
		ticks++;
		TestWindow.keepToItself(client);
		IntegratedServer server = client.getServer();
		switch (stage) {
			case WAIT_WORLD -> {
				if (client.world != null && client.player != null && server != null) {
					ShootingStar.LOGGER.info("[film] joined as {}", client.player.getGameProfile().getName());
					stage = Stage.SETUP;
					ticks = 0;
				}
			}
			case SETUP -> {
				if (ticks < 40) {
					return;
				}
				client.options.getFov().setValue(80);
				client.options.getGuiScale().setValue(0);
				client.options.getViewDistance().setValue(15);
				client.options.setPerspective(Perspective.FIRST_PERSON);
				client.options.hudHidden = true;
				client.onResolutionChanged();
				server.execute(() -> setUp(server));
				stage = Stage.SETTLE;
				ticks = 0;
			}
			case SETTLE -> {
				client.getToastManager().clear();
				if (!ready) {
					ticks = 0;
				} else if (QUICK ? ticks < 0 : ticks < RELOAD) {
					// The chunks sent while the light was still being worked out came with none: let them go (a small view
					// distance) and have them sent again, lit.
					client.options.getViewDistance().setValue(2);
				} else if (!QUICK && ticks == RELOAD) {
					client.options.getViewDistance().setValue(15);
				} else if (ticks >= (QUICK ? 200 : SETTLE)) {
					ShootingStar.LOGGER.info("[film] rolling");
					lightReport(client, server);
					Capture.start(client, client.runDirectory.toPath().resolve("capture"));
					stage = Stage.RUN;
					ticks = 0;
				}
			}
			case RUN -> {
				client.getToastManager().clear();
				act(client);
				if (clientTick >= 0 && castAt >= 0 && clientTick - castAt >= LENGTH || ticks > 2400) {
					ShootingStar.LOGGER.info("[film] cut ({} ticks after the cast)", clientTick - castAt);
					Capture.stop();
					stage = Stage.DONE;
				}
			}
			case DONE -> {
				ShootingStar.LOGGER.info("[film] done");
				stage = Stage.FINISHED;
				Thread out = new Thread(() -> {
					try {
						Thread.sleep(30_000L);
					} catch (InterruptedException e) {
						return;
					}
					ShootingStar.LOGGER.warn("[film] the game did not stop by itself; stopping it");
					Runtime.getRuntime().halt(0);
				}, "shootingstar-film-purple-exit");
				out.setDaemon(true);
				out.start();
				client.scheduleStop();
			}
			case FINISHED -> {
			}
		}
	}

	/** Sky light at a few places, as the client has it and as the server has it (the mountain's face should be bright). */
	private static void lightReport(MinecraftClient client, @Nullable IntegratedServer server) {
		if (spot == null || client.world == null || server == null) {
			return;
		}
		int[][] samples = {{12, 4, 0}, {60, 14, 0}, {120, 3, 0}, {128, 3, 20}, {150, 12, 0}, {150, 30, 0}, {120, 20, 0}};
		for (int[] p : samples) {
			BlockPos at = spot.add(p[0], p[1], p[2]);
			int mine = client.world.getLightLevel(net.minecraft.world.LightType.SKY, at);
			int theirs = server.getOverworld().getLightLevel(net.minecraft.world.LightType.SKY, at);
			ShootingStar.LOGGER.info("[film] sky light at +{} +{} +{}: client {} server {} ({})", p[0], p[1], p[2], mine, theirs,
					client.world.getBlockState(at).isAir() ? "air" : "solid");
		}
	}

	/** Each tick of the recording: he looks where he looks and presses the key; after the cinematic, the closing shots. */
	private static void act(MinecraftClient client) {
		if (client.player == null || spot == null) {
			return;
		}
		int now = ++clientTick;
		if (castAt < 0) {
			client.player.setYaw(FACING);
			client.player.setPitch(PITCH);
			client.player.setHeadYaw(FACING);
			client.player.setBodyYaw(FACING);
		}
		if (!pressed && now >= 1) {
			pressed = true;
			castAt = now;
			castClock = Capture.time();
			ShootingStar.LOGGER.info("[film] the friend presses the key at {} (blindfold on: {}, key {}, can send: {})", now,
					io.github.kortev.gojolander.BlindfoldItem.wearing(client.player), KeyBindingHelper.getBoundKeyOf(GojolanderClient.PURPLE).getTranslationKey(),
					net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(io.github.kortev.gojolander.network.PurpleCastPayload.ID));
			// The real key path: the client's own key binding, which sends its packet to the server.
			KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(GojolanderClient.PURPLE));
		}
		if (castAt < 0) {
			return;
		}
		int age = now - castAt;
		ClientPurple p = ClientPurples.mine();
		if (p != null && (age == 10 || age == 20 || age == 30 || age == 52 || age == 62)) {
			io.github.kortev.gojolander.client.PurplePose.Anchor at = io.github.kortev.gojolander.client.PurplePose.anchor(client, p, 0.0F);
			Vec3d cam = client.gameRenderer.getCamera().getPos();
			Vec3d blue = io.github.kortev.gojolander.client.PurplePose.hand(p, true, p.age, at);
			Vec3d red = io.github.kortev.gojolander.client.PurplePose.hand(p, false, p.age, at);
			ShootingStar.LOGGER.info("[film] age {} camera {} blue at {} ({} away) red at {} ({} away) feet {} fov option {} scale {}", p.age,
					cam, blue, String.format(Locale.ROOT, "%.2f", cam.distanceTo(blue)), red, String.format(Locale.ROOT, "%.2f", cam.distanceTo(red)),
					at.feet(), client.options.getFov().getValue(), io.github.kortev.gojolander.client.PurpleCamera.fovScale(0.0F));
		}
		if (p != null && age % 5 == 0 && age <= 150) {
			shot(client, String.format(Locale.ROOT, "p%03d", age));
		}
		if (!aerial && age >= CINEMA + 2 && !ClientPurples.shooting()) {
			aerial = true;
			Capture.camera = time -> closing(time - castClock - CINEMA - 2.0);
		}
		if (aerial && age % 10 == 0) {
			shot(client, String.format(Locale.ROOT, "p%03d", age));
		}
	}

	private static void shot(MinecraftClient client, String name) {
		String file = "purple_" + name + ".png";
		if (!SHOT.add(file)) {
			return;
		}
		ScreenshotRecorder.saveScreenshot(client.runDirectory, file, client.getFramebuffer(), message -> {
		});
	}

	// =============================================================================================
	// The closing shots (after the cinematic): over what is left of the world.
	// =============================================================================================

	private static Capture.Pose closing(double since) {
		BlockPos s = spot;
		double x0 = s.getX() + 0.5;
		double y0 = s.getY();
		double z0 = s.getZ() + 0.5;
		double span = LENGTH - CINEMA - 2.0;
		double one = span * 0.36;
		double two = span * 0.68;
		if (since < one) {
			// High over the trench, looking down on what it cut through the forest and the village, drifting on along it.
			double k = ease(since / one);
			Vec3d at = new Vec3d(x0 + 4.0 + 34.0 * k, y0 + 36.0 - 4.0 * k, z0 - 22.0 + 4.0 * k);
			Vec3d look = new Vec3d(x0 + 66.0 + 40.0 * k, y0 - 3.0, z0);
			return look(at, look);
		}
		if (since < two) {
			// Flying down the trench itself, just over its edge, through the village: houses cut in half.
			double k = (since - one) / (two - one);
			Vec3d at = new Vec3d(x0 + 58.0 + 52.0 * k, y0 + 13.0 - 4.0 * k, z0 - 9.0 + 6.0 * k);
			Vec3d look = new Vec3d(x0 + 102.0 + 52.0 * k, y0 + 2.0, z0 + 1.0);
			return look(at, look);
		}
		// Cut: low in the trench at the village end, looking down it at the mountain, the round mouth of the tunnel ahead.
		double k = ease((since - two) / (span - two));
		Vec3d at = new Vec3d(x0 + 104.0 + 12.0 * k, y0 + 3.5, z0 + 0.5);
		Vec3d look = new Vec3d(x0 + 140.0, y0 + 2.5, z0);
		return look(at, look);
	}

	private static Capture.Pose look(Vec3d at, Vec3d look) {
		Vec3d to = look.subtract(at);
		float yaw = (float) (MathHelper.atan2(to.z, to.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) (-(MathHelper.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * MathHelper.DEGREES_PER_RADIAN));
		return new Capture.Pose(at.x, at.y, at.z, yaw, pitch);
	}

	private static double ease(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}

	// =============================================================================================
	// Server: the scene
	// =============================================================================================

	/** How high the ground stands at {@code x, z} (blocks ahead of him, and to his right), above the plain: his hill, the mountain. */
	static int height(int x, int z) {
		double h = 0.0;
		double r = Math.hypot(x, z);
		if (r < 17.0) {
			double k = 1.0 - r / 17.0;
			h = 3.0 * k * k * (3.0 - 2.0 * k) * 1.6;
			h = Math.min(h, 3.0);
		}
		// Rolling meadow and wood: a block or two.
		h += 1.2 * (noise(x * 0.07, z * 0.07) - 0.5) * smooth((x - 12.0) / 14.0) * (1.0 - smooth((x - 150.0) / 10.0));
		double dx = (x - MOUNTAIN_X) / 66.0;
		double dz = (z - MOUNTAIN_Z) / 52.0;
		double m = 1.0 - Math.sqrt(dx * dx + dz * dz);
		if (m > 0.0) {
			double bumps = 0.8 + 0.32 * noise(x * 0.045 + 5.0, z * 0.045) + 0.14 * noise(x * 0.12, z * 0.12 + 9.0);
			h += PEAK * Math.pow(m, 1.25) * bumps;
		}
		return (int) Math.round(h);
	}

	private static double smooth(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}

	private static double hash(int x, int z) {
		long h = x * 374761393L + z * 668265263L;
		h = (h ^ (h >> 13)) * 1274126177L;
		return ((h ^ (h >> 16)) & 0xFFFFFFL) / (double) 0x1000000;
	}

	/** Smooth value noise in two dimensions. */
	private static double noise(double x, double z) {
		int ix = (int) Math.floor(x);
		int iz = (int) Math.floor(z);
		double fx = smooth(x - ix);
		double fz = smooth(z - iz);
		double a = MathHelper.lerp(fx, hash(ix, iz), hash(ix + 1, iz));
		double b = MathHelper.lerp(fx, hash(ix, iz + 1), hash(ix + 1, iz + 1));
		return MathHelper.lerp(fz, a, b);
	}

	private static void setUp(MinecraftServer server) {
		ServerPlayerEntity friend = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = friend.getServerWorld();
		GameRules rules = world.getGameRules();
		rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		rules.get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		rules.get(GameRules.DO_MOB_SPAWNING).set(false, server);
		rules.get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, server);
		rules.get(GameRules.SHOW_DEATH_MESSAGES).set(false, server);
		rules.get(GojoRules.PURPLE_RADIUS).set(RADIUS, server);
		rules.get(GojoRules.PURPLE_RANGE).set(200, server);
		rules.get(GojoRules.PURPLE_ERASES).set(true, server);
		rules.get(GojoRules.PURPLE_COOLDOWN).set(0, server);
		// No outlines round the animals through the trees: the Six Eyes would show in the picture.
		rules.get(GojoRules.SIX_EYES_RANGE).set(0, server);
		world.setTimeOfDay(5200);
		world.setWeather(24000, 0, false, false);
		Owner.set(server, friend.getGameProfile().getName());

		BlockPos plains = REUSE ? remembered() : plains(world);
		int y0 = REUSE ? plains.getY() : Math.max(world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, plains.getX(), plains.getZ()), 66);
		BlockPos base = new BlockPos(plains.getX(), y0, plains.getZ());
		long started = System.currentTimeMillis();
		// The chunks, made and kept loaded.
		for (int cx = (base.getX() + FROM - 16) >> 4; cx <= (base.getX() + TO + 16) >> 4; cx++) {
			for (int cz = (base.getZ() - SIDE - 16) >> 4; cz <= (base.getZ() + SIDE + 16) >> 4; cz++) {
				world.getChunk(cx, cz);
				world.setChunkForced(cx, cz, true);
			}
		}
		ShootingStar.LOGGER.info("[film] chunks made in {} ms", System.currentTimeMillis() - started);
		if (REUSE) {
			ShootingStar.LOGGER.info("[film] the scene as it was left, at {}", base.toShortString());
		} else if (!QUICK) {
			terrain(world, base);
			ShootingStar.LOGGER.info("[film] terrain built at {} ({} ms in all)", base.toShortString(), System.currentTimeMillis() - started);
			forest(world, base);
			village(world, base);
		}
		if (!REUSE) {
			animals(world, base);
		}
		ShootingStar.LOGGER.info("[film] scene built ({} ms in all)", System.currentTimeMillis() - started);

		friend.changeGameMode(GameMode.SURVIVAL);
		friend.getInventory().clear();
		friend.equipStack(EquipmentSlot.HEAD, new ItemStack(Gojolander.BLINDFOLD));
		friend.setHealth(friend.getMaxHealth());
		Vec3d at = new Vec3d(base.getX() + 0.5, y0 + height(0, 0), base.getZ() + 0.5);
		friend.networkHandler.requestTeleport(at.x, at.y, at.z, FACING, PITCH);
		spot = BlockPos.ofFloored(at);
		building = world;
	}

	/**
	 * The scene is shown only once the game has finished lighting it: a hundred thousand blocks changed leave the light engine
	 * hours behind (it takes five tasks a tick), and until it is done the new ground and the mountain are drawn black.
	 */
	private static void lit(MinecraftServer server) {
		ServerWorld world = building;
		if (world == null || ready) {
			return;
		}
		int backlog = backlog(world);
		boolean busy = backlog > 0 || world.getLightingProvider().hasUpdates();
		quiet = busy ? 0 : quiet + 1;
		if (++waited % 400 == 0) {
			ShootingStar.LOGGER.info("[film] lighting: {} tasks to go", backlog);
		}
		if (quiet >= 60 || (QUICK || REUSE) && waited > 60) {
			ShootingStar.LOGGER.info("[film] lit after {} ticks", waited);
			if (!REUSE && !QUICK) {
				keep(server, world);
			}
			ready = true;
		}
	}

	/** Saves the scene, as it is before the cast, for the next take (build/film/purple_world, and where it stands). */
	private static void keep(MinecraftServer server, ServerWorld world) {
		try {
			server.saveAll(true, true, true);
			java.nio.file.Path from = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).normalize();
			java.nio.file.Path to = server.getRunDirectory().toAbsolutePath().normalize().getParent().resolve("film").resolve("purple_world");
			java.nio.file.Files.createDirectories(to.getParent());
			if (java.nio.file.Files.exists(to)) {
				try (var walk = java.nio.file.Files.walk(to)) {
					walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
				}
			}
			try (var walk = java.nio.file.Files.walk(from)) {
				for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) walk::iterator) {
					java.nio.file.Path target = to.resolve(from.relativize(path).toString());
					if (java.nio.file.Files.isDirectory(path)) {
						java.nio.file.Files.createDirectories(target);
					} else if (!path.getFileName().toString().equals("session.lock")) {
						java.nio.file.Files.copy(path, target);
					}
				}
			}
			BlockPos base = spot.add(0, -height(0, 0), 0);
			java.nio.file.Files.writeString(to.getParent().resolve("purple_spot.txt"), base.getX() + " " + base.getY() + " " + base.getZ());
			ShootingStar.LOGGER.info("[film] scene kept in {}", to);
		} catch (java.io.IOException | RuntimeException e) {
			ShootingStar.LOGGER.warn("[film] could not keep the scene", e);
		}
	}

	private static BlockPos remembered() {
		try {
			String[] parts = java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("shootingstar.film.spot"))).trim().split(" ");
			return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		} catch (java.io.IOException | RuntimeException e) {
			throw new IllegalStateException("the kept scene's place", e);
		}
	}

	private static int backlog(ServerWorld world) {
		try {
			java.lang.reflect.Field field = net.minecraft.server.world.ServerLightingProvider.class.getDeclaredField("pendingTasks");
			field.setAccessible(true);
			return ((java.util.Collection<?>) field.get(world.getChunkManager().getLightingProvider())).size();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0;
		}
	}

	/** Somewhere in plains near the spawn, for the grass. */
	private static BlockPos plains(ServerWorld world) {
		BlockPos origin = world.getSpawnPos();
		Pair<BlockPos, RegistryEntry<Biome>> found = world.locateBiome(
				entry -> entry.matchesKey(BiomeKeys.PLAINS) || entry.matchesKey(BiomeKeys.SUNFLOWER_PLAINS), origin.withY(80), 6400, 64, 64);
		return found != null ? found.getFirst() : origin;
	}

	private static BlockState state(int h, int depth, int x, int z) {
		boolean high = h > 11;
		if (depth == 0) {
			if (h > 44) {
				return Blocks.SNOW_BLOCK.getDefaultState();
			}
			if (high) {
				double r = hash(x * 7, z * 13);
				return r < 0.1 ? Blocks.ANDESITE.getDefaultState() : r < 0.16 ? Blocks.COBBLESTONE.getDefaultState()
						: r < 0.2 ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.STONE.getDefaultState();
			}
			return Blocks.GRASS_BLOCK.getDefaultState();
		}
		if (depth <= 3 && !high) {
			return Blocks.DIRT.getDefaultState();
		}
		return Blocks.STONE.getDefaultState();
	}

	/** The land: levelled to the plain with his hill and the mountain on it, the trees and what was there before cleared. */
	private static void terrain(ServerWorld world, BlockPos base) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int y0 = base.getY();
		for (int dx = FROM; dx <= TO; dx++) {
			for (int dz = -SIDE; dz <= SIDE; dz++) {
				int x = base.getX() + dx;
				int z = base.getZ() + dz;
				int h = height(dx, dz);
				int top = y0 + h;
				int natural = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
				int clearTo = Math.min(world.getTopY() - 1, Math.max(natural, top) + 2);
				for (int y = top; y <= clearTo; y++) {
					pos.set(x, y, z);
					if (!world.getBlockState(pos).isAir()) {
						world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2 | 16);
					}
				}
				for (int y = top - 1; y >= Math.max(world.getBottomY(), y0 - 8); y--) {
					pos.set(x, y, z);
					BlockState want = state(h, top - 1 - y, x, z);
					BlockState have = world.getBlockState(pos);
					if (y < top - 5 && !have.isAir() && have.getFluidState().isEmpty()) {
						break;
					}
					if (have != want) {
						world.setBlockState(pos, want, 2 | 16);
					}
				}
			}
		}
	}

	private static void tree(ServerWorld world, BlockPos at, int kind) {
		boolean birch = kind == 1;
		boolean big = kind == 2;
		int trunk = big ? 8 + RANDOM.nextInt(3) : 5 + RANDOM.nextInt(3);
		BlockState log = (birch ? Blocks.BIRCH_LOG : Blocks.OAK_LOG).getDefaultState();
		BlockState leaves = (birch ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES).getDefaultState().with(LeavesBlock.PERSISTENT, true);
		int radius = big ? 3 : 2;
		for (int dy = -radius; dy <= 1; dy++) {
			int r = dy >= 1 ? radius - 1 : dy == 0 ? radius - (big ? 0 : 1) : radius;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) == r && Math.abs(dz) == r && (r > 1 && RANDOM.nextInt(3) == 0 || dy >= 1)) {
						continue;
					}
					BlockPos p = at.add(dx, trunk + dy, dz);
					if (world.getBlockState(p).isAir()) {
						world.setBlockState(p, leaves, 2 | 16);
					}
				}
			}
		}
		for (int y = 0; y < trunk + 1; y++) {
			world.setBlockState(at.up(y), log, 2 | 16);
		}
	}

	/** Trees everywhere in the wood, and flowers in the meadow before it. */
	private static void forest(ServerWorld world, BlockPos base) {
		for (int dx = 26; dx <= 84; dx += 3) {
			for (int dz = -SIDE + 4; dz <= SIDE - 4; dz += 3) {
				if (RANDOM.nextInt(100) >= 62) {
					continue;
				}
				int x = dx + RANDOM.nextInt(3) - 1;
				int z = dz + RANDOM.nextInt(3) - 1;
				BlockPos ground = new BlockPos(base.getX() + x, base.getY() + height(x, z), base.getZ() + z);
				tree(world, ground, RANDOM.nextInt(7) == 0 ? 2 : RANDOM.nextInt(4) == 0 ? 1 : 0);
			}
		}
		for (int dx = 8; dx <= 28; dx++) {
			for (int dz = -20; dz <= 20; dz++) {
				if (RANDOM.nextInt(14) == 0) {
					BlockPos at = new BlockPos(base.getX() + dx, base.getY() + height(dx, dz), base.getZ() + dz);
					BlockState flower = switch (RANDOM.nextInt(4)) {
						case 0 -> Blocks.DANDELION.getDefaultState();
						case 1 -> Blocks.POPPY.getDefaultState();
						case 2 -> Blocks.CORNFLOWER.getDefaultState();
						default -> Blocks.SHORT_GRASS.getDefaultState();
					};
					world.setBlockState(at, flower, 2 | 16);
				}
			}
		}
	}

	/** A village: a street along the way, houses either side, a well in the middle of it, a farm. */
	private static void village(ServerWorld world, BlockPos base) {
		// The street, and the cross street.
		for (int dx = 86; dx <= 134; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				path(world, base, dx, dz);
			}
		}
		for (int dz = -22; dz <= 22; dz++) {
			path(world, base, 110, dz);
			path(world, base, 111, dz);
		}
		for (int row = 0; row < 2; row++) {
			int[] cols = {92, 100, 116, 124};
			for (int i = 0; i < cols.length; i++) {
				int sx = 6 + (i + row) % 3;
				int sz = 6 + (i * 2 + row) % 2;
				int z = row == 0 ? -4 - sz - (i % 2) : 5 + (i % 2);
				house(world, base, cols[i], z, sx, sz, row == 0, (i + row) % 2 == 0);
			}
		}
		// The well.
		for (int dx = -1; dx <= 0; dx++) {
			for (int dz = 0; dz <= 1; dz++) {
				BlockPos at = ground(base, 110 + dx, dz);
				world.setBlockState(at.down(), Blocks.COBBLESTONE.getDefaultState(), 2 | 16);
				world.setBlockState(at, Blocks.COBBLESTONE.getDefaultState(), 2 | 16);
				world.setBlockState(at.up(), Blocks.AIR.getDefaultState(), 2 | 16);
			}
		}
		world.setBlockState(ground(base, 109, 0).down(), Blocks.WATER.getDefaultState(), 2 | 16);
		for (int dy = 1; dy <= 3; dy++) {
			world.setBlockState(ground(base, 109, 0).up(dy), Blocks.OAK_FENCE.getDefaultState(), 2 | 16);
			world.setBlockState(ground(base, 110, 1).up(dy), Blocks.OAK_FENCE.getDefaultState(), 2 | 16);
		}
		world.setBlockState(ground(base, 109, 0).up(4), Blocks.OAK_SLAB.getDefaultState(), 2 | 16);
		world.setBlockState(ground(base, 110, 1).up(4), Blocks.OAK_SLAB.getDefaultState(), 2 | 16);
		world.setBlockState(ground(base, 109, 1).up(4), Blocks.OAK_SLAB.getDefaultState(), 2 | 16);
		world.setBlockState(ground(base, 110, 0).up(4), Blocks.OAK_SLAB.getDefaultState(), 2 | 16);
		// A farm at the village's far end: rows of wheat.
		for (int dx = 128; dx <= 134; dx++) {
			for (int dz = 8; dz <= 18; dz++) {
				BlockPos at = ground(base, dx, dz);
				boolean water = dz == 13;
				world.setBlockState(at.down(), water ? Blocks.WATER.getDefaultState() : Blocks.FARMLAND.getDefaultState(), 2 | 16);
				if (!water) {
					world.setBlockState(at, Blocks.WHEAT.getDefaultState().with(net.minecraft.block.CropBlock.AGE, 6 + RANDOM.nextInt(2)), 2 | 16);
				}
			}
		}
	}

	private static BlockPos ground(BlockPos base, int dx, int dz) {
		return new BlockPos(base.getX() + dx, base.getY() + height(dx, dz), base.getZ() + dz);
	}

	private static void path(ServerWorld world, BlockPos base, int dx, int dz) {
		world.setBlockState(ground(base, dx, dz).down(), Blocks.DIRT_PATH.getDefaultState(), 2 | 16);
	}

	/** A house {@code sx} along the way by {@code sz} across, its floor at the ground: planks over cobblestone, a gable roof, a door to the street. */
	private static void house(ServerWorld world, BlockPos base, int dx, int dz, int sx, int sz, boolean north, boolean stone) {
		BlockPos o = ground(base, dx, dz);
		BlockState walls = (stone ? Blocks.COBBLESTONE : Blocks.OAK_PLANKS).getDefaultState();
		BlockState log = Blocks.OAK_LOG.getDefaultState();
		int wallHeight = 4;
		for (int x = 0; x < sx; x++) {
			for (int z = 0; z < sz; z++) {
				world.setBlockState(o.add(x, -1, z), Blocks.COBBLESTONE.getDefaultState(), 2 | 16);
				boolean edgeX = x == 0 || x == sx - 1;
				boolean edgeZ = z == 0 || z == sz - 1;
				for (int y = 0; y < wallHeight; y++) {
					BlockState s;
					if (edgeX && edgeZ) {
						s = log;
					} else if (edgeX || edgeZ) {
						boolean window = y == 1 || y == 2 ? (edgeZ && x % 3 == 1) || (edgeX && z % 3 == 1) : false;
						s = window ? Blocks.GLASS.getDefaultState() : y < 1 ? Blocks.COBBLESTONE.getDefaultState() : walls;
					} else {
						s = Blocks.AIR.getDefaultState();
					}
					world.setBlockState(o.add(x, y, z), s, 2 | 16);
				}
				world.setBlockState(o.add(x, wallHeight + 3, z), Blocks.AIR.getDefaultState(), 2 | 16);
			}
		}
		// The door, in the wall that faces the street.
		int doorZ = north ? sz - 1 : 0;
		int doorX = sx / 2;
		Direction face = north ? Direction.SOUTH : Direction.NORTH;
		world.setBlockState(o.add(doorX, 0, doorZ), Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, face).with(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2 | 16);
		world.setBlockState(o.add(doorX, 1, doorZ), Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, face).with(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2 | 16);
		// A gable roof along the way: stairs rising to a ridge.
		int layers = (sz + 1) / 2;
		for (int i = 0; i < layers; i++) {
			int y = wallHeight + i;
			for (int x = -1; x <= sx; x++) {
				int zl = i - 1;
				int zr = sz - i;
				if (zl <= zr) {
					world.setBlockState(o.add(x, y, zl), Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH).with(StairsBlock.HALF, BlockHalf.BOTTOM), 2 | 16);
				}
				if (zr > zl) {
					world.setBlockState(o.add(x, y, zr), Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH).with(StairsBlock.HALF, BlockHalf.BOTTOM), 2 | 16);
				}
				for (int z = zl + 1; z < zr; z++) {
					world.setBlockState(o.add(x, y, z), Blocks.OAK_PLANKS.getDefaultState(), 2 | 16);
				}
			}
		}
		world.setBlockState(o.add(1, 0, 1), Blocks.CRAFTING_TABLE.getDefaultState(), 2 | 16);
		world.setBlockState(o.add(sx - 2, 0, sz - 2), Blocks.RED_BED.getDefaultState(), 2 | 16);
	}

	/** Villagers in the street and about the houses, an iron golem, animals in the meadow and the wood: all in its way. */
	private static void animals(ServerWorld world, BlockPos base) {
		double[][] villagers = {{96, -1.5}, {100, 1.5}, {104, -0.5}, {108, 1.0}, {113, -1.2}, {117, 0.8}, {121, -0.3}, {126, 1.2}, {104, 8}, {120, -8}};
		for (double[] v : villagers) {
			mob(world, base, EntityType.VILLAGER, v[0], v[1]);
		}
		mob(world, base, EntityType.IRON_GOLEM, 99.0, 0.5);
		mob(world, base, EntityType.IRON_GOLEM, 115.0, -2.5);
		double[][] herd = {{22, 1.5}, {24, -2.0}, {20, 3.0}, {30, 0.5}, {36, -3}, {44, 1}, {52, -1.5}, {60, 2}, {70, -0.5}, {78, 1}};
		EntityType<?>[] kinds = {EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.SHEEP, EntityType.COW, EntityType.PIG,
				EntityType.CHICKEN, EntityType.SHEEP, EntityType.COW, EntityType.WOLF};
		for (int i = 0; i < herd.length; i++) {
			mob(world, base, kinds[i], herd[i][0], herd[i][1]);
		}
	}

	private static void mob(ServerWorld world, BlockPos base, EntityType<?> type, double dx, double dz) {
		BlockPos feet = ground(base, (int) Math.floor(dx), (int) Math.floor(dz));
		if (!(type.create(world) instanceof MobEntity mob)) {
			return;
		}
		float yaw = RANDOM.nextFloat() * 360.0F;
		mob.refreshPositionAndAngles(base.getX() + dx + 0.5, feet.getY(), base.getZ() + dz + 0.5, yaw, 0.0F);
		mob.setHeadYaw(yaw);
		mob.setBodyYaw(yaw);
		mob.setPersistent();
		mob.initialize(world, world.getLocalDifficulty(feet), SpawnReason.COMMAND, null);
		mob.setAiDisabled(true);
		world.spawnEntity(mob);
	}
}
