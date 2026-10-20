package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ClientCombat;
import io.github.kortev.chainsawman.client.GetUp;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Back up after being down: the body getting up off its front ({@link GetUp}), not tipped up like a plank. */
@Mixin(PlayerEntityRenderer.class)
public abstract class GetUpMixin {
	@Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V",
			at = @At("TAIL"))
	private void chainsawman$getUp(AbstractClientPlayerEntity player, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta,
			float scale, CallbackInfo ci) {
		float k = ClientCombat.gettingUp(player, tickDelta);
		if (k >= 0.0F) {
			GetUp.root(matrices, false, k);
		}
	}
}
