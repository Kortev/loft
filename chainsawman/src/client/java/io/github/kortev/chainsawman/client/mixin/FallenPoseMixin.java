package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ClientTeams;
import io.github.kortev.chainsawman.client.ReviveCord;
import io.github.kortev.chainsawman.form.Fallen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The body of a human who is down ({@link Fallen}), lying on their front, head up, propped on one arm with the other reaching ahead, a
 * leg drawn up; and the body of a friend picking them up, leaning in with their right arm reaching down to them (the same arm the cord
 * revive uses, {@link ReviveCord#angles}).
 */
@Mixin(PlayerEntityModel.class)
public abstract class FallenPoseMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void chainsawman$fallen(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch,
			CallbackInfo ci) {
		if (!(entity instanceof PlayerEntity player)) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		boolean ownEyes = entity == client.player && !client.gameRenderer.getCamera().isThirdPerson();
		PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;
		boolean changed = false;
		if (Fallen.is(player)) {
			float breath = MathHelper.sin(animationProgress * 0.12F) * 0.04F;
			model.head.pitch = -0.85F + breath;
			model.head.yaw = 0.0F;
			// Propped on the left forearm, the right arm reaching on ahead; the legs drawn up a little, one over the other.
			model.leftArm.pitch = -1.45F;
			model.leftArm.yaw = 0.35F;
			model.leftArm.roll = -0.15F;
			model.rightArm.pitch = -2.65F + breath;
			model.rightArm.yaw = -0.25F;
			model.rightArm.roll = 0.2F;
			model.rightLeg.pitch = 0.45F;
			model.rightLeg.yaw = -0.05F;
			model.rightLeg.roll = 0.05F;
			model.leftLeg.pitch = -0.1F;
			model.leftLeg.yaw = 0.1F;
			model.leftLeg.roll = -0.05F;
			changed = true;
		}
		PlayerEntity down = ownEyes ? null : ClientTeams.lifting(player);
		if (down != null && client.world != null) {
			float tickDelta = client.getRenderTickCounter().getTickDelta(true);
			Vec3d shoulder = ReviveCord.shoulder(player, tickDelta);
			Vec3d target = down.getLerpedPos(tickDelta).add(0.0, 0.35, 0.0);
			float yaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
			float[] arm = ReviveCord.angles(target.subtract(shoulder).normalize(), yaw);
			// The hand reaches, and heaves a little as it takes the weight.
			float heave = MathHelper.sin((client.world.getTime() + tickDelta) * 0.5F) * 0.08F;
			model.rightArm.pitch = arm[0] + heave;
			model.rightArm.yaw = arm[1];
			model.rightArm.roll = 0.0F;
			model.body.pitch = 0.5F;
			model.head.pitch += 0.35F;
			changed = true;
		}
		if (changed) {
			model.rightSleeve.copyTransform(model.rightArm);
			model.leftSleeve.copyTransform(model.leftArm);
			model.rightPants.copyTransform(model.rightLeg);
			model.leftPants.copyTransform(model.leftLeg);
			model.jacket.copyTransform(model.body);
			model.hat.copyTransform(model.head);
		}
	}
}
