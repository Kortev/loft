package io.github.kortev.chitty.mixin;

import io.github.kortev.chitty.carriage.CarriageEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A horse in a carriage's shafts does nothing of its own accord: no wandering, grazing or looking about (and nothing to
 * put back when it is unhitched, unlike NoAI, which a horse might have been given on purpose).
 */
@Mixin(MobEntity.class)
public abstract class CarriageHorseAiMixin {
	@Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
	private void chitty$inHarness(CallbackInfo ci) {
		if (CarriageEntity.hitchedTo((MobEntity) (Object) this) != null) {
			ci.cancel();
		}
	}
}
