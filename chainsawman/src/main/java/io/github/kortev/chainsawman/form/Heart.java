package io.github.kortev.chainsawman.form;

import com.mojang.serialization.Codec;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.network.FormEventPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

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

	/** Everyone told who has the heart (the cord shows on their chest): whoever joins, and they of everyone there. */
	static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity joined = handler.player;
			if (merged(joined)) {
				told(joined);
			}
			for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
				if (other != joined && merged(other) && ServerPlayNetworking.canSend(joined, FormEventPayload.ID)) {
					ServerPlayNetworking.send(joined, new FormEventPayload(other.getUuid(), FormEventPayload.HEART, 1));
				}
			}
		});
	}

	/** Pochita's heart given to {@code player} straight off, no merge (to try things out). */
	public static void give(ServerPlayerEntity player) {
		player.setAttached(MERGED, true);
		told(player);
	}

	/** Pochita's heart taken back from {@code player} (to see the merge again). */
	public static void take(ServerPlayerEntity player) {
		player.removeAttached(MERGED);
		Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.HEART, 0));
	}

	/** Everyone told that {@code player} has the heart now. */
	static void told(ServerPlayerEntity player) {
		Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.HEART, 1));
	}

	public static boolean has(PlayerEntity player) {
		return merged(player);
	}

	public static boolean merged(PlayerEntity player) {
		return player.getAttachedOrElse(MERGED, false);
	}
}
