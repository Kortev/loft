package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Something done in the form, from the one doing it: a slash of the saws, the saws held grinding (or let go), the chain
 * fired, the ult (Beam) called up, a lunge (attacking while sprinting), the chain let go. A slash says what was under their
 * crosshair ({@code target}, an entity's id; -1 for nothing), so a hit that looked good to them lands. The server decides
 * whether anything comes of it.
 */
public record FormActionPayload(byte action, boolean on, int target) implements CustomPayload {
	public static final byte SLASH = 0;
	public static final byte GRIND = 1;
	public static final byte CHAIN = 2;
	/** The ult key: Beam (it was the blood frenzy's, and keeps its number). */
	public static final byte FRENZY = 3;
	public static final byte LUNGE = 4;
	public static final byte RELEASE = 5;
	public static final byte KICK = 6;

	public static final CustomPayload.Id<FormActionPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("form_action"));
	public static final PacketCodec<RegistryByteBuf, FormActionPayload> CODEC = PacketCodec.tuple(PacketCodecs.BYTE, FormActionPayload::action,
			PacketCodecs.BOOL, FormActionPayload::on, PacketCodecs.VAR_INT, FormActionPayload::target, FormActionPayload::new);

	public FormActionPayload(byte action, boolean on) {
		this(action, on, -1);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
