package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.FormCinematic;
import io.github.kortev.chainsawman.form.Downed;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Through your own transformation your hand (and what it holds) is not drawn, until the camera is back in your eyes;
 * nor while you are down, your arms gone.
 */
@Mixin(GameRenderer.class)
public abstract class FormHandHiddenMixin {
	@Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
	private void chainsawman$noHand(Camera camera, float tickDelta, Matrix4f matrix, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (FormCinematic.filming(tickDelta) || client.player != null && Downed.is(client.player)) {
			ci.cancel();
		}
	}
}
