package io.github.kortev.chainsawman.mixin;

import io.github.kortev.chainsawman.form.Buddies;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** With your buddy near, a hit on you is {@link Buddies#REDUCTION} smaller (before armour takes its share). */
@Mixin(PlayerEntity.class)
public abstract class BuddyDamageMixin {
	@ModifyVariable(method = "applyDamage", at = @At("HEAD"), argsOnly = true)
	private float chainsawman$buddyNear(float amount, DamageSource source) {
		return (Object) this instanceof ServerPlayerEntity player ? Buddies.damage(player, amount) : amount;
	}
}
