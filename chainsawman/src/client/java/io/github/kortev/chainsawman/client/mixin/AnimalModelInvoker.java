package io.github.kortev.chainsawman.client.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.AnimalModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The parts of a mob's model (head and body), to cut it in two ({@code CutDown}). */
@Mixin(AnimalModel.class)
public interface AnimalModelInvoker {
	@Invoker("getHeadParts")
	Iterable<ModelPart> chainsawman$headParts();

	@Invoker("getBodyParts")
	Iterable<ModelPart> chainsawman$bodyParts();
}
