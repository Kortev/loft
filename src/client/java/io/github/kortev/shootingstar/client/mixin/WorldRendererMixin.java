package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.gap.ClientGaps;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No clouds over a Ginnungagap: the camera works below them, and the other universe's sky takes their place. */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
	@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
	private void shootingstar$noClouds(CallbackInfo ci) {
		if (ClientGaps.cloudless()) {
			ci.cancel();
		}
	}
}
