package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.client.feed.Feed;
import io.github.kortev.shootingstar.gap.GapTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

/** What goes over the shooter's screen through a Ginnungagap event: the key's readout, the feed, and the way out of the black. */
public final class GapHud {
	private static final int WHITE = 0xFFFFFF;
	private static final int PALE = 0xA8F8FF;

	private GapHud() {
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientGap gap = ClientGaps.mine();
		if (gap == null || client.player == null) {
			return;
		}
		float tickDelta = counter.getTickDelta(false);
		double t = gap.time(tickDelta);
		TextRenderer font = client.textRenderer;
		float w = ctx.getScaledWindowWidth();
		float h = ctx.getScaledWindowHeight();

		if (ClientGaps.feedShowing(gap, t)) {
			Feed.renderGap(ctx, t);
			return;
		}
		if (t < GapTimeline.RISE + 8) {
			float a = (float) (1.0 - ease((t - GapTimeline.RISE) / 8.0));
			header(ctx, font, w, typed("[ Ω-00 · GENESIS KEY ]", (t - 4) / 12.0), a);
			String line = t < 27 ? typed("SEARCHING NEIGHBOURING UNIVERSES", (t - 12) / 12.0) : typed("UNIVERSE 4,096,113 · FOUND", (t - 27) / 7.0);
			text(ctx, font, line, w / 2, 34, 1.0F, PALE, a, true);
		} else if (t >= GapTimeline.END) {
			// Nothing is said in the black. Only, after a while, how to get out of it.
			float a = (float) ease((t - GapTimeline.END - 40) / 20.0);
			text(ctx, font, "USE THE KEY TO LET REALITY BACK IN", w / 2, h * 0.16F, 1.0F, 0x8A8A8A, a, true);
		}
	}

	private static String typed(String s, double p) {
		p = MathHelper.clamp(p, 0.0, 1.0);
		int n = (int) Math.floor(s.length() * p);
		return s.substring(0, n) + (n < s.length() && p > 0.0 ? "_" : "");
	}

	private static void header(DrawContext ctx, TextRenderer font, float w, String s, float a) {
		text(ctx, font, s, w / 2, 14, 1.4F, WHITE, a, true);
	}

	private static void text(DrawContext ctx, TextRenderer font, String s, float x, float y, float scale, int color, float a,
			boolean centered) {
		if (a <= 0.01F || s.isEmpty()) {
			return;
		}
		int argb = (Math.max(4, (int) (a * 255)) << 24) | color;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x, y, 0);
		ctx.getMatrices().scale(scale, scale, 1.0F);
		int dx = centered ? -font.getWidth(s) / 2 : 0;
		ctx.drawText(font, s, dx, 0, argb, true);
		ctx.getMatrices().pop();
	}

	private static double ease(double x) {
		return GapCamera.ease(x);
	}
}
