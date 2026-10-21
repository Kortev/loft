package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.shootingstar.test.ReelSelfTest;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The reel's merge is dreamt in an alley, walls a few blocks off, in the rain: in the dream, none of it (ReelSelfTest):
 * no blocks, no clouds below, no rain falling or heard. Nor the rain under the dumpster's shut lid.
 */
@Mixin(WorldRenderer.class)
public abstract class ReelDreamMixin {
	@Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true)
	private void shootingstarTest$noBlocks(RenderLayer layer, double x, double y, double z, Matrix4f matrix, Matrix4f projection, CallbackInfo ci) {
		if (ReelSelfTest.dreaming()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
	private void shootingstarTest$noClouds(MatrixStack matrices, Matrix4f matrix, Matrix4f projection, float tickDelta, double x, double y, double z,
			CallbackInfo ci) {
		if (ReelSelfTest.dreaming()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
	private void shootingstarTest$noRain(LightmapTextureManager lightmap, float tickDelta, double x, double y, double z, CallbackInfo ci) {
		if (ReelSelfTest.underTheLid()) {
			ci.cancel();
		}
	}

	@Inject(method = "tickRainSplashing", at = @At("HEAD"), cancellable = true)
	private void shootingstarTest$noRainHeard(Camera camera, CallbackInfo ci) {
		if (ReelSelfTest.underTheLid()) {
			ci.cancel();
		}
	}
}
