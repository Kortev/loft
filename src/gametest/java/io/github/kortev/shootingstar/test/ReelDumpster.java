package io.github.kortev.shootingstar.test;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * The dumpster in the first reel's alley ({@link ReelSelfTest}): drawn, not built, so it can be any size and its lid can
 * swing. A green steel bin, open inside (Denji lies in it), a black rim and base, its lid hinged along the back edge.
 * Made of the game's own block faces, stretched.
 */
final class ReelDumpster {
	private static final BlockState BODY = Blocks.GREEN_CONCRETE.getDefaultState();
	private static final BlockState LID = Blocks.GREEN_TERRACOTTA.getDefaultState();
	private static final BlockState TRIM = Blocks.BLACK_CONCRETE.getDefaultState();
	private static final double WALL = 0.07;

	private ReelDumpster() {
	}

	/** Draws it ({@code origin} the set's, {@code cam} the camera's), its lid {@code open} radians up off it. */
	static void render(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d origin, Vec3d cam, float open, int light) {
		double x0 = ReelSelfTest.BIN_WEST;
		double x1 = ReelSelfTest.BIN_EAST;
		double z0 = ReelSelfTest.BIN_NORTH;
		double z1 = ReelSelfTest.BIN_SOUTH;
		double h = ReelSelfTest.BIN_HIGH;
		matrices.push();
		matrices.translate(origin.x - cam.x, origin.y - cam.y, origin.z - cam.z);
		// Its floor and four walls, the inside left open.
		box(matrices, consumers, BODY, x0, 0.0, z0, x1, WALL, z1, light);
		box(matrices, consumers, BODY, x0, 0.0, z0, x1, h, z0 + WALL, light);
		box(matrices, consumers, BODY, x0, 0.0, z1 - WALL, x1, h, z1, light);
		box(matrices, consumers, BODY, x0, 0.0, z0, x0 + WALL, h, z1, light);
		box(matrices, consumers, BODY, x1 - WALL, 0.0, z0, x1, h, z1, light);
		// A black base and a rim round the top, standing out from the walls a little.
		box(matrices, consumers, TRIM, x0 - 0.02, 0.0, z0 - 0.02, x1 + 0.02, 0.09, z1 + 0.02, light);
		box(matrices, consumers, TRIM, x0 - 0.03, h - 0.06, z0 - 0.03, x1 + 0.03, h, z0 + 0.04, light);
		box(matrices, consumers, TRIM, x0 - 0.03, h - 0.06, z1 - 0.04, x1 + 0.03, h, z1 + 0.03, light);
		box(matrices, consumers, TRIM, x0 - 0.03, h - 0.06, z0, x0 + 0.04, h, z1, light);
		box(matrices, consumers, TRIM, x1 - 0.04, h - 0.06, z0, x1 + 0.03, h, z1, light);
		// The lid, on its hinge along the back (south) edge: shut, it lies over the top and hangs past the front.
		matrices.push();
		matrices.translate(0.0, h, z1 + 0.03);
		matrices.multiply(RotationAxis.POSITIVE_X.rotation(open));
		box(matrices, consumers, LID, x0 - 0.04, 0.0, -(z1 - z0) - 0.1, x1 + 0.04, 0.06, 0.0, light);
		// Its front lip.
		box(matrices, consumers, TRIM, x0 - 0.04, -0.05, -(z1 - z0) - 0.12, x1 + 0.04, 0.06, -(z1 - z0) - 0.08, light);
		matrices.pop();
		matrices.pop();
	}

	private static void box(MatrixStack matrices, VertexConsumerProvider consumers, BlockState state, double x0, double y0, double z0, double x1,
			double y1, double z1, int light) {
		matrices.push();
		matrices.translate(x0, y0, z0);
		matrices.scale((float) (x1 - x0), (float) (y1 - y0), (float) (z1 - z0));
		MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(state, matrices, consumers, light, OverlayTexture.DEFAULT_UV);
		matrices.pop();
	}
}
