package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.contract.Bond;
import io.github.kortev.chainsawman.contract.Contract;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;

/** This player's contract bonds as the server last told them (uses per contract), and the moment of a bond-up (the line on screen, the grey). */
public final class ClientBonds {
	/** How long the bond-up line stays on screen (ticks). */
	public static final int MOMENT = 90;
	private static final Map<Integer, Integer> USES = new ConcurrentHashMap<>();
	private static long upAt = -1000;
	private static int upStep;
	private static Contract upContract = Contract.NONE;

	private ClientBonds() {
	}

	public static void set(Contract contract, int uses, boolean up) {
		USES.put(contract.ordinal(), uses);
		MinecraftClient client = MinecraftClient.getInstance();
		if (up && client.world != null) {
			upAt = client.world.getTime();
			upStep = Bond.step(uses);
			upContract = contract;
			// The screen drains grey for a beat.
			ForeverHud.catchUp(34);
		}
	}

	public static int uses(Contract contract) {
		return USES.getOrDefault(contract.ordinal(), 0);
	}

	public static int step(Contract contract) {
		return Bond.step(uses(contract));
	}

	/** Ticks since the last bond-up (large when none lately). */
	public static float sinceUp(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.world == null ? 1000.0F : client.world.getTime() + tickDelta - upAt;
	}

	public static int upStep() {
		return upStep;
	}

	public static Contract upContract() {
		return upContract;
	}

	/** Whether "Not yet" is open to the player here: Forever selected at bond 3. */
	public static boolean canNotYet() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.player != null && ClientContracts.get(client.player.getUuid()) == Contract.ETERNITY && step(Contract.ETERNITY) >= 3;
	}

	public static void clear() {
		USES.clear();
		upAt = -1000;
	}
}
