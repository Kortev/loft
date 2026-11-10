package io.github.kortev.chainsawman.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * The Eternity Devil's words on the screen: the crowd voice, shown as the same line written over itself several times, each copy a little
 * off and drifting, so it reads as many mouths saying it at once. Large, in a dark band, so it can be read on a phone.
 */
public final class HotelHud {
	private HotelHud() {
	}

	/** The mouth's dark red while swallowed, and the red-black flash as the shaft arrives. */
	private static void shaft(DrawContext context, float delta) {
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		if (ClientHotel.swallowed()) {
			swallowed(context, width, height, delta);
		}
		float flash = ClientHotel.flash(delta);
		if (flash > 0.0F) {
			int a = (int) (flash * 235.0F);
			context.fill(0, 0, width, height, a << 24 | 0x1A0404);
		}
	}

	private static int argb(int a, int r, int g, int b) {
		return MathHelper.clamp(a, 0, 255) << 24 | MathHelper.clamp(r, 0, 255) << 16 | MathHelper.clamp(g, 0, 255) << 8 | MathHelper.clamp(b, 0, 255);
	}

	/** Where the wounds open, as fractions of the screen (round the middle, where the crosshair cuts). */
	private static final float[][] WOUNDS = {{0.5F, 0.52F}, {0.36F, 0.46F}, {0.63F, 0.58F}, {0.45F, 0.66F}, {0.58F, 0.38F}, {0.32F, 0.62F}, {0.68F, 0.44F}};

	/**
	 * Inside the mouth: walls of flesh close round you from every side and heave (ridged, wet, lit dimly from above), a slit of light far up
	 * where the mouth is, and every cut you make opens a lit wound that widens as you cut toward out, until the light floods in.
	 */
	private static void swallowed(DrawContext context, int width, int height, float delta) {
		float t = ClientHotel.swallowedFor(delta);
		float out = ClientHotel.cutOut();
		int cuts = ClientHotel.cuts();
		float fresh = MathHelper.clamp(1.0F - ClientHotel.sinceCut(delta) / 8.0F, 0.0F, 1.0F);
		// It closes in over the first second, then heaves.
		float close = MathHelper.clamp(t / 18.0F, 0.0F, 1.0F);
		float heave = 0.5F + 0.5F * MathHelper.sin(t * 0.16F);
		context.fill(0, 0, width, height, argb(245, 22, 4, 4));
		// The slit of light up where the mouth is: a thin lens, breathing, a glow round it.
		int sx = width / 2;
		int sy = (int) (height * 0.13F);
		float slitW = width * (0.16F + 0.05F * heave) * (1.0F + 0.6F * out);
		for (int k = 4; k >= 0; k--) {
			float grow = 1.0F + k * 0.55F;
			int halfH = (int) ((1.5F + 1.6F * heave) * grow);
			int alpha = k == 0 ? 255 : 40 - k * 6;
			lens(context, sx, sy, slitW * grow * 0.5F, halfH, k == 0 ? argb(alpha, 255, 214, 160) : argb(alpha, 255, 120, 60), true);
		}
		// The walls: four sides of ridged flesh whose inner edge waves and squeezes in and out.
		int step = 2;
		for (int y = 0; y < height; y += step) {
			float fy = y / (float) height;
			float wave = MathHelper.sin(fy * 9.0F + t * 0.11F) * 0.035F + MathHelper.sin(fy * 23.0F - t * 0.07F) * 0.012F;
			float inset = (0.14F + 0.07F * heave + wave) * close * (1.0F - 0.55F * out);
			int edgeL = (int) (width * inset);
			int edgeR = (int) (width * (1.0F - inset - 0.01F * MathHelper.sin(fy * 13.0F + t * 0.09F)));
			boolean ridge = Math.floorMod((int) (y + t * 1.3F), 22) < 4;
			// (Lit from above: the walls are lighter toward the slit.)
			float lit = 1.0F - fy * 0.6F;
			int dark = argb(250, (int) (48 * lit), 10, 9);
			int meat = ridge ? argb(250, (int) (128 * lit), (int) (40 * lit), (int) (34 * lit)) : argb(250, (int) (92 * lit), (int) (22 * lit), (int) (19 * lit));
			int band = Math.max(3, width / 40);
			context.fill(0, y, Math.max(0, edgeL - band), y + step, dark);
			context.fill(Math.max(0, edgeL - band), y, edgeL, y + step, meat);
			context.fill(edgeR, y, Math.min(width, edgeR + band), y + step, meat);
			context.fill(Math.min(width, edgeR + band), y, width, y + step, dark);
		}
		for (int x = 0; x < width; x += step) {
			float fx = x / (float) width;
			float wave = MathHelper.sin(fx * 8.0F - t * 0.13F) * 0.03F;
			int top = (int) (height * (0.05F + 0.04F * heave + wave) * close * (1.0F - 0.5F * out));
			int bottom = (int) (height * (1.0F - (0.12F + 0.06F * heave - wave) * close * (1.0F - 0.5F * out)));
			boolean ridge = Math.floorMod((int) (x - t * 1.1F), 26) < 5;
			int band = Math.max(3, height / 50);
			context.fill(x, 0, x + step, Math.max(0, top - band), argb(250, 70, 16, 14));
			context.fill(x, Math.max(0, top - band), x + step, top, ridge ? argb(250, 150, 52, 40) : argb(250, 110, 30, 25));
			context.fill(x, bottom, x + step, Math.min(height, bottom + band), ridge ? argb(250, 70, 18, 15) : argb(250, 52, 12, 10));
			context.fill(x, Math.min(height, bottom + band), x + step, height, argb(250, 28, 5, 5));
		}
		// The wounds: one for each cut, all wider the nearer you are to out, the newest flaring white as it opens.
		for (int i = 0; i < Math.min(cuts, WOUNDS.length); i++) {
			float[] w = WOUNDS[i];
			boolean newest = i == cuts - 1;
			float open = 0.25F + 0.75F * out;
			float halfH = height * (0.06F + 0.16F * open);
			float halfW = width * (0.008F + 0.05F * open) * (newest ? 1.0F + 0.5F * fresh : 1.0F);
			int cx = (int) (width * w[0]);
			int cy = (int) (height * w[1]);
			// A glow round it, the torn rim, the light inside.
			lens(context, cx, cy, halfW * 3.2F, (int) (halfH * 1.25F), argb(46 + (int) (60 * out), 255, 90, 30), false);
			lens(context, cx, cy, halfW * 1.6F, (int) (halfH * 1.08F), argb(255, 190, 36, 22), false);
			int core = newest ? (int) (200 + 55 * fresh) : 200;
			lens(context, cx, cy, halfW, (int) halfH, argb(255, 255, core, (int) (120 + 110 * (newest ? fresh : 0.0F))), false);
		}
		if (out > 0.8F) {
			// Nearly out: the light floods in.
			context.fill(0, 0, width, height, argb((int) ((out - 0.8F) / 0.2F * 150.0F), 255, 200, 150));
		}
	}

	/** A lens (a slit or a gash) round (cx, cy): half-width {@code hw}, half-height {@code hh}; {@code wide} lies it on its side. */
	private static void lens(DrawContext context, int cx, int cy, float hw, int hh, int color, boolean wide) {
		if (hh < 1 || hw < 0.5F) {
			return;
		}
		if (wide) {
			int w = (int) hw;
			for (int dx = -w; dx <= w; dx += 2) {
				float k = 1.0F - (dx / hw) * (dx / hw);
				int h = (int) (hh * Math.sqrt(Math.max(0.0F, k)));
				if (h > 0) {
					context.fill(cx + dx, cy - h, cx + dx + 2, cy + h, color);
				}
			}
			return;
		}
		for (int dy = -hh; dy <= hh; dy += 2) {
			float k = 1.0F - (dy / (float) hh) * (dy / (float) hh);
			int w = (int) (hw * Math.sqrt(Math.max(0.0F, k)));
			if (w > 0) {
				context.fill(cx - w, cy + dy, cx + w, cy + dy + 2, color);
			}
		}
	}

	public static void render(DrawContext context, RenderTickCounter counter) {
		shaft(context, counter.getTickDelta(true));
		if (ClientHotel.swallowed()) {
			// (The pain bar stays in sight over the inside of the mouth.)
			MinecraftClient.getInstance().inGameHud.getBossBarHud().render(context);
		}
		ClientHotel.Line line = ClientHotel.line();
		MinecraftClient client = MinecraftClient.getInstance();
		if (line == null || client.world == null || client.options.hudHidden && false) {
			return;
		}
		float delta = counter.getTickDelta(true);
		float age = client.world.getTime() - line.start() + delta;
		float fade = MathHelper.clamp(age / 6.0F, 0.0F, 1.0F) * MathHelper.clamp((line.ticks() - age) / 12.0F, 0.0F, 1.0F);
		if (fade <= 0.0F) {
			return;
		}
		TextRenderer font = client.textRenderer;
		Text text = Text.translatable(line.key());
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		float scale = line.style() == 2 ? 2.3F : (line.style() == 1 ? 2.1F : 2.5F);
		java.util.List<net.minecraft.text.OrderedText> rows = font.wrapLines(text, (int) (width * 0.9F / scale));
		float rowHeight = 11.0F * scale;
		float top = height * 0.58F - rows.size() * rowHeight / 2.0F;
		// A dark band behind.
		int a = (int) (fade * 140.0F);
		context.fillGradient(0, (int) (top - 10.0F * scale), width, (int) (top + rows.size() * rowHeight + 6.0F * scale), a << 24, a << 24);
		boolean beg = line.style() == 1;
		for (int r = 0; r < rows.size(); r++) {
			net.minecraft.text.OrderedText row = rows.get(r);
			int w = font.getWidth(row);
			for (int copy = 0; copy < 5; copy++) {
				float phase = age * (0.31F + 0.07F * copy) + copy * 1.7F + r;
				float dx = MathHelper.sin(phase) * (beg ? 3.0F : 1.8F) * (copy == 4 ? 0.0F : 1.0F);
				float dy = MathHelper.cos(phase * 0.77F) * 1.5F * (copy == 4 ? 0.0F : 1.0F);
				float alpha = copy == 4 ? fade : fade * 0.32F;
				int color = ((int) (alpha * 255.0F) << 24) | (beg ? 0xFF9C9C : 0xF2E8DC);
				context.getMatrices().push();
				context.getMatrices().translate(width / 2.0F + dx, top + r * rowHeight + dy, 0.0F);
				context.getMatrices().scale(scale, scale, 1.0F);
				context.drawText(font, row, -w / 2, 0, color, copy == 4);
				context.getMatrices().pop();
			}
		}
	}
}
