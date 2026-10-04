package io.github.kortev.shootingstar.gap;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.network.GapEndPayload;
import io.github.kortev.shootingstar.network.GapLockPayload;
import io.github.kortev.shootingstar.network.GapSwapPayload;
import io.github.kortev.shootingstar.network.ModNetworking;
import io.github.kortev.shootingstar.registry.ModBlocks;
import io.github.kortev.shootingstar.registry.ModDamageTypes;
import io.github.kortev.shootingstar.registry.ModGameRules;
import io.github.kortev.shootingstar.registry.ModSounds;
import io.github.kortev.shootingstar.strike.Targeting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Runs Ω-00 Ginnungagap events on the server: matter swapping between the worlds while they close, the
 * erasure of everything in the zone after contact, and the shooter held safe in the black until they use
 * the key again.
 */
public final class GapManager {
	private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("shootingstar_gap",
			Comparator.comparingLong(ChunkPos::toLong), GapTimeline.END + 200);
	/** The shooter is let back out on their own after this long in the black. */
	private static final int HOLD_LIMIT = 20 * 120;
	private static final int TREE_LIMIT = 320;
	private static final int FLOOR_RADIUS = 14;

	private static final List<Gap> GAPS = new ArrayList<>();
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
		/** What trades places whole at {@link GapTimeline#TREE_SWAP}: the base of a tree, or a patch of ground. */
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
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GAPS.clear());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			for (Gap gap : GAPS) {
				boolean mine = gap.shooter.equals(player.getUuid());
				if (gap.dimension == player.getWorld().getRegistryKey() && !gap.released && (gap.age < GapTimeline.END || mine)) {
					ModNetworking.send(player, gap.payload());
				}
			}
		});
		// Nothing touches the shooter while their event plays: they cannot move, and they are the one thing left.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayerEntity player
				&& shielded(player)));
	}

	/** Turns the key on {@code hit}. The shooter may be null for events called in by command. */
	public static Gap launch(ServerWorld world, BlockPos hit, @Nullable ServerPlayerEntity shooter) {
		BlockPos target = Targeting.settle(world, hit);
		int radius = world.getGameRules().getInt(ModGameRules.GAP_RADIUS);
		boolean terrain = world.getGameRules().getBoolean(ModGameRules.GAP_TERRAIN);
		BlockPos from = shooter != null ? shooter.getBlockPos() : target.add(48, 0, 0);
		BlockPos tree = findTree(world, target, from);
		BlockPos spot = tree != null ? tree : ground(world, (target.getX() + from.getX()) / 2, (target.getZ() + from.getZ()) / 2);
		Gap gap = new Gap(nextId++, world.getRegistryKey(), target, shooter != null ? shooter.getUuid() : Util.NIL_UUID, radius,
				terrain, spot, tree != null);
		GAPS.add(gap);
		ChunkPos chunk = new ChunkPos(target);
		world.getChunkManager().addTicket(TICKET, chunk, MathHelper.clamp(MathHelper.ceil(radius / 16.0) + 2, 1, 32), chunk);
		ModNetworking.broadcast(world, gap.payload());
		ShootingStar.LOGGER.info("Ginnungagap #{} on {} in {} (swap at {}, tree {})", gap.id, target.toShortString(),
				world.getRegistryKey().getValue(), spot.toShortString(), gap.tree);
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

	/** The event holding this player in the black, which their key can now end. */
	@Nullable
	public static Gap holding(UUID shooter) {
		for (Gap gap : GAPS) {
			if (gap.shooter.equals(shooter) && !gap.released && gap.age >= GapTimeline.END) {
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

	/** Lets reality back in for the shooter: their screen comes back and they are set down on solid ground. */
	public static void release(Gap gap, MinecraftServer server) {
		if (gap.released) {
			return;
		}
		gap.released = true;
		ServerWorld world = server.getWorld(gap.dimension);
		if (world == null) {
			return;
		}
		ModNetworking.broadcast(world, new GapEndPayload(gap.id));
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
		for (Iterator<Gap> it = GAPS.iterator(); it.hasNext(); ) {
			Gap gap = it.next();
			ServerWorld world = server.getWorld(gap.dimension);
			if (world == null || gap.released) {
				it.remove();
				continue;
			}
			gap.age++;
			ServerPlayerEntity shooter = server.getPlayerManager().getPlayer(gap.shooter);
			if (gap.age >= GapTimeline.CLOSING && gap.age < GapTimeline.SWAPS_END && gap.age % 3 == 0) {
				swapBlock(world, gap, shooter);
			}
			if (gap.age == GapTimeline.TREE_SWAP) {
				swapWhole(world, gap);
			}
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
			if (gap.age >= GapTimeline.END) {
				boolean gone = shooter == null || shooter.isRemoved() || shooter.getWorld() != world;
				if (gone || gap.age >= GapTimeline.END + HOLD_LIMIT || gap.shooter.equals(Util.NIL_UUID)) {
					release(gap, server);
				}
			}
			if (gap.released) {
				it.remove();
			}
		}
	}

	// --- matter swapping ------------------------------------------------------------------

	/** One block of our ground near the target trades places with its mirror. */
	private static void swapBlock(ServerWorld world, Gap gap, @Nullable ServerPlayerEntity shooter) {
		for (int tries = 0; tries < 16; tries++) {
			double angle = gap.random.nextDouble() * Math.PI * 2.0;
			double dist = 6.0 + Math.abs(gap.random.nextGaussian()) * 20.0;
			int x = MathHelper.floor(gap.target.getX() + 0.5 + Math.cos(angle) * dist);
			int z = MathHelper.floor(gap.target.getZ() + 0.5 + Math.sin(angle) * dist);
			if (shooter != null && Math.abs(shooter.getX() - x) < 4.0 && Math.abs(shooter.getZ() - z) < 4.0) {
				continue;
			}
			if (Math.abs(gap.swapSpot.getX() - x) < 5 && Math.abs(gap.swapSpot.getZ() - z) < 5) {
				continue;
			}
			BlockPos pos = ground(world, x, z);
			BlockState mirror = mirrorOf(world, pos, world.getBlockState(pos));
			if (mirror == null) {
				continue;
			}
			// The payload goes first so clients can still see what the block was.
			ModNetworking.broadcast(world, new GapSwapPayload(gap.id, 0, List.of(pos)));
			world.setBlockState(pos, mirror, Block.NOTIFY_ALL);
			world.playSound(null, pos, ModSounds.GAP_SWAP, SoundCategory.BLOCKS, 2.0F, 0.8F + 0.4F * gap.random.nextFloat());
			return;
		}
	}

	/** The tree (or, with no tree about, a patch of ground) the close-up watches trades places with its twin. */
	private static void swapWhole(ServerWorld world, Gap gap) {
		List<BlockPos> blocks = new ArrayList<>();
		if (gap.tree) {
			BlockPos base = gap.swapSpot;
			ArrayDeque<BlockPos> queue = new ArrayDeque<>();
			Set<BlockPos> seen = new HashSet<>();
			queue.add(base);
			seen.add(base);
			while (!queue.isEmpty() && blocks.size() < TREE_LIMIT) {
				BlockPos p = queue.poll();
				BlockState s = world.getBlockState(p);
				if (!(s.isIn(BlockTags.LOGS) || s.isIn(BlockTags.LEAVES))) {
					continue;
				}
				blocks.add(p);
				for (Direction d : Direction.values()) {
					BlockPos n = p.offset(d);
					if (Math.abs(n.getX() - base.getX()) <= 7 && Math.abs(n.getZ() - base.getZ()) <= 7 && n.getY() >= base.getY()
							&& n.getY() <= base.getY() + 24 && seen.add(n)) {
						queue.add(n);
					}
				}
			}
		}
		if (blocks.isEmpty()) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					BlockPos p = ground(world, gap.swapSpot.getX() + dx, gap.swapSpot.getZ() + dz);
					if (mirrorOf(world, p, world.getBlockState(p)) != null) {
						blocks.add(p);
					}
				}
			}
		}
		ModNetworking.broadcast(world, new GapSwapPayload(gap.id, 1, blocks));
		for (BlockPos p : blocks) {
			BlockState mirror = mirrorOf(world, p, world.getBlockState(p));
			if (mirror != null) {
				world.setBlockState(p, mirror, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
			}
		}
		world.playSound(null, gap.swapSpot, ModSounds.GAP_SWAP, SoundCategory.BLOCKS, 4.0F, 0.55F);
	}

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
			if (entity instanceof ServerPlayerEntity player) {
				if (!player.isCreative() && !player.isSpectator()) {
					player.damage(ModDamageTypes.erased(world), Float.MAX_VALUE);
				}
			} else {
				entity.discard();
			}
		}
	}

	// --- helpers --------------------------------------------------------------------------

	/** The tree nearest the middle of the line from the shooter to the target, as the base of its trunk. */
	@Nullable
	private static BlockPos findTree(ServerWorld world, BlockPos target, BlockPos from) {
		int mx = (target.getX() + from.getX()) / 2;
		int mz = (target.getZ() + from.getZ()) / 2;
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int dx = -30; dx <= 30; dx++) {
			for (int dz = -30; dz <= 30; dz++) {
				int x = mx + dx;
				int z = mz + dz;
				double toTarget = Math.hypot(x - target.getX(), z - target.getZ());
				if (toTarget < 12.0 || Math.hypot(x - from.getX(), z - from.getZ()) < 10.0) {
					continue;
				}
				int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
				if (!world.getBlockState(pos.set(x, top, z)).isIn(BlockTags.LEAVES)) {
					continue;
				}
				for (int y = top - 1; y > top - 14; y--) {
					if (world.getBlockState(pos.set(x, y, z)).isIn(BlockTags.LOGS)) {
						while (world.getBlockState(pos.set(x, y - 1, z)).isIn(BlockTags.LOGS)) {
							y--;
						}
						double score = Math.hypot(dx, dz);
						if (score < bestScore) {
							bestScore = score;
							best = new BlockPos(x, y, z);
						}
						break;
					}
				}
			}
		}
		return best;
	}

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
