package io.github.kortev.gojolander.network;

import io.github.kortev.gojolander.Gojolander;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** The Domain key, pressed by someone wearing the blindfold. The server decides whether anything comes of it. */
public record DomainCastPayload() implements CustomPayload {
	public static final CustomPayload.Id<DomainCastPayload> ID = new CustomPayload.Id<>(Gojolander.id("domain_cast"));
	public static final PacketCodec<RegistryByteBuf, DomainCastPayload> CODEC = PacketCodec.unit(new DomainCastPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
