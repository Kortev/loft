package io.github.kortev.chitty.mixin;

import io.github.kortev.chitty.airship.AirshipEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An airship's rope ladder is climbed like any ladder against a wall: it is solid on its far side (towards her), so
 * walking into it climbs it, as does jumping; sneak to hold on, let go to slide down.
 */
@Mixin(LivingEntity.class)
public abstract class AirshipLadderMixin {
	/** Which way is into the rope ladder this entity walked into this tick (and so climbs), or null. */
	@Unique
	private Vec3d chitty$intoLadder;

	@Inject(method = "isClimbing", at = @At("HEAD"), cancellable = true)
	private void chitty$onRopeLadder(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.isSpectator() && AirshipEntity.onLadder(self)) {
			cir.setReturnValue(true);
		}
	}

	/** Walking into the ladder goes no further into it. */
	@ModifyArg(method = "applyMovementInput", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/LivingEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"),
			index = 1)
	private Vec3d chitty$ladderWall(Vec3d movement) {
		LivingEntity self = (LivingEntity) (Object) this;
		chitty$intoLadder = null;
		Vec3d into = self.isSpectator() ? null : AirshipEntity.intoLadder(self);
		if (into == null) {
			return movement;
		}
		double push = movement.x * into.x + movement.z * into.z;
		if (push <= 0.0) {
			return movement;
		}
		chitty$intoLadder = into;
		return movement.subtract(into.x * push, 0.0, into.z * push);
	}

	/** And climbs it, as walking into a ladder does, keeping none of the push into it. */
	@Inject(method = "applyMovementInput", at = @At("RETURN"), cancellable = true)
	private void chitty$climbRopeLadder(Vec3d movementInput, float slipperiness, CallbackInfoReturnable<Vec3d> cir) {
		Vec3d into = chitty$intoLadder;
		if (into != null) {
			Vec3d velocity = cir.getReturnValue();
			double push = Math.max(0.0, velocity.x * into.x + velocity.z * into.z);
			cir.setReturnValue(new Vec3d(velocity.x - into.x * push, 0.2, velocity.z - into.z * push));
			chitty$intoLadder = null;
		}
	}
}
