package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.shootingstar.test.FilmSelfTest;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The film's player's view, turned every frame as a mouse turns it. The game draws its own player's look as it is,
 * never in between ticks (a mouse moves it every frame anyway), so a look set once a tick stood still for one frame in
 * three of the film.
 */
@Mixin(GameRenderer.class)
public abstract class FilmLookMixin {
	@Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("HEAD"))
	private void shootingstarTest$look(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
		FilmSelfTest.frame(tickCounter.getTickDelta(true));
	}
}
