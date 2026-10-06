package io.github.kortev.shootingstar.chitty;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Sent by the driver's client whenever what they are doing changes: the pedals, the wheel, the lever, the wings. */
public record ChittyInputPayload(byte controls, boolean wings) implements CustomPayload {
	public static final CustomPayload.Id<ChittyInputPayload> ID = new CustomPayload.Id<>(ShootingStar.id("chitty_input"));
	public static final PacketCodec<RegistryByteBuf, ChittyInputPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BYTE, ChittyInputPayload::controls,
			PacketCodecs.BOOL, ChittyInputPayload::wings,
			ChittyInputPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
