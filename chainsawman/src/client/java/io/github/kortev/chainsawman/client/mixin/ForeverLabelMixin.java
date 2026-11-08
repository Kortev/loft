package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ForeverCopyRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A copy of a locked target (Forever) is drawn with the target's own renderer; it must not draw the target's name tag again. */
@Mixin(EntityRenderer.class)
public abstract class ForeverLabelMixin<T extends Entity> {
	@Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
	private void chainsawman$noLabel(T entity, Text text, MatrixStack matrices, VertexConsumerProvider provider, int light, float delta, CallbackInfo ci) {
		if (ForeverCopyRenderer.drawing) {
			ci.cancel();
		}
	}
}
