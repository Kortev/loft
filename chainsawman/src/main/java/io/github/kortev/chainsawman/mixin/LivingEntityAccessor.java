package io.github.kortev.chainsawman.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Whether a living thing's jump key is held: the game keeps it in a field nothing else may read, and for a rider on Beam
 * it is the leap ({@code Beam}). (A riding player's keys reach the server in the game's own input packet.)
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("jumping")
	boolean chainsawman$jumping();
}
