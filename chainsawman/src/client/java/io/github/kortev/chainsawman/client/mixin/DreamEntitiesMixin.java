package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.MergeCinematic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In the merge's dream there's nothing else: only you (and him, drawn by the merge itself). */
@Mixin(EntityRenderDispatcher.class)
public abstract class DreamEntitiesMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void chainsawman$alone(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (entity != client.player && MergeCinematic.dream(client.getRenderTickCounter().getTickDelta(true)) > 0.4F) {
			cir.setReturnValue(false);
		}
	}
}
