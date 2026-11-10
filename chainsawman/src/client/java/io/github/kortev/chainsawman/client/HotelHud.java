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

	/**
	 * The shaft's moments on the screen: a short red flash as the shaft arrives (never black), the pain bar flaring at each cut that lands (so
	 * every cut shows on it, though in the arena each moves it only a little), and the light flaring as it can bear no more.
	 */
	private static void shaft(DrawContext context, float delta) {
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		float flash = ClientHotel.flash(delta);
		if (flash > 0.0F) {
			context.fill(0, 0, width, height, argb((int) (flash * 120.0F), 200, 40, 24));
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			return;
		}
		io.github.kortev.chainsawman.hotel.EternityDevilEntity devil = null;
		for (net.minecraft.entity.Entity e : client.world.getEntities()) {
			if (e instanceof io.github.kortev.chainsawman.hotel.EternityDevilEntity d && d.shaft() && d.isAlive()) {
				devil = d;
			}
		}
		if (devil == null) {
			return;
		}
		float cut = MathHelper.clamp(1.0F - ClientHotel.sinceCut(delta) / 7.0F, 0.0F, 1.0F);
		if (cut > 0.0F && !client.options.hudHidden) {
			// The boss bar's place (the game's own: 182 wide, 12 down): it flares, and the edge where the pain has reached burns white.
			int x0 = width / 2 - 91;
			int y = 12;
			int edge = x0 + (int) (182 * devil.pain());
			context.fill(x0 - 2, y - 2, x0 + 184, y + 7, argb((int) (cut * 110.0F), 255, 120, 60));
			context.fill(Math.max(x0, edge - 6), y - 3, Math.min(x0 + 182, edge + 2), y + 8, argb((int) (cut * 255.0F), 255, 240, 210));
		}
		float beg = ClientHotel.sinceBeg(delta);
		if (beg >= 0.0F && beg < 50.0F) {
			// Full pain: the light flares, warm, and fades (the room convulses: ArenaRenderer).
			float k = beg < 6.0F ? beg / 6.0F : 1.0F - (beg - 6.0F) / 44.0F;
			context.fill(0, 0, width, height, argb((int) (k * 150.0F), 255, 190, 120));
		}
	}

	private static int argb(int a, int r, int g, int b) {
		return MathHelper.clamp(a, 0, 255) << 24 | MathHelper.clamp(r, 0, 255) << 16 | MathHelper.clamp(g, 0, 255) << 8 | MathHelper.clamp(b, 0, 255);
	}

	public static void render(DrawContext context, RenderTickCounter counter) {
		shaft(context, counter.getTickDelta(true));
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
