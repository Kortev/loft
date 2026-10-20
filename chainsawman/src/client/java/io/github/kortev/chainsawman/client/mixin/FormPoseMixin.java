package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ClientForms;
import io.github.kortev.chainsawman.client.CordPull;
import io.github.kortev.chainsawman.client.SawPose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The body in the form, for everyone to see: through the transformation the yank, the brace and the roar ({@link
 * CordPull}); after it, fighting with the saws in place of the game's punch ({@link SawPose}).
 */
@Mixin(PlayerEntityModel.class)
public abstract class FormPoseMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void chainsawman$pullCord(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
			float headPitch, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		// Not your own arm seen from your own eyes: that is posed for the hand in front of the camera.
		boolean ownEyes = entity == client.player && !client.gameRenderer.getCamera().isThirdPerson();
		if (!(entity instanceof PlayerEntity player) || ownEyes) {
			return;
		}
		float tickDelta = client.getRenderTickCounter().getTickDelta(true);
		float t = ClientForms.pullAge(player, tickDelta);
		PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;
		float merge = io.github.kortev.chainsawman.client.MergeCinematic.age(player, tickDelta);
		if (merge >= 0.0F && t < 0.0F) {
			io.github.kortev.chainsawman.client.CordPull.lie(model, merge);
		} else if (t >= 0.0F) {
			CordPull.pose(model, t);
		} else if (ClientForms.on(player)) {
			SawPose.pose(model, player, tickDelta);
		} else {
			return;
		}
		model.rightSleeve.copyTransform(model.rightArm);
		model.leftSleeve.copyTransform(model.leftArm);
		model.jacket.copyTransform(model.body);
		model.hat.copyTransform(model.head);
	}
}
