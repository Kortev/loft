package io.github.kortev.chainsawman.mixin;

import io.github.kortev.chainsawman.contract.ForeverFlags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A locked player cannot attack (the server's say, however the client behaves). */
@Mixin(PlayerEntity.class)
public abstract class ForeverAttackMixin {
	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void chainsawman$noAttack(Entity target, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayerEntity player && (ForeverFlags.SERVER.contains(player.getId()) || io.github.kortev.chainsawman.contract.Forever.notYet(player))) {
			ci.cancel();
		}
	}
}
