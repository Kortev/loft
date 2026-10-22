package io.github.kortev.shootingstar.thunder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * The scar the stroke burns into the ground: a Lichtenberg figure, the branching fern of fused channels that the
 * current leaves as it spreads out through the earth from where lightning lands. It grows from one seed, so the server
 * (which burns it into the blocks) and every client (which draws the current racing along it) grow exactly the same one.
 *
 * <p>Coordinates are in blocks relative to the centre of the struck block, x and z. A handful of main channels leave the
 * strike point, wander outward and keep forking, each fork thinner than the channel it came from, until they thin out
 * to nothing or reach the edge of the scar. Each segment knows how far along its path from the strike point it starts and
 * ends, which is when the burning front reaches it.
 */
public final class Lichtenberg {
	/** One straight piece of a channel: from (x0, z0) to (x1, z1), {@code width} blocks across, at path distance from..to. */
	public record Segment(float x0, float z0, float x1, float z1, float width, float from, float to) {
	}

	/** A cell of the scar burned into the ground: its offset, when the front reaches it, how wide and how central it is. */
	public record Cell(int dx, int dz, float arrival, float width, float centrality) {
	}

	private static final int MAX_SEGMENTS = 9000;
	/** Thinner than this and a fork has burned out. */
	private static final float MIN_WIDTH = 0.38F;

	/** A channel still growing; {@code home} is the way a main channel keeps heading, so the arms stay spread round. */
	private record Branch(float x, float z, float heading, float home, float width, float distance, float limit, int depth) {
	}

	private final List<Segment> segments;
	private final float reach;
	private final int limit;

	private Lichtenberg(List<Segment> segments, int limit) {
		this.segments = Collections.unmodifiableList(segments);
		float max = 0.0F;
		for (Segment s : segments) {
			max = Math.max(max, s.to());
		}
		this.reach = max;
		this.limit = limit;
	}

	public List<Segment> segments() {
		return segments;
	}

	/** The longest path from the strike point to a tip, in blocks. */
	public float reach() {
		return reach;
	}

	/** How far from the strike point any part of the figure may lie. */
	public int limit() {
		return limit;
	}

	/** Grows the figure for a strike of the given radius (the scar reaches out to 1.5 times it). */
	public static Lichtenberg grow(long seed, int radius) {
		Random random = new Random(seed);
		int limit = ThunderTimeline.scarRadius(radius);
		double scale = radius / 64.0;
		float startWidth = (float) Math.max(1.6, Math.min(5.0, 3.4 * Math.sqrt(scale)));
		// Big strikes take longer steps, so their scars branch like the default one instead of turning to wire.
		float stepScale = (float) Math.max(1.0, Math.pow(scale, 0.6));
		List<Segment> out = new ArrayList<>();
		Deque<Branch> open = new ArrayDeque<>();
		int arms = 7 + random.nextInt(3);
		double spacing = Math.PI * 2.0 / arms;
		double turn = random.nextDouble() * Math.PI * 2.0;
		for (int i = 0; i < arms; i++) {
			float heading = (float) (turn + i * spacing + random.nextGaussian() * spacing * 0.18);
			float reachOut = (float) (limit * (0.78 + 0.22 * random.nextDouble()));
			open.add(new Branch(0.0F, 0.0F, heading, heading, startWidth * (float) (0.8 + 0.3 * random.nextDouble()), 0.0F,
					reachOut, 0));
		}
		while (!open.isEmpty() && out.size() < MAX_SEGMENTS) {
			grow(open.pollFirst(), random, open, out, stepScale);
		}
		return new Lichtenberg(out, limit);
	}

	private static void grow(Branch b, Random random, Deque<Branch> open, List<Segment> out, float stepScale) {
		float x = b.x();
		float z = b.z();
		float heading = b.heading();
		float width = b.width();
		float distance = b.distance();
		// Forks branch less the deeper they are, so the figure stays a fern and not a mat.
		double split = b.depth() < 2 ? 0.09 : b.depth() < 4 ? 0.065 : 0.04;
		while (out.size() < MAX_SEGMENTS) {
			float r = (float) Math.sqrt(x * x + z * z);
			if (r >= b.limit() || width < MIN_WIDTH || distance > b.limit() * 2.4F) {
				return;
			}
			// A random walk, pulled gently back: a main channel towards its own heading, so the arms stay spread round
			// the strike point, and a fork towards running straight out from it.
			heading += (float) (random.nextGaussian() * 0.26);
			if (b.depth() == 0) {
				heading += wrap(b.home() - heading) * 0.14F;
			} else if (r > 1.5F) {
				float outward = (float) Math.atan2(z, x);
				heading += wrap(outward - heading) * 0.12F;
			}
			float step = (float) (1.6 + 1.3 * random.nextDouble()) * stepScale;
			float nx = x + (float) Math.cos(heading) * step;
			float nz = z + (float) Math.sin(heading) * step;
			out.add(new Segment(x, z, nx, nz, width, distance, distance + step));
			x = nx;
			z = nz;
			distance += step;
			width *= 0.988F;
			if (width > 0.7F && random.nextDouble() < split) {
				float side = random.nextBoolean() ? 1.0F : -1.0F;
				float fork = heading + side * (float) (0.4 + 0.5 * random.nextDouble());
				float forkWidth = width * (float) (0.55 + 0.15 * random.nextDouble());
				float remaining = Math.max(0.0F, b.limit() - (float) Math.sqrt(x * x + z * z));
				float forkLimit = (float) Math.sqrt(x * x + z * z) + remaining * (float) (0.45 + 0.55 * random.nextDouble());
				open.add(new Branch(x, z, fork, fork, forkWidth, distance, Math.min(b.limit(), forkLimit), b.depth() + 1));
				width *= 0.88F;
			}
		}
	}

	private static float wrap(float angle) {
		while (angle > Math.PI) {
			angle -= (float) (Math.PI * 2.0);
		}
		while (angle < -Math.PI) {
			angle += (float) (Math.PI * 2.0);
		}
		return angle;
	}

	/**
	 * The figure as cells of the ground, outside {@code skip} blocks from the strike point, sorted by when the burning
	 * front reaches them. A cell is in the scar when it lies within half a channel's width (and a little) of the channel's
	 * line; where channels cross, the more central one decides how deep it burns and the earlier one when.
	 */
	public List<Cell> cells(double skip) {
		int half = limit + 4;
		int size = half * 2 + 1;
		float[] arrival = new float[size * size];
		float[] width = new float[size * size];
		float[] centrality = new float[size * size];
		Arrays.fill(arrival, Float.POSITIVE_INFINITY);
		for (Segment s : segments) {
			float reachOut = s.width() * 0.5F + 0.35F;
			int minX = (int) Math.floor(Math.min(s.x0(), s.x1()) - reachOut);
			int maxX = (int) Math.ceil(Math.max(s.x0(), s.x1()) + reachOut);
			int minZ = (int) Math.floor(Math.min(s.z0(), s.z1()) - reachOut);
			int maxZ = (int) Math.ceil(Math.max(s.z0(), s.z1()) + reachOut);
			float dx = s.x1() - s.x0();
			float dz = s.z1() - s.z0();
			float len2 = Math.max(dx * dx + dz * dz, 1.0E-6F);
			for (int cx = minX; cx <= maxX; cx++) {
				for (int cz = minZ; cz <= maxZ; cz++) {
					if (Math.abs(cx) > half || Math.abs(cz) > half) {
						continue;
					}
					float t = Math.max(0.0F, Math.min(1.0F, ((cx - s.x0()) * dx + (cz - s.z0()) * dz) / len2));
					float px = s.x0() + dx * t - cx;
					float pz = s.z0() + dz * t - cz;
					float d = (float) Math.sqrt(px * px + pz * pz);
					if (d > reachOut) {
						continue;
					}
					int i = (cx + half) * size + (cz + half);
					float central = 1.0F - d / reachOut;
					float when = s.from() + (s.to() - s.from()) * t;
					arrival[i] = Math.min(arrival[i], when);
					if (central * s.width() > centrality[i] * width[i]) {
						centrality[i] = central;
						width[i] = s.width();
					}
				}
			}
		}
		List<Cell> cells = new ArrayList<>();
		double skip2 = skip * skip;
		for (int cx = -half; cx <= half; cx++) {
			for (int cz = -half; cz <= half; cz++) {
				int i = (cx + half) * size + (cz + half);
				if (arrival[i] != Float.POSITIVE_INFINITY && cx * cx + cz * cz > skip2) {
					cells.add(new Cell(cx, cz, arrival[i], width[i], centrality[i]));
				}
			}
		}
		cells.sort((a, b) -> Float.compare(a.arrival(), b.arrival()));
		return cells;
	}
}
