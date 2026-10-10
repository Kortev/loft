package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.gap.ClientGaps;
import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nothing that cannot be seen gets in the way of the camera on the floor of nothing: the third-person camera is not
 * pulled in by the unseen world, and unseen water does not close over it.
 */
@Mixin(Camera.class)
public abstract class VoidCameraMixin {
	@Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
	private void shootingstar$unclipped(float distance, CallbackInfoReturnable<Float> cir) {
		if (ClientGaps.floating()) {
			cir.setReturnValue(distance);
		}
	}

	@Inject(method = "getSubmersionType", at = @At("HEAD"), cancellable = true)
	private void shootingstar$dry(CallbackInfoReturnable<CameraSubmersionType> cir) {
		if (ClientGaps.floating()) {
			cir.setReturnValue(CameraSubmersionType.NONE);
		}
	}
}
