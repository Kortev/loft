package io.github.kortev.shootingstar.network;

import io.github.kortev.shootingstar.ShootingStar;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.Vec3d;

/** A player is about to be carried from one place to another, {@code delay} ticks from now, by the Ginnungagap event. */
public record GapWarpPayload(UUID player, Vec3d from, Vec3d to, int delay) implements CustomPayload {
	public static final CustomPayload.Id<GapWarpPayload> ID = new CustomPayload.Id<>(ShootingStar.id("gap_warp"));
	private static final PacketCodec<RegistryByteBuf, Vec3d> VEC = PacketCodec.tuple(
			PacketCodecs.DOUBLE, Vec3d::getX,
			PacketCodecs.DOUBLE, Vec3d::getY,
			PacketCodecs.DOUBLE, Vec3d::getZ,
			Vec3d::new);
	public static final PacketCodec<RegistryByteBuf, GapWarpPayload> CODEC = PacketCodec.tuple(
			Uuids.PACKET_CODEC, GapWarpPayload::player,
			VEC, GapWarpPayload::from,
			VEC, GapWarpPayload::to,
			PacketCodecs.VAR_INT, GapWarpPayload::delay,
			GapWarpPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
