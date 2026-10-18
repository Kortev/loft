package io.github.kortev.gojolander.network;

import io.github.kortev.gojolander.Gojolander;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** The barrier has closed: these are the entities (by network id) it caught, held still until it shatters. */
public record DomainCaughtPayload(int domainId, List<Integer> caught) implements CustomPayload {
	public static final CustomPayload.Id<DomainCaughtPayload> ID = new CustomPayload.Id<>(Gojolander.id("domain_caught"));
	public static final PacketCodec<RegistryByteBuf, DomainCaughtPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DomainCaughtPayload::domainId,
			PacketCodecs.VAR_INT.collect(PacketCodecs.toList()), DomainCaughtPayload::caught,
			DomainCaughtPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
