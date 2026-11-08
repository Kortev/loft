package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.contract.ForeverFlags;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A thing locked in an endless moment (Forever) must be drawn exactly the same in every frame. The game draws a walking body from
 * {@code limbAnimator.getPos(tickDelta)}, which is {@code pos - speed * (1 - tickDelta)}, and animates idle arms and the like from
 * {@code age + tickDelta}: with nothing ticking, both would still sweep forward through every tick and snap back at the next, a stagger
 * twenty times a second. So while a locked thing is drawn its limb position is moved by exactly what that term takes away, and its
 * animation clock is its age with no partial tick.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class ForeverPoseMixin {
	@Unique
	private static float chainsawman$saved;
	@Unique
	private static boolean chainsawman$swapped;

	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
	private void chainsawman$holdLimbs(LivingEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light, CallbackInfo ci) {
		if (ForeverFlags.CLIENT.contains(entity.getId()) && !chainsawman$swapped) {
			LimbAnimatorAccessor limbs = (LimbAnimatorAccessor) (Object) entity.limbAnimator;
			chainsawman$saved = limbs.chainsawman$pos();
			limbs.chainsawman$setPos(chainsawman$saved + limbs.chainsawman$speed() * (1.0F - delta));
			chainsawman$swapped = true;
		}
	}

	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("RETURN"))
	private void chainsawman$releaseLimbs(LivingEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light, CallbackInfo ci) {
		if (chainsawman$swapped) {
			((LimbAnimatorAccessor) (Object) entity.limbAnimator).chainsawman$setPos(chainsawman$saved);
			chainsawman$swapped = false;
		}
	}

	@Inject(method = "getAnimationProgress", at = @At("RETURN"), cancellable = true)
	private void chainsawman$noSway(LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
		if (ForeverFlags.CLIENT.contains(entity.getId())) {
			cir.setReturnValue((float) entity.age);
		}
	}
}
