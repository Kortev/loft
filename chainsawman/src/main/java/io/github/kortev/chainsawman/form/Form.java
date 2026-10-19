package io.github.kortev.chainsawman.form;

import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.PochitaItem;
import io.github.kortev.chainsawman.network.FormPayload;
import io.github.kortev.chainsawman.network.FormPullPayload;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

/**
 * Who is Chainsaw Man right now. Pulling the cord with Pochita carried yanks it out of the chest, the engine catches,
 * and at the burst ({@link FormTimeline#BURST}) the saws tear out of head and arms: the form is on. (Every client plays
 * the transformation out from the pull: {@code FormCinematic}.)
 * Pulling it again lets the engine die and turns them back; so does losing Pochita or dying, and leaving. Everyone is
 * told, so everyone sees and hears it.
 */
public final class Form {
	private static final Set<UUID> ON = ConcurrentHashMap.newKeySet();
	/** Who has pulled the cord and is on the way into the form, and how many ticks ago. */
	private static final Map<UUID, Integer> PULLING = new ConcurrentHashMap<>();

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
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			PULLING.remove(handler.player.getUuid());
			set(server, handler.player.getUuid(), false);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ON.clear();
			PULLING.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Form::tick);
	}

	/** Whether {@code player} is in the form. */
	public static boolean on(ServerPlayerEntity player) {
		return ON.contains(player.getUuid());
	}

	/** The cord pulled: into the form if they carry Pochita, or back out of it. */
	public static void pullCord(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		if (PULLING.containsKey(id)) {
			return;
		}
		if (on(player)) {
			play(player, ChainsawSounds.STOP, 1.0F);
			set(player.server, id, false);
		} else if (PochitaItem.carried(player) && player.isAlive()) {
			PULLING.put(id, 0);
			play(player, ChainsawSounds.CORD, 1.0F);
			play(player, ChainsawSounds.START, 1.4F);
			broadcast(player.server, new FormPullPayload(id));
		}
	}

	private static void tick(MinecraftServer server) {
		for (Map.Entry<UUID, Integer> pull : Map.copyOf(PULLING).entrySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(pull.getKey());
			int age = pull.getValue() + 1;
			if (player == null || !player.isAlive()) {
				PULLING.remove(pull.getKey());
			} else if (age >= FormTimeline.BURST) {
				// The form is on from the burst: the clients show the saws growing out and the rest of it.
				PULLING.remove(pull.getKey());
				set(server, pull.getKey(), true);
			} else {
				PULLING.put(pull.getKey(), age);
			}
		}
		// Out of the form whoever has lost Pochita or died.
		if (ON.isEmpty() || server.getTicks() % 10 != 0) {
			return;
		}
		for (UUID id : Set.copyOf(ON)) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player == null || !player.isAlive() || !PochitaItem.carried(player)) {
				if (player != null) {
					play(player, ChainsawSounds.STOP, 1.0F);
				}
				set(server, id, false);
			}
		}
	}

	private static void play(ServerPlayerEntity player, SoundEvent sound, float volume) {
		player.getServerWorld().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), sound, SoundCategory.PLAYERS, volume, 1.0F);
	}

	private static void set(MinecraftServer server, UUID id, boolean on) {
		if (on ? !ON.add(id) : !ON.remove(id)) {
			return;
		}
		broadcast(server, new FormPayload(id, on));
	}

	private static void broadcast(MinecraftServer server, CustomPayload payload) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (ServerPlayNetworking.canSend(p, payload.getId())) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}
}
