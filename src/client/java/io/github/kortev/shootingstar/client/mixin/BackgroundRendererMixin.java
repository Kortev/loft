package io.github.kortev.shootingstar.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.camera.AerialHaze;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The rise's aerial haze and cloud deck (see {@link AerialHaze}). */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
	@Shadow
	private static float red;

	@Shadow
	private static float green;

	@Shadow
	private static float blue;

	@Inject(method = "render", at = @At("TAIL"))
	private static void shootingstar$mistColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance,
			float skyDarkness, CallbackInfo ci) {
		AerialHaze.State haze = AerialHaze.get(tickDelta);
		if (haze == null || haze.mist() <= 0.0F) {
			return;
		}
		red = MathHelper.lerp(haze.mist(), red, haze.r());
		green = MathHelper.lerp(haze.mist(), green, haze.g());
		blue = MathHelper.lerp(haze.mist(), blue, haze.b());
		RenderSystem.clearColor(red, green, blue, 0.0F);
	}

	@Inject(method = "applyFog", at = @At("TAIL"))
	private static void shootingstar$haze(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
			boolean thickFog, float tickDelta, CallbackInfo ci) {
		AerialHaze.State haze = AerialHaze.get(tickDelta);
		if (haze == null) {
			return;
		}
		if (fogType == BackgroundRenderer.FogType.FOG_TERRAIN) {
			RenderSystem.setShaderFogStart(Math.min(RenderSystem.getShaderFogStart(), haze.start()));
			RenderSystem.setShaderFogEnd(Math.min(RenderSystem.getShaderFogEnd(), haze.end()));
		} else if (haze.mist() > 0.0F) {
			// Inside the deck the sky goes too.
			RenderSystem.setShaderFogStart(MathHelper.lerp(haze.mist(), RenderSystem.getShaderFogStart(), 0.0F));
			RenderSystem.setShaderFogEnd(MathHelper.lerp(haze.mist(), RenderSystem.getShaderFogEnd(), 2.0F));
		}
	}
}
