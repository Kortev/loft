package io.github.kortev.chainsawman.mixin;

import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Merge;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Down ({@link Downed}), or lying dead while Pochita becomes your heart ({@link Merge}), you're nothing for a mob to go
 * after: none takes you for a target, and any that had you gives up (their goals ask this each tick).
 */
@Mixin(PlayerEntity.class)
public abstract class DownedUnseenMixin {
	@Inject(method = "canTakeDamage", at = @At("HEAD"), cancellable = true)
	private void chainsawman$notWorthIt(CallbackInfoReturnable<Boolean> cir) {
		PlayerEntity player = (PlayerEntity) (Object) this;
		if (!player.getWorld().isClient && (Downed.is(player) || Merge.is(player))) {
			cir.setReturnValue(false);
		}
	}
}
