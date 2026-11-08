package io.github.kortev.chainsawman.hotel;

import io.github.kortev.chainsawman.form.Blood;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/** A bottle of the Eternity Devil's blood, from its death: drunk, it refills Chainsaw Man's blood bar completely. */
public class EternityBloodItem extends Item {
	public EternityBloodItem(Settings settings) {
		super(settings);
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.DRINK;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return 32;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		return ItemUsage.consumeHeldItem(world, user, hand);
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (user instanceof ServerPlayerEntity player) {
			Blood.set(player, Blood.MAX);
			player.sendMessage(Text.translatable("message.chainsawman.blood_full").formatted(Formatting.DARK_RED), true);
		}
		world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.8F, 0.8F);
		if (user instanceof PlayerEntity player) {
			return player.isCreative() ? stack : ItemUsage.exchangeStack(stack, player, new ItemStack(Items.GLASS_BOTTLE));
		}
		stack.decrement(1);
		return stack;
	}
}
