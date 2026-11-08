package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.contract.ForeverFlags;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Locked in an endless moment you cannot attack, use items or dig (the server stops it too; this keeps the swing from showing). */
@Mixin(MinecraftClient.class)
public abstract class ForeverLockedActionMixin {
	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void chainsawman$noAttack(CallbackInfoReturnable<Boolean> cir) {
		if (chainsawman$locked()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
	private void chainsawman$noUse(CallbackInfo ci) {
		if (chainsawman$locked()) {
			ci.cancel();
		}
	}

	@Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
	private void chainsawman$noDig(boolean breaking, CallbackInfo ci) {
		if (chainsawman$locked()) {
			ci.cancel();
		}
	}

	private boolean chainsawman$locked() {
		MinecraftClient client = (MinecraftClient) (Object) this;
		return client.player != null && ForeverFlags.CLIENT.contains(client.player.getId());
	}
}
