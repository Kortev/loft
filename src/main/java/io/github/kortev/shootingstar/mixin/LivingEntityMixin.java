package io.github.kortev.shootingstar.mixin;

import io.github.kortev.shootingstar.gap.VoidFloor;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nothing left in the world can see a player on the floor of nothing to go after them: they are out of the game. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "playBlockFallSound", at = @At("HEAD"), cancellable = true)
	private void shootingstar$silentLanding(CallbackInfo ci) {
		if (VoidFloor.floating((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "isPartOfGame", at = @At("HEAD"), cancellable = true)
	private void shootingstar$outOfReach(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.getWorld().isClient() && VoidFloor.floating(self)) {
			cir.setReturnValue(false);
		}
	}
}
