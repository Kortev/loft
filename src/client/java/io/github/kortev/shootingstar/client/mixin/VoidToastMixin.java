package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.gap.ClientGaps;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No toasts while a Ginnungagap event has the screen (the key turning, the black, the rebuild): they wait, and show
 * when it is over (a toast's time only starts when it is first drawn).
 */
@Mixin(ToastManager.class)
public abstract class VoidToastMixin {
	@Inject(method = "draw", at = @At("HEAD"), cancellable = true)
	private void shootingstar$later(DrawContext context, CallbackInfo ci) {
		if (ClientGaps.mine() != null || ClientGaps.floating() || ClientGaps.rebuilding() != null) {
			ci.cancel();
		}
	}
}
