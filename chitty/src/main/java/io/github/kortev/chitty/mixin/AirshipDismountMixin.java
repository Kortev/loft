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
 * Sneaking aboard the airship is hers to decide (AirshipEntity.letsGo): at the wheel it lets go of the wheel; anywhere
 * else it gets you off beside her when she is down, or in the air onto her rope ladder, never to your death. Nor does
 * sneaking take a player off her grapple until they have struggled free (AirshipHookEntity).
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
		} else if (vehicle instanceof AirshipEntity ship) {
			cir.setReturnValue(self.isSneaking() && ship.letsGo(self));
		}
	}
}
