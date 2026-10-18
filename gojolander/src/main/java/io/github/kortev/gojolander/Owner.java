package io.github.kortev.gojolander;

import io.github.kortev.shootingstar.OwnerOnly;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;

/**
 * Whose this mod is: the one player who can craft the blindfold and the one it works for (on anyone else it is a strip
 * of black cloth). Until kortev has his Minecraft name it is {@link #PLACEHOLDER}; an operator sets the real one on a
 * running server with {@code /gojolander owner <name>}, and the world keeps it from then on.
 */
public final class Owner {
	/** Who it is until told otherwise. Change it here, or with the command (which needs no new jar). */
	public static final String PLACEHOLDER = "gojolander";
	private static volatile String current = PLACEHOLDER;

	private Owner() {
	}

	static void init() {
		OwnerOnly.register(Gojolander.MOD_ID, Owner::get);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> current = GojoState.get(server).owner());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> current = PLACEHOLDER);
	}

	/** The owner's Minecraft name. */
	public static String get() {
		return current;
	}

	public static boolean is(PlayerEntity player) {
		return player.getGameProfile().getName().equalsIgnoreCase(current);
	}

	/** Makes {@code name} the owner from now on, kept with the world. */
	public static void set(MinecraftServer server, String name) {
		GojoState state = GojoState.get(server);
		state.setOwner(name);
		current = state.owner();
	}
}
