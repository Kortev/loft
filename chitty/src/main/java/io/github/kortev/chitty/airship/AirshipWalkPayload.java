package io.github.kortev.chitty.airship;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Sent by someone aboard as they walk about the gondola: where they now stand on its floor (local x and z). */
public record AirshipWalkPayload(float x, float z) implements CustomPayload {
	public static final CustomPayload.Id<AirshipWalkPayload> ID = new CustomPayload.Id<>(ShootingStar.id("airship_walk"));
	public static final PacketCodec<RegistryByteBuf, AirshipWalkPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT, AirshipWalkPayload::x,
			PacketCodecs.FLOAT, AirshipWalkPayload::z,
			AirshipWalkPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
