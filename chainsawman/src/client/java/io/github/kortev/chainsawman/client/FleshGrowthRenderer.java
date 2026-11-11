package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.FleshGrowthEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * The flesh that grows on the loop floor ({@link FleshGrowthEntity}) and the eyes in its wallpaper, at the game's own pixel size (package
 * 0.1g): the lumps are voxels a sixteenth of a block that come out of the wall and floor through their roots ({@link FleshVoxels}); an eye
 * is pixel art set into the wallpaper (a shadowed socket, lids that part a row at a time, a pupil that moves a whole pixel at a time to
 * follow whoever is looking). Drawn with the back to the wall (+z out of it, into the corridor).
 */
public class FleshGrowthRenderer extends EntityRenderer<FleshGrowthEntity> {
	public FleshGrowthRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(FleshGrowthEntity entity) {
		return PixelArt.PX;
	}

	@Override
	public boolean shouldRender(FleshGrowthEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		return true;
	}

	@Override
	public void render(FleshGrowthEntity growth, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(growth.getWorld() instanceof ClientWorld world)) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		Vec3d drawn = growth.getLerpedPos(delta).add(ClientLoop.drawnAt());
		if (client.gameRenderer.getCamera().getPos().squaredDistanceTo(drawn) > 18.0 * 18.0) {
			// (Past the loop's fog: nothing to see.)
			return;
		}
		// The light of the corridor in front of it, not of the wall it is set in (a growth on a wall's face stands on the wall block's edge).
		double yr = Math.toRadians(growth.getYaw());
		Vec3d front = drawn.add(-Math.sin(yr) * 0.3, 0.3, Math.cos(yr) * 0.3);
		light = net.minecraft.client.render.WorldRenderer.getLightmapCoordinates(world, net.minecraft.util.math.BlockPos.ofFloored(front));
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-growth.getYaw()));
		VertexConsumer vc = PixelArt.buffer(provider);
		int kind = growth.kind();
		if (kind == FleshGrowthEntity.EYE) {
			eye(growth, delta, matrices, vc, light);
			matrices.pop();
			return;
		}
		float grow = MathHelper.clamp((world.getTime() + delta - growth.born()) / 24.0F, 0.0F, 1.0F);
		int stage = Math.min(FleshVoxels.STAGES - 1, (int) (grow * FleshVoxels.STAGES));
		int seed = growth.seed();
		if (kind == FleshGrowthEntity.BREATH) {
			float breath = 0.5F + 0.5F * MathHelper.sin((world.getTime() + delta) * 0.11F);
			FleshVoxels.draw(vc, matrices.peek(), FleshVoxels.mesh(kind, seed, FleshVoxels.STAGES - 1, Math.round(breath * 3.0F)), light);
			matrices.pop();
			return;
		}
		// (Kinds no longer grown, in the ceiling, are drawn as bulges of the wall.)
		int shape = kind == FleshGrowthEntity.FLOOR ? FleshGrowthEntity.FLOOR : FleshGrowthEntity.WALL;
		FleshVoxels.draw(vc, matrices.peek(), FleshVoxels.mesh(shape, seed, stage, 0), light);
		// An eye on about half of them, in the lump's front near its top, looking at the viewer.
		if ((seed & 1) == 0 && stage == FleshVoxels.STAGES - 1) {
			int[] spot = FleshVoxels.eyeSpot(shape, seed);
			Vec3d look = toViewer(growth, delta, new Vec3d(spot[0] / 16.0, spot[1] / 16.0, spot[2] / 16.0));
			int[][] g = smallEye(look);
			PixelArt.grid(vc, matrices.peek(), g, spot[0] - 2, spot[1] - 1, spot[2] / 16.0F + 0.002F, light);
		}
		matrices.pop();
	}

	/** The way to whoever is looking (the camera), in the growth's frame, from the point {@code at} of it. */
	private static Vec3d toViewer(FleshGrowthEntity growth, float delta, Vec3d at) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return new Vec3d(0.0, 0.0, 1.0);
		}
		Vec3d eye = client.gameRenderer.getCamera().getPos().subtract(growth.getLerpedPos(delta).add(ClientLoop.drawnAt()));
		org.joml.Vector3f v = new org.joml.Vector3f((float) eye.x, (float) eye.y, (float) eye.z).rotateY((float) Math.toRadians(growth.getYaw()));
		Vec3d to = new Vec3d(v.x, v.y, v.z).subtract(at);
		return to.lengthSquared() > 1.0E-6 ? to.normalize() : new Vec3d(0.0, 0.0, 1.0);
	}

	// ---- The eye in the wallpaper.

	static final int SOCKET = PixelArt.rgb(64, 36, 28);
	static final int SOCKET_SOFT = PixelArt.rgb(170, 132, 100);
	static final int LID = PixelArt.rgb(206, 176, 142);
	static final int LID_SHADE = PixelArt.rgb(178, 136, 108);
	static final int LID_EDGE = PixelArt.rgb(112, 58, 46);
	static final int LASH = PixelArt.rgb(42, 24, 20);
	static final int WHITE = PixelArt.rgb(234, 226, 210);
	static final int WHITE_SHADE = PixelArt.rgb(196, 178, 160);
	static final int VEIN = PixelArt.rgb(178, 62, 58);
	static final int IRIS = PixelArt.rgb(118, 32, 22);
	static final int IRIS_RING = PixelArt.rgb(66, 14, 12);
	static final int PUPIL = PixelArt.rgb(14, 8, 8);
	static final int GLINT = PixelArt.rgb(255, 246, 230);
	/** Half the eye's height, column by column (12 columns, 8 rows: rows -4 to 3 about its middle line). */
	private static final int[] HALF = {1, 2, 3, 3, 4, 4, 4, 4, 3, 3, 2, 1};

	/** The eye at its tracked state: it comes open (and shuts) a row at a time; shut, it is a seam with lashes in the wallpaper. */
	private void eye(FleshGrowthEntity growth, float delta, MatrixStack matrices, VertexConsumer vc, int light) {
		float target = growth.isOpen() ? 1.0F : 0.0F;
		float open = OPEN.getOrDefault(growth.getId(), 0.0F);
		long now = growth.getWorld().getTime();
		if (LAST.getOrDefault(growth.getId(), -1L) != now) {
			LAST.put(growth.getId(), now);
			open += MathHelper.clamp(target - open, -0.25F, 0.12F);
			OPEN.put(growth.getId(), open);
		}
		Vec3d look = toViewer(growth, delta, Vec3d.ZERO);
		PixelArt.grid(vc, matrices.peek(), wallEye(open, look, growth.seed()), -7, -5, 0.002F, light);
	}

	/** The eye's pixels (14 x 10, the socket round the 12 x 8 eye), row 0 at the bottom. */
	static int[][] wallEye(float open, Vec3d look, int seed) {
		int[][] g = new int[10][14];
		// The socket: a pixel round the eye, dark over it (the brow's shadow), soft under it.
		for (int c = 0; c < 12; c++) {
			int h = HALF[c];
			for (int r = -h - 1; r <= h; r++) {
				g[r + 5][c + 1] = r == h ? SOCKET : r == -h - 1 ? SOCKET_SOFT : SOCKET;
			}
		}
		for (int r = -2; r <= 1; r++) {
			g[r + 5][0] = SOCKET;
			g[r + 5][13] = SOCKET;
		}
		int ox = (int) Math.round(MathHelper.clamp(look.x * 4.0, -3.0, 3.0));
		int oy = (int) Math.round(MathHelper.clamp(look.y * 3.0, -2.0, 1.0));
		for (int c = 0; c < 12; c++) {
			int h = HALF[c];
			int vis = Math.round(h * MathHelper.clamp(open, 0.0F, 1.0F));
			for (int r = -h; r < h; r++) {
				int col;
				if (r >= -vis && r < vis) {
					// The white, a vein or two, the iris and pupil where the look puts them (a whole pixel at a time).
					int dx = c - (6 + ox);
					int dy = r - oy;
					boolean iris = dx >= -2 && dx <= 1 && dy >= -2 && dy <= 1 && !(Math.abs(dx + 0.5) > 1.0 && Math.abs(dy + 0.5) > 1.0);
					if (iris) {
						boolean pupil = dx >= -1 && dx <= 0 && dy >= -1 && dy <= 0;
						col = pupil ? PUPIL : (dy == 1 || dx == -2 || dx == 1 ? IRIS_RING : IRIS);
						if (dx == -1 && dy == 0) {
							col = GLINT;
						}
					} else if (r == vis - 1) {
						col = WHITE_SHADE;
					} else {
						col = ((c * 7 + r * 3 + seed) & 15) == 0 ? VEIN : WHITE;
					}
				} else if (r == vis || r == -vis - 1) {
					col = LID_EDGE;
				} else {
					col = r >= 0 ? LID : LID_SHADE;
				}
				g[r + 5][c + 1] = col;
			}
		}
		// Shut: the seam across the middle.
		if (open < 0.13F) {
			for (int c = 1; c < 11; c++) {
				g[5][c + 1] = LID_EDGE;
				g[4][c + 1] = LID_SHADE;
			}
		}
		// Lashes over the upper lid's edge.
		int lidTop = Math.round(4 * MathHelper.clamp(open, 0.0F, 1.0F));
		for (int c = 2; c < 10; c += 2) {
			int r = Math.min(4, Math.max(0, lidTop)) + 5;
			if (r < 10 && g[r][c + 1] != 0) {
				g[r][c + 1] = LASH;
			}
		}
		return g;
	}

	/** A small eye on a lump (5 x 3), its pupil following. */
	private static int[][] smallEye(Vec3d look) {
		int[][] g = new int[3][5];
		for (int c = 0; c < 5; c++) {
			g[0][c] = c == 0 || c == 4 ? 0 : LID_EDGE;
			g[1][c] = c == 0 || c == 4 ? LID_EDGE : WHITE;
			g[2][c] = c == 0 || c == 4 ? 0 : LID_EDGE;
		}
		int px = 2 + (int) Math.round(MathHelper.clamp(look.x * 2.0, -1.0, 1.0));
		g[1][px] = PUPIL;
		return g;
	}

	/** How far open each eye is on this client (entity id to 0 to 1), and the tick it was last stepped. */
	private static final java.util.Map<Integer, Float> OPEN = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Long> LAST = new java.util.HashMap<>();
}
