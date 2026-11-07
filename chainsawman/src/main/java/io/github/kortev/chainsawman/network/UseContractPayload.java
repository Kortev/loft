package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** The "Use contract" key, pressed: the server decides what the contract the player holds does. */
public record UseContractPayload() implements CustomPayload {
	public static final CustomPayload.Id<UseContractPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("use_contract"));
	public static final PacketCodec<RegistryByteBuf, UseContractPayload> CODEC = PacketCodec.unit(new UseContractPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
