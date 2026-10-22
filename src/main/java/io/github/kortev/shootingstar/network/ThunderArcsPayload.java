package io.github.kortev.shootingstar.network;

import io.github.kortev.shootingstar.ShootingStar;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * The arcs off Mjölnir's bolt, seven numbers to an arc: the point it jumps from, the point it lands on, and how many
 * ticks after the stroke it lands.
 */
public record ThunderArcsPayload(int strikeId, List<Float> arcs) implements CustomPayload {
	public static final CustomPayload.Id<ThunderArcsPayload> ID = new CustomPayload.Id<>(ShootingStar.id("thunder_arcs"));
	public static final PacketCodec<RegistryByteBuf, ThunderArcsPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, ThunderArcsPayload::strikeId,
			PacketCodecs.FLOAT.collect(PacketCodecs.toList()), ThunderArcsPayload::arcs,
			ThunderArcsPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
