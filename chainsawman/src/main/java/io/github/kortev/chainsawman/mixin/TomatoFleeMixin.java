package io.github.kortev.chainsawman.mixin;

import io.github.kortev.chainsawman.devil.TomatoDevilEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.sensor.VillagerHostilesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Villagers see a Tomato Devil as danger (as they see a zombie) within {@link TomatoDevilEntity#VILLAGER_FLEE} blocks, and run from it. */
@Mixin(VillagerHostilesSensor.class)
public abstract class TomatoFleeMixin {
	@Inject(method = "matches", at = @At("HEAD"), cancellable = true)
	private void chainsawman$fleeTomato(LivingEntity villager, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (target instanceof TomatoDevilEntity) {
			cir.setReturnValue(target.squaredDistanceTo(villager) <= TomatoDevilEntity.VILLAGER_FLEE * TomatoDevilEntity.VILLAGER_FLEE);
		}
	}
}
