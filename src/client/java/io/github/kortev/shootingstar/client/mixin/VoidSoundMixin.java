package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.gap.ClientGaps;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the void nothing of the world is heard: not its creatures, its weather or its blocks (ClientGaps.hushes). */
@Mixin(SoundManager.class)
public abstract class VoidSoundMixin {
	@Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
	private void shootingstar$hush(SoundInstance sound, CallbackInfo ci) {
		if (ClientGaps.hushes(sound.getCategory())) {
			ci.cancel();
		}
	}

	@Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;I)V", at = @At("HEAD"), cancellable = true)
	private void shootingstar$hushLater(SoundInstance sound, int delay, CallbackInfo ci) {
		if (ClientGaps.hushes(sound.getCategory())) {
			ci.cancel();
		}
	}
}
