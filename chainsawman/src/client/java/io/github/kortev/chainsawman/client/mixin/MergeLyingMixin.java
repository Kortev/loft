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
 * the feet, a little off the ground; and get up off it ({@code GetUp}) as you rise.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class MergeLyingMixin {
	@Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V",
			at = @At("TAIL"))
	private void chainsawman$lie(AbstractClientPlayerEntity player, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta,
			float scale, CallbackInfo ci) {
		float t = MergeCinematic.age(player, tickDelta);
		if (t >= io.github.kortev.chainsawman.form.MergeTimeline.RISE && t < io.github.kortev.chainsawman.form.MergeTimeline.END) {
			// Getting up off your back, as a body does.
			MergeCinematic.drawn(player, bodyYaw);
			io.github.kortev.chainsawman.client.GetUp.root(matrices, true, MergeCinematic.rising(t));
			return;
		}
		float k = MergeCinematic.lying(player, tickDelta);
		if (k > 0.0F) {
			MergeCinematic.drawn(player, bodyYaw);
			// A little off the ground, and jolted off it by each beat of the heart.
			matrices.translate(0.0F, 0.15F * k + MergeCinematic.jolt(player, tickDelta), 0.0F);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F * k));
		}
	}
}
