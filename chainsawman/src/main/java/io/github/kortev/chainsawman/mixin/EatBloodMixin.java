package io.github.kortev.chainsawman.mixin;

import io.github.kortev.chainsawman.form.Blood;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Eating, with Pochita's heart, makes blood ({@link Blood#ate}): how you get over fainting from the loss. */
@Mixin(PlayerEntity.class)
public abstract class EatBloodMixin {
	@Inject(method = "eatFood", at = @At("HEAD"))
	private void chainsawman$makeBlood(World world, ItemStack stack, FoodComponent food, CallbackInfoReturnable<ItemStack> cir) {
		if ((Object) this instanceof ServerPlayerEntity player) {
			Blood.ate(player, food.nutrition());
		}
	}
}
