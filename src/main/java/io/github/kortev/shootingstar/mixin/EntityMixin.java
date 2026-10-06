package io.github.kortev.shootingstar.mixin;

import io.github.kortev.shootingstar.gap.VoidFloor;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player on the floor of nothing ({@link VoidFloor}) walks at one height through a world they cannot see: nothing
 * but the floor stops them, no water or lava takes hold of them, and nothing they pass through does anything to them.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Shadow
	protected boolean submergedInWater;

	@Inject(method = "adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"),
			cancellable = true)
	private void shootingstar$floor(Vec3d movement, CallbackInfoReturnable<Vec3d> cir) {
		Entity self = (Entity) (Object) this;
		double floor = VoidFloor.floorFor(self);
		if (!Double.isNaN(floor)) {
			double y = movement.y;
			double feet = self.getBoundingBox().minY;
			if (feet + y < floor) {
				y = floor - feet;
			}
			cir.setReturnValue(new Vec3d(movement.x, y, movement.z));
		}
	}

	@Inject(method = "updateMovementInFluid", at = @At("HEAD"), cancellable = true)
	private void shootingstar$dry(TagKey<Fluid> tag, double speed, CallbackInfoReturnable<Boolean> cir) {
		if (VoidFloor.floating((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "updateSubmergedInWaterState", at = @At("TAIL"))
	private void shootingstar$notUnder(CallbackInfo ci) {
		if (VoidFloor.floating((Entity) (Object) this)) {
			this.submergedInWater = false;
		}
	}

	@Inject(method = "isSubmergedIn", at = @At("HEAD"), cancellable = true)
	private void shootingstar$notIn(TagKey<Fluid> tag, CallbackInfoReturnable<Boolean> cir) {
		if (VoidFloor.floating((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "checkBlockCollision", at = @At("HEAD"), cancellable = true)
	private void shootingstar$untouched(CallbackInfo ci) {
		if (VoidFloor.floating((Entity) (Object) this)) {
			ci.cancel();
		}
	}
}
