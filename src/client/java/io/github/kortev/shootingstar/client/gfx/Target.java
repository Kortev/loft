package io.github.kortev.shootingstar.client.gfx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * An offscreen render target that follows the size it is asked for. HDR targets store half floats,
 * so light can go well past white and be tone-mapped (and bloomed) at the end instead of clipping.
 * The optional depth texture uses the same format as Minecraft's own, so world depth can be copied in.
 */
public final class Target {
	private final boolean depth;
	private final boolean hdr;
	private int fbo = -1;
	private int color = -1;
	private int depthTex = -1;
	private int width;
	private int height;

	public Target(boolean depth, boolean hdr) {
		this.depth = depth;
		this.hdr = hdr;
	}

	/** Creates the buffers, or resizes them when the requested size changed. */
	public Target ensure(int w, int h) {
		RenderSystem.assertOnRenderThread();
		if (fbo != -1 && w == width && h == height) {
			return this;
		}
		if (fbo == -1) {
			fbo = GlStateManager.glGenFramebuffers();
			color = TextureUtil.generateTextureId();
			if (depth) {
				depthTex = TextureUtil.generateTextureId();
			}
		}
		width = w;
		height = h;
		GlStateManager._bindTexture(color);
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
		GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, hdr ? GL30.GL_RGBA16F : GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA,
				hdr ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_BYTE, null);
		if (depth) {
			GlStateManager._bindTexture(depthTex);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, 0);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
			GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, w, h, 0, GL11.GL_DEPTH_COMPONENT,
					GL11.GL_FLOAT, null);
		}
		GlStateManager._bindTexture(0);
		GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
		GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
		if (depth) {
			GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTex, 0);
		}
		int status = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
		if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
			ShootingStar.LOGGER.error("Render target {}x{} (hdr={}, depth={}) is incomplete: {}", w, h, hdr, depth, status);
		}
		GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		return this;
	}

	/** Binds for drawing over the whole target. */
	public Target bind() {
		GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
		RenderSystem.viewport(0, 0, width, height);
		return this;
	}

	/** Creates if needed, binds and clears to the given colour (and depth). */
	public Target begin(int w, int h, float r, float g, float b, float a) {
		ensure(w, h);
		bind();
		// glClear only touches buffers whose write masks are on.
		RenderSystem.depthMask(true);
		RenderSystem.colorMask(true, true, true, true);
		RenderSystem.clearColor(r, g, b, a);
		RenderSystem.clearDepth(1.0);
		RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | (depth ? GL11.GL_DEPTH_BUFFER_BIT : 0), MinecraftClient.IS_SYSTEM_MAC);
		return this;
	}

	/** Copies {@code source}'s depth into this target (sizes must match). */
	public void copyDepthFrom(Framebuffer source) {
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.fbo);
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
		GlStateManager._glBlitFrameBuffer(0, 0, source.textureWidth, source.textureHeight, 0, 0, width, height,
				GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
		GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
	}

	/** Copies {@code source}'s colour into this target. */
	public void copyColorFrom(Framebuffer source) {
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.fbo);
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
		GlStateManager._glBlitFrameBuffer(0, 0, source.textureWidth, source.textureHeight, 0, 0, width, height,
				GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
		GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
	}

	public int color() {
		return color;
	}

	public int depth() {
		return depthTex;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}
}
