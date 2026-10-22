package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.shootingstar.test.FilmSelfTest;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** While filming, the frame (1080x1920) is shown scaled to fit the window, however small, so it can be watched. */
@Mixin(MinecraftClient.class)
public abstract class FilmPreviewMixin {
	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;draw(II)V"), require = 0)
	private void shootingstar$fitPreview(Framebuffer framebuffer, int width, int height) {
		if (FilmSelfTest.filming()) {
			int[] w = new int[1];
			int[] h = new int[1];
			GLFW.glfwGetFramebufferSize(((MinecraftClient) (Object) this).getWindow().getHandle(), w, h);
			if (w[0] > 0 && h[0] > 0) {
				framebuffer.draw(w[0], h[0]);
				return;
			}
		}
		framebuffer.draw(width, height);
	}
}
