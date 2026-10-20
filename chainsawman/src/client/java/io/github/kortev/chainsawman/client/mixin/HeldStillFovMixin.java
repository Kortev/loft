package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.MergeCinematic;
import io.github.kortev.chainsawman.form.Downed;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Held still (down, or through the merge) your speed is taken away, and the game would zoom your view in by half as it
 * does for slowness: none of that, the camera's shots are framed for your own field of view.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class HeldStillFovMixin {
	@Inject(method = "getFovMultiplier", at = @At("RETURN"), cancellable = true)
	private void chainsawman$noZoom(CallbackInfoReturnable<Float> cir) {
		AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
		if (Downed.is(self) || MergeCinematic.merging(self)) {
			cir.setReturnValue(1.0F);
		}
	}
}
