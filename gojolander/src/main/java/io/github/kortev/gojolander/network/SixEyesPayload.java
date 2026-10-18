package io.github.kortev.gojolander.network;

import io.github.kortev.gojolander.Gojolander;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** How far this player sees through the blindfold with the Six Eyes now, in blocks (0: not at all). */
public record SixEyesPayload(int range) implements CustomPayload {
	public static final CustomPayload.Id<SixEyesPayload> ID = new CustomPayload.Id<>(Gojolander.id("six_eyes"));
	public static final PacketCodec<RegistryByteBuf, SixEyesPayload> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, SixEyesPayload::range,
			SixEyesPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
