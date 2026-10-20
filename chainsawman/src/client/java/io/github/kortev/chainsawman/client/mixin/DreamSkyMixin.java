package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.MergeCinematic;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the merge's dream there's no sky and no clouds: only the black. */
@Mixin(WorldRenderer.class)
public abstract class DreamSkyMixin {
	@Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
	private void chainsawman$noSky(Matrix4f matrix, Matrix4f projection, float tickDelta, Camera camera, boolean thickFog, Runnable fog,
			CallbackInfo ci) {
		if (MergeCinematic.dream(tickDelta) > 0.4F) {
			ci.cancel();
		}
	}

	@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
	private void chainsawman$noClouds(MatrixStack matrices, Matrix4f matrix, Matrix4f projection, float tickDelta, double x, double y, double z,
			CallbackInfo ci) {
		if (MergeCinematic.dream(tickDelta) > 0.4F) {
			ci.cancel();
		}
	}
}
