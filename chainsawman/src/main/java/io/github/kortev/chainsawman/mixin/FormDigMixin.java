package io.github.kortev.chainsawman.mixin;

import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Saw;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In the form the saws chew through blocks {@link Saw#CUTS} times as fast as hands, as he tears through a building:
 * wood, stone, glass, walls. Not the very hard (obsidian and the like).
 */
@Mixin(PlayerEntity.class)
public abstract class FormDigMixin {
	@Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
	private void chainsawman$cut(BlockState state, CallbackInfoReturnable<Float> cir) {
		float hardness = state.getBlock().getHardness();
		if (hardness >= 0.0F && hardness < 10.0F && Form.in((PlayerEntity) (Object) this)) {
			cir.setReturnValue(cir.getReturnValue() * Saw.CUTS);
		}
	}
}
