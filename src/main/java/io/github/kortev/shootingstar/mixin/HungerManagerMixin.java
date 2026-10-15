package io.github.kortev.shootingstar.mixin;

import io.github.kortev.shootingstar.gap.VoidFloor;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hunger stands still for a player held on the floor of nothing, from the black until they are home: no food used,
 * nothing healed by it, no starving.
 */
@Mixin(HungerManager.class)
public abstract class HungerManagerMixin {
	@Inject(method = "update", at = @At("HEAD"), cancellable = true)
	private void shootingstar$heldStill(PlayerEntity player, CallbackInfo ci) {
		if (!player.getWorld().isClient() && VoidFloor.floating(player)) {
			ci.cancel();
		}
	}
}
