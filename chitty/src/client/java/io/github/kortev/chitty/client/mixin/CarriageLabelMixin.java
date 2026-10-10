package io.github.kortev.chitty.client.mixin;

import io.github.kortev.chitty.carriage.CarriageEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the Child Catcher's carriage wears its disguise, nobody sees who is in its cage: its cloths hide them, and
 * this their names, which would show through.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class CarriageLabelMixin {
	@Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
	private void chitty$hiddenInTheCage(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (CarriageEntity.hidden(entity)) {
			cir.setReturnValue(false);
		}
	}
}
