package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.devil.Devils;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.safety.BoardBlock;
import io.github.kortev.chainsawman.safety.Safety;
import io.github.kortev.chainsawman.safety.SafetyLedger;
import io.github.kortev.shootingstar.ShootingStar;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestTypes;
import org.lwjgl.glfw.GLFW;

/**
 * With -Dshootingstar.selftest=safety: the village notice board played from your eyes, first person, the HUD on, the normal lens, by day,
 * in a real village of the self-test world (the nearest to the hotel's plains). You walk into the village, see the board by the bell (the
 * mod put it up as the village came near), read the poster and take a licence from it, look at the licence, then use the phone with a
 * Tomato carcass in hand: the call, the payday, and the licence (by its key, J) shows the yen. Staged: one Tomato already hunted (where
 * the carcass came from), where you start (in front of the board, about twenty blocks off), and the moments you stop to look.
 * <pre>./gradlew runSelftest -Pselftest=safety -Pusername=Kortev -Pcapture=true -Pwidth=1080 -Pheight=1920 "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"</pre>
 */
public class SafetySelfTest implements ClientModInitializer {
	static final BlockPos FROM = new BlockPos(288, 64, -256);
	static final long DAY = 3500L;
	static final double START_BACK = 22.0;

	private enum Stage {
		WAIT_WORLD, SETUP, SETTLE, FIND, RUN, DONE
	}

	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static volatile boolean ready;
	private static volatile BlockPos bell;
	private static volatile BlockPos poster;
	private static volatile BlockPos phone;
	private static volatile Direction facing;
	private static final Fighter YOU = new Fighter();
	private static int step;
	private static int sub;

	public static boolean active() {
		return "safety".equals(System.getProperty("shootingstar.selftest"));
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
			ShootingStar.LOGGER.error("[selftest] safety timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-safety-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		if (capturing()) {
			ServerTickEvents.START_SERVER_TICK.register(server -> Capture.serverTickStart());
			ServerTickEvents.END_SERVER_TICK.register(server -> Capture.serverTickEnd());
		}
		ClientTickEvents.END_CLIENT_TICK.register(SafetySelfTest::tick);
	}

	private static void tick(MinecraftClient client) {
		TestWindow.keepToItself(client);
		ticks++;
		IntegratedServer server = client.getServer();
		client.options.pauseOnLostFocus = false;
		if (stage == null) {
			return;
		}
		if ((stage == Stage.SETTLE || stage == Stage.FIND) && client.currentScreen != null) {
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
				if (ready && ticks > 120) {
					server.execute(() -> find(server));
					stage = Stage.FIND;
					ticks = 0;
				}
			}
			case FIND -> {
				if (poster != null && ticks > 100) {
					stage = Stage.RUN;
					ticks = 0;
				} else if (ticks > 600) {
					ShootingStar.LOGGER.error("[selftest] safety: no board went up by the bell at {}", bell);
					stage = Stage.DONE;
				}
			}
			case RUN -> {
				client.getToastManager().clear();
				if (play(client, server)) {
					stage = Stage.DONE;
				}
			}
			case DONE -> {
				YOU.release(client);
				Capture.stop();
				Capture.camera = null;
				ShootingStar.LOGGER.info("[selftest] safety: done");
				stage = null;
				client.scheduleStop();
			}
			default -> {
			}
		}
	}

	private static ServerPlayerEntity you(IntegratedServer s) {
		return s.getPlayerManager().getPlayerList().get(0);
	}

	/** The nearest village to the hotel's plains, its bell found, you put down far enough off that the board is not up yet. */
	static void setup(IntegratedServer server) {
		ServerWorld world = server.getOverworld();
		ServerPlayerEntity you = you(server);
		server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		world.setTimeOfDay(DAY);
		world.setWeather(12000, 0, false, false);
		BlockPos village = world.locateStructure(StructureTags.VILLAGE, FROM, 100, false);
		if (village == null) {
			ShootingStar.LOGGER.error("[selftest] safety: no village found");
			ready = true;
			return;
		}
		ShootingStar.LOGGER.info("[selftest] safety: village at {}", village);
		for (int cx = (village.getX() - 96) >> 4; cx <= (village.getX() + 96) >> 4; cx++) {
			for (int cz = (village.getZ() - 96) >> 4; cz <= (village.getZ() + 96) >> 4; cz++) {
				world.setChunkForced(cx, cz, true);
				world.getChunk(cx, cz);
			}
		}
		bell = world.getPointOfInterestStorage().getInSquare(e -> e.matchesKey(PointOfInterestTypes.MEETING), village, 96,
				PointOfInterestStorage.OccupationStatus.ANY).map(p -> p.getPos()).min((a, b) -> Double.compare(a.getSquaredDistance(village), b.getSquaredDistance(village))).orElse(null);
		if (bell == null) {
			// (The bell's point of interest may not be in yet: look for the block itself.)
			bell = bellBlock(world, village);
			ShootingStar.LOGGER.info("[selftest] safety: bell block at {}, its point of interest: {}", bell,
					bell == null ? "-" : world.getPointOfInterestStorage().getType(bell));
		}
		if (bell == null) {
			ShootingStar.LOGGER.error("[selftest] safety: no bell in the village at {}", village);
			ready = true;
			return;
		}
		// (A take from before may have put the board up: this world forgets it, so the board goes up as you come.)
		SafetyLedger.of(server).bells.values().forEach(set -> set.removeIf(l -> BlockPos.fromLong(l).getSquaredDistance(bell) < 96 * 96));
		for (Entity e : world.getOtherEntities(you, new Box(bell).expand(96.0, 60.0, 96.0))) {
			if (e instanceof HostileEntity) {
				e.discard();
			}
		}
		if (Form.on(you)) {
			Form.end(you);
		}
		Downed.up(you);
		you.changeGameMode(GameMode.SURVIVAL);
		you.getInventory().clear();
		you.getInventory().setStack(0, new ItemStack(Items.IRON_SWORD));
		you.getInventory().setStack(1, new ItemStack(Devils.TOMATO_CARCASS));
		you.getInventory().setStack(2, new ItemStack(Items.BREAD, 6));
		you.getInventory().selectedSlot = 0;
		you.setHealth(you.getMaxHealth());
		you.getHungerManager().setFoodLevel(20);
		you.removeAttached(Safety.YEN);
		you.removeAttached(Safety.REPORTED);
		you.removeAttached(Safety.HUNTED);
		// (Staged: the carcass is from a Tomato you killed last night.)
		Safety.count(you, Registries.ENTITY_TYPE.getId(Devils.TOMATO_DEVIL));
		// Out of the village, 90 blocks off, till the board is found to start from in front of it.
		Vec3d at = Vec3d.ofBottomCenter(world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bell.add(90, 0, 0)));
		you.teleport(world, at.x, at.y, at.z, 90.0F, 0.0F);
		ready = true;
		ShootingStar.LOGGER.info("[selftest] safety: bell at {}", bell);
	}

	private static BlockPos bellBlock(ServerWorld world, BlockPos village) {
		BlockPos best = null;
		for (int cx = (village.getX() - 96) >> 4; cx <= (village.getX() + 96) >> 4; cx++) {
			for (int cz = (village.getZ() - 96) >> 4; cz <= (village.getZ() + 96) >> 4; cz++) {
				var chunk = world.getChunk(cx, cz);
				var sections = chunk.getSectionArray();
				for (int i = 0; i < sections.length; i++) {
					if (sections[i].isEmpty() || !sections[i].hasAny(st -> st.isOf(net.minecraft.block.Blocks.BELL))) {
						continue;
					}
					int y0 = chunk.sectionIndexToCoord(i) << 4;
					for (BlockPos p : BlockPos.iterate(cx << 4, y0, cz << 4, (cx << 4) + 15, y0 + 15, (cz << 4) + 15)) {
						if (world.getBlockState(p).isOf(net.minecraft.block.Blocks.BELL)
								&& (best == null || p.getSquaredDistance(village) < best.getSquaredDistance(village))) {
							best = p.toImmutable();
						}
					}
				}
			}
		}
		return best;
	}

	/** Brings you in within the board's notice and waits for it; then puts you at the start, in front of it. */
	static void find(IntegratedServer server) {
		ServerWorld world = server.getOverworld();
		ServerPlayerEntity you = you(server);
		if (bell == null) {
			return;
		}
		Vec3d near = Vec3d.ofBottomCenter(world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bell.add(50, 0, 0)));
		you.teleport(world, near.x, near.y, near.z, 90.0F, 0.0F);
		new Thread(() -> {
			for (int i = 0; i < 40 && poster == null; i++) {
				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
					return;
				}
				server.execute(() -> look(server));
			}
		}, "safety-find").start();
	}

	private static void look(IntegratedServer server) {
		if (poster != null) {
			return;
		}
		ServerWorld world = server.getOverworld();
		for (BlockPos p : BlockPos.iterate(bell.add(-12, -6, -12), bell.add(12, 6, 12))) {
			BlockState s = world.getBlockState(p);
			if (s.isOf(Safety.POSTER)) {
				facing = s.get(BoardBlock.FACING);
				phone = findPhone(world, p);
				ServerPlayerEntity you = you(server);
				Vec3d stand = Vec3d.ofBottomCenter(p.down()).add(Vec3d.of(facing.getVector()).multiply(2.6));
				Vec3d front = start(world, stand, Vec3d.of(facing.getVector()));
				BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, BlockPos.ofFloored(front));
				float yaw = (float) Math.toDegrees(Math.atan2(-(stand.x - front.x), stand.z - front.z));
				you.teleport(world, front.x, top.getY(), front.z, yaw, 10.0F);
				ShootingStar.LOGGER.info("[selftest] safety: board's poster at {} facing {}, phone at {}, you start at {}", p, facing, phone, top);
				poster = p.toImmutable();
				return;
			}
		}
	}

	/**
	 * Where to start walking from: the furthest point (up to {@link #START_BACK} blocks, within 40 degrees either side of straight in front of the
	 * board) from which a straight walk to {@code stand} is clear: open sky, no step higher than one block, no fence or wall, no water.
	 */
	private static Vec3d start(ServerWorld world, Vec3d stand, Vec3d out) {
		Vec3d best = stand.add(out.multiply(6.0));
		double bestScore = -1.0;
		for (int angle = -40; angle <= 40; angle += 10) {
			Vec3d dir = out.rotateY((float) Math.toRadians(angle));
			for (double d = START_BACK; d >= 8.0; d -= 1.0) {
				if (clear(world, stand, dir, d)) {
					double score = d - Math.abs(angle) * 0.05;
					if (score > bestScore) {
						bestScore = score;
						best = stand.add(dir.multiply(d));
					}
					break;
				}
			}
		}
		ShootingStar.LOGGER.info("[selftest] safety: walk of {} blocks", String.format("%.0f", best.distanceTo(stand)));
		return best;
	}

	private static boolean clear(ServerWorld world, Vec3d stand, Vec3d dir, double length) {
		Vec3d side = new Vec3d(-dir.z, 0.0, dir.x).multiply(0.4);
		int last = Integer.MIN_VALUE;
		for (double t = 0.0; t <= length; t += 0.4) {
			for (int k = -1; k <= 1; k++) {
				Vec3d p = stand.add(dir.multiply(t)).add(side.multiply(k));
				int x = net.minecraft.util.math.MathHelper.floor(p.x);
				int z = net.minecraft.util.math.MathHelper.floor(p.z);
				int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
				if (y != world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z)) {
					return false;
				}
				BlockPos ground = new BlockPos(x, y - 1, z);
				BlockState g = world.getBlockState(ground);
				if (!g.getFluidState().isEmpty() || g.getCollisionShape(world, ground).isEmpty()
						|| g.getCollisionShape(world, ground).getMax(Direction.Axis.Y) > 1.0) {
					return false;
				}
				if (k == 0) {
					if (last != Integer.MIN_VALUE && Math.abs(y - last) > 1) {
						return false;
					}
					last = y;
				}
			}
		}
		return true;
	}

	private static BlockPos findPhone(ServerWorld world, BlockPos poster) {
		for (BlockPos p : BlockPos.iterate(poster.add(-4, -2, -4), poster.add(4, 2, 4))) {
			if (world.getBlockState(p).isOf(Safety.PHONE)) {
				return p.toImmutable();
			}
		}
		return poster;
	}

	private static void still(MinecraftClient c, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot safety_{}", name);
		ScreenshotRecorder.saveScreenshot(c.runDirectory, "safety_" + name + ".png", c.getFramebuffer(), m -> {
		});
	}

	private static void mark(MinecraftClient c, String what) {
		ShootingStar.LOGGER.info("[safety] mark {} at capture {}", what, String.format("%.1f", Capture.active() ? Capture.time() / 20.0 : -1.0));
	}

	private static void next(MinecraftClient c, String why) {
		step++;
		sub = 0;
		mark(c, "step " + step + " " + why);
	}

	private static int slotOf(MinecraftClient c, net.minecraft.item.Item item) {
		for (int i = 0; i < 9; i++) {
			if (c.player.getInventory().getStack(i).isOf(item)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean use(MinecraftClient c, BlockPos target) {
		if (c.crosshairTarget instanceof BlockHitResult bh && bh.getBlockPos().equals(target)) {
			c.interactionManager.interactBlock(c.player, Hand.MAIN_HAND, bh);
			c.player.swingHand(Hand.MAIN_HAND);
			return true;
		}
		return false;
	}

	/** One client tick of the take; true when it is over. */
	private static boolean play(MinecraftClient c, IntegratedServer s) {
		if (c.player == null) {
			return false;
		}
		sub++;
		c.options.hudHidden = false;
		c.options.setPerspective(Perspective.FIRST_PERSON);
		c.options.getFov().setValue(70);
		c.options.getAutoJump().setValue(true);
		c.inGameHud.getChatHud().clear(false);
		Vec3d posterAt = Vec3d.ofCenter(poster).add(0.0, 0.35, 0.0).subtract(Vec3d.of(facing.getVector()).multiply(0.45));
		Vec3d phoneAt = Vec3d.ofCenter(phone).add(0.0, -0.12, 0.0).subtract(Vec3d.of(facing.getVector()).multiply(0.3));
		Vec3d stand = Vec3d.ofBottomCenter(poster.down()).add(Vec3d.of(facing.getVector()).multiply(2.6));
		YOU.turn = 6.0F;
		switch (step) {
			case 0 -> {
				YOU.aim(c, c.player.getEyePos().add(Vec3d.of(facing.getOpposite().getVector()).multiply(10.0)).add(0.0, -1.0, 0.0));
				if (sub > 20) {
					if (capturing()) {
						Capture.start(c, c.runDirectory.toPath().resolve("capture"));
					}
					next(c, "walk into the village");
				}
			}
			case 1 -> {
				// Walking in, looking about the square, then finding the board and making for it.
				double dist = Math.hypot(stand.x - c.player.getX(), stand.z - c.player.getZ());
				if (sub < 40) {
					// (Looking about the village first: off to the side of the board.)
					YOU.aim(c, posterAt.add(Vec3d.of(facing.rotateYClockwise().getVector()).multiply(9.0)).add(0.0, 1.5, 0.0));
				} else {
					YOU.aim(c, dist > 6.0 ? posterAt.add(0.0, 0.6, 0.0) : posterAt);
				}
				if (sub == 40) {
					still(c, "1_village");
				}
				if (sub % 20 == 0) {
					ShootingStar.LOGGER.info("[safety] walking: at {} to {} ({} off)", c.player.getPos(), stand, String.format("%.2f", dist));
				}
				if (YOU.strafeTo(c, stand, 0.35, false)) {
					YOU.stop();
					next(c, "read the poster");
				}
				if (sub > 600) {
					next(c, "never got there");
				}
			}
			case 2 -> {
				YOU.stop();
				YOU.aim(c, posterAt);
				if (sub == 30) {
					still(c, "2_poster");
				}
				if (sub >= 40 && use(c, poster)) {
					mark(c, "the poster used");
					next(c, "a licence");
				} else if (sub > 120) {
					next(c, "the poster could not be reached");
				}
			}
			case 3 -> {
				YOU.aim(c, posterAt.add(0.0, -0.6, 0.0));
				int slot = slotOf(c, Safety.LICENCE);
				if (sub == 26 && slot >= 0) {
					c.player.getInventory().selectedSlot = slot;
				}
				if (sub == 44 && slot >= 0) {
					c.interactionManager.interactItem(c.player, Hand.MAIN_HAND);
					mark(c, "the licence used");
				}
				if (sub == 44 + 40) {
					still(c, "3_licence_new");
				}
				if (sub == 44 + 72) {
					if (c.currentScreen != null) {
						c.currentScreen.close();
					}
					next(c, "the phone");
				}
			}
			case 4 -> {
				YOU.aim(c, phoneAt);
				YOU.strafeTo(c, Vec3d.ofBottomCenter(phone.down()).add(Vec3d.of(facing.getVector()).multiply(2.1)), 0.25, false);
				if (sub == 16) {
					c.player.getInventory().selectedSlot = slotOf(c, Devils.TOMATO_CARCASS);
				}
				if (sub >= 40 && use(c, phone)) {
					YOU.stop();
					mark(c, "the phone used");
					next(c, "payday");
				} else if (sub > 160) {
					next(c, "the phone could not be reached");
				}
			}
			case 5 -> {
				YOU.stop();
				YOU.aim(c, phoneAt.add(0.0, -0.2, 0.0));
				if (sub == 54 + 30) {
					still(c, "4_payday");
				}
				if (sub == 54 + 40 + 30) {
					still(c, "5_paid");
				}
				if (sub > 54 + 40 + 40) {
					next(c, "the licence again");
				}
			}
			case 6 -> {
				if (sub == 4) {
					// The licence key, J.
					KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_J));
					mark(c, "J pressed");
				}
				if (sub == 36) {
					still(c, "6_licence_paid");
				}
				if (sub > 46) {
					if (c.currentScreen != null) {
						c.currentScreen.close();
					}
					next(c, "end");
				}
			}
			case 7 -> {
				YOU.aim(c, posterAt);
				if (sub > 6) {
					return true;
				}
			}
			default -> {
				return true;
			}
		}
		YOU.apply(c);
		return false;
	}
}
