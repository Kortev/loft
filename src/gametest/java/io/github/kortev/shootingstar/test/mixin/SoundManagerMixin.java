package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.shootingstar.test.Capture;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Logs every sound for the capture's soundtrack (the CI machine has no audio device). */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
	@Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"))
	private void shootingstarTest$log(SoundInstance sound, CallbackInfo ci) {
		Capture.sound(sound, 0);
	}

	@Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;I)V", at = @At("HEAD"))
	private void shootingstarTest$logDelayed(SoundInstance sound, int delay, CallbackInfo ci) {
		Capture.sound(sound, delay);
	}
}
