package io.github.kortev.gojolander.network;

import io.github.kortev.gojolander.Gojolander;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

/**
 * A Hollow Purple cast: who cast it ({@link #NOBODY} for one an operator called), where its sphere forms (the caster's
 * eyes), the way it flies (a unit vector), its radius and how far it flies. {@code age} is non-zero when telling someone
 * of one already under way.
 */
public record PurpleLaunchPayload(int id, UUID caster, Vec3d eye, Vec3d dir, float radius, float range, int age) implements CustomPayload {
	public static final UUID NOBODY = new UUID(0L, 0L);
	public static final CustomPayload.Id<PurpleLaunchPayload> ID = new CustomPayload.Id<>(Gojolander.id("purple_launch"));
	public static final PacketCodec<RegistryByteBuf, PurpleLaunchPayload> CODEC = PacketCodec.of(PurpleLaunchPayload::write,
			PurpleLaunchPayload::read);

	private void write(RegistryByteBuf buf) {
		buf.writeVarInt(id);
		buf.writeUuid(caster);
		buf.writeDouble(eye.x);
		buf.writeDouble(eye.y);
		buf.writeDouble(eye.z);
		buf.writeDouble(dir.x);
		buf.writeDouble(dir.y);
		buf.writeDouble(dir.z);
		buf.writeFloat(radius);
		buf.writeFloat(range);
		buf.writeVarInt(age);
	}

	private static PurpleLaunchPayload read(RegistryByteBuf buf) {
		return new PurpleLaunchPayload(buf.readVarInt(), buf.readUuid(), new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
				new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(), buf.readVarInt());
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
