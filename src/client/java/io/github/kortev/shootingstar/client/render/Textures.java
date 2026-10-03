package io.github.kortev.shootingstar.client.render;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Textures generated at runtime (soft glows, cloud puffs, the halftone screen) plus the status card.
 */
public final class Textures {
	public static final Identifier GLOW = ShootingStar.id("dynamic/glow");
	public static final Identifier CLOUD = ShootingStar.id("dynamic/cloud");
	public static final Identifier HALFTONE = ShootingStar.id("dynamic/halftone");

	public static final Identifier CARD = ShootingStar.id("textures/gui/gungnir_card.png");

	public static final int HALFTONE_SIZE = 512;

	private static boolean ready;

	private Textures() {
	}

	/** Creates the generated textures on first use; must run on the render thread. */
	public static void ensure() {
		if (ready) {
			return;
		}
		ready = true;
		TextureManager manager = MinecraftClient.getInstance().getTextureManager();
		register(manager, GLOW, glow(64), true);
		register(manager, CLOUD, cloud(128, 7L), true);
		register(manager, HALFTONE, halftone(HALFTONE_SIZE, 5), true);
	}

	private static void register(TextureManager manager, Identifier id, NativeImage image, boolean smooth) {
		NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
		manager.registerTexture(id, texture);
		texture.setFilter(smooth, false);
	}

	/** NativeImage pixels are ABGR. */
	private static int abgr(int a, int r, int g, int b) {
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	private static NativeImage glow(int size) {
		NativeImage image = new NativeImage(size, size, false);
		float half = size / 2.0F;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float dx = (x + 0.5F - half) / half;
				float dy = (y + 0.5F - half) / half;
				float d = MathHelper.sqrt(dx * dx + dy * dy);
				float a = MathHelper.clamp(1.0F - d, 0.0F, 1.0F);
				a = a * a * (0.6F + 0.4F * a);
				image.setColor(x, y, abgr((int) (a * 255), 255, 255, 255));
			}
		}
		return image;
	}

	private static NativeImage cloud(int size, long seed) {
		NativeImage image = new NativeImage(size, size, false);
		float half = size / 2.0F;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float dx = (x + 0.5F - half) / half;
				float dy = (y + 0.5F - half) / half;
				float falloff = MathHelper.clamp(1.0F - MathHelper.sqrt(dx * dx + dy * dy), 0.0F, 1.0F);
				double n = fbm(x / 22.0, y / 22.0, seed, 5);
				float a = MathHelper.clamp((float) (n * 1.4 - 0.15) * falloff * 1.6F, 0.0F, 1.0F);
				image.setColor(x, y, abgr((int) (a * 255), 255, 255, 255));
			}
		}
		return image;
	}

	/**
	 * A halftone screen: black dots on a {@code cell}-pixel grid, rotated 15 degrees, growing from
	 * pinpricks at the centre to solid at the corners.
	 */
	private static NativeImage halftone(int size, int cell) {
		NativeImage image = new NativeImage(size, size, false);
		float half = size / 2.0F;
		double angle = Math.toRadians(15);
		double ca = Math.cos(angle), sa = Math.sin(angle);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				double px = x + 0.5 - half;
				double py = y + 0.5 - half;
				double rx = px * ca - py * sa;
				double ry = px * sa + py * ca;
				double fx = rx / cell - Math.floor(rx / cell) - 0.5;
				double fy = ry / cell - Math.floor(ry / cell) - 0.5;
				double inCell = Math.sqrt(fx * fx + fy * fy);
				double radial = Math.min(1.0, Math.sqrt(px * px + py * py) / (half * 1.15));
				double dotRadius = 0.08 + 0.58 * Math.pow(radial, 0.9);
				double edge = MathHelper.clamp((dotRadius - inCell) * cell * 1.2, 0.0, 1.0);
				image.setColor(x, y, abgr((int) (edge * 255), 0, 0, 0));
			}
		}
		return image;
	}

	private static double fbm(double x, double y, long seed, int octaves) {
		double sum = 0;
		double amp = 0.5;
		double norm = 0;
		for (int i = 0; i < octaves; i++) {
			sum += amp * valueNoise(x, y, seed + i * 1013L);
			norm += amp;
			x *= 2.03;
			y *= 2.03;
			amp *= 0.5;
		}
		return sum / norm;
	}

	private static double valueNoise(double x, double y, long seed) {
		int x0 = MathHelper.floor(x);
		int y0 = MathHelper.floor(y);
		double tx = x - x0;
		double ty = y - y0;
		tx = tx * tx * (3 - 2 * tx);
		ty = ty * ty * (3 - 2 * ty);
		double a = MathHelper.lerp(tx, hash(x0, y0, seed), hash(x0 + 1, y0, seed));
		double b = MathHelper.lerp(tx, hash(x0, y0 + 1, seed), hash(x0 + 1, y0 + 1, seed));
		return MathHelper.lerp(ty, a, b);
	}

	private static double hash(int x, int y, long seed) {
		long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + y * 0x165667B19E3779F9L;
		h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h & 0xFFFFFF) / (double) 0xFFFFFF;
	}
}
