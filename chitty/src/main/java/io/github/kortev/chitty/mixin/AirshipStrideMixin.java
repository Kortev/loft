package io.github.kortev.chitty.mixin;

import io.github.kortev.chitty.airship.AirshipEntity;
import io.github.kortev.chitty.airship.AirshipHookEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Aboard the airship, people's legs go as they walk about the gondola and stand still otherwise, however she moves
 * under them: she moves their legs herself (AirshipEntity's client tick), and the game's own stride, taken from how
 * far they have moved, is left out. Whoever hangs from her grapple dangles, their legs still as they swing.
 */
@Mixin(LivingEntity.class)
public abstract class AirshipStrideMixin {
	@Inject(method = "updateLimbs(Z)V", at = @At("HEAD"), cancellable = true)
	private void chitty$walkAboard(boolean flutter, CallbackInfo ci) {
		if (aboard((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "updateLimbs(F)V", at = @At("HEAD"), cancellable = true)
	private void chitty$strideAboard(float posDelta, CallbackInfo ci) {
		if (aboard((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}

	@Unique
	private static boolean aboard(LivingEntity entity) {
		return entity.getVehicle() instanceof AirshipEntity || entity.getVehicle() instanceof AirshipHookEntity;
	}
}
