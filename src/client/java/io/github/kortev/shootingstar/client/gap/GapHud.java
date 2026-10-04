package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.client.render.WorldProjector;
import io.github.kortev.shootingstar.gap.GapTimeline;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/** The words over a Ginnungagap event, as the shooter sees them: headers, readouts, the title and the labels. */
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

		if (t < GapTimeline.TURNED) {
			header(ctx, font, w, typed("[ Ω-00 · GENESIS KEY ]", (t - 4) / 16.0), 1.0F);
			String line = t < 38 ? typed("SEARCHING NEIGHBOURING UNIVERSES", (t - 18) / 14.0) : typed("UNIVERSE 4,096,113 · FOUND", (t - 38) / 8.0);
			text(ctx, font, line, w / 2, 34, 1.0F, PALE, 1.0F, true);
		} else if (t < GapTimeline.CLOSING) {
			float title = (float) (ease((t - GapTimeline.TEAR - 8) / 8.0) * (1.0 - ease((t - GapTimeline.TEAR - 40) / 8.0)));
			if (title > 0.0F) {
				ctx.fill(0, (int) (h * 0.66F), (int) w, (int) (h * 0.94F), (int) (0x80 * title) << 24);
				text(ctx, font, "Ω-00", w / 2, h * 0.69F, 1.2F, WHITE, title, true);
				text(ctx, font, "GINNUNGAGAP", w / 2, h * 0.75F, 4.0F, WHITE, title, true);
				text(ctx, font, "IT CRASHES A UNIVERSE INTO OURS", w / 2, h * 0.87F, 1.2F, PALE, title, true);
			}
			Vec3d tip = gap.shardTip(t).add(0, 24, 0);
			Vector3f p = WorldProjector.project(tip.x, tip.y, tip.z, w, h);
			float label = (float) ease((t - GapTimeline.TEAR - 16) / 8.0);
			// Kept clear of the header when the shard is high in the frame.
			if (p != null && label > 0.0F && p.y > 40 && p.y < h * 0.62F) {
				leader(ctx, font, p.x + 8, p.y, p.x + 48, p.y + 18, "UNIVERSE 4,096,113", label);
			}
		} else if (t < GapTimeline.CONTACT) {
			boolean eyes = t >= GapCamera.CUT_EYES && t < GapCamera.CUT_EYES_END;
			if (eyes) {
				text(ctx, font, gapReadout(t), w / 2, 34, 1.0F, PALE, 1.0F, true);
			} else {
				footer(ctx, font, w, h, gapReadout(t), 1.0F);
			}
		} else if (t >= GapTimeline.END) {
			// Nothing is said in the black. Only, after a while, how to get out of it.
			float a = (float) ease((t - GapTimeline.END - 40) / 20.0);
			text(ctx, font, "USE THE KEY TO LET REALITY BACK IN", w / 2, h * 0.16F, 1.0F, 0x8A8A8A, a, true);
		}
	}

	private static String gapReadout(double t) {
		double p = MathHelper.clamp((t - GapTimeline.CLOSING) / (GapTimeline.CONTACT - GapTimeline.CLOSING), 0.0, 1.0);
		if (p >= 1.0) {
			return "GAP 0 KM";
		}
		double e = 23.94 * Math.pow(1.0 - p, 1.3);
		double v = Math.pow(10.0, e);
		if (e >= 4.0) {
			int ex = (int) Math.floor(e);
			return String.format(Locale.ROOT, "GAP %.2f × 10%s KM", v / Math.pow(10.0, ex), superscript(ex));
		}
		return String.format(Locale.ROOT, e > 2.0 ? "GAP %.0f KM" : "GAP %.3f KM", v - 1.0);
	}

	private static String superscript(int n) {
		String digits = "⁰¹²³⁴⁵⁶⁷⁸⁹";
		StringBuilder b = new StringBuilder();
		for (char c : Integer.toString(n).toCharArray()) {
			b.append(digits.charAt(c - '0'));
		}
		return b.toString();
	}

	private static String typed(String s, double p) {
		p = MathHelper.clamp(p, 0.0, 1.0);
		int n = (int) Math.floor(s.length() * p);
		return s.substring(0, n) + (n < s.length() && p > 0.0 ? "_" : "");
	}

	private static void header(DrawContext ctx, TextRenderer font, float w, String s, float a) {
		text(ctx, font, s, w / 2, 14, 1.4F, WHITE, a, true);
	}

	private static void footer(DrawContext ctx, TextRenderer font, float w, float h, String s, float a) {
		text(ctx, font, s, w / 2, h - 22, 1.15F, WHITE, a, true);
	}

	private static void leader(DrawContext ctx, TextRenderer font, float x0, float y0, float x1, float y1, String s, float a) {
		leader(ctx, font, x0, y0, x1, y1, s, a, WHITE);
	}

	private static void leader(DrawContext ctx, TextRenderer font, float x0, float y0, float x1, float y1, String s, float a, int color) {
		if (a <= 0.01F) {
			return;
		}
		int argb = (int) (a * 255) << 24 | color;
		int steps = Math.max(1, (int) Math.hypot(x1 - x0, y1 - y0));
		for (int i = 0; i <= steps; i++) {
			int x = Math.round(MathHelper.lerp((float) i / steps, x0, x1));
			int y = Math.round(MathHelper.lerp((float) i / steps, y0, y1));
			ctx.fill(x, y, x + 1, y + 1, argb);
		}
		text(ctx, font, s, x1 + 4, y1 - 4, 1.0F, color, a, false);
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
