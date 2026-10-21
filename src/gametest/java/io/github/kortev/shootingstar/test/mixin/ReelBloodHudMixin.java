package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.chainsawman.client.BloodHud;
import io.github.kortev.shootingstar.test.ReelSelfTest;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No blood bar in the reel (ReelSelfTest): it's a film, and you're only playing Denji in it. */
@Mixin(value = BloodHud.class, remap = false)
public abstract class ReelBloodHudMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private static void shootingstarTest$noBar(DrawContext ctx, RenderTickCounter counter, CallbackInfo ci) {
		if (ReelSelfTest.active()) {
			ci.cancel();
		}
	}
}
