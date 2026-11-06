package io.github.kortev.gojolander.network;

import io.github.kortev.gojolander.Gojolander;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** The Hollow Purple key, pressed by someone wearing the blindfold. The server decides whether anything comes of it. */
public record PurpleCastPayload() implements CustomPayload {
	public static final CustomPayload.Id<PurpleCastPayload> ID = new CustomPayload.Id<>(Gojolander.id("purple_cast"));
	public static final PacketCodec<RegistryByteBuf, PurpleCastPayload> CODEC = PacketCodec.unit(new PurpleCastPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
