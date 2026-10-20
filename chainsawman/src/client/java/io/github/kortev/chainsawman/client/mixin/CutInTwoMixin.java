package io.github.kortev.chainsawman.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.kortev.chainsawman.client.CutDown;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A mob the saws killed, drawn as its two halves coming apart ({@link CutDown}) instead of whole. */
@Mixin(LivingEntityRenderer.class)
public abstract class CutInTwoMixin {
	@WrapOperation(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"))
	private void chainsawman$cutInTwo(EntityModel<?> model, MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color,
			Operation<Void> original) {
		if (!CutDown.drawing()) {
			original.call(model, matrices, vertices, light, overlay, color);
			return;
		}
		CutDown.halves(model, matrices, () -> original.call(model, matrices, vertices, light, overlay, color));
	}
}
