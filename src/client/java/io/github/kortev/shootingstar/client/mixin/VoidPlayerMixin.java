package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.gap.ClientGaps;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On the floor of nothing the unseen world does not push anyone out of itself: they walk through it. Once it is back
 * where they stand it does again, so nobody is left with their head in a wall.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class VoidPlayerMixin {
	@Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
	private void shootingstar$noPush(double x, double z, CallbackInfo ci) {
		if (ClientGaps.passingThrough()) {
			ci.cancel();
		}
	}
}
