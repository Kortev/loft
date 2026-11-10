package io.github.kortev.chainsawman;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Uuids;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3f;

/**
 * Pochita picked up ({@link PochitaEntity}): in your hands, a chainsaw to fight with (about an iron axe's hit, quicker,
 * revving and throwing blood). Used on the ground, he's set down again, yours. Carried when you die, he becomes your
 * heart.
 */
public class PochitaItem extends Item {
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.62F, 0.02F, 0.04F), 1.0F);

	public PochitaItem(Settings settings) {
		super(settings);
	}

	/**
	 * As a tool: through wood and plants as a chainsaw goes through a tree (kortev, 2026-10-22). (As a weapon he isn't
	 * swung: held in both hands, holding the attack key revs him up and cuts, Saw.HELD_BITE.)
	 */
	public static ToolComponent tool() {
		return new ToolComponent(List.of(ToolComponent.Rule.of(BlockTags.AXE_MINEABLE, 14.0F), ToolComponent.Rule.of(BlockTags.LEAVES, 15.0F),
				ToolComponent.Rule.of(BlockTags.SWORD_EFFICIENT, 15.0F), ToolComponent.Rule.of(BlockTags.HOE_MINEABLE, 10.0F)), 1.0F, 0);
	}

	/** Who tamed him (a player's id), kept with the item: he is theirs in anyone's hands. */
	public static final ComponentType<UUID> TAMER = Registry.register(Registries.DATA_COMPONENT_TYPE, ChainsawMan.id("tamer"),
			ComponentType.<UUID>builder().codec(Uuids.INT_STREAM_CODEC).packetCodec(Uuids.PACKET_CODEC).build());
	/** The den he came from (a block position), kept with the item as it is with him: lost, he goes back there. */
	public static final ComponentType<Long> DEN = Registry.register(Registries.DATA_COMPONENT_TYPE, ChainsawMan.id("den"),
			ComponentType.<Long>builder().codec(Codec.LONG).packetCodec(PacketCodecs.VAR_LONG).build());

	/** Whether {@code player} carries their own Pochita anywhere in their inventory (one tamed by someone else doesn't count). */
	public static boolean carried(PlayerEntity player) {
		return slot(player) >= 0;
	}

	/** The inventory slot of {@code player}'s own Pochita, or -1. One nobody has tamed yet (an old item, a given one) becomes theirs. */
	public static int slot(PlayerEntity player) {
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (stack.isOf(ChainsawMan.POCHITA) && own(stack, player)) {
				return i;
			}
		}
		return -1;
	}

	/** Whether {@code stack} is {@code player}'s Pochita; with no tamer yet, he takes them (on the server). */
	private static boolean own(ItemStack stack, PlayerEntity player) {
		UUID tamer = stack.get(TAMER);
		if (tamer == null) {
			if (!player.getWorld().isClient) {
				stack.set(TAMER, player.getUuid());
			}
			return true;
		}
		return tamer.equals(player.getUuid());
	}

	/**
	 * In someone's inventory: he takes them if nobody has tamed him; in anyone else's but his tamer's he jumps out (at once) as his tamed
	 * self, still theirs; and once every heart is taken ({@code chainsawHearts}) he jumps out wild.
	 */
	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (!(world instanceof ServerWorld server) || !(entity instanceof PlayerEntity player) || stack.isEmpty()) {
			return;
		}
		if (world.getTime() % 20 == 0 && io.github.kortev.chainsawman.form.Hearts.full(server.getServer())) {
			jumpOut(server, player, stack, null);
			return;
		}
		if (!own(stack, player)) {
			jumpOut(server, player, stack, stack.get(TAMER));
		}
	}

	/** He leaves {@code holder}'s inventory and stands beside them: tamed by {@code tamer} (or wild, if null), from the same den. */
	private static void jumpOut(ServerWorld world, PlayerEntity holder, ItemStack stack, @Nullable UUID tamer) {
		PochitaEntity pochita = ChainsawMan.POCHITA_ENTITY.create(world);
		if (pochita == null) {
			return;
		}
		pochita.refreshPositionAndAngles(holder.getX(), holder.getY(), holder.getZ(), holder.getYaw(), 0.0F);
		if (tamer != null) {
			pochita.setOwnerUuid(tamer);
			pochita.setTamed(true, true);
		}
		Long den = stack.get(DEN);
		pochita.den = den == null ? null : BlockPos.fromLong(den);
		stack.setCount(0);
		world.spawnEntity(pochita);
		world.playSound(null, holder.getX(), holder.getY(), holder.getZ(), net.minecraft.sound.SoundEvents.ENTITY_WOLF_WHINE, SoundCategory.NEUTRAL, 0.8F, 1.2F);
	}

	/** {@code pochita} picked up by {@code player}, his tamer: the item, his tamer and his den on it. */
	public static ItemStack of(PochitaEntity pochita, PlayerEntity player) {
		ItemStack item = new ItemStack(ChainsawMan.POCHITA);
		item.set(TAMER, player.getUuid());
		if (pochita.den != null) {
			item.set(DEN, pochita.den.asLong());
		}
		return item;
	}

	/** Burnt up, cut to pieces by a cactus, blown up: he is really lost, and a den gets him again before long. */
	@Override
	public void onItemEntityDestroyed(ItemEntity entity) {
		lostFrom(entity);
	}

	/** {@code entity}, a dropped Pochita, is gone for good. */
	static void lostFrom(ItemEntity entity) {
		if (entity.getWorld() instanceof ServerWorld world) {
			Long den = entity.getStack().get(DEN);
			PochitaPlace.lost(world.getServer(), den == null ? null : BlockPos.fromLong(den));
		}
	}

	/** A dropped Pochita never despawns from age; one that falls into the void, or is killed by a command, is lost (and a den gets him). */
	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof ItemEntity item && item.getStack().isOf(ChainsawMan.POCHITA)) {
				item.setNeverDespawn();
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof ItemEntity item && item.getStack().isOf(ChainsawMan.POCHITA) && entity.getRemovalReason() != null
					&& (entity.getRemovalReason() == Entity.RemovalReason.KILLED
							|| entity.getRemovalReason().shouldDestroy() && entity.getY() < world.getBottomY())) {
				lostFrom(item);
			}
		});
	}

	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (attacker.getWorld() instanceof ServerWorld world) {
			world.playSound(null, attacker.getX(), attacker.getY() + 1.0, attacker.getZ(), ChainsawSounds.HIT, SoundCategory.PLAYERS, 0.7F, 1.3F);
			world.spawnParticles(BLOOD, target.getX(), target.getBodyY(0.6), target.getZ(), 10, 0.25, 0.25, 0.25, 0.1);
		}
		return true;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (!(context.getWorld() instanceof ServerWorld world) || context.getPlayer() == null) {
			return ActionResult.SUCCESS;
		}
		BlockPos at = context.getBlockPos().offset(context.getSide());
		PochitaEntity pochita = ChainsawMan.POCHITA_ENTITY.create(world);
		if (pochita == null) {
			return ActionResult.FAIL;
		}
		pochita.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, context.getPlayerYaw(), 0.0F);
		pochita.setOwner(context.getPlayer());
		pochita.setTamed(true, true);
		Long den = context.getStack().get(DEN);
		pochita.den = den == null ? null : BlockPos.fromLong(den);
		world.spawnEntity(pochita);
		context.getStack().decrementUnlessCreative(1, context.getPlayer());
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.chainsawman.pochita.tooltip.0").formatted(Formatting.GOLD));
		tooltip.add(Text.translatable("item.chainsawman.pochita.tooltip.1").formatted(Formatting.GRAY));
	}
}
