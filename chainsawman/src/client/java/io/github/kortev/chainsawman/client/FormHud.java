package io.github.kortev.chainsawman.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.gfx.Post;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.gfx.Target;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * What goes over the screen in your own transformation ({@code csm_impact}): at the roar, the anime's impact frames, the
 * picture cut to flat red, black and white and flipped to its negative every tick; and the edges of the screen washed
 * red at the burst and flooded red at the roar, fading.
 */
public final class FormHud {
	public static ShaderProgram impact;
	private static final Target COPY = new Target(false, false);
	/** The roar lettered as the manga would: ヴ, ゥ, ン, each a 20 by 20 cell of the texture. */
	private static final Identifier ROAR = io.github.kortev.chainsawman.ChainsawMan.id("textures/gui/form_roar.png");
	/**
	 * Each letter: when it slams in after the roar (ticks), across the screen and down it (shares), its size (a share of
	 * the screen's height), its tilt. Round the head, not over it: the face is the shot.
	 */
	private static final float[][] LETTERS = {{0.0F, 0.23F, 0.3F, 0.30F, -9.0F}, {1.0F, 0.33F, 0.52F, 0.2F, 6.0F}, {2.0F, 0.77F, 0.32F, 0.30F, -4.0F}};
	/** How long the letters stay (ticks from the roar). */
	private static final float LETTERED = 16.0F;

	private FormHud() {
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		float tickDelta = counter.getTickDelta(true);
		int style = FormCinematic.impactStyle(tickDelta);
		float vignette = Math.max(Math.max(FormCinematic.vignette(tickDelta), ClientCombat.vignette(tickDelta)), MergeCinematic.vignette(tickDelta));
		float grey = MergeCinematic.grey(tickDelta);
		float dark = MergeCinematic.darkness(tickDelta);
		if (impact == null || client.player == null || (style == 0 && vignette < 0.01F && grey < 0.01F && dark < 0.01F)) {
			MergeCinematic.bars(ctx, tickDelta);
			lettering(ctx, tickDelta);
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
		Shaders.set(impact, "ScreenSize", (float) w, (float) h);
		Shaders.set(impact, "Style", (float) style);
		Shaders.set(impact, "Vignette", vignette);
		Shaders.set(impact, "Grey", grey);
		Shaders.set(impact, "Dark", dark);
		Post.quad(impact);
		RenderSystem.setShaderTexture(0, 0);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		// The film's black bars, over all of it.
		MergeCinematic.bars(ctx, tickDelta);
		lettering(ctx, tickDelta);
	}

	/**
	 * The engine's roar in manga letters over the impact frames of your own transformation: each slammed in a tick
	 * after the last, big and tilted, shaking with the engine, then gone.
	 */
	private static void lettering(DrawContext ctx, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		float t = ClientForms.pullAge(client.player, tickDelta) - io.github.kortev.chainsawman.form.FormTimeline.ROAR;
		if (t < 0.0F || t > LETTERED) {
			return;
		}
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		float fade = MathHelper.clamp((LETTERED - t) / 3.0F, 0.0F, 1.0F);
		int tick = (int) (t + io.github.kortev.chainsawman.form.FormTimeline.ROAR);
		for (int i = 0; i < LETTERS.length; i++) {
			float[] l = LETTERS[i];
			float in = t - l[0];
			if (in < 0.0F) {
				continue;
			}
			float slam = 1.0F + 0.7F * (float) Math.exp(-in / 1.0F);
			float size = h * l[3];
			// The engine's shake: a pixel or two, a new way each tick.
			float sx = ((tick * 31 + i * 17) % 5 - 2) * 0.8F;
			float sy = ((tick * 23 + i * 11) % 5 - 2) * 0.8F;
			ctx.getMatrices().push();
			ctx.getMatrices().translate(w * l[1] + sx, h * l[2] + sy, 0.0F);
			ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(l[4]));
			ctx.getMatrices().scale(slam * size / 20.0F, slam * size / 20.0F, 1.0F);
			RenderSystem.enableBlend();
			ctx.setShaderColor(1.0F, 1.0F, 1.0F, fade);
			ctx.drawTexture(ROAR, -10, -10, 20, 20, i * 20.0F, 0.0F, 20, 20, 64, 32);
			ctx.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
			ctx.getMatrices().pop();
		}
	}
}
