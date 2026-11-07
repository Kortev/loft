package io.github.kortev.chainsawman.mixin;

import io.github.kortev.chainsawman.contract.Echo;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player's swing (the attack key, a click in the air or on something), told to {@link Echo}: the copies swing after it. */
@Mixin(LivingEntity.class)
public abstract class EchoSwingMixin {
	@Inject(method = "swingHand(Lnet/minecraft/util/Hand;Z)V", at = @At("HEAD"))
	private void chainsawman$swung(Hand hand, boolean fromServerPlayer, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayerEntity player) {
			Echo.swung(player);
		}
	}
}
