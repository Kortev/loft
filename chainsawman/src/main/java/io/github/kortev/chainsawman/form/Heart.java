package io.github.kortev.chainsawman.form;

import com.mojang.serialization.Codec;
import io.github.kortev.chainsawman.ChainsawMan;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Pochita's heart: whether a player has it, and so can pull the cord and can't die. They have it once Pochita has
 * become their heart ({@link #MERGED}, kept with them): dying the first time with him carried or near ({@link Merge}).
 * Then he's gone into their chest for good, and they never need him again.
 */
public final class Heart {
	public static final AttachmentType<Boolean> MERGED = AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL)
			.copyOnDeath().buildAndRegister(ChainsawMan.id("heart"));

	private Heart() {
	}

	public static boolean has(PlayerEntity player) {
		return merged(player);
	}

	public static boolean merged(PlayerEntity player) {
		return player.getAttachedOrElse(MERGED, false);
	}
}
