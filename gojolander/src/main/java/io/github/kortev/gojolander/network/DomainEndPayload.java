package io.github.kortev.gojolander.network;

import io.github.kortev.gojolander.Gojolander;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * A Domain ended before its time: its caster fell, left or was taken elsewhere. If it had not closed yet it is simply
 * {@code cancelled}; otherwise it shatters now, and {@code age} is the tick it is at.
 */
public record DomainEndPayload(int domainId, int age, boolean cancelled) implements CustomPayload {
	public static final CustomPayload.Id<DomainEndPayload> ID = new CustomPayload.Id<>(Gojolander.id("domain_end"));
	public static final PacketCodec<RegistryByteBuf, DomainEndPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DomainEndPayload::domainId,
			PacketCodecs.VAR_INT, DomainEndPayload::age,
			PacketCodecs.BOOL, DomainEndPayload::cancelled,
			DomainEndPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
