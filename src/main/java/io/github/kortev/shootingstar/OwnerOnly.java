package io.github.kortev.shootingstar;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * This is kortev's own mod on a server where everyone has their own: only kortev can craft its things (the Genesis
 * Key, Gungnir's uplink and the rest, and Chitty from her own jar, whose things share this namespace). Everyone can use
 * them once made, ride in them, and break them. Crafters, which have nobody to ask, do not make them at all.
 */
public final class OwnerOnly {
	/** The Minecraft name of the one player who can craft this mod's things. */
	public static final String OWNER = "kortev";

	private OwnerOnly() {
	}

	/** Whether this is one of this mod's things. */
	public static boolean isOurs(ItemStack stack) {
		return !stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).getNamespace().equals(ShootingStar.MOD_ID);
	}

	/** Whether the player may craft this mod's things. */
	public static boolean mayCraft(PlayerEntity player) {
		return player.getGameProfile().getName().equalsIgnoreCase(OWNER);
	}
}
