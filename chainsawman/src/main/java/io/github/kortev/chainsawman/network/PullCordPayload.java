package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** The cord key, pressed by someone carrying Pochita. The server decides whether anything comes of it. */
public record PullCordPayload() implements CustomPayload {
	public static final CustomPayload.Id<PullCordPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("pull_cord"));
	public static final PacketCodec<RegistryByteBuf, PullCordPayload> CODEC = PacketCodec.unit(new PullCordPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
