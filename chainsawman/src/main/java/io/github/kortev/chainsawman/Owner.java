package io.github.kortev.chainsawman;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Whose Pochita is, on a server that wants it to be one player's (an SMP). Nobody's by default, and while it is nobody's
 * anyone can feed Pochita, merge with him and find the den, so the mod works for everyone. An operator makes it one
 * player's with {@code /chainsaw owner <player>} (and nobody's again with {@code /chainsaw owner clear}); the world
 * keeps it from then on, and only that player passes the gates. It is kept by player id (so a name change, or someone
 * else taking the name, changes nothing), and shown by name; a name kept from before turns into an id the next time that
 * player joins.
 */
public final class Owner {
	private static volatile String current;
	@Nullable
	private static volatile UUID currentId;

	private Owner() {
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			current = PochitaPlace.owner(server);
			currentId = PochitaPlace.ownerId(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			current = null;
			currentId = null;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> joined(server, handler.player.getGameProfile()));
	}

	/** {@code profile} has joined: if the owner is a name kept from before and this is that name, it is their id from now on (and their name as it is now). */
	static void joined(MinecraftServer server, GameProfile profile) {
		if (current != null && currentId == null && profile.getName().equalsIgnoreCase(current)) {
			set(server, profile.getName(), profile.getId());
		} else if (currentId != null && currentId.equals(profile.getId()) && !profile.getName().equals(current)) {
			// (Renamed: shown by the name they have now.)
			set(server, profile.getName(), profile.getId());
		}
	}

	/** The owner's Minecraft name, or null while it is nobody's. */
	public static String get() {
		return current;
	}

	/** The owner's player id, or null while it is nobody's, or while it is a name kept from before that player has not joined since. */
	@Nullable
	public static UUID id() {
		return currentId;
	}

	public static boolean claimed() {
		return current != null;
	}

	/** Whether Pochita's gates are open to {@code player}: their owner, or anyone while there is none. */
	public static boolean is(PlayerEntity player) {
		if (current == null) {
			return true;
		}
		UUID id = currentId;
		return id != null ? id.equals(player.getUuid()) : player.getGameProfile().getName().equalsIgnoreCase(current);
	}

	/** Makes {@code name} the owner from now on (null: nobody), kept with the world: by id if they are in the game, by name till they join if not. */
	public static void set(MinecraftServer server, String name) {
		if (name == null) {
			set(server, null, null);
			return;
		}
		GameProfile profile = profile(server, name);
		if (profile != null) {
			set(server, profile.getName(), profile.getId());
		} else {
			set(server, name, null);
		}
	}

	/** Makes the player {@code name}, {@code id} the owner from now on (null name: nobody), kept with the world. */
	public static void set(MinecraftServer server, String name, @Nullable UUID id) {
		PochitaPlace.setOwner(server, name, id);
		current = name;
		currentId = name == null ? null : id;
	}

	/**
	 * Who {@code name} is, if they are in the game. (Not looked up anywhere else: the server's cache of names would ask Mojang, or make up
	 * an offline id, for a name it hasn't seen. A player not in the game is kept by name until they join.)
	 */
	@Nullable
	private static GameProfile profile(MinecraftServer server, String name) {
		PlayerEntity online = server.getPlayerManager().getPlayer(name);
		return online != null ? online.getGameProfile() : null;
	}
}
