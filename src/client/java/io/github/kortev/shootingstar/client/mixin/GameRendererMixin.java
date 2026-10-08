package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.camera.ScreenShake;
import io.github.kortev.shootingstar.client.thunder.ClientThunders;
import io.github.kortev.shootingstar.client.thunder.ThunderCamera;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
	private void shootingstar$shake(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		ScreenShake.apply(matrices, tickDelta);
	}

	/**
	 * While a feed covers the whole screen, the world behind it is not drawn at all: nobody can see it, and drawing it
	 * (with the storm and its light over it) as well as the feed is most of what made Mjölnir's cinematic slow.
	 */
	@Inject(method = "renderWorld", at = @At("HEAD"), cancellable = true)
	private void shootingstar$skipWorldUnderFeed(RenderTickCounter tickCounter, CallbackInfo ci) {
		if (ClientThunders.feedCovers(tickCounter.getTickDelta(false))) {
			ci.cancel();
		}
	}

	/** Mjölnir's camera shots zoom: in on the leader as it comes down, out with the stroke (the world only, not the hand). */
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void shootingstar$shotFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
		if (changingFov) {
			float scale = ThunderCamera.fovScale(tickDelta);
			if (scale != 1.0F) {
				cir.setReturnValue(cir.getReturnValue() * scale);
			}
		}
	}
}
