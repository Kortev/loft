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
		// The falling star, the flash and the impact frames are drawn in the world (WorldFx).
		for (ClientStrike strike : ClientStrikes.all()) {
			double t = strike.time(tickDelta);
			if (t < StrikeTimeline.INBOUND) {
				lockMarker(m, w, h, strike, t, words, client);
			} else if (strike.mine && t >= StrikeTimeline.IMPACT + 18) {
				readout(words, w, h, strike, t);
			}
		}
		if (Aim.holding && !shot && !client.options.hudHidden) {
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

	// --- impact ----------------------------------------------------------------------------

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
