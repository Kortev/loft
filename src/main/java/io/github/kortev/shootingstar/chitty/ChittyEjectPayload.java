package io.github.kortev.shootingstar.chitty;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** The driver pulled the ejector: whoever is in the back seat goes up into the air. */
public record ChittyEjectPayload() implements CustomPayload {
	public static final CustomPayload.Id<ChittyEjectPayload> ID = new CustomPayload.Id<>(ShootingStar.id("chitty_eject"));
	public static final PacketCodec<RegistryByteBuf, ChittyEjectPayload> CODEC = PacketCodec.unit(new ChittyEjectPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
