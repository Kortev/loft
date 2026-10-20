package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.form.Downed;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Down, your arms and legs are gone: not drawn, sleeves and trouser legs with them (FormFeature draws the raw stumps). (An
 * arm lost in the fight, up, leaves its stub: FormPoseMixin, Stumps.)
 * The renderer shows them again before the next frame's pose, so nothing is left hidden once you are back up.
 */
@Mixin(PlayerEntityModel.class)
public abstract class DownedBodyMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void chainsawman$noLimbs(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
			float headPitch, CallbackInfo ci) {
		if (entity instanceof PlayerEntity player && Downed.is(player)) {
			PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;
			model.rightArm.visible = false;
			model.leftArm.visible = false;
			model.rightLeg.visible = false;
			model.leftLeg.visible = false;
			model.rightSleeve.visible = false;
			model.leftSleeve.visible = false;
			model.rightPants.visible = false;
			model.leftPants.visible = false;
		}
	}
}
