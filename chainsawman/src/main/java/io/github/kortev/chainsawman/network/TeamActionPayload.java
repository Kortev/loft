package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** What someone who is down asks of the server: to give up and die at once, or to struggle against a hand tearing at their heart. */
public record TeamActionPayload(byte action) implements CustomPayload {
	public static final byte GIVE_UP = 0;
	public static final byte STRUGGLE = 1;

	public static final CustomPayload.Id<TeamActionPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("team_action"));
	public static final PacketCodec<RegistryByteBuf, TeamActionPayload> CODEC = PacketCodec.tuple(PacketCodecs.BYTE, TeamActionPayload::action,
			TeamActionPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
