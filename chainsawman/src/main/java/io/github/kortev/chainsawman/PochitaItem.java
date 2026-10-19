package io.github.kortev.chainsawman;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Pochita, the little chainsaw devil. Carried anywhere in the inventory, he is what the cord starts. */
public class PochitaItem extends Item {
	public PochitaItem(Settings settings) {
		super(settings);
	}

	/** Whether {@code player} carries him anywhere in their inventory. */
	public static boolean carried(PlayerEntity player) {
		return player.getInventory().contains(new ItemStack(ChainsawMan.POCHITA));
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.chainsawman.pochita.tooltip.0").formatted(Formatting.GOLD));
		tooltip.add(Text.translatable("item.chainsawman.pochita.tooltip.1").formatted(Formatting.GRAY));
	}
}
