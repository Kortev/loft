package io.github.kortev.gojolander.client.mixin;

import io.github.kortev.gojolander.Gojolander;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The blindfold is not stood on the head like a pumpkin or a block, as anything else worn there that is not armour
 * would be: it is drawn as the cloth tied round it ({@code BlindfoldFeature}).
 */
@Mixin(HeadFeatureRenderer.class)
public abstract class HeadItemMixin {
	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
			at = @At("HEAD"), cancellable = true)
	private void gojolander$notOnTop(MatrixStack matrices, VertexConsumerProvider consumers, int light, LivingEntity entity, float limbAngle,
			float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
		if (entity.getEquippedStack(EquipmentSlot.HEAD).isOf(Gojolander.BLINDFOLD)) {
			ci.cancel();
		}
	}
}
