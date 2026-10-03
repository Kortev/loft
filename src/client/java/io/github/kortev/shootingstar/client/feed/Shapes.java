package io.github.kortev.shootingstar.client.feed;

import io.github.kortev.shootingstar.client.render.Gfx;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/** Planets, orbits and the solid models of the feed. */
final class Shapes {
	private Shapes() {
	}

	// --- planets ---------------------------------------------------------------------------

	/** A sphere with an orthonormal frame: A points at longitude 0, N at the north pole. */
	static final class Planet {
		double x, y, z, r;
		double ax = 1, ay = 0, az = 0;
		double nx = 0, ny = 1, nz = 0;
		double bx = 0, by = 0, bz = 1;
		double spin;

		Planet(double x, double y, double z, double r) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.r = r;
		}

		/** Tilts the pole: first about the world X axis, then about Z. */
		Planet tilt(double aboutX, double aboutZ) {
			double cx = Math.cos(aboutX), sx = Math.sin(aboutX);
			double cz = Math.cos(aboutZ), sz = Math.sin(aboutZ);
			double[][] basis = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
			for (double[] v : basis) {
				double y1 = v[1] * cx - v[2] * sx;
				double z1 = v[1] * sx + v[2] * cx;
				double x2 = v[0] * cz - y1 * sz;
				double y2 = v[0] * sz + y1 * cz;
				v[0] = x2;
				v[1] = y2;
				v[2] = z1;
			}
			ax = basis[0][0];
			ay = basis[0][1];
			az = basis[0][2];
			nx = basis[1][0];
			ny = basis[1][1];
			nz = basis[1][2];
			bx = basis[2][0];
			by = basis[2][1];
			bz = basis[2][2];
			return this;
		}

		/** Unit direction from the centre for a latitude/longitude (radians), east to the right. */
		void dir(double lat, double lon, double[] out) {
			double cl = Math.cos(lat);
			double sl = Math.sin(lat);
			double co = Math.cos(lon + spin);
			double so = Math.sin(lon + spin);
			out[0] = cl * co * ax + sl * nx - cl * so * bx;
			out[1] = cl * co * ay + sl * ny - cl * so * by;
			out[2] = cl * co * az + sl * nz - cl * so * bz;
		}

		/** Point on a circle of radius {@code radius} in the equatorial plane, angle in radians. */
		void equator(double radius, double angle, double[] out) {
			double c = Math.cos(angle);
			double s = Math.sin(angle);
			out[0] = x + radius * (c * ax - s * bx);
			out[1] = y + radius * (c * ay - s * by);
			out[2] = z + radius * (c * az - s * bz);
		}

		double distanceTo(View3D v) {
			double dx = x - v.ex, dy = y - v.ey, dz = z - v.ez;
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		/** True when the planet blocks the line of sight from the camera to the point. */
		boolean occludes(View3D v, double px, double py, double pz) {
			double dx = px - v.ex, dy = py - v.ey, dz = pz - v.ez;
			double ox = v.ex - x, oy = v.ey - y, oz = v.ez - z;
			double a = dx * dx + dy * dy + dz * dz;
			double b = 2.0 * (dx * ox + dy * oy + dz * oz);
			double c = ox * ox + oy * oy + oz * oz - r * r;
			double disc = b * b - 4.0 * a * c;
			if (disc < 0) {
				return false;
			}
			double t = (-b - Math.sqrt(disc)) / (2.0 * a);
			return t > 0.0 && t < 0.999;
		}
	}

	/**
	 * Draws a textured, lit sphere. Lighting comes from {@code sun} (unit vector towards the sun);
	 * {@code ambient} is the night-side floor. Back faces are culled, so no sorting is needed.
	 */
	static void planet(Matrix4f m, View3D v, Planet p, Identifier texture, double[] sun, float ambient, int lat, int lon,
			int tint) {
		int cols = lon + 1;
		int rows = lat + 1;
		float[] sx = new float[rows * cols];
		float[] sy = new float[rows * cols];
		float[] light = new float[rows * cols];
		boolean[] ok = new boolean[rows * cols];
		double[] d = new double[3];
		double[] out = new double[3];
		for (int i = 0; i < rows; i++) {
			double la = -Math.PI / 2 + Math.PI * i / lat;
			for (int j = 0; j < cols; j++) {
				double lo = Math.PI * 2 * j / lon;
				p.dir(la, lo, d);
				int k = i * cols + j;
				ok[k] = v.project(p.x + d[0] * p.r, p.y + d[1] * p.r, p.z + d[2] * p.r, out);
				sx[k] = (float) out[0];
				sy[k] = (float) out[1];
				double ndots = d[0] * sun[0] + d[1] * sun[1] + d[2] * sun[2];
				double soft = smoothstep(-0.12, 0.25, ndots);
				light[k] = (float) (ambient + (1.0 - ambient) * (0.35 * soft + 0.65 * Math.max(0.0, ndots)));
			}
		}
		int tr = (tint >> 16) & 0xFF, tg = (tint >> 8) & 0xFF, tb = tint & 0xFF, ta = (tint >>> 24) & 0xFF;
		BufferBuilder b = Gfx.texQuads();
		for (int i = 0; i < lat; i++) {
			double la = -Math.PI / 2 + Math.PI * (i + 0.5) / lat;
			for (int j = 0; j < lon; j++) {
				int k00 = i * cols + j;
				int k01 = k00 + 1;
				int k10 = k00 + cols;
				int k11 = k10 + 1;
				if (!ok[k00] || !ok[k01] || !ok[k10] || !ok[k11]) {
					continue;
				}
				p.dir(la, Math.PI * 2 * (j + 0.5) / lon, d);
				double px = p.x + d[0] * p.r, py = p.y + d[1] * p.r, pz = p.z + d[2] * p.r;
				if (d[0] * (v.ex - px) + d[1] * (v.ey - py) + d[2] * (v.ez - pz) <= 0) {
					continue;
				}
				float u0 = (float) j / lon;
				float u1 = (float) (j + 1) / lon;
				float v0 = 1.0F - (float) i / lat;
				float v1 = 1.0F - (float) (i + 1) / lat;
				vertex(b, m, sx[k00], sy[k00], u0, v0, light[k00], tr, tg, tb, ta);
				vertex(b, m, sx[k10], sy[k10], u0, v1, light[k10], tr, tg, tb, ta);
				vertex(b, m, sx[k11], sy[k11], u1, v1, light[k11], tr, tg, tb, ta);
				vertex(b, m, sx[k01], sy[k01], u1, v0, light[k01], tr, tg, tb, ta);
			}
		}
		Gfx.drawTex(b, texture);
	}

	private static void vertex(BufferBuilder b, Matrix4f m, float x, float y, float u, float v, float light, int r, int g,
			int bl, int a) {
		int lr = Math.min(255, (int) (r * light));
		int lg = Math.min(255, (int) (g * light));
		int lb = Math.min(255, (int) (bl * light));
		b.vertex(m, x, y, 0).texture(u, v).color(lr, lg, lb, a);
	}

	/** Atmospheric limb glow, brighter on the side facing the sun. Additive. */
	static void atmosphere(Matrix4f m, View3D v, Planet p, double[] sun, int rgb, float strength, float thickness) {
		double[] c = new double[3];
		double[] s = new double[3];
		if (!v.project(p.x, p.y, p.z, c)) {
			return;
		}
		double radius = v.sphereRadius(p.r, p.distanceTo(v));
		double sunAngle = 0.0;
		if (v.project(p.x + sun[0] * p.r, p.y + sun[1] * p.r, p.z + sun[2] * p.r, s)) {
			sunAngle = Math.atan2(s[1] - c[1], s[0] - c[0]);
		}
		float facing = (float) MathHelper.clamp(1.0 - (sun[0] * v.fx + sun[1] * v.fy + sun[2] * v.fz), 0.0, 1.0);
		int segments = 120;
		float cx = (float) c[0];
		float cy = (float) c[1];
		float r0 = (float) (radius * 0.94);
		float r1 = (float) radius;
		float r2 = (float) (radius * (1.0 + thickness));
		BufferBuilder b = Gfx.quads();
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2 * i / segments;
			double a1 = Math.PI * 2 * (i + 1) / segments;
			float w0 = (float) (0.15 + 0.85 * Math.max(0.0, Math.cos(a0 - sunAngle)) * facing + 0.15 * (1 - facing));
			float w1 = (float) (0.15 + 0.85 * Math.max(0.0, Math.cos(a1 - sunAngle)) * facing + 0.15 * (1 - facing));
			int in0 = rgb & 0xFFFFFF;
			int peak0 = Gfx.fade(0xFF000000 | rgb, strength * w0);
			int peak1 = Gfx.fade(0xFF000000 | rgb, strength * w1);
			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			Gfx.quad(b, m, cx + c0 * r0, cy + s0 * r0, cx + c0 * r1, cy + s0 * r1, cx + c1 * r1, cy + s1 * r1,
					cx + c1 * r0, cy + s1 * r0, in0, peak0, peak1, in0);
			Gfx.quad(b, m, cx + c0 * r1, cy + s0 * r1, cx + c0 * r2, cy + s0 * r2, cx + c1 * r2, cy + s1 * r2,
					cx + c1 * r1, cy + s1 * r1, peak0, in0, in0, peak1);
		}
		Gfx.additive();
		Gfx.draw(b);
		Gfx.alpha();
	}

	/**
	 * Polyline of a circle in the planet's equatorial plane from angle {@code from} to {@code to},
	 * hidden where the planet is in front of it.
	 */
	static void orbit(Matrix4f m, View3D v, Planet p, double radius, double from, double to, int segments, float width,
			int color) {
		double[] pt = new double[3];
		double[] prev = new double[3];
		double[] cur = new double[3];
		boolean havePrev = false;
		BufferBuilder b = Gfx.quads();
		for (int k = 0; k <= segments; k++) {
			double angle = from + (to - from) * k / segments;
			p.equator(radius, angle, pt);
			boolean visible = !p.occludes(v, pt[0], pt[1], pt[2]) && v.project(pt[0], pt[1], pt[2], cur);
			if (visible && havePrev) {
				Gfx.line(b, m, (float) prev[0], (float) prev[1], (float) cur[0], (float) cur[1], width, color);
			}
			if (visible) {
				prev[0] = cur[0];
				prev[1] = cur[1];
			}
			havePrev = visible;
		}
		Gfx.draw(b);
	}

	// --- solid models ----------------------------------------------------------------------

	/** Position and orientation for placing a model; axes must be orthonormal. */
	static final class Pose {
		double ox, oy, oz;
		double xx = 1, xy, xz;
		double yx, yy = 1, yz;
		double zx, zy, zz = 1;
		double scale = 1;

		Pose at(double x, double y, double z) {
			ox = x;
			oy = y;
			oz = z;
			return this;
		}

		/** Points the model's +Z along {@code forward}, keeping +Y near {@code up}. */
		Pose facing(double fx, double fy, double fz, double ux, double uy, double uz) {
			double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
			zx = fx / fl;
			zy = fy / fl;
			zz = fz / fl;
			// x = up x forward
			double x1 = uy * zz - uz * zy;
			double y1 = uz * zx - ux * zz;
			double z1 = ux * zy - uy * zx;
			double xl = Math.sqrt(x1 * x1 + y1 * y1 + z1 * z1);
			xx = x1 / xl;
			xy = y1 / xl;
			xz = z1 / xl;
			yx = zy * xz - zz * xy;
			yy = zz * xx - zx * xz;
			yz = zx * xy - zy * xx;
			return this;
		}

		/** Spins the model about its own forward axis. */
		Pose roll(double radians) {
			double c = Math.cos(radians), s = Math.sin(radians);
			double nxx = xx * c + yx * s, nxy = xy * c + yy * s, nxz = xz * c + yz * s;
			double nyx = yx * c - xx * s, nyy = yy * c - xy * s, nyz = yz * c - xz * s;
			xx = nxx;
			xy = nxy;
			xz = nxz;
			yx = nyx;
			yy = nyy;
			yz = nyz;
			return this;
		}

		Pose scale(double scale) {
			this.scale = scale;
			return this;
		}

		void apply(double lx, double ly, double lz, double[] out, int index) {
			out[index * 3] = ox + scale * (lx * xx + ly * yx + lz * zx);
			out[index * 3 + 1] = oy + scale * (lx * xy + ly * yy + lz * zy);
			out[index * 3 + 2] = oz + scale * (lx * xz + ly * yz + lz * zz);
		}
	}

	static final int HULL = 0xFF2C2A2E;
	static final int HULL_DARK = 0xFF1A191C;
	static final int ORANGE = 0xFFFF7A1E;
	static final int ORANGE_HOT = 0xFFFFC27A;

	/**
	 * The SS-03 round: an octagonal dart one unit long along +Z (nose forward) with four swept
	 * tail fins and glowing coil bands.
	 */
	static void projectile(Painter painter, View3D v, Pose pose, float glow) {
		double[] p = new double[12];
		int sides = 8;
		double radius = 0.045;
		double tail = -0.5;
		double shoulder = 0.34;
		double nose = 0.5;
		for (int i = 0; i < sides; i++) {
			double a0 = Math.PI * 2 * (i + 0.5) / sides;
			double a1 = Math.PI * 2 * (i + 1.5) / sides;
			double x0 = Math.cos(a0) * radius, y0 = Math.sin(a0) * radius;
			double x1 = Math.cos(a1) * radius, y1 = Math.sin(a1) * radius;
			pose.apply(x0, y0, tail, p, 0);
			pose.apply(x1, y1, tail, p, 1);
			pose.apply(x1, y1, shoulder, p, 2);
			pose.apply(x0, y0, shoulder, p, 3);
			painter.face(v, p, 4, i % 2 == 0 ? HULL : HULL_DARK, 0.28F, false);
			pose.apply(x0, y0, shoulder, p, 0);
			pose.apply(x1, y1, shoulder, p, 1);
			pose.apply(0, 0, nose, p, 2);
			painter.face(v, p, 3, HULL, 0.3F, false);
			// Tail cap glows where the coils pushed on it.
			pose.apply(x0, y0, tail, p, 0);
			pose.apply(x1, y1, tail, p, 1);
			pose.apply(0, 0, tail, p, 2);
			painter.face(v, p, 3, Gfx.lerpColor(glow, 0xFF3A2A20, ORANGE_HOT), 1.0F, true);
		}
		// Coil bands.
		for (double z : new double[] {-0.36, -0.12, 0.12}) {
			for (int i = 0; i < sides; i++) {
				double a0 = Math.PI * 2 * (i + 0.5) / sides;
				double a1 = Math.PI * 2 * (i + 1.5) / sides;
				double rr = radius * 1.08;
				pose.apply(Math.cos(a0) * rr, Math.sin(a0) * rr, z, p, 0);
				pose.apply(Math.cos(a1) * rr, Math.sin(a1) * rr, z, p, 1);
				pose.apply(Math.cos(a1) * rr, Math.sin(a1) * rr, z + 0.018, p, 2);
				pose.apply(Math.cos(a0) * rr, Math.sin(a0) * rr, z + 0.018, p, 3);
				painter.face(v, p, 4, ORANGE, 1.0F, true);
			}
		}
		// Panel line along the spine.
		double rs = radius * 1.04;
		pose.apply(-0.006, rs, -0.45, p, 0);
		pose.apply(0.006, rs, -0.45, p, 1);
		pose.apply(0.006, rs, 0.3, p, 2);
		pose.apply(-0.006, rs, 0.3, p, 3);
		painter.face(v, p, 4, ORANGE, 1.0F, true);
		// Four swept fins.
		for (int f = 0; f < 4; f++) {
			double a = Math.PI / 2 * f + Math.PI / 4;
			double c = Math.cos(a), s = Math.sin(a);
			pose.apply(c * radius, s * radius, -0.24, p, 0);
			pose.apply(c * 0.17, s * 0.17, -0.42, p, 1);
			pose.apply(c * 0.17, s * 0.17, -0.53, p, 2);
			pose.apply(c * radius, s * radius, -0.5, p, 3);
			painter.face(v, p, 4, HULL, 0.32F, false);
			pose.apply(c * 0.165, s * 0.165, -0.43, p, 0);
			pose.apply(c * 0.178, s * 0.178, -0.43, p, 1);
			pose.apply(c * 0.178, s * 0.178, -0.535, p, 2);
			pose.apply(c * 0.165, s * 0.165, -0.535, p, 3);
			painter.face(v, p, 4, ORANGE, 1.0F, true);
		}
	}

	/**
	 * One accelerator coil: a square-section ring around the track axis (+Z) at {@code z}, with a
	 * strut down to the anchor beam. {@code lit} is how many of its 12 lamps are on.
	 */
	static void coil(Painter painter, View3D v, Pose track, double z, double rIn, double rOut, double depth, int lit) {
		double[] p = new double[12];
		int segments = 24;
		double zf = z - depth / 2;
		double zb = z + depth / 2;
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2 * i / segments;
			double a1 = Math.PI * 2 * (i + 1) / segments;
			double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
			// front annulus
			track.apply(c0 * rIn, s0 * rIn, zf, p, 0);
			track.apply(c0 * rOut, s0 * rOut, zf, p, 1);
			track.apply(c1 * rOut, s1 * rOut, zf, p, 2);
			track.apply(c1 * rIn, s1 * rIn, zf, p, 3);
			painter.face(v, p, 4, HULL, 0.35F, false);
			// outer rim
			track.apply(c0 * rOut, s0 * rOut, zf, p, 0);
			track.apply(c0 * rOut, s0 * rOut, zb, p, 1);
			track.apply(c1 * rOut, s1 * rOut, zb, p, 2);
			track.apply(c1 * rOut, s1 * rOut, zf, p, 3);
			painter.face(v, p, 4, HULL_DARK, 0.3F, false);
			// inner rim
			track.apply(c0 * rIn, s0 * rIn, zf, p, 0);
			track.apply(c0 * rIn, s0 * rIn, zb, p, 1);
			track.apply(c1 * rIn, s1 * rIn, zb, p, 2);
			track.apply(c1 * rIn, s1 * rIn, zf, p, 3);
			painter.face(v, p, 4, HULL_DARK, 0.25F, false);
			// back annulus
			track.apply(c0 * rIn, s0 * rIn, zb, p, 0);
			track.apply(c0 * rOut, s0 * rOut, zb, p, 1);
			track.apply(c1 * rOut, s1 * rOut, zb, p, 2);
			track.apply(c1 * rIn, s1 * rIn, zb, p, 3);
			painter.face(v, p, 4, HULL, 0.35F, false);
			// lamps on the front face, every other segment
			if (i % 2 == 0) {
				double rm0 = rIn + (rOut - rIn) * 0.3;
				double rm1 = rIn + (rOut - rIn) * 0.7;
				double am0 = a0 + (a1 - a0) * 0.15;
				double am1 = a1 - (a1 - a0) * 0.15;
				track.apply(Math.cos(am0) * rm0, Math.sin(am0) * rm0, zf - 0.002, p, 0);
				track.apply(Math.cos(am0) * rm1, Math.sin(am0) * rm1, zf - 0.002, p, 1);
				track.apply(Math.cos(am1) * rm1, Math.sin(am1) * rm1, zf - 0.002, p, 2);
				track.apply(Math.cos(am1) * rm0, Math.sin(am1) * rm0, zf - 0.002, p, 3);
				painter.face(v, p, 4, i / 2 < lit ? ORANGE : 0xFF3A2418, 1.0F, true);
			}
		}
		// Strut down to the anchor beam.
		double w = (rOut - rIn) * 0.45;
		double top = -rOut + 0.01;
		double bottom = -rOut * 9;
		box(painter, v, track, -w, bottom, z - depth * 0.35, w, top, z + depth * 0.35, HULL_DARK);
	}

	/** Axis-aligned box in pose space. */
	static void box(Painter painter, View3D v, Pose pose, double x0, double y0, double z0, double x1, double y1, double z1,
			int color) {
		double[] p = new double[12];
		double[][] faces = {
				{x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0},
				{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
				{x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
				{x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0},
				{x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1},
				{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}};
		for (double[] f : faces) {
			for (int i = 0; i < 4; i++) {
				pose.apply(f[i * 3], f[i * 3 + 1], f[i * 3 + 2], p, i);
			}
			painter.face(v, p, 4, color, 0.3F, false);
		}
	}

	static double smoothstep(double edge0, double edge1, double x) {
		double t = MathHelper.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
}
