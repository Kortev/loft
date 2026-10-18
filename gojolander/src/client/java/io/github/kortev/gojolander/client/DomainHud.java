package io.github.kortev.gojolander.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.gojolander.Gojolander;
import io.github.kortev.gojolander.domain.DomainTimeline;
import io.github.kortev.shootingstar.client.gfx.Post;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.gfx.Target;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * What goes over the screen in a Domain: for whoever it caught, the flood ({@code gl_flood}), everything there is
 * pouring in at once from the moment the void opens; and for everyone in it, its name: DOMAIN EXPANSION as the sign
 * snaps up, and as the void opens, UNLIMITED VOID over its name brushed in ink, 無量空処.
 */
public final class DomainHud {
	private static final Target COPY = new Target(false, false);
	/** The brushed name, white on nothing, 1024 by 256 (made by tools/gen_gojolander_textures.py). */
	private static final Identifier KANJI = Gojolander.id("textures/gui/unlimited_void.png");
	/** When the great name comes and goes. */
	private static final double NAME_IN = DomainTimeline.VOID + 4.0;
	private static final double NAME_OUT = DomainTimeline.VOID + 70.0;

	public static ShaderProgram flood;

	private DomainHud() {
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || ClientDomains.all().isEmpty()) {
			return;
		}
		float tickDelta = counter.getTickDelta(false);
		for (ClientDomain d : ClientDomains.all()) {
			double t = d.time(tickDelta);
			if (d.holdsMe && t >= DomainTimeline.VOID && t < d.collapse() + 6) {
				flood(ctx, client, d, t);
			}
			boolean inside = d.mine || d.holdsMe || d.contains(client.gameRenderer.getCamera().getPos());
			if (inside && t < NAME_OUT && t < d.collapse()) {
				name(ctx, client.textRenderer, t);
			}
		}
	}

	/** How hard the flood pours in at {@code t}: in with the void opening, then never letting up, gone as it shatters. */
	static float strength(ClientDomain d, double t) {
		double in = MathHelper.clamp((t - DomainTimeline.VOID) / 8.0, 0.0, 1.0);
		double out = 1.0 - MathHelper.clamp((t - d.collapse()) / 6.0, 0.0, 1.0);
		double surge = 0.85 + 0.15 * Math.sin(t * 0.9) * Math.sin(t * 0.37);
		return (float) (in * out * surge);
	}

	private static void flood(DrawContext ctx, MinecraftClient client, ClientDomain d, double t) {
		if (flood == null) {
			return;
		}
		ctx.draw();
		Framebuffer main = client.getFramebuffer();
		int w = main.textureWidth;
		int h = main.textureHeight;
		COPY.ensure(w, h);
		COPY.copyColorFrom(main);
		main.beginWrite(true);
		Post.begin();
		RenderSystem.setShaderTexture(0, COPY.color());
		Shaders.set(flood, "ScreenSize", (float) w, (float) h);
		Shaders.set(flood, "Time", (float) (t / 20.0));
		Shaders.set(flood, "Strength", strength(d, t));
		Shaders.set(flood, "Seed", (float) Math.floor(t * 1.5));
		Post.quad(flood);
		RenderSystem.setShaderTexture(0, 0);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
	}

	/** Its name, a little below the middle of the picture. */
	private static void name(DrawContext ctx, TextRenderer font, double t) {
		float w = ctx.getScaledWindowWidth();
		float h = ctx.getScaledWindowHeight();
		float y = h * 0.7F;
		float line = (float) (ease((t - DomainTimeline.HAND) / 6.0) * (1.0 - ease((t - (DomainTimeline.CLOSE - 6.0)) / 6.0)));
		if (line > 0.02F) {
			text(ctx, font, Text.translatable("hud.gojolander.domain.expansion").getString(), w / 2, y, 1.4F, 0xB8E4FF, line, 3);
		}
		float name = (float) (ease((t - NAME_IN) / 8.0) * (1.0 - ease((t - (NAME_OUT - 14.0)) / 14.0)));
		if (name > 0.02F) {
			float grow = 2.2F + 0.12F * (float) ease((t - NAME_IN) / 60.0);
			text(ctx, font, Text.translatable("hud.gojolander.domain.void").getString(), w / 2, y - 22, grow, 0xFFFFFF, name, 2);
			kanji(ctx, w / 2, y - 22 + 9 * grow + 12, Math.min(w * 0.42F, 220.0F), name);
		}
	}

	/** The brushed name, {@code width} wide, its top centred at ({@code x}, {@code y}), with a cold glow behind it. */
	private static void kanji(DrawContext ctx, float x, float y, float width, float alpha) {
		float height = width / 4.0F;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x - width / 2.0F, y, 0.0F);
		ctx.getMatrices().scale(width / 1024.0F, height / 256.0F, 1.0F);
		RenderSystem.setShaderColor(0.35F, 0.6F, 1.0F, alpha * 0.45F);
		ctx.drawTexture(KANJI, 6, 6, 0, 0, 1024, 256, 1024, 256);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
		ctx.drawTexture(KANJI, 0, 0, 0, 0, 1024, 256, 1024, 256);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		ctx.getMatrices().pop();
	}

	/** {@code s} spread out letter by letter, centred on {@code x}, with a faint blue halo. */
	private static void text(DrawContext ctx, TextRenderer font, String s, float x, float y, float scale, int color, float alpha,
			int spacing) {
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
			ctx.drawText(font, c, cx + 1, 1, (a / 3 << 24) | 0x3A7BFF, false);
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
