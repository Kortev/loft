package io.github.kortev.chitty.carriage;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** The driver does something: CarriageEntity's ACTION_ codes (crack the whip, put up or take down the disguise). */
public record CarriageActionPayload(int action) implements CustomPayload {
	public static final CustomPayload.Id<CarriageActionPayload> ID = new CustomPayload.Id<>(ShootingStar.id("carriage_action"));
	public static final PacketCodec<RegistryByteBuf, CarriageActionPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, CarriageActionPayload::action,
			CarriageActionPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
