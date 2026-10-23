package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.BeamEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A rider on Beam can sprint. The game stops a rider sprinting unless their mount is steered by them (a horse, a pig); Beam is moved by
 * the server, so by default the sprint key does nothing on his back. His rider's sprint (the sprint key, or a double tap of forward, and
 * the game's own packet that tells the server) is what makes him dive and swim ({@code Beam}); on his legs he walks.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class BeamSprintMixin {
	@Inject(method = "canVehicleSprint", at = @At("HEAD"), cancellable = true)
	private void chainsawman$sprintOnBeam(Entity vehicle, CallbackInfoReturnable<Boolean> cir) {
		if (vehicle instanceof BeamEntity) {
			cir.setReturnValue(true);
		}
	}
}
