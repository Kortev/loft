package io.github.kortev.chainsawman;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;

/**
 * Whose Pochita is, on a server that wants it to be one player's (an SMP). Nobody's by default, and while it is nobody's
 * anyone can feed Pochita, merge with him and find the den, so the mod works for everyone. An operator makes it one
 * player's with {@code /chainsaw owner <player>} (and nobody's again with {@code /chainsaw owner clear}); the world
 * keeps it from then on, and only that player passes the gates.
 */
public final class Owner {
	private static volatile String current;

	private Owner() {
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> current = PochitaPlace.owner(server));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> current = null);
	}

	/** The owner's Minecraft name, or null while it is nobody's. */
	public static String get() {
		return current;
	}

	public static boolean claimed() {
		return current != null;
	}

	/** Whether Pochita's gates are open to {@code player}: their owner, or anyone while there is none. */
	public static boolean is(PlayerEntity player) {
		return current == null || player.getGameProfile().getName().equalsIgnoreCase(current);
	}

	/** Makes {@code name} the owner from now on (null: nobody), kept with the world. */
	public static void set(MinecraftServer server, String name) {
		PochitaPlace.setOwner(server, name);
		current = name;
	}
}
