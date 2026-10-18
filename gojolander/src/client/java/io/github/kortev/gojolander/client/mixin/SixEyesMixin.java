package io.github.kortev.gojolander.client.mixin;

import io.github.kortev.gojolander.client.SixEyesClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** What the Six Eyes see is drawn outlined, through walls, as glowing things are ({@link SixEyesClient}). */
@Mixin(MinecraftClient.class)
public abstract class SixEyesMixin {
	@Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
	private void gojolander$sixEyes(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (SixEyesClient.sees(entity)) {
			cir.setReturnValue(true);
		}
	}
}
