package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ClientForms;
import io.github.kortev.chainsawman.client.CordPull;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The body through the transformation, for everyone to see ({@link CordPull}): the yank, the brace and the roar. */
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
		float t = ClientForms.pullAge(player, client.getRenderTickCounter().getTickDelta(true));
		if (t >= 0.0F) {
			PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;
			CordPull.pose(model, t);
			model.rightSleeve.copyTransform(model.rightArm);
			model.leftSleeve.copyTransform(model.leftArm);
			model.hat.copyTransform(model.head);
		}
	}
}
