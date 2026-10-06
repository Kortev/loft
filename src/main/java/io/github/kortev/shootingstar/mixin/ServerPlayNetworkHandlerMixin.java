package io.github.kortev.shootingstar.mixin;

import io.github.kortev.shootingstar.gap.VoidFloor;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Standing on the floor of nothing is standing, not flying: nobody is kicked for it. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
	@Inject(method = "isEntityOnAir", at = @At("HEAD"), cancellable = true)
	private void shootingstar$onTheFloor(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (VoidFloor.floating(entity)) {
			cir.setReturnValue(false);
		}
	}
}
