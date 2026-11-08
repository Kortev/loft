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

	public static void render(DrawContext context, RenderTickCounter counter) {
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
