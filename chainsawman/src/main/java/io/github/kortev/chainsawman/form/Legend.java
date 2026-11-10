package io.github.kortev.chainsawman.form;

import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.network.FormEventPayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The legend on the map (game rule {@code legendRoar}, off by default): every time Chainsaw Man pulls the cord to transform, every other
 * player in that dimension hears a distant engine roar (the engine's start, pitched down and quiet, from his direction) and is shown
 * where he transformed, a marker on their screen for {@link #LASTS} ticks (a minute). As a human he is not shown: only the cord
 * starts it.
 */
public final class Legend {
	/** How long the marker stays (ticks). */
	public static final int LASTS = 60 * 20;
	/** How far off, toward him, the roar seems to come (blocks), and how loud. */
	private static final double HEARD_FROM = 12.0;
	private static final float VOLUME = 0.55F;
	private static final float PITCH = 0.7F;

	/** A marker a player has been sent: who, where, and until when (world time). */
	public record Marker(UUID legend, double x, double y, double z, long until) {
	}

	private static final Map<UUID, Marker> MARKERS = new ConcurrentHashMap<>();

	private Legend() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!MARKERS.isEmpty() && server.getTicks() % 20 == 0) {
				long now = server.getOverworld().getTime();
				MARKERS.entrySet().removeIf(e -> e.getValue().until() < now);
			}
		});
	}

	/** The marker {@code player} has now, or null: what the server last sent them. */
	@Nullable
	public static Marker markerFor(ServerPlayerEntity player) {
		Marker marker = MARKERS.get(player.getUuid());
		return marker != null && marker.until() >= player.getServerWorld().getTime() ? marker : null;
	}

	/** {@code legend} has pulled the cord: the others in their dimension hear it and are shown where. */
	public static void roar(ServerPlayerEntity legend) {
		MinecraftServer server = legend.server;
		if (!legend.getServerWorld().getGameRules().getBoolean(TeamRules.LEGEND_ROAR)) {
			return;
		}
		long until = legend.getServerWorld().getTime() + LASTS;
		Vec3d at = legend.getPos();
		FormEventPayload payload = new FormEventPayload(legend.getUuid(), FormEventPayload.LEGEND, LASTS, at.x, at.y, at.z);
		for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
			if (other == legend || other.getServerWorld() != legend.getServerWorld()) {
				continue;
			}
			MARKERS.put(other.getUuid(), new Marker(legend.getUuid(), at.x, at.y, at.z, until));
			if (ServerPlayNetworking.canSend(other, FormEventPayload.ID)) {
				ServerPlayNetworking.send(other, payload);
			}
			// The roar comes from his direction, a little way off.
			Vec3d toward = at.subtract(other.getPos()).multiply(1.0, 0.0, 1.0);
			Vec3d dir = toward.lengthSquared() < 1.0E-4 ? new Vec3d(0.0, 0.0, 1.0) : toward.normalize();
			Vec3d from = other.getEyePos().add(dir.multiply(HEARD_FROM));
			other.networkHandler.sendPacket(new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(ChainsawSounds.LEGEND_ROAR), SoundCategory.PLAYERS,
					from.x, from.y, from.z, VOLUME, PITCH, other.getWorld().random.nextLong()));
		}
	}

	static void clear() {
		MARKERS.clear();
	}
}
