package io.github.kortev.shootingstar.client.feed;

import io.github.kortev.shootingstar.client.render.Gfx;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;

/**
 * Painter's-algorithm renderer for the feed's solid models (coils, the round, rocks): polygons are
 * projected and flat-shaded on the CPU, sorted far to near, then drawn as triangles.
 */
final class Painter {
	/** Light direction for solid models, roughly from the upper left behind the camera. */
	static final double LX = -0.45;
	static final double LY = 0.72;
	static final double LZ = -0.53;

	private static final class Poly {
		double depth;
		final float[] xs = new float[4];
		final float[] ys = new float[4];
		final int[] colors = new int[4];
		int count;
	}

	private final List<Poly> polys = new ArrayList<>();
	private final List<Poly> pool = new ArrayList<>();
	private final double[] tmp = new double[3];

	void clear() {
		pool.addAll(polys);
		polys.clear();
	}

	private Poly obtain() {
		return pool.isEmpty() ? new Poly() : pool.remove(pool.size() - 1);
	}

	/**
	 * Adds a flat-shaded polygon (3 or 4 world-space points, packed xyz). {@code emissive} faces
	 * ignore lighting. Polygons with a vertex behind the camera are dropped.
	 */
	void face(View3D view, double[] p, int count, int baseColor, float ambient, boolean emissive) {
		Poly poly = obtain();
		double depthSum = 0;
		for (int i = 0; i < count; i++) {
			if (!view.project(p[i * 3], p[i * 3 + 1], p[i * 3 + 2], tmp)) {
				pool.add(poly);
				return;
			}
			poly.xs[i] = (float) tmp[0];
			poly.ys[i] = (float) tmp[1];
			depthSum += tmp[2];
		}
		poly.count = count;
		poly.depth = depthSum / count;
		int color = baseColor;
		if (!emissive) {
			// Face normal from the first three points.
			double ax = p[3] - p[0], ay = p[4] - p[1], az = p[5] - p[2];
			double bx = p[6] - p[0], by = p[7] - p[1], bz = p[8] - p[2];
			double nx = ay * bz - az * by;
			double ny = az * bx - ax * bz;
			double nz = ax * by - ay * bx;
			double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (nl > 1.0E-12) {
				nx /= nl;
				ny /= nl;
				nz /= nl;
			}
			// Faces are two-sided: flip the normal towards the camera.
			double cxv = view.ex - p[0], cyv = view.ey - p[1], czv = view.ez - p[2];
			if (nx * cxv + ny * cyv + nz * czv < 0) {
				nx = -nx;
				ny = -ny;
				nz = -nz;
			}
			double lambert = Math.max(0.0, nx * LX + ny * LY + nz * LZ);
			float shade = (float) Math.min(1.0, ambient + (1.0 - ambient) * lambert);
			color = shadeColor(baseColor, shade);
		}
		for (int i = 0; i < count; i++) {
			poly.colors[i] = color;
		}
		polys.add(poly);
	}

	/** Adds an already projected polygon (billboards such as rocks). */
	void projected(double depth, float[] xs, float[] ys, int[] colors, int count) {
		Poly poly = obtain();
		poly.depth = depth;
		poly.count = count;
		System.arraycopy(xs, 0, poly.xs, 0, count);
		System.arraycopy(ys, 0, poly.ys, 0, count);
		System.arraycopy(colors, 0, poly.colors, 0, count);
		polys.add(poly);
	}

	void flush(Matrix4f m) {
		if (polys.isEmpty()) {
			return;
		}
		polys.sort(Comparator.comparingDouble((Poly poly) -> poly.depth).reversed());
		BufferBuilder b = Gfx.triangles();
		for (Poly poly : polys) {
			for (int i = 1; i + 1 < poly.count; i++) {
				b.vertex(m, poly.xs[0], poly.ys[0], 0).color(poly.colors[0]);
				b.vertex(m, poly.xs[i], poly.ys[i], 0).color(poly.colors[i]);
				b.vertex(m, poly.xs[i + 1], poly.ys[i + 1], 0).color(poly.colors[i + 1]);
			}
		}
		Gfx.draw(b);
		clear();
	}

	static int shadeColor(int argb, float shade) {
		int a = (argb >>> 24) & 0xFF;
		int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * shade));
		int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * shade));
		int b = Math.min(255, (int) ((argb & 0xFF) * shade));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
