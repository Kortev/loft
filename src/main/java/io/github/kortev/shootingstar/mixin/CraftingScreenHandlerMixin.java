package io.github.kortev.shootingstar.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.kortev.shootingstar.OwnerOnly;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Only the owner can craft this mod's things ({@link OwnerOnly}): for anyone else, a crafting grid (a table's or their
 * own inventory's) with one of its recipes laid out in it makes nothing.
 */
@Mixin(CraftingScreenHandler.class)
public abstract class CraftingScreenHandlerMixin {
	@WrapOperation(method = "updateResult", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/recipe/CraftingRecipe;craft(Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/registry/RegistryWrapper$WrapperLookup;)Lnet/minecraft/item/ItemStack;"))
	private static ItemStack shootingstar$ownerOnly(CraftingRecipe recipe, RecipeInput input, RegistryWrapper.WrapperLookup lookup,
			Operation<ItemStack> original, ScreenHandler handler, World world, PlayerEntity player, RecipeInputInventory craftingInventory,
			CraftingResultInventory resultInventory, @Nullable RecipeEntry<CraftingRecipe> last) {
		ItemStack made = original.call(recipe, input, lookup);
		return OwnerOnly.isOurs(made) && !OwnerOnly.mayCraft(player) ? ItemStack.EMPTY : made;
	}
}
