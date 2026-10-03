package io.github.kortev.shootingstar.client.gfx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL11;

/** An offscreen colour (and optionally depth) buffer that follows the size it is asked for. */
public final class Target {
	private final boolean depth;
	private Framebuffer fb;

	public Target(boolean depth) {
		this.depth = depth;
	}

	public Framebuffer get(int width, int height) {
		if (fb == null) {
			fb = new SimpleFramebuffer(width, height, depth, MinecraftClient.IS_SYSTEM_MAC);
			fb.setTexFilter(GL11.GL_LINEAR);
		} else if (fb.textureWidth != width || fb.textureHeight != height) {
			fb.resize(width, height, MinecraftClient.IS_SYSTEM_MAC);
			fb.setTexFilter(GL11.GL_LINEAR);
		}
		return fb;
	}

	/** Binds for drawing and clears to the given colour (and depth). */
	public Framebuffer begin(int width, int height, float r, float g, float b, float a) {
		Framebuffer target = get(width, height);
		// glClear only touches buffers whose write masks are on.
		RenderSystem.depthMask(true);
		RenderSystem.colorMask(true, true, true, true);
		target.setClearColor(r, g, b, a);
		target.clear(MinecraftClient.IS_SYSTEM_MAC);
		target.beginWrite(true);
		return target;
	}
}
