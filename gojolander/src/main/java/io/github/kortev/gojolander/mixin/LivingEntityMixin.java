package io.github.kortev.gojolander.mixin;

import io.github.kortev.gojolander.domain.DomainHold;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Whatever a Domain holds does not move: no walking, falling or being knocked about, and a mob held does not think. On
 * a client, only the local player is held this way (everyone else it sees where the server says they are).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
	private void gojolander$held(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (DomainHold.held(self)) {
			self.setVelocity(Vec3d.ZERO);
			ci.cancel();
		}
	}
}
