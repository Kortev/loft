package io.github.kortev.shootingstar.mixin;

import io.github.kortev.shootingstar.OwnerOnly;
import java.util.Optional;
import net.minecraft.block.CrafterBlock;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A crafter has nobody to ask whether they are the owner ({@link OwnerOnly}), so it never makes this mod's things. */
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {
	@Inject(method = "getCraftingRecipe", at = @At("RETURN"), cancellable = true)
	private static void shootingstar$notOurs(World world, CraftingRecipeInput input, CallbackInfoReturnable<Optional<RecipeEntry<CraftingRecipe>>> cir) {
		Optional<RecipeEntry<CraftingRecipe>> found = cir.getReturnValue();
		if (found.isPresent() && OwnerOnly.isOurs(found.get().value().getResult(world.getRegistryManager()))) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
