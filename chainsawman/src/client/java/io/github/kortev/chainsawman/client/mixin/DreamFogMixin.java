package io.github.kortev.chainsawman.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.chainsawman.client.MergeCinematic;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the merge's dream ({@link MergeCinematic#dream}) the world goes into black fog a few blocks off: a void. */
@Mixin(BackgroundRenderer.class)
public abstract class DreamFogMixin {
	@Shadow
	private static float red;
	@Shadow
	private static float green;
	@Shadow
	private static float blue;

	@Inject(method = "render", at = @At("RETURN"))
	private static void chainsawman$black(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo ci) {
		float d = MergeCinematic.dream(tickDelta);
		if (d > 0.0F) {
			red *= 1.0F - d;
			green *= 1.0F - d;
			blue *= 1.0F - d;
			RenderSystem.clearColor(red, green, blue, 0.0F);
		}
	}

	@Inject(method = "applyFog", at = @At("RETURN"))
	private static void chainsawman$close(Camera camera, BackgroundRenderer.FogType type, float viewDistance, boolean thick, float tickDelta,
			CallbackInfo ci) {
		float d = MergeCinematic.dream(tickDelta);
		if (d > 0.0F) {
			RenderSystem.setShaderFogStart(MathHelper.lerp(d, RenderSystem.getShaderFogStart(), 2.5F));
			RenderSystem.setShaderFogEnd(MathHelper.lerp(d, RenderSystem.getShaderFogEnd(), 11.0F));
			RenderSystem.setShaderFogShape(FogShape.SPHERE);
			RenderSystem.setShaderFogColor(red, green, blue);
		}
	}
}
