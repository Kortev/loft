package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.gap.GapManager;
import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.registry.ModItems;
import io.github.kortev.shootingstar.strike.Targeting;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * With -Dshootingstar.mptest=server, the dedicated server's half of the multiplayer test: once Shooter, Victim and
 * Netherling have joined, the shooter is stood where they can see a target with the key in hand, the victim where the
 * burst will come up and the netherling in the Nether; then it logs where everyone is as the event goes, and stops the
 * server once they have all left. The clients' half is {@link MultiplayerTestClient}.
 */
public class MultiplayerTestServer implements ModInitializer {
	private static final String[] NAMES = {"Shooter", "Victim", "Netherling"};
	private static final double RANGE = 64.0;
	private static final Map<String, Vec3d> STARTS = new HashMap<>();
	private static boolean setUp;
	private static boolean ran;
	private static int ticks;
	private static int doneAt = -1;
	private static int lastAge = -1;

	@Override
	public void onInitialize() {
		if (!"server".equals(System.getProperty("shootingstar.mptest"))) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerWorld world = server.getOverworld();
			world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
			world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
			world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
			world.setTimeOfDay(2000);
			world.setWeather(12000, 0, false, false);
			ShootingStar.LOGGER.info("[mptest] server ready");
		});
		ServerTickEvents.END_SERVER_TICK.register(MultiplayerTestServer::tick);
	}

	private static void tick(MinecraftServer server) {
		ticks++;
		if (ticks > 20 * 60 * 12) {
			ShootingStar.LOGGER.error("[mptest] timed out");
			server.stop(false);
			return;
		}
		// The netherling into the Nether as soon as they are in, to have it loaded long before anything happens.
		ServerPlayerEntity early = server.getPlayerManager().getPlayer("Netherling");
		if (!setUp && early != null && early.getWorld() != server.getWorld(World.NETHER)) {
			toNether(server, early);
		}
		if (!setUp) {
			for (String name : NAMES) {
				if (server.getPlayerManager().getPlayer(name) == null) {
					return;
				}
			}
			setUp(server);
			setUp = true;
			return;
		}
		GapManager.Gap gap = GapManager.active().isEmpty() ? null : GapManager.active().get(0);
		if (gap != null) {
			ran = true;
			int age = gap.age();
			for (int mark : new int[] {GapTimeline.CONTACT + 10, GapTimeline.NOTHING + 40, GapTimeline.END}) {
				if (lastAge < mark && age >= mark) {
					report(server, "age " + mark);
				}
			}
			lastAge = age;
		}
		if (ran && !GapManager.running() && doneAt < 0) {
			doneAt = ticks;
		}
		if (doneAt >= 0 && ticks == doneAt + 100) {
			report(server, "home");
			for (String name : NAMES) {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(name);
				Vec3d start = STARTS.get(name);
				if (player != null && start != null) {
					ShootingStar.LOGGER.info("[mptest] {} is {} from where they started{}", name,
							String.format(Locale.ROOT, "%.1f", player.getPos().distanceTo(start)),
							name.equals("Netherling") ? " (in " + player.getWorld().getRegistryKey().getValue() + ")" : "");
				}
			}
		}
		if (doneAt >= 0 && ticks > doneAt + 100 && server.getPlayerManager().getCurrentPlayerCount() == 0) {
			ShootingStar.LOGGER.info("[mptest] everyone has left; stopping");
			server.stop(false);
		}
	}

	private static void report(MinecraftServer server, String when) {
		for (String name : NAMES) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(name);
			if (player == null) {
				ShootingStar.LOGGER.info("[mptest] {}: {} is gone", when, name);
				continue;
			}
			ShootingStar.LOGGER.info("[mptest] {}: {} in {} at {} alive {} held {}", when, name, player.getWorld().getRegistryKey().getValue(),
					String.format(Locale.ROOT, "%.1f %.2f %.1f", player.getX(), player.getY(), player.getZ()), player.isAlive(),
					GapManager.held(player));
		}
	}

	private static void setUp(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		BlockPos spawn = world.getSpawnPos();
		BlockPos target = null;
		double best = Double.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI * 2 * i / 8;
			int x = spawn.getX() + (int) Math.round(Math.cos(angle) * RANGE);
			int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * RANGE);
			double score = roughness(world, x, z);
			if (score < best) {
				best = score;
				target = new BlockPos(x, top(world, x, z) - 1, z);
			}
		}
		// The shooter on the ground at spawn, or on a pillar tall enough to see the target.
		ServerPlayerEntity shooter = server.getPlayerManager().getPlayer("Shooter");
		int ground = top(world, spawn.getX(), spawn.getZ());
		int feet = ground;
		while (feet < ground + 60 && !reaches(world, new Vec3d(spawn.getX() + 0.5, feet + 1.62, spawn.getZ() + 0.5), target)) {
			feet++;
		}
		for (int y = ground; y < feet; y++) {
			world.setBlockState(new BlockPos(spawn.getX(), y, spawn.getZ()), Blocks.STONE.getDefaultState());
		}
		shooter.changeGameMode(GameMode.SURVIVAL);
		shooter.getInventory().clear();
		shooter.getInventory().setStack(0, new ItemStack(ModItems.GENESIS_KEY));
		shooter.getInventory().selectedSlot = 0;
		shooter.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(0));
		look(world, shooter, spawn.getX() + 0.5, feet, spawn.getZ() + 0.5, Vec3d.ofCenter(target));
		// The victim where the burst will come up.
		ServerPlayerEntity victim = server.getPlayerManager().getPlayer("Victim");
		victim.changeGameMode(GameMode.SURVIVAL);
		int vx = target.getX() + 5;
		int vz = target.getZ();
		look(world, victim, vx + 0.5, top(world, vx, vz), vz + 0.5, Vec3d.ofCenter(target));
		BlockPos ledge = toNether(server, server.getPlayerManager().getPlayer("Netherling"));
		STARTS.put("Shooter", new Vec3d(spawn.getX() + 0.5, feet, spawn.getZ() + 0.5));
		STARTS.put("Victim", new Vec3d(vx + 0.5, top(world, vx, vz), vz + 0.5));
		STARTS.put("Netherling", new Vec3d(ledge.getX() + 0.5, ledge.getY() + 1, ledge.getZ() + 0.5));
		ShootingStar.LOGGER.info("[mptest] set up: target {}, shooter on {} at {}", target.toShortString(), feet - ground, feet);
	}

	/** The netherling on a ledge of stone in the Nether, under the overworld's spawn. */
	private static BlockPos toNether(MinecraftServer server, ServerPlayerEntity netherling) {
		ServerWorld nether = server.getWorld(World.NETHER);
		BlockPos spawn = server.getOverworld().getSpawnPos();
		BlockPos ledge = new BlockPos(spawn.getX() / 8, 100, spawn.getZ() / 8);
		if (netherling.getWorld() != nether) {
			netherling.changeGameMode(GameMode.SURVIVAL);
			for (BlockPos pos : BlockPos.iterate(ledge.add(-2, 0, -2), ledge.add(2, 3, 2))) {
				nether.setBlockState(pos, pos.getY() == ledge.getY() ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
			}
			netherling.teleport(nether, ledge.getX() + 0.5, ledge.getY() + 1, ledge.getZ() + 0.5, 0.0F, 10.0F);
		}
		return ledge;
	}

	private static void look(ServerWorld world, ServerPlayerEntity player, double x, double feet, double z, Vec3d at) {
		double dx = at.x - x;
		double dy = at.y - (feet + player.getStandingEyeHeight());
		double dz = at.z - z;
		float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * MathHelper.DEGREES_PER_RADIAN);
		player.teleport(world, x, feet, z, yaw, pitch);
	}

	private static boolean reaches(ServerWorld world, Vec3d eye, BlockPos target) {
		BlockPos hit = Targeting.findTarget(world, eye, Vec3d.ofCenter(target).subtract(eye).normalize(), Targeting.MAX_RANGE);
		return hit != null && hit.getManhattanDistance(target) <= 2;
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
		boolean water = !world.getFluidState(new BlockPos(cx, top(world, cx, cz) - 1, cz)).isEmpty();
		return sumSq / n - mean * mean + (water ? 400 : 0);
	}
}
