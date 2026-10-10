package io.github.kortev.shootingstar.network;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Sent by a client whose rebuild is over (sooner, if they hurried it): they can be sent home now. */
public record GapSettlePayload() implements CustomPayload {
	public static final CustomPayload.Id<GapSettlePayload> ID = new CustomPayload.Id<>(ShootingStar.id("gap_settle"));
	public static final PacketCodec<RegistryByteBuf, GapSettlePayload> CODEC = PacketCodec.unit(new GapSettlePayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
