package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.FormCinematic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Through your own transformation no pop-ups (advancements, tutorial hints) are drawn either; they wait until it is over. */
@Mixin(ToastManager.class)
public abstract class FormToastHiddenMixin {
	@Inject(method = "draw", at = @At("HEAD"), cancellable = true)
	private void chainsawman$noToasts(DrawContext context, CallbackInfo ci) {
		if (FormCinematic.filming(MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true))) {
			ci.cancel();
		}
	}
}
