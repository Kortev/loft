package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.shootingstar.test.ReelSelfTest;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No names over anyone's head in the reel (ReelSelfTest): it's a film. */
@Mixin(LivingEntityRenderer.class)
public abstract class ReelLabelMixin {
	@Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
	private void shootingstarTest$noNames(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (ReelSelfTest.active()) {
			cir.setReturnValue(false);
		}
	}
}
