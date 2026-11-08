package io.github.kortev.chainsawman.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.chainsawman.client.ClientHotel;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On the loop floor of the Eternity Devil's hotel the corridor ends in a dark fog a few blocks off ({@link ClientHotel}): it is what hides
 * the place where the repeating corridor stops and a player is put back.
 */
@Mixin(BackgroundRenderer.class)
public abstract class HotelFogMixin {
	@Inject(method = "applyFog", at = @At("RETURN"))
	private static void chainsawman$corridor(Camera camera, BackgroundRenderer.FogType type, float viewDistance, boolean thick, float tickDelta,
			CallbackInfo ci) {
		float d = ClientHotel.fogAmount(tickDelta);
		if (d > 0.0F && type == BackgroundRenderer.FogType.FOG_TERRAIN) {
			float end = ClientHotel.fogEnd();
			RenderSystem.setShaderFogStart(MathHelper.lerp(d, RenderSystem.getShaderFogStart(), end * 0.3F));
			RenderSystem.setShaderFogEnd(MathHelper.lerp(d, RenderSystem.getShaderFogEnd(), end));
			RenderSystem.setShaderFogShape(FogShape.SPHERE);
			RenderSystem.setShaderFogColor(0.03F, 0.02F, 0.02F);
		}
	}
}
