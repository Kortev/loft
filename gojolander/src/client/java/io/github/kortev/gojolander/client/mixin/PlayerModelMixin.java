package io.github.kortev.gojolander.client.mixin;

import io.github.kortev.gojolander.client.ClientDomain;
import io.github.kortev.gojolander.client.ClientDomains;
import io.github.kortev.gojolander.client.HandSign;
import io.github.kortev.gojolander.domain.DomainTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * How players move in a Domain, for everyone to see ({@link HandSign}): whoever casts it lifts the blindfold with his
 * left hand, his head dipping, makes the sign with his right, and pulls the blindfold down again at the end; whoever is
 * caught in it has their head tipped back and trembles as the flood pours in.
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerModelMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void gojolander$domainPose(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
			float headPitch, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		// Not his own arms seen from his own eyes: those are posed for the hand in front of the camera.
		boolean ownEyes = entity == client.player && !client.gameRenderer.getCamera().isThirdPerson();
		if (!(entity instanceof PlayerEntity player) || ownEyes) {
			return;
		}
		PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;
		float tickDelta = client.getRenderTickCounter().getTickDelta(true);
		ClientDomain cast = ClientDomains.unveiled(player);
		if (cast != null) {
			double t = cast.time(tickDelta);
			float nod = HandSign.nod(t);
			if (nod > 0.0F) {
				HandSign.poseNod(model, nod);
			}
			float brow = HandSign.brow(cast, t);
			if (brow > 0.0F) {
				HandSign.poseBrow(model, brow);
			}
			float sign = HandSign.raise(cast, t);
			if (sign > 0.0F) {
				HandSign.pose(model, sign);
			}
		}
		ClientDomain held = ClientDomains.holdingEntity(player);
		if (held != null) {
			double t = held.time(tickDelta);
			float k = (float) MathHelper.clamp((t - DomainTimeline.VOID) / 6.0, 0.0, 1.0);
			if (k > 0.0F) {
				HandSign.poseHeld(model, k, t, player.getId());
			}
		}
	}
}
