package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.gap.GapTimeline;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;

/**
 * Universe 4,096,113: the piece of the world round the target, rebuilt from its own blocks, recoloured into
 * cyans and hung upside down, with a mountain on it that points down at the target. Vertices are local to the
 * target column with heights mirrored about our surface, so the mirror of a point at height y sits at local
 * height −(y − s0) and the whole thing is placed by lifting it {@code s0 + lift} (see {@link ClientGap#mirrorY}).
 */
public final class MirrorWorld implements AutoCloseable {
	public static final int RADIUS = 72;
	/** Trees and buildings taller than this over the ground are cut off. */
	private static final int MAX_ABOVE = 40;
	private static final int DEPTH = 6;
	private static final int MOUNTAIN_SPREAD = 50;
	// Pre-mirror face directions: +y, -y, +x, -x, +z, -z.
	private static final int[][] DIRS = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
	private static final float[] SHADE = {1.0F, 0.5F, 0.72F, 0.72F, 0.84F, 0.84F};

	private final VertexBuffer buffer;
	private final int faces;
	public final int originX;
	public final int originZ;
	public final int surface;

	private MirrorWorld(VertexBuffer buffer, int faces, int originX, int originZ, int surface) {
		this.buffer = buffer;
		this.faces = faces;
		this.originX = originX;
		this.originZ = originZ;
		this.surface = surface;
	}

	public int faces() {
		return faces;
	}

	/** Height in blocks of the inverted mountain over a column {@code d} blocks from the target. */
	public static int mountain(double d) {
		return (int) Math.round(GapTimeline.PEAK * Math.exp(-d * d / MOUNTAIN_SPREAD));
	}

	public static MirrorWorld build(ClientWorld world, ClientGap gap) {
		int cx = gap.target.getX();
		int cz = gap.target.getZ();
		int s0 = gap.surface;
		int size = 2 * RADIUS + 1;
		int[] ground = new int[size * size];
		int[] tops = new int[size * size];
		int[] peaks = new int[size * size];
		int ymin = Integer.MAX_VALUE;
		int ymax = Integer.MIN_VALUE;
		int reach2 = (RADIUS + 2) * (RADIUS + 2);
		for (int i = 0; i < size; i++) {
			for (int k = 0; k < size; k++) {
				int dx = i - RADIUS;
				int dz = k - RADIUS;
				int c = i * size + k;
				if (dx * dx + dz * dz > reach2) {
					ground[c] = Integer.MIN_VALUE;
					continue;
				}
				int g = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx + dx, cz + dz) - 1;
				if (g <= world.getBottomY()) {
					// Not loaded on this client, or nothing there.
					ground[c] = Integer.MIN_VALUE;
					continue;
				}
				int t =Math.min(world.getTopY(Heightmap.Type.WORLD_SURFACE, cx + dx, cz + dz) - 1, g + MAX_ABOVE);
				int h = mountain(Math.sqrt(dx * dx + dz * dz));
				ground[c] = g;
				tops[c] = Math.max(t, g + h);
				peaks[c] = h;
				ymin = Math.min(ymin, g - DEPTH);
				ymax = Math.max(ymax, tops[c]);
			}
		}
		int height = Math.max(1, ymax - ymin + 1);
		int[] cells = new int[size * size * height];
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int i = 0; i < size; i++) {
			for (int k = 0; k < size; k++) {
				int c = i * size + k;
				if (ground[c] == Integer.MIN_VALUE) {
					continue;
				}
				int x = cx + i - RADIUS;
				int z = cz + k - RADIUS;
				int g = ground[c];
				for (int y = g - DEPTH; y <= tops[c]; y++) {
					int idx = (c * height) + (y - ymin);
					if (y > g && y <= g + peaks[c]) {
						// The mountain: grown from our ground before the whole thing is turned upside down.
						boolean tip = y == g + peaks[c];
						cells[idx] = 0x1000000 | noisy(tip ? 0x3BE8E2 : 0x1C6C77, x, y, z, 0.08F);
						continue;
					}
					BlockState state = world.getBlockState(pos.set(x, y, z));
					if (state.isAir()) {
						continue;
					}
					boolean leaves = state.isIn(BlockTags.LEAVES);
					if (!leaves && state.getFluidState().isEmpty() && !state.isFullCube(world, pos)) {
						continue;
					}
					int rgb = state.getMapColor(world, pos).color;
					cells[idx] = 0x1000000 | noisy(other(rgb), x, y, z, 0.06F);
				}
			}
		}

		BufferAllocator allocator = new BufferAllocator(1 << 22);
		BufferBuilder b = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
		int faces = 0;
		for (int i = 0; i < size; i++) {
			for (int k = 0; k < size; k++) {
				int c = i * size + k;
				if (ground[c] == Integer.MIN_VALUE) {
					continue;
				}
				for (int yy = 0; yy < height; yy++) {
					int cell = cells[c * height + yy];
					if (cell == 0) {
						continue;
					}
					for (int f = 0; f < 6; f++) {
						int ni = i + DIRS[f][0];
						int ny = yy + DIRS[f][1];
						int nk = k + DIRS[f][2];
						boolean open = ni < 0 || nk < 0 || ni >= size || nk >= size || ny < 0 || ny >= height
								|| ground[ni * size + nk] == Integer.MIN_VALUE || cells[(ni * size + nk) * height + ny] == 0;
						if (!open) {
							continue;
						}
						face(b, i - RADIUS, yy + ymin - s0, k - RADIUS, f, shade(cell & 0xFFFFFF, SHADE[f]));
						faces++;
					}
				}
			}
		}
		VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
		BuiltBuffer built = b.endNullable();
		if (built != null) {
			vb.bind();
			vb.upload(built);
			VertexBuffer.unbind();
		}
		allocator.close();
		return new MirrorWorld(vb, faces, cx, cz, s0);
	}

	/** One face of the block whose pre-mirror corner is (x, y, z) relative to the target column and our surface. */
	static void face(BufferBuilder b, float x, float y, float z, int f, int rgb) {
		face(b, x, y, z, 1.0F, f, rgb, 0.0F);
	}

	static void face(BufferBuilder b, float x, float y, float z, float s, int f, int rgb, float grow) {
		float x0 = x - grow;
		float y0 = y - grow;
		float z0 = z - grow;
		float x1 = x + s + grow;
		float y1 = y + s + grow;
		float z1 = z + s + grow;
		float[][] q = switch (f) {
			case 0 -> new float[][] {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
			case 1 -> new float[][] {{x0, y0, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}};
			case 2 -> new float[][] {{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}};
			case 3 -> new float[][] {{x0, y0, z0}, {x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}};
			case 4 -> new float[][] {{x0, y0, z1}, {x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}};
			default -> new float[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}};
		};
		float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
		int r = rgb >> 16 & 255;
		int g = rgb >> 8 & 255;
		int bl = rgb & 255;
		// Upside down: heights are negated and so is the normal's y.
		for (int v = 0; v < 4; v++) {
			b.vertex(q[v][0], -q[v][1], q[v][2]).texture(uv[v][0], uv[v][1]).color(r, g, bl, 255)
					.normal(DIRS[f][0], -DIRS[f][1], DIRS[f][2]);
		}
	}

	/** Our block's colour as it is in the other universe: swung round to the cyans, a little paler. */
	static int other(int rgb) {
		float[] hsv = java.awt.Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
		float hue = (180.0F + (hsv[0] * 360.0F - 120.0F) * 0.25F) / 360.0F;
		float sat = Math.min(1.0F, hsv[1] * 0.85F + 0.18F);
		float val = Math.max(0.18F, Math.min(0.95F, hsv[2] * 1.08F));
		return java.awt.Color.HSBtoRGB(hue, sat, val) & 0xFFFFFF;
	}

	static int shade(int rgb, float k) {
		int r = Math.min(255, Math.round((rgb >> 16 & 255) * k));
		int g = Math.min(255, Math.round((rgb >> 8 & 255) * k));
		int b = Math.min(255, Math.round((rgb & 255) * k));
		return r << 16 | g << 8 | b;
	}

	private static int noisy(int rgb, int x, int y, int z, float amount) {
		long h = x * 3129871L ^ z * 116129781L ^ y * 7919L;
		h = h * h * 42317861L + h * 11L;
		float n = 1.0F + amount * (((h >> 16) & 255) / 127.5F - 1.0F);
		return shade(rgb, n);
	}

	public void draw(ShaderProgram program, Matrix4f modelView, Matrix4f projection) {
		buffer.bind();
		buffer.draw(modelView, projection, program);
		VertexBuffer.unbind();
	}

	@Override
	public void close() {
		buffer.close();
	}
}
