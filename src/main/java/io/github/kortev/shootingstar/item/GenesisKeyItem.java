package io.github.kortev.shootingstar.item;

import io.github.kortev.shootingstar.gap.GapManager;
import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.registry.ModSounds;
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

/**
 * The Genesis Key. Turn it on a block to steer a second universe into ours there; turn it again, once nothing
 * is left, to let reality back in.
 */
public class GenesisKeyItem extends Item {
	public static final int COOLDOWN = 40;

	public GenesisKeyItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world instanceof ServerWorld serverWorld && user instanceof ServerPlayerEntity player) {
			GapManager.Gap holding = GapManager.holding(player.getUuid());
			if (holding != null) {
				GapManager.release(holding, serverWorld.getServer());
				user.getItemCooldownManager().set(this, COOLDOWN);
				return TypedActionResult.success(stack, false);
			}
			if (GapManager.isBusy(player.getUuid())) {
				return TypedActionResult.fail(stack);
			}
		}
		BlockPos target = Targeting.findTarget(world, user.getEyePos(), user.getRotationVec(1.0F), Targeting.MAX_RANGE);
		if (target == null || Vec3d.ofCenter(target).squaredDistanceTo(user.getEyePos()) < GapTimeline.MIN_RANGE * GapTimeline.MIN_RANGE) {
			if (!world.isClient()) {
				Text message = target == null ? Text.translatable("message.shootingstar.gap.no_target")
						: Text.translatable("message.shootingstar.gap.too_close", (int) GapTimeline.MIN_RANGE);
				user.sendMessage(message.copy().formatted(Formatting.AQUA), true);
				world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.UPLINK_DENIED, SoundCategory.PLAYERS, 0.7F, 1.4F);
			}
			return TypedActionResult.fail(stack);
		}
		if (world instanceof ServerWorld serverWorld && user instanceof ServerPlayerEntity player) {
			GapManager.launch(serverWorld, target, player);
			user.getItemCooldownManager().set(this, COOLDOWN);
			user.incrementStat(Stats.USED.getOrCreateStat(this));
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.shootingstar.genesis_key.tooltip.0").formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("item.shootingstar.genesis_key.tooltip.1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.shootingstar.genesis_key.tooltip.2").formatted(Formatting.DARK_GRAY));
	}
}
