package io.github.kortev.shootingstar.mixin;

import io.github.kortev.shootingstar.gap.VoidFloor;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On the server, a player on the floor of nothing goes where their client puts them, through whatever unseen world is
 * in the way (their client keeps them on their floor); and nothing they do there makes them hungry.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	@Inject(method = "addExhaustion", at = @At("HEAD"), cancellable = true)
	private void shootingstar$notHungry(float exhaustion, CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!self.getWorld().isClient() && VoidFloor.floating(self)) {
			ci.cancel();
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void shootingstar$throughTheBlack(CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!self.getWorld().isClient() && VoidFloor.floating(self)) {
			self.noClip = true;
		}
	}
}
