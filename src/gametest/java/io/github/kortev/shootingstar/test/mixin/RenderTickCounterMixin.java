package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.shootingstar.test.Capture;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While capturing, frames advance by a fixed slice of game time instead of real time. */
@Mixin(RenderTickCounter.Dynamic.class)
public abstract class RenderTickCounterMixin {
	private static final float[] DELTA = new float[1];

	@Shadow
	private float lastFrameDuration;

	@Shadow
	private float tickDelta;

	@Shadow
	private float lastDuration;

	@Shadow
	private long prevTimeMillis;

	@Shadow
	private long timeMillis;

	@Inject(method = "beginRenderTick(JZ)I", at = @At("HEAD"), cancellable = true)
	private void shootingstarTest$capture(long time, boolean tick, CallbackInfoReturnable<Integer> cir) {
		if (!tick || !Capture.active()) {
			return;
		}
		int ticks = Capture.beginFrame(DELTA);
		if (ticks < 0) {
			return;
		}
		// Keep the real-time bookkeeping current so nothing jumps when the capture ends.
		this.timeMillis = time;
		this.prevTimeMillis = time;
		this.lastFrameDuration = (float) Capture.STEP;
		this.lastDuration = (float) Capture.STEP;
		this.tickDelta = DELTA[0];
		cir.setReturnValue(ticks);
	}
}
