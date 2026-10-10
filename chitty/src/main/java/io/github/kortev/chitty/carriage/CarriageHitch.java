package io.github.kortev.chitty.carriage;

import org.jetbrains.annotations.Nullable;

/**
 * A horse (any of the game's horses, donkeys and mules, CarriageHitchMixin) knows the carriage it was last hitched to,
 * which the carriage tells it every tick; CarriageEntity.hitchedTo says whether it still is.
 */
public interface CarriageHitch {
	@Nullable
	CarriageEntity chitty$getCarriage();

	void chitty$setCarriage(@Nullable CarriageEntity carriage);
}
