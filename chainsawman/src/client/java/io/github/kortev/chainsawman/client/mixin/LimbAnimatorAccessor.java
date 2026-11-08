package io.github.kortev.chainsawman.client.mixin;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A limb animator's stride, to draw a copy of a mob in a stride of its own (Forever). */
@Mixin(LimbAnimator.class)
public interface LimbAnimatorAccessor {
	@Accessor("pos")
	float chainsawman$pos();

	@Accessor("pos")
	void chainsawman$setPos(float pos);

	@Accessor("speed")
	float chainsawman$speed();

	@Accessor("speed")
	void chainsawman$setSpeed(float speed);

	@Accessor("prevSpeed")
	float chainsawman$prevSpeed();

	@Accessor("prevSpeed")
	void chainsawman$setPrevSpeed(float speed);
}
