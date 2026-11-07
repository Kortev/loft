package io.github.kortev.chainsawman.contract;

import com.mojang.serialization.Codec;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.network.ContractPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * A player's devil contract: none, or one (a player holds one at a time, so granting another ends the old). Saved on the player
 * and told to every client the way the form is ({@link ContractPayload}). A hybrid (someone who has merged with Pochita, {@link
 * Heart}) cannot hold a contract: a devil in you is the one or the other. The price and how a contract is gained are not decided
 * yet: for now {@code /chainsaw contract} grants it.
 */
public final class Contracts {
	public static final AttachmentType<String> HELD = AttachmentRegistry.<String>builder().persistent(Codec.STRING).copyOnDeath()
			.buildAndRegister(ChainsawMan.id("contract"));

	private Contracts() {
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
				Contract held = get(other);
				if (held != Contract.NONE && ServerPlayNetworking.canSend(handler.player, ContractPayload.ID)) {
					ServerPlayNetworking.send(handler.player, new ContractPayload(other.getUuid(), (byte) held.ordinal()));
				}
			}
			Contract mine = get(handler.player);
			if (mine != Contract.NONE) {
				told(handler.player);
			}
		});
		Echo.init();
	}

	public static Contract get(PlayerEntity player) {
		return Contract.byId(player.getAttachedOrElse(HELD, Contract.NONE.id));
	}

	public static boolean has(PlayerEntity player, Contract contract) {
		return get(player) == contract;
	}

	/** Why a contract could not be granted. */
	public enum Result {
		GRANTED, HYBRID
	}

	/** {@code contract} to {@code player}, ending any other they hold; refused for a hybrid. */
	public static Result grant(ServerPlayerEntity player, Contract contract) {
		if (contract == Contract.NONE) {
			clear(player);
			return Result.GRANTED;
		}
		if (Heart.has(player)) {
			return Result.HYBRID;
		}
		if (get(player) != contract) {
			Echo.end(player, false);
		}
		player.setAttached(HELD, contract.id);
		told(player);
		return Result.GRANTED;
	}

	public static void clear(ServerPlayerEntity player) {
		Echo.end(player, false);
		player.removeAttached(HELD);
		told(player);
	}

	private static void told(ServerPlayerEntity player) {
		ContractPayload payload = new ContractPayload(player.getUuid(), (byte) get(player).ordinal());
		for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
			if (ServerPlayNetworking.canSend(p, ContractPayload.ID)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}
}
