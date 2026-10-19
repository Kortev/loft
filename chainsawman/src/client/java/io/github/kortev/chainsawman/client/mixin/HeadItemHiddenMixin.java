package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ClientForms;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the form, nothing worn on the head that is not armour (a pumpkin, a skull, a block) is stood on it either. */
@Mixin(HeadFeatureRenderer.class)
public abstract class HeadItemHiddenMixin {
	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
			at = @At("HEAD"), cancellable = true)
	private void chainsawman$nothingOnTop(MatrixStack matrices, VertexConsumerProvider consumers, int light, LivingEntity entity, float limbAngle,
			float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
		if (entity instanceof PlayerEntity player && ClientForms.on(player)) {
			ci.cancel();
		}
	}
}
