package io.github.kortev.shootingstar.test.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How long the item a test's stand-in player raised has been held up: they never tick, so a shield never comes up by itself. */
@Mixin(LivingEntity.class)
public interface ItemUseAccessor {
	@Accessor("itemUseTimeLeft")
	void shootingstar$setItemUseTimeLeft(int left);
}
