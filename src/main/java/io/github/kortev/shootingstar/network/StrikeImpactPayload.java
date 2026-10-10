package io.github.kortev.shootingstar.network;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/** The round hit. Carries what the shooter's readout reports. */
public record StrikeImpactPayload(int strikeId, BlockPos impact, int radius, int zoneDiameter, int spireHeight)
		implements CustomPayload {
	public static final CustomPayload.Id<StrikeImpactPayload> ID = new CustomPayload.Id<>(ShootingStar.id("strike_impact"));
	public static final PacketCodec<RegistryByteBuf, StrikeImpactPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, StrikeImpactPayload::strikeId,
			BlockPos.PACKET_CODEC, StrikeImpactPayload::impact,
			PacketCodecs.VAR_INT, StrikeImpactPayload::radius,
			PacketCodecs.VAR_INT, StrikeImpactPayload::zoneDiameter,
			PacketCodecs.VAR_INT, StrikeImpactPayload::spireHeight,
			StrikeImpactPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
