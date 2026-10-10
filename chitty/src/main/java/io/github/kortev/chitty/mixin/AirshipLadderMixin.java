package io.github.kortev.chitty.mixin;

import io.github.kortev.chitty.airship.AirshipEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An airship's rope ladder is climbed like any ladder against a wall: it is solid on its far side (towards her), so
 * walking into it climbs it, as does jumping; sneak to hold on, let go to slide down. Whoever is on it is carried along
 * as she goes, and its rungs knock as they climb.
 */
@Mixin(LivingEntity.class)
public abstract class AirshipLadderMixin {
	/** Which way is into the rope ladder this entity walked into this tick (and so climbs), or null. */
	@Unique
	private Vec3d chitty$intoLadder;
	/** How far this entity has climbed since its last knock on a rung. */
	@Unique
	private double chitty$sinceRung;

	@Inject(method = "isClimbing", at = @At("HEAD"), cancellable = true)
	private void chitty$onRopeLadder(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.isSpectator() && AirshipEntity.onLadder(self)) {
			cir.setReturnValue(true);
		}
	}

	/** Walking into the ladder goes no further into it; and she carries the ladder, and whoever is on it, along. */
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
		Vec3d carry = AirshipEntity.ladderCarry(self);
		chitty$knockRungs(self, carry);
		Vec3d carried = carry == null ? movement : movement.add(carry);
		double push = movement.x * into.x + movement.z * into.z;
		if (push <= 0.0) {
			return carried;
		}
		chitty$intoLadder = into;
		return carried.subtract(into.x * push, 0.0, into.z * push);
	}

	/**
	 * The rungs knock under hands and feet as someone climbs: by how far they climbed up or down it last tick (not how
	 * hard they were pulled, which, standing at its foot, is only their weight).
	 */
	@Unique
	private void chitty$knockRungs(LivingEntity self, Vec3d carry) {
		if (self.isOnGround()) {
			chitty$sinceRung = 0.0;
			return;
		}
		// Less what she carried them up or down with the ladder.
		chitty$sinceRung += Math.abs(self.getY() - self.prevY - (carry == null ? 0.0 : carry.y));
		if (chitty$sinceRung < 0.6) {
			return;
		}
		chitty$sinceRung = 0.0;
		World world = self.getWorld();
		float pitch = 0.9F + world.random.nextFloat() * 0.2F;
		if (world.isClient) {
			world.playSound(self.getX(), self.getY(), self.getZ(), SoundEvents.BLOCK_LADDER_STEP, SoundCategory.PLAYERS, 0.4F, pitch, false);
		} else {
			world.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.BLOCK_LADDER_STEP, SoundCategory.NEUTRAL, 0.4F,
					pitch);
		}
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
