package io.github.kortev.shootingstar.client.feed;

import io.github.kortev.shootingstar.client.render.Gfx;
import io.github.kortev.shootingstar.client.render.Textures;
import java.util.Random;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;

/** Fixed stars and a faint Milky Way band, drawn at infinity. */
final class Starfield {
	private static final int STARS = 2200;
	private static final int GLOWS = 340;

	private final float[] dirs = new float[STARS * 3];
	private final float[] size = new float[STARS];
	private final int[] color = new int[STARS];
	private final float[] glowDirs = new float[GLOWS * 3];
	private final float[] glowSize = new float[GLOWS];
	private final int[] glowColor = new int[GLOWS];
	private final double[] out = new double[3];

	Starfield(long seed) {
		Random random = new Random(seed);
		// Galactic plane normal, tilted so the band crosses the sky diagonally.
		double gx = 0.35, gy = 0.82, gz = 0.45;
		double gl = Math.sqrt(gx * gx + gy * gy + gz * gz);
		gx /= gl;
		gy /= gl;
		gz /= gl;
		for (int i = 0; i < STARS; i++) {
			double x, y, z;
			if (i % 5 < 2) {
				// Concentrate some stars along the band.
				double[] d = randomDir(random);
				double off = random.nextGaussian() * 0.16;
				double dot = d[0] * gx + d[1] * gy + d[2] * gz;
				x = d[0] - gx * (dot - off);
				y = d[1] - gy * (dot - off);
				z = d[2] - gz * (dot - off);
			} else {
				double[] d = randomDir(random);
				x = d[0];
				y = d[1];
				z = d[2];
			}
			double l = Math.sqrt(x * x + y * y + z * z);
			dirs[i * 3] = (float) (x / l);
			dirs[i * 3 + 1] = (float) (y / l);
			dirs[i * 3 + 2] = (float) (z / l);
			double mag = Math.pow(random.nextDouble(), 7.0);
			size[i] = (float) (0.35 + mag * 1.6);
			float bright = (float) (0.35 + 0.65 * Math.min(1.0, mag * 3.0 + random.nextDouble() * 0.4));
			float temp = random.nextFloat();
			int r = (int) (255 * bright * (temp < 0.2F ? 0.75F : 1.0F));
			int g = (int) (255 * bright * (temp < 0.2F ? 0.85F : temp > 0.85F ? 0.88F : 1.0F));
			int b = (int) (255 * bright * (temp > 0.85F ? 0.7F : 1.0F));
			color[i] = Gfx.argb(255, r, g, b);
		}
		for (int i = 0; i < GLOWS; i++) {
			double[] d = randomDir(random);
			double off = random.nextGaussian() * 0.09;
			double dot = d[0] * gx + d[1] * gy + d[2] * gz;
			double x = d[0] - gx * (dot - off);
			double y = d[1] - gy * (dot - off);
			double z = d[2] - gz * (dot - off);
			double l = Math.sqrt(x * x + y * y + z * z);
			glowDirs[i * 3] = (float) (x / l);
			glowDirs[i * 3 + 1] = (float) (y / l);
			glowDirs[i * 3 + 2] = (float) (z / l);
			glowSize[i] = (float) (0.05 + random.nextDouble() * 0.12);
			float warm = random.nextFloat();
			glowColor[i] = warm < 0.7F ? Gfx.argb(255, 150, 160, 200) : warm < 0.9F ? Gfx.argb(255, 200, 170, 150)
					: Gfx.argb(255, 120, 140, 220);
		}
	}

	private static double[] randomDir(Random random) {
		double z = random.nextDouble() * 2.0 - 1.0;
		double t = random.nextDouble() * Math.PI * 2.0;
		double r = Math.sqrt(1.0 - z * z);
		return new double[] {r * Math.cos(t), z, r * Math.sin(t)};
	}

	void draw(Matrix4f m, View3D v, float alpha) {
		if (alpha <= 0.01F) {
			return;
		}
		// Milky Way haze first.
		BufferBuilder glow = Gfx.texQuads();
		for (int i = 0; i < GLOWS; i++) {
			if (!v.projectDir(glowDirs[i * 3], glowDirs[i * 3 + 1], glowDirs[i * 3 + 2], out)) {
				continue;
			}
			float half = (float) (glowSize[i] * v.focal);
			if (!v.onScreen(out[0], out[1], half)) {
				continue;
			}
			Gfx.sprite(glow, m, (float) out[0], (float) out[1], half, half, 0, Gfx.fade(glowColor[i], 0.09F * alpha));
		}
		Gfx.additive();
		Gfx.drawTex(glow, Textures.GLOW);

		BufferBuilder b = Gfx.quads();
		for (int i = 0; i < STARS; i++) {
			if (!v.projectDir(dirs[i * 3], dirs[i * 3 + 1], dirs[i * 3 + 2], out)) {
				continue;
			}
			if (!v.onScreen(out[0], out[1], 2)) {
				continue;
			}
			float s = size[i] * 0.5F;
			float x = (float) out[0];
			float y = (float) out[1];
			Gfx.rect(b, m, x - s, y - s, x + s, y + s, Gfx.fade(color[i], alpha));
		}
		Gfx.draw(b);
		Gfx.alpha();
	}
}
