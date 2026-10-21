package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.shootingstar.test.ReelSelfTest;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The reel's stand-ins posed as each cut has them (ReelSelfTest): sitting, carrying, a zombie's arms, limp. */
@Mixin(value = PlayerEntityModel.class, priority = 1500)
public abstract class ReelPoseMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void shootingstarTest$reelPose(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
			float headPitch, CallbackInfo ci) {
		if (ReelSelfTest.active() && entity instanceof AbstractClientPlayerEntity player) {
			ReelSelfTest.pose(player, (PlayerEntityModel<?>) (Object) this);
		}
	}
}
