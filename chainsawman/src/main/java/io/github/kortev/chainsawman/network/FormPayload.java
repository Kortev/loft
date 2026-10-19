package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

/** Whether a player is in the form now: sent to everyone when it changes, and to a player joining for everyone in it. */
public record FormPayload(UUID player, boolean on) implements CustomPayload {
	public static final CustomPayload.Id<FormPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("form"));
	public static final PacketCodec<RegistryByteBuf, FormPayload> CODEC = PacketCodec.tuple(Uuids.PACKET_CODEC, FormPayload::player,
			PacketCodecs.BOOL, FormPayload::on, FormPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
