package io.github.kortev.chitty.mixin;

import io.github.kortev.chitty.airship.AirshipEntity;
import io.github.kortev.chitty.airship.AirshipHookEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sneaking does not take a player off the airship where it would drop them to their death (only when she is down or
 * her ladder is let down to climb), nor off her grapple until they have struggled free (AirshipHookEntity).
 */
@Mixin(PlayerEntity.class)
public abstract class AirshipDismountMixin {
	@Inject(method = "shouldDismount", at = @At("HEAD"), cancellable = true)
	private void chitty$holdOn(CallbackInfoReturnable<Boolean> cir) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		Entity vehicle = self.getVehicle();
		if (vehicle instanceof AirshipHookEntity hook) {
			if (!hook.freed(self)) {
				cir.setReturnValue(false);
			}
		} else if (vehicle instanceof AirshipEntity ship && !ship.letsOff(self)) {
			cir.setReturnValue(false);
		}
	}
}
