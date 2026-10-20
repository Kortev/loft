package io.github.kortev.chainsawman.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.kortev.chainsawman.client.MergeCinematic;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In the merge, as Pochita becomes your heart, your body is seen through: faint, him inside it ({@link MergeCinematic#seeThrough}). */
@Mixin(LivingEntityRenderer.class)
public abstract class SeeThroughMixin {
	@Unique
	private static boolean chainsawman$through;

	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"))
	private void chainsawman$which(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			net.minecraft.client.render.VertexConsumerProvider consumers, int light, CallbackInfo ci) {
		chainsawman$through = entity instanceof PlayerEntity player && MergeCinematic.seeThrough(player);
	}

	@Inject(method = "getRenderLayer", at = @At("RETURN"), cancellable = true)
	private void chainsawman$faint(LivingEntity entity, boolean showBody, boolean translucent, boolean showOutline,
			CallbackInfoReturnable<RenderLayer> cir) {
		if (chainsawman$through && cir.getReturnValue() != null) {
			@SuppressWarnings("unchecked")
			LivingEntityRenderer<LivingEntity, ?> renderer = (LivingEntityRenderer<LivingEntity, ?>) (Object) this;
			cir.setReturnValue(RenderLayer.getItemEntityTranslucentCull(renderer.getTexture(entity)));
		}
	}

	@WrapOperation(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"))
	private void chainsawman$drawFaint(EntityModel<?> model, MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color,
			Operation<Void> original) {
		original.call(model, matrices, vertices, light, overlay, chainsawman$through ? (0x50 << 24) | (color & 0xFFFFFF) : color);
	}
}
