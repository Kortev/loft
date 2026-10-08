package io.github.kortev.shootingstar.client.gfx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.Random;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

/**
 * A tiling texture of cloud noise for the shaders to look up instead of working noise out for every pixel, which is
 * what made the storm the slowest thing on screen. Made once, on first use, from a fixed seed; it repeats every 1.0 of
 * texture space both ways. Each channel is a different noise:
 * <ul>
 * <li>red: billowing fractal noise, five octaves from 4 cells across (the body of a cloud);</li>
 * <li>green: the same, from another seed (to warp the red, or for a second layer);</li>
 * <li>blue: ridged noise, sharp creases where the fractal crosses its middle (striations, the edges of lumps);</li>
 * <li>alpha: fine, fast noise, three octaves from 32 cells across (grain and small detail).</li>
 * </ul>
 */
public final class NoiseTex {
	public static final int SIZE = 256;
	private static int id = -1;

	private NoiseTex() {
	}

	/** The texture's GL id (made on the first call, on the render thread). */
	public static int get() {
		if (id == -1) {
			id = make();
		}
		return id;
	}

	private static int make() {
		RenderSystem.assertOnRenderThread();
		float[] red = fractal(SIZE, 4, 5, 11L, false);
		float[] green = fractal(SIZE, 4, 5, 23L, false);
		float[] blue = fractal(SIZE, 6, 4, 37L, true);
		float[] alpha = fractal(SIZE, 32, 3, 41L, false);
		ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
		try {
			for (int i = 0; i < SIZE * SIZE; i++) {
				pixels.put(toByte(red[i])).put(toByte(green[i])).put(toByte(blue[i])).put(toByte(alpha[i]));
			}
			pixels.flip();
			int tex = TextureUtil.generateTextureId();
			GlStateManager._bindTexture(tex);
			RenderSystem.pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
			RenderSystem.pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
			RenderSystem.pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
			RenderSystem.pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SIZE, SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
			GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
			GlStateManager._bindTexture(0);
			return tex;
		} finally {
			MemoryUtil.memFree(pixels);
		}
	}

	private static byte toByte(float v) {
		return (byte) Math.round(Math.max(0.0F, Math.min(1.0F, v)) * 255.0F);
	}

	/**
	 * Tiling fractal value noise, {@code size} pixels square: {@code octaves} layers from {@code cells} lattice cells
	 * across, each twice as fine and half as strong, normalised to 0..1. Ridged turns each octave into 1 - |2n - 1|.
	 */
	static float[] fractal(int size, int cells, int octaves, long seed, boolean ridged) {
		float[] out = new float[size * size];
		Random random = new Random(seed);
		float amp = 1.0F;
		float total = 0.0F;
		for (int o = 0; o < octaves; o++) {
			int n = cells << o;
			float[] lattice = new float[n * n];
			for (int i = 0; i < lattice.length; i++) {
				lattice[i] = random.nextFloat();
			}
			for (int y = 0; y < size; y++) {
				float fy = (float) y * n / size;
				int y0 = (int) fy;
				float ty = smooth(fy - y0);
				int ya = y0 % n;
				int yb = (y0 + 1) % n;
				for (int x = 0; x < size; x++) {
					float fx = (float) x * n / size;
					int x0 = (int) fx;
					float tx = smooth(fx - x0);
					int xa = x0 % n;
					int xb = (x0 + 1) % n;
					float top = lerp(tx, lattice[ya * n + xa], lattice[ya * n + xb]);
					float bottom = lerp(tx, lattice[yb * n + xa], lattice[yb * n + xb]);
					float v = lerp(ty, top, bottom);
					if (ridged) {
						v = 1.0F - Math.abs(2.0F * v - 1.0F);
						v *= v;
					}
					out[y * size + x] += v * amp;
				}
			}
			total += amp;
			amp *= 0.5F;
		}
		float lo = Float.MAX_VALUE;
		float hi = -Float.MAX_VALUE;
		for (int i = 0; i < out.length; i++) {
			out[i] /= total;
			lo = Math.min(lo, out[i]);
			hi = Math.max(hi, out[i]);
		}
		// Stretched to fill 0..1, so the shaders' thresholds mean the same whichever channel they read.
		float span = Math.max(1.0E-6F, hi - lo);
		for (int i = 0; i < out.length; i++) {
			out[i] = (out[i] - lo) / span;
		}
		return out;
	}

	private static float smooth(float t) {
		return t * t * (3.0F - 2.0F * t);
	}

	private static float lerp(float t, float a, float b) {
		return a + (b - a) * t;
	}
}
