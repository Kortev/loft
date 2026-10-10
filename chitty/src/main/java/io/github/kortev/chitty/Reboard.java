package io.github.kortev.chitty;

import io.github.kortev.chitty.airship.AirshipEntity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.Heightmap;

/**
 * A player who leaves the game aboard Chitty or the airship while other players are aboard her too: the game keeps a
 * vehicle with its only player when they leave, and otherwise leaves it in the world, and them, when they come back,
 * where they were, in the air if she was flying. They are put back aboard her when they come back, if she is still
 * near; if she is not, they are let down gently (SoftLanding) instead of falling to their death.
 *
 * <p>Which vehicle is kept on the player, in a tag of theirs (saved with them), until they are back.
 */
public final class Reboard {
	private static final String TAG = "chitty.aboard:";
	/** How long (ticks) she is looked for after they come back: her part of the world may load after them. */
	private static final int LOOK = 60;
	/** How far from where they come back she may be, to be put back aboard her. */
	private static final double REACH = 64.0;
	private static final Map<UUID, Looking> LOOKING = new HashMap<>();

	private record Looking(UUID vehicle, int until) {
	}

	private Reboard() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> leaving(handler.player));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> back(handler.player, server));
		ServerTickEvents.END_SERVER_TICK.register(Reboard::tick);
	}

	private static boolean flies(Entity vehicle) {
		return vehicle instanceof AirshipEntity || vehicle instanceof ChittyEntity;
	}

	/** Before they are saved: which vehicle they were aboard, if she stays behind them. */
	private static void leaving(ServerPlayerEntity player) {
		player.getCommandTags().removeIf(tag -> tag.startsWith(TAG));
		Entity vehicle = player.getVehicle();
		if (vehicle != null && flies(vehicle) && !vehicle.getRootVehicle().hasPlayerRider()) {
			player.addCommandTag(TAG + vehicle.getUuid());
		}
	}

	private static void back(ServerPlayerEntity player, MinecraftServer server) {
		String tag = player.getCommandTags().stream().filter(t -> t.startsWith(TAG)).findFirst().orElse(null);
		if (tag == null) {
			return;
		}
		player.removeCommandTag(tag);
		UUID vehicle;
		try {
			vehicle = UUID.fromString(tag.substring(TAG.length()));
		} catch (IllegalArgumentException e) {
			return;
		}
		if (player.hasVehicle()) {
			return;
		}
		LOOKING.put(player.getUuid(), new Looking(vehicle, server.getTicks() + LOOK));
		// While she is looked for, they do not fall.
		if (aloft(player)) {
			SoftLanding.letDown(player);
		}
	}

	private static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Looking>> it = LOOKING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Looking> e = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
			if (player == null || player.hasVehicle() || !player.isAlive()) {
				it.remove();
				continue;
			}
			Entity vehicle = player.getServerWorld().getEntity(e.getValue().vehicle());
			if (vehicle != null && !vehicle.isRemoved() && vehicle.squaredDistanceTo(player) < REACH * REACH && player.startRiding(vehicle)) {
				player.fallDistance = 0.0F;
				it.remove();
			} else if (server.getTicks() > e.getValue().until()) {
				// She is not there for them: they float down (SoftLanding, from back()) if they are up in the air.
				it.remove();
			}
		}
	}

	/** Whether someone is up in the air, more than a fall's worth above the ground. */
	private static boolean aloft(ServerPlayerEntity player) {
		int ground = player.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
		return !player.isOnGround() && player.getY() - Math.max(ground, player.getWorld().getBottomY()) > 2.5;
	}
}
