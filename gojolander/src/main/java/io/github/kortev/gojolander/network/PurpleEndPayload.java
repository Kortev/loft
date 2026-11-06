package io.github.kortev.gojolander.network;

import io.github.kortev.gojolander.Gojolander;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** A Hollow Purple cut short before it was released (its caster fell or left): it simply stops, with a snap. */
public record PurpleEndPayload(int id) implements CustomPayload {
	public static final CustomPayload.Id<PurpleEndPayload> ID = new CustomPayload.Id<>(Gojolander.id("purple_end"));
	public static final PacketCodec<RegistryByteBuf, PurpleEndPayload> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT,
			PurpleEndPayload::id, PurpleEndPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
