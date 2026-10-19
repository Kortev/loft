package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.form.FormTimeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Blood on the lens in your own transformation: at the burst it splatters the screen, in the form's flat colours and at
 * a chunky pixel size to sit with the game (a red splat round a darker heart, a dark edge, a wet glint, flecks thrown
 * off it), then runs down in drips, and dissolves away pixel by pixel before the roar. Each pull's splatter is its own, made from when it was
 * pulled.
 */
public final class FormLens {
	/** The screen's width in the lens's pixels. */
	private static final int WIDE = 160;
	/** When the drips have run their length, and when the blood fades out (ticks). */
	private static final int RUN = FormTimeline.BURST + 14;
	private static final int FADE_FROM = FormTimeline.ROAR - 6;
	private static final int FADE_TO = FormTimeline.ROAR;
	/** The form's blood: its red, the darker heart, the dark edge, the glint. */
	private static final int RED = 0xBA121A;
	private static final int DARK = 0x78080E;
	private static final int EDGE = 0x3C040A;
	private static final int GLINT = 0xFFAAAA;

	/** A pixel of a splat: where, and its colour. */
	private record Cell(int x, int y, int rgb) {
	}

	/** A drip running down from a splat: its column, top, width and how far it runs (pixels), and how soon it starts. */
	private record Drip(int x, int y, int width, int length, float delay) {
	}

	private record Splatter(long pulled, int high, List<Cell> cells, List<Drip> drips) {
	}

	private static Splatter splatter;

	private FormLens() {
	}

	/** Draws the blood on the lens, if your transformation has got that far. */
	static void render(DrawContext ctx, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		float t = FormCinematic.mine(tickDelta);
		ClientForms.Pull pull = client.player != null ? ClientForms.pull(client.player) : null;
		if (pull == null || t < FormTimeline.BURST || t >= FADE_TO) {
			return;
		}
		float cell = ctx.getScaledWindowWidth() / (float) WIDE;
		int high = MathHelper.ceil(ctx.getScaledWindowHeight() / cell);
		if (splatter == null || splatter.pulled() != pull.at() || splatter.high() != high) {
			splatter = make(pull.at(), high);
		}
		// How much has dissolved: each pixel goes at its own moment.
		float gone = MathHelper.clamp((t - FADE_FROM) / (FADE_TO - FADE_FROM), 0.0F, 1.0F);
		int a = 0xFF000000;
		ctx.getMatrices().push();
		ctx.getMatrices().scale(cell, cell, 1.0F);
		for (Cell c : splatter.cells()) {
			if (kept(c.x(), c.y(), gone)) {
				ctx.fill(c.x(), c.y(), c.x() + 1, c.y() + 1, a | c.rgb());
			}
		}
		for (Drip d : splatter.drips()) {
			float k = MathHelper.clamp((t - FormTimeline.BURST - d.delay()) / (RUN - FormTimeline.BURST - d.delay()), 0.0F, 1.0F);
			// Running fast, then slowing as it thins.
			int length = Math.round(d.length() * (1.0F - (1.0F - k) * (1.0F - k)));
			if (length <= 0) {
				continue;
			}
			int bottom = d.y() + length;
			// Row by row, so it dissolves as the splats do; the bead at its tip, and a darker side to a wide one.
			for (int y = d.y(); y < bottom + 1; y++) {
				if (!kept(d.x(), y, gone)) {
					continue;
				}
				boolean bead = y >= bottom - 2;
				int left = d.x() - (bead ? 2 : 1);
				int right = d.x() + d.width() + (bead ? 2 : 1);
				ctx.fill(left, y, right, y + 1, a | EDGE);
				if (y < bottom) {
					ctx.fill(left + 1, y, right - 1, y + 1, a | RED);
					if (d.width() > 1 && !bead) {
						ctx.fill(d.x() + d.width() - 1, y, d.x() + d.width(), y + 1, a | DARK);
					}
				}
			}
		}
		ctx.getMatrices().pop();
	}

	/** Whether the lens pixel at ({@code x}, {@code y}) is still there with {@code gone} of the blood dissolved. */
	private static boolean kept(int x, int y, float gone) {
		int h = x * 73856093 ^ y * 19349663;
		h = (h ^ h >>> 13) * 1274126177;
		return ((h ^ h >>> 16) & 0xFFFF) / 65535.0F >= gone;
	}

	/** A splatter over a screen {@code high} lens pixels high: a few big splats, smaller ones, the flecks, the drips. */
	private static Splatter make(long pulled, int high) {
		Random random = Random.create(pulled * 31 + 7);
		List<Cell> cells = new ArrayList<>();
		List<Drip> drips = new ArrayList<>();
		int splats = 6 + random.nextInt(3);
		for (int i = 0; i < splats; i++) {
			// The big ones first; none over the middle of the screen, where they are.
			float r = i < 2 ? 7.0F + random.nextFloat() * 4.0F : 2.5F + random.nextFloat() * 4.0F;
			float x;
			float y;
			do {
				x = WIDE * (0.04F + random.nextFloat() * 0.92F);
				y = high * (0.05F + random.nextFloat() * 0.6F);
			} while (Math.abs(x - WIDE / 2.0F) < WIDE * 0.17F && Math.abs(y - high / 2.0F) < high * 0.3F);
			blob(cells, random, x, y, r, true);
			// Flecks thrown off it, away from the middle of the screen.
			float away = (float) Math.atan2(y - high / 2.0F, x - WIDE / 2.0F);
			int flecks = 2 + random.nextInt(4);
			for (int f = 0; f < flecks; f++) {
				float angle = away + (random.nextFloat() - 0.5F) * 1.6F;
				float d = r * (1.3F + random.nextFloat() * 1.2F);
				blob(cells, random, x + MathHelper.cos(angle) * d, y + MathHelper.sin(angle) * d, 0.8F + random.nextFloat() * 1.2F, false);
			}
			int runs = r > 5.0F ? 1 + random.nextInt(3) : random.nextInt(2);
			for (int k = 0; k < runs; k++) {
				int width = r > 6.0F && random.nextBoolean() ? 2 : 1;
				int column = Math.round(x + (random.nextFloat() - 0.5F) * r * 1.1F);
				drips.add(new Drip(column, Math.round(y + r * 0.5F), width, Math.round(r * (1.0F + random.nextFloat() * 2.2F)) + 4,
						random.nextFloat() * 3.0F));
			}
		}
		return new Splatter(pulled, high, cells, drips);
	}

	/** A splat round ({@code x}, {@code y}), about {@code r} across from the middle, its edge ragged. */
	private static void blob(List<Cell> cells, Random random, float x, float y, float r, boolean big) {
		float p1 = random.nextFloat() * MathHelper.TAU;
		float p2 = random.nextFloat() * MathHelper.TAU;
		float lobes = big ? 0.22F : 0.1F;
		int reach = MathHelper.ceil(r * 1.4F) + 1;
		for (int cy = Math.round(y) - reach; cy <= Math.round(y) + reach; cy++) {
			for (int cx = Math.round(x) - reach; cx <= Math.round(x) + reach; cx++) {
				float dx = cx + 0.5F - x;
				float dy = cy + 0.5F - y;
				float d = MathHelper.sqrt(dx * dx + dy * dy);
				float a = (float) Math.atan2(dy, dx);
				float edge = r * (1.0F + lobes * MathHelper.sin(3.0F * a + p1) + lobes * 0.6F * MathHelper.sin(5.0F * a + p2));
				if (d > edge) {
					continue;
				}
				int rgb = RED;
				if (d > edge - 1.0F && r > 1.5F) {
					rgb = EDGE;
				} else if (big && MathHelper.sqrt(dx * dx + (dy - r * 0.25F) * (dy - r * 0.25F)) < r * 0.45F) {
					// The darker heart, sagging the way it runs.
					rgb = DARK;
				}
				cells.add(new Cell(cx, cy, rgb));
			}
		}
		if (big && r > 4.0F) {
			int gx = Math.round(x - r * 0.4F);
			int gy = Math.round(y - r * 0.4F);
			cells.add(new Cell(gx, gy, GLINT));
			cells.add(new Cell(gx + 1, gy, GLINT));
			cells.add(new Cell(gx, gy + 1, GLINT));
		}
	}
}
