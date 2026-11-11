package io.github.kortev.chainsawman.client.safety;

import io.github.kortev.chainsawman.safety.Safety;
import io.github.kortev.chainsawman.safety.SafetyPayloads;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * The payday on screen: while the phone rings and the voice murmurs, a black slip "Reporting to Public Safety..."; then (with the cash) what
 * was sold line by line and the yen counting up over {@link #COUNT} ticks, a click at each step, and it stays a while and goes.
 */
final class PaydayHud {
	/** When the call is answered and paid (ticks after it starts: the cash in the ring sound's wake). */
	static final int PAID = 54;
	static final int COUNT = 40;
	static final int STAY = 70;
	static final int FADE = 12;

	private static SafetyPayloads.Payday payday;
	private static int age = -1;
	private static long shown;

	private PaydayHud() {
	}

	static void start(SafetyPayloads.Payday p) {
		payday = p;
		age = 0;
		shown = p.from();
	}

	static void tick(MinecraftClient c) {
		if (payday == null) {
			return;
		}
		age++;
		if (age == PAID) {
			c.getSoundManager().play(PositionedSoundInstance.master(Safety.Sounds.CASH, 1.0F, 1.0F));
		}
		if (age > PAID && age <= PAID + COUNT) {
			long before = shown;
			shown = value(age - PAID);
			if (shown != before && (age - PAID) % 3 == 0) {
				float rise = (age - PAID) / (float) COUNT;
				c.getSoundManager().play(PositionedSoundInstance.master(Safety.Sounds.TICK, 0.9F + rise * 0.6F, 0.45F));
			}
		}
		if (age > PAID + COUNT + STAY + FADE) {
			payday = null;
			age = -1;
		}
	}

	/** The yen shown {@code t} ticks into the count: fast at first, settling onto the sum. */
	private static long value(int t) {
		float k = MathHelper.clamp(t / (float) COUNT, 0.0F, 1.0F);
		float e = 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k);
		return payday.from() + Math.round((payday.to() - payday.from()) * (double) e);
	}

	static void render(DrawContext context, RenderTickCounter counter) {
		if (payday == null) {
			return;
		}
		MinecraftClient c = MinecraftClient.getInstance();
		if (c.options.hudHidden) {
			return;
		}
		TextRenderer tr = c.textRenderer;
		float alpha = 1.0F;
		int end = PAID + COUNT + STAY;
		if (age > end) {
			alpha = 1.0F - (age - end) / (float) FADE;
		}
		alpha *= Math.min(1.0F, (age + 1) / 4.0F);
		if (alpha <= 0.02F) {
			return;
		}
		int a = (int) (alpha * 255.0F) << 24;
		boolean paid = age >= PAID;
		int w = 210;
		int lines = paid ? payday.lines().size() + (payday.bonus() > 0 ? 1 : 0) : 1;
		int h = paid ? 30 + lines * 11 + 40 : 42;
		int x = (context.getScaledWindowWidth() - w) / 2;
		int y = context.getScaledWindowHeight() / 5;
		context.fill(x - 1, y - 1, x + w + 1, y + h + 1, (a & 0xFF000000) | 0xDADADA);
		context.fill(x, y, x + w, y + h, (a & 0xFF000000) | 0x101010);
		context.drawText(tr, Text.translatable("safety.chainsawman.licence.header"), x + 8, y + 7, a | 0xF2F2F2, false);
		context.fill(x + 8, y + 19, x + w - 8, y + 21, a | 0xD0201A);
		if (!paid) {
			String dots = ".".repeat(1 + age / 8 % 3);
			context.drawText(tr, Text.translatable("safety.chainsawman.payday.calling").append(dots), x + 8, y + 27, a | 0xBBBBBB, false);
			return;
		}
		int ly = y + 28;
		for (SafetyPayloads.Line l : payday.lines()) {
			Text left = Text.translatable("safety.chainsawman.payday.line", Text.translatable(l.key()), l.count());
			Text right = Text.literal(SafetyClient.yen(l.yen()));
			context.drawText(tr, left, x + 8, ly, a | 0xF2F2F2, false);
			context.drawText(tr, right, x + w - 8 - tr.getWidth(right), ly, a | 0xF2F2F2, false);
			ly += 11;
		}
		if (payday.bonus() > 0) {
			Text left = Text.translatable("safety.chainsawman.payday.bonus");
			Text right = Text.literal(SafetyClient.yen(payday.bonus()));
			context.drawText(tr, left, x + 8, ly, a | 0xD0D0D0, false);
			context.drawText(tr, right, x + w - 8 - tr.getWidth(right), ly, a | 0xD0D0D0, false);
			ly += 11;
		}
		// The yen, big, counting up (a pop of scale as each step lands); when it lands on the sum, a bigger pop and a red flash fading to white.
		String sum = SafetyClient.yen(shown);
		float scale = 3.0F;
		int color = 0xFFFFFF;
		int t = age - PAID;
		if (t > 0 && t <= COUNT) {
			scale += 0.15F * (float) Math.max(0.0, Math.sin(t * 1.1));
		} else if (t > COUNT && t <= COUNT + 10) {
			float k = (t - COUNT) / 10.0F;
			scale += 0.8F * (1.0F - k) * (1.0F - k);
			int g = (int) (0x20 + (0xFF - 0x20) * k);
			color = 0xFF0000 | g << 8 | g;
		}
		context.getMatrices().push();
		int sw = tr.getWidth(sum);
		context.getMatrices().translate(x + w - 8 - sw * 1.5F, ly + 6 + 12, 0);
		context.getMatrices().scale(scale, scale, 1.0F);
		context.drawText(tr, sum, -sw / 2, -4, a | color, false);
		context.getMatrices().pop();
		context.drawText(tr, Text.translatable("safety.chainsawman.payday.total"), x + 8, ly + 14, a | 0x9A9A9A, false);
	}
}
