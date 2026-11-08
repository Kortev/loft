package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.network.HotelPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * The Eternity Devil's contract, in the show's way: a sheet of old paper with ink on it, the devil's name, what it gives in one line, the
 * price, a red seal; two big boxes, ACCEPT and REFUSE, and a bar of the seconds left to answer. Nothing else on the screen answers for
 * you: the way out of the loop is one of the two. (It does not pause the game: the devil is waiting in the walls.)
 */
public class ContractScreen extends Screen {
	private static final int INK = 0xFF1A1210;
	private static final int PAPER = 0xFFE8DDC4;
	private static final int PAPER_DARK = 0xFFD2C4A4;
	private static final int RED = 0xFF9A1420;

	private final int total;
	private int left;
	/** The two boxes, as laid out the last time it was drawn: x, y, width, height. */
	private final int[] accept = new int[4];
	private final int[] refuse = new int[4];
	private int hover = -1;
	/** For films only: draw a pointer where the (scripted) mouse is, since a capture has no cursor of its own. */
	public static boolean showCursor;
	private double curX = -1;
	private double curY = -1;

	public ContractScreen(int ticks) {
		super(Text.translatable("contract.chainsawman.screen.title"));
		this.total = ticks;
		this.left = ticks;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void tick() {
		if (left > 0) {
			left--;
		}
	}

	/** The screen's own answer: sends it to the server and closes. */
	public void answer(boolean take) {
		if (ClientPlayNetworking.canSend(HotelPayloads.Answer.ID)) {
			ClientPlayNetworking.send(new HotelPayloads.Answer(take));
		}
		MinecraftClient.getInstance().setScreen(null);
	}

	/** Whether {@code (x, y)} is on the ACCEPT (1), the REFUSE (2) or neither (0) (as last drawn). */
	public int boxAt(double x, double y) {
		if (in(accept, x, y)) {
			return 1;
		}
		return in(refuse, x, y) ? 2 : 0;
	}

	/** The middle of the ACCEPT box, and the REFUSE, as last drawn (for a test to click on). */
	public double[] centre(boolean take) {
		int[] b = take ? accept : refuse;
		return new double[] {b[0] + b[2] / 2.0, b[1] + b[3] / 2.0};
	}

	private static boolean in(int[] b, double x, double y) {
		return x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3];
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int hit = boxAt(mouseX, mouseY);
			if (hit != 0) {
				answer(hit == 1);
				return true;
			}
		}
		return true;
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		hover = boxAt(mouseX, mouseY) - 1;
		curX = mouseX;
		curY = mouseY;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xC0000000);
		TextRenderer font = textRenderer;
		// The sheet: as wide as the screen allows, tall enough for what is on it.
		int pw = Math.min(width - 16, 320);
		float u = pw / 320.0F;
		int ph = Math.min(height - 12, (int) (292 * u));
		int px = (width - pw) / 2;
		int py = (height - ph) / 2;
		context.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, INK);
		context.fill(px, py, px + pw, py + ph, PAPER);
		// Age: a darker edge and a few stains.
		context.fill(px, py, px + pw, py + 3, PAPER_DARK);
		context.fill(px, py + ph - 3, px + pw, py + ph, PAPER_DARK);
		context.fill(px, py, px + 3, py + ph, PAPER_DARK);
		context.fill(px + pw - 3, py, px + pw, py + ph, PAPER_DARK);
		context.fill(px + (int) (pw * 0.62F), py + (int) (ph * 0.46F), px + (int) (pw * 0.62F) + 4, py + (int) (ph * 0.46F) + 3, PAPER_DARK);
		context.fill(px + (int) (pw * 0.18F), py + (int) (ph * 0.7F), px + (int) (pw * 0.18F) + 3, py + (int) (ph * 0.7F) + 3, PAPER_DARK);
		// A red double rule inside the edge.
		int m = (int) (8 * u) + 2;
		rule(context, px + m, py + m, pw - 2 * m, ph - 2 * m, RED);
		int cx = px + pw / 2;
		float s = Math.max(1.0F, u);
		int y = py + m + (int) (14 * s);
		// The title, and the seal.
		centred(context, font, Text.translatable("contract.chainsawman.screen.title"), cx - (int) (14 * s), y, 3.0F * s, INK);
		seal(context, px + pw - m - (int) (40 * s), y - (int) (6 * s), (int) (30 * s));
		y += (int) (34 * s);
		context.fill(px + m + 10, y, px + pw - m - 10, y + 1, INK);
		y += (int) (12 * s);
		centred(context, font, Text.translatable("contract.chainsawman.screen.devil"), cx, y, 2.0F * s, RED);
		y += (int) (30 * s);
		centred(context, font, Text.translatable("contract.chainsawman.screen.offer"), cx, y, 1.15F * s, INK);
		y += (int) (22 * s);
		centred(context, font, Text.translatable("contract.chainsawman.screen.power"), cx, y, 1.15F * s, INK);
		y += (int) (22 * s);
		centred(context, font, Text.translatable("contract.chainsawman.screen.price"), cx, y, 1.15F * s, RED);
		// The seconds left.
		int barY = py + ph - m - (int) (84 * s);
		int bw = pw - 2 * m - 20;
		context.fill(px + m + 10, barY, px + m + 10 + bw, barY + (int) (5 * s), 0xFF8A7A5C);
		float rest = MathHelper.clamp((left - delta) / (float) total, 0.0F, 1.0F);
		context.fill(px + m + 10, barY, px + m + 10 + (int) (bw * rest), barY + (int) (5 * s), RED);
		centred(context, font, Text.translatable("contract.chainsawman.screen.time", Math.max(0, (left + 19) / 20)), cx, barY + (int) (9 * s), 1.0F * s, INK);
		// The two boxes.
		int bh = (int) (34 * s);
		int by = py + ph - m - bh - (int) (12 * s);
		int gap = (int) (10 * s);
		int bwid = (pw - 2 * m - 20 - gap) / 2;
		box(accept, px + m + 10, by, bwid, bh);
		box(refuse, px + m + 10 + bwid + gap, by, bwid, bh);
		context.fill(accept[0], accept[1], accept[0] + accept[2], accept[1] + accept[3], hover == 0 ? 0xFFB8202E : RED);
		context.fill(refuse[0], refuse[1], refuse[0] + refuse[2], refuse[1] + refuse[3], hover == 1 ? 0xFF3A2A28 : INK);
		outline(context, accept, 0xFF2A0A0E);
		outline(context, refuse, 0xFF000000);
		centred(context, font, Text.translatable("contract.chainsawman.screen.accept"), accept[0] + accept[2] / 2, accept[1] + accept[3] / 2 - (int) (5 * s), 1.6F * s, 0xFFF4EBDD);
		centred(context, font, Text.translatable("contract.chainsawman.screen.refuse"), refuse[0] + refuse[2] / 2, refuse[1] + refuse[3] / 2 - (int) (5 * s), 1.6F * s, 0xFFF4EBDD);
		if (showCursor && curX >= 0.0) {
			int cx0 = (int) curX;
			int cy0 = (int) curY;
			for (int i = 0; i < 12; i++) {
				int w = i / 2 + 2;
				context.fill(cx0 - 1, cy0 + i - 1, cx0 + w + 1, cy0 + i + 1, 0xFF000000);
			}
			for (int i = 0; i < 12; i++) {
				context.fill(cx0, cy0 + i, cx0 + i / 2 + 1, cy0 + i + 1, 0xFFFFFFFF);
			}
		}
	}

	private static void box(int[] b, int x, int y, int w, int h) {
		b[0] = x;
		b[1] = y;
		b[2] = w;
		b[3] = h;
	}

	private static void outline(DrawContext c, int[] b, int colour) {
		c.fill(b[0] - 2, b[1] - 2, b[0] + b[2] + 2, b[1], colour);
		c.fill(b[0] - 2, b[1] + b[3], b[0] + b[2] + 2, b[1] + b[3] + 2, colour);
		c.fill(b[0] - 2, b[1], b[0], b[1] + b[3], colour);
		c.fill(b[0] + b[2], b[1], b[0] + b[2] + 2, b[1] + b[3], colour);
	}

	private static void rule(DrawContext c, int x, int y, int w, int h, int colour) {
		c.fill(x, y, x + w, y + 1, colour);
		c.fill(x, y + h - 1, x + w, y + h, colour);
		c.fill(x, y, x + 1, y + h, colour);
		c.fill(x + w - 1, y, x + w, y + h, colour);
		c.fill(x + 3, y + 3, x + w - 3, y + 4, colour);
		c.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, colour);
		c.fill(x + 3, y + 3, x + 4, y + h - 3, colour);
		c.fill(x + w - 4, y + 3, x + w - 3, y + h - 3, colour);
	}

	/** A round red seal with a mark cut into it. */
	private void seal(DrawContext c, int x, int y, int size) {
		int r = size / 2;
		for (int dy = -r; dy <= r; dy++) {
			int half = (int) Math.sqrt((double) r * r - (double) dy * dy);
			c.fill(x + r - half, y + r + dy, x + r + half, y + r + dy + 1, RED);
		}
		// An 8 on its side (endless): two rings cut out of the red.
		int k = Math.max(2, size / 9);
		int ring = Math.max(3, size / 4);
		for (int side = -1; side <= 1; side += 2) {
			int rx = x + r + side * (ring - k);
			int ry = y + r;
			for (int dy = -ring; dy <= ring; dy++) {
				int outer = (int) Math.sqrt((double) ring * ring - (double) dy * dy);
				int inner = ring - k > Math.abs(dy) ? (int) Math.sqrt((double) (ring - k) * (ring - k) - (double) dy * dy) : -1;
				if (inner < 0) {
					c.fill(rx - outer, ry + dy, rx + outer, ry + dy + 1, PAPER);
				} else {
					c.fill(rx - outer, ry + dy, rx - inner, ry + dy + 1, PAPER);
					c.fill(rx + inner, ry + dy, rx + outer, ry + dy + 1, PAPER);
				}
			}
		}
	}

	private static void centred(DrawContext context, TextRenderer font, Text text, int cx, int y, float scale, int colour) {
		int w = font.getWidth(text);
		float shown = scale;
		context.getMatrices().push();
		context.getMatrices().translate(cx, y, 0.0F);
		context.getMatrices().scale(shown, shown, 1.0F);
		context.drawText(font, text, -w / 2, 0, colour, false);
		context.getMatrices().pop();
	}
}
