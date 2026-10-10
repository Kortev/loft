package io.github.kortev.chitty;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Sent by the driver's client whenever what they are doing changes: the pedals, the wheel, the lever, and how her wings
 * and raft are (ChittyEntity's STATE_ bits).
 */
public record ChittyInputPayload(byte controls, byte state) implements CustomPayload {
	public static final CustomPayload.Id<ChittyInputPayload> ID = new CustomPayload.Id<>(ShootingStar.id("chitty_input"));
	public static final PacketCodec<RegistryByteBuf, ChittyInputPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BYTE, ChittyInputPayload::controls,
			PacketCodecs.BYTE, ChittyInputPayload::state,
			ChittyInputPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
