package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

/** A player has just pulled the cord: everyone sees their arm yank it out, and the form comes at the burst. */
public record FormPullPayload(UUID player) implements CustomPayload {
	public static final CustomPayload.Id<FormPullPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("form_pull"));
	public static final PacketCodec<RegistryByteBuf, FormPullPayload> CODEC = PacketCodec.tuple(Uuids.PACKET_CODEC, FormPullPayload::player,
			FormPullPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
