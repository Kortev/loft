package io.github.kortev.gojolander.client.mixin;

import io.github.kortev.gojolander.client.SixEyesClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Seen by the Six Eyes, things are outlined in the blue of his eyes; anything truly glowing, or on a team with a colour,
 * keeps its own.
 */
@Mixin(Entity.class)
public abstract class SixEyesColorMixin {
	@Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
	private void gojolander$sixEyesBlue(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self.getWorld().isClient() && !self.isGlowing() && self.getScoreboardTeam() == null && SixEyesClient.sees(self)) {
			cir.setReturnValue(SixEyesClient.BLUE);
		}
	}
}
