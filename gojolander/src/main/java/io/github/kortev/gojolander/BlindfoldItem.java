package io.github.kortev.gojolander;

import java.util.List;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * A band of black cloth tied over the eyes. It goes where a helmet would and keeps out nothing a helmet would: wearing
 * it is going without one. On its owner it is what he sees through, and what he casts his Domain with (the Domain key,
 * Z unless rebound); on anyone else it is only cloth.
 */
public class BlindfoldItem extends Item implements Equipment {
	public BlindfoldItem(Settings settings) {
		super(settings);
	}

	/** Whether {@code player} has it on. */
	public static boolean wearing(PlayerEntity player) {
		return player.getEquippedStack(EquipmentSlot.HEAD).isOf(Gojolander.BLINDFOLD);
	}

	@Override
	public EquipmentSlot getSlotType() {
		return EquipmentSlot.HEAD;
	}

	@Override
	public RegistryEntry<SoundEvent> getEquipSound() {
		return SoundEvents.ITEM_ARMOR_EQUIP_LEATHER;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		return equipAndSwap(this, world, user, hand);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.gojolander.blindfold.tooltip.0").formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("item.gojolander.blindfold.tooltip.1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.gojolander.blindfold.tooltip.2").formatted(Formatting.DARK_GRAY));
	}
}
