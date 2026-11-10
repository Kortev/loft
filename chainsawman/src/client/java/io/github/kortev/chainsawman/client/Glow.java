package io.github.kortev.chainsawman.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Light drawn on its own (full bright, added over what is behind it): the Eternity Devil's lit veins, seams, wounds and the tells of its
 * attacks. Every quad is drawn from both sides. Coordinates are in the matrices' frame at the moment it is made.
 */
final class Glow {
	private final VertexConsumer vc;
	private final Matrix4f m;

	Glow(VertexConsumerProvider provider, MatrixStack matrices) {
		this.vc = provider.getBuffer(RenderLayer.getLightning());
		this.m = matrices.peek().getPositionMatrix();
	}

	private void v(Vec3d p, float r, float g, float b, float a) {
		vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a);
	}

	/** A quad of one colour, from both sides. */
	void quad(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, float r, float g, float b, float a) {
		if (a <= 0.004F) {
			return;
		}
		v(p0, r, g, b, a);
		v(p1, r, g, b, a);
		v(p2, r, g, b, a);
		v(p3, r, g, b, a);
		v(p3, r, g, b, a);
		v(p2, r, g, b, a);
		v(p1, r, g, b, a);
		v(p0, r, g, b, a);
	}

	/** A quad fading from one end ({@code a0} at p0 and p1) to the other ({@code a1} at p2 and p3). */
	void fade(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, float r, float g, float b, float a0, float a1) {
		if (a0 <= 0.004F && a1 <= 0.004F) {
			return;
		}
		v(p0, r, g, b, a0);
		v(p1, r, g, b, a0);
		v(p2, r, g, b, a1);
		v(p3, r, g, b, a1);
		v(p3, r, g, b, a1);
		v(p2, r, g, b, a1);
		v(p1, r, g, b, a0);
		v(p0, r, g, b, a0);
	}

	/** A strip {@code w} wide from {@code a} to {@code b} lying flat on the surface whose normal is {@code n}. */
	void line(Vec3d a, Vec3d b, Vec3d n, double w, float r, float g, float bl, float alpha) {
		Vec3d d = b.subtract(a);
		Vec3d side = d.crossProduct(n);
		if (side.lengthSquared() < 1.0E-8) {
			return;
		}
		side = side.normalize().multiply(w / 2.0);
		quad(a.subtract(side), a.add(side), b.add(side), b.subtract(side), r, g, bl, alpha);
	}

	/** A slash (a lens: pointed at both ends) {@code len} long and {@code w} wide at {@code c} along {@code dir}, on the surface whose normal is {@code n}. */
	void slash(Vec3d c, Vec3d dir, Vec3d n, double len, double w, float r, float g, float b, float a) {
		Vec3d d = dir.normalize().multiply(len / 2.0);
		Vec3d side = dir.crossProduct(n);
		if (side.lengthSquared() < 1.0E-8) {
			return;
		}
		side = side.normalize().multiply(w / 2.0);
		quad(c.subtract(d), c.subtract(d.multiply(0.3)).add(side), c.add(d), c.subtract(d.multiply(-0.3)).subtract(side), r, g, b, a);
		quad(c.subtract(d), c.add(d.multiply(0.3)).add(side), c.add(d), c.add(d.multiply(-0.3)).subtract(side), r, g, b, a * 0.6F);
	}

	/** A flat ring (or an arc of one: from {@code a0} to {@code a1} radians) round {@code c} on the floor, {@code w} wide, at radius {@code rad}. */
	void arc(Vec3d c, double rad, double w, double a0, double a1, Vec3d u, Vec3d v, float r, float g, float b, float a) {
		int n = Math.max(4, (int) (Math.abs(a1 - a0) * rad * 2.0));
		for (int i = 0; i < n; i++) {
			double t0 = a0 + (a1 - a0) * i / n;
			double t1 = a0 + (a1 - a0) * (i + 1) / n;
			Vec3d d0 = u.multiply(Math.cos(t0)).add(v.multiply(Math.sin(t0)));
			Vec3d d1 = u.multiply(Math.cos(t1)).add(v.multiply(Math.sin(t1)));
			quad(c.add(d0.multiply(rad - w / 2.0)), c.add(d1.multiply(rad - w / 2.0)), c.add(d1.multiply(rad + w / 2.0)), c.add(d0.multiply(rad + w / 2.0)), r, g, b, a);
		}
	}
}
