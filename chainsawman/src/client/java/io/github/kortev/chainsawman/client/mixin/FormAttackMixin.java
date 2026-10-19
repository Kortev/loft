package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ClientCombat;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In the form the attack key is a slash of the saws ({@link ClientCombat#attack}), not a punch: what it hits is the
 * server's to work out. On a block it still starts cutting through it, as mining. Down, it does nothing.
 */
@Mixin(MinecraftClient.class)
public abstract class FormAttackMixin {
	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void chainsawman$slash(CallbackInfoReturnable<Boolean> cir) {
		if (!ClientCombat.attack((MinecraftClient) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
