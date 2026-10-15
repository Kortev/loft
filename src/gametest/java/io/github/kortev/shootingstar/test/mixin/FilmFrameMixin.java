package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.shootingstar.test.FilmSelfTest;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A film is drawn at the Short's own size (1080x1920) whatever the size of the window it plays in, which on most
 * screens can't be that tall: the game's frame is that size from the start and doesn't follow the window.
 */
@Mixin(Window.class)
public abstract class FilmFrameMixin {
	@Shadow
	private int framebufferWidth;
	@Shadow
	private int framebufferHeight;

	@Inject(method = "updateFramebufferSize", at = @At("TAIL"))
	private void shootingstar$filmFrame(CallbackInfo ci) {
		if (FilmSelfTest.filming()) {
			framebufferWidth = FilmSelfTest.WIDTH;
			framebufferHeight = FilmSelfTest.HEIGHT;
		}
	}

	@Inject(method = "onFramebufferSizeChanged", at = @At("HEAD"), cancellable = true)
	private void shootingstar$keepFilmFrame(long window, int width, int height, CallbackInfo ci) {
		if (FilmSelfTest.filming()) {
			ci.cancel();
		}
	}
}
