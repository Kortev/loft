package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Something done in the form, from the one doing it: a slash of the saws, the saws held grinding (or let go), the chain
 * fired, the frenzy called up, a lunge (attacking while sprinting), the chain let go. The server decides whether
 * anything comes of it.
 */
public record FormActionPayload(byte action, boolean on) implements CustomPayload {
	public static final byte SLASH = 0;
	public static final byte GRIND = 1;
	public static final byte CHAIN = 2;
	public static final byte FRENZY = 3;
	public static final byte LUNGE = 4;
	public static final byte RELEASE = 5;

	public static final CustomPayload.Id<FormActionPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("form_action"));
	public static final PacketCodec<RegistryByteBuf, FormActionPayload> CODEC = PacketCodec.tuple(PacketCodecs.BYTE, FormActionPayload::action,
			PacketCodecs.BOOL, FormActionPayload::on, FormActionPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
