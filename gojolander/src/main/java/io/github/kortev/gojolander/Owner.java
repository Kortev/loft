package io.github.kortev.gojolander;

import io.github.kortev.shootingstar.OwnerOnly;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;

/**
 * Whose this mod is: the one player who can craft the blindfold and the one it works for (on anyone else it is a strip
 * of black cloth). Until kortev has his Minecraft name it is nobody's ({@link #PLACEHOLDER}), and while it is nobody's
 * anyone can craft it and cast with it, so it can be tried out. An operator makes it his on a running server with
 * {@code /gojolander owner <name>}, and the world keeps it from then on.
 */
public final class Owner {
	/**
	 * Who it is until told otherwise: a name no Minecraft account can have, standing for nobody (and so for everyone).
	 * Set the real one here, or with the command (which needs no new jar).
	 */
	public static final String PLACEHOLDER = "-unclaimed-";
	private static volatile String current = PLACEHOLDER;

	private Owner() {
	}

	static void init() {
		OwnerOnly.register(Gojolander.MOD_ID, () -> claimed() ? current : null);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> current = GojoState.get(server).owner());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> current = PLACEHOLDER);
	}

	/** The owner's Minecraft name ({@link #PLACEHOLDER} while it is nobody's). */
	public static String get() {
		return current;
	}

	/** Whether anyone has been made its owner yet. */
	public static boolean claimed() {
		return !PLACEHOLDER.equals(current);
	}

	/** Whether its things work for {@code player}: its owner's, or anyone's while it is nobody's. */
	public static boolean is(PlayerEntity player) {
		return !claimed() || player.getGameProfile().getName().equalsIgnoreCase(current);
	}

	/** Makes {@code name} the owner from now on, kept with the world. */
	public static void set(MinecraftServer server, String name) {
		GojoState state = GojoState.get(server);
		state.setOwner(name);
		current = state.owner();
	}
}
