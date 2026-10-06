package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.client.gap.ClientGap;
import io.github.kortev.shootingstar.client.gap.ClientGaps;
import io.github.kortev.shootingstar.client.gap.GapRender;
import java.util.Locale;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;

/**
 * Watches the camera through the capture, frame by frame, and logs where it goes wrong: inside or right up against
 * a block, or out over chunks that are not loaded (nothing under it but sky). Each run of bad frames is logged once,
 * with the video time it starts and ends, so the log reads as a list of glitches to look at. A line of where the
 * camera is goes in every half second besides.
 */
final class CameraProbe {
	/** Closer than this to a block's faces is a glitch: the near plane cuts into it, or it fills the screen. */
	private static final double AGAINST = 0.6;

	private static String run;
	private static long runStart;
	private static double runWorst;
	private static String runWhere;

	private CameraProbe() {
	}

	static void frame(MinecraftClient client, long frame) {
		ClientWorld world = client.world;
		Camera camera = client.gameRenderer.getCamera();
		if (world == null || client.player == null || !camera.isReady()) {
			return;
		}
		Vec3d p = camera.getPos();
		// Where the world is black it cannot be seen, so there is nothing for the lens to hit.
		double nearest = ClientGaps.voidPhase() ? 9.99 : nearestBlock(world, p);
		int cx = MathHelper.floor(p.x) >> 4;
		int cz = MathHelper.floor(p.z) >> 4;
		boolean loaded = world.getChunkManager().isChunkLoaded(cx, cz);
		String kind = null;
		if (!loaded) {
			kind = "OVER UNLOADED CHUNKS";
		} else if (nearest <= 0.0) {
			kind = "IN TERRAIN";
		} else if (nearest < AGAINST) {
			kind = "AGAINST TERRAIN";
		}
		String where = String.format(Locale.ROOT, "%s cam %.1f %.1f %.1f", phase(), p.x, p.y, p.z);
		if (!java.util.Objects.equals(kind, run)) {
			if (run != null) {
				ShootingStar.LOGGER.warn("[probe] {} from {}s to {}s (closest {} blocks) at {}", run, seconds(runStart), seconds(frame - 1),
						String.format(Locale.ROOT, "%.2f", runWorst), runWhere);
			}
			run = kind;
			runStart = frame;
			runWorst = nearest;
			runWhere = where;
		} else if (kind != null && nearest < runWorst) {
			runWorst = nearest;
			runWhere = where;
		}
		if (frame % 15 == 0) {
			int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(p.x), MathHelper.floor(p.z));
			Vec3d feet = client.player.getPos();
			ShootingStar.LOGGER.info("[probe] {}s {} yaw {} pitch {} | ground {} | nearest {} | player {} {} {} ({} away){}",
					seconds(frame), where, String.format(Locale.ROOT, "%.0f", camera.getYaw()),
					String.format(Locale.ROOT, "%.0f", camera.getPitch()), top, String.format(Locale.ROOT, "%.2f", Math.min(nearest, 9.99)),
					String.format(Locale.ROOT, "%.1f", feet.x), String.format(Locale.ROOT, "%.1f", feet.y), String.format(Locale.ROOT, "%.1f", feet.z),
					String.format(Locale.ROOT, "%.0f", p.distanceTo(feet.add(0, 1.6, 0))), loaded ? "" : " UNLOADED");
		}
	}

	/** Closes off a run still open when the capture stops. */
	static void stop(long frames) {
		if (run != null) {
			ShootingStar.LOGGER.warn("[probe] {} from {}s to {}s (closest {} blocks) at {}", run, seconds(runStart), seconds(frames - 1),
					String.format(Locale.ROOT, "%.2f", runWorst), runWhere);
			run = null;
		}
	}

	private static String phase() {
		ClientGap gap = ClientGaps.mine();
		if (gap == null) {
			gap = ClientGaps.rebuilding();
		}
		if (gap == null) {
			return "after";
		}
		return gap.rebuildAt >= 0 ? String.format(Locale.ROOT, "rebuild %.0f", gap.rebuildClock) : "age " + gap.age;
	}

	private static String seconds(long frame) {
		return String.format(Locale.ROOT, "%.2f", frame / 30.0);
	}

	/** How far the lens is from the nearest face of anything solid round it, up to 2 blocks; 0 inside one. */
	private static double nearestBlock(ClientWorld world, Vec3d p) {
		double best = 9.99;
		// While the world is rebuilt only what the ring has reached is there to be seen.
		ClientGap rebuilding = ClientGaps.rebuilding();
		double front = rebuilding == null ? -1.0 : GapRender.rebuildFront(rebuilding, rebuilding.rebuild(1.0F));
		if (rebuilding != null && front >= 0.0
				&& Math.hypot(p.x - rebuilding.contact.x, p.z - rebuilding.contact.z) > front + 12.0) {
			return best;
		}
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int x0 = MathHelper.floor(p.x);
		int y0 = MathHelper.floor(p.y);
		int z0 = MathHelper.floor(p.z);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					pos.set(x0 + dx, y0 + dy, z0 + dz);
					BlockState state = world.getBlockState(pos);
					// Anything drawn counts, plants too (a bush against the lens fills the screen); barriers do not.
					if (state.isAir() || state.getRenderType() == BlockRenderType.INVISIBLE) {
						continue;
					}
					VoxelShape shape = state.getOutlineShape(world, pos);
					if (shape.isEmpty()) {
						continue;
					}
					for (Box box : shape.getBoundingBoxes()) {
						Box b = box.offset(pos);
						double ex = Math.max(Math.max(b.minX - p.x, 0.0), p.x - b.maxX);
						double ey = Math.max(Math.max(b.minY - p.y, 0.0), p.y - b.maxY);
						double ez = Math.max(Math.max(b.minZ - p.z, 0.0), p.z - b.maxZ);
						best = Math.min(best, Math.sqrt(ex * ex + ey * ey + ez * ez));
					}
				}
			}
		}
		return best;
	}
}
