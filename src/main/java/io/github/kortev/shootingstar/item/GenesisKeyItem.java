package io.github.kortev.shootingstar.item;

import io.github.kortev.shootingstar.gap.GapManager;
import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.registry.ModCriteria;
import io.github.kortev.shootingstar.registry.ModSounds;
import io.github.kortev.shootingstar.strike.Targeting;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Genesis Key. Turn it on a block to steer a second universe into ours there: it cracks, and the world is
 * erased into the void. Turn it once more, in the void, to let the world back in: it shatters.
 */
public class GenesisKeyItem extends Item {
	public static final int COOLDOWN = 40;

	public GenesisKeyItem(Settings settings) {
		super(settings);
	}

	/** Whether the key has been turned once already: cracked, with one turn left in it. */
	public static boolean cracked(ItemStack stack) {
		return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getBoolean("Cracked");
	}

	/** The crack taken out of it again: the event it was turned for was called off before it took the world. */
	public static void mend(ItemStack stack) {
		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.remove("Cracked");
		if (nbt.isEmpty()) {
			stack.remove(DataComponentTypes.CUSTOM_DATA);
		} else {
			stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
		}
	}

	private static void crack(ItemStack stack) {
		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putBoolean("Cracked", true);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		GapManager.Gap gone = world.isClient() ? null : GapManager.goneWith(world);
		if (cracked(stack)) {
			// One turn left in it, and only once the world is gone: it lets the world back in, and shatters.
			if (world instanceof ServerWorld serverWorld && user instanceof ServerPlayerEntity player) {
				GapManager.Gap gap = gone;
				if (gap == null) {
					deny(world, user, Text.translatable("message.shootingstar.gap.cracked"));
					return TypedActionResult.fail(stack);
				}
				// Not before whoever turned it is back in their own eyes, alone in the void; nor while the world is still coming apart.
				if (gap.age() < GapTimeline.RETURN) {
					return TypedActionResult.fail(stack);
				}
				if (!gap.unmade()) {
					deny(world, user, Text.translatable("message.shootingstar.gap.unmaking"));
					return TypedActionResult.fail(stack);
				}
				shatter(serverWorld, player, stack);
				GapManager.release(gap, serverWorld.getServer(), true);
				ModCriteria.fire(player, ModCriteria.GAP_RESTORED);
				user.incrementStat(Stats.USED.getOrCreateStat(this));
				user.setStackInHand(hand, ItemStack.EMPTY);
				return TypedActionResult.success(ItemStack.EMPTY, false);
			}
			return TypedActionResult.success(stack, true);
		}
		if (gone != null) {
			deny(world, user, Text.translatable("message.shootingstar.gap.void"));
			return TypedActionResult.fail(stack);
		}
		// One at a time: everyone on the server is in it.
		if (world instanceof ServerWorld && GapManager.running()) {
			deny(world, user, Text.translatable("message.shootingstar.gap.busy"));
			return TypedActionResult.fail(stack);
		}
		BlockPos target = Targeting.findTarget(world, user.getEyePos(), user.getRotationVec(1.0F), Targeting.MAX_RANGE);
		if (target == null || Vec3d.ofCenter(target).squaredDistanceTo(user.getEyePos()) < GapTimeline.MIN_RANGE * GapTimeline.MIN_RANGE) {
			deny(world, user, target == null ? Text.translatable("message.shootingstar.gap.no_target")
					: Text.translatable("message.shootingstar.gap.too_close", (int) GapTimeline.MIN_RANGE));
			return TypedActionResult.fail(stack);
		}
		if (world instanceof ServerWorld serverWorld && user instanceof ServerPlayerEntity player) {
			GapManager.launch(serverWorld, target, player);
			// The first turn cracks it.
			crack(stack);
			user.getItemCooldownManager().set(this, COOLDOWN);
			user.incrementStat(Stats.USED.getOrCreateStat(this));
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	private static void deny(World world, PlayerEntity user, Text message) {
		if (!world.isClient()) {
			user.sendMessage(message.copy().formatted(Formatting.AQUA), true);
			world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.UPLINK_DENIED, SoundCategory.PLAYERS, 0.7F, 1.4F);
		}
	}

	/** The key's last turn: it breaks apart in the hand, in a burst of its own shards and a ring of glass. */
	private static void shatter(ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
		Vec3d hand = player.getEyePos().add(player.getRotationVec(1.0F).multiply(0.7)).add(0.0, -0.2, 0.0);
		world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, stack.copy()), hand.x, hand.y, hand.z, 40, 0.15, 0.15, 0.15, 0.12);
		world.spawnParticles(ParticleTypes.END_ROD, hand.x, hand.y, hand.z, 24, 0.1, 0.1, 0.1, 0.08);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 1.0F, 0.7F);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0F, 0.6F);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.shootingstar.genesis_key.tooltip.0").formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("item.shootingstar.genesis_key.tooltip.1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.shootingstar.genesis_key.tooltip.2").formatted(Formatting.DARK_GRAY));
		if (cracked(stack)) {
			tooltip.add(Text.translatable("item.shootingstar.genesis_key.tooltip.cracked").formatted(Formatting.DARK_AQUA, Formatting.ITALIC));
		}
	}
}
