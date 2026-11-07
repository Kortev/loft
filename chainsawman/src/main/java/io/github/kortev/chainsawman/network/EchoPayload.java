package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

/**
 * Whether a player has an Echo going: sent to everyone when it starts (the crowd, {@code kind} the way the player is disguised
 * among the copies: past or future) and when it ends ({@code kind} -1), and to a player joining. {@code finale} is the
 * world time the finale began (-1 before it). Clients use it to draw the owner as one of the copies and for the time-warp look.
 */
public record EchoPayload(UUID owner, byte kind, int target, int finale) implements CustomPayload {
	public static final CustomPayload.Id<EchoPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("echo"));
	public static final PacketCodec<RegistryByteBuf, EchoPayload> CODEC = PacketCodec.tuple(Uuids.PACKET_CODEC, EchoPayload::owner,
			PacketCodecs.BYTE, EchoPayload::kind, PacketCodecs.VAR_INT, EchoPayload::target, PacketCodecs.INTEGER, EchoPayload::finale, EchoPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
