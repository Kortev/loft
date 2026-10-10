package io.github.kortev.chitty.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Whether a living thing is jumping: for a player riding something, whether they hold the jump key (the server hears it
 * with their other movement keys). Someone hanging on the airship's grapple climbs its rope while they do.
 */
@Mixin(LivingEntity.class)
public interface AirshipJumper {
	@Accessor("jumping")
	boolean isChittyJumping();
}
