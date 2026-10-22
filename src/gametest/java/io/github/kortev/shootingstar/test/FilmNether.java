package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * gerald_2009's corner of the film: out over a lava sea in the Nether, bridging across it from a netherrack shore, a
 * few blocks of cobblestone already behind them. Found in the test world's Nether (an open stretch of the lava sea with
 * room over it), the shore and the start of the bridge built there. The bridge runs east (+x).
 */
final class FilmNether {
	/** How high the bridge is over the lava (the sea's top is at 31): high enough to be falling a while. */
	static final int BRIDGE_Y = 40;
	/** How far east the lava must run open from the start, for all the bridging before the fall. */
	static final int LENGTH = 60;

	/** The bridge's last block so far: gerald stands on it, facing back along the bridge (west). */
	final BlockPos end;

	private FilmNether(BlockPos end) {
		this.end = end;
	}

	/** Where gerald stands to start, crouched at the end of the bridge. */
	Vec3d start() {
		return new Vec3d(end.getX() + 0.5, end.getY() + 1, end.getZ() + 0.5);
	}

	static FilmNether build(ServerWorld nether) {
		BlockPos at = null;
		search:
		for (int r = 0; r <= 12; r++) {
			for (int i = -r; i <= r; i++) {
				for (int[] c : new int[][] {{i, -r}, {i, r}, {-r, i}, {r, i}}) {
					int x = c[0] * 40;
					int z = c[1] * 40;
					if (open(nether, x, z)) {
						at = new BlockPos(x, BRIDGE_Y, z);
						break search;
					}
				}
			}
		}
		if (at == null) {
			at = new BlockPos(0, BRIDGE_Y, 0);
			ShootingStar.LOGGER.warn("[film] no open lava sea found in the Nether: making one at {}", at.toShortString());
			carve(nether, at);
		}
		FilmNether set = new FilmNether(at.add(5, 0, 0));
		set.shore(nether, at);
		ShootingStar.LOGGER.info("[film] gerald_2009 bridges over the lava from {}", set.end.toShortString());
		return set;
	}

	/** Open lava from (x, z) east for the whole bridge: lava at the sea's top, nothing over it for a good way up. */
	private static boolean open(ServerWorld nether, int x, int z) {
		int good = 0;
		int all = 0;
		for (int dx = -14; dx <= LENGTH; dx += 4) {
			for (int dz = -8; dz <= 8; dz += 4) {
				all++;
				nether.getChunk((x + dx) >> 4, (z + dz) >> 4);
				if (!nether.getFluidState(new BlockPos(x + dx, 31, z + dz)).isOf(Fluids.LAVA)) {
					continue;
				}
				boolean clear = true;
				for (int y = 33; y <= BRIDGE_Y + 10 && clear; y++) {
					clear = nether.getBlockState(new BlockPos(x + dx, y, z + dz)).isAir();
				}
				if (clear) {
					good++;
				}
			}
		}
		return good >= all * 0.95;
	}

	/** No sea found: a cavern with one, with room over it. */
	private static void carve(ServerWorld nether, BlockPos at) {
		for (int dx = -20; dx <= LENGTH + 8; dx++) {
			for (int dz = -14; dz <= 14; dz++) {
				for (int y = 24; y <= BRIDGE_Y + 22; y++) {
					BlockPos p = new BlockPos(at.getX() + dx, y, at.getZ() + dz);
					nether.setBlockState(p, y <= 31 ? Blocks.LAVA.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
				}
			}
		}
	}

	/**
	 * The shore the bridge leaves from (a ragged netherrack bank west of {@code at}, its top at the bridge's height), and
	 * the bridge so far: five blocks of cobblestone out over the lava.
	 */
	private void shore(ServerWorld nether, BlockPos at) {
		for (int dx = -14; dx <= 0; dx++) {
			for (int dz = -9; dz <= 9; dz++) {
				// The bank's edge wanders, and its top falls away to the lava at the edge.
				double edge = -1.0 + 2.2 * Math.sin(dz * 0.7 + 1.3) + 1.4 * Math.sin(dz * 1.9);
				double in = edge - dx;
				if (in < 0.0) {
					continue;
				}
				int top = BRIDGE_Y - (in < 2.0 ? 3 : in < 4.0 ? 1 : 0) + (int) Math.round(1.2 * Math.sin(dx * 0.9 + dz * 0.6));
				top = MathHelper.clamp(top, 33, BRIDGE_Y + 1);
				for (int y = 20; y <= top; y++) {
					nether.setBlockState(new BlockPos(at.getX() + dx, y, at.getZ() + dz), Blocks.NETHERRACK.getDefaultState(), 2);
				}
				for (int y = top + 1; y <= BRIDGE_Y + 6; y++) {
					nether.setBlockState(new BlockPos(at.getX() + dx, y, at.getZ() + dz), Blocks.AIR.getDefaultState(), 2);
				}
			}
		}
		// Where the bridge starts, the bank is solid up to its height.
		for (int dx = -4; dx <= 0; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 20; y <= BRIDGE_Y + 2; y++) {
					nether.setBlockState(new BlockPos(at.getX() + dx, y, at.getZ() + dz),
							y <= BRIDGE_Y ? Blocks.NETHERRACK.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
				}
			}
		}
		for (int dx = 1; dx <= end.getX() - at.getX(); dx++) {
			nether.setBlockState(new BlockPos(at.getX() + dx, BRIDGE_Y, at.getZ()), Blocks.COBBLESTONE.getDefaultState(), 2);
		}
	}
}
