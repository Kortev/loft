package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.camera.ScreenShake;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
	private void shootingstar$shake(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		ScreenShake.apply(matrices, tickDelta);
	}
}
