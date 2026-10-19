package io.github.kortev.chainsawman.form;

import io.github.kortev.chainsawman.PochitaItem;
import io.github.kortev.chainsawman.network.FormPayload;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Who is Chainsaw Man right now. Pulling the cord with Pochita carried turns a player into the form, and pulling it
 * again turns them back; so does losing Pochita, dying or leaving. Everyone is told, so everyone sees the form.
 */
public final class Form {
	private static final Set<UUID> ON = ConcurrentHashMap.newKeySet();

	private Form() {
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			for (UUID id : ON) {
				if (ServerPlayNetworking.canSend(handler.player, FormPayload.ID)) {
					ServerPlayNetworking.send(handler.player, new FormPayload(id, true));
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> set(server, handler.player.getUuid(), false));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ON.clear());
		ServerTickEvents.END_SERVER_TICK.register(Form::tick);
	}

	/** Whether {@code player} is in the form. */
	public static boolean on(ServerPlayerEntity player) {
		return ON.contains(player.getUuid());
	}

	/** The cord pulled: into the form if they carry Pochita, or back out of it. */
	public static void pullCord(ServerPlayerEntity player) {
		if (on(player)) {
			set(player.server, player.getUuid(), false);
		} else if (PochitaItem.carried(player) && player.isAlive()) {
			set(player.server, player.getUuid(), true);
		}
	}

	/** Out of the form whoever has lost Pochita or died. */
	private static void tick(MinecraftServer server) {
		if (ON.isEmpty() || server.getTicks() % 10 != 0) {
			return;
		}
		for (UUID id : Set.copyOf(ON)) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player == null || !player.isAlive() || !PochitaItem.carried(player)) {
				set(server, id, false);
			}
		}
	}

	private static void set(MinecraftServer server, UUID id, boolean on) {
		if (on ? !ON.add(id) : !ON.remove(id)) {
			return;
		}
		FormPayload payload = new FormPayload(id, on);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (ServerPlayNetworking.canSend(p, FormPayload.ID)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}
}
