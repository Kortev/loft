package io.github.kortev.shootingstar.item;

import io.github.kortev.shootingstar.registry.ModSounds;
import io.github.kortev.shootingstar.strike.StrikeManager;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import io.github.kortev.shootingstar.strike.Targeting;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** The handheld uplink. Use it on a block to call SS-03 Gungnir down on it. */
public class UplinkItem extends Item {
	public static final int COOLDOWN = StrikeTimeline.IMPACT + 60;

	public UplinkItem(Settings settings) {
		super(settings);
	}

	/** The block the player is aiming at, or null when there is no firing solution. */
	@Nullable
	public static BlockPos aim(PlayerEntity player) {
		return Targeting.findTarget(player.getWorld(), player.getEyePos(), player.getRotationVec(1.0F), Targeting.MAX_RANGE);
	}

	public static boolean dangerClose(PlayerEntity player, BlockPos target) {
		return Vec3d.ofCenter(target).squaredDistanceTo(player.getEyePos()) < Targeting.MIN_RANGE * Targeting.MIN_RANGE;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		BlockPos target = aim(user);
		if (target == null || dangerClose(user, target)) {
			if (!world.isClient()) {
				String key = target == null ? "message.shootingstar.no_solution" : "message.shootingstar.danger_close";
				user.sendMessage(Text.translatable(key).formatted(Formatting.RED), true);
				world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.UPLINK_DENIED, SoundCategory.PLAYERS, 0.7F, 1.0F);
			}
			return TypedActionResult.fail(stack);
		}
		if (world instanceof ServerWorld serverWorld && user instanceof ServerPlayerEntity player) {
			if (StrikeManager.isBusy(player.getUuid())) {
				user.sendMessage(Text.translatable("message.shootingstar.cycling").formatted(Formatting.GOLD), true);
				return TypedActionResult.fail(stack);
			}
			StrikeManager.launch(serverWorld, target, player);
			user.getItemCooldownManager().set(this, COOLDOWN);
			user.incrementStat(Stats.USED.getOrCreateStat(this));
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.shootingstar.gungnir_uplink.tooltip.0").formatted(Formatting.GOLD));
		tooltip.add(Text.translatable("item.shootingstar.gungnir_uplink.tooltip.1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.shootingstar.gungnir_uplink.tooltip.2").formatted(Formatting.DARK_GRAY));
	}
}
