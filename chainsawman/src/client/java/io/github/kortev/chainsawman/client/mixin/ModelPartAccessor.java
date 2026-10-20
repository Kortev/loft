package io.github.kortev.chainsawman.client.mixin;

import java.util.Map;
import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A model part's own parts, to cut a mob in two ({@code CutDown}). */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
	@Accessor("children")
	Map<String, ModelPart> chainsawman$children();
}
