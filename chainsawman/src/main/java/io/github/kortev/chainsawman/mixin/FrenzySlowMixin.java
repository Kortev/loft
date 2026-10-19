package io.github.kortev.chainsawman.mixin;

import io.github.kortev.chainsawman.form.Frenzy;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In a frenzy ({@link Frenzy}) nothing holds you back: cobwebs, berry bushes and powder snow don't slow you. */
@Mixin(Entity.class)
public abstract class FrenzySlowMixin {
	@Inject(method = "slowMovement", at = @At("HEAD"), cancellable = true)
	private void chainsawman$notSlowed(BlockState state, Vec3d multiplier, CallbackInfo ci) {
		if ((Object) this instanceof PlayerEntity player && Frenzy.is(player)) {
			ci.cancel();
		}
	}
}
