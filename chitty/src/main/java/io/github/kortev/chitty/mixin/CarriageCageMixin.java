package io.github.kortev.chitty.mixin;

import io.github.kortev.chitty.carriage.CarriageEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Child Catcher's cage, its door shut, holds whatever is in it (CarriageEntity.holds): nothing takes it out of her,
 * an ender pearl, a chorus fruit or getting on something else within reach. (The ender pearl still lands; its thrower is
 * pulled back in.)
 */
@Mixin(Entity.class)
public abstract class CarriageCageMixin {
	@Inject(method = "dismountVehicle", at = @At("HEAD"), cancellable = true)
	private void chitty$heldIn(CallbackInfo ci) {
		if (CarriageEntity.holds((Entity) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "startRiding(Lnet/minecraft/entity/Entity;Z)Z", at = @At("HEAD"), cancellable = true)
	private void chitty$heldFrom(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (vehicle != self.getVehicle() && CarriageEntity.holds(self)) {
			cir.setReturnValue(false);
		}
	}
}
