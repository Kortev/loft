package io.github.kortev.shootingstar.network;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * Mjölnir's bolt came down. Carries what the clients need to draw it and the shooter's readout reports: whether the
 * ground was broken, and how tall the petrified bolt stands (0 for none).
 */
public record ThunderStrokePayload(int strikeId, BlockPos target, int radius, long seed, boolean terrain, int boltHeight)
		implements CustomPayload {
	public static final CustomPayload.Id<ThunderStrokePayload> ID = new CustomPayload.Id<>(ShootingStar.id("thunder_stroke"));
	public static final PacketCodec<RegistryByteBuf, ThunderStrokePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, ThunderStrokePayload::strikeId,
			BlockPos.PACKET_CODEC, ThunderStrokePayload::target,
			PacketCodecs.VAR_INT, ThunderStrokePayload::radius,
			PacketCodecs.VAR_LONG, ThunderStrokePayload::seed,
			PacketCodecs.BOOL, ThunderStrokePayload::terrain,
			PacketCodecs.VAR_INT, ThunderStrokePayload::boltHeight,
			ThunderStrokePayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
