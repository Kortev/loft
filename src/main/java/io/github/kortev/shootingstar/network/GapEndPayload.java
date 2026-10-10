package io.github.kortev.shootingstar.network;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** The event is over: the shooter has let reality back in (or it was called off). */
public record GapEndPayload(int gapId) implements CustomPayload {
	public static final CustomPayload.Id<GapEndPayload> ID = new CustomPayload.Id<>(ShootingStar.id("gap_end"));
	public static final PacketCodec<RegistryByteBuf, GapEndPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, GapEndPayload::gapId,
			GapEndPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
