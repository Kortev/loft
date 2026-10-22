package io.github.kortev.shootingstar.network;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** A Mjölnir strike was called off before its bolt came down. */
public record ThunderCancelPayload(int strikeId) implements CustomPayload {
	public static final CustomPayload.Id<ThunderCancelPayload> ID = new CustomPayload.Id<>(ShootingStar.id("thunder_cancel"));
	public static final PacketCodec<RegistryByteBuf, ThunderCancelPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, ThunderCancelPayload::strikeId,
			ThunderCancelPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
