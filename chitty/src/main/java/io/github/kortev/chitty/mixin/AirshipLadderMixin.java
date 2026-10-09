package io.github.kortev.chitty.mixin;

import io.github.kortev.chitty.airship.AirshipEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An airship's rope ladder is climbed like any ladder: jump to go up, sneak to hold on, let go to slide down. */
@Mixin(LivingEntity.class)
public abstract class AirshipLadderMixin {
	@Inject(method = "isClimbing", at = @At("HEAD"), cancellable = true)
	private void chitty$onRopeLadder(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.isSpectator() && AirshipEntity.onLadder(self)) {
			cir.setReturnValue(true);
		}
	}
}
