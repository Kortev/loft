package io.github.kortev.shootingstar.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.gap.ClientGaps;
import io.github.kortev.shootingstar.client.gap.GapCamera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No clouds over a Ginnungagap: the camera works below them, and the other universe's sky takes their place. Through
 * the rebuild they are faded from a camera up by them (GapCamera.cloudFade): the cloud shader takes its opacity from the
 * shader colour, and drops what is all but clear, depth and all.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
	@Unique
	private boolean shootingstar$faded;

	@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
	private void shootingstar$noClouds(CallbackInfo ci) {
		if (ClientGaps.cloudless()) {
			ci.cancel();
			return;
		}
		float fade = GapCamera.cloudFade(MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true));
		if (fade <= 0.0F) {
			ci.cancel();
		} else if (fade < 1.0F) {
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, fade);
			shootingstar$faded = true;
		}
	}

	@Inject(method = "renderClouds", at = @At("RETURN"), require = 0)
	private void shootingstar$cloudsDrawn(CallbackInfo ci) {
		if (shootingstar$faded) {
			shootingstar$faded = false;
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		}
	}
}
