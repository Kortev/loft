package io.github.kortev.shootingstar.client.gfx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * A universe in its block, as Ginnungagap takes it: the cosmic web of {@link Mesh#universe} (a hundred thousand
 * galaxies drawn by {@code ss_galaxy}) inside a block of dark glass drawn by {@code ss_block}. Shared by the feed
 * and the world. Universes seen from far off draw only a sample of the galaxies: they are too small on screen for
 * the rest to tell.
 */
public final class Universe {
	/** How many of the galaxies to draw: all of them, or a sample for universes seen from further and further off. */
	public static final int FULL = 0;
	public static final int MID = 1;
	public static final int LOW = 2;
	public static final int TINY = 3;
	private static final int[] GALAXIES = {100_000, 15_000, 4_000, 1_000};
	private static final int[] GLOWS = {14_000, 4_000, 2_000, 600};
	/** How much brighter a sample's galaxies and glows are drawn, so it carries the light of the whole from far off. */
	private static final float[] GAIN = {1.0F, 2.2F, 4.0F, 7.0F};
	private static final float[] GLOW_GAIN = {1.0F, 2.0F, 3.0F, 5.0F};
	/** The 48 ways to turn or mirror a block onto itself, so the one universe can stand for many. */
	private static final Matrix4f[] TURNS = new Matrix4f[48];
	private static Mesh[] galaxies;
	private static Mesh[] glows;
	private static Mesh cube;
	private static Mesh quad;

	static {
		int[][] orders = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
		int n = 0;
		for (int[] order : orders) {
			for (int signs = 0; signs < 8; signs++) {
				Matrix4f m = new Matrix4f().zero();
				for (int row = 0; row < 3; row++) {
					m.set(order[row], row, (signs >> row & 1) == 0 ? 1.0F : -1.0F);
				}
				m.m33(1.0F);
				TURNS[n++] = m;
			}
		}
	}

	private Universe() {
	}

	/** Loads the universe onto the GPU on first use (render thread). */
	public static void ensure() {
		if (galaxies != null) {
			return;
		}
		Mesh[] meshes = Mesh.universe(GALAXIES, GLOWS);
		galaxies = new Mesh[GALAXIES.length];
		glows = new Mesh[GLOWS.length];
		System.arraycopy(meshes, 0, galaxies, 0, GALAXIES.length);
		System.arraycopy(meshes, GALAXIES.length, glows, 0, GLOWS.length);
		cube = Mesh.cube();
		quad = Mesh.quad();
	}

	/**
	 * Draws a universe in its block. {@code view} takes the block's own space (the cube from -1 to 1) to view space;
	 * {@code turn} is one of 48 ways to lay the universe in it (0 as it is). {@code dark} is how much each face of the
	 * glass darkens what is behind it, {@code edge} how bright its edges are, in {@code edgeColor}; {@code glow} brings
	 * up the light of the web, {@code heat} sets the whole of it burning.
	 */
	public static void draw(Matrix4f view, Matrix4f proj, float width, float height, int detail, int turn, float brightness, float glow,
			Vector3f tint, float dark, float edge, int edgeColor, float heat) {
		ensure();
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		if (dark > 0.0F) {
			RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
					GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
			glass(view, proj, 0, dark, 0.0F, 0xFFFFFF, 0.0F);
		}
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ZERO,
				GlStateManager.DstFactor.ONE);
		Matrix4f inner = new Matrix4f(view).mul(TURNS[Math.floorMod(turn, TURNS.length)]);
		Shaders.set(Shaders.galaxy, "ScreenSize", width, height);
		Shaders.set(Shaders.galaxy, "Tint", tint);
		Shaders.set(Shaders.galaxy, "Heat", heat);
		if (glow > 0.0F) {
			Shaders.set(Shaders.galaxy, "Brightness", brightness * glow * GLOW_GAIN[detail]);
			Shaders.set(Shaders.galaxy, "Glow", 1.0F);
			glows[detail].draw(Shaders.galaxy, inner, proj);
		}
		Shaders.set(Shaders.galaxy, "Brightness", brightness * GAIN[detail]);
		Shaders.set(Shaders.galaxy, "Glow", 0.0F);
		galaxies[detail].draw(Shaders.galaxy, inner, proj);
		if (edge > 0.0F || heat > 0.0F) {
			glass(view, proj, 1, 0.0F, edge, edgeColor, heat);
		}
		RenderSystem.depthMask(true);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}

	/**
	 * The window of an open gate, opened {@code reveal} of the way. {@code view} takes the window's own space (the quad
	 * from -1 to 1 at z 0) to view space; {@code beyond} is the texture what lies beyond the window was drawn into, from
	 * the same eye, at {@code width} by {@code height}. It leaves the depth buffer alone, so the block still coming
	 * through it is drawn over it.
	 */
	public static void window(Matrix4f view, Matrix4f proj, float width, float height, int beyond, float reveal, float time) {
		ensure();
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.disableBlend();
		RenderSystem.setShaderTexture(0, beyond);
		Shaders.setInt(Shaders.block, "Mode", 2);
		Shaders.set(Shaders.block, "Reveal", reveal);
		Shaders.set(Shaders.block, "Time", time);
		Shaders.set(Shaders.block, "ScreenSize", width, height);
		quad.draw(Shaders.block, view, proj);
		RenderSystem.setShaderTexture(0, 0);
		RenderSystem.depthMask(true);
	}

	private static void glass(Matrix4f view, Matrix4f proj, int mode, float dark, float edge, int edgeColor, float heat) {
		Shaders.setInt(Shaders.block, "Mode", mode);
		Shaders.set(Shaders.block, "Dark", dark);
		Shaders.set(Shaders.block, "Edge", edge);
		Shaders.set(Shaders.block, "EdgeColor", edgeColor);
		Shaders.set(Shaders.block, "Heat", heat);
		cube.draw(Shaders.block, view, proj);
	}
}
