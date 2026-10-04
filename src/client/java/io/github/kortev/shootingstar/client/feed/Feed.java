package io.github.kortev.shootingstar.client.feed;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.ClientStrike;
import io.github.kortev.shootingstar.client.ShootingStarClient;
import io.github.kortev.shootingstar.client.gfx.Post;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.gfx.Target;
import io.github.kortev.shootingstar.client.render.Gfx;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

/**
 * The uplink feed: the shooter's cut of the shot from Jupiter, drawn full-screen over the HUD. The 3D
 * shots render offscreen, then bloom and grading go to the screen and the HUD text goes on top.
 */
public final class Feed {
	static final int RED = 0xFFFF4A32;
	static final int ORANGE = 0xFFFF7A1E;
	static final int WHITE = 0xFFEDEDED;
	static final int GREY = 0xFFA8A8A8;
	static final int CYAN = 0xFF7FD8FF;

	private static final Shots SHOTS = new Shots();
	private static final Target SCENE = new Target(true, true);
	private static final Target SHUTTER = new Target(false, true);
	private static final int SHUTTER_SAMPLES = 6;

	private Feed() {
	}

	public static boolean showing(double t) {
		return t >= StrikeTimeline.ORBIT && t < StrikeTimeline.INBOUND;
	}

	public static void render(DrawContext ctx, ClientStrike strike, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		double t = strike.time(tickDelta);
		float w = ctx.getScaledWindowWidth();
		float h = ctx.getScaledWindowHeight();
		TextRenderer font = client.textRenderer;
		ctx.draw();

		Overlay overlay;
		if (Shaders.ready()) {
			overlay = renderScene(client, t, w, h);
		} else {
			// Shaders still loading (or failed): a plain black feed so the text still reads.
			overlay = new Overlay();
			ctx.fill(0, 0, (int) w + 1, (int) h + 1, 0xFF000000);
		}
		RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
		drawOverlay(ctx, font, overlay, w, h, t);
	}

	private static Overlay renderScene(MinecraftClient client, double t, float guiW, float guiH) {
		int fw = client.getWindow().getFramebufferWidth();
		int fh = client.getWindow().getFramebufferHeight();
		Framebuffer main = client.getFramebuffer();

		RenderSystem.backupProjectionMatrix();
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.identity();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

		SCENE.begin(fw, fh, 0.0F, 0.0F, 0.0F, 1.0F);
		SHOTS.frameTicks = MathHelper.clamp(client.getRenderTickCounter().getLastFrameDuration(), 0.05F, 1.0F);
		Overlay overlay = SHOTS.render(t, fw, fh, guiW, guiH);
		int picture = SCENE.color();
		if (overlay.shutter > 0.0F) {
			float frame = MathHelper.clamp(client.getRenderTickCounter().getLastFrameDuration(), 0.05F, 1.0F);
			picture = shutter(t, overlay.shutter * frame, fw, fh, guiW, guiH);
		}

		Post.begin();
		int[] bloom = Post.bloom(picture, fw, fh, overlay.threshold);
		main.beginWrite(true);
		RenderSystem.setShaderTexture(0, picture);
		RenderSystem.setShaderTexture(1, bloom[0]);
		RenderSystem.setShaderTexture(2, bloom[1]);
		RenderSystem.setShaderTexture(3, bloom[2]);
		Shaders.set(Shaders.composite, "StreakStrength", overlay.streak);
		Shaders.set(Shaders.composite, "BloomStrength", overlay.bloom);
		Shaders.set(Shaders.composite, "WideStrength", overlay.wideBloom);
		Shaders.set(Shaders.composite, "Exposure", overlay.exposure);
		Shaders.set(Shaders.composite, "Vignette", overlay.vignette);
		Shaders.set(Shaders.composite, "Grain", 0.025F);
		Shaders.set(Shaders.composite, "Time", (float) t);
		Shaders.set(Shaders.composite, "Aberration", overlay.aberration + 0.0015F);
		Shaders.set(Shaders.composite, "Flash", Math.max(overlay.flash, cutFlash(t)));
		Shaders.set(Shaders.composite, "FlashColor", overlay.flashColor);
		Shaders.set(Shaders.composite, "Scanlines", 1.0F);
		Shaders.set(Shaders.composite, "Fade", overlay.fade);
		Shaders.set(Shaders.composite, "ScreenSize", fw, fh);
		Shaders.set(Shaders.composite, "ZoomBlur", overlay.zoomBlur);
		Shaders.set(Shaders.composite, "Saturation", overlay.saturation);
		Post.quad(Shaders.composite);

		for (int i = 0; i < 4; i++) {
			RenderSystem.setShaderTexture(i, 0);
		}
		modelView.popMatrix();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.restoreProjectionMatrix();
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		return overlay;
	}

	/**
	 * Motion blur: the shot is drawn at several moments over the last {@code open} ticks and averaged,
	 * so what moves faster than the frame rate streaks instead of strobing. The picture for {@code t}
	 * is already in {@link #SCENE}.
	 */
	private static int shutter(double t, float open, int fw, int fh, float guiW, float guiH) {
		SHUTTER.begin(fw, fh, 0.0F, 0.0F, 0.0F, 1.0F);
		float weight = 1.0F / SHUTTER_SAMPLES;
		accumulate(weight);
		for (int i = 1; i < SHUTTER_SAMPLES; i++) {
			SCENE.begin(fw, fh, 0.0F, 0.0F, 0.0F, 1.0F);
			SHOTS.render(t - open * i / (SHUTTER_SAMPLES - 1.0), fw, fh, guiW, guiH);
			accumulate(weight);
		}
		return SHUTTER.color();
	}

	private static void accumulate(float weight) {
		SHUTTER.bind();
		Post.begin();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ZERO,
				GlStateManager.DstFactor.ONE);
		RenderSystem.setShaderTexture(0, SCENE.color());
		Shaders.set(Shaders.blit, "Weight", weight);
		Post.quad(Shaders.blit);
		RenderSystem.disableBlend();
	}

	/** A short white pop where the feed cuts between scenes that have no flash of their own. */
	private static float cutFlash(double t) {
		double since = t - StrikeTimeline.WAKE;
		return since >= 0 && since < 2 ? (float) (0.35 * (1 - since / 2)) : 0.0F;
	}

	// --- text ------------------------------------------------------------------------------

	private static void drawOverlay(DrawContext ctx, TextRenderer font, Overlay overlay, float w, float h, double t) {
		Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
		Gfx.begin2d();
		BufferBuilder marks = Gfx.quads();
		for (Overlay.Label label : overlay.labels) {
			if (label.marker) {
				float blink = ((int) (t / 3)) % 2 == 0 ? 1.0F : 0.55F;
				Gfx.brackets(marks, m, label.markerX, label.markerY, 6, 3, 1, Gfx.fade(label.color1, label.alpha * blink));
				Gfx.diamond(marks, m, label.markerX, label.markerY, 1.5F, Gfx.fade(label.color1, label.alpha));
			} else {
				Gfx.line(marks, m, label.markerX, label.markerY, label.x - 2, label.y + 4, 0.6F, Gfx.fade(label.color1, label.alpha * 0.8F));
			}
		}
		if (overlay.bars != null) {
			overlay.bars.draw(marks, m, w, h);
		}
		Gfx.draw(marks);
		Gfx.end2d();

		for (Overlay.Label label : overlay.labels) {
			drawLabel(ctx, font, label);
		}
		if (overlay.header != null) {
			String text = typed(overlay.header, overlay.headerReveal);
			centered(ctx, font, text, w / 2, 14, overlay.headerColor, 1.0F, true);
		}
		if (overlay.title != null && overlay.titleAlpha > 0.02F) {
			int color = Gfx.fade(RED, overlay.titleAlpha);
			float scale = Math.max(2.0F, Math.min(4.0F, w / 150.0F));
			float rise = (1.0F - overlay.titleAlpha) * 6.0F;
			centered(ctx, font, overlay.title, w / 2, h * 0.3F + rise, color, scale, true);
			if (overlay.subtitle != null) {
				centered(ctx, font, overlay.subtitle, w / 2, h * 0.3F + rise + 10 * scale, Gfx.fade(WHITE, overlay.titleAlpha), 0.75F, true);
			}
		}
		if (overlay.banner != null && overlay.bannerAlpha > 0.02F) {
			float scale = Math.max(2.0F, Math.min(3.0F, w / 200.0F));
			centered(ctx, font, overlay.banner, w / 2, h / 2 - 4 * scale, Gfx.fade(RED, overlay.bannerAlpha), scale, false);
		}
		if (overlay.footer != null) {
			centered(ctx, font, overlay.footer, w / 2, h - 36, WHITE, 1.0F, true);
		}
		if (overlay.footerSmall != null) {
			centered(ctx, font, overlay.footerSmall, w / 2, h - 24, GREY, 0.75F, true);
		}
		Text skip = Text.translatable("hud.shootingstar.skip", ShootingStarClient.SKIP_FEED.getBoundKeyLocalizedText());
		ctx.getMatrices().push();
		ctx.getMatrices().translate(w - 6, h - 10, 0);
		ctx.getMatrices().scale(0.6F, 0.6F, 1.0F);
		ctx.drawText(font, skip, -font.getWidth(skip), 0, 0x66FFFFFF, false);
		ctx.getMatrices().pop();
		ctx.draw();
	}

	/** Reveals a header a character at a time, with a block cursor while typing. */
	private static String typed(String text, float reveal) {
		if (reveal >= 1.0F) {
			return text;
		}
		int n = MathHelper.clamp((int) (text.length() * reveal), 0, text.length());
		return text.substring(0, n) + (n < text.length() ? "_" : "");
	}

	private static void drawLabel(DrawContext ctx, TextRenderer font, Overlay.Label label) {
		ctx.getMatrices().push();
		ctx.getMatrices().translate(label.x, label.y, 0);
		ctx.getMatrices().scale(0.75F, 0.75F, 1.0F);
		ctx.drawText(font, label.line1, 0, 0, Gfx.fade(label.color1, label.alpha), true);
		if (label.line2 != null) {
			ctx.drawText(font, label.line2, 0, 10, Gfx.fade(label.color2, label.alpha), true);
		}
		ctx.getMatrices().pop();
	}

	static void centered(DrawContext ctx, TextRenderer font, String text, float x, float y, int color, float scale, boolean shadow) {
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x, y, 0);
		ctx.getMatrices().scale(scale, scale, 1.0F);
		ctx.drawText(font, text, -font.getWidth(text) / 2, 0, color, shadow);
		ctx.getMatrices().pop();
	}

	static String commas(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}
}
