package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

/**
 * A player has just pulled the cord: everyone sees their arm yank it out, and the form comes at the burst. {@code skip}
 * is how many ticks of the transformation are already past (0, or {@code FormTimeline.REVIVE_SKIP} when a friend pulled
 * the cord of someone who was down).
 */
public record FormPullPayload(UUID player, int skip) implements CustomPayload {
	public static final CustomPayload.Id<FormPullPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("form_pull"));
	public static final PacketCodec<RegistryByteBuf, FormPullPayload> CODEC = PacketCodec.tuple(Uuids.PACKET_CODEC, FormPullPayload::player,
			PacketCodecs.VAR_INT, FormPullPayload::skip, FormPullPayload::new);

	public FormPullPayload(UUID player) {
		this(player, 0);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
