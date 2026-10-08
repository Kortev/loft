package io.github.kortev.shootingstar.client.thunder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.util.math.Vec3d;

/**
 * The shape of Mjölnir's bolt: one tortuous main channel from the cloud base to the ground, and the branches the
 * stepped leader put out on its way down that never reached it. Grown from the strike's seed, so everyone sees the same
 * bolt, and the leader that feels its way down is the same path the return stroke lights.
 */
public final class BoltPath {
	/** A branch off the channel: its points from where it leaves to its tip, and how bright it lights. */
	public record Branch(List<Vec3d> points, float strength) {
	}

	/** From the cloud base (first) to the ground (last). */
	public final List<Vec3d> channel;
	public final List<Branch> branches = new ArrayList<>();
	public final double top;
	public final double bottom;

	private BoltPath(List<Vec3d> channel) {
		this.channel = channel;
		this.top = channel.get(0).y;
		this.bottom = channel.get(channel.size() - 1).y;
	}

	public static BoltPath grow(long seed, Vec3d from, Vec3d ground, int radius) {
		Random random = new Random(seed ^ 0x2545F4914F6CDD1DL);
		double lean = Math.min(18.0, radius * 0.15);
		Vec3d start = from.add(random.nextGaussian() * lean, 0.0, random.nextGaussian() * lean);
		BoltPath path = new BoltPath(jagged(start, ground, 0.17, 7, random));
		double height = path.top - path.bottom;
		int count = 7 + random.nextInt(5);
		for (int i = 0; i < count; i++) {
			int at = (int) (path.channel.size() * (0.04 + 0.76 * random.nextDouble()));
			Vec3d root = path.channel.get(at);
			double above = root.y - path.bottom;
			double angle = random.nextDouble() * Math.PI * 2.0;
			double spread = 0.5 + 0.9 * random.nextDouble();
			Vec3d dir = new Vec3d(Math.cos(angle) * spread, -1.0, Math.sin(angle) * spread).normalize();
			double length = above * (0.22 + 0.4 * random.nextDouble());
			List<Vec3d> points = jagged(root, root.add(dir.multiply(length)), 0.3, 5, random);
			path.branches.add(new Branch(points, 0.65F));
			// A fork or two off the branch, shorter again.
			int forks = random.nextInt(3);
			for (int f = 0; f < forks; f++) {
				Vec3d fork = points.get(points.size() / 4 + random.nextInt(Math.max(1, points.size() / 2)));
				double a = angle + (random.nextBoolean() ? 1 : -1) * (0.5 + random.nextDouble());
				Vec3d d = new Vec3d(Math.cos(a) * spread, -0.8, Math.sin(a) * spread).normalize();
				path.branches.add(new Branch(jagged(fork, fork.add(d.multiply(length * (0.25 + 0.3 * random.nextDouble()))), 0.35, 4,
						random), 0.4F));
			}
		}
		if (height <= 0) {
			path.branches.clear();
		}
		return path;
	}

	/** How far down the descent a height is, 0 at the cloud base and 1 at the ground. */
	public double depth(double y) {
		return (top - y) / Math.max(1.0, top - bottom);
	}

	/** A jagged line from a to b by midpoint displacement, sideways to the line, rougher at the coarser levels. */
	public static List<Vec3d> jagged(Vec3d a, Vec3d b, double roughness, int levels, Random random) {
		List<Vec3d> points = new ArrayList<>();
		points.add(a);
		subdivide(points, a, b, roughness, levels, random);
		return points;
	}

	private static void subdivide(List<Vec3d> points, Vec3d a, Vec3d b, double roughness, int levels, Random random) {
		if (levels == 0) {
			points.add(b);
			return;
		}
		Vec3d d = b.subtract(a);
		double len = d.length();
		Vec3d axis = d.multiply(1.0 / Math.max(len, 1.0E-6));
		Vec3d jitter = new Vec3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
		jitter = jitter.subtract(axis.multiply(jitter.dotProduct(axis)));
		Vec3d mid = a.add(d.multiply(0.5)).add(jitter.multiply(len * roughness * 0.5));
		subdivide(points, a, mid, roughness * 0.72, levels - 1, random);
		subdivide(points, mid, b, roughness * 0.72, levels - 1, random);
	}
}
