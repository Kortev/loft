package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ClientCombat;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the form, right-clicking at nothing (or at a mob) is the lunge ({@link ClientCombat#use}), not a use of the hand. */
@Mixin(MinecraftClient.class)
public abstract class FormUseMixin {
	@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
	private void chainsawman$lunge(CallbackInfo ci) {
		if (ClientCombat.use((MinecraftClient) (Object) this)) {
			ci.cancel();
		}
	}
}
