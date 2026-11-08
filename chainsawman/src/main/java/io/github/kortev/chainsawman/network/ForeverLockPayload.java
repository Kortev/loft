package io.github.kortev.chainsawman.network;

import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * A living thing (by entity id) is locked in an endless moment ({@code on}) or let go: sent to everyone, and to a player joining. A lock
 * carries the state the thing was frozen in (where, which way it faces, how far into a stride), so every client puts it exactly there once
 * and holds it, with nothing left to interpolate or sway.
 */
public record ForeverLockPayload(int target, boolean on, double x, double y, double z, float yaw, float pitch, float headYaw, float bodyYaw, float limbPos,
		float limbSpeed) implements CustomPayload {
	public static final CustomPayload.Id<ForeverLockPayload> ID = new CustomPayload.Id<>(ChainsawMan.id("forever_lock"));
	public static final PacketCodec<RegistryByteBuf, ForeverLockPayload> CODEC = PacketCodec.of((value, buf) -> {
		buf.writeVarInt(value.target);
		buf.writeBoolean(value.on);
		buf.writeDouble(value.x);
		buf.writeDouble(value.y);
		buf.writeDouble(value.z);
		buf.writeFloat(value.yaw);
		buf.writeFloat(value.pitch);
		buf.writeFloat(value.headYaw);
		buf.writeFloat(value.bodyYaw);
		buf.writeFloat(value.limbPos);
		buf.writeFloat(value.limbSpeed);
	}, buf -> new ForeverLockPayload(buf.readVarInt(), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
			buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

	public static ForeverLockPayload off(int target) {
		return new ForeverLockPayload(target, false, 0.0, 0.0, 0.0, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
