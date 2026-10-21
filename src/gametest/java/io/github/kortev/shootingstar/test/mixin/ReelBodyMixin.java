package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.shootingstar.test.ReelSelfTest;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The reel's stand-ins sitting on the floor or lying on their backs (ReelSelfTest). */
@Mixin(PlayerEntityRenderer.class)
public abstract class ReelBodyMixin {
	@Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V",
			at = @At("TAIL"))
	private void shootingstarTest$reelBody(AbstractClientPlayerEntity player, MatrixStack matrices, float animationProgress, float bodyYaw,
			float tickDelta, float scale, CallbackInfo ci) {
		if (ReelSelfTest.active()) {
			ReelSelfTest.body(player, matrices);
		}
	}
}
