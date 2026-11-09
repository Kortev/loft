package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** To a player alone: how many counted uses a contract ({@code Contract} ordinal) has had, and whether this is a bond-up (show the moment). */
public record BondPayload(byte contract, int uses, boolean up) implements CustomPayload {
	public static final CustomPayload.Id<BondPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("bond"));
	public static final PacketCodec<RegistryByteBuf, BondPayload> CODEC = PacketCodec.tuple(PacketCodecs.BYTE, BondPayload::contract,
			PacketCodecs.VAR_INT, BondPayload::uses, PacketCodecs.BOOL, BondPayload::up, BondPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
