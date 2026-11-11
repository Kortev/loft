package io.github.kortev.chainsawman.safety;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** The hunter's licence: using it shows the card. What is on it is saved on the player, not on the card. */
public class LicenceItem extends Item {
	public LicenceItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		if (user instanceof ServerPlayerEntity sp) {
			Safety.show(sp);
		}
		return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
	}
}
