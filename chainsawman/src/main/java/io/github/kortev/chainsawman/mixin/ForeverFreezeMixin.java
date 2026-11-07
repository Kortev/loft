package io.github.kortev.chainsawman.mixin;

import io.github.kortev.chainsawman.contract.ForeverFlags;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A thing locked in an endless moment (Forever) does nothing at all: no AI, no walking, no falling, no animation. Both sides skip
 * its movement and its limbs' update, so it holds the pose it was caught in, and when it is let go its velocity is as it was.
 */
@Mixin(LivingEntity.class)
public abstract class ForeverFreezeMixin {
	@Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
	private void chainsawman$frozen(CallbackInfo ci) {
		if (ForeverFlags.locked((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "updateLimbs(F)V", at = @At("HEAD"), cancellable = true)
	private void chainsawman$limbsFrozen(float posDelta, CallbackInfo ci) {
		if (ForeverFlags.locked((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
