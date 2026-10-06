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
		} else if (gap.rebuildAt >= 0) {
			loading(ctx, font, w, h, gap.rebuild(tickDelta));
		} else if (t >= GapTimeline.END) {
			// Nothing is said in the black. Only, after a while, how to get out of it.
			float a = (float) ease((t - GapTimeline.END - 40) / 20.0);
			text(ctx, font, "USE THE KEY TO LET REALITY BACK IN", w / 2, h * 0.16F, 1.0F, 0x8A8A8A, a, true);
		}
	}

	/**
	 * The rebuild's loading screen, low on the picture: what is happening, a bar that sticks and then runs as reality is
	 * remade from the edges in, and the count of what is being put back.
	 */
	private static void loading(DrawContext ctx, TextRenderer font, float w, float h, double r) {
		float a = (float) (ease(r / 20.0) * (1.0 - ease((r - GapTimeline.REBUILD_END + 40) / 30.0)));
		header(ctx, font, w, typed("[ REBUILDING REALITY ]", r / 25.0), a);
		String status;
		if (r < GapTimeline.REBUILD_TREE) {
			status = "SIGNAL LOST · SEARCHING FOR A WORLD";
		} else if (r < GapTimeline.REBUILD_WORLDS) {
			status = "SOMETHING IS GROWING IN THE VOID";
		} else if (r < GapTimeline.REBUILD_SWEEP) {
			status = "YGGDRASIL · THE AXIS OF THE NINE WORLDS";
		} else if (r < GapTimeline.REBUILD_DONE) {
			long placed = Math.round(3_912_004L * ease((r - GapTimeline.REBUILD_SWEEP) / (GapTimeline.REBUILD_DONE - GapTimeline.REBUILD_SWEEP)));
			status = "REMAKING MIDGARD · " + String.format(java.util.Locale.ROOT, "%,d", placed) + " BLOCKS PLACED";
		} else {
			status = "REALITY RESTORED";
		}
		text(ctx, font, status, w / 2, h * 0.78F, 1.0F, PALE, a, true);
		// The bar: a few percent while the void is searched, stuck while the tree grows, then running with the remaking.
		double p;
		if (r < GapTimeline.REBUILD_WORLDS) {
			p = 0.04 * ease(r / GapTimeline.REBUILD_WORLDS);
		} else if (r < GapTimeline.REBUILD_SWEEP) {
			p = 0.04 + 0.03 * ease((r - GapTimeline.REBUILD_WORLDS - 60) / 40.0);
		} else {
			p = 0.07 + 0.93 * ease((r - GapTimeline.REBUILD_SWEEP) / (GapTimeline.REBUILD_DONE + 20 - GapTimeline.REBUILD_SWEEP));
		}
		int width = (int) Math.min(240.0F, w * 0.5F);
		int x0 = (int) (w / 2) - width / 2;
		int y0 = (int) (h * 0.78F) + 14;
		int alpha = Math.max(4, (int) (a * 255)) << 24;
		ctx.fill(x0 - 1, y0 - 1, x0 + width + 1, y0 + 4, alpha | 0x303040);
		ctx.fill(x0, y0, x0 + (int) Math.round(width * p), y0 + 3, alpha | 0xD8C8FF);
		text(ctx, font, Math.round(p * 100.0) + "%", x0 + width + 8, y0 - 2, 1.0F, WHITE, a, false);
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
