package io.github.kortev.shootingstar.strike;

import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class Targeting {
	public static final double MAX_RANGE = 640.0;
	/** Danger-close floor for tiny craters; normally the limit is 1.5x the crater radius. */
	public static final double MIN_RANGE = 16.0;
	/** Crater radius the client assumes for its aim hint; the server checks the real game rule. */
	public static final int DEFAULT_RADIUS = 28;

	private Targeting() {
	}

	/**
	 * Walks the view ray voxel by voxel and returns the first block that would stop it (anything
	 * with collision, or a fluid surface). Returns null when the ray leaves loaded terrain, so it
	 * never forces chunks to load on the server.
	 */
	@Nullable
	public static BlockPos findTarget(World world, Vec3d start, Vec3d direction, double range) {
		Vec3d dir = direction.normalize();
		int x = MathHelper.floor(start.x);
		int y = MathHelper.floor(start.y);
		int z = MathHelper.floor(start.z);
		int stepX = (int) Math.signum(dir.x);
		int stepY = (int) Math.signum(dir.y);
		int stepZ = (int) Math.signum(dir.z);
		double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dir.x);
		double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dir.y);
		double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dir.z);
		double maxX = stepX > 0 ? (x + 1 - start.x) * deltaX : stepX < 0 ? (start.x - x) * deltaX : Double.POSITIVE_INFINITY;
		double maxY = stepY > 0 ? (y + 1 - start.y) * deltaY : stepY < 0 ? (start.y - y) * deltaY : Double.POSITIVE_INFINITY;
		double maxZ = stepZ > 0 ? (z + 1 - start.z) * deltaZ : stepZ < 0 ? (start.z - z) * deltaZ : Double.POSITIVE_INFINITY;

		BlockPos.Mutable pos = new BlockPos.Mutable(x, y, z);
		boolean startsInFluid = !world.getBlockState(pos).getFluidState().isEmpty();
		int bottom = world.getBottomY();
		int top = world.getTopY();
		double t = 0;
		while (t <= range) {
			if (maxX < maxY && maxX < maxZ) {
				x += stepX;
				t = maxX;
				maxX += deltaX;
			} else if (maxY < maxZ) {
				y += stepY;
				t = maxY;
				maxY += deltaY;
			} else {
				z += stepZ;
				t = maxZ;
				maxZ += deltaZ;
			}
			if (t > range || y < bottom) {
				return null;
			}
			if (y >= top) {
				if (stepY >= 0) {
					return null;
				}
				continue;
			}
			if (!world.isChunkLoaded(x >> 4, z >> 4)) {
				return null;
			}
			pos.set(x, y, z);
			BlockState state = world.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			if (!startsInFluid && !state.getFluidState().isEmpty()) {
				return pos.toImmutable();
			}
			if (!state.getCollisionShape(world, pos).isEmpty()) {
				return pos.toImmutable();
			}
		}
		return null;
	}

	/** Closest a target may be to the shooter: just outside the scorched ring. */
	public static double minRange(int craterRadius) {
		return Math.max(MIN_RANGE, Math.ceil(craterRadius * 1.5));
	}

	/** Drops a hit on a tree canopy or plant down to the ground underneath it. */
	public static BlockPos settle(World world, BlockPos hit) {
		BlockPos.Mutable pos = hit.mutableCopy();
		for (int i = 0; i < 48 && pos.getY() > world.getBottomY(); i++) {
			BlockState state = world.getBlockState(pos);
			boolean soft = state.isAir() || state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS)
					|| (state.getFluidState().isEmpty() && state.getCollisionShape(world, pos).isEmpty());
			if (!soft) {
				break;
			}
			pos.move(0, -1, 0);
		}
		return pos.toImmutable();
	}
}
