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
	private static final int CYAN = 0x3BE8E2;
	private static final int PALE = 0xA8F8FF;
	private static final int RED = 0xFF3B30;

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

		if (t < GapTimeline.AIM) {
			header(ctx, font, w, typed("[ Ω-00 · GENESIS KEY ]", (t - 4) / 16.0), 1.0F);
			String line = t < 38 ? typed("SEARCHING NEIGHBOURING UNIVERSES", (t - 18) / 14.0) : typed("UNIVERSE 4,096,113 · FOUND", (t - 38) / 8.0);
			text(ctx, font, line, w / 2, 34, 1.0F, PALE, 1.0F, true);
		} else if (t < GapTimeline.TEAR) {
			header(ctx, font, w, typed("[ LOCK ]", (t - GapTimeline.AIM) / 8.0), 1.0F);
			float lock = (float) (ease((t - 104) / 3.0) * (1.0 - ease((t - 134) / 6.0)));
			Vector3f p = WorldProjector.project(gap.contact.x, gap.contact.y + 2.5, gap.contact.z, w, h);
			if (p != null && lock > 0.0F) {
				text(ctx, font, "TARGET LOCKED", p.x, p.y - 16, 1.0F, RED, lock, true);
			}
			int blocks = (int) Math.round(gap.shooterPos.distanceTo(gap.contact));
			footer(ctx, font, w, h, t < 126 ? String.format(Locale.ROOT, "TARGET %03d BLOCKS AWAY", blocks)
					: "PULLING UNIVERSE 4,096,113 TOWARDS THE TARGET", t < 126 ? 1.0F : (float) ease((t - 126) / 6.0));
		} else if (t < GapTimeline.CLOSING) {
			header(ctx, font, w, typed("[ GAP OPENING ]", (t - GapTimeline.TEAR) / 12.0), 1.0F);
			float title = (float) (ease((t - 158) / 8.0) * (1.0 - ease((t - 190) / 8.0)));
			if (title > 0.0F) {
				ctx.fill(0, (int) (h * 0.66F), (int) w, (int) (h * 0.94F), (int) (0x80 * title) << 24);
				text(ctx, font, "Ω-00", w / 2, h * 0.69F, 1.2F, WHITE, title, true);
				text(ctx, font, "GINNUNGAGAP", w / 2, h * 0.75F, 4.0F, WHITE, title, true);
				text(ctx, font, "IT STEERS A UNIVERSE INTO OURS", w / 2, h * 0.87F, 1.2F, PALE, title, true);
			}
			Vector3f p = WorldProjector.project(gap.contact.x, gap.contact.y + gap.lift(t) - GapTimeline.PEAK + 6, gap.contact.z, w, h);
			float label = (float) ease((t - 166) / 8.0);
			if (p != null && label > 0.0F) {
				leader(ctx, font, p.x, p.y, p.x + 40, p.y + 18, "UNIVERSE 4,096,113 · UPSIDE DOWN", label);
			}
		} else if (t < GapTimeline.CONTACT) {
			boolean eyes = t >= 236 && t < 248;
			header(ctx, font, w, "[ CLOSING ]", 1.0F);
			if (eyes) {
				text(ctx, font, gapReadout(t), w / 2, 34, 1.0F, PALE, 1.0F, true);
			} else {
				footer(ctx, font, w, h, gapReadout(t), 1.0F);
			}
			if (t >= 248 && t < 292) {
				boolean swapped = t >= GapTimeline.TREE_SWAP;
				Vec3d ours = Vec3d.ofBottomCenter(gap.swapSpot).add(0, 4, 0);
				Vec3d theirs = new Vec3d(ours.x, gap.mirrorY(gap.swapSpot.getY() + 4, t), ours.z);
				float a = (float) ease((t - 252) / 6.0);
				String noun = gap.tree ? "TREE" : "GROUND";
				Vector3f po = WorldProjector.project(ours.x, ours.y, ours.z, w, h);
				Vector3f pt = WorldProjector.project(theirs.x, theirs.y, theirs.z, w, h);
				if (po != null) {
					leader(ctx, font, po.x + 6, po.y, po.x + 46, po.y - 14, (swapped ? "THEIR " : "OUR ") + noun, a,
							swapped ? CYAN : WHITE);
				}
				if (pt != null) {
					leader(ctx, font, pt.x + 6, pt.y, pt.x + 46, pt.y + 14, (swapped ? "OUR " : "THEIR ") + noun, a,
							swapped ? WHITE : CYAN);
				}
				if (swapped) {
					caption(ctx, font, w, h, typed("CLOSE ENOUGH TO SWAP MATTER", (t - GapTimeline.TREE_SWAP) / 12.0), 1.0F);
				}
			}
			if (t >= 292) {
				caption(ctx, font, w, h, "BLOCKS SWAPPING ALL OVER THE MAP", (float) (ease((t - 292) / 4.0) * (1.0 - ease((t - 304) / 5.0))));
			}
		} else if (t < GapTimeline.FRAMES) {
			float a = (float) ease((t - GapTimeline.CONTACT) / 3.0);
			text(ctx, font, "CONTACT", w / 2, h * 0.18F, 3.2F, WHITE, a, true);
			footer(ctx, font, w, h, "UNIVERSE 1 × UNIVERSE 4,096,113 · ALL SOUND STOPS", (float) ease((t - GapTimeline.CONTACT - 4) / 6.0));
		} else if (t >= GapTimeline.ERASURE && t < GapTimeline.NOTHING) {
			header(ctx, font, w, typed("[ ERASURE ]", (t - GapTimeline.ERASURE) / 10.0), 1.0F);
			double front = GapTimeline.eraseFront(t);
			int pct = (int) Math.floor(100.0 * MathHelper.clamp(front / 900.0, 0.0, 1.0));
			footer(ctx, font, w, h, "REALITY ERASED " + pct + "%", 1.0F);
			if (t >= 600) {
				text(ctx, font, "[ REALITY DELETED ]", w / 2, h * 0.36F, 2.6F, WHITE, (float) ease((t - 600) / 4.0), true);
			}
		} else if (t >= GapTimeline.NOTHING && t < GapTimeline.END) {
			if (t < 720) {
				header(ctx, font, w, typed("[ GINNUNGAGAP ]", (t - 640) / 16.0), 1.0F);
				caption(ctx, font, w, h, typed("ALL OF REALITY IS GONE", (t - 650) / 18.0), 1.0F);
			} else {
				text(ctx, font, typed("THERE IS NOTHING LEFT BUT YOU", (t - 732) / 24.0), w / 2, h * 0.16F, 1.6F, WHITE, 1.0F, true);
			}
		} else if (t >= GapTimeline.END) {
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

	private static void caption(DrawContext ctx, TextRenderer font, float w, float h, String s, float a) {
		text(ctx, font, s, w / 2, h * 0.82F, 1.6F, WHITE, a, true);
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
