package io.github.kortev.gojolander.client;

import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.util.math.MathHelper;

/**
 * What goes over the screen in a Domain: for whoever it caught, the flood ({@code gl_flood}), everything there is
 * pouring in at once; and for everyone in it, its name, as it is cast.
 */
public final class DomainHud {
	private static final Target COPY = new Target(false, false);
	/** When the name comes and goes: the small line with the sign, the great one as it closes. */
	private static final double LINE_IN = 6.0;
	private static final double NAME_IN = DomainTimeline.CLOSE - 2.0;
	private static final double NAME_OUT = DomainTimeline.VOID + 36.0;

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
			if (d.holdsMe && t >= DomainTimeline.CLOSE && t < d.collapse() + 6) {
				flood(ctx, client, d, t);
			}
			boolean inside = d.mine || d.holdsMe || d.contains(client.gameRenderer.getCamera().getPos());
			if (inside && t < NAME_OUT) {
				name(ctx, client.textRenderer, t);
			}
		}
	}

	/** How hard the flood pours in at {@code t}: all at once as it closes, then never letting up, gone as it shatters. */
	static float strength(ClientDomain d, double t) {
		double in = MathHelper.clamp((t - DomainTimeline.CLOSE) / 4.0, 0.0, 1.0);
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

	/** Its name, below the middle of the picture: DOMAIN EXPANSION as the fingers cross, UNLIMITED VOID as it closes. */
	private static void name(DrawContext ctx, TextRenderer font, double t) {
		float w = ctx.getScaledWindowWidth();
		float h = ctx.getScaledWindowHeight();
		float out = (float) (1.0 - ease((t - (NAME_OUT - 10.0)) / 10.0));
		float line = (float) ease((t - LINE_IN) / 8.0) * out;
		float name = (float) ease((t - NAME_IN) / 6.0) * out;
		float y = h * 0.6F;
		if (line > 0.02F) {
			text(ctx, font, Text.translatable("hud.gojolander.domain.expansion").getString(), w / 2, y, 1.0F, 0xB8E4FF, line, 3);
		}
		if (name > 0.02F) {
			float grow = 2.2F + 0.1F * (float) ease((t - NAME_IN) / 40.0);
			text(ctx, font, Text.translatable("hud.gojolander.domain.void").getString(), w / 2, y + 12, grow, 0xFFFFFF, name, 2);
			text(ctx, font, Text.translatable("hud.gojolander.domain.void.kanji").getString(), w / 2, y + 12 + 9 * grow + 5, 1.8F,
					0x9ECFFF, name * 0.85F, 3);
		}
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
