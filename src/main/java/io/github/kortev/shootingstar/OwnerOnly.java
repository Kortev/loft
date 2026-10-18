package io.github.kortev.shootingstar;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.jetbrains.annotations.Nullable;

/**
 * This is kortev's own mod on a server where everyone has their own: only kortev can craft its things (the Genesis
 * Key, Gungnir's uplink and the rest, and Chitty from her own jar, whose things share this namespace). Everyone can use
 * them once made, ride in them, and break them. Crafters, which have nobody to ask, do not make them at all.
 *
 * <p>The other players' mods built beside this one hold to the same rule for their own owner: each names its namespace
 * and whose it is with {@link #register}, and then only that player crafts anything in it.
 */
public final class OwnerOnly {
	/** The Minecraft name of the one player who can craft this mod's things. */
	public static final String OWNER = "kortev";
	/** Whose each namespace's things are: this mod's kortev's, and each other mod's whoever it was made for. */
	private static final Map<String, Supplier<String>> OWNERS = new ConcurrentHashMap<>(Map.of(ShootingStar.MOD_ID, () -> OWNER));

	private OwnerOnly() {
	}

	/**
	 * Puts every item in {@code namespace} under this rule, for the player {@code owner} names when asked (asked each
	 * time, so a mod can let its owner be changed while the server runs; null while it has none, and anyone may craft).
	 */
	public static void register(String namespace, Supplier<String> owner) {
		OWNERS.put(namespace, owner);
	}

	/** Whose thing this is, if it is one that only its owner crafts; null for anything else. */
	@Nullable
	public static String ownerOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		Supplier<String> owner = OWNERS.get(Registries.ITEM.getId(stack.getItem()).getNamespace());
		return owner != null ? owner.get() : null;
	}

	/** Whether this is a thing only its owner crafts: one of this mod's, or of another player's mod beside it. */
	public static boolean isOurs(ItemStack stack) {
		return ownerOf(stack) != null;
	}

	/** Whether the player may craft {@code made}: anyone may craft what is nobody's, and only its owner what is. */
	public static boolean mayCraft(PlayerEntity player, ItemStack made) {
		String owner = ownerOf(made);
		return owner == null || player.getGameProfile().getName().equalsIgnoreCase(owner);
	}

	/** Whether the player may craft this mod's things. */
	public static boolean mayCraft(PlayerEntity player) {
		return player.getGameProfile().getName().equalsIgnoreCase(OWNER);
	}
}
