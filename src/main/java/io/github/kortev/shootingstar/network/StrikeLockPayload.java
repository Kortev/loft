package io.github.kortev.shootingstar.network;

import io.github.kortev.shootingstar.ShootingStar;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;

/** A strike got its kinetic lock. {@code age} is non-zero when syncing a strike already in flight. */
public record StrikeLockPayload(int strikeId, BlockPos target, UUID shooter, int age) implements CustomPayload {
	public static final CustomPayload.Id<StrikeLockPayload> ID = new CustomPayload.Id<>(ShootingStar.id("strike_lock"));
	public static final PacketCodec<RegistryByteBuf, StrikeLockPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, StrikeLockPayload::strikeId,
			BlockPos.PACKET_CODEC, StrikeLockPayload::target,
			Uuids.PACKET_CODEC, StrikeLockPayload::shooter,
			PacketCodecs.VAR_INT, StrikeLockPayload::age,
			StrikeLockPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
