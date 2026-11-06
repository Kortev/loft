package io.github.kortev.gojolander.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.gojolander.Gojolander;
import io.github.kortev.gojolander.purple.PurpleTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * What goes over the screen as Hollow Purple is cast: the white of the collision (a flash that dies away) and a violet
 * one as it is released, and its name: HOLLOW PURPLE as the sphere forms, with 虚式「茈」 brushed under it, held until it
 * is well away. Seen by the caster, and by whoever is near enough to watch.
 */
public final class PurpleHud {
	/** The brushed name, white on nothing, 1024 by 256 (made by tools/gen_gojolander_textures.py). */
	private static final Identifier KANJI = Gojolander.id("textures/gui/hollow_purple.png");
	private static final double NAME_IN = PurpleTimeline.MERGE + 3.0;
	private static final double NAME_OUT = PurpleTimeline.RELEASE + 46.0;
	/** How near someone has to be to be shown the name and the flash. */
	private static final double NEAR = 90.0;

	private PurpleHud() {
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || ClientPurples.all().isEmpty()) {
			return;
		}
		float tickDelta = counter.getTickDelta(false);
		for (ClientPurple p : ClientPurples.all()) {
			double t = p.time(tickDelta);
			if (p.cancelled || t >= NAME_OUT + 2.0 || !(p.mine || client.gameRenderer.getCamera().getPos().distanceTo(p.eye) < NEAR)) {
				continue;
			}
			flash(ctx, t);
			if (t >= NAME_IN) {
				name(ctx, client.textRenderer, t);
			}
		}
	}

	/** The white of the orbs meeting, and the violet of its release. */
	private static void flash(DrawContext ctx, double t) {
		double merge = PurpleTimeline.MERGE;
		double release = PurpleTimeline.RELEASE;
		float white = t >= merge ? (float) (0.95 * Math.exp(-(t - merge) / 1.7)) : 0.0F;
		float violet = t >= release ? (float) (0.5 * Math.exp(-(t - release) / 2.0)) : 0.0F;
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		if (white > 0.01F) {
			ctx.fill(0, 0, w, h, (MathHelper.clamp((int) (white * 255), 0, 255) << 24) | 0xF4ECFF);
		}
		if (violet > 0.01F) {
			ctx.fill(0, 0, w, h, (MathHelper.clamp((int) (violet * 255), 0, 255) << 24) | 0xB070FF);
		}
	}

	/** Its name, a little below the middle of the picture. */
	private static void name(DrawContext ctx, TextRenderer font, double t) {
		float w = ctx.getScaledWindowWidth();
		float h = ctx.getScaledWindowHeight();
		float y = h * 0.7F;
		float a = (float) (ease((t - NAME_IN) / 6.0) * (1.0 - ease((t - (NAME_OUT - 14.0)) / 14.0)));
		if (a <= 0.02F) {
			return;
		}
		float grow = Math.min(4.4F, w * 0.9F / 100.0F) + 0.25F * (float) ease((t - NAME_IN) / 60.0);
		text(ctx, font, Text.translatable("hud.gojolander.purple").getString(), w / 2, y - 22, grow, 0xFFFFFF, a, 2);
		kanji(ctx, w / 2, y - 22 + 9 * grow + 14, Math.min(w * 0.7F, 420.0F), a);
	}

	/** The brushed name, {@code width} wide, its top centred at ({@code x}, {@code y}), with a violet glow behind it. */
	private static void kanji(DrawContext ctx, float x, float y, float width, float alpha) {
		float height = width / 4.0F;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x - width / 2.0F, y, 0.0F);
		ctx.getMatrices().scale(width / 1024.0F, height / 256.0F, 1.0F);
		RenderSystem.setShaderColor(0.6F, 0.25F, 1.0F, alpha * 0.55F);
		ctx.drawTexture(KANJI, 6, 6, 0, 0, 1024, 256, 1024, 256);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
		ctx.drawTexture(KANJI, 0, 0, 0, 0, 1024, 256, 1024, 256);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		ctx.getMatrices().pop();
	}

	/** {@code s} spread out letter by letter, centred on {@code x}, with a violet halo. */
	private static void text(DrawContext ctx, TextRenderer font, String s, float x, float y, float scale, int color, float alpha, int spacing) {
		int width = 0;
		for (int i = 0; i < s.length(); i++) {
			width += font.getWidth(String.valueOf(s.charAt(i))) + (i < s.length() - 1 ? spacing : 0);
		}
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x, y, 0.0F);
		ctx.getMatrices().scale(scale, scale, 1.0F);
		int a = MathHelper.clamp((int) (alpha * 255), 0, 255);
		int cx = -width / 2;
		for (int i = 0; i < s.length(); i++) {
			String c = String.valueOf(s.charAt(i));
			for (int[] o : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {1, 1}}) {
				ctx.drawText(font, c, cx + o[0], o[1], (a * 3 / 4 << 24) | 0x5A1FD0, false);
			}
			ctx.drawText(font, c, cx, 0, (a << 24) | color, false);
			cx += font.getWidth(c) + spacing;
		}
		ctx.getMatrices().pop();
	}

	private static double ease(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}
}
