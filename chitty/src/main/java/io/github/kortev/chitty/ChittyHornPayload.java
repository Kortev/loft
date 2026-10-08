package io.github.kortev.chitty;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Someone in the car squeezed the serpent's bulb. */
public record ChittyHornPayload() implements CustomPayload {
	public static final CustomPayload.Id<ChittyHornPayload> ID = new CustomPayload.Id<>(ShootingStar.id("chitty_horn"));
	public static final PacketCodec<RegistryByteBuf, ChittyHornPayload> CODEC = PacketCodec.unit(new ChittyHornPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
