package io.github.kortev.gojolander.sight;

import io.github.kortev.gojolander.BlindfoldItem;
import io.github.kortev.gojolander.GojoRules;
import io.github.kortev.gojolander.Owner;
import io.github.kortev.gojolander.network.SixEyesPayload;
import io.github.kortev.shootingstar.network.ModNetworking;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The Six Eyes: the blindfold does not blind him, it is how he sees. Its owner, wearing it, sees every living thing
 * near him through whatever stands between them (their outlines, in the cold blue of his eyes), out to
 * {@code sixEyesRange} blocks. The server says who sees and how far; the client draws it.
 */
public final class SixEyes {
	/** How often each player's sight is looked at again (ticks). */
	private static final int EVERY = 10;
	/** What each player was last told. */
	private static final Map<UUID, Integer> TOLD = new HashMap<>();

	private SixEyes() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SixEyes::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> TOLD.remove(handler.getPlayer().getUuid()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TOLD.remove(handler.getPlayer().getUuid()));
	}

	/** How far {@code player} sees with the Six Eyes now (0: not at all). */
	public static int range(ServerPlayerEntity player) {
		if (!BlindfoldItem.wearing(player) || !Owner.is(player) || player.isSpectator()) {
			return 0;
		}
		return player.getServerWorld().getGameRules().getInt(GojoRules.SIX_EYES_RANGE);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTicks() % EVERY != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			int range = range(player);
			Integer told = TOLD.get(player.getUuid());
			if (told == null || told != range) {
				TOLD.put(player.getUuid(), range);
				ModNetworking.send(player, new SixEyesPayload(range));
			}
		}
	}
}
