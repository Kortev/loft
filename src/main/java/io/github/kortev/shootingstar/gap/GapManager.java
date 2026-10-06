package io.github.kortev.shootingstar.gap;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.network.GapEndPayload;
import io.github.kortev.shootingstar.network.GapLockPayload;
import io.github.kortev.shootingstar.network.ModNetworking;
import io.github.kortev.shootingstar.registry.ModBlocks;
import io.github.kortev.shootingstar.registry.ModGameRules;
import io.github.kortev.shootingstar.strike.Targeting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Runs Ω-00 Ginnungagap events on the server: the erasure of everything in the zone after the block of the
 * other universe comes down, and the shooter held safe in the black until they use the key again.
 */
public final class GapManager {
	private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("shootingstar_gap",
			Comparator.comparingLong(ChunkPos::toLong), GapTimeline.END + 200);
	/**
	 * Only the key lets the world back. But if whoever has it is gone (logged off) this long, it comes back on its own,
	 * so nobody is left in the void for good.
	 */
	private static final int ABSENT_LIMIT = 20 * 60 * 5;
	private static final int FLOOR_RADIUS = 14;

	private static final List<Gap> GAPS = new ArrayList<>();
	/** The other universe growing into holes the world has come back round. */
	private static final List<MirrorGrowth> GROWTHS = new ArrayList<>();
	private static int nextId = 1;

	private GapManager() {
	}

	public static final class Gap {
		final int id;
		final RegistryKey<World> dimension;
		final BlockPos target;
		final UUID shooter;
		final int radius;
		final boolean terrain;
		/** Halfway between the shooter and the target (kept for the network format; nothing watches it now). */
		final BlockPos swapSpot;
		final boolean tree;
		final Random random;
		int age;
		@Nullable
		Erasure erasure;
		boolean erased;
		/** The barrier the shooter stands on once the ground under them is gone. */
		final List<BlockPos> floor = new ArrayList<>();
		boolean floored;
		boolean released;
		/** Everyone in the world has been taken into the void. */
		boolean taken;
		/** How long the shooter has been gone (logged off) while everyone waits in the void. */
		int absent;

		Gap(int id, RegistryKey<World> dimension, BlockPos target, UUID shooter, int radius, boolean terrain, BlockPos swapSpot,
				boolean tree) {
			this.id = id;
			this.dimension = dimension;
			this.target = target;
			this.shooter = shooter;
			this.radius = radius;
			this.terrain = terrain;
			this.swapSpot = swapSpot;
			this.tree = tree;
			this.random = Random.create(target.asLong() ^ id);
		}

		public int id() {
			return id;
		}

		public BlockPos target() {
			return target;
		}

		public int age() {
			return age;
		}

		GapLockPayload payload() {
			return new GapLockPayload(id, target, shooter, age, radius, terrain, swapSpot, tree);
		}
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(GapManager::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GAPS.clear();
			GROWTHS.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			// In the void with nothing holding them there (the server stopped while they were): back home.
			if (VoidWorld.in(player) && voidOf() == null) {
				server.execute(() -> VoidWorld.bringBack(player, null, 0));
			}
			for (Gap gap : GAPS) {
				boolean mine = gap.shooter.equals(player.getUuid());
				if (gap.dimension == player.getWorld().getRegistryKey() && !gap.released && (gap.age < GapTimeline.END || mine)) {
					ModNetworking.send(player, gap.payload());
				}
			}
		});
		// Nothing touches the shooter while their event plays: they cannot move, and they are the one thing left.
		// Nor anyone in the void.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayerEntity player
				&& (shielded(player) || VoidWorld.in(player))));
	}

	/** Turns the key on {@code hit}. The shooter may be null for events called in by command. */
	public static Gap launch(ServerWorld world, BlockPos hit, @Nullable ServerPlayerEntity shooter) {
		BlockPos target = Targeting.settle(world, hit);
		int radius = world.getGameRules().getInt(ModGameRules.GAP_RADIUS);
		boolean terrain = world.getGameRules().getBoolean(ModGameRules.GAP_TERRAIN);
		BlockPos from = shooter != null ? shooter.getBlockPos() : target.add(48, 0, 0);
		BlockPos spot = ground(world, (target.getX() + from.getX()) / 2, (target.getZ() + from.getZ()) / 2);
		Gap gap = new Gap(nextId++, world.getRegistryKey(), target, shooter != null ? shooter.getUuid() : Util.NIL_UUID, radius,
				terrain, spot, false);
		GAPS.add(gap);
		ChunkPos chunk = new ChunkPos(target);
		world.getChunkManager().addTicket(TICKET, chunk, MathHelper.clamp(MathHelper.ceil(radius / 16.0) + 2, 1, 32), chunk);
		ModNetworking.broadcast(world, gap.payload());
		ShootingStar.LOGGER.info("Ginnungagap #{} on {} in {}", gap.id, target.toShortString(), world.getRegistryKey().getValue());
		return gap;
	}

	/** True while the player's last event is still playing or holding them in the black. */
	public static boolean isBusy(UUID shooter) {
		for (Gap gap : GAPS) {
			if (gap.shooter.equals(shooter) && !gap.released) {
				return true;
			}
		}
		return false;
	}

	/** The event that has taken its world into the void, which a cracked key turned in the void ends; or null. */
	@Nullable
	public static Gap voidOf() {
		for (Gap gap : GAPS) {
			if (gap.taken && !gap.released) {
				return gap;
			}
		}
		return null;
	}

	public static List<Gap> active() {
		return List.copyOf(GAPS);
	}

	private static boolean shielded(ServerPlayerEntity player) {
		for (Gap gap : GAPS) {
			if (!gap.released && gap.shooter.equals(player.getUuid())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Lets reality back in: everyone the void took goes back where they were (onto the rim, if where they stood is now
	 * the hole), and is told, so they see it rebuilt.
	 */
	public static void release(Gap gap, MinecraftServer server) {
		if (gap.released) {
			return;
		}
		gap.released = true;
		ServerWorld world = server.getWorld(gap.dimension);
		if (world == null) {
			return;
		}
		for (ServerPlayerEntity player : List.copyOf(server.getPlayerManager().getPlayerList())) {
			if (VoidWorld.in(player) && (VoidWorld.takenBy(player, gap.id) || voidOf() == null)) {
				VoidWorld.bringBack(player, gap.terrain ? gap.target : null, gap.radius);
			}
		}
		// Anyone who was not taken (the event was called off early) and is still in the hole is set down on its rim.
		ServerPlayerEntity shooter = server.getPlayerManager().getPlayer(gap.shooter);
		if (shooter != null && shooter.getWorld() == world && gap.terrain && horizontal(shooter.getPos(), gap.target) <= gap.radius + 2) {
			Vec3d away = new Vec3d(shooter.getX() - gap.target.getX() - 0.5, 0, shooter.getZ() - gap.target.getZ() - 0.5);
			away = away.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : away.normalize();
			int x = MathHelper.floor(gap.target.getX() + 0.5 + away.x * (gap.radius + 4));
			int z = MathHelper.floor(gap.target.getZ() + 0.5 + away.z * (gap.radius + 4));
			int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (y > world.getBottomY()) {
				shooter.teleport(world, x + 0.5, y, z + 0.5, shooter.getYaw(), shooter.getPitch());
			}
		}
		for (BlockPos p : gap.floor) {
			if (world.getBlockState(p).isOf(Blocks.BARRIER)) {
				world.setBlockState(p, Blocks.AIR.getDefaultState());
			}
		}
		if (gap.terrain && gap.erasure != null) {
			// The hole stays; the other universe grows into it. Kept loaded while it does.
			ChunkPos chunk = new ChunkPos(gap.target);
			world.getChunkManager().addTicket(TICKET, chunk, MathHelper.clamp(MathHelper.ceil(gap.radius / 16.0) + 2, 1, 32), chunk);
			GROWTHS.add(new MirrorGrowth(world, gap.target, gap.radius));
		}
		ModNetworking.broadcast(world, new GapEndPayload(gap.id));
		ShootingStar.LOGGER.info("Ginnungagap #{} released", gap.id);
	}

	/** Calls off every event still in its sequence. Returns how many were stopped. */
	public static int cancelAll(MinecraftServer server) {
		int n = 0;
		for (Gap gap : GAPS) {
			if (!gap.released) {
				release(gap, server);
				n++;
			}
		}
		return n;
	}

	private static void tick(MinecraftServer server) {
		GROWTHS.removeIf(MirrorGrowth::step);
		for (Iterator<Gap> it = GAPS.iterator(); it.hasNext(); ) {
			Gap gap = it.next();
			ServerWorld world = server.getWorld(gap.dimension);
			if (world == null || gap.released) {
				it.remove();
				continue;
			}
			gap.age++;
			ServerPlayerEntity shooter = server.getPlayerManager().getPlayer(gap.shooter);
			// The floor goes down once the black has swallowed everything round the shooter, so no one sees the
			// ground change, and long before the real erasure gets to them.
			if (!gap.floored && gap.age >= GapTimeline.ERASURE && shooter != null && shooter.getWorld() == world
					&& GapTimeline.eraseFront(gap.age) > manhattan(shooter.getPos(), gap.target) + 18.0) {
				gap.floored = true;
				plantFloor(world, gap, shooter);
			}
			if (gap.age >= GapTimeline.ERASURE) {
				erase(world, gap);
			}
			// Once the black has everything, the whole world goes into the void: everyone in it, and anyone who comes into
			// it while it is gone.
			if (gap.age >= GapTimeline.NOTHING && gap.age % 10 == 0) {
				gap.taken = true;
				for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
					VoidWorld.takeIn(player, gap.id);
				}
			}
			if (gap.age >= GapTimeline.END) {
				// Called in by command, with no key to turn: it comes back by itself once it is over.
				if (gap.shooter.equals(Util.NIL_UUID)) {
					release(gap, server);
				} else {
					gap.absent = shooter == null ? gap.absent + 1 : 0;
					if (gap.absent >= ABSENT_LIMIT) {
						ShootingStar.LOGGER.info("Ginnungagap #{}: the key has been gone too long, letting the world back", gap.id);
						release(gap, server);
					}
				}
			}
			if (gap.released) {
				it.remove();
			}
		}
	}

	// --- the mirror blocks ------------------------------------------------------------

	/** What a block becomes when it trades places with its twin in the mirror universe, or null if it cannot. */
	@Nullable
	public static BlockState mirrorOf(World world, BlockPos pos, BlockState state) {
		if (state.isAir() || !state.getFluidState().isEmpty() || state.getHardness(world, pos) < 0.0F
				|| state.isIn(BlockTags.WITHER_IMMUNE)) {
			return null;
		}
		if (state.isIn(BlockTags.LOGS)) {
			BlockState log = ModBlocks.MIRROR_LOG.getDefaultState();
			return state.contains(Properties.AXIS) ? log.with(Properties.AXIS, state.get(Properties.AXIS)) : log;
		}
		if (state.isIn(BlockTags.LEAVES)) {
			return ModBlocks.MIRROR_LEAVES.getDefaultState();
		}
		if (!state.isFullCube(world, pos)) {
			return null;
		}
		if (state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND) || state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.SNOW_BLOCK)) {
			return ModBlocks.MIRROR_GRASS.getDefaultState();
		}
		return ModBlocks.MIRROR_STONE.getDefaultState();
	}

	// --- the erasure ---------------------------------------------------------------------

	/** A wide, invisible floor under the shooter, so they can walk about in the black. */
	private static void plantFloor(ServerWorld world, Gap gap, ServerPlayerEntity shooter) {
		if (!gap.terrain || horizontal(shooter.getPos(), gap.target) > gap.radius + FLOOR_RADIUS) {
			return;
		}
		BlockPos below = shooter.getBlockPos().down();
		for (int dx = -FLOOR_RADIUS; dx <= FLOOR_RADIUS; dx++) {
			for (int dz = -FLOOR_RADIUS; dz <= FLOOR_RADIUS; dz++) {
				BlockPos p = below.add(dx, 0, dz);
				if (dx * dx + dz * dz > FLOOR_RADIUS * FLOOR_RADIUS || horizontal(Vec3d.ofCenter(p), gap.target) > gap.radius) {
					continue;
				}
				BlockState state = world.getBlockState(p);
				if (!state.isOf(Blocks.BARRIER) && Erasure.erasable(state)) {
					world.setBlockState(p, Blocks.BARRIER.getDefaultState(), Erasure.FLAGS);
					gap.floor.add(p);
				}
			}
		}
	}

	private static void erase(ServerWorld world, Gap gap) {
		if (gap.terrain && !gap.erased) {
			if (gap.erasure == null) {
				gap.erasure = new Erasure(world, gap.target, gap.radius);
			}
			gap.erased = gap.erasure.step(GapTimeline.eraseReach(gap.age, gap.radius));
			if (gap.erased) {
				ShootingStar.LOGGER.info("Ginnungagap #{} erased {} blocks", gap.id, gap.erasure.erased());
			}
		}
		if (gap.age % 2 != 0 || gap.age > GapTimeline.NOTHING + 40) {
			return;
		}
		double reach = Math.min(gap.radius, GapTimeline.eraseReach(gap.age, gap.radius));
		Box box = new Box(gap.target).expand(reach, world.getHeight(), reach);
		for (Entity entity : world.getOtherEntities(null, box, e -> !e.getUuid().equals(gap.shooter)
				&& horizontal(e.getPos(), gap.target) <= reach)) {
			// Players are not erased: the void takes them, with everyone else, when the black has it all.
			if (!(entity instanceof ServerPlayerEntity)) {
				entity.discard();
			}
		}
	}

	// --- helpers --------------------------------------------------------------------------

	private static BlockPos ground(World world, int x, int z) {
		return new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
	}

	private static double manhattan(Vec3d pos, BlockPos target) {
		return Math.abs(pos.x - target.getX() - 0.5) + Math.abs(pos.y - target.getY()) + Math.abs(pos.z - target.getZ() - 0.5);
	}

	private static double horizontal(Vec3d pos, BlockPos target) {
		return Math.hypot(pos.x - target.getX() - 0.5, pos.z - target.getZ() - 0.5);
	}
}
