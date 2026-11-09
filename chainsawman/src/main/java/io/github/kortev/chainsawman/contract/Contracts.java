package io.github.kortev.chainsawman.contract;

import com.mojang.serialization.Codec;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.network.ContractPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * A player's devil contracts: up to {@link #SLOTS} (their prices stack), one of them selected for the "Use contract" key (another key switches).
 * Saved on the player and told to every client the way the form is ({@link ContractPayload}: the selected one). A hybrid (someone who has
 * merged with Pochita, {@link Heart}) cannot hold a contract: the heart of a devil fills all three slots, so every devil is refused.
 *
 * <p>The slots hold contract ids, so a contract this mod does not know (yet) keeps its slot. Old saves held one contract in {@link #HELD}
 * (a single id): it is read as the first slot and moved over the first time it is looked at.
 */
public final class Contracts {
	public static final int SLOTS = 3;
	/** The old single contract (saves from before the slots): read, and moved into {@link #LIST}. */
	public static final AttachmentType<String> HELD = AttachmentRegistry.<String>builder().persistent(Codec.STRING).copyOnDeath()
			.buildAndRegister(ChainsawMan.id("contract"));
	/** The contracts held, in order. */
	public static final AttachmentType<List<String>> LIST = AttachmentRegistry.<List<String>>builder().persistent(Codec.STRING.listOf()).copyOnDeath()
			.buildAndRegister(ChainsawMan.id("contracts"));
	/** The slot the key uses. */
	public static final AttachmentType<Integer> SELECTED = AttachmentRegistry.<Integer>builder().persistent(Codec.INT).copyOnDeath()
			.buildAndRegister(ChainsawMan.id("contract_selected"));

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
		Bond.init();
		Forever.init();
	}

	/** The ids of the contracts held, in slot order (an old save's single contract is the first). */
	public static List<String> ids(PlayerEntity player) {
		List<String> list = player.getAttached(LIST);
		if (list != null) {
			return list;
		}
		String old = player.getAttachedOrElse(HELD, Contract.NONE.id);
		if (old.isEmpty() || old.equals(Contract.NONE.id)) {
			return List.of();
		}
		List<String> moved = List.of(old);
		if (!player.getWorld().isClient) {
			player.setAttached(LIST, moved);
			player.removeAttached(HELD);
		}
		return moved;
	}

	/** How many contracts are held. */
	public static int count(PlayerEntity player) {
		return ids(player).size();
	}

	/** The slot the key uses (0 to 2), or -1 with nothing held. */
	public static int selected(PlayerEntity player) {
		int n = count(player);
		if (n == 0) {
			return -1;
		}
		return Math.max(0, Math.min(n - 1, player.getAttachedOrElse(SELECTED, 0)));
	}

	/** The contract the key uses: the selected slot's, or none. */
	public static Contract get(PlayerEntity player) {
		int slot = selected(player);
		return slot < 0 ? Contract.NONE : Contract.byId(ids(player).get(slot));
	}

	/** Whether any slot holds {@code contract}. */
	public static boolean has(PlayerEntity player, Contract contract) {
		return contract != Contract.NONE && ids(player).contains(contract.id);
	}

	/** Why a contract could not be granted. */
	public enum Result {
		GRANTED, HYBRID, FULL
	}

	/**
	 * {@code contract} into a free slot of {@code player} (and selected); one they hold already stays as it is. Refused for a hybrid (the heart
	 * fills every slot) and when all {@link #SLOTS} are full: {@link #replace} with the one they give up.
	 */
	public static Result grant(ServerPlayerEntity player, Contract contract) {
		if (contract == Contract.NONE) {
			clear(player);
			return Result.GRANTED;
		}
		return grant(player, contract.id);
	}

	/** The same, by id (a contract this mod does not know yet). */
	public static Result grant(ServerPlayerEntity player, String id) {
		if (Heart.has(player)) {
			return Result.HYBRID;
		}
		List<String> list = new ArrayList<>(ids(player));
		int at = list.indexOf(id);
		if (at < 0) {
			if (list.size() >= SLOTS) {
				return Result.FULL;
			}
			list.add(id);
			at = list.size() - 1;
		}
		player.setAttached(LIST, List.copyOf(list));
		player.setAttached(SELECTED, at);
		told(player);
		return Result.GRANTED;
	}

	/** {@code id} into slot {@code slot}, in place of what was there (the one they gave up), and selected. */
	public static Result replace(ServerPlayerEntity player, int slot, String id) {
		if (Heart.has(player)) {
			return Result.HYBRID;
		}
		List<String> list = new ArrayList<>(ids(player));
		if (slot < 0 || slot >= list.size()) {
			return grant(player, id);
		}
		list.set(slot, id);
		player.setAttached(LIST, List.copyOf(list));
		player.setAttached(SELECTED, slot);
		told(player);
		return Result.GRANTED;
	}

	/** The selected slot moves to the next contract held; the action bar says which. */
	public static void cycle(ServerPlayerEntity player) {
		int n = count(player);
		if (n == 0) {
			player.sendMessage(Text.translatable("chainsawman.forever.no_contract"), true);
			return;
		}
		int next = (selected(player) + 1) % n;
		player.setAttached(SELECTED, next);
		told(player);
		player.sendMessage(Text.translatable("chainsawman.contract.selected", name(ids(player).get(next)), next + 1, n), true);
	}

	/** A contract's name for the player (an id this mod does not know is shown as it is). */
	public static Text name(String id) {
		Contract c = Contract.byId(id);
		return c == Contract.NONE ? Text.literal(id) : Text.translatable("contract.chainsawman." + c.id);
	}

	/** Every contract ended. */
	public static void clear(ServerPlayerEntity player) {
		wipe(player);
		told(player);
	}

	/** Every contract ended, without telling anyone (a player who is leaving). */
	public static void wipe(PlayerEntity player) {
		player.removeAttached(HELD);
		player.removeAttached(LIST);
		player.removeAttached(SELECTED);
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
