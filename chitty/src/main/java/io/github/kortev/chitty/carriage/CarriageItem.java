package io.github.kortev.chitty.carriage;

import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

/** Puts the Child Catcher's carriage down where you are looking, facing the way you face, waiting for a horse. */
public class CarriageItem extends Item {
	public CarriageItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		BlockHitResult hit = raycast(world, user, RaycastContext.FluidHandling.NONE);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return TypedActionResult.pass(stack);
		}
		CarriageEntity carriage = new CarriageEntity(Carriage.ENTITY, world);
		carriage.refreshPositionAndAngles(hit.getPos().x, hit.getPos().y, hit.getPos().z, user.getYaw(), 0.0F);
		if (!world.isSpaceEmpty(carriage, carriage.getBoundingBox())) {
			return TypedActionResult.fail(stack);
		}
		if (!world.isClient) {
			if (stack.contains(DataComponentTypes.CUSTOM_NAME)) {
				carriage.setCustomName(stack.getName());
			}
			world.spawnEntity(carriage);
			world.emitGameEvent(user, GameEvent.ENTITY_PLACE, hit.getPos());
			stack.decrementUnlessCreative(1, user);
		}
		user.incrementStat(Stats.USED.getOrCreateStat(this));
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		for (int i = 0; i < 3; i++) {
			tooltip.add(Text.translatable("item.shootingstar.carriage.tooltip." + i).formatted(Formatting.GRAY));
		}
	}
}
