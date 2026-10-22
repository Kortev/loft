package io.github.kortev.chainsawman;

import java.util.List;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.entity.LivingEntity;
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

	/** Whether {@code player} carries him anywhere in their inventory. */
	public static boolean carried(PlayerEntity player) {
		return player.getInventory().contains(new ItemStack(ChainsawMan.POCHITA));
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
