package io.github.kortev.chitty.client.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.HorseEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A horse model's body and head, for the carriage harness to sit on them as they move (CarriageHarnessFeature). */
@Mixin(HorseEntityModel.class)
public interface HorseModelAccess {
	@Accessor("body")
	ModelPart chitty$body();

	@Accessor("head")
	ModelPart chitty$head();
}
