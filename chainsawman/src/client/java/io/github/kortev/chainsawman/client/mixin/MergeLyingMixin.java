package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.MergeCinematic;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Through the merge ({@code MergeCinematic}) you lie on your back where you fell: the body tipped over backwards from
 * the feet, a little off the ground, and back up again as you rise.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class MergeLyingMixin {
	@Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V",
			at = @At("TAIL"))
	private void chainsawman$lie(AbstractClientPlayerEntity player, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta,
			float scale, CallbackInfo ci) {
		float k = MergeCinematic.lying(player, tickDelta);
		if (k > 0.0F) {
			MergeCinematic.drawn(player, bodyYaw);
			matrices.translate(0.0F, 0.15F * k, 0.0F);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F * k));
		}
	}
}
