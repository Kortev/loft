package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ClientCombat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Getting up after being down, the game's own crawl doesn't go on leaning the body: the get-up has it ({@code GetUpMixin}). */
@Mixin(LivingEntity.class)
public abstract class GetUpLeanMixin {
	@Inject(method = "getLeaningPitch", at = @At("HEAD"), cancellable = true)
	private void chainsawman$upright(float tickDelta, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof PlayerEntity player && player.getWorld().isClient && ClientCombat.gettingUp(player, tickDelta) >= 0.0F) {
			cir.setReturnValue(0.0F);
		}
	}
}
