package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Time catches up with the player who called Eternity: after the release their screen stays grey for this many ticks. */
public record ForeverCatchUpPayload(int ticks) implements CustomPayload {
	public static final CustomPayload.Id<ForeverCatchUpPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("forever_catch_up"));
	public static final PacketCodec<RegistryByteBuf, ForeverCatchUpPayload> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, ForeverCatchUpPayload::ticks, ForeverCatchUpPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
