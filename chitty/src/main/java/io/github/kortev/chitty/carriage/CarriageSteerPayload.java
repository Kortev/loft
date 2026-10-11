package io.github.kortev.chitty.carriage;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Where the driver has the horse turned: the fore-carriage's angle to her (degrees, left positive). The driver's client
 * moves her, and tells the server this as it changes, for everyone else to see the horse and the front wheels turn.
 */
public record CarriageSteerPayload(float steer) implements CustomPayload {
	public static final CustomPayload.Id<CarriageSteerPayload> ID = new CustomPayload.Id<>(ShootingStar.id("carriage_steer"));
	public static final PacketCodec<RegistryByteBuf, CarriageSteerPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT, CarriageSteerPayload::steer,
			CarriageSteerPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
