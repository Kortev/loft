package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

/** Which contract a player holds ({@code Contract} ordinal, 0 for none): sent to everyone when it changes, and to a player joining. */
public record ContractPayload(UUID player, byte contract) implements CustomPayload {
	public static final CustomPayload.Id<ContractPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("contract"));
	public static final PacketCodec<RegistryByteBuf, ContractPayload> CODEC = PacketCodec.tuple(Uuids.PACKET_CODEC, ContractPayload::player,
			PacketCodecs.BYTE, ContractPayload::contract, ContractPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
