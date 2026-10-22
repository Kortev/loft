package io.github.kortev.gojolander.client.mixin;

import io.github.kortev.gojolander.client.ClientDomain;
import io.github.kortev.gojolander.client.ClientDomains;
import io.github.kortev.gojolander.client.HandSign;
import io.github.kortev.gojolander.domain.DomainTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anything with arms and legs like a player's (a zombie, a skeleton, a piglin) is held and let go of as a player is
 * ({@link PlayerModelMixin} poses the players themselves): its head tipped back and arms out in the flood, jolted at each
 * step down, and down onto its knees as it lets go, and up again. What it does with its arms of its own (a zombie's
 * reaching) it still does after.
 */
@Mixin(BipedEntityModel.class)
public abstract class BipedKneelMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void gojolander$heldAndKnelt(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
			float headPitch, CallbackInfo ci) {
		if (entity instanceof PlayerEntity) {
			return;
		}
		BipedEntityModel<?> model = (BipedEntityModel<?>) (Object) this;
		float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
		ClientDomain held = ClientDomains.holdingEntity(entity);
		if (held != null) {
			double t = held.time(tickDelta);
			float k = (float) MathHelper.clamp((t - DomainTimeline.VOID) / 6.0, 0.0, 1.0);
			if (k > 0.0F) {
				HandSign.poseHeld(model, k, t, entity.getId(), held.jolt(t));
			}
			return;
		}
		ClientDomain let = ClientDomains.kneeling(entity);
		if (let != null) {
			double t = let.time(tickDelta);
			if (let.snapping(entity.getId(), t)) {
				HandSign.poseHeld(model, 1.0F - let.kneel(entity.getId(), t), t, entity.getId(),
						(float) ((t - let.collapse()) / DomainTimeline.SNAP) * 0.5F);
			}
			float kneel = let.kneel(entity.getId(), t);
			if (kneel > 0.0F) {
				HandSign.poseKneel(model, kneel, t, entity.getId());
			}
		}
	}
}
