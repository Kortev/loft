package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

/** To everyone: {@code player} has stepped out of time ("Not yet", {@code ticks} long), or is back ({@code ticks} 0). */
public record NotYetPayload(UUID player, int ticks) implements CustomPayload {
	public static final CustomPayload.Id<NotYetPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("not_yet"));
	public static final PacketCodec<RegistryByteBuf, NotYetPayload> CODEC = PacketCodec.tuple(Uuids.PACKET_CODEC, NotYetPayload::player,
			PacketCodecs.VAR_INT, NotYetPayload::ticks, NotYetPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
