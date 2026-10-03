package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.shootingstar.test.Capture;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Grabs each finished frame (world and HUD) just before it is shown. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientCaptureMixin {
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;swapBuffers()V"))
	private void shootingstarTest$grab(boolean tick, CallbackInfo ci) {
		Capture.endFrame((MinecraftClient) (Object) this);
	}
}
