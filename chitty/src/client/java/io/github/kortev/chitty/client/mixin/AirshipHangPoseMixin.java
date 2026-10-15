package io.github.kortev.chitty.client.mixin;

import io.github.kortev.chitty.airship.AirshipHookEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * How someone on the airship's grapple is drawn, so that everyone can tell at a glance whether they chose to be there:
 * hanging on by choice, they hang from its ring by both hands, arms straight up, looking up, legs together and swaying;
 * caught by the back of the collar, they hang limp, arms and legs dangling a little apart, and when they kick or
 * struggle (AirshipHookEntity.isKicking) they thrash.
 */
@Mixin(BipedEntityModel.class)
public abstract class AirshipHangPoseMixin {
	@Shadow
	@Final
	public ModelPart head;
	@Shadow
	@Final
	public ModelPart rightArm;
	@Shadow
	@Final
	public ModelPart leftArm;
	@Shadow
	@Final
	public ModelPart rightLeg;
	@Shadow
	@Final
	public ModelPart leftLeg;

	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void chitty$hangOnGrapple(LivingEntity entity, float limbAngle, float limbDistance, float age, float headYaw,
			float headPitch, CallbackInfo ci) {
		if (!(entity.getVehicle() instanceof AirshipHookEntity hook)) {
			return;
		}
		float sway = MathHelper.sin(age * 0.09F);
		if (hook.isVoluntary()) {
			rightArm.pitch = (float) Math.PI + 0.1F;
			leftArm.pitch = (float) Math.PI + 0.1F;
			rightArm.yaw = 0.0F;
			leftArm.yaw = 0.0F;
			rightArm.roll = 0.12F;
			leftArm.roll = -0.12F;
			head.pitch = Math.min(head.pitch, -0.35F);
			rightLeg.pitch = 0.08F * sway;
			leftLeg.pitch = 0.08F * sway;
			rightLeg.roll = 0.02F;
			leftLeg.roll = -0.02F;
			return;
		}
		if (hook.isKicking()) {
			float thrash = MathHelper.sin(age * 1.6F);
			rightArm.pitch = -0.6F + 0.9F * thrash;
			leftArm.pitch = -0.6F - 0.9F * thrash;
			rightArm.roll = 0.5F;
			leftArm.roll = -0.5F;
			rightLeg.pitch = 0.8F * thrash;
			leftLeg.pitch = -0.8F * thrash;
		} else {
			rightArm.pitch = -0.2F + 0.05F * sway;
			leftArm.pitch = -0.2F - 0.05F * sway;
			rightArm.roll = 0.3F;
			leftArm.roll = -0.3F;
			rightLeg.pitch = -0.12F;
			leftLeg.pitch = 0.04F;
			head.pitch = Math.max(head.pitch, 0.3F);
		}
		rightArm.yaw = 0.0F;
		leftArm.yaw = 0.0F;
		rightLeg.roll = 0.12F;
		leftLeg.roll = -0.12F;
	}
}
