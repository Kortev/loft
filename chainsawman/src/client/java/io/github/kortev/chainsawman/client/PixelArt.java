package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Drawing at the game's own pixel size (package 0.1g): everything the mod draws in the hotel's corridor is made of squares a sixteenth of a
 * block, each one colour (the shading is in the colours, as in a block's texture, and the game's own light and face shading on top), laid on
 * the blocks' own grid. A white texture and the colour per pixel; runs of one colour along a row are one quad.
 */
public final class PixelArt {
	/** One white pixel ({@code tools/gen_hotel_corridor.py}). */
	public static final Identifier PX = ChainsawMan.id("textures/entity/hotel_px.png");
	public static final float P = 1.0F / 16.0F;

	private PixelArt() {
	}

	public static VertexConsumer buffer(VertexConsumerProvider provider) {
		return provider.getBuffer(RenderLayer.getEntityCutoutNoCull(PX));
	}

	private static void v(VertexConsumer vc, MatrixStack.Entry e, float x, float y, float z, int rgb, int light, float nx, float ny, float nz) {
		vc.vertex(e, x, y, z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV).light(light)
				.normal(e, nx, ny, nz);
	}

	/** A quad of four corners (counter-clockwise seen from where its normal points), one colour. */
	public static void quad(VertexConsumer vc, MatrixStack.Entry e, float[] c, int rgb, int light, float nx, float ny, float nz) {
		v(vc, e, c[0], c[1], c[2], rgb, light, nx, ny, nz);
		v(vc, e, c[3], c[4], c[5], rgb, light, nx, ny, nz);
		v(vc, e, c[6], c[7], c[8], rgb, light, nx, ny, nz);
		v(vc, e, c[9], c[10], c[11], rgb, light, nx, ny, nz);
	}

	/** A flat rectangle facing +z at depth {@code z}, from pixel (x0, y0) to (x1, y1) (pixel units: sixteenths). */
	public static void rect(VertexConsumer vc, MatrixStack.Entry e, int x0, int y0, int x1, int y1, float z, int rgb, int light) {
		float a = x0 * P;
		float b = x1 * P;
		float c = y0 * P;
		float d = y1 * P;
		v(vc, e, a, c, z, rgb, light, 0, 0, 1);
		v(vc, e, b, c, z, rgb, light, 0, 0, 1);
		v(vc, e, b, d, z, rgb, light, 0, 0, 1);
		v(vc, e, a, d, z, rgb, light, 0, 0, 1);
	}

	/**
	 * A grid of pixels facing +z ({@code grid[row][col]}, row 0 at the bottom; 0 is no pixel), its lower left pixel at (x0, y0) in pixel
	 * units, at depth {@code z}. A run of one colour along a row is one quad.
	 */
	public static void grid(VertexConsumer vc, MatrixStack.Entry e, int[][] grid, int x0, int y0, float z, int light) {
		for (int r = 0; r < grid.length; r++) {
			int[] row = grid[r];
			int c = 0;
			while (c < row.length) {
				int col = row[c];
				if (col == 0) {
					c++;
					continue;
				}
				int end = c + 1;
				while (end < row.length && row[end] == col) {
					end++;
				}
				rect(vc, e, x0 + c, y0 + r, x0 + end, y0 + r + 1, z, col, light);
				c = end;
			}
		}
	}

	public static int rgb(int r, int g, int b) {
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
