package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.contract.Contract;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Which contract each player holds, as this client has been told. */
public final class ClientContracts {
	private static final Map<UUID, Contract> HELD = new ConcurrentHashMap<>();

	private ClientContracts() {
	}

	public static void set(UUID player, Contract contract) {
		if (contract == Contract.NONE) {
			HELD.remove(player);
		} else {
			HELD.put(player, contract);
		}
	}

	public static Contract get(UUID player) {
		return HELD.getOrDefault(player, Contract.NONE);
	}

	public static void clear() {
		HELD.clear();
	}
}
