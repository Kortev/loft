package io.github.kortev.chainsawman.form;

import com.mojang.serialization.Codec;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.PochitaItem;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Pochita's heart: whether a player has it, and so can pull the cord and can't die. They have it once Pochita has
 * become their heart ({@link #MERGED}, kept with them): carrying him when they die, the first time (kortev only). Then
 * he's gone into their chest for good, and they never need him again.
 */
public final class Heart {
	public static final AttachmentType<Boolean> MERGED = AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL)
			.copyOnDeath().buildAndRegister(ChainsawMan.id("heart"));

	private Heart() {
	}

	public static boolean has(PlayerEntity player) {
		return merged(player);
	}

	/** Whether {@code player}, dying now, becomes Chainsaw Man: kortev, carrying Pochita, not yet merged. */
	public static boolean merges(PlayerEntity player) {
		return !merged(player) && PochitaItem.carried(player) && player.getGameProfile().getName().equalsIgnoreCase(ChainsawMan.OWNER);
	}

	/**
	 * Pochita becomes {@code player}'s heart: out of their pack and into their chest; they're whole, full of blood, and
	 * the cord pulls itself.
	 */
	public static void merge(ServerPlayerEntity player) {
		for (int i = 0; i < player.getInventory().size(); i++) {
			if (player.getInventory().getStack(i).isOf(ChainsawMan.POCHITA)) {
				player.getInventory().removeStack(i, 1);
				break;
			}
		}
		player.setAttached(MERGED, true);
		player.setHealth(player.getMaxHealth());
		player.clearStatusEffects();
		player.extinguish();
		Blood.set(player, Blood.MAX);
		player.sendMessage(Text.translatable("message.chainsawman.merged").formatted(Formatting.GOLD), false);
		Form.pullCord(player);
	}

	public static boolean merged(PlayerEntity player) {
		return player.getAttachedOrElse(MERGED, false);
	}
}
