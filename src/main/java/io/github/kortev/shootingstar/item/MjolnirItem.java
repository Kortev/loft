package io.github.kortev.shootingstar.item;

import io.github.kortev.shootingstar.registry.ModGameRules;
import io.github.kortev.shootingstar.registry.ModSounds;
import io.github.kortev.shootingstar.strike.Targeting;
import io.github.kortev.shootingstar.thunder.ThunderManager;
import io.github.kortev.shootingstar.thunder.ThunderTimeline;
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

/**
 * Mjölnir, Þ-01: a short-hafted hammer that calls the whole planet's thunderstorms down on one spot. Raise it at a block
 * far enough away and the storm gathers over it for eighteen seconds, then the bolt comes down.
 */
public class MjolnirItem extends Item {
	public static final int COOLDOWN = ThunderTimeline.STROKE + 60;

	public MjolnirItem(Settings settings) {
		super(settings);
	}

	/** The block the player is aiming at, or null when the hammer finds nothing to strike. */
	@Nullable
	public static BlockPos aim(PlayerEntity player) {
		return Targeting.findTarget(player.getWorld(), player.getEyePos(), player.getRotationVec(1.0F), ThunderTimeline.MAX_RANGE);
	}

	/** Closest a target may be, from the game rule on the server and the default on the client. */
	public static double minRange(World world) {
		int radius = world instanceof ServerWorld server ? server.getGameRules().getInt(ModGameRules.MJOLNIR_RADIUS)
				: ThunderTimeline.DEFAULT_RADIUS;
		return ThunderTimeline.minRange(radius);
	}

	/** Why the hammer will not strike {@code target} from {@code eye}, or null when it will. */
	@Nullable
	public static Text refusal(@Nullable BlockPos target, Vec3d eye, double minRange) {
		if (target == null) {
			return Text.translatable("message.shootingstar.mjolnir.no_target");
		}
		if (Vec3d.ofCenter(target).squaredDistanceTo(eye) < minRange * minRange) {
			return Text.translatable("message.shootingstar.mjolnir.too_close", (int) minRange);
		}
		return null;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		BlockPos target = aim(user);
		Text refused = refusal(target, user.getEyePos(), minRange(world));
		if (refused != null) {
			if (!world.isClient()) {
				user.sendMessage(refused.copy().formatted(Formatting.RED), true);
				world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.MJOLNIR_DENIED, SoundCategory.PLAYERS, 0.8F, 1.0F);
			}
			return TypedActionResult.fail(stack);
		}
		if (world instanceof ServerWorld serverWorld && user instanceof ServerPlayerEntity player) {
			if (ThunderManager.isBusy(player.getUuid())) {
				user.sendMessage(Text.translatable("message.shootingstar.mjolnir.gathering").formatted(Formatting.AQUA), true);
				return TypedActionResult.fail(stack);
			}
			ThunderManager.launch(serverWorld, target, player);
			user.getItemCooldownManager().set(this, COOLDOWN);
			user.incrementStat(Stats.USED.getOrCreateStat(this));
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.shootingstar.mjolnir.tooltip.0").formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("item.shootingstar.mjolnir.tooltip.1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.shootingstar.mjolnir.tooltip.2").formatted(Formatting.DARK_GRAY));
	}
}
