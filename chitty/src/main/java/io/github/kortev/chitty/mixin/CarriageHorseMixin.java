package io.github.kortev.chitty.mixin;

import io.github.kortev.chitty.carriage.CarriageEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A horse hitched to a carriage goes where its shafts are, on every side: it puts itself there as it ticks (the
 * carriage puts it there too as it moves, whichever comes first), it does not move itself, and its legs go as fast as
 * the carriage does (a passenger's never would). A client does not take where the server says it is: it is already
 * where the carriage, which the client draws moving smoothly, has it.
 */
@Mixin(LivingEntity.class)
public abstract class CarriageHorseMixin {
	@Shadow
	protected abstract void updateLimbs(float posDelta);

	@Inject(method = "tickMovement", at = @At("HEAD"))
	private void chitty$inTheShafts(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		CarriageEntity carriage = CarriageEntity.hitchedTo(self);
		if (carriage != null && self instanceof AbstractHorseEntity horse) {
			carriage.holdHorse(horse);
		}
	}

	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void chitty$pulling(Vec3d movementInput, CallbackInfo ci) {
		CarriageEntity carriage = CarriageEntity.hitchedTo((LivingEntity) (Object) this);
		if (carriage != null) {
			updateLimbs(carriage.getStride());
			ci.cancel();
		}
	}

	@Inject(method = "updateTrackedPositionAndAngles", at = @At("HEAD"), cancellable = true)
	private void chitty$whereTheShaftsAre(double x, double y, double z, float yaw, float pitch, int interpolationSteps, CallbackInfo ci) {
		if (CarriageEntity.hitchedTo((LivingEntity) (Object) this) != null) {
			ci.cancel();
		}
	}

	@Inject(method = "updateTrackedHeadRotation", at = @At("HEAD"), cancellable = true)
	private void chitty$headInHarness(float yaw, int interpolationSteps, CallbackInfo ci) {
		if (CarriageEntity.hitchedTo((LivingEntity) (Object) this) != null) {
			ci.cancel();
		}
	}
}
