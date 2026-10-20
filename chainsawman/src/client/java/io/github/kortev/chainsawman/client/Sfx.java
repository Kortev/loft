package io.github.kortev.chainsawman.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.chainsawman.ChainsawMan;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;

/**
 * The fight's sounds lettered over your picture as the manga letters them ({@code form_sfx.png}, painted by
 * {@code gen_chainsaw_textures.py}'s {@code sfx}): a kill ブシャッ, the overhead chop landing ザシュッ, grinding into
 * something ギャリギャリ; your heart starting in the merge ドクン. Each slammed in big and tilted, shaking, gone in a moment;
 * never over the middle.
 */
public final class Sfx {
	private static final Identifier ATLAS = ChainsawMan.id("textures/gui/form_sfx.png");
	private static final int GLYPHS = 11;
	/** The glyphs' cells in the atlas. */
	private static final int BU = 0;
	private static final int SHI = 1;
	private static final int YA = 2;
	private static final int TSU = 3;
	private static final int ZA = 4;
	private static final int YU = 5;
	private static final int GI = 6;
	private static final int RI = 7;
	private static final int DO = 8;
	private static final int KU = 9;
	private static final int N = 10;
	/** The words. */
	public static final int[] KILL = {BU, SHI, YA, TSU};
	public static final int[] CHOP = {ZA, SHI, YU, TSU};
	public static final int[] GRIND = {GI, YA, RI, GI, YA, RI};
	/** Your heart starting, in the merge. */
	public static final int[] HEARTBEAT = {DO, KU, N};
	private static final Random RANDOM = Random.create();

	/** A word on screen: its glyphs, when, where (shares of the screen), each glyph's size (a share of its height), its tilt. */
	private record Word(int[] glyphs, long at, float x, float y, float size, float tilt, int lasts) {
	}

	private static final List<Word> ON = new CopyOnWriteArrayList<>();
	private static long lastGrind = Long.MIN_VALUE / 2;

	private Sfx() {
	}

	/** {@code word} slammed onto the screen now, off to one side of the middle, for {@code lasts} ticks. */
	static void show(int[] word, float size, int lasts, long now) {
		// One sound to a moment: a kill's takes the place of the hit's that came with it.
		ON.removeIf(w -> now - w.at() <= 1);
		float side = RANDOM.nextBoolean() ? 1.0F : -1.0F;
		float x = 0.5F + side * (0.17F + RANDOM.nextFloat() * 0.08F);
		float y = 0.22F + RANDOM.nextFloat() * 0.18F;
		ON.add(new Word(word, now, x, y, size, side * (4.0F + RANDOM.nextFloat() * 8.0F), lasts));
	}

	/** Grinding into something: its sound now and then, not every bite. */
	static void grinding(long now) {
		if (now - lastGrind >= 14) {
			lastGrind = now;
			show(GRIND, 0.075F, 10, now);
		}
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ON.isEmpty() || client.player == null || client.options.hudHidden) {
			return;
		}
		long now = client.player.getWorld().getTime();
		float tickDelta = counter.getTickDelta(true);
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		RenderSystem.enableBlend();
		for (Word word : ON) {
			float t = now - word.at() + tickDelta;
			if (t > word.lasts()) {
				ON.remove(word);
				continue;
			}
			float slam = 1.0F + 0.6F * (float) Math.exp(-t / 1.2F);
			float fade = MathHelper.clamp((word.lasts() - t) / 3.0F, 0.0F, 1.0F);
			float size = h * word.size() * slam;
			int tick = (int) (now - word.at());
			// Laid along a line, centred on the word's place; the small kana smaller and low.
			float width = 0.0F;
			for (int glyph : word.glyphs()) {
				width += size * (small(glyph) ? 0.62F : 0.88F);
			}
			ctx.getMatrices().push();
			ctx.getMatrices().translate(w * word.x() + ((tick * 31) % 5 - 2) * 0.7F, h * word.y() + ((tick * 17) % 5 - 2) * 0.7F, 0.0F);
			ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(word.tilt()));
			ctx.setShaderColor(1.0F, 1.0F, 1.0F, fade);
			float x = -width / 2.0F;
			for (int glyph : word.glyphs()) {
				float s = small(glyph) ? size * 0.72F : size;
				ctx.getMatrices().push();
				ctx.getMatrices().translate(x + s / 2.0F, size / 2.0F - s / 2.0F, 0.0F);
				ctx.getMatrices().scale(s / 20.0F, s / 20.0F, 1.0F);
				ctx.drawTexture(ATLAS, -10, -10, 20, 20, glyph * 20.0F, 0.0F, 20, 20, GLYPHS * 20, 20);
				ctx.getMatrices().pop();
				x += size * (small(glyph) ? 0.62F : 0.88F);
			}
			ctx.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
			ctx.getMatrices().pop();
		}
		RenderSystem.disableBlend();
	}

	private static boolean small(int glyph) {
		return glyph == YA || glyph == TSU || glyph == YU;
	}

	static void clear() {
		ON.clear();
	}
}
