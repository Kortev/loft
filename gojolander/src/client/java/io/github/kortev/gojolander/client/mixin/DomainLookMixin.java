package io.github.kortev.gojolander.client.mixin;

import io.github.kortev.gojolander.client.ClientDomains;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Holds this player's head still while a Domain holds them, or while the camera has them casting one. */
@Mixin(Entity.class)
public abstract class DomainLookMixin {
	@Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
	private void gojolander$holdLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		if ((Object) this == MinecraftClient.getInstance().player && ClientDomains.locked()) {
			ci.cancel();
		}
	}
}
