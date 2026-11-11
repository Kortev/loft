package io.github.kortev.chainsawman.mixin;

import io.github.kortev.chainsawman.devil.Devils;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.IronGolemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Iron golems leave a Tomato Devil alone (it is the players' fight; it leaves them alone too). */
@Mixin(IronGolemEntity.class)
public abstract class GolemIgnoresTomatoMixin {
	@Inject(method = "canTarget(Lnet/minecraft/entity/EntityType;)Z", at = @At("HEAD"), cancellable = true)
	private void chainsawman$ignoreTomato(EntityType<?> type, CallbackInfoReturnable<Boolean> cir) {
		if (type == Devils.TOMATO_DEVIL) {
			cir.setReturnValue(false);
		}
	}
}
