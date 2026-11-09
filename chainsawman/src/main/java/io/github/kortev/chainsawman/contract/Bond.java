package io.github.kortev.chainsawman.contract;

import com.mojang.serialization.Codec;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.network.BondPayload;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;

/**
 * Contract bonds: the more a contract is used, the deeper the bond (3 steps, kept for each contract and each player, even if the contract
 * ends and comes back). Each step changes the power in a way that can be seen; the steps are the same for every contract, what each does is
 * the contract's own (Forever's: {@link Forever#cubeHalf}, {@link Forever#lasts}, {@link Forever#farCopies}, the burst, and at the last step
 * "Not yet").
 *
 * <p>Only a use that locked something counts ({@link #record}). Bond 2 comes at {@link #TWO} uses, bond 3 at {@link #THREE}. A bond-up is a
 * moment: the devil's crowd voice says a line (on screen and a sound), the screen drains grey for a beat, and the contract HUD shows the new
 * step ({@link BondPayload} tells the one whose bond it is).
 */
public final class Bond {
	public static final int TWO = 15;
	public static final int THREE = 40;
	public static final int MAX = 3;

	/** How many counted uses each contract has had, by contract id. */
	public static final AttachmentType<Map<String, Integer>> USES = AttachmentRegistry.<Map<String, Integer>>builder()
			.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath().buildAndRegister(ChainsawMan.id("contract_uses"));

	private Bond() {
	}

	static void init() {
		// Told your bonds as you join, so the HUD is right from the first frame.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			for (Contract c : Contract.values()) {
				if (c != Contract.NONE && uses(handler.player, c) > 0) {
					sync(handler.player, c, false);
				}
			}
		});
	}

	/** How many counted uses {@code contract} has had for {@code player}. */
	public static int uses(PlayerEntity player, Contract contract) {
		return uses(player, contract.id);
	}

	public static int uses(PlayerEntity player, String id) {
		return player.getAttachedOrElse(USES, Map.<String, Integer>of()).getOrDefault(id, 0);
	}

	/** The bond step (1 to 3) that {@code uses} uses make. */
	public static int step(int uses) {
		return uses >= THREE ? 3 : uses >= TWO ? 2 : 1;
	}

	/** {@code player}'s bond with {@code contract}: 1 (new), 2 or 3. */
	public static int step(PlayerEntity player, Contract contract) {
		return step(uses(player, contract));
	}

	/** The uses the next step needs in all (0 at the last step). */
	public static int next(int uses) {
		return uses >= THREE ? 0 : uses >= TWO ? THREE : TWO;
	}

	/** Sets the uses outright (for the command and the tests); told to the player, with no moment. */
	public static void set(ServerPlayerEntity player, Contract contract, int uses) {
		set(player, contract.id, uses);
	}

	/** The same, by contract id (a contract this mod does not know yet keeps its own bond). */
	public static void set(ServerPlayerEntity player, String id, int uses) {
		Map<String, Integer> map = new HashMap<>(player.getAttachedOrElse(USES, Map.<String, Integer>of()));
		map.put(id, Math.max(0, uses));
		player.setAttached(USES, Map.copyOf(map));
		sync(player, Contract.byId(id), false);
	}

	/**
	 * One counted use of {@code contract}: a use that locked something. Returns the new bond step if this use deepened the bond, else 0.
	 * A bond-up is announced (the line, the grey, the HUD).
	 */
	public static int record(ServerPlayerEntity player, Contract contract) {
		int before = uses(player, contract);
		Map<String, Integer> map = new HashMap<>(player.getAttachedOrElse(USES, Map.<String, Integer>of()));
		map.put(contract.id, before + 1);
		player.setAttached(USES, Map.copyOf(map));
		int was = step(before);
		int now = step(before + 1);
		sync(player, contract, now > was);
		if (now > was) {
			player.getServerWorld().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), ChainsawSounds.BOND_UP, SoundCategory.PLAYERS, 1.6F, now == 3 ? 0.85F : 1.0F);
			return now;
		}
		return 0;
	}

	/** Tells {@code player} their uses with {@code contract} ({@code up}: a bond-up, show the moment). */
	public static void sync(ServerPlayerEntity player, Contract contract, boolean up) {
		if (contract != Contract.NONE && ServerPlayNetworking.canSend(player, BondPayload.ID)) {
			ServerPlayNetworking.send(player, new BondPayload((byte) contract.ordinal(), uses(player, contract), up));
		}
	}
}
