package io.github.kortev.shootingstar.client.render;

import io.github.kortev.shootingstar.client.Aim;
import io.github.kortev.shootingstar.client.ClientStrike;
import io.github.kortev.shootingstar.client.ClientStrikes;
import io.github.kortev.shootingstar.client.feed.Feed;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Everything the strike draws over the HUD outside the feed itself. */
public final class HudEffects {
	private static final int RED = 0xFFFF4A32;
	private static final int ORANGE = 0xFFFF8A2A;
	private static final int WHITE = 0xFFEDEDED;
	private static final int GREY = 0xFFA8A8A8;

	private record Words(float x, float y, String text, int color, float scale, boolean centered) {
	}

	private HudEffects() {
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) {
			return;
		}
		float tickDelta = counter.getTickDelta(false);
		ClientStrike cinematic = ClientStrikes.cinematic();
		if (cinematic != null && ClientStrikes.feedActive(cinematic, cinematic.time(tickDelta))) {
			Feed.render(ctx, cinematic, tickDelta);
			return;
		}
		Textures.ensure();
		float w = ctx.getScaledWindowWidth();
		float h = ctx.getScaledWindowHeight();
		Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
		TextRenderer font = client.textRenderer;
		List<Words> words = new ArrayList<>();
		boolean shot = cinematic != null && ClientStrikes.shotActive(cinematic, cinematic.time(tickDelta));

		ctx.draw();
		Gfx.begin2d();
		for (ClientStrike strike : ClientStrikes.all()) {
			double t = strike.time(tickDelta);
			if (t < StrikeTimeline.IMPACT) {
				if (t < StrikeTimeline.INBOUND) {
					lockMarker(m, w, h, strike, t, words, client);
				} else {
					inboundStar(m, w, h, strike, t);
				}
			} else {
				if (shot && strike == cinematic && t < StrikeTimeline.IMPACT_FRAME_END) {
					impactFrame(m, w, h, strike, t);
				}
				flash(m, w, h, strike, t, client);
				if (strike.mine && t >= StrikeTimeline.IMPACT + 18) {
					readout(words, w, h, strike, t);
				}
			}
		}
		if (Aim.holding && !shot) {
			aimInfo(m, w, h, words, client);
		}
		Gfx.end2d();

		for (Words word : words) {
			ctx.getMatrices().push();
			ctx.getMatrices().translate(word.x(), word.y(), 0);
			ctx.getMatrices().scale(word.scale(), word.scale(), 1.0F);
			int x = word.centered() ? -font.getWidth(word.text()) / 2 : 0;
			ctx.drawText(font, word.text(), x, 0, word.color(), true);
			ctx.getMatrices().pop();
		}
		if (Aim.holding && !shot && !client.options.hudHidden) {
			statusCard(ctx, font, w, h);
		}
		ctx.draw();
	}

	// --- before impact ---------------------------------------------------------------------

	private static void lockMarker(Matrix4f m, float w, float h, ClientStrike strike, double t, List<Words> words,
			MinecraftClient client) {
		Vec3d c = strike.center;
		double distance = client.player.getPos().distanceTo(c);
		if (distance > 720) {
			return;
		}
		Vector3f p = WorldProjector.project(c.x, c.y + 0.5, c.z, w, h);
		if (p == null || p.x < -40 || p.y < -40 || p.x > w + 40 || p.y > h + 40) {
			return;
		}
		float blink = t < 12 && ((int) (t / 2)) % 2 == 0 ? 0.3F : 1.0F;
		BufferBuilder b = Gfx.quads();
		Gfx.brackets(b, m, p.x, p.y, 6, 3, 1, Gfx.fade(RED, blink));
		Gfx.diamond(b, m, p.x, p.y, 1.5F, Gfx.fade(RED, blink));
		Gfx.draw(b);
		words.add(new Words(p.x + 9, p.y - 5, "KINETIC LOCK", Gfx.fade(RED, blink), 0.75F, false));
		words.add(new Words(p.x + 9, p.y + 3, String.format(Locale.ROOT, "%d M · T-%.1fs", (int) distance,
				(StrikeTimeline.IMPACT - t) / 20.0), GREY, 0.6F, false));
	}

	/** The round as a star above the target, swelling as it falls. */
	private static void inboundStar(Matrix4f m, float w, float h, ClientStrike strike, double t) {
		double p = (t - StrikeTimeline.INBOUND) / (StrikeTimeline.IMPACT - StrikeTimeline.INBOUND);
		double height = 2400.0 * Math.pow(1.0 - p, 3.0) + 30.0;
		Vec3d c = strike.center;
		Vector3f star = WorldProjector.project(c.x, c.y + height, c.z, w, h);
		if (star == null) {
			return;
		}
		Vector3f trail = WorldProjector.project(c.x, c.y + height + 600.0, c.z, w, h);
		float size = (float) (5.0 + 30.0 * p * p);
		float alpha = (float) MathHelper.clamp(p * 4.0, 0.0, 1.0);

		BufferBuilder lines = Gfx.quads();
		if (trail != null) {
			Gfx.line(lines, m, star.x, star.y, trail.x, trail.y, 1.4F, Gfx.fade(0xFFFFE6C8, 0.8F * alpha), 0x00FFE6C8);
		}
		float reach = (float) (w * (0.18 + 0.4 * p));
		Gfx.line(lines, m, star.x - reach, star.y, star.x + reach, star.y, 0.8F, 0x00FFD8B0, Gfx.fade(0xFFFFD8B0, 0.0F));
		Gfx.line(lines, m, star.x - reach, star.y, star.x, star.y, 0.8F, 0x00FFD8B0, Gfx.fade(0xFFFFD8B0, 0.8F * alpha));
		Gfx.line(lines, m, star.x, star.y, star.x + reach, star.y, 0.8F, Gfx.fade(0xFFFFD8B0, 0.8F * alpha), 0x00FFD8B0);
		Gfx.line(lines, m, star.x, star.y - reach * 0.7F, star.x, star.y, 0.8F, 0x00FFD8B0, Gfx.fade(0xFFFFD8B0, 0.8F * alpha));
		Gfx.line(lines, m, star.x, star.y, star.x, star.y + reach * 0.7F, 0.8F, Gfx.fade(0xFFFFD8B0, 0.8F * alpha), 0x00FFD8B0);
		Gfx.circle(lines, m, star.x, star.y, size * 0.8F, 0.6F, Gfx.fade(0xFFFFC890, 0.7F * alpha), 48);
		Gfx.circle(lines, m, star.x, star.y, size * 1.25F, 0.5F, Gfx.fade(0xFFFFC890, 0.4F * alpha), 48);
		Gfx.additive();
		Gfx.draw(lines);

		BufferBuilder glow = Gfx.texQuads();
		Gfx.sprite(glow, m, star.x, star.y, size * 2.5F, size * 2.5F, 0, Gfx.fade(0xFFFF9A50, 0.45F * alpha));
		Gfx.sprite(glow, m, star.x, star.y, size, size, 0, Gfx.fade(0xFFFFFFFF, alpha));
		Gfx.drawTex(glow, Textures.GLOW);
		Gfx.alpha();
	}

	// --- impact ----------------------------------------------------------------------------

	/** White flash then an orange afterglow, scaled by distance and whether you were looking. */
	private static void flash(Matrix4f m, float w, float h, ClientStrike strike, double t, MinecraftClient client) {
		double e = t - strike.impactAge;
		if (e > 60 || e < 0) {
			return;
		}
		Vec3d eye = client.player.getEyePos();
		Vec3d to = strike.center.subtract(eye);
		double distance = to.length();
		double near = MathHelper.clamp(1.0 - distance / 900.0, 0.0, 1.0);
		double looking = 0.35 + 0.65 * Math.max(0.0, client.player.getRotationVec(1.0F).dotProduct(to.normalize()));
		float white = (float) (near * looking * Math.exp(-e / 3.0));
		float orange = (float) (near * looking * 0.55 * Math.exp(-e / 16.0));
		if (strike.cinematic()) {
			// The shooter gets a short pop of white so the halftone frame stays readable.
			white = (float) (0.85 * Math.exp(-e / 1.2));
			orange = (float) (0.3 * Math.exp(-e / 10.0));
		}
		BufferBuilder b = Gfx.quads();
		Gfx.rect(b, m, 0, 0, w, h, Gfx.fade(0xFFFF8A3A, orange));
		Gfx.rect(b, m, 0, 0, w, h, Gfx.fade(0xFFFFFFFF, white));
		Gfx.additive();
		Gfx.draw(b);
		Gfx.alpha();
	}

	/** The comic-book halftone frame on the shooter's aerial shot of the hit. */
	private static void impactFrame(Matrix4f m, float w, float h, ClientStrike strike, double t) {
		double e = t - StrikeTimeline.IMPACT;
		float fade = (float) MathHelper.clamp((StrikeTimeline.IMPACT_FRAME_END - t) / 3.0, 0.0, 1.0);
		Vec3d c = strike.center;
		Vector3f hit = WorldProjector.project(c.x, c.y, c.z, w, h);
		float hx = hit != null ? hit.x : w / 2;
		float hy = hit != null ? hit.y : h / 2;

		BufferBuilder tint = Gfx.quads();
		Gfx.rect(tint, m, 0, 0, w, h, Gfx.fade(0xFFFF5A1E, 0.62F * fade));
		Gfx.draw(tint);

		// The halftone screen is centred on the hit and always reaches the far corners.
		BufferBuilder dots = Gfx.texQuads();
		float size = (float) Math.max(Math.max(Math.hypot(hx, hy), Math.hypot(w - hx, hy)),
				Math.max(Math.hypot(hx, h - hy), Math.hypot(w - hx, h - hy)));
		Gfx.texRect(dots, m, hx - size, hy - size, hx + size, hy + size, 0, 0, 1, 1, Gfx.fade(0xFFFFFFFF, 0.9F * fade));
		Gfx.drawTex(dots, Textures.HALFTONE);

		// Wireframe shock rings and speed lines in white.
		BufferBuilder lines = Gfx.quads();
		double grow = 1.0 + e * 0.35;
		for (int ring = 0; ring < 4; ring++) {
			double radius = strike.radius * (0.25 + ring * 0.28) * grow;
			double y = c.y + 2 + ring * 9;
			worldCircle(lines, m, w, h, c.x, y, c.z, radius, Gfx.fade(0xFFFFFFFF, 0.85F * fade), 0.9F);
		}
		for (int i = 0; i < 28; i++) {
			double a = Math.PI * 2 * i / 28 + 0.1;
			float inner = (float) (Math.min(w, h) * 0.12);
			float outer = (float) (Math.max(w, h) * 0.9);
			Gfx.line(lines, m, hx + (float) Math.cos(a) * inner, hy + (float) Math.sin(a) * inner,
					hx + (float) Math.cos(a) * outer, hy + (float) Math.sin(a) * outer, 0.7F, Gfx.fade(0xFFFFFFFF, 0.6F * fade), 0x00FFFFFF);
		}
		Gfx.draw(lines);

		BufferBuilder core = Gfx.texQuads();
		float coreSize = (float) (Math.min(w, h) * (0.12 + 0.02 * e));
		Gfx.sprite(core, m, hx, hy, coreSize, coreSize, 0, Gfx.fade(0xFFFFFFFF, fade));
		Gfx.additive();
		Gfx.drawTex(core, Textures.GLOW);
		Gfx.alpha();
	}

	private static void worldCircle(BufferBuilder b, Matrix4f m, float w, float h, double cx, double cy, double cz,
			double radius, int color, float width) {
		Vector3f prev = null;
		for (int i = 0; i <= 64; i++) {
			double a = Math.PI * 2 * i / 64;
			Vector3f p = WorldProjector.project(cx + Math.cos(a) * radius, cy, cz + Math.sin(a) * radius, w, h);
			if (p != null && prev != null) {
				Gfx.line(b, m, prev.x, prev.y, p.x, p.y, width, color);
			}
			prev = p;
		}
	}

	private static void readout(List<Words> words, float w, float h, ClientStrike strike, double t) {
		float alpha = (float) MathHelper.clamp((StrikeTimeline.END - t) / 30.0, 0.0, 1.0);
		float in = (float) MathHelper.clamp((t - StrikeTimeline.IMPACT - 18) / 4.0, 0.0, 1.0);
		alpha *= in;
		if (alpha <= 0.02F) {
			return;
		}
		String detail;
		if (strike.zoneDiameter == 0) {
			detail = "TERRAIN HELD · NO SPIRE";
		} else if (strike.spireHeight > 0) {
			detail = String.format(Locale.ROOT, "ZONE %04d PLANED · SPIRE STANDING · %d M", strike.zoneDiameter,
					strike.spireHeight);
		} else {
			detail = String.format(Locale.ROOT, "ZONE %04d PLANED", strike.zoneDiameter);
		}
		words.add(new Words(w / 2, h - 58, "[ IMPACT CONFIRMED ]", Gfx.fade(RED, alpha), 1.0F, true));
		words.add(new Words(w / 2, h - 46, detail, Gfx.fade(WHITE, alpha), 0.75F, true));
	}

	// --- uplink in hand --------------------------------------------------------------------

	private static void aimInfo(Matrix4f m, float w, float h, List<Words> words, MinecraftClient client) {
		ClientStrike mine = ClientStrikes.mine();
		String text;
		int color;
		if (mine != null && mine.age < StrikeTimeline.IMPACT) {
			text = String.format(Locale.ROOT, "ROUND INBOUND · T-%.1fs", (StrikeTimeline.IMPACT - mine.age) / 20.0);
			color = ORANGE;
		} else if (Aim.cooldown > 0.0F) {
			text = "CYCLING";
			color = GREY;
		} else if (Aim.target == null) {
			text = "NO SOLUTION";
			color = GREY;
		} else if (Aim.dangerClose) {
			text = "DANGER CLOSE";
			color = RED;
		} else {
			text = String.format(Locale.ROOT, "RANGE %d M", (int) Aim.distance);
			color = ORANGE;
		}
		words.add(new Words(w / 2 + 8, h / 2 + 6, text, color, 0.6F, false));
	}

	private static void statusCard(DrawContext ctx, TextRenderer font, float w, float h) {
		int cardW = 64;
		int cardH = 36;
		int x = 6;
		int y = (int) h - cardH - 8;
		ctx.fill(x - 2, y - 2, x + cardW + 2, y + cardH + 2, 0xCC120A08);
		ctx.drawBorder(x - 2, y - 2, cardW + 4, cardH + 4, 0xFFC8321E);
		ctx.drawTexture(Textures.CARD, x, y, 0, 0, cardW, cardH, cardW, cardH);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x - 2, y - 10, 0);
		ctx.getMatrices().scale(0.75F, 0.75F, 1.0F);
		ctx.drawText(font, Text.literal("SS-03 · Gungnir"), 0, 0, 0xFFFF5A3C, true);
		ctx.getMatrices().pop();
	}
}
