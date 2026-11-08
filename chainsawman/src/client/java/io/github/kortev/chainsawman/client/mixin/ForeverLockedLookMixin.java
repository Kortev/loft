package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.contract.ForeverFlags;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Locked in an endless moment you cannot turn: the mouse does nothing while the Eternity Devil holds you. */
@Mixin(Entity.class)
public abstract class ForeverLockedLookMixin {
	@Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
	private void chainsawman$held(double dx, double dy, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (self.getWorld().isClient && ForeverFlags.CLIENT.contains(self.getId())) {
			ci.cancel();
		}
	}
}
