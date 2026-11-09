package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** The "Switch contract" key held, not tapped, with Forever selected: "Not yet" (a bond-3 move). */
public record NotYetKeyPayload() implements CustomPayload {
	public static final CustomPayload.Id<NotYetKeyPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("not_yet_key"));
	public static final PacketCodec<RegistryByteBuf, NotYetKeyPayload> CODEC = PacketCodec.unit(new NotYetKeyPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
