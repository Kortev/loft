package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.contract.ForeverHubEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;

/**
 * Where the Eternity Devil's flesh comes up round a locked target: five to seven low lumps (each a main mass with two lesser ones
 * against it) two to three and a half blocks out, on the real ground there (the client's own look at the world), eyes on about half
 * of them, a mouth on each, cracks running out from each. Everything is worked out from the hub's seed, so every client draws the same
 * devil without being told, and positions are relative to the hub (where the target froze).
 */
final class ForeverLayout {
	static final class Part {
		double x;
		double y;
		double z;
		double rx;
		double ry;
		double rz;
		int seed;
	}

	static final class Lump {
		final List<Part> parts = new ArrayList<>();
		double x;
		double z;
		/** The ground under it, relative to the hub. */
		double ground;
		double rx;
		double rz;
		double ry;
		/** How many ticks after the first this one starts to rise. */
		double delay;
	}

	static final class Eye {
		int lump;
		int index;
		Vec3d pos;
		Vec3d normal;
		double radius;
	}

	static final class Mouth {
		int lump;
		Vec3d pos;
		Vec3d normal;
		double width;
		int seed;
		int teeth;
	}

	static final class Crack {
		int lump;
		final List<Vec3d> pts = new ArrayList<>();
	}

	final int seed;
	final List<Lump> lumps = new ArrayList<>();
	final List<Eye> eyes = new ArrayList<>();
	final List<Mouth> mouths = new ArrayList<>();
	final List<Crack> cracks = new ArrayList<>();
	/** How high the ground under the hub is, relative to the hub (negative: the target hangs in the air). */
	double centreGround;

	ForeverLayout(ClientWorld world, ForeverHubEntity hub) {
		this.seed = hub.seed();
		Random random = Random.create(seed);
		double hy = hub.getY();
		centreGround = groundBelow(world, hub.getX(), hy, hub.getZ()) - hy;
		int count = 5 + random.nextInt(3);
		double base = random.nextDouble() * Math.PI * 2.0;
		for (int i = 0; i < count; i++) {
			Lump l = new Lump();
			double ang = base + i * Math.PI * 2.0 / count + (random.nextDouble() - 0.5) * 0.5;
			double dist = 2.0 + random.nextDouble() * 1.5;
			l.x = Math.cos(ang) * dist;
			l.z = Math.sin(ang) * dist;
			l.rx = 0.95 + random.nextDouble() * 0.5;
			l.rz = 0.9 + random.nextDouble() * 0.45;
			l.ry = 0.55 + random.nextDouble() * 0.35;
			l.delay = i * 0.6;
			l.ground = groundNear(world, hub.getX() + l.x, hy + centreGround, hub.getZ() + l.z) - hy;
			Part main = new Part();
			main.x = l.x;
			main.z = l.z;
			main.y = l.ground + 0.02;
			main.rx = l.rx;
			main.ry = l.ry;
			main.rz = l.rz;
			main.seed = random.nextInt();
			l.parts.add(main);
			// A cluster: lobes of different sizes round and over the main one (not one smooth shape).
			int lobes = 4;
			for (int k = 0; k < lobes; k++) {
				Part s = new Part();
				double a2 = k * Math.PI * 2.0 / lobes + random.nextDouble() * 1.2;
				double size = 0.34 + random.nextDouble() * 0.36;
				s.rx = l.rx * size;
				s.rz = l.rz * size;
				s.ry = l.ry * (0.55 + random.nextDouble() * 0.5);
				s.x = l.x + Math.cos(a2) * l.rx * (0.62 + random.nextDouble() * 0.3);
				s.z = l.z + Math.sin(a2) * l.rz * (0.62 + random.nextDouble() * 0.3);
				s.y = groundNear(world, hub.getX() + s.x, hy + centreGround, hub.getZ() + s.z) - hy - 0.04 + (k >= 2 ? l.ry * 0.35 : 0.0);
				s.seed = random.nextInt();
				l.parts.add(s);
			}
			lumps.add(l);
		}
		int eyeIndex = 0;
		for (int i = 0; i < count; i++) {
			Lump l = lumps.get(i);
			Part m = l.parts.get(0);
			// A mouth on every lump, low, looking out.
			Mouth mouth = new Mouth();
			mouth.lump = i;
			double outward = Math.atan2(l.z, l.x) + (random.nextDouble() - 0.5) * 1.3;
			double e = 0.34 + random.nextDouble() * 0.16;
			Vec3d h = new Vec3d(Math.cos(outward), 0.0, Math.sin(outward));
			mouth.pos = surface(m, h, e);
			mouth.normal = normal(m, h, e);
			mouth.width = 1.0 + random.nextDouble() * 0.4;
			mouth.seed = random.nextInt();
			mouth.teeth = 12 + random.nextInt(4);
			mouths.add(mouth);
			{
				int n = 1 + random.nextInt(2);
				for (int k = 0; k < n; k++) {
					Eye eye = new Eye();
					eye.lump = i;
					eye.index = eyeIndex++;
					double inward = Math.atan2(-l.z, -l.x) + (random.nextDouble() - 0.5) * 0.9 + (k == 0 ? -0.35 : 0.45);
					double el = 0.3 + random.nextDouble() * 0.45;
					Vec3d hh = new Vec3d(Math.cos(inward), 0.0, Math.sin(inward));
					eye.radius = 0.26 + random.nextDouble() * 0.06;
					Vec3d surf = surface(m, hh, el);
					eye.normal = normal(m, hh, el);
					eye.pos = surf.add(eye.normal.multiply(eye.radius * 0.4));
					eyes.add(eye);
				}
			}
			// Cracks in the ground, out from the lump.
			int strands = 4 + random.nextInt(2);
			for (int k = 0; k < strands; k++) {
				Crack c = new Crack();
				c.lump = i;
				double ang = random.nextDouble() * Math.PI * 2.0;
				double x = l.x + Math.cos(ang) * l.rx * 0.8;
				double z = l.z + Math.sin(ang) * l.rz * 0.8;
				int segs = 5 + random.nextInt(3);
				for (int s = 0; s <= segs; s++) {
					double y = groundNear(world, hub.getX() + x, hy + centreGround, hub.getZ() + z) - hy + 0.03;
					c.pts.add(new Vec3d(x, y, z));
					ang += (random.nextDouble() - 0.5) * 1.3;
					double len = 0.35 + random.nextDouble() * 0.4;
					x += Math.cos(ang) * len;
					z += Math.sin(ang) * len;
				}
				cracks.add(c);
			}
		}
	}

	/** The point on {@code part}'s surface in horizontal direction {@code h} at elevation {@code e} (radians). */
	static Vec3d surface(Part part, Vec3d h, double e) {
		return new Vec3d(part.x + part.rx * Math.cos(e) * h.x, part.y + part.ry * Math.sin(e), part.z + part.rz * Math.cos(e) * h.z);
	}

	static Vec3d normal(Part part, Vec3d h, double e) {
		return new Vec3d(Math.cos(e) * h.x / part.rx, Math.sin(e) / part.ry, Math.cos(e) * h.z / part.rz).normalize();
	}

	/** The height of the highest solid thing under {@code (x, y, z)} within 24 blocks (or {@code y - 24}). */
	static double groundBelow(ClientWorld world, double x, double y, double z) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		for (int by = MathHelper.floor(y + 0.01); by > MathHelper.floor(y) - 24; by--) {
			double top = topOf(world, bx, by, bz);
			if (!Double.isNaN(top) && top <= y + 0.01) {
				return top;
			}
		}
		return y - 24.0;
	}

	/** The ground near height {@code y} in the column of {@code (x, z)}: the highest solid top from 1.5 above {@code y} down to 5 below it. */
	static double groundNear(ClientWorld world, double x, double y, double z) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		for (int by = MathHelper.floor(y) + 1; by >= MathHelper.floor(y) - 5; by--) {
			double top = topOf(world, bx, by, bz);
			if (!Double.isNaN(top)) {
				return top;
			}
		}
		return y;
	}

	private static double topOf(ClientWorld world, int x, int y, int z) {
		BlockPos pos = new BlockPos(x, y, z);
		BlockState state = world.getBlockState(pos);
		VoxelShape shape = state.getCollisionShape(world, pos);
		return shape.isEmpty() ? Double.NaN : y + shape.getMax(Direction.Axis.Y);
	}
}
