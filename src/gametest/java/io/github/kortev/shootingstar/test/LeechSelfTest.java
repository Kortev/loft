package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.devil.Devils;
import io.github.kortev.chainsawman.devil.LeechDevilEntity;
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
 * With -Dshootingstar.selftest=leech: the Leech Devil, on levelled open grass at dusk.
 * <ul>
 * <li>-Ptarget=model (the default): stills to judge it by: from the front, the side, the back and three-quarter, its face close, and each move's
 * tell and blow, and dying.</li>
 * <li>-Ptarget=played: the fight played from your eyes ({@link LeechFight}); with -Pcapture=true, the film.</li>
 * <li>-Ptarget=played-iron / played-stone: one fight from first sight to its death with that sword, logged, no film.</li>
 * </ul>
 * <pre>./gradlew runSelftest -Pselftest=leech -Ptarget=played -Pusername=Kortev -Pcapture=true -Pwidth=1080 -Pheight=1920 "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"</pre>
 */
public class LeechSelfTest implements ClientModInitializer {
	static final BlockPos SITE = new BlockPos(288, 62, -256);
	static final int GROUND = 62;
	static final long DUSK = 11900L;

	private enum Stage {
		WAIT_WORLD, SETUP, SETTLE, RUN, DONE
	}

	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static volatile boolean ready;
	private static volatile int devilId = -1;

	public static boolean active() {
		return "leech".equals(System.getProperty("shootingstar.selftest"));
	}

	/** The played take is drawn as a phone's portrait frame (1080x1920) whatever the window. */
	public static boolean portrait() {
		return active() && target().startsWith("played");
	}

	static String target() {
		String t = System.getProperty("shootingstar.selftest.target", "");
		return t.isEmpty() ? "model" : t;
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
				Thread.sleep(30 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[selftest] leech timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-leech-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		if (capturing()) {
			ServerTickEvents.START_SERVER_TICK.register(server -> Capture.serverTickStart());
			ServerTickEvents.END_SERVER_TICK.register(server -> Capture.serverTickEnd());
		}
		ServerTickEvents.END_SERVER_TICK.register(LeechFight::snap);
		ClientTickEvents.END_CLIENT_TICK.register(LeechSelfTest::tick);
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
				boolean over = target().startsWith("played") ? LeechFight.tick(client, server, ticks) : model(client, server, ticks);
				if (over) {
					stage = Stage.DONE;
				}
			}
			case DONE -> {
				Capture.stop();
				Capture.camera = null;
				ShootingStar.LOGGER.info("[selftest] leech: done");
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

	/** The site: levelled open grass 34 blocks round, dusk held, nothing else alive, you in survival with an iron sword and no shield. */
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
		if (Form.on(you)) {
			Form.end(you);
		}
		Downed.up(you);
		you.changeGameMode(target().startsWith("played") ? GameMode.SURVIVAL : GameMode.CREATIVE);
		you.getInventory().clear();
		you.getInventory().setStack(0, new ItemStack(target().equals("played-stone") ? Items.STONE_SWORD : Items.IRON_SWORD));
		you.getInventory().setStack(1, new ItemStack(Items.BREAD, 8));
		you.getInventory().selectedSlot = 0;
		you.setHealth(you.getMaxHealth());
		you.getHungerManager().setFoodLevel(20);
		Blood.set(you, Blood.MAX);
		Vec3d at = site(0.0, 0.0, -14.0);
		you.teleport(world, at.x, at.y, at.z, 0.0F, 0.0F);
		ready = true;
		ShootingStar.LOGGER.info("[selftest] leech: site at {}", SITE);
	}

	/** A devil put down at a point of the site (as its spawn egg does), facing {@code yaw}. */
	static LeechDevilEntity devil(ServerWorld world, Vec3d at, float yaw) {
		LeechDevilEntity d = Devils.LEECH_DEVIL.create(world);
		d.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0F);
		d.setHeadYaw(yaw);
		d.setBodyYaw(yaw);
		d.aimAt(yaw);
		d.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(at)), SpawnReason.SPAWN_EGG, null);
		world.spawnEntity(d);
		return d;
	}

	static void still(MinecraftClient c, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot leech_{}", name);
		ScreenshotRecorder.saveScreenshot(c.runDirectory, "leech_" + name + ".png", c.getFramebuffer(), m -> {
		});
	}

	private static LeechDevilEntity serverDevil(IntegratedServer s) {
		return s.getOverworld().getEntityById(devilId) instanceof LeechDevilEntity d ? d : null;
	}

	/**
	 * -Ptarget=model: the devil standing still (its face on you), from the front, the side, the back and three-quarter (7 to 8 blocks), its
	 * face from 4; then its moves from three-quarter: the tongue's tell and lance, the latch's coil and whip, the trample's tell; dying.
	 */
	private static boolean model(MinecraftClient c, IntegratedServer s, int f) {
		c.options.hudHidden = true;
		c.options.setPerspective(Perspective.FIRST_PERSON);
		c.options.getFov().setValue(60);
		Vec3d p = site(0.0, 0.0, 0.0);
		if (f == 1) {
			s.execute(() -> {
				LeechDevilEntity d = devil(s.getOverworld(), p, 180.0F);
				d.setAiDisabled(true);
				d.notice(true);
				devilId = d.getId();
			});
		}
		// name, camera offset from its feet (x, y, z), yaw, pitch (it faces -Z)
		Object[][] shots = {
				{"front", 0.0, 2.2, -7.5, 0.0F, 6.0F}, {"side", 8.5, 2.0, 0.0, 90.0F, 5.0F}, {"back", 0.0, 2.6, 8.5, 180.0F, 8.0F},
				{"threequarter", 5.6, 2.6, -5.6, 45.0F, 10.0F}, {"face", 0.0, 1.75, -4.6, 0.0F, 0.0F}};
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
		// The moves, from three-quarter, a little further back.
		int[][] moves = {{LeechDevilEntity.TONGUE, 10, 18, 24}, {LeechDevilEntity.LATCH, 11, 15, 18}, {LeechDevilEntity.TRAMPLE_TELL, 6, 12, 15}, {-1, 15, 50, 85}};
		for (int[] mv : moves) {
			int st = mv[0];
			if (f == at - 20) {
				Capture.camera = t -> new Capture.Pose(p.x + 6.6, p.y + 2.6, p.z - 6.6, 45.0F, 9.0F);
				s.execute(() -> {
					ServerWorld w = s.getOverworld();
					for (LeechDevilEntity e : w.getEntitiesByType(Devils.LEECH_DEVIL, e -> true)) {
						e.discard();
					}
					LeechDevilEntity d = devil(w, p, 180.0F);
					// (its mind on, so the move plays out as it would; told to stand where it is in between)
					d.walkTo = p;
					devilId = d.getId();
					you(s).teleport(w, p.x + 6.6, p.y + 2.6 - 1.62, p.z - 6.6, 45.0F, 9.0F);
				});
			}
			if (f == at) {
				s.execute(() -> {
					LeechDevilEntity d = serverDevil(s);
					if (d == null) {
						return;
					}
					if (st >= 0) {
						d.aimAt(180.0F);
						d.begin(st);
					} else {
						d.damage(d.getDamageSources().generic(), 1000.0F);
					}
				});
			}
			String name = st == LeechDevilEntity.TONGUE ? "tongue" : st == LeechDevilEntity.LATCH ? "latch" : st == LeechDevilEntity.TRAMPLE_TELL ? "trample" : "death";
			for (int k = 1; k < mv.length; k++) {
				if (f == at + mv[k]) {
					still(c, "pose_" + name + "_" + mv[k]);
				}
			}
			at += 120;
		}
		return f > at + 10;
	}
}
