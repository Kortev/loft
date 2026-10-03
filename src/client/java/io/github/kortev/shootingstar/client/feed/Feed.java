package io.github.kortev.shootingstar.client.feed;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.ClientStrike;
import io.github.kortev.shootingstar.client.ShootingStarClient;
import io.github.kortev.shootingstar.client.render.Gfx;
import io.github.kortev.shootingstar.client.render.Textures;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * The uplink feed: the cut-together space sequence the shooter watches between the kinetic lock
 * and the round coming down. Drawn full-screen over the HUD.
 */
public final class Feed {
	static final int RED = 0xFFFF4A32;
	static final int ORANGE = 0xFFFF7A1E;
	static final int WHITE = 0xFFEDEDED;
	static final int GREY = 0xFFA8A8A8;
	static final int CYAN = 0xFF7FD8FF;

	private static final Scenes SCENES = new Scenes();

	private Feed() {
	}

	public static boolean showing(double t) {
		return t >= StrikeTimeline.ORBIT && t < StrikeTimeline.INBOUND;
	}

	public static void render(DrawContext ctx, ClientStrike strike, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		Textures.ensure();
		double t = strike.time(tickDelta);
		float w = ctx.getScaledWindowWidth();
		float h = ctx.getScaledWindowHeight();
		Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
		TextRenderer font = client.textRenderer;

		ctx.draw();
		// The vanilla HUD wrote depth underneath us; the feed is meant to cover it completely.
		RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
		Gfx.begin2d();
		BufferBuilder bg = Gfx.quads();
		Gfx.rect(bg, m, 0, 0, w, h, 0xFF000000);
		Gfx.draw(bg);

		Scenes.Overlay overlay;
		if (t < StrikeTimeline.RELAY) {
			overlay = SCENES.orbit(m, w, h, t - StrikeTimeline.ORBIT, StrikeTimeline.RELAY - StrikeTimeline.ORBIT);
		} else if (t < StrikeTimeline.WAKE) {
			overlay = SCENES.relay(m, w, h, t - StrikeTimeline.RELAY, StrikeTimeline.WAKE - StrikeTimeline.RELAY);
		} else if (t < StrikeTimeline.LOADING) {
			overlay = SCENES.wake(m, w, h, t - StrikeTimeline.WAKE, StrikeTimeline.LOADING - StrikeTimeline.WAKE);
		} else if (t < StrikeTimeline.LAPS) {
			overlay = SCENES.loading(m, w, h, t - StrikeTimeline.LOADING, StrikeTimeline.LAPS - StrikeTimeline.LOADING);
		} else if (t < StrikeTimeline.DEBRIS) {
			overlay = SCENES.laps(m, w, h, t);
		} else if (t < StrikeTimeline.TERMINAL) {
			overlay = SCENES.debris(m, w, h, t - StrikeTimeline.DEBRIS, StrikeTimeline.TERMINAL - StrikeTimeline.DEBRIS);
		} else {
			overlay = SCENES.terminal(m, w, h, t - StrikeTimeline.TERMINAL, StrikeTimeline.INBOUND - StrikeTimeline.TERMINAL);
		}

		feedFinish(m, w, h, t);
		Gfx.end2d();

		// Text goes through the batched GUI pipeline on top of everything.
		for (Scenes.Label label : overlay.labels) {
			drawLabel(ctx, font, label);
		}
		if (overlay.header != null) {
			centered(ctx, font, overlay.header, w / 2, 14, overlay.headerColor, 1.0F);
		}
		if (overlay.title != null && overlay.titleAlpha > 0.02F) {
			int color = Gfx.fade(RED, overlay.titleAlpha);
			float scale = Math.max(2.0F, Math.min(4.0F, w / 150.0F));
			centered(ctx, font, overlay.title, w / 2, h * 0.36F, color, scale);
			if (overlay.subtitle != null) {
				centered(ctx, font, overlay.subtitle, w / 2, h * 0.36F + 10 * scale, Gfx.fade(WHITE, overlay.titleAlpha), 1.0F);
			}
		}
		if (overlay.footer != null) {
			centered(ctx, font, overlay.footer, w / 2, h - 34, WHITE, 1.0F);
		}
		if (overlay.footerSmall != null) {
			centered(ctx, font, overlay.footerSmall, w / 2, h - 22, GREY, 0.75F);
		}
		Text skip = Text.translatable("hud.shootingstar.skip", ShootingStarClient.SKIP_FEED.getBoundKeyLocalizedText());
		ctx.getMatrices().push();
		ctx.getMatrices().translate(w - 6, h - 10, 0);
		ctx.getMatrices().scale(0.6F, 0.6F, 1.0F);
		ctx.drawText(font, skip, -font.getWidth(skip), 0, 0x66FFFFFF, false);
		ctx.getMatrices().pop();
		ctx.draw();

		if (overlay.bars != null) {
			ctx.draw();
			Gfx.begin2d();
			BufferBuilder bars = Gfx.quads();
			overlay.bars.draw(bars, m, w, h);
			Gfx.draw(bars);
			Gfx.end2d();
		}
	}

	/** Vignette, scanlines and a flash on scene cuts. */
	private static void feedFinish(Matrix4f m, float w, float h, double t) {
		BufferBuilder b = Gfx.quads();
		// Vignette as a frame of gradients.
		float edge = Math.min(w, h) * 0.22F;
		int dark = 0xB0000000;
		Gfx.rectV(b, m, 0, 0, w, edge, dark, 0x00000000);
		Gfx.rectV(b, m, 0, h - edge, w, h, 0x00000000, dark);
		Gfx.quad(b, m, 0, 0, 0, h, edge, h, edge, 0, dark, dark, 0, 0);
		Gfx.quad(b, m, w - edge, 0, w - edge, h, w, h, w, 0, 0, 0, dark, dark);
		// Scanlines.
		for (float y = 0; y < h; y += 3) {
			Gfx.rect(b, m, 0, y, w, y + 1, 0x12000000);
		}
		Gfx.draw(b);

		// Bright flash for a couple of ticks after each hard cut.
		double since = sinceCut(t);
		if (since < 3.0) {
			float a = (float) (1.0 - since / 3.0);
			BufferBuilder flash = Gfx.quads();
			Gfx.rect(flash, m, 0, 0, w, h, Gfx.fade(0xFFFFFFFF, a * 0.55F));
			Gfx.additive();
			Gfx.draw(flash);
			Gfx.alpha();
		}
	}

	private static double sinceCut(double t) {
		int[] cuts = {StrikeTimeline.ORBIT, StrikeTimeline.RELAY, StrikeTimeline.WAKE, StrikeTimeline.LOADING,
				StrikeTimeline.LAPS, StrikeTimeline.DEBRIS, StrikeTimeline.TERMINAL};
		double best = Double.MAX_VALUE;
		for (int cut : cuts) {
			if (t >= cut) {
				best = Math.min(best, t - cut);
			}
		}
		return best;
	}

	private static void drawLabel(DrawContext ctx, TextRenderer font, Scenes.Label label) {
		ctx.getMatrices().push();
		ctx.getMatrices().translate(label.x, label.y, 0);
		ctx.getMatrices().scale(label.scale, label.scale, 1.0F);
		ctx.drawText(font, label.line1, 0, 0, label.color1, false);
		if (label.line2 != null) {
			ctx.drawText(font, label.line2, 0, 10, label.color2, false);
		}
		ctx.getMatrices().pop();
	}

	static void centered(DrawContext ctx, TextRenderer font, String text, float x, float y, int color, float scale) {
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x, y, 0);
		ctx.getMatrices().scale(scale, scale, 1.0F);
		ctx.drawText(font, text, -font.getWidth(text) / 2, 0, color, false);
		ctx.getMatrices().pop();
	}

	static String commas(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	static float ease(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return (float) (x * x * (3.0 - 2.0 * x));
	}
}
