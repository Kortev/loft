package io.github.kortev.shootingstar.test.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.test.TomatoCinematic;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Tomato Devil's horror short: a ground fog, as near as each shot wants it (the sky and the moon are left clear). */
@Mixin(value = BackgroundRenderer.class, priority = 1500)
public abstract class CinematicFogMixin {
	@Inject(method = "applyFog", at = @At("RETURN"))
	private static void shootingstarTest$cinematicFog(Camera camera, BackgroundRenderer.FogType type, float viewDistance, boolean thick, float tickDelta,
			CallbackInfo ci) {
		if (TomatoCinematic.on() && TomatoCinematic.fogEnd > 0.0F && type == BackgroundRenderer.FogType.FOG_TERRAIN) {
			RenderSystem.setShaderFogStart(TomatoCinematic.fogStart);
			RenderSystem.setShaderFogEnd(TomatoCinematic.fogEnd);
			RenderSystem.setShaderFogShape(FogShape.SPHERE);
		}
	}
}
