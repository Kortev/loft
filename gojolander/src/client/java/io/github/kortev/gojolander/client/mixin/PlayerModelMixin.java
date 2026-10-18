package io.github.kortev.gojolander.client.mixin;

import io.github.kortev.gojolander.client.ClientDomain;
import io.github.kortev.gojolander.client.ClientDomains;
import io.github.kortev.gojolander.client.HandSign;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Whoever is casting a Domain holds the sign up in front of his face, for everyone to see ({@link HandSign}). */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerModelMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void gojolander$sign(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
			float headPitch, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		// Not his own arm seen from his own eyes: that is posed for the hand in front of the camera, not for the sign.
		boolean ownEyes = entity == client.player && !client.gameRenderer.getCamera().isThirdPerson();
		if (entity instanceof PlayerEntity player && !ownEyes) {
			ClientDomain domain = ClientDomains.signing(player);
			if (domain != null) {
				float k = HandSign.raise(domain, domain.time(client.getRenderTickCounter().getTickDelta(true)));
				if (k > 0.0F) {
					HandSign.pose((PlayerEntityModel<?>) (Object) this, k);
				}
			}
		}
	}
}
