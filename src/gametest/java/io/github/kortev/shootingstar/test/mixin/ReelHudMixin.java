package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.shootingstar.test.ReelSelfTest;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In the reel, none of the game's own HUD (it's a film), but what the mods draw over the picture: the sounds lettered,
 * blood on the lens, the merge's bars.
 */
@Mixin(value = InGameHud.class, priority = 900)
public abstract class ReelHudMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void shootingstarTest$reelHud(DrawContext context, RenderTickCounter counter, CallbackInfo ci) {
		if (ReelSelfTest.active()) {
			HudRenderCallback.EVENT.invoker().onHudRender(context, counter);
			ci.cancel();
		}
	}
}
