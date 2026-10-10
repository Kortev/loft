package io.github.kortev.chitty;

import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.tag.FluidTags;
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

/** Puts Chitty down where you are looking, on the ground or on the water, facing the way you face. */
public class ChittyItem extends Item {
	public ChittyItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		BlockHitResult hit = raycast(world, user, RaycastContext.FluidHandling.ANY);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return TypedActionResult.pass(stack);
		}
		ChittyEntity car = new ChittyEntity(Chitty.ENTITY, world);
		car.refreshPositionAndAngles(hit.getPos().x, hit.getPos().y, hit.getPos().z, user.getYaw(), 0.0F);
		if (!world.isSpaceEmpty(car, car.getBoundingBox())) {
			return TypedActionResult.fail(stack);
		}
		if (!world.isClient) {
			if (stack.contains(DataComponentTypes.CUSTOM_NAME)) {
				car.setCustomName(stack.getName());
			}
			if (world.getFluidState(hit.getBlockPos()).isIn(FluidTags.WATER)) {
				car.blowUpRaft();
			}
			world.spawnEntity(car);
			world.emitGameEvent(user, GameEvent.ENTITY_PLACE, hit.getPos());
			stack.decrementUnlessCreative(1, user);
		}
		user.incrementStat(Stats.USED.getOrCreateStat(this));
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		for (int i = 0; i < 3; i++) {
			tooltip.add(Text.translatable("item.shootingstar.chitty.tooltip." + i).formatted(Formatting.GRAY));
		}
	}
}
