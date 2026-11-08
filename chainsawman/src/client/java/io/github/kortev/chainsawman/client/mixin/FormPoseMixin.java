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
		PlayerEntityModel<?> whole = (PlayerEntityModel<?>) (Object) this;
		// (One model draws every player: whatever was regrowing on the last one is whole again first.)
		for (net.minecraft.client.model.ModelPart limb : new net.minecraft.client.model.ModelPart[] {whole.rightArm, whole.leftArm, whole.rightLeg,
				whole.leftLeg}) {
			limb.xScale = 1.0F;
			limb.yScale = 1.0F;
			limb.zScale = 1.0F;
		}
		// Not your own arm seen from your own eyes: that is posed for the hand in front of the camera.
		boolean ownEyes = entity == client.player && !client.gameRenderer.getCamera().isThirdPerson();
		if (!(entity instanceof PlayerEntity player) || ownEyes) {
			return;
		}
		float tickDelta = client.getRenderTickCounter().getTickDelta(true);
		float t = ClientForms.pullAge(player, tickDelta);
		PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;
		float merge = io.github.kortev.chainsawman.client.MergeCinematic.age(player, tickDelta);
		float back = ClientForms.turnAge(player, tickDelta);
		float up = io.github.kortev.chainsawman.client.ClientCombat.gettingUp(player, tickDelta);
		java.util.UUID[] pulled = new java.util.UUID[1];
		io.github.kortev.chainsawman.client.ReviveCord.Pull revive = io.github.kortev.chainsawman.client.ReviveCord.by(player, pulled);
		net.minecraft.entity.player.PlayerEntity down = revive != null && client.world != null ? client.world.getPlayerByUuid(pulled[0]) : null;
		if (entity != null && io.github.kortev.chainsawman.form.Downed.is(player)) {
			// The one whose cord is pulled jolts with each tug.
			float jolt = io.github.kortev.chainsawman.client.ReviveCord.jolt(player, client.world.getTime(), tickDelta);
			if (jolt > 0.0F) {
				model.head.pitch -= 0.5F * jolt;
				model.head.roll += 0.2F * jolt;
				model.body.pitch -= 0.15F * jolt;
			}
		}
		if (down != null && up < 0.0F) {
			// Pulling a friend's cord out of their chest: the arm reaches to it and draws back with it.
			long now = client.world.getTime();
			net.minecraft.util.math.Vec3d shoulder = io.github.kortev.chainsawman.client.ReviveCord.shoulder(player, tickDelta);
			io.github.kortev.chainsawman.client.ReviveCord.Grip grip = io.github.kortev.chainsawman.client.ReviveCord.grip(revive, player, down, now, tickDelta);
			float yaw = net.minecraft.util.math.MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
			float[] arm = io.github.kortev.chainsawman.client.ReviveCord.angles(grip.hand().subtract(shoulder).normalize(), yaw);
			model.rightArm.pitch = arm[0];
			model.rightArm.yaw = arm[1];
			model.rightArm.roll = 0.0F;
			// Leaning in over the chest between tugs; his whole body rocks back with each tug, hardest with the yank.
			boolean yanked = revive.how() == io.github.kortev.chainsawman.client.ReviveCord.YANKED;
			model.body.pitch = 0.65F - (yanked ? 1.1F : 0.6F) * grip.pulse();
			model.head.pitch += 0.15F - 0.5F * grip.pulse();
		} else if (up >= 0.0F) {
			// Back up after being down: the limbs growing out of their stumps as you push yourself up.
			io.github.kortev.chainsawman.client.GetUp.limbs(model, false, up, 0.0F);
			float g = io.github.kortev.chainsawman.client.GetUp.regrown(up);
			for (net.minecraft.client.model.ModelPart limb : new net.minecraft.client.model.ModelPart[] {model.rightArm, model.leftArm, model.rightLeg,
					model.leftLeg}) {
				limb.yScale = g;
				limb.xScale = 0.6F + 0.4F * g;
				limb.zScale = 0.6F + 0.4F * g;
			}
			model.rightPants.copyTransform(model.rightLeg);
			model.leftPants.copyTransform(model.leftLeg);
		} else if (merge >= 0.0F && t < 0.0F) {
			io.github.kortev.chainsawman.client.CordPull.lie(model, merge);
		} else if (back >= 0.0F) {
			io.github.kortev.chainsawman.client.CordPull.turnBack(model, back);
		} else if (t >= 0.0F) {
			CordPull.pose(model, t);
		} else if (ClientForms.on(player)) {
			SawPose.pose(model, player, tickDelta);
		} else if (io.github.kortev.chainsawman.client.HeldPochita.held(player)) {
			// Pochita held as a chainsaw, in both hands.
			io.github.kortev.chainsawman.client.HeldPochita.pose(model, player, tickDelta);
		} else {
			return;
		}
		for (int arm : new int[] {io.github.kortev.chainsawman.form.Limbs.RIGHT, io.github.kortev.chainsawman.form.Limbs.LEFT}) {
			float k = io.github.kortev.chainsawman.client.ClientCombat.regrowing(player, arm, tickDelta);
			net.minecraft.client.model.ModelPart part = arm == io.github.kortev.chainsawman.form.Limbs.RIGHT ? model.rightArm : model.leftArm;
			if (k >= 0.0F) {
				// Grown back out of blood: out of the stub, a little past its length, settling.
				float g = io.github.kortev.chainsawman.client.GetUp.regrown(k * 0.3F);
				float stub = io.github.kortev.chainsawman.client.Stumps.STUB;
				part.yScale = stub + (1.0F - stub) * g;
				part.xScale = 0.85F + 0.15F * g;
				part.zScale = 0.85F + 0.15F * g;
			} else if (!io.github.kortev.chainsawman.form.Limbs.has(player, arm)) {
				// Lost: the stub of it.
				io.github.kortev.chainsawman.client.Stumps.stub(part);
			}
		}
		model.rightSleeve.copyTransform(model.rightArm);
		model.leftSleeve.copyTransform(model.leftArm);
		model.jacket.copyTransform(model.body);
		model.hat.copyTransform(model.head);
	}
}
