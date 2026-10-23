package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.BeamClient;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The hit-stop of Beam's big hits ({@link BeamClient#hold}): for a few frames the game's clock does not advance, so your view and
 * everything in it (the mob that was hit, the particles) hold still. It is the rendering clock of your own client only; the server
 * never stops, and nobody else sees it. (After the test capture's own clock, which handles the hold itself.)
 */
@Mixin(value = RenderTickCounter.Dynamic.class, priority = 1500)
public abstract class BeamHoldMixin {
	@Shadow
	private float lastFrameDuration;

	@Shadow
	private long prevTimeMillis;

	@Shadow
	private long timeMillis;

	@Inject(method = "beginRenderTick(JZ)I", at = @At("HEAD"), cancellable = true)
	private void chainsawman$hold(long time, boolean tick, CallbackInfoReturnable<Integer> cir) {
		if (tick && BeamClient.hold()) {
			// Real time keeps being kept, so nothing jumps afterwards.
			this.timeMillis = time;
			this.prevTimeMillis = time;
			this.lastFrameDuration = 0.0F;
			cir.setReturnValue(0);
		}
	}
}
