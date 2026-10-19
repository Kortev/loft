package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

/**
 * What a player in the form has done, told to everyone so they see it: a slash ({@code value} the step of the combo),
 * grinding on or off ({@code value} 1 or 0), the chain fired ({@code value} the entity it caught or -1, {@code x y z}
 * where it went), downed or back up ({@code value} 1 or 0), the frenzy ({@code value} its ticks left, 0 when over).
 */
public record FormEventPayload(UUID player, byte kind, int value, double x, double y, double z) implements CustomPayload {
	public static final byte SWING = 0;
	public static final byte GRIND = 1;
	public static final byte CHAIN = 2;
	public static final byte DOWNED = 3;
	public static final byte FRENZY = 4;

	public static final CustomPayload.Id<FormEventPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("form_event"));
	public static final PacketCodec<RegistryByteBuf, FormEventPayload> CODEC = PacketCodec.tuple(Uuids.PACKET_CODEC, FormEventPayload::player,
			PacketCodecs.BYTE, FormEventPayload::kind, PacketCodecs.VAR_INT, FormEventPayload::value, PacketCodecs.DOUBLE, FormEventPayload::x,
			PacketCodecs.DOUBLE, FormEventPayload::y, PacketCodecs.DOUBLE, FormEventPayload::z, FormEventPayload::new);

	public FormEventPayload(UUID player, byte kind, int value) {
		this(player, kind, value, 0.0, 0.0, 0.0);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
