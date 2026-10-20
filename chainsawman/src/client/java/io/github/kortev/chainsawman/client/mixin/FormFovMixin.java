package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.FormCinematic;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The roar punches in on the face: a zoom of the world's view (not the hand's). */
@Mixin(GameRenderer.class)
public abstract class FormFovMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void chainsawman$punchIn(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
		if (changingFov) {
			float scale = FormCinematic.fovScale(tickDelta) * io.github.kortev.chainsawman.client.ClientCombat.fov(tickDelta);
			if (scale != 1.0F) {
				cir.setReturnValue(cir.getReturnValue() * scale);
			}
		}
	}
}
