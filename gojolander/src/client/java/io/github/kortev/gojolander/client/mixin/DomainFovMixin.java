package io.github.kortev.gojolander.client.mixin;

import io.github.kortev.gojolander.client.DomainCamera;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The caster's shot punches in on the sign as it snaps up: a zoom of the world's view (not the hand's). */
@Mixin(GameRenderer.class)
public abstract class DomainFovMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void gojolander$punchIn(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
		if (changingFov) {
			float scale = DomainCamera.fovScale(tickDelta);
			if (scale != 1.0F) {
				cir.setReturnValue(cir.getReturnValue() * scale);
			}
		}
	}
}
