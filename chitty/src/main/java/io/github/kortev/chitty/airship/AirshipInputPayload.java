package io.github.kortev.chitty.airship;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Sent by the pilot's client whenever what they are doing changes: the throttle, the rudder, climbing or sinking. */
public record AirshipInputPayload(byte controls) implements CustomPayload {
	public static final CustomPayload.Id<AirshipInputPayload> ID = new CustomPayload.Id<>(ShootingStar.id("airship_input"));
	public static final PacketCodec<RegistryByteBuf, AirshipInputPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BYTE, AirshipInputPayload::controls,
			AirshipInputPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
