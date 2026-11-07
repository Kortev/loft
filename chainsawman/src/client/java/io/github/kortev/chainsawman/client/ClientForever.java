package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.contract.ForeverFlags;
import io.github.kortev.chainsawman.network.ForeverLockPayload;

/** Forever as this client knows it: which living things are locked (told by the server; the freezing mixins read it). */
public final class ClientForever {
	private ClientForever() {
	}

	public static void told(ForeverLockPayload payload) {
		if (payload.on()) {
			ForeverFlags.CLIENT.add(payload.target());
		} else {
			ForeverFlags.CLIENT.remove(payload.target());
		}
	}

	public static void clear() {
		ForeverFlags.CLIENT.clear();
	}
}
