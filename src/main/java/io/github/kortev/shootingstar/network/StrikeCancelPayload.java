package io.github.kortev.shootingstar.network;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record StrikeCancelPayload(int strikeId) implements CustomPayload {
	public static final CustomPayload.Id<StrikeCancelPayload> ID = new CustomPayload.Id<>(ShootingStar.id("strike_cancel"));
	public static final PacketCodec<RegistryByteBuf, StrikeCancelPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, StrikeCancelPayload::strikeId,
			StrikeCancelPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
