package io.github.kortev.chitty.airship;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** A crew member works something aboard: AirshipEntity's ACTION_ codes (the grapple, the ladder, a bomb, overboard). */
public record AirshipActionPayload(int action) implements CustomPayload {
	public static final CustomPayload.Id<AirshipActionPayload> ID = new CustomPayload.Id<>(ShootingStar.id("airship_action"));
	public static final PacketCodec<RegistryByteBuf, AirshipActionPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, AirshipActionPayload::action,
			AirshipActionPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
