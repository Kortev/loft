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
 * Where the Eternity Devil's flesh comes up round a locked target, as in kortev's storyboard: one small low lump (about a block across,
 * half a block high, a cluster of lobes) at the feet of the held thing and of each moment of it, with the sinews rising from it up
 * that moment's legs; an eye on each (they all turn to the held one) and a mouth on each facing out sideways from the line; cracks in the
 * ground round them. Worked out from the hub's seed and where the copies stand, so every client draws the same devil without being told;
 * positions are relative to the hub (where the target froze).
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
	/** What it was made from: the number of moments (to remake it when they change). */
	final int moments;
	final List<Lump> lumps = new ArrayList<>();
	final List<Eye> eyes = new ArrayList<>();
	final List<Mouth> mouths = new ArrayList<>();
	final List<Crack> cracks = new ArrayList<>();
	/** How high the ground under the hub is, relative to the hub (negative: the target hangs in the air). */
	double centreGround;

	/**
	 * @param feet where the held thing (first) and each moment of it stand, relative to the hub
	 * @param scale 1 for something the size of a person, more for something bigger
	 */
	ForeverLayout(ClientWorld world, ForeverHubEntity hub, List<Vec3d> feet, double scale) {
		this.seed = hub.seed();
		this.moments = feet.size();
		Random random = Random.create(seed);
		double hy = hub.getY();
		centreGround = groundBelow(world, hub.getX(), hy, hub.getZ()) - hy;
		// The line's way: from the farthest pair.
		Vec3d axis = new Vec3d(1.0, 0.0, 0.0);
		double far = 0.0;
		for (Vec3d a : feet) {
			for (Vec3d b : feet) {
				double d = (a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z);
				if (d > far) {
					far = d;
					axis = new Vec3d(a.x - b.x, 0.0, a.z - b.z).normalize();
				}
			}
		}
		Vec3d across = new Vec3d(-axis.z, 0.0, axis.x);
		double size = Math.min(2.6, Math.max(1.0, scale));
		for (int i = 0; i < feet.size(); i++) {
			Vec3d p = feet.get(i);
			Lump l = new Lump();
			l.x = p.x;
			l.z = p.z;
			l.rx = 0.5 * size * (0.9 + random.nextDouble() * 0.25);
			l.rz = 0.48 * size * (0.9 + random.nextDouble() * 0.25);
			l.ry = 0.3 * size * (0.9 + random.nextDouble() * 0.2);
			l.delay = i * 0.5;
			l.ground = groundBelow(world, hub.getX() + p.x, hy + Math.max(p.y, centreGround) + 1.0, hub.getZ() + p.z) - hy;
			Part main = new Part();
			main.x = l.x;
			main.z = l.z;
			main.y = l.ground - 0.05;
			main.rx = l.rx;
			main.ry = l.ry;
			main.rz = l.rz;
			main.seed = random.nextInt();
			l.parts.add(main);
			// A small cluster: lobes of different sizes round the main one.
			for (int k = 0; k < 3; k++) {
				Part s = new Part();
				double a2 = k * Math.PI * 2.0 / 3.0 + random.nextDouble() * 1.4;
				double sz = 0.38 + random.nextDouble() * 0.34;
				s.rx = l.rx * sz;
				s.rz = l.rz * sz;
				s.ry = l.ry * (0.6 + random.nextDouble() * 0.4);
				s.x = l.x + Math.cos(a2) * l.rx * (0.6 + random.nextDouble() * 0.3);
				s.z = l.z + Math.sin(a2) * l.rz * (0.6 + random.nextDouble() * 0.3);
				s.y = l.ground - 0.06;
				s.seed = random.nextInt();
				l.parts.add(s);
			}
			lumps.add(l);
			// An eye on it, up on the side that looks in at the held one.
			Eye eye = new Eye();
			eye.lump = i;
			eye.index = i;
			Vec3d inward = new Vec3d(-p.x, 0.0, -p.z);
			double az = inward.lengthSquared() < 0.04 ? random.nextDouble() * Math.PI * 2.0 : Math.atan2(inward.z, inward.x) + (random.nextDouble() - 0.5) * 0.5;
			Vec3d hh = new Vec3d(Math.cos(az), 0.0, Math.sin(az));
			eye.radius = 0.2 * Math.min(size, 1.7);
			double el = 0.55 + random.nextDouble() * 0.35;
			eye.normal = normal(main, hh, el);
			eye.pos = surface(main, hh, el).add(eye.normal.multiply(eye.radius * 0.45));
			eyes.add(eye);
			// A mouth on it, facing out sideways from the line, alternate sides.
			Mouth mouth = new Mouth();
			mouth.lump = i;
			Vec3d out = across.multiply(i % 2 == 0 ? 1.0 : -1.0).add(axis.multiply((random.nextDouble() - 0.5) * 0.5)).normalize();
			double me = 0.22 + random.nextDouble() * 0.18;
			mouth.pos = surface(main, out, me);
			mouth.normal = normal(main, out, me);
			mouth.width = 0.62 * Math.pow(size, 0.8) * (0.9 + random.nextDouble() * 0.3);
			mouth.seed = random.nextInt();
			mouth.teeth = 10 + random.nextInt(3);
			mouths.add(mouth);
			// Cracks in the ground, short, out from the lump.
			int strands = 2 + random.nextInt(2);
			for (int k = 0; k < strands; k++) {
				Crack c = new Crack();
				c.lump = i;
				double ang = random.nextDouble() * Math.PI * 2.0;
				double x = l.x + Math.cos(ang) * l.rx * 0.8;
				double z = l.z + Math.sin(ang) * l.rz * 0.8;
				int segs = 3 + random.nextInt(3);
				for (int s = 0; s <= segs; s++) {
					double y = groundNear(world, hub.getX() + x, hy + l.ground, hub.getZ() + z) - hy + 0.03;
					c.pts.add(new Vec3d(x, y, z));
					ang += (random.nextDouble() - 0.5) * 1.5;
					double len = (0.25 + random.nextDouble() * 0.3) * size;
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
