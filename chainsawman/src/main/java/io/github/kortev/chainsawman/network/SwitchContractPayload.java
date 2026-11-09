package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** The "Switch contract" key, pressed: the server moves the selected slot on to the next contract the player holds. */
public record SwitchContractPayload() implements CustomPayload {
	public static final CustomPayload.Id<SwitchContractPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("switch_contract"));
	public static final PacketCodec<RegistryByteBuf, SwitchContractPayload> CODEC = PacketCodec.unit(new SwitchContractPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
