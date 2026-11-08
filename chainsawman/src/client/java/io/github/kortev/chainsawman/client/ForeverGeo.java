package io.github.kortev.chainsawman.client;

import java.util.List;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The shapes the Eternity Devil is drawn with ({@link ForeverHubRenderer}): lumpy blobs of flesh, eyeballs and their lids, boxes (the
 * teeth), flat strips (the cracks) and square-section ropes (the sinews), written straight into a vertex consumer.
 */
final class ForeverGeo {
	private ForeverGeo() {
	}

	/** A vertex sink: where it writes, the matrices, the light and a tint. */
	static final class Pen {
		final VertexConsumer vc;
		final MatrixStack.Entry entry;
		int light;
		int r = 255;
		int g = 255;
		int b = 255;
		int a = 255;

		Pen(VertexConsumer vc, MatrixStack.Entry entry, int light) {
			this.vc = vc;
			this.entry = entry;
			this.light = light;
		}

		Pen tint(float r, float g, float b, float a) {
			this.r = Math.round(MathHelper.clamp(r, 0.0F, 1.0F) * 255.0F);
			this.g = Math.round(MathHelper.clamp(g, 0.0F, 1.0F) * 255.0F);
			this.b = Math.round(MathHelper.clamp(b, 0.0F, 1.0F) * 255.0F);
			this.a = Math.round(MathHelper.clamp(a, 0.0F, 1.0F) * 255.0F);
			return this;
		}

		void v(double x, double y, double z, float u, float v, double nx, double ny, double nz) {
			vc.vertex(entry, (float) x, (float) y, (float) z).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light)
					.normal(entry, (float) nx, (float) ny, (float) nz);
		}

		/** A quad with the normal given (for ropes, whose faces are lit from the middle of the rope outward). */
		void quadN(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, Vec3d n, float u0, float v0, float u1, float v1) {
			v(p0.x, p0.y, p0.z, u0, v1, n.x, n.y, n.z);
			v(p1.x, p1.y, p1.z, u1, v1, n.x, n.y, n.z);
			v(p2.x, p2.y, p2.z, u1, v0, n.x, n.y, n.z);
			v(p3.x, p3.y, p3.z, u0, v0, n.x, n.y, n.z);
		}

		/** A quad {@code p0..p3} (counter-clockwise from outside) with its flat normal and the uv corners (u0,v0)-(u1,v1). */
		void quad(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, float u0, float v0, float u1, float v1) {
			Vec3d n = p1.subtract(p0).crossProduct(p3.subtract(p0));
			if (n.lengthSquared() < 1.0E-12) {
				return;
			}
			n = n.normalize();
			v(p0.x, p0.y, p0.z, u0, v1, n.x, n.y, n.z);
			v(p1.x, p1.y, p1.z, u1, v1, n.x, n.y, n.z);
			v(p2.x, p2.y, p2.z, u1, v0, n.x, n.y, n.z);
			v(p3.x, p3.y, p3.z, u0, v0, n.x, n.y, n.z);
		}
	}

	/** Light with a floor under it: the flesh must read at dusk. */
	static int lit(int light, int floorBlock, int floorSky) {
		int block = Math.max(LightmapTextureManager.getBlockLightCoordinates(light), floorBlock);
		int sky = Math.max(LightmapTextureManager.getSkyLightCoordinates(light), floorSky);
		return LightmapTextureManager.pack(block, sky);
	}

	// ---- Noise.

	private static double hash(int x, int y, int z, int seed) {
		int h = x * 374761393 + y * 668265263 + z * 2147483647 + seed * 362437;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (double) 0xFFFFFF;
	}

	static double noise(double x, double y, double z, int seed) {
		int x0 = MathHelper.floor(x);
		int y0 = MathHelper.floor(y);
		int z0 = MathHelper.floor(z);
		double fx = x - x0;
		double fy = y - y0;
		double fz = z - z0;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		fz = fz * fz * (3 - 2 * fz);
		double c00 = MathHelper.lerp(fx, hash(x0, y0, z0, seed), hash(x0 + 1, y0, z0, seed));
		double c10 = MathHelper.lerp(fx, hash(x0, y0 + 1, z0, seed), hash(x0 + 1, y0 + 1, z0, seed));
		double c01 = MathHelper.lerp(fx, hash(x0, y0, z0 + 1, seed), hash(x0 + 1, y0, z0 + 1, seed));
		double c11 = MathHelper.lerp(fx, hash(x0, y0 + 1, z0 + 1, seed), hash(x0 + 1, y0 + 1, z0 + 1, seed));
		return MathHelper.lerp(fz, MathHelper.lerp(fy, c00, c10), MathHelper.lerp(fy, c01, c11));
	}

	// ---- Shapes.

	/**
	 * A lump of flesh: the upper part of an ellipsoid at {@code (cx, cy, cz)} with half-sizes {@code (rx, ry, rz)}, its surface pushed
	 * about by noise ({@code amp}), from the top down to {@code below} radians under its equator. UVs wrap the flesh texture
	 * {@code uTiles} times round and {@code vTiles} up it.
	 */
	static void blob(Pen p, double cx, double cy, double cz, double rx, double ry, double rz, int lat, int lon, double below, int seed, double amp,
			float uTiles, float vTiles) {
		Vec3d[][] pos = new Vec3d[lat + 1][lon + 1];
		Vec3d[][] nor = new Vec3d[lat + 1][lon + 1];
		for (int i = 0; i <= lat; i++) {
			double e = Math.PI / 2.0 - i * (Math.PI / 2.0 + below) / lat;
			for (int j = 0; j <= lon; j++) {
				double phi = (j % lon) * Math.PI * 2.0 / lon;
				double dx = Math.cos(e) * Math.cos(phi);
				double dy = Math.sin(e);
				double dz = Math.cos(e) * Math.sin(phi);
				double n = 1.0 + amp * (noise(dx * 2.3 + 11.0, dy * 2.3 + 5.0, dz * 2.3 + 3.0, seed) * 2.0 - 1.0)
						+ amp * 0.5 * (noise(dx * 5.1, dy * 5.1, dz * 5.1, seed + 7) * 2.0 - 1.0);
				pos[i][j] = new Vec3d(cx + rx * dx * n, cy + ry * dy * n, cz + rz * dz * n);
				nor[i][j] = new Vec3d(dx / rx, dy / ry, dz / rz).normalize();
			}
		}
		for (int i = 0; i < lat; i++) {
			for (int j = 0; j < lon; j++) {
				float u0 = j / (float) lon * uTiles;
				float u1 = (j + 1) / (float) lon * uTiles;
				float v0 = i / (float) lat * vTiles;
				float v1 = (i + 1) / (float) lat * vTiles;
				Vec3d a = pos[i][j];
				Vec3d b = pos[i][j + 1];
				Vec3d c = pos[i + 1][j + 1];
				Vec3d d = pos[i + 1][j];
				p.v(a.x, a.y, a.z, u0, v0, nor[i][j].x, nor[i][j].y, nor[i][j].z);
				p.v(d.x, d.y, d.z, u0, v1, nor[i + 1][j].x, nor[i + 1][j].y, nor[i + 1][j].z);
				p.v(c.x, c.y, c.z, u1, v1, nor[i + 1][j + 1].x, nor[i + 1][j + 1].y, nor[i + 1][j + 1].z);
				p.v(b.x, b.y, b.z, u1, v0, nor[i][j + 1].x, nor[i][j + 1].y, nor[i][j + 1].z);
			}
		}
	}

	/** An eyeball of radius {@code r} about the origin: the whole sphere, the eye map's front (the iris) towards +z. */
	static void ball(Pen p, double r, int lat, int lon) {
		for (int i = 0; i < lat; i++) {
			double e0 = Math.PI / 2.0 - i * Math.PI / lat;
			double e1 = Math.PI / 2.0 - (i + 1) * Math.PI / lat;
			for (int j = 0; j < lon; j++) {
				double l0 = -Math.PI + j * Math.PI * 2.0 / lon;
				double l1 = -Math.PI + (j + 1) * Math.PI * 2.0 / lon;
				ballVertex(p, r, e0, l0, (float) (0.5 + l0 / (Math.PI * 2.0)), (float) (0.5 - e0 / Math.PI));
				ballVertex(p, r, e1, l0, (float) (0.5 + l0 / (Math.PI * 2.0)), (float) (0.5 - e1 / Math.PI));
				ballVertex(p, r, e1, l1, (float) (0.5 + l1 / (Math.PI * 2.0)), (float) (0.5 - e1 / Math.PI));
				ballVertex(p, r, e0, l1, (float) (0.5 + l1 / (Math.PI * 2.0)), (float) (0.5 - e0 / Math.PI));
			}
		}
	}

	private static void ballVertex(Pen p, double r, double lat, double lon, float u, float v) {
		double dx = Math.cos(lat) * Math.sin(lon);
		double dy = Math.sin(lat);
		double dz = Math.cos(lat) * Math.cos(lon);
		p.v(dx * r, dy * r, dz * r, u, v, dx, dy, dz);
	}

	/** A half-shell of flesh over an eyeball: the upper half ({@code upper}) or the lower, radius {@code r}, round the origin. */
	static void lid(Pen p, double r, boolean upper, int lat, int lon) {
		double sign = upper ? 1.0 : -1.0;
		for (int i = 0; i < lat; i++) {
			double e0 = sign * i * (Math.PI / 2.0) / lat;
			double e1 = sign * (i + 1) * (Math.PI / 2.0) / lat;
			for (int j = 0; j < lon; j++) {
				double l0 = j * Math.PI * 2.0 / lon;
				double l1 = (j + 1) * Math.PI * 2.0 / lon;
				lidVertex(p, r, e0, l0, j / (float) lon, i / (float) lat * 0.5F);
				lidVertex(p, r, e1, l0, j / (float) lon, (i + 1) / (float) lat * 0.5F);
				lidVertex(p, r, e1, l1, (j + 1) / (float) lon, (i + 1) / (float) lat * 0.5F);
				lidVertex(p, r, e0, l1, (j + 1) / (float) lon, i / (float) lat * 0.5F);
			}
		}
	}

	private static void lidVertex(Pen p, double r, double lat, double lon, float u, float v) {
		double dx = Math.cos(lat) * Math.sin(lon);
		double dy = Math.sin(lat);
		double dz = Math.cos(lat) * Math.cos(lon);
		p.v(dx * r, dy * r, dz * r, u, v, dx, dy, dz);
	}

	/** An axis-aligned box with one flat colour (a cell of the mouth atlas at {@code (u, v)}). */
	static void box(Pen p, double x0, double y0, double z0, double x1, double y1, double z1, float u, float v) {
		Vec3d a = new Vec3d(x0, y0, z0);
		Vec3d b = new Vec3d(x1, y0, z0);
		Vec3d c = new Vec3d(x1, y1, z0);
		Vec3d d = new Vec3d(x0, y1, z0);
		Vec3d e = new Vec3d(x0, y0, z1);
		Vec3d f = new Vec3d(x1, y0, z1);
		Vec3d g = new Vec3d(x1, y1, z1);
		Vec3d h = new Vec3d(x0, y1, z1);
		p.quad(e, f, g, h, u, v, u, v);
		p.quad(b, a, d, c, u, v, u, v);
		p.quad(f, b, c, g, u, v, u, v);
		p.quad(a, e, h, d, u, v, u, v);
		p.quad(h, g, c, d, u, v, u, v);
		p.quad(a, b, f, e, u, v, u, v);
	}

	/** A flat strip from {@code a} to {@code b} (both on the ground, y included), {@code width0} wide at {@code a} and {@code width1} at {@code b}. */
	static void strip(Pen p, Vec3d a, Vec3d b, double width0, double width1, float u0, float u1) {
		Vec3d dir = b.subtract(a);
		Vec3d flat = new Vec3d(dir.x, 0.0, dir.z);
		if (flat.lengthSquared() < 1.0E-8) {
			return;
		}
		Vec3d side = new Vec3d(-flat.z, 0.0, flat.x).normalize();
		Vec3d a0 = a.add(side.multiply(width0 / 2.0));
		Vec3d a1 = a.subtract(side.multiply(width0 / 2.0));
		Vec3d b0 = b.add(side.multiply(width1 / 2.0));
		Vec3d b1 = b.subtract(side.multiply(width1 / 2.0));
		// Faces up.
		p.v(a1.x, a1.y, a1.z, u0, 1.0F, 0.0, 1.0, 0.0);
		p.v(b1.x, b1.y, b1.z, u1, 1.0F, 0.0, 1.0, 0.0);
		p.v(b0.x, b0.y, b0.z, u1, 0.0F, 0.0, 1.0, 0.0);
		p.v(a0.x, a0.y, a0.z, u0, 0.0F, 0.0, 1.0, 0.0);
	}

	/**
	 * A rope of flesh along {@code pts}: a square section {@code radius} (half-width) at {@code start} tapering to {@code end} at its
	 * far end, with knots along it.
	 */
	static void rope(Pen p, List<Vec3d> pts, double start, double end, float uTiles) {
		int n = pts.size();
		if (n < 2) {
			return;
		}
		Vec3d[][] ring = new Vec3d[n][4];
		Vec3d up = new Vec3d(0.0, 1.0, 0.0);
		for (int i = 0; i < n; i++) {
			Vec3d t = pts.get(Math.min(n - 1, i + 1)).subtract(pts.get(Math.max(0, i - 1)));
			if (t.lengthSquared() < 1.0E-10) {
				t = new Vec3d(0.0, 1.0, 0.0);
			}
			t = t.normalize();
			Vec3d side = Math.abs(t.dotProduct(up)) > 0.95 ? new Vec3d(1.0, 0.0, 0.0) : t.crossProduct(up).normalize();
			Vec3d top = side.crossProduct(t).normalize();
			double k = i / (double) (n - 1);
			double r = MathHelper.lerp(k, start, end) * (1.0 + 0.3 * Math.sin(i * 1.9) + 0.12 * Math.sin(i * 4.7));
			Vec3d c = pts.get(i);
			ring[i][0] = c.add(side.multiply(r)).add(top.multiply(r));
			ring[i][1] = c.subtract(side.multiply(r)).add(top.multiply(r));
			ring[i][2] = c.subtract(side.multiply(r)).subtract(top.multiply(r));
			ring[i][3] = c.add(side.multiply(r)).subtract(top.multiply(r));
		}
		for (int i = 0; i < n - 1; i++) {
			float u0 = i / (float) (n - 1) * uTiles;
			float u1 = (i + 1) / (float) (n - 1) * uTiles;
			for (int s = 0; s < 4; s++) {
				int t = (s + 1) % 4;
				Vec3d face = ring[i][s].add(ring[i][t]).add(ring[i + 1][s]).add(ring[i + 1][t]).multiply(0.25);
				Vec3d mid = pts.get(i).add(pts.get(i + 1)).multiply(0.5);
				Vec3d out = face.subtract(mid);
				p.quadN(ring[i][s], ring[i][t], ring[i + 1][t], ring[i + 1][s], out.lengthSquared() < 1.0E-12 ? new Vec3d(0.0, 1.0, 0.0) : out.normalize(), u0, s * 0.25F, u1,
						s * 0.25F + 0.25F);
			}
		}
	}
}
