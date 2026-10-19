package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.FormCinematic;
import io.github.kortev.chainsawman.client.FormHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Through your own transformation the HUD (hotbar, hearts, crosshair, chat) is not drawn, until the camera is back in
 * your eyes: only the cinematic's own impact frames and red ({@link FormHud}), which would otherwise come with the rest.
 */
@Mixin(InGameHud.class)
public abstract class FormHudHiddenMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void chainsawman$noHud(DrawContext context, RenderTickCounter counter, CallbackInfo ci) {
		if (FormCinematic.filming(counter.getTickDelta(true))) {
			FormHud.render(context, counter);
			ci.cancel();
		}
	}
}
