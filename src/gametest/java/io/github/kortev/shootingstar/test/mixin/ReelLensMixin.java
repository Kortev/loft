package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.shootingstar.test.ReelSelfTest;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Each of the reel's own cuts through its own lens (ReelSelfTest#lens), over whatever else the game's view would be. */
@Mixin(value = GameRenderer.class, priority = 1500)
public abstract class ReelLensMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void shootingstarTest$lens(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
		float lens = ReelSelfTest.lens();
		if (lens > 0.0F) {
			cir.setReturnValue((double) lens);
		}
	}
}
