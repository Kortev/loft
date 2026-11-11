package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.devil.Devils;
import io.github.kortev.chainsawman.devil.TomatoDevilEntity;
import io.github.kortev.chainsawman.form.Blood;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.shootingstar.ShootingStar;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

/**
 * With -Dshootingstar.selftest=tomato: the Tomato Devil, on a levelled plains at dusk with two oaks.
 * <ul>
 * <li>-Ptarget=look (the default): stills to judge it by: its walk seen from the side (one cycle, every two ticks), and each move's tell
 * and blow from the side and from the front, on its back, dying.</li>
 * <li>-Ptarget=played: the fight played from your eyes ({@link TomatoFight}); with -Pcapture=true, the film.</li>
 * <li>-Ptarget=cinematic: the horror short's eight shots ({@link TomatoCinematic}); with -Pcapture=true, its frames.</li>
 * <li>-Ptarget=seeds: one still of three seeds, whole, cut once, cut twice.</li>
 * <li>-Ptarget=firstnight: a Tomato Devil's arrival played from your eyes by a dirt hut as night falls, with a stone sword and torches, and
 * the fight to its death and its seeds ({@link TomatoFirstNight}); with -Pcapture=true, the film.</li>
 * </ul>
 * <pre>./gradlew runSelftest -Pselftest=tomato -Ptarget=played -Pusername=Kortev -Pcapture=true -Pwidth=1080 -Pheight=1920 "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"</pre>
 */
public class TomatoSelfTest implements ClientModInitializer {
	/** A plains (the hotel's site, which every take shares) and its ground. */
	static final BlockPos SITE = new BlockPos(288, 62, -256);
	static final int GROUND = 62;
	/** Dusk: the sun low, every frame still lit. */
	static final long DUSK = 11900L;
	/** The two oaks the roll can be baited into (offsets from the site). */
	static final int[][] OAKS = {{6, 9}, {-9, -4}};

	private enum Stage {
		WAIT_WORLD, SETUP, SETTLE, RUN, DONE
	}

	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static volatile boolean ready;
	private static volatile int devilId = -1;

	public static boolean active() {
		return "tomato".equals(System.getProperty("shootingstar.selftest"));
	}

	/** The played take is drawn as a phone's portrait frame (1080x1920) whatever the window. */
	public static boolean portrait() {
		return active() && (target().startsWith("played") || target().equals("cinematic") || target().equals("firstnight"));
	}

	static String target() {
		String t = System.getProperty("shootingstar.selftest.target", "");
		return t.isEmpty() ? "look" : t;
	}

	static boolean capturing() {
		return "true".equals(System.getProperty("shootingstar.selftest.capture"));
	}

	@Override
	public void onInitializeClient() {
		if (!active()) {
			return;
		}
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(25 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[selftest] tomato timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-tomato-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		if (capturing()) {
			ServerTickEvents.START_SERVER_TICK.register(server -> Capture.serverTickStart());
			ServerTickEvents.END_SERVER_TICK.register(server -> Capture.serverTickEnd());
		}
		ServerTickEvents.END_SERVER_TICK.register(TomatoFight::snap);
		ClientTickEvents.END_CLIENT_TICK.register(TomatoSelfTest::tick);
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
				if (client.player != null && !client.player.isAlive() && ticks % 20 == 0) {
					client.player.requestRespawn();
				}
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
				if (ready && ticks > 160) {
					stage = Stage.RUN;
					ticks = 0;
				}
			}
			case RUN -> {
				client.getToastManager().clear();
				boolean over;
				if (target().startsWith("played")) {
					over = TomatoFight.tick(client, server, ticks);
				} else if (target().equals("cinematic")) {
					over = TomatoCinematic.tick(client, server, ticks);
				} else if (target().equals("firstnight")) {
					over = TomatoFirstNight.tick(client, server, ticks);
				} else if (target().equals("model")) {
					over = model(client, server, ticks);
				} else if (target().equals("seeds")) {
					over = seeds(client, server, ticks);
				} else {
					over = look(client, server, ticks);
				}
				if (over) {
					stage = Stage.DONE;
				}
			}
			case DONE -> {
				Capture.stop();
				Capture.camera = null;
				ShootingStar.LOGGER.info("[selftest] tomato: done");
				stage = null;
				client.scheduleStop();
			}
			default -> {
			}
		}
	}

	static Vec3d site(double dx, double dy, double dz) {
		return new Vec3d(SITE.getX() + 0.5 + dx, GROUND + 1 + dy, SITE.getZ() + 0.5 + dz);
	}

	static ServerPlayerEntity you(IntegratedServer s) {
		return s.getPlayerManager().getPlayerList().get(0);
	}

	private static int surface(ServerWorld world, int x, int z) {
		int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
		while (y > world.getBottomY()) {
			BlockState s = world.getBlockState(new BlockPos(x, y, z));
			if (s.isAir() || s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.LOGS) || s.isReplaceable() && s.getFluidState().isEmpty() || s.isOf(Blocks.SNOW)) {
				y--;
			} else {
				break;
			}
		}
		return y;
	}

	/** The site: levelled grass 34 blocks round, two oaks, dusk held, nothing else alive, you in survival with a sword and torches. */
	static void setup(IntegratedServer server) {
		ServerWorld world = server.getOverworld();
		ServerPlayerEntity you = you(server);
		server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_GRIEFING).set(false, server);
		server.setDifficulty(Difficulty.NORMAL, true);
		world.setTimeOfDay(DUSK);
		world.setWeather(12000, 0, false, false);
		int x0 = SITE.getX();
		int z0 = SITE.getZ();
		for (int cx = (x0 - 48) >> 4; cx <= (x0 + 48) >> 4; cx++) {
			for (int cz = (z0 - 48) >> 4; cz <= (z0 + 48) >> 4; cz++) {
				world.setChunkForced(cx, cz, true);
				world.getChunk(cx, cz);
			}
		}
		for (Entity e : world.getOtherEntities(you, new Box(SITE).expand(80.0, 60.0, 80.0))) {
			if (!(e instanceof ServerPlayerEntity)) {
				e.discard();
			}
		}
		for (int x = x0 - 40; x <= x0 + 40; x++) {
			for (int z = z0 - 40; z <= z0 + 40; z++) {
				double d = Math.max(0.0, Math.max(Math.abs(x - x0), Math.abs(z - z0)) - 26.0);
				int natural = surface(world, x, z);
				int want = (int) Math.round(GROUND + (natural - GROUND) * Math.min(1.0, d / 14.0));
				for (int y = Math.min(natural, want) + 1; y <= Math.max(natural, want) + 16; y++) {
					BlockPos p = new BlockPos(x, y, z);
					if (!world.getBlockState(p).isAir()) {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
				for (int y = want - 3; y <= want; y++) {
					world.setBlockState(new BlockPos(x, y, z), y == want ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
		if (target().equals("cinematic")) {
			TomatoCinematic.build(world);
			TomatoCinematic.sleeper(world);
		} else if (target().equals("firstnight")) {
			TomatoFirstNight.hut(world);
		} else {
			for (int[] o : OAKS) {
				oak(world, new BlockPos(x0 + o[0], GROUND + 1, z0 + o[1]));
			}
		}
		if (Form.on(you)) {
			Form.end(you);
		}
		Downed.up(you);
		you.changeGameMode(target().startsWith("played") || target().equals("firstnight") ? GameMode.SURVIVAL : target().equals("model") || target().equals("cinematic") ? GameMode.CREATIVE : GameMode.SPECTATOR);
		you.getInventory().clear();
		you.getInventory().setStack(0, new ItemStack(target().equals("firstnight") ? Items.STONE_SWORD : Items.IRON_SWORD));
		you.getInventory().setStack(1, new ItemStack(Items.TORCH, 16));
		you.getInventory().setStack(2, new ItemStack(Items.BREAD, 8));
		you.getInventory().selectedSlot = 0;
		you.setHealth(you.getMaxHealth());
		you.getHungerManager().setFoodLevel(20);
		Blood.set(you, Blood.MAX);
		Vec3d at = site(0.0, 0.0, -14.0);
		you.teleport(world, at.x, at.y, at.z, 0.0F, 0.0F);
		ready = true;
		ShootingStar.LOGGER.info("[selftest] tomato: site at {}", SITE);
	}

	private static void oak(ServerWorld world, BlockPos base) {
		for (int y = 0; y < 6; y++) {
			world.setBlockState(base.up(y), Blocks.OAK_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = 3; dy <= 6; dy++) {
					int r = dy >= 5 ? 1 : 2;
					if (Math.abs(dx) <= r && Math.abs(dz) <= r && !(Math.abs(dx) == r && Math.abs(dz) == r && dy != 4)) {
						BlockPos p = base.add(dx, dy, dz);
						if (world.getBlockState(p).isAir()) {
							world.setBlockState(p, Blocks.OAK_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true), Block.NOTIFY_LISTENERS);
						}
					}
				}
			}
		}
	}

	/** A devil put down at a point of the site (as its spawn egg does), facing {@code yaw}. */
	static TomatoDevilEntity devil(ServerWorld world, Vec3d at, float yaw) {
		TomatoDevilEntity d = Devils.TOMATO_DEVIL.create(world);
		d.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0F);
		d.setHeadYaw(yaw);
		d.setBodyYaw(yaw);
		d.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(at)), SpawnReason.SPAWN_EGG, null);
		world.spawnEntity(d);
		return d;
	}

	static void still(MinecraftClient c, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot tomato_{}", name);
		ScreenshotRecorder.saveScreenshot(c.runDirectory, "tomato_" + name + ".png", c.getFramebuffer(), m -> {
		});
	}

	private static TomatoDevilEntity clientDevil(MinecraftClient c) {
		return devilId >= 0 && c.world.getEntityById(devilId) instanceof TomatoDevilEntity d ? d : null;
	}

	/**
	 * -Ptarget=model: the body judged on its own (it stands still, every eye on you): front, side and three-quarter from 4 and from 10 blocks
	 * (at your eyes' height); its eyes at night from 10 blocks (eyeshine); its face from 2 blocks; its eyes wandering before it notices you;
	 * the hop's squat and leap; dying on its back; the burst's chunks and the seeds glowing at night.
	 */
	private static boolean model(MinecraftClient c, IntegratedServer s, int f) {
		c.options.hudHidden = true;
		c.options.setPerspective(Perspective.FIRST_PERSON);
		c.options.getFov().setValue(70);
		Vec3d p = site(0.0, 0.0, 0.0);
		double eyeY = 2.45;
		if (f == 1) {
			s.execute(() -> {
				TomatoDevilEntity d = devil(s.getOverworld(), p, 180.0F);
				d.setAiDisabled(true);
				d.notice(true);
				devilId = d.getId();
			});
		}
		// name, camera offset from its feet (x, y, z), yaw, pitch
		Object[][] shots = {
				{"front_4", 0.0, eyeY, -4.0, 0.0F, 0.0F}, {"side_4", 4.0, eyeY, 0.0, 90.0F, 0.0F}, {"threequarter_4", 2.83, eyeY + 0.3, -2.83, 45.0F, 5.0F},
				{"front_10", 0.0, 1.62, -10.0, 0.0F, -4.0F}, {"side_10", 10.0, 1.62, 0.0, 90.0F, -4.0F}, {"threequarter_10", 7.07, 1.62, -7.07, 45.0F, -4.0F},
				{"face_2", 0.0, eyeY, -3.3, 0.0F, 0.0F}};
		int at = 60;
		for (Object[] sh : shots) {
			if (f >= at - 25 && f <= at) {
				double x = (double) sh[1];
				double y = (double) sh[2];
				double z = (double) sh[3];
				float yaw = (float) sh[4];
				float pitch = (float) sh[5];
				Capture.camera = t -> new Capture.Pose(p.x + x, p.y + y, p.z + z, yaw, pitch);
				if (f == at - 25) {
					s.execute(() -> you(s).teleport(s.getOverworld(), p.x + x, p.y + y - 1.62, p.z + z, yaw, pitch));
				}
			}
			if (f == at) {
				still(c, "model_" + sh[0]);
			}
			at += 30;
		}
		// eyes wandering: it has noticed nobody
		if (f == at - 25) {
			s.execute(() -> {
				if (s.getOverworld().getEntityById(devilId) instanceof TomatoDevilEntity d) {
					d.notice(false);
				}
				you(s).teleport(s.getOverworld(), p.x + 3.0, p.y, p.z - 5.5, 30.0F, 10.0F);
			});
			Capture.camera = t -> new Capture.Pose(p.x + 3.0, p.y + 2.6, p.z - 5.5, 30.0F, 6.0F);
		}
		if (f == at - 8 || f == at) {
			still(c, "model_wander_" + (f == at ? "b" : "a"));
		}
		if (f == at + 4) {
			s.execute(() -> {
				if (s.getOverworld().getEntityById(devilId) instanceof TomatoDevilEntity d) {
					d.notice(true);
				}
			});
		}
		if (f == at + 9) {
			still(c, "model_snap");
		}
		at += 40;
		// night: the eyes from 10 blocks
		if (f == at - 25) {
			s.execute(() -> {
				s.getOverworld().setTimeOfDay(18000L);
				you(s).teleport(s.getOverworld(), p.x, p.y, p.z - 10.0, 0.0F, -4.0F);
			});
			Capture.camera = t -> new Capture.Pose(p.x, p.y + 1.62, p.z - 10.0, 0.0F, -4.0F);
		}
		if (f == at) {
			still(c, "model_night_10");
		}
		at += 30;
		// back to dusk; the hop, from the side
		if (f == at - 25) {
			s.execute(() -> {
				s.getOverworld().setTimeOfDay(DUSK);
				you(s).teleport(s.getOverworld(), p.x + 8.0, p.y, p.z, 90.0F, 0.0F);
			});
			Capture.camera = t -> new Capture.Pose(p.x + 8.0, p.y + 2.0, p.z, 90.0F, 3.0F);
		}
		if (f == at) {
			s.execute(() -> {
				if (s.getOverworld().getEntityById(devilId) instanceof TomatoDevilEntity d) {
					d.setAiDisabled(false);
					d.begin(TomatoDevilEntity.HOP);
				}
			});
		}
		for (int k : new int[] {14, 21, 24, 30, 36}) {
			if (f == at + k) {
				still(c, "model_hop_" + k);
			}
		}
		at += 70;
		// on its back, killed there
		if (f == at) {
			s.execute(() -> {
				if (s.getOverworld().getEntityById(devilId) instanceof TomatoDevilEntity d) {
					d.begin(TomatoDevilEntity.ON_BACK);
				}
			});
		}
		if (f == at + 12) {
			s.execute(() -> {
				if (s.getOverworld().getEntityById(devilId) instanceof TomatoDevilEntity d) {
					d.damage(d.getDamageSources().generic(), 1000.0F);
				}
			});
		}
		for (int k : new int[] {10, 22, 30, 38, 46}) {
			if (f == at + k) {
				still(c, "model_backdeath_" + k);
			}
		}
		// the burst's chunks, and (at night) the seeds glowing
		for (int k : new int[] {50, 56, 70}) {
			if (f == at + k) {
				still(c, "model_burst_" + k);
			}
		}
		if (f == at + 130) {
			s.execute(() -> s.getOverworld().setTimeOfDay(18000L));
			Capture.camera = t -> new Capture.Pose(p.x + 8.0, p.y + 2.5, p.z, 90.0F, 15.0F);
		}
		if (f == at + 150) {
			still(c, "model_seeds_night");
		}
		return f > at + 160;
	}

	/** -Ptarget=seeds: three seeds side by side, left to right whole, cut once, cut twice; one still, close, at dusk. */
	private static boolean seeds(MinecraftClient c, IntegratedServer s, int f) {
		c.options.hudHidden = true;
		c.options.setPerspective(Perspective.FIRST_PERSON);
		c.options.getFov().setValue(50);
		Vec3d p = site(0.0, 0.0, 0.0);
		if (f == 1) {
			s.execute(() -> {
				for (int i = 0; i < 3; i++) {
					var seed = new io.github.kortev.chainsawman.devil.TomatoSeedEntity(Devils.TOMATO_SEED, s.getOverworld());
					seed.refreshPositionAndAngles(p.x + 0.75 * (1 - i), p.y + 0.2, p.z, 0.0F, 0.0F);
					seed.index = 1;
					s.getOverworld().spawnEntity(seed);
				}
				you(s).teleport(s.getOverworld(), p.x, p.y + 1.1 - 1.62, p.z - 1.6, 0.0F, 34.0F);
			});
			Capture.camera = t -> new Capture.Pose(p.x, p.y + 1.1, p.z - 1.6, 0.0F, 34.0F);
		}
		if (f == 30) {
			s.execute(() -> {
				var list = s.getOverworld().getEntitiesByClass(io.github.kortev.chainsawman.devil.TomatoSeedEntity.class, new Box(p, p).expand(3.0), e -> true);
				for (var seed : list) {
					int cuts = (int) Math.round((p.x + 0.75 - seed.getX()) / 0.75);
					for (int k = 0; k < cuts; k++) {
						seed.cut();
					}
				}
			});
		}
		if (f == 60) {
			still(c, "seed_cuts");
		}
		return f > 70;
	}

	/** The stills: the walk from the side, then each pose. */
	private static boolean look(MinecraftClient c, IntegratedServer s, int f) {
		c.options.hudHidden = true;
		c.options.setPerspective(Perspective.FIRST_PERSON);
		c.options.getFov().setValue(50);
		if (f == 1) {
			s.execute(() -> {
				TomatoDevilEntity d = devil(s.getOverworld(), site(-12.0, 0.0, 0.0), -90.0F);
				d.walkTo = site(14.0, 0.0, 0.0);
				devilId = d.getId();
			});
		}
		TomatoDevilEntity d = clientDevil(c);
		if (d != null && f < 140) {
			// Side on, from a fixed place it walks past (so a hand that slid would show against the grass).
			Vec3d p = d.getLerpedPos(c.getRenderTickCounter().getTickDelta(true));
			Vec3d cam = site(0.0, 2.0, -8.0);
			Capture.camera = t -> new Capture.Pose(cam.x, cam.y, cam.z, 0.0F, 3.0F);
			if (f >= 70 && f < 82 && f % 2 == 0) {
				still(c, "walk_" + (f - 70) / 2);
			}
			if (f == 90) {
				// three-quarter from the front
				Capture.camera = t -> new Capture.Pose(p.x + 5.5, p.y + 2.2, p.z - 5.5, 45.0F, 10.0F);
			}
			if (f == 92) {
				still(c, "walk_front");
			}
		}
		// The poses: it stands in the middle facing the camera's right (side on), each move begun and caught at its moments.
		int[][] shots = {
				{160, TomatoDevilEntity.SLAP}, {300, TomatoDevilEntity.ROLL_TELL}, {420, TomatoDevilEntity.ON_BACK}, {520, TomatoDevilEntity.SPIT},
				{640, TomatoDevilEntity.EMERGE}, {760, -1}};
		for (int[] sh : shots) {
			int at = sh[0];
			int st = sh[1];
			if (f == at - 20) {
				s.execute(() -> {
					ServerWorld w = s.getOverworld();
					for (TomatoDevilEntity e : w.getEntitiesByType(Devils.TOMATO_DEVIL, e -> true)) {
						e.discard();
					}
					TomatoDevilEntity e = devil(w, site(0.0, 0.0, 0.0), -90.0F);
					devilId = e.getId();
				});
			}
			if (f == at) {
				s.execute(() -> {
					if (s.getOverworld().getEntityById(devilId) instanceof TomatoDevilEntity e) {
						if (st >= 0) {
							e.aimAt(-90.0F);
							e.begin(st);
						} else {
							e.damage(e.getDamageSources().generic(), 1000.0F);
						}
					}
				});
			}
			if (f >= at - 18 && f < at + 100) {
				Vec3d p = site(0.0, 0.0, 0.0);
				boolean front = (f >= at + 50 || st == TomatoDevilEntity.SPIT) && st != TomatoDevilEntity.ROLL_TELL;
				Capture.camera = front ? t -> new Capture.Pose(p.x + 6.5, p.y + 2.2, p.z - 1.8, 74.0F, 6.0F)
						: t -> new Capture.Pose(p.x, p.y + 2.0, p.z - 7.5, 0.0F, 3.0F);
			}
			String name = st < 0 ? "death" : st == TomatoDevilEntity.SLAP ? "slap" : st == TomatoDevilEntity.ROLL_TELL ? "roll" : st == TomatoDevilEntity.ON_BACK ? "back"
					: st == TomatoDevilEntity.SPIT ? "spit" : "emerge";
			int[] when = st == TomatoDevilEntity.SLAP ? new int[] {8, 15, 19, 26, 58, 66, 70} : st == TomatoDevilEntity.ROLL_TELL ? new int[] {6, 12, 17, 22, 28}
					: st == TomatoDevilEntity.ON_BACK ? new int[] {4, 16, 30, 60, 75} : st == TomatoDevilEntity.SPIT ? new int[] {7, 13, 16, 63, 64}
					: st == TomatoDevilEntity.EMERGE ? new int[] {4, 10, 18, 26, 64} : new int[] {3, 8, 13, 15, 18};
			for (int k : when) {
				if (f == at + k) {
					still(c, name + "_" + k);
				}
			}
		}
		return f > 880;
	}
}
