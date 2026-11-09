package io.github.kortev.chainsawman.hotel;

import io.github.kortev.chainsawman.ChainsawSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The arena the devil rips open when it tears out. The east end of floor 8, from {@link #X0} to {@link #X1} along the corridor and the
 * hotel's whole depth (z 1 to 12): the walls into the rooms on both sides, the ceiling slab and the roof above them, and what stands on the
 * roof (the water tank), all go, from the devil's end back toward the stairs, in a burst of block particles and dust. The outer walls, the
 * parapet and the floor stay, and frame it against the night sky. Every block torn is kept (state and block entity: the signs' words, the
 * lecterns' books) in the home's own data ({@link HomeBlockEntity#arena}), so it is put back exactly when the fight is over (the devil
 * killed, spared, or nobody left) and after a server restart in the middle of it.
 */
public final class Arena {
	/** The torn section, local: along the corridor, across the hotel, and up from the walking surface of floor 8 to the roof's dressing. */
	public static final int X0 = 23;
	public static final int X1 = 36;
	public static final int Z0 = 1;
	public static final int Z1 = 12;
	public static final int Y0 = HotelPlan.floorY(HotelPlan.FLOORS - 1) + 1;
	public static final int Y1 = HotelPlan.floorY(HotelPlan.FLOORS) + 3;
	/** The columns torn a tick (the sweep runs from the devil's end toward the stairs). */
	public static final double COLUMNS_PER_TICK = 1.4;
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;

	private Arena() {
	}

	/** Whether a wall's block is in the wall columns: the corridor's two walls, and the partitions between the rooms. */
	static boolean wallColumn(int x, int z) {
		return z == 4 || z == 8 || x % 6 == 0 && (z <= 3 || z >= 9);
	}

	/** How many blocks of a torn wall column are left standing from the floor: the walls are ripped jagged, not razed. */
	static int stump(int x, int z) {
		int h = ((x * 73856093) ^ (z * 83492791)) & 255;
		if (x < X0 + 5) {
			// (None stand in the first blocks: that is where the players come in, and look from.)
			return 0;
		}
		if (z != 4 && z != 8) {
			// (The partitions between the rooms go lower: they would stand in the corridor's view.)
			return h < 150 ? 0 : 1;
		}
		return h < 90 ? 0 : h < 175 ? 1 : 2;
	}

	/** Whether the block at the local place goes: the ceiling, the roof and what is on it, the walls above their stumps, the furniture; the carpet stays. */
	static boolean goes(int x, int y, int z, BlockState state) {
		if (y >= Y0 + 3) {
			return true;
		}
		if (wallColumn(x, z)) {
			return y - Y0 >= stump(x, z);
		}
		return y > Y0 || !(state.getBlock() instanceof net.minecraft.block.CarpetBlock);
	}

	private static int hash(int x, int y, int z) {
		return ((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) & 255;
	}

	/** Flesh grows on the floor of the rooms (a few blocks), and on the top of most stumps: the devil's own, torn through the hotel. */
	static boolean fleshFloor(int x, int y, int z) {
		return y == Y0 && !wallColumn(x, z) && (z < 5 || z > 7) && x >= X0 + 5 && hash(x, y, z) < 34;
	}

	static boolean fleshStump(int x, int y, int z) {
		return wallColumn(x, z) && x >= X0 + 5 && stump(x, z) > 0 && y - Y0 == stump(x, z) - 1 && hash(x, y, z) < 170;
	}

	static BlockState flesh(int x, int y, int z) {
		int h = hash(x, y, z) % 3;
		return (h == 0 ? Blocks.RED_TERRACOTTA : h == 1 ? Blocks.NETHER_WART_BLOCK : Blocks.PINK_TERRACOTTA).getDefaultState();
	}

	/** One torn block: its local place, its state and its block entity data (or null). */
	record Cell(int x, int y, int z, BlockState state, NbtCompound entity) {
	}

	/** Whether this home's hotel has its arena torn open. */
	public static boolean torn(HomeBlockEntity h) {
		return h.arena != null;
	}

	/** The arena rips open: every block of it is kept, and the sweep starts (see {@link #tick}). */
	public static void tear(ServerWorld world, HomeBlockEntity h, long now) {
		if (h.arena != null) {
			mend(world, h);
		}
		List<Cell> cells = new ArrayList<>();
		for (int x = X0; x <= X1; x++) {
			for (int z = Z0; z <= Z1; z++) {
				for (int y = Y0; y <= Y1; y++) {
					BlockPos at = HotelBuilder.world(h.origin, h.rot, x, y, z);
					BlockState state = world.getBlockState(at);
					if (state.isAir() ? !fleshFloor(x, y, z) : !goes(x, y, z, state) && !fleshStump(x, y, z)) {
						continue;
					}
					BlockEntity be = world.getBlockEntity(at);
					cells.add(new Cell(x, y, z, state, be == null ? null : be.createNbtWithIdentifyingData(world.getRegistryManager())));
				}
			}
			// The lamps in the floor (the ceiling lamps of the floor below) go out: the arena is dark, and moonlit.
			BlockPos lamp = HotelBuilder.world(h.origin, h.rot, x, Y0 - 1, 6);
			BlockState lit = world.getBlockState(lamp);
			if (lit.getBlock() instanceof net.minecraft.block.RedstoneLampBlock && lit.get(net.minecraft.block.RedstoneLampBlock.LIT)) {
				cells.add(new Cell(x, Y0 - 1, 6, lit, null));
			}
		}
		// The flesh growing at the corridor's corners (the loop's signs) is on walls that are gone now.
		Vec3d lo = HotelBuilder.world(h.origin, h.rot, X0 - 0.5, Y0 - 1.0, Z0 - 0.5);
		Vec3d hi = HotelBuilder.world(h.origin, h.rot, X1 + 1.5, Y1 + 1.0, Z1 + 1.5);
		Box area = new Box(lo, hi);
		for (io.github.kortev.chainsawman.hotel.FleshGrowthEntity e : world.getEntitiesByType(io.github.kortev.chainsawman.ChainsawMan.FLESH_GROWTH, area, x -> true)) {
			e.discard();
		}
		h.arena = write(cells);
		h.markDirty();
		h.run.tearAt = now;
		h.run.torn = 0;
		h.run.arenaCells = cells;
		Vec3d mid = HotelBuilder.world(h.origin, h.rot, X1 - 3.0, Y0 + 1.0, 6.5);
		world.playSound(null, mid.x, mid.y, mid.z, ChainsawSounds.HOTEL_LOOP_BREAK, SoundCategory.HOSTILE, 4.0F, 0.5F);
		world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 4.0F, 0.6F);
		world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 4.0F, 0.6F);
		tick(world, h, now);
	}

	/** One tick of the sweep: the next columns go, from the east end back toward the stairs. */
	public static void tick(ServerWorld world, HomeBlockEntity h, long now) {
		Hotel.Run r = h.run;
		if (r.tearAt < 0 || h.arena == null) {
			return;
		}
		if (r.arenaCells == null) {
			r.arenaCells = read(h.arena, world);
			r.torn = 0;
		}
		int total = X1 - X0 + 1;
		int want = Math.min(total, (int) ((now - r.tearAt + 1) * COLUMNS_PER_TICK) + 1);
		while (r.torn < want) {
			int x = X1 - r.torn;
			r.torn++;
			for (Cell c : r.arenaCells) {
				if (c.x() != x) {
					continue;
				}
				BlockPos at = HotelBuilder.world(h.origin, h.rot, c.x(), c.y(), c.z());
				if (c.y() < Y0) {
					world.setBlockState(at, c.state().with(net.minecraft.block.RedstoneLampBlock.LIT, false), FLAGS);
					continue;
				}
				if (c.state().isAir() || !goes(c.x(), c.y(), c.z(), c.state())) {
					// (Not torn: flesh is grown here.)
					world.setBlockState(at, flesh(c.x(), c.y(), c.z()), FLAGS);
					continue;
				}
				if (world.getBlockEntity(at) != null) {
					world.removeBlockEntity(at);
				}
				if (!world.getBlockState(at).isAir()) {
					world.setBlockState(at, Blocks.AIR.getDefaultState(), FLAGS);
				}
				// A burst of the block's own pieces, thickest at the walls and the roof.
				if (world.random.nextInt(4) == 0) {
					world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, c.state()), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3, 0.4, 0.4, 0.4, 0.08);
				}
				if (world.random.nextInt(3) == 0) {
					// Plaster dust, thrown off the wall.
					world.spawnParticles(new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(0.78F, 0.72F, 0.58F), 1.6F), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3, 0.5, 0.5, 0.5, 0.0);
				}
			}
			Vec3d col = HotelBuilder.world(h.origin, h.rot, x + 0.5, Y0 + 2.0, 6.5);
			world.spawnParticles(ParticleTypes.LARGE_SMOKE, col.x, col.y + 1.0, col.z, 10, 2.5, 1.2, 2.5, 0.03);
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.RED_TERRACOTTA.getDefaultState()), col.x, col.y, col.z, 14, 2.0, 1.0, 2.0, 0.12);
			if (r.torn % 3 == 0) {
				world.playSound(null, col.x, col.y, col.z, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 3.0F, 0.6F + world.random.nextFloat() * 0.3F);
			}
		}
		if (r.torn >= total) {
			r.tearAt = -1;
		}
	}

	/** The arena put back, block for block (and anyone standing in what comes back is put in the corridor). */
	public static void mend(ServerWorld world, HomeBlockEntity h) {
		Hotel.Run r = h.run;
		r.tearAt = -1;
		r.torn = 0;
		r.arenaCells = null;
		if (h.arena == null) {
			return;
		}
		List<Cell> cells = read(h.arena, world);
		for (Cell c : cells) {
			BlockPos at = HotelBuilder.world(h.origin, h.rot, c.x(), c.y(), c.z());
			if (world.getBlockState(at) != c.state()) {
				world.setBlockState(at, c.state(), FLAGS);
			}
			if (c.entity() != null) {
				BlockEntity be = BlockEntity.createFromNbt(at, c.state(), c.entity(), world.getRegistryManager());
				if (be != null) {
					world.getChunk(at).setBlockEntity(be);
					be.markDirty();
				}
			}
		}
		h.arena = null;
		h.markDirty();
		// Anyone standing in a block that came back (a room's wall, the slab over their head) is put in the corridor under it.
		for (ServerPlayerEntity p : world.getPlayers()) {
			Vec3d lp = HotelBuilder.local(h.origin, h.rot, p.getPos());
			if (lp.x < X0 - 2.0 || lp.x > X1 + 2.0 || lp.z < -1.0 || lp.z > 14.0 || lp.y < Y0 - 2.0 || lp.y > Y1 + 4.0) {
				continue;
			}
			Box box = p.getBoundingBox();
			boolean stuck = lp.y >= Y0 + 3.2 || !world.isSpaceEmpty(p, box);
			if (stuck) {
				Vec3d to = HotelBuilder.world(h.origin, h.rot, Math.max(X0 + 0.5, Math.min(X1 - 0.5, lp.x)), Y0, 6.5);
				p.stopRiding();
				p.networkHandler.requestTeleport(to.x, to.y, to.z, p.getYaw(), p.getPitch());
			}
		}
		Vec3d mid = HotelBuilder.world(h.origin, h.rot, (X0 + X1) / 2.0, Y0 + 2.0, 6.5);
		world.spawnParticles(ParticleTypes.POOF, mid.x, mid.y, mid.z, 30, 5.0, 1.5, 3.0, 0.02);
		world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 3.0F, 0.6F);
	}

	// ---- What is kept, as data.

	private static NbtCompound write(List<Cell> cells) {
		NbtCompound nbt = new NbtCompound();
		List<BlockState> palette = new ArrayList<>();
		int[] flat = new int[cells.size() * 4];
		NbtList entities = new NbtList();
		int i = 0;
		int n = 0;
		for (Cell c : cells) {
			int index = palette.indexOf(c.state());
			if (index < 0) {
				index = palette.size();
				palette.add(c.state());
			}
			flat[i++] = c.x();
			flat[i++] = c.y();
			flat[i++] = c.z();
			flat[i++] = index;
			if (c.entity() != null) {
				NbtCompound e = new NbtCompound();
				e.putInt("Cell", n);
				e.put("Data", c.entity());
				entities.add(e);
			}
			n++;
		}
		NbtList pal = new NbtList();
		for (BlockState s : palette) {
			pal.add(NbtHelper.fromBlockState(s));
		}
		nbt.put("Palette", pal);
		nbt.putIntArray("Cells", flat);
		nbt.put("Entities", entities);
		return nbt;
	}

	private static List<Cell> read(NbtCompound nbt, ServerWorld world) {
		NbtList pal = nbt.getList("Palette", NbtElement.COMPOUND_TYPE);
		List<BlockState> palette = new ArrayList<>();
		for (int i = 0; i < pal.size(); i++) {
			palette.add(NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), pal.getCompound(i)));
		}
		int[] flat = nbt.getIntArray("Cells");
		NbtList entities = nbt.getList("Entities", NbtElement.COMPOUND_TYPE);
		java.util.Map<Integer, NbtCompound> data = new java.util.HashMap<>();
		for (int i = 0; i < entities.size(); i++) {
			NbtCompound e = entities.getCompound(i);
			data.put(e.getInt("Cell"), e.getCompound("Data"));
		}
		List<Cell> cells = new ArrayList<>();
		for (int i = 0; i + 3 < flat.length; i += 4) {
			cells.add(new Cell(flat[i], flat[i + 1], flat[i + 2], palette.get(flat[i + 3]), data.get(i / 4)));
		}
		return cells;
	}
}
