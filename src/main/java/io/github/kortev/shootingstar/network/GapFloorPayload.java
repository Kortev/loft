package io.github.kortev.shootingstar.network;

import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * The floor of nothing this player walks on while their world is gone and coming back: how high it is, or NaN
 * when they are back on the ground of the world.
 */
public record GapFloorPayload(double floor) implements CustomPayload {
	public static final CustomPayload.Id<GapFloorPayload> ID = new CustomPayload.Id<>(ShootingStar.id("gap_floor"));
	public static final PacketCodec<RegistryByteBuf, GapFloorPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.DOUBLE, GapFloorPayload::floor,
			GapFloorPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
