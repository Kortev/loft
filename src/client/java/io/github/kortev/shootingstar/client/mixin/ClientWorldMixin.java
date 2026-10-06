package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.gap.GapRender;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The clouds come back with the sky as the world is rebuilt: dark while it is dark, not white against the black. */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
	@Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true, require = 0)
	private void shootingstar$darkClouds(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
		float light = GapRender.skyLight(tickDelta);
		if (light < 1.0F) {
			cir.setReturnValue(cir.getReturnValue().multiply(light));
		}
	}
}
