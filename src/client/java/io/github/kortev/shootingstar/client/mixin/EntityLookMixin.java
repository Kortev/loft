package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.gap.ClientGaps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Holds the shooter's head still through a Ginnungagap event's shots, whatever the mouse does. */
@Mixin(Entity.class)
public abstract class EntityLookMixin {
	@Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
	private void shootingstar$holdLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		if ((Object) this == MinecraftClient.getInstance().player && ClientGaps.locked()) {
			ci.cancel();
		}
	}
}
