package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.gfx.WorldPasses;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Once the whole world is on the screen, a shader pack's last pass and all (which it draws as the world's drawing
 * returns), and before the depth is cleared for the hand: the passes over the world held back for it.
 */
@Mixin(GameRenderer.class)
public abstract class AfterWorldMixin {
	@Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", shift = At.Shift.AFTER))
	private void shootingstar$afterWorld(RenderTickCounter tickCounter, CallbackInfo ci) {
		WorldPasses.afterWorld();
	}
}
