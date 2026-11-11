package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.EternityDevilEntity;
import io.github.kortev.chainsawman.hotel.HotelBuilder;
import io.github.kortev.chainsawman.hotel.Shaft;
import io.github.kortev.chainsawman.network.HotelPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The inside of the Eternity Devil, the arena at the foot of the shaft ({@link Shaft}): drawn round the devil entity (sunk in the arena's
 * floor) in the arena's own frame. The blocks are its flesh; this lights it from inside and makes it move:
 * <ul>
 * <li>veins glowing in the walls, floor and ceiling, beating like a heart (more of them lit, and beating faster, at each stage of the bar);</li>
 * <li>the mouth overhead: its lips lit round the throat, teeth, the light falling from it; above, the mouth itself in its floor of fused arms;</li>
 * <li>heads half sunk in the walls watching you (more at each stage), and growths heaving on the walls (faster at each stage);</li>
 * <li>the squeeze: the half's walls bulge, their seams lit, the line on the floor where it will reach; then they clench shut;</li>
 * <li>every cut's lit wound, a few seconds, and the flesh flinching at it;</li>
 * <li>full pain: the whole room convulses, every vein flares.</li>
 * </ul>
 */
final class ArenaRenderer {
	private ArenaRenderer() {
	}

	/** One lit vein's piece (local): its ends, the surface's normal, its phase in the beat, the stage it is lit from, and its half (0 low x, 1 high x, -1 the middle). */
	private record Seg(Vec3d a, Vec3d b, Vec3d n, float phase, int stage, int half, float width) {
	}

	private static List<Seg> veins;

	/** The heads in the walls (local: the face's place, the wall's inward normal), as many as the stage allows: 3, 6, then all. */
	private static final double[][] HEADS = {{Shaft.AX0 + 0.1, Shaft.FLOOR + 4.3, 3.5, 1, 0}, {Shaft.AX1 + 0.9, Shaft.FLOOR + 4.1, 9.6, -1, 0},
			{16.5, Shaft.FLOOR + 4.4, Shaft.AZ0 + 0.1, 0, 1}, {22.5, Shaft.FLOOR + 4.2, Shaft.AZ1 + 0.9, 0, -1}, {Shaft.AX0 + 0.1, Shaft.FLOOR + 4.0, 9.6, 1, 0},
			{Shaft.AX1 + 0.9, Shaft.FLOOR + 4.3, 3.4, -1, 0}, {13.6, Shaft.FLOOR + 4.2, Shaft.AZ1 + 0.9, 0, -1}, {25.4, Shaft.FLOOR + 4.1, Shaft.AZ0 + 0.1, 0, 1},
			{19.5, Shaft.FLOOR + 4.5, Shaft.AZ0 + 0.1, 0, 1}, {19.5, Shaft.FLOOR + 4.5, Shaft.AZ1 + 0.9, 0, -1}};
	/** The growths heaving on the walls, high up (over anyone's head): local foot, inward normal x and z. */
	private static final double[][] HEAVERS = {{Shaft.AX0, Shaft.FLOOR + 3.6, 6.5, 1, 0}, {Shaft.AX1 + 1, Shaft.FLOOR + 3.6, 6.5, -1, 0},
			{14.0, Shaft.FLOOR + 3.8, Shaft.AZ0, 0, 1}, {25.0, Shaft.FLOOR + 3.8, Shaft.AZ1 + 1, 0, -1}, {24.0, Shaft.FLOOR + 3.7, Shaft.AZ0, 0, 1},
			{15.0, Shaft.FLOOR + 3.7, Shaft.AZ1 + 1, 0, -1}};

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

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** The beat: a quick double pulse each {@code period} ticks, 0 to 1. */
	private static float beat(float t, float period, float phase) {
		float p = ((t / period + phase) % 1.0F + 1.0F) % 1.0F;
		float a = (float) Math.exp(-Math.pow((p - 0.08F) / 0.05F, 2.0));
		float b = 0.6F * (float) Math.exp(-Math.pow((p - 0.24F) / 0.06F, 2.0));
		return Math.max(a, b);
	}

	private static List<Seg> veins() {
		if (veins != null) {
			return veins;
		}
		List<Seg> out = new ArrayList<>();
		Random rnd = new Random(1717);
		double y0 = Shaft.FLOOR;
		double y1 = Shaft.FLOOR + Shaft.HIGH;
		double x0 = Shaft.AX0;
		double x1 = Shaft.AX1 + 1;
		double z0 = Shaft.AZ0;
		double z1 = Shaft.AZ1 + 1;
		// The four walls: veins climb from the floor and branch.
		for (int w = 0; w < 4; w++) {
			int n = w < 2 ? 9 : 13;
			for (int k = 0; k < n; k++) {
				double u = (k + 0.2 + rnd.nextDouble() * 0.6) / n;
				vein(out, rnd, w, u, 0.0, Math.PI / 2.0 + (rnd.nextDouble() - 0.5) * 0.9, 14 + rnd.nextInt(6), x0, x1, y0, y1, z0, z1, 0.13);
			}
		}
		// The ceiling: out from the mouth like spokes; the floor: a few, thin.
		for (int k = 0; k < 14; k++) {
			double a = k * Math.PI * 2.0 / 14 + rnd.nextDouble() * 0.3;
			vein(out, rnd, 4, a, 0.0, a, 16 + rnd.nextInt(6), x0, x1, y0, y1, z0, z1, 0.15);
		}
		for (int k = 0; k < 10; k++) {
			double a = rnd.nextDouble() * Math.PI * 2.0;
			vein(out, rnd, 5, a, 0.0, a, 10 + rnd.nextInt(5), x0, x1, y0, y1, z0, z1, 0.1);
		}
		veins = out;
		return out;
	}

	/** One vein on surface {@code w} (0 low-x wall, 1 high-x, 2 low-z, 3 high-z, 4 ceiling, 5 floor) from (u, v), heading {@code a}. */
	private static void vein(List<Seg> out, Random rnd, int w, double u, double v, double a, int steps, double x0, double x1, double y0, double y1, double z0, double z1,
			double width) {
		double su;
		double sv;
		if (w <= 3) {
			su = u * (w < 2 ? z1 - z0 : x1 - x0);
			sv = 0.05;
		} else {
			// (u is the angle; it starts round the mouth on the ceiling, anywhere on the floor)
			su = w == 4 ? 2.0 * Math.cos(u) : (rnd.nextDouble() - 0.5) * (x1 - x0 - 2.0);
			sv = w == 4 ? 2.0 * Math.sin(u) : (rnd.nextDouble() - 0.5) * (z1 - z0 - 2.0);
		}
		int stage = rnd.nextFloat() < 0.45F ? 0 : rnd.nextFloat() < 0.55F ? 1 : 2;
		float phase = rnd.nextFloat() * 0.25F;
		double heading = a;
		for (int i = 0; i < steps; i++) {
			double nu = su + Math.cos(heading) * 0.55;
			double nv = sv + Math.sin(heading) * 0.55;
			Vec3d p = onSurface(w, su, sv, x0, x1, y0, y1, z0, z1);
			Vec3d q = onSurface(w, nu, nv, x0, x1, y0, y1, z0, z1);
			if (p == null || q == null) {
				break;
			}
			Vec3d n = w == 0 ? new Vec3d(1, 0, 0) : w == 1 ? new Vec3d(-1, 0, 0) : w == 2 ? new Vec3d(0, 0, 1) : w == 3 ? new Vec3d(0, 0, -1) : w == 4 ? new Vec3d(0, -1, 0) : new Vec3d(0, 1, 0);
			double mx = (p.x + q.x) / 2.0;
			int half = mx < Shaft.MID_X - 2.0 ? 0 : mx > Shaft.MID_X + 2.0 ? 1 : -1;
			out.add(new Seg(p.add(n.multiply(0.025)), q.add(n.multiply(0.025)), n, phase + i * 0.012F, stage, half, (float) (width * (1.0 - 0.5 * i / steps))));
			su = nu;
			sv = nv;
			heading += (rnd.nextDouble() - 0.5) * 0.8;
			if (w <= 3) {
				// (Up the walls, mostly.)
				heading = heading * 0.85 + Math.PI / 2.0 * 0.15;
			}
			if (rnd.nextFloat() < 0.12F && steps - i > 5) {
				vein(out, rnd, w, w <= 3 ? su / (w < 2 ? z1 - z0 : x1 - x0) : 0.0, sv, heading + (rnd.nextBoolean() ? 0.9 : -0.9), (steps - i) / 2, x0, x1, y0, y1, z0, z1,
						width * 0.7);
				if (w > 3) {
					break;
				}
			}
		}
	}

	private static Vec3d onSurface(int w, double u, double v, double x0, double x1, double y0, double y1, double z0, double z1) {
		double mx = (x0 + x1) / 2.0;
		double mz = (z0 + z1) / 2.0;
		Vec3d p = switch (w) {
			case 0 -> new Vec3d(x0, y0 + v, z0 + u);
			case 1 -> new Vec3d(x1, y0 + v, z1 - u);
			case 2 -> new Vec3d(x1 - u, y0 + v, z0);
			case 3 -> new Vec3d(x0 + u, y0 + v, z1);
			case 4 -> new Vec3d(mx + u, y1, mz + v);
			default -> new Vec3d(mx + u, y0, mz + v);
		};
		if (p.x < x0 - 0.01 || p.x > x1 + 0.01 || p.z < z0 - 0.01 || p.z > z1 + 0.01 || p.y < y0 - 0.01 || p.y > y1 - 0.15 && w <= 3) {
			return null;
		}
		return p;
	}

	static void render(EternityDevilEntity devil, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(devil.getWorld() instanceof ClientWorld world)) {
			return;
		}
		int rot = rotOf(devil.facing());
		float now = world.getTime() + delta;
		int stage = Shaft.stage(devil);
		float share = Shaft.share(devil);
		boolean dead = devil.isDead();
		float dying = dead ? ease((devil.deathTime + delta) / EternityDevilEntity.DEATH_TICKS) : 0.0F;
		float sinceBeg = ClientHotel.sinceBeg(delta);
		boolean begging = sinceBeg >= 0.0F && devil.pain() > 0.999F;
		float convulse = begging ? MathHelper.clamp(1.0F - (sinceBeg - 40.0F) / 30.0F, 0.0F, 1.0F) : 0.0F;
		// The flinch at the newest cut, and the squeeze if one is on.
		float flinch = 0.0F;
		for (ClientHotel.Wound w : ClientHotel.wounds()) {
			flinch = Math.max(flinch, 1.0F - (world.getTime() + delta - w.start()) / 6.0F);
		}
		flinch = MathHelper.clamp(flinch, 0.0F, 1.0F);
		int squeezeHalf = -1;
		float squeezeT = -1.0F;
		for (ClientHotel.Event e : ClientHotel.events(devil.getId())) {
			if (e.kind() == HotelPayloads.Devil.SQUEEZE) {
				float t = now - e.start();
				if (t >= 0.0F && t < Shaft.SQUEEZE_TELL + Shaft.SQUEEZE_SHUT + Shaft.SQUEEZE_SOFT) {
					squeezeHalf = ClientHotel.squeezeHalf(e);
					squeezeT = t;
				}
			}
		}
		Vec3d devilPos = devil.getLerpedPos(delta);
		PlayerEntity near = world.getClosestPlayer(devil.getX(), devil.getY() + 4.0, devil.getZ(), 24.0, false);

		Glow g = new Glow(provider, matrices);
		// ---- The veins: beating, more of them and faster as it hurts; the squeezing half's flare; full pain, all of them flare.
		float period = stage == 0 ? 30.0F : stage == 1 ? 21.0F : 14.0F;
		for (Seg s : veins()) {
			boolean squeezeVein = squeezeHalf >= 0 && s.half() == squeezeHalf && s.n().y == 0.0;
			if (s.stage() > stage && !squeezeVein && !begging && dead == false) {
				// (Not lit yet: a dim trace, so the walls are never bare.)
				Vec3d a = at(rot, s.a().x, s.a().y, s.a().z);
				Vec3d b = at(rot, s.b().x, s.b().y, s.b().z);
				g.line(a, b, dir(rot, s.n().x, s.n().y, s.n().z), s.width() * 0.8, 0.6F, 0.08F, 0.04F, 0.18F);
				continue;
			}
			float pulse = beat(now, period, s.phase());
			float r = 1.0F;
			float gg = 0.30F + 0.15F * pulse;
			float b = 0.08F;
			float alpha = 0.42F + 0.5F * pulse + 0.4F * flinch;
			double width = s.width() * (1.0 + 0.4 * pulse);
			if (squeezeVein && squeezeT < Shaft.SQUEEZE_TELL + Shaft.SQUEEZE_SHUT) {
				// The tell: the half's walls light orange, beating faster and faster to the clench.
				float k = squeezeT / Shaft.SQUEEZE_TELL;
				float fast = beat(now, 9.0F - 5.0F * k, s.phase());
				gg = 0.55F + 0.3F * fast;
				b = 0.12F;
				alpha = 0.75F + 0.25F * fast;
				width = s.width() * (1.6 + 0.8 * fast);
			} else if (squeezeVein) {
				// Soft after the clench: pink-red and slow.
				gg = 0.25F;
				b = 0.2F;
				alpha = 0.8F;
				width = s.width() * 1.6;
			}
			if (begging) {
				gg = 0.5F + 0.3F * pulse;
				b = 0.2F;
				alpha = 0.9F + 0.6F * convulse;
				width = s.width() * (1.8 + convulse);
			}
			if (dead) {
				alpha *= 1.0F - 0.75F * dying;
			}
			Vec3d a = at(rot, s.a().x, s.a().y, s.a().z);
			Vec3d bb = at(rot, s.b().x, s.b().y, s.b().z);
			Vec3d n = dir(rot, s.n().x, s.n().y, s.n().z);
			g.line(a, bb, n, width * 2.6, r, gg * 0.6F, b, alpha * 0.22F);
			g.line(a, bb, n, width, r, gg, b, Math.min(1.0F, alpha));
		}
		// ---- The mouth overhead: the throat's lips lit, teeth round it, light falling from it.
		double cy = Shaft.FLOOR + Shaft.HIGH - 0.02;
		Vec3d down = dir(rot, 0.0, -1.0, 0.0);
		double hx0 = Shaft.IX0;
		double hx1 = Shaft.IX1 + 1;
		double hz0 = Shaft.IZ0;
		double hz1 = Shaft.IZ1 + 1;
		float lip = 0.7F + 0.3F * beat(now, period, 0.0F) + 0.5F * convulse;
		for (int side = 0; side < 4; side++) {
			double[] e = switch (side) {
				case 0 -> new double[] {hx0, hz0, hx1, hz0, 0, -1};
				case 1 -> new double[] {hx1, hz0, hx1, hz1, 1, 0};
				case 2 -> new double[] {hx1, hz1, hx0, hz1, 0, 1};
				default -> new double[] {hx0, hz1, hx0, hz0, -1, 0};
			};
			Vec3d p0 = at(rot, e[0], cy, e[1]);
			Vec3d p1 = at(rot, e[2], cy, e[3]);
			Vec3d out = dir(rot, e[4], 0.0, e[5]);
			g.fade(p0, p1, p1.add(out.multiply(1.6)), p0.add(out.multiply(1.6)), 1.0F, 0.45F, 0.15F, Math.min(1.0F, 0.9F * lip), 0.0F);
			// Teeth hanging from the lip, pale.
			for (int k = 0; k < 4; k++) {
				double f = (k + 0.5) / 4.0;
				Vec3d tp = p0.add(p1.subtract(p0).multiply(f)).add(out.multiply(0.15));
				Vec3d along = p1.subtract(p0).normalize().multiply(0.16);
				double len = 0.45 + 0.2 * ((k * 7 + side * 3) % 3) + 0.25 * convulse * MathHelper.sin(now * 1.3F + k);
				g.quad(tp.subtract(along), tp.add(along), tp.add(along.multiply(0.15)).add(down.multiply(len)), tp.subtract(along.multiply(0.15)).add(down.multiply(len)), 1.0F,
						0.92F, 0.8F, 0.75F);
			}
		}
		// The light falling from the mouth (a soft column down to the floor).
		float fall = 0.11F + 0.05F * beat(now, period, 0.1F) + 0.25F * convulse;
		for (int k = 0; k < 2; k++) {
			Vec3d a0 = at(rot, k == 0 ? hx0 + 0.3 : Shaft.MID_X, cy, k == 0 ? Shaft.MID_Z : hz0 + 0.3);
			Vec3d a1 = at(rot, k == 0 ? hx1 - 0.3 : Shaft.MID_X, cy, k == 0 ? Shaft.MID_Z : hz1 - 0.3);
			Vec3d b0 = at(rot, k == 0 ? hx0 - 1.0 : Shaft.MID_X, Shaft.FLOOR + 0.02, k == 0 ? Shaft.MID_Z : hz0 - 1.0);
			Vec3d b1 = at(rot, k == 0 ? hx1 + 1.0 : Shaft.MID_X, Shaft.FLOOR + 0.02, k == 0 ? Shaft.MID_Z : hz1 + 1.0);
			g.fade(a0, a1, b1, b0, 1.0F, 0.75F, 0.5F, fall, fall * 0.25F);
		}
		// Seen from above, the mouth's throat is full of the arena's light (never a black hole).
		Vec3d h0 = at(rot, hx0 + 0.05, Shaft.BASE + 0.05, hz0 + 0.05);
		Vec3d h1 = at(rot, hx1 - 0.05, Shaft.BASE + 0.05, hz0 + 0.05);
		Vec3d h2 = at(rot, hx1 - 0.05, Shaft.BASE + 0.05, hz1 - 0.05);
		Vec3d h3 = at(rot, hx0 + 0.05, Shaft.BASE + 0.05, hz1 - 0.05);
		g.quad(h0, h1, h2, h3, 1.0F, 0.5F, 0.2F, 0.5F * lip);
		// A pool of the mouth's light on the floor under it.
		Vec3d pool = at(rot, Shaft.MID_X, Shaft.FLOOR + 0.03, Shaft.MID_Z);
		g.arc(pool, 1.2, 2.4, 0.0, Math.PI * 2.0, dir(rot, 1, 0, 0), dir(rot, 0, 0, 1), 1.0F, 0.6F, 0.35F, 0.10F + 0.2F * convulse);
		// ---- The squeeze's tell on the floor: where it will reach, a line that brightens to the clench.
		if (squeezeHalf >= 0 && squeezeT < Shaft.SQUEEZE_TELL + Shaft.SQUEEZE_SHUT) {
			double lx = squeezeHalf == 0 ? Shaft.AX0 + Shaft.SQUEEZE_REACH : Shaft.AX1 + 1 - Shaft.SQUEEZE_REACH;
			float k = MathHelper.clamp(squeezeT / Shaft.SQUEEZE_TELL, 0.0F, 1.0F);
			float a = 0.5F + 0.5F * beat(now, 8.0F - 4.0F * k, 0.0F);
			Vec3d n = dir(rot, 0, 1, 0);
			g.line(at(rot, lx, Shaft.FLOOR + 0.04, Shaft.AZ0), at(rot, lx, Shaft.FLOOR + 0.04, Shaft.AZ1 + 1), n, 0.22, 1.0F, 0.6F, 0.12F, a);
			// The caught side's floor tinted.
			double ex = squeezeHalf == 0 ? Shaft.AX0 : Shaft.AX1 + 1;
			g.fade(at(rot, ex, Shaft.FLOOR + 0.03, Shaft.AZ0), at(rot, ex, Shaft.FLOOR + 0.03, Shaft.AZ1 + 1), at(rot, lx, Shaft.FLOOR + 0.03, Shaft.AZ1 + 1),
					at(rot, lx, Shaft.FLOOR + 0.03, Shaft.AZ0), 1.0F, 0.4F, 0.1F, 0.25F * k, 0.08F * k);
		}
		// ---- The wounds: lit gashes where the cuts landed, white-hot as they open, then orange, red, gone.
		for (ClientHotel.Wound w : ClientHotel.wounds()) {
			float age = world.getTime() + delta - w.start();
			float life = MathHelper.clamp(1.0F - age / ClientHotel.WOUND_TICKS, 0.0F, 1.0F);
			float hot = MathHelper.clamp(1.0F - age / 10.0F, 0.0F, 1.0F);
			Direction face = Direction.byId(w.face());
			Vec3d n = new Vec3d(face.getOffsetX(), face.getOffsetY(), face.getOffsetZ());
			Vec3d c = new Vec3d(w.x() - devilPos.x, w.y() - devilPos.y, w.z() - devilPos.z).add(n.multiply(0.02));
			Vec3d t1 = Math.abs(n.y) > 0.5 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
			Vec3d t2 = n.crossProduct(t1);
			double ang = ((int) (w.x() * 13.0 + w.y() * 7.0 + w.z() * 5.0) & 7) * 0.4 - 1.4;
			Vec3d along = t1.multiply(Math.cos(ang)).add(t2.multiply(Math.sin(ang)));
			double len = (w.soft() ? 1.5 : 1.05) * (0.8 + 0.2 * hot);
			double wid = (w.soft() ? 0.34 : 0.22) * (0.7 + 0.3 * life);
			g.slash(c, along, n, len * 1.7, wid * 3.2, 1.0F, 0.35F, 0.1F, 0.35F * life);
			g.slash(c, along, n, len, wid, 1.0F, 0.55F + 0.4F * hot, 0.25F + 0.6F * hot, Math.min(1.0F, 0.6F + 0.5F * life));
		}

		// ---- The meshes: the mouth over the arena, the heads watching, the walls heaving, the squeeze's bulges.
		float shake = convulse * 0.35F + flinch * 0.06F;
		java.util.function.Function<Vec3d, Vec3d> jitter = p -> shake <= 0.0F ? p
				: p.add(MathHelper.sin(now * 2.7F + (float) p.x) * shake, MathHelper.sin(now * 3.1F + (float) p.z) * shake * 0.5, MathHelper.sin(now * 2.3F + (float) p.y) * shake);
		Matrix4f base = matrices.peek().getPositionMatrix();
		// The mouth (in its floor of fused arms, above the arena's ceiling: seen falling in, and up through the throat).
		Vec3d mouth = at(rot, Shaft.MID_X, Shaft.BASE, Shaft.MID_Z);
		int mouthLight = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(devilPos.add(mouth).add(0.0, 1.5, 0.0)));
		Vec3d cam = net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getPos().subtract(devilPos);
		// (Not while the eye is going through the mouth itself: its flesh would fill the view.)
		if (!(cam.y < mouth.y + 1.5 && cam.y > mouth.y - 4.0 && Math.hypot(cam.x - mouth.x, cam.z - mouth.z) < 2.6)) {
			ShaftMassRenderer.render(devil, delta, new Matrix4f(base).translate((float) mouth.x, (float) mouth.y, (float) mouth.z).rotateY((float) Math.toRadians(-devil.facing())),
					mouthLight);
		}
		// The throat lit from inside: its four walls glowing orange, brightest at the arena's end, so the way in is never a black hole.
		for (int side = 0; side < 4; side++) {
			double[] e = switch (side) {
				case 0 -> new double[] {hx0, hz0, hx1, hz0};
				case 1 -> new double[] {hx1, hz0, hx1, hz1};
				case 2 -> new double[] {hx1, hz1, hx0, hz1};
				default -> new double[] {hx0, hz1, hx0, hz0};
			};
			double inset = 0.03;
			double cx = (e[0] + e[2]) / 2.0 < Shaft.MID_X - 0.1 ? inset : (e[0] + e[2]) / 2.0 > Shaft.MID_X + 0.1 ? -inset : 0.0;
			double cz = (e[1] + e[3]) / 2.0 < Shaft.MID_Z - 0.1 ? inset : (e[1] + e[3]) / 2.0 > Shaft.MID_Z + 0.1 ? -inset : 0.0;
			Vec3d b0 = at(rot, e[0] + cx, Shaft.FLOOR + Shaft.HIGH, e[1] + cz);
			Vec3d b1 = at(rot, e[2] + cx, Shaft.FLOOR + Shaft.HIGH, e[3] + cz);
			Vec3d t0 = at(rot, e[0] + cx, Shaft.BASE, e[1] + cz);
			Vec3d t1 = at(rot, e[2] + cx, Shaft.BASE, e[3] + cz);
			g.fade(b0, b1, t1, t0, 1.0F, 0.45F, 0.12F, Math.min(1.0F, 0.75F * lip), 0.3F * lip);
		}
		// The heads in the walls: 3, 6, then all 10 watching (turned toward the nearest player, as far as a head sunk in a wall can turn).
		int heads = begging ? HEADS.length : stage == 0 ? 3 : stage == 1 ? 6 : HEADS.length;
		for (int i = 0; i < heads; i++) {
			double[] hd = HEADS[i];
			Vec3d spot = at(rot, hd[0], hd[1], hd[2]);
			Vec3d in = dir(rot, hd[3], 0.0, hd[4]);
			double theta = Math.atan2(-in.z, in.x);
			if (near != null) {
				Vec3d to = near.getEyePos().subtract(devilPos).subtract(spot);
				double want = Math.atan2(-to.z, to.x);
				double diff = MathHelper.wrapDegrees(Math.toDegrees(want - theta));
				theta += Math.toRadians(MathHelper.clamp(diff, -60.0, 60.0));
			}
			float emerge = begging ? 1.0F : stage == 0 && i >= 3 || stage == 1 && i >= 6 ? 0.0F : 1.0F;
			Vec3d p = jitter.apply(spot.add(in.multiply(-0.35 + 0.1 * MathHelper.sin(now * 0.05F + i))));
			Matrix4f m = new Matrix4f(base).translate((float) p.x, (float) (p.y - 1.45 * 0.9), (float) p.z).rotateY((float) theta).scale(0.9F * emerge);
			FleshMesh.Look look = new FleshMesh.Look().light(LightmapPacked.FULL, 0.7F, 1.25F);
			look.rim = 0.55F;
			look.glowAt = new Vector3f(0.45F, 1.5F, 0.0F);
			look.glowRange = 0.55F;
			look.glowR = 0.9F + 0.6F * convulse;
			look.glowG = 0.35F;
			look.glowB = 0.05F;
			float jaw = ShaftHeadRenderer.JAW_OPEN * (0.5F + 0.5F * MathHelper.sin(now * 0.09F + i * 1.7F)) * (begging ? 0.2F : 1.0F);
			FleshMesh.get("head").draw(m, now + i * 13.0F, look, ShaftHeadRenderer.jawHook(jaw));
		}
		// The growths heaving on the walls, faster as it hurts.
		float heave = stage == 0 ? 0.05F : stage == 1 ? 0.08F : 0.12F;
		int heavers = stage == 0 ? 4 : HEAVERS.length;
		for (int i = 0; i < heavers; i++) {
			double[] hv = HEAVERS[i];
			if (squeezeHalf >= 0 && ((hv[0] < Shaft.MID_X - 2.0 && squeezeHalf == 0) || (hv[0] > Shaft.MID_X + 2.0 && squeezeHalf == 1))) {
				continue;
			}
			Vec3d foot = jitter.apply(at(rot, hv[0], hv[1], hv[2]));
			Vec3d in = dir(rot, hv[3], 0.0, hv[4]);
			if (Math.hypot(cam.x - foot.x - in.x * 0.5, cam.z - foot.z - in.z * 0.5) < 1.6) {
				continue;
			}
			float breathe = 1.0F + 0.12F * MathHelper.sin(now * heave + i * 2.1F) + 0.15F * flinch + 0.25F * convulse * MathHelper.sin(now * 1.9F + i);
			Matrix4f m = new Matrix4f(base).translate((float) foot.x, (float) foot.y, (float) foot.z).rotateY((float) Math.atan2(-in.z, in.x))
					.scale(0.55F * breathe, 0.9F, 1.1F);
			FleshMesh.Look look = new FleshMesh.Look().light(LightmapPacked.FULL, 0.7F, 1.0F);
			look.rim = 0.5F;
			look.r = 1.05F;
			look.g = 0.62F;
			look.b = 0.58F;
			FleshMesh.get("growing").draw(m, now * (1.0F + stage * 0.6F), look, null);
		}
		// The squeeze: the half's walls bulge out (the tell), clench shut across the half, and draw back.
		if (squeezeHalf >= 0) {
			float t = squeezeT;
			float bulge = ease(t / 10.0F);
			float shut = t < Shaft.SQUEEZE_TELL ? 0.0F : ease((t - Shaft.SQUEEZE_TELL) / 3.0F) * (1.0F - ease((t - Shaft.SQUEEZE_TELL - Shaft.SQUEEZE_SHUT) / 10.0F));
			float back = t > Shaft.SQUEEZE_TELL + Shaft.SQUEEZE_SHUT + 4 ? 1.0F - 0.5F * ease((t - Shaft.SQUEEZE_TELL - Shaft.SQUEEZE_SHUT - 4) / 12.0F) : 1.0F;
			double endX = squeezeHalf == 0 ? Shaft.AX0 : Shaft.AX1 + 1;
			double s = squeezeHalf == 0 ? 1.0 : -1.0;
			double reach = Shaft.SQUEEZE_REACH;
			double[][] spots = {{endX, 3.0, s, 0}, {endX, 6.5, s, 0}, {endX, 10.0, s, 0}, {endX + s * 1.6, Shaft.AZ0, 0, 1}, {endX + s * 3.8, Shaft.AZ0, 0, 1},
					{endX + s * 1.6, Shaft.AZ1 + 1, 0, -1}, {endX + s * 3.8, Shaft.AZ1 + 1, 0, -1}};
			for (int i = 0; i < spots.length; i++) {
				double[] sp = spots[i];
				Vec3d in = dir(rot, sp[2], 0.0, sp[3]);
				// (They close the caught half and no further: the line on the floor is where they stop.)
				double travel = sp[3] == 0 ? reach - 3.2 : 3.4;
				double pulse = 0.08 * Math.sin(now * (0.4 + 0.6 * t / Shaft.SQUEEZE_TELL) + i);
				double out = (0.25 + pulse) * bulge + travel * shut;
				Vec3d foot = at(rot, sp[0], Shaft.FLOOR - 0.1, sp[1]).add(in.multiply(out));
				float grow = (float) ((0.6 + 0.5 * bulge + 0.9 * shut) * back);
				if (Math.hypot(cam.x - foot.x - in.x * grow, cam.z - foot.z - in.z * grow) < 1.6 * grow + 1.0) {
					continue;
				}
				// (Narrow across, so each stays on its own side of the line: the end wall's three fill the half's width, the sides' two its length.)
				float across = sp[3] == 0 ? 0.75F : 0.5F;
				Matrix4f m = new Matrix4f(base).translate((float) foot.x, (float) foot.y, (float) foot.z).rotateY((float) Math.atan2(-in.z, in.x))
						.scale(1.1F * grow, 1.4F * (0.8F + 0.3F * grow), across * (1.0F + 0.35F * grow));
				FleshMesh.Look look = new FleshMesh.Look().light(LightmapPacked.FULL, 0.7F, 1.05F);
				look.rim = 0.9F;
				look.r = 1.15F;
				look.g = 0.55F;
				look.b = 0.45F;
				look.hurt = t < Shaft.SQUEEZE_TELL ? 0.25F + 0.25F * (float) Math.sin(now * 0.8F) : 0.15F;
				look.glowAt = new Vector3f(0.6F, 1.3F, 0.0F);
				look.glowRange = 1.6F;
				look.glowR = 1.0F;
				look.glowG = 0.5F;
				look.glowB = 0.1F;
				FleshMesh.get("growing").draw(m, now * 2.0F, look, null);
			}
		}
	}

	/** A packed light coordinate of full light (the arena is lit from inside its own flesh). */
	static final class LightmapPacked {
		static final int FULL = net.minecraft.client.render.LightmapTextureManager.pack(15, 15);
	}
}
