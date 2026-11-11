package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.FleshGrowthEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * The flesh growing out of the loop floor's walls (package 0.1g), as voxels a sixteenth of a block: lumps that come out of the wall (and the
 * floor) through their own roots, veins creeping out over the surface round them, and nothing floating clear of it. Each voxel is one colour
 * picked as a block's texture would be (dark in the folds, light on what faces up, a wet glint on some tops); the game's own face shading
 * and light do the rest.
 *
 * <p>The frame is the growth's own: {@code x} along the wall, {@code y} up, {@code z} out of the wall (the wall's face is z 0); a floor lump's
 * floor is y 0. Meshes are made once a shape and a stage of its growing, and kept.
 */
final class FleshVoxels {
	private FleshVoxels() {
	}

	/** One face: its corners (pixel units), its normal, its colour. */
	record Face(float[] c, float nx, float ny, float nz, int rgb) {
	}

	static final int FL0 = PixelArt.rgb(120, 24, 32);
	static final int FL1 = PixelArt.rgb(146, 34, 40);
	static final int FL2 = PixelArt.rgb(168, 50, 48);
	static final int FL3 = PixelArt.rgb(188, 70, 62);
	static final int LIGHT = PixelArt.rgb(218, 116, 104);
	static final int WET = PixelArt.rgb(246, 200, 188);
	static final int DARK = PixelArt.rgb(78, 14, 22);
	static final int VEIN = PixelArt.rgb(96, 22, 50);
	static final int VEIN_LIGHT = PixelArt.rgb(132, 38, 70);

	/** The box the voxels may fill (pixels): x and y from -R to R (a floor lump's y from 0), z from 0. */
	static final int R = 14;
	static final int D = 12;
	static final int STAGES = 8;

	private static final Map<Long, List<Face>> CACHE = new HashMap<>();

	/** The faces of the growth of {@code kind} and {@code seed} at {@code stage} of {@link #STAGES} of its growing ({@code breath} for the wall that breathes, 0 to 3). */
	static List<Face> mesh(int kind, int seed, int stage, int breath) {
		long key = ((long) seed << 20) ^ ((long) kind << 12) ^ ((long) stage << 4) ^ breath;
		return CACHE.computeIfAbsent(key, k -> build(kind, seed, stage, breath));
	}

	static void draw(VertexConsumer vc, MatrixStack.Entry e, List<Face> faces, int light) {
		for (Face f : faces) {
			PixelArt.quad(vc, e, f.c(), f.rgb(), light, f.nx(), f.ny(), f.nz());
		}
	}

	private static final class Grid {
		final boolean floor;
		final int y0;
		final int[][][] cell = new int[2 * R][2 * R][D];
		final float[][][] born = new float[2 * R][2 * R][D];

		Grid(boolean floor) {
			this.floor = floor;
			this.y0 = floor ? 0 : -R;
		}

		boolean in(int x, int y, int z) {
			return x >= -R && x < R && y >= y0 && y < y0 + 2 * R && z >= 0 && z < D;
		}

		int get(int x, int y, int z) {
			return in(x, y, z) ? cell[x + R][y - y0][z] : 0;
		}

		void set(int x, int y, int z, int v, float b) {
			if (in(x, y, z) && cell[x + R][y - y0][z] == 0) {
				cell[x + R][y - y0][z] = v;
				born[x + R][y - y0][z] = b;
			}
		}
	}

	/** 1 flesh, 2 a vein (a root over the surface). */
	private static List<Face> build(int kind, int seed, int stage, int breath) {
		Random rng = new Random(seed * 31L + kind);
		boolean floor = kind == FleshGrowthEntity.FLOOR;
		Grid g = new Grid(floor);
		List<double[]> balls = new ArrayList<>();
		double size = kind == FleshGrowthEntity.BREATH ? 0.0 : floor ? 0.8 + 0.35 * rng.nextDouble() : 1.0;
		if (kind == FleshGrowthEntity.BREATH) {
			// A swelling of the wall itself: a low dome a pixel or three out, in and out with the breath.
			double h = 1.0 + breath;
			balls.add(new double[] {0, 0, -9.0 + h, 10.0});
		} else if (floor) {
			// A heap in the corner: one lobe against the wall on the floor, two or three smaller round it.
			balls.add(new double[] {rng.nextInt(3) - 1, 4.0 * size, 2.0 * size, 6.5 * size});
			int n = 2 + rng.nextInt(2);
			for (int i = 0; i < n; i++) {
				double side = (i % 2 == 0 ? 1 : -1) * (4.0 + 3.0 * rng.nextDouble()) * size;
				balls.add(new double[] {side, (1.5 + 4.0 * rng.nextDouble()) * size, (1.0 + 4.0 * rng.nextDouble()) * size, (3.2 + 2.0 * rng.nextDouble()) * size});
			}
		} else {
			// A bulge out of the wall's face: lobes pressed flat against it.
			balls.add(new double[] {0, 0, -1.0, 7.0});
			int n = 2 + rng.nextInt(3);
			for (int i = 0; i < n; i++) {
				double a = rng.nextDouble() * Math.PI * 2.0;
				balls.add(new double[] {Math.cos(a) * 5.5, Math.sin(a) * 4.5, -0.5 + rng.nextDouble() * 1.5, 3.5 + 1.8 * rng.nextDouble()});
			}
		}
		// The lobes, as voxels (a sum of round fields over one).
		double ax = balls.get(0)[0];
		double ay = floor ? 0.0 : balls.get(0)[1];
		for (int x = -R; x < R; x++) {
			for (int y = g.y0; y < g.y0 + 2 * R; y++) {
				for (int z = 0; z < D; z++) {
					double f = 0.0;
					double cx = x + 0.5;
					double cy = y + 0.5;
					double cz = z + 0.5;
					for (double[] b : balls) {
						double d2 = (cx - b[0]) * (cx - b[0]) + (cy - b[1]) * (cy - b[1]) * 1.1 + (cz - b[2]) * (cz - b[2]) * 1.25;
						f += b[3] * b[3] / Math.max(0.3, d2);
					}
					// A little lumpiness in the surface.
					f *= 0.93 + 0.14 * noise(x, y, z, seed);
					if (f > 1.0) {
						double dist = Math.sqrt((cx - ax) * (cx - ax) + (cy - ay) * (cy - ay) + cz * cz);
						g.set(x, y, z, 1, (float) (dist / (R * 1.2)));
					}
				}
			}
		}
		// The roots: veins out over the wall (and the floor), a pixel thick, from the lump's foot.
		if (kind != FleshGrowthEntity.BREATH) {
			int roots = 4 + rng.nextInt(3);
			for (int i = 0; i < roots; i++) {
				double a = rng.nextDouble() * Math.PI * 2.0;
				boolean onFloor = floor && i % 2 == 1;
				double px = ax;
				double py = floor ? (onFloor ? 0.0 : 2.0) : ay;
				double pz = onFloor ? 2.0 : 0.0;
				double len = 8.0 + rng.nextDouble() * 7.0;
				for (double s = 0; s < len; s += 0.5) {
					a += (rng.nextDouble() - 0.5) * 0.5;
					if (onFloor) {
						px += Math.cos(a) * 0.5;
						pz += Math.abs(Math.sin(a)) * 0.5;
						g.set((int) Math.floor(px), 0, (int) Math.floor(pz), 2, (float) (0.5 + s / len * 0.5));
					} else {
						px += Math.cos(a) * 0.5;
						py += (floor ? Math.abs(Math.sin(a)) : Math.sin(a)) * 0.5;
						g.set((int) Math.floor(px), (int) Math.floor(py), 0, 2, (float) (0.5 + s / len * 0.5));
					}
				}
			}
		}
		// Grown to this stage: what has been born by now.
		float upTo = (stage + 1) / (float) STAGES;
		List<Face> out = new ArrayList<>();
		int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int x = -R; x < R; x++) {
			for (int y = g.y0; y < g.y0 + 2 * R; y++) {
				for (int z = 0; z < D; z++) {
					int v = g.get(x, y, z);
					if (v == 0 || g.born[x + R][y - g.y0][z] > upTo) {
						continue;
					}
					for (int[] d : dirs) {
						int nx = x + d[0];
						int ny = y + d[1];
						int nz = z + d[2];
						if (nz < 0 || floor && ny < 0) {
							// (Against the wall or the floor: the face is in it.)
							continue;
						}
						if (g.in(nx, ny, nz) && g.get(nx, ny, nz) != 0 && g.born[nx + R][ny - g.y0][nz] <= upTo) {
							continue;
						}
						out.add(new Face(corners(x, y, z, d), d[0], d[1], d[2], colour(g, x, y, z, d, v, seed, kind)));
					}
				}
			}
		}
		return out;
	}

	/** How many of the 26 round a voxel are full (a fold is dark). */
	private static int crowd(Grid g, int x, int y, int z) {
		int n = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if ((dx != 0 || dy != 0 || dz != 0) && g.get(x + dx, y + dy, z + dz) == 1) {
						n++;
					}
				}
			}
		}
		return n;
	}

	private static int colour(Grid g, int x, int y, int z, int[] d, int v, int seed, int kind) {
		double n = noise(x * 3 + d[0], y * 3 + d[1], z * 3 + d[2], seed + 17);
		if (v == 2) {
			return n < 0.3 ? VEIN_LIGHT : VEIN;
		}
		if (kind == FleshGrowthEntity.BREATH) {
			// The wallpaper going to flesh: the edges keep the paper's colour.
			double r = Math.sqrt(x * x + y * y);
			if (r > 7.5) {
				return n < 0.5 ? PixelArt.rgb(213, 196, 150) : PixelArt.rgb(198, 160, 128);
			}
			return r > 5.5 ? PixelArt.rgb(190, 120, 100) : (n < 0.5 ? FL2 : FL3);
		}
		int crowd = crowd(g, x, y, z);
		boolean up = d[1] == 1;
		if (up && crowd < 12 && n > 0.88) {
			return WET;
		}
		if (up) {
			return n < 0.35 ? LIGHT : n < 0.8 ? FL3 : FL2;
		}
		if (d[1] == -1) {
			return n < 0.5 ? DARK : FL0;
		}
		// The sides: deep red, darker low down and in the folds between lobes (where the voxel above sticks out further).
		boolean overhung = g.get(x, y + 1, z + Math.max(0, d[2])) == 1 && d[2] == 1;
		if (overhung || crowd > 19) {
			return n < 0.45 ? FL0 : FL1;
		}
		return n < 0.4 ? FL1 : n < 0.85 ? FL2 : FL3;
	}

	/** The face of voxel (x, y, z) toward {@code d}, counter-clockwise seen from outside, in blocks. */
	private static float[] corners(int x, int y, int z, int[] d) {
		float p = PixelArt.P;
		float x0 = x * p;
		float x1 = (x + 1) * p;
		float y0 = y * p;
		float y1 = (y + 1) * p;
		float z0 = z * p;
		float z1 = (z + 1) * p;
		if (d[0] == 1) {
			return new float[] {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1};
		}
		if (d[0] == -1) {
			return new float[] {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0};
		}
		if (d[1] == 1) {
			return new float[] {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0};
		}
		if (d[1] == -1) {
			return new float[] {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1};
		}
		if (d[2] == 1) {
			return new float[] {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1};
		}
		return new float[] {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0};
	}

	/** The front of the lump near its top (pixel x, y, and the depth of its face), for an eye. */
	static int[] eyeSpot(int kind, int seed) {
		List<Face> faces = mesh(kind, seed, STAGES - 1, 0);
		int bx = 0;
		int by = 0;
		float bz = -1.0F;
		for (Face f : faces) {
			if (f.nz() == 1.0F) {
				int x = Math.round(f.c()[0] * 16.0F);
				int y = Math.round(f.c()[1] * 16.0F);
				float z = f.c()[2];
				// Toward the middle and up: the front-most face there.
				float score = z * 16.0F - Math.abs(x) * 0.3F + y * 0.12F;
				if (Math.abs(x) <= 3 && score > bz) {
					bz = score;
					bx = x;
					by = y;
				}
			}
		}
		float depth = 0.0F;
		for (Face f : faces) {
			if (f.nz() == 1.0F && Math.round(f.c()[0] * 16.0F) == bx && Math.round(f.c()[1] * 16.0F) == by) {
				depth = f.c()[2];
			}
		}
		return new int[] {bx, by, Math.round(depth * 16.0F)};
	}

	private static double noise(int x, int y, int z, int seed) {
		int h = x * 374761393 + y * 668265263 + z * 1274126177 + seed * 362437;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (double) 0xFFFFFF;
	}
}
