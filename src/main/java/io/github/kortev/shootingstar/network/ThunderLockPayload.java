package io.github.kortev.shootingstar.network;

import io.github.kortev.shootingstar.ShootingStar;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;

/**
 * Mjölnir was raised at a target. {@code age} is non-zero when syncing a strike already under way; {@code radius} is the
 * zone it will kill everything in, and {@code seed} grows the storm, the bolt and the scar the same on every client.
 */
public record ThunderLockPayload(int strikeId, BlockPos target, UUID shooter, int age, int radius, long seed)
		implements CustomPayload {
	public static final CustomPayload.Id<ThunderLockPayload> ID = new CustomPayload.Id<>(ShootingStar.id("thunder_lock"));
	public static final PacketCodec<RegistryByteBuf, ThunderLockPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, ThunderLockPayload::strikeId,
			BlockPos.PACKET_CODEC, ThunderLockPayload::target,
			Uuids.PACKET_CODEC, ThunderLockPayload::shooter,
			PacketCodecs.VAR_INT, ThunderLockPayload::age,
			PacketCodecs.VAR_INT, ThunderLockPayload::radius,
			PacketCodecs.VAR_LONG, ThunderLockPayload::seed,
			ThunderLockPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
