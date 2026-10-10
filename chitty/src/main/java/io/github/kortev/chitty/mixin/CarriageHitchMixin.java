package io.github.kortev.chitty.mixin;

import io.github.kortev.chitty.carriage.CarriageEntity;
import io.github.kortev.chitty.carriage.CarriageHitch;
import net.minecraft.entity.passive.AbstractHorseEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Every horse, donkey and mule can be hitched to a carriage, and knows which (CarriageHitch). */
@Mixin(AbstractHorseEntity.class)
public abstract class CarriageHitchMixin implements CarriageHitch {
	@Unique
	@Nullable
	private CarriageEntity chitty$carriage;

	@Override
	@Nullable
	public CarriageEntity chitty$getCarriage() {
		return chitty$carriage;
	}

	@Override
	public void chitty$setCarriage(@Nullable CarriageEntity carriage) {
		chitty$carriage = carriage;
	}
}
