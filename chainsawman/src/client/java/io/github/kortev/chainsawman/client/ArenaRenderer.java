package io.github.kortev.chainsawman.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.github.kortev.chainsawman.hotel.EternityDevilEntity;
import io.github.kortev.chainsawman.hotel.HotelBuilder;
import io.github.kortev.chainsawman.hotel.Shaft;
import io.github.kortev.chainsawman.hotel.ShaftArmEntity;
import io.github.kortev.chainsawman.hotel.ShaftHeadEntity;
import io.github.kortev.chainsawman.network.HotelPayloads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The inside of the Eternity Devil, the arena at the foot of the shaft ({@link Shaft}), drawn round the devil entity (sunk in its floor). Nothing
 * there is a block (the walls are {@code ArenaFleshBlock}s nothing draws): it is one flesh model at the pixel size of everything else in it
 * ({@code tools/eternity_3d_room.py}, 1/16 block, drawn at scale 1): floor, ceiling, throat, the mouth's hollow and four walls, each its own
 * part so the long walls can move in with the rounds and the end walls thrust in for the squeeze. On it:
 * <ul>
 * <li>one light, from itself: dim wet red, its veins brighter, the whole room beating faster each round, the same on walls and limbs;</li>
 * <li>the tells in the floor's veins: those under a slam's lane, a head's line, a hand's sweep, the squeezing half light and beat faster to
 * the strike;</li>
 * <li>heads half sunk in the walls watching the nearest player; stumps of torn limbs left in the walls and floor, bleeding;</li>
 * <li>the core's window: the far end wall bulging and splitting (the core comes out of it: {@code ShaftCoreRenderer});</li>
 * <li>convulsing at the scream, the laugh, the beg; every cut's wound for a moment; the mouth above ({@link ShaftMassRenderer}).</li>
 * </ul>
 */
final class ArenaRenderer {
	private ArenaRenderer() {
	}

	/** The heads half sunk in the walls (hotel-local: x, height over the floor, z; the wall they are in), as many as the round allows. */
	private static final double[][] HEADS = {{Shaft.AX0, 4.3, 3.5, Shaft.W_X0}, {Shaft.AX1 + 1, 4.1, 9.6, Shaft.W_X1}, {16.5, 4.4, Shaft.AZ0, Shaft.W_Z0},
			{22.5, 4.2, Shaft.AZ1 + 1, Shaft.W_Z1}, {Shaft.AX0, 4.0, 9.6, Shaft.W_X0}, {Shaft.AX1 + 1, 4.3, 3.4, Shaft.W_X1}, {13.6, 4.2, Shaft.AZ1 + 1, Shaft.W_Z1},
			{25.4, 4.1, Shaft.AZ0, Shaft.W_Z0}, {19.5, 4.6, Shaft.AZ0, Shaft.W_Z0}, {19.5, 4.6, Shaft.AZ1 + 1, Shaft.W_Z1}};
	/** Each wall's inward direction in the room's frame (x, z). */
	static final float[][] INWARD = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private static List<float[]> veins;

	/** The hotel's turn from the way the devil faces. */
	static int rotOf(float facing) {
		for (int r = 0; r < 4; r++) {
			if (Math.abs(MathHelper.wrapDegrees(HotelBuilder.alongYaw(r) + 180.0F - facing)) < 1.0F) {
				return r;
			}
		}
		return 0;
	}

	/** A local direction (or difference) in the world. */
	static Vec3d dir(int rot, double x, double y, double z) {
		return HotelBuilder.dir(rot, x, y, z);
	}

	/** A local point, from the devil (which is at the arena's {@link Shaft#MID_X}, {@link Shaft#DEVIL_Y}, {@link Shaft#MID_Z}). */
	static Vec3d at(int rot, double x, double y, double z) {
		return dir(rot, x - Shaft.MID_X, y - Shaft.DEVIL_Y, z - Shaft.MID_Z);
	}

	static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** The beat: a quick double pulse each {@code period} ticks, 0 to 1. */
	static float beat(float t, float period, float phase) {
		float p = ((t / period + phase) % 1.0F + 1.0F) % 1.0F;
		float a = (float) Math.exp(-Math.pow((p - 0.08F) / 0.05F, 2.0));
		float b = 0.6F * (float) Math.exp(-Math.pow((p - 0.24F) / 0.06F, 2.0));
		return Math.max(a, b);
	}

	/** A room-frame direction turned with the hotel: x along the arena, z across. */
	static Matrix4f turn(int rot) {
		Vec3d x = dir(rot, 1, 0, 0);
		Vec3d z = dir(rot, 0, 0, 1);
		return new Matrix4f((float) x.x, 0.0F, (float) x.z, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, (float) z.x, 0.0F, (float) z.z, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
	}

	/** The room model's frame at {@code base} (the devil's place): turned with the hotel, its origin the arena's middle on the floor. */
	static Matrix4f roomFrame(Matrix4f base, int rot) {
		Vec3d o = at(rot, Shaft.MID_X, Shaft.FLOOR, Shaft.MID_Z);
		return new Matrix4f(base).translate((float) o.x, (float) o.y, (float) o.z).mul(turn(rot));
	}

	/** The one light of the room: dim wet red, the same on walls, floor, limbs, heads and core ({@code k}: the beat's lift). */
	static FleshMesh.Look look(float k) {
		FleshMesh.Look look = new FleshMesh.Look().light(LightmapTextureManager.pack(15, 15), 0.7F, 1.0F);
		look.r = 1.0F * k;
		look.g = 0.86F * k;
		look.b = 0.84F * k;
		look.rim = 0.3F;
		return look;
	}

	/** The light of the room as the nearest devil has it now (for the limbs and the core: the same as the walls). */
	static FleshMesh.Look lookNear(ClientWorld world, net.minecraft.entity.Entity near, float now) {
		for (EternityDevilEntity d : world.getEntitiesByClass(EternityDevilEntity.class, near.getBoundingBox().expand(24.0), EternityDevilEntity::shaft)) {
			return look(lift(d, now));
		}
		return look(1.0F);
	}

	/** The beat's lift on the room's light now (faster each round, a flare at a convulsion). */
	static float lift(EternityDevilEntity devil, float now) {
		int round = Shaft.stage(devil);
		float period = round == 0 ? 30.0F : round == 1 ? 22.0F : 15.0F;
		return 1.0F + 0.07F * beat(now, period, 0.0F) + 0.25F * convulse(devil, now);
	}

	/** How hard the room convulses now: the scream as its core is forced out, the laugh as a limb grows back, the beg. */
	static float convulse(EternityDevilEntity devil, float now) {
		NbtCompound tag = devil.arena();
		float c = 0.0F;
		long windowAt = tag.contains("windowAt") ? tag.getLong("windowAt") : -1;
		if (windowAt >= 0 && now - windowAt < Shaft.WINDOW_TELL + 10) {
			c = Math.max(c, 1.0F - Math.max(0.0F, now - windowAt - Shaft.WINDOW_TELL) / 10.0F);
		}
		long laugh = tag.contains("laughAt") ? tag.getLong("laughAt") : -1;
		if (laugh >= 0 && now - laugh < 36) {
			c = Math.max(c, 0.6F * (1.0F - (now - laugh) / 36.0F));
		}
		float sinceBeg = ClientHotel.sinceBeg(0.0F);
		if (sinceBeg >= 0.0F && devil.pain() > 0.999F) {
			c = Math.max(c, MathHelper.clamp(1.0F - (sinceBeg - 40.0F) / 30.0F, 0.0F, 1.0F));
		}
		return c;
	}

	/** How far each wall is in from where it first stands (blocks): the end walls (the squeeze's thrust, the window's bulge), the long ones (the rounds). */
	static double[] walls(EternityDevilEntity devil, float now) {
		NbtCompound tag = devil.arena();
		int inset = tag.getInt("inset");
		long wallAt = tag.contains("wallAt") ? tag.getLong("wallAt") : -1;
		double in = inset;
		if (wallAt >= 0) {
			float k = ease((now - wallAt) / Shaft.WALL_MOVE);
			in = tag.getInt("wallFrom") + (inset - tag.getInt("wallFrom")) * k;
		}
		double[] w = {0.0, 0.0, in, in};
		for (ClientHotel.Event e : ClientHotel.events(devil.getId())) {
			if (e.kind() != HotelPayloads.Devil.SQUEEZE) {
				continue;
			}
			float t = now - e.start();
			if (t < 0.0F || t > Shaft.SQUEEZE_TELL + Shaft.SQUEEZE_SHUT + 16) {
				continue;
			}
			int half = ClientHotel.squeezeHalf(e);
			double out;
			if (t < Shaft.SQUEEZE_TELL) {
				// The tell: the end wall swells and throbs, faster to the clench.
				float k = t / Shaft.SQUEEZE_TELL;
				out = 0.12 + 0.25 * k + 0.08 * Math.sin(now * (0.4 + 1.2 * k));
			} else {
				float s = t - Shaft.SQUEEZE_TELL;
				double reach = Shaft.SQUEEZE_REACH - 0.6;
				out = reach * ease(s / 3.0F) * (1.0 - ease((s - Shaft.SQUEEZE_SHUT) / 14.0F));
			}
			w[half] = Math.max(w[half], out);
		}
		long windowAt = tag.contains("windowAt") ? tag.getLong("windowAt") : -1;
		if (windowAt >= 0 && now - windowAt < Shaft.WINDOW_TELL + 6) {
			int side = tag.getInt("coreSide");
			w[side] = Math.max(w[side], 0.25 * ease((now - windowAt) / 8.0F) + 0.06 * Math.sin(now * 1.7));
		}
		return w;
	}

	/** The floor's veins (room frame: x0, z0, x1, z1 a piece), from the room model's json. */
	static List<float[]> veins() {
		if (veins == null) {
			List<float[]> out = new ArrayList<>();
			FleshMesh mesh = FleshMesh.get("room");
			if (mesh.info != null && mesh.info.has("floorVeins")) {
				for (JsonElement line : mesh.info.getAsJsonArray("floorVeins")) {
					JsonArray a = line.getAsJsonArray();
					for (int i = 0; i + 3 < a.size(); i += 2) {
						out.add(new float[] {a.get(i).getAsFloat(), a.get(i + 1).getAsFloat(), a.get(i + 2).getAsFloat(), a.get(i + 3).getAsFloat()});
					}
				}
			}
			veins = out;
		}
		return veins;
	}

	/** One tell's region on the floor (devil-relative world x, z) and how near it is to striking (0 to 1). */
	private interface Region {
		boolean in(double x, double z);
	}

	private record Tell(Region region, float k) {
	}

	private static Region lane(Vec3d from, Vec3d d, double len, double half) {
		return (x, z) -> {
			double rx = x - from.x;
			double rz = z - from.z;
			double along = rx * d.x + rz * d.z;
			double across = Math.abs(rx * d.z - rz * d.x);
			return along >= -0.2 && along <= len + 0.2 && across <= half;
		};
	}

	/** The tells out now: the limbs coming, the squeeze, the core's window. */
	private static List<Tell> tells(EternityDevilEntity devil, ClientWorld world, int rot, Vec3d devilPos, float now) {
		List<Tell> out = new ArrayList<>();
		Box box = devil.getBoundingBox().expand(24.0);
		for (ShaftArmEntity a : world.getEntitiesByClass(ShaftArmEntity.class, box, e -> e.outcome() == ShaftArmEntity.PENDING)) {
			float t = a.age(0.0F);
			Vec3d p = a.getPos().subtract(devilPos);
			if (a.mode() == ShaftArmEntity.SLAM) {
				out.add(new Tell(lane(p, a.dir(), a.len(), ShaftArmEntity.LANE / 2.0), MathHelper.clamp(t / ShaftArmEntity.SLAM_TELL, 0.0F, 1.0F)));
			} else if (a.mode() == ShaftArmEntity.GRAB) {
				Vec3d in = a.dir();
				double r = ShaftArmEntity.REACH_GRAB + ShaftArmEntity.HAND * 0.5;
				out.add(new Tell((x, z) -> {
					double rx = x - p.x;
					double rz = z - p.z;
					return rx * rx + rz * rz <= r * r && rx * in.x + rz * in.z >= -0.2;
				}, MathHelper.clamp(t / ShaftArmEntity.GRAB_TELL, 0.0F, 1.0F)));
			}
		}
		for (ShaftHeadEntity h : world.getEntitiesByClass(ShaftHeadEntity.class, box, e -> e.outcome() == ShaftHeadEntity.PENDING && e.locked())) {
			float t = h.age(0.0F);
			out.add(new Tell(lane(h.getPos().subtract(devilPos), h.dir(), ShaftHeadEntity.REACH + 0.7, ShaftHeadEntity.BITE_RADIUS + 0.2),
					MathHelper.clamp((t - ShaftHeadEntity.UP) / ShaftHeadEntity.REAR, 0.0F, 1.0F)));
		}
		for (ClientHotel.Event e : ClientHotel.events(devil.getId())) {
			if (e.kind() == HotelPayloads.Devil.SQUEEZE) {
				float t = now - e.start();
				if (t >= 0.0F && t < Shaft.SQUEEZE_TELL + 2) {
					int half = ClientHotel.squeezeHalf(e);
					double lx = half == 0 ? Shaft.AX0 + Shaft.SQUEEZE_REACH : Shaft.AX1 + 1 - Shaft.SQUEEZE_REACH;
					Vec3d d = dir(rot, half == 0 ? -1 : 1, 0, 0);
					Vec3d line = at(rot, lx, Shaft.FLOOR, Shaft.MID_Z);
					out.add(new Tell((x, z) -> (x - line.x) * d.x + (z - line.z) * d.z >= -0.1, t / Shaft.SQUEEZE_TELL));
				}
			}
		}
		NbtCompound tag = devil.arena();
		long windowAt = tag.contains("windowAt") ? tag.getLong("windowAt") : -1;
		if (windowAt >= 0) {
			// The core's window: the veins run to the end wall it comes out of.
			int side = tag.getInt("coreSide");
			Vec3d d = dir(rot, side == 0 ? -1 : 1, 0, 0);
			Vec3d mid = at(rot, Shaft.MID_X, Shaft.FLOOR, Shaft.MID_Z);
			float k = MathHelper.clamp((now - windowAt) / Shaft.WINDOW_TELL, 0.0F, 1.0F);
			out.add(new Tell((x, z) -> (x - mid.x) * d.x + (z - mid.z) * d.z > 8.5 - 9.0 * k && Math.abs((x - mid.x) * d.z - (z - mid.z) * d.x) < 2.2 + 3.0 * k,
					0.5F * k));
		}
		return out;
	}

	static void render(EternityDevilEntity devil, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(devil.getWorld() instanceof ClientWorld world)) {
			return;
		}
		int rot = rotOf(devil.facing());
		float now = world.getTime() + delta;
		Vec3d devilPos = devil.getLerpedPos(delta);
		boolean dead = devil.isDead();
		float dying = dead ? ease((devil.deathTime + delta) / EternityDevilEntity.DEATH_TICKS) : 0.0F;
		float convulse = convulse(devil, now);
		float flinch = 0.0F;
		for (ClientHotel.Wound w : ClientHotel.wounds()) {
			flinch = Math.max(flinch, 1.0F - (world.getTime() + delta - w.start()) / 6.0F);
		}
		flinch = MathHelper.clamp(flinch, 0.0F, 1.0F);
		float k = lift(devil, now) * (1.0F - 0.4F * dying);
		Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
		Matrix4f room = roomFrame(base, rot);
		float shake = convulse * 0.06F + flinch * 0.015F;
		if (shake > 0.0F) {
			room.translate(MathHelper.sin(now * 2.7F) * shake, MathHelper.sin(now * 3.3F) * shake * 0.5F, MathHelper.sin(now * 2.3F) * shake);
		}
		FleshMesh mesh = FleshMesh.get("room");
		FleshMesh.Look look = look(k);
		double[] w = walls(devil, now);
		for (String name : new String[] {"floor", "ceiling", "throat", "chamber"}) {
			FleshMesh.Part p = mesh.byName.get(name);
			if (p != null) {
				mesh.drawPart(p, room, look, room);
			}
		}
		String[] wallNames = {"wall_x0", "wall_x1", "wall_z0", "wall_z1"};
		for (int i = 0; i < 4; i++) {
			FleshMesh.Part p = mesh.byName.get(wallNames[i]);
			if (p != null) {
				Matrix4f m = new Matrix4f(room).translate((float) (INWARD[i][0] * w[i]), 0.0F, (float) (INWARD[i][1] * w[i]));
				mesh.drawPart(p, m, look, m);
			}
		}
		// ---- The heads in the walls: 4, 7, then all 10 watching the nearest player (as far as a head sunk in a wall can turn).
		int round = Shaft.stage(devil);
		boolean begging = ClientHotel.sinceBeg(delta) >= 0.0F && devil.pain() > 0.999F;
		int heads = begging ? HEADS.length : round == 0 ? 4 : round == 1 ? 7 : HEADS.length;
		PlayerEntity near = world.getClosestPlayer(devilPos.x, devilPos.y + 4.0, devilPos.z, 24.0, false);
		FleshMesh headMesh = FleshMesh.get("head");
		for (int i = 0; i < heads; i++) {
			double[] hd = HEADS[i];
			int wall = (int) hd[3];
			Vec3d inward = new Vec3d(INWARD[wall][0], 0.0, INWARD[wall][1]);
			Vec3d spot = new Vec3d(hd[0] - Shaft.MID_X, hd[1], hd[2] - Shaft.MID_Z).add(inward.multiply(w[wall]));
			double theta = Math.atan2(-inward.z, inward.x);
			if (near != null) {
				Vec3d spotWorld = devilPos.add(at(rot, spot.x + Shaft.MID_X, spot.y + Shaft.FLOOR, spot.z + Shaft.MID_Z));
				Vec3d to = near.getEyePos().subtract(spotWorld);
				// (Back into the room's frame: the room's matrix carries the hotel's turn.)
				Vec3d lto = HotelBuilder.dir((4 - rot) & 3, to.x, to.y, to.z);
				double want = Math.atan2(-lto.z, lto.x);
				theta += Math.toRadians(MathHelper.clamp(MathHelper.wrapDegrees(Math.toDegrees(want - theta)), -60.0, 60.0));
			}
			// Sunk so the back of the skull is in the wall: the face and jaw out of it.
			Vec3d p = spot.subtract(inward.multiply(0.5 - 0.06 * Math.sin(now * 0.05 + i)));
			Matrix4f m = new Matrix4f(room).translate((float) p.x, (float) (p.y - 1.45), (float) p.z).rotateY((float) theta);
			FleshMesh.Look hl = look(k);
			hl.glowAt = new Vector3f(0.45F, 1.5F, 0.0F);
			hl.glowRange = 0.5F;
			hl.glowR = 0.6F + 0.6F * convulse;
			hl.glowG = 0.2F;
			hl.glowB = 0.04F;
			float jaw = ShaftHeadRenderer.JAW_OPEN * (0.5F + 0.5F * MathHelper.sin(now * 0.09F + i * 1.7F)) * (begging ? 0.2F : 1.0F);
			FleshMesh.Hook jawHook = ShaftHeadRenderer.jawHook(jaw);
			// (No cords hanging under a head sunk in a wall: nothing floats.)
			headMesh.draw(m, now + i * 13.0F, hl, (part, pm, pb) -> !part.kind.equals("cord") && jawHook.pose(part, pm, pb));
		}
		// ---- The stumps of torn limbs, in the walls they came out of (moving with them), bleeding.
		NbtList stumps = devil.arena().getList("stumps", NbtElement.COMPOUND_TYPE);
		FleshMesh.Part stumpArm = mesh.byName.get("stump_arm");
		FleshMesh.Part stumpHead = mesh.byName.get("stump_head");
		for (int i = 0; i < stumps.size(); i++) {
			NbtCompound s = stumps.getCompound(i);
			int wall = s.getInt("w");
			double u = s.getDouble("u");
			double v = s.getDouble("v");
			Vec3d at;
			Vec3d axis;
			if (wall == Shaft.W_FLOOR) {
				at = new Vec3d(u - Shaft.MID_X, 0.0, v - Shaft.MID_Z);
				axis = new Vec3d(0, 1, 0);
			} else {
				double hgt = Math.max(v, 0.45);
				axis = new Vec3d(INWARD[wall][0], 0.0, INWARD[wall][1]);
				at = wall <= Shaft.W_X1 ? new Vec3d(wall == Shaft.W_X0 ? Shaft.AX0 - Shaft.MID_X + w[0] : Shaft.AX1 + 1 - Shaft.MID_X - w[1], hgt, u - Shaft.MID_Z)
						: new Vec3d(u - Shaft.MID_X, hgt, wall == Shaft.W_Z0 ? Shaft.AZ0 - Shaft.MID_Z + w[2] : Shaft.AZ1 + 1 - Shaft.MID_Z - w[3]);
			}
			Quaternionf q = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) axis.x, (float) axis.y, (float) axis.z);
			Matrix4f m = new Matrix4f(room).translate((float) at.x, (float) at.y, (float) at.z).rotate(q);
			FleshMesh.Part part = s.getByte("k") == Shaft.LIMB_HEAD ? stumpHead : stumpArm;
			if (part != null) {
				FleshMesh.Look sl = look(k);
				sl.hurt = 0.4F * MathHelper.clamp(1.0F - (now - s.getLong("at")) / 60.0F, 0.0F, 1.0F);
				mesh.drawPart(part, m, sl, m);
			}
			// Bleeding: a drop now and then from the torn end.
			if (world.random.nextInt(14) == 0) {
				Vec3d tip = devilPos.add(at(rot, at.x + axis.x * 0.45 + Shaft.MID_X, at.y + axis.y * 0.45 + Shaft.FLOOR, at.z + axis.z * 0.45 + Shaft.MID_Z));
				Vec3d wd = dir(rot, axis.x, axis.y, axis.z);
				world.addParticle(BLOOD, tip.x, tip.y, tip.z, wd.x * 0.05, -0.05, wd.z * 0.05);
			}
		}
		// ---- The tells, in the floor's veins: those in an attack's way light up, beating faster to the strike.
		Glow g = new Glow(provider, matrices);
		List<Tell> tells = tells(devil, world, rot, devilPos, now);
		if (!tells.isEmpty()) {
			Vec3d up = new Vec3d(0, 1, 0);
			Vec3d o = at(rot, Shaft.MID_X, Shaft.FLOOR + 0.025, Shaft.MID_Z);
			for (float[] s : veins()) {
				Vec3d a = o.add(dir(rot, s[0], 0, s[1]));
				Vec3d b = o.add(dir(rot, s[2], 0, s[3]));
				double mx = (a.x + b.x) / 2.0;
				double mz = (a.z + b.z) / 2.0;
				float best = -1.0F;
				for (Tell t : tells) {
					if (t.region().in(mx, mz)) {
						best = Math.max(best, t.k());
					}
				}
				if (best < 0.0F) {
					continue;
				}
				float pulse = beat(now, 14.0F - 9.0F * best, (float) (mx * 0.05));
				float alpha = Math.min(1.0F, 0.45F + 0.4F * best + 0.3F * pulse);
				g.line(a, b, up, 0.11 + 0.05 * best, 1.0F, 0.42F + 0.25F * best, 0.1F, alpha);
			}
		}
		// ---- The wounds: lit gashes where the cuts landed, white-hot as they open, then gone.
		for (ClientHotel.Wound wd : ClientHotel.wounds()) {
			float age = world.getTime() + delta - wd.start();
			float life = MathHelper.clamp(1.0F - age / ClientHotel.WOUND_TICKS, 0.0F, 1.0F);
			float hot = MathHelper.clamp(1.0F - age / 10.0F, 0.0F, 1.0F);
			Direction face = Direction.byId(wd.face());
			Vec3d n = new Vec3d(face.getOffsetX(), face.getOffsetY(), face.getOffsetZ());
			Vec3d c = new Vec3d(wd.x() - devilPos.x, wd.y() - devilPos.y, wd.z() - devilPos.z).add(n.multiply(0.02));
			Vec3d t1 = Math.abs(n.y) > 0.5 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
			Vec3d t2 = n.crossProduct(t1);
			double ang = ((int) (wd.x() * 13.0 + wd.y() * 7.0 + wd.z() * 5.0) & 7) * 0.4 - 1.4;
			Vec3d along = t1.multiply(Math.cos(ang)).add(t2.multiply(Math.sin(ang)));
			double len = (wd.soft() ? 0.9 : 0.6) * (0.8 + 0.2 * hot);
			double wid = (wd.soft() ? 0.12 : 0.08) * (0.7 + 0.3 * life);
			g.slash(c, along, n, len, wid, 1.0F, 0.55F + 0.4F * hot, 0.25F + 0.6F * hot, Math.min(1.0F, 0.6F + 0.5F * life) * life);
		}
		// ---- The mouth above (in its floor of fused arms), seen falling in and up through the throat.
		Vec3d mouth = at(rot, Shaft.MID_X, Shaft.BASE, Shaft.MID_Z);
		Vec3d cam = net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getPos().subtract(devilPos);
		// (Not while the eye is going through the mouth itself: its flesh would fill the view.)
		if (!(cam.y < mouth.y + 1.2 && cam.y > mouth.y - 3.0 && Math.hypot(cam.x - mouth.x, cam.z - mouth.z) < 1.4)) {
			ShaftMassRenderer.render(devil, delta, new Matrix4f(base).translate((float) mouth.x, (float) mouth.y, (float) mouth.z).rotateY((float) Math.toRadians(-devil.facing())), k);
		}
	}

	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.55F, 0.02F, 0.03F), 1.0F);

	/** A packed light coordinate of full light (the arena is lit from inside its own flesh). */
	static final class LightmapPacked {
		static final int FULL = LightmapTextureManager.pack(15, 15);
	}
}
