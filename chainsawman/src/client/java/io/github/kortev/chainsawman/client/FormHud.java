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

/**
 * What goes over the screen in your own transformation: the blood on the lens from the burst ({@link FormLens}); and
 * ({@code csm_impact}) at the roar, the anime's impact frames, the picture cut to flat red, black and white and flipped
 * to its negative every tick, and the edges of the screen washed red at the burst and flooded red at the roar, fading.
 */
public final class FormHud {
	public static ShaderProgram impact;
	private static final Target COPY = new Target(false, false);

	private FormHud() {
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		float tickDelta = counter.getTickDelta(true);
		FormLens.render(ctx, tickDelta);
		int style = FormCinematic.impactStyle(tickDelta);
		float vignette = FormCinematic.vignette(tickDelta);
		if (impact == null || client.player == null || (style == 0 && vignette < 0.01F)) {
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
		Post.quad(impact);
		RenderSystem.setShaderTexture(0, 0);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
	}
}
