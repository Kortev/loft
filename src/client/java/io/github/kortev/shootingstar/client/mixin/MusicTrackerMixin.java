package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.gap.ClientGaps;
import net.minecraft.client.sound.MusicTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft's own music waits while a Ginnungagap plays out, and while the song in the black is on. */
@Mixin(MusicTracker.class)
public abstract class MusicTrackerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void shootingstar$hold(CallbackInfo ci) {
		if (ClientGaps.holdMusic()) {
			ci.cancel();
		}
	}
}
