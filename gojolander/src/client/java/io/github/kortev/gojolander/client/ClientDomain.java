package io.github.kortev.gojolander.client;

import io.github.kortev.gojolander.domain.DomainTimeline;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** What a client knows about one Domain. It ages on client ticks, in step with the server. */
public final class ClientDomain {
	public final int id;
	/** Who cast it, or null for one an operator called. */
	@Nullable
	public final UUID caster;
	/** Whether this client's player cast it. */
	public final boolean mine;
	/** Where he stood: the middle of the ring on the ground and of the sphere. */
	public final Vec3d center;
	public final float radius;
	public final int hold;
	/** Which way he faced as he cast it (degrees): the camera's shot is taken from in front of him, and the void's great eye is that way. */
	public final float yaw;
	public int age;
	/** The entities (network ids) it caught when it closed. */
	public final Set<Integer> caught = new HashSet<>();
	/** Whether this client's player is one of them. */
	public boolean holdsMe;
	/** Whether it was cut short (its caster fell, an operator ended it): then it shatters, instead of fading away. */
	public boolean shattered;
	/** The void's sounds, stopped as it ends. */
	@Nullable
	SoundInstance voidSound;
	@Nullable
	SoundInstance floodSound;
	/** Whether this player's camera was inside it when it closed, for which sounds they hear. */
	boolean heardInside;
	/**
	 * The caught (network ids) whose knees gave out as it let them go at its end (not cut short), and the age their
	 * going down is counted from: its collapse for whoever was on the ground then; for whoever it held in the air, a
	 * little before they landed, so their knees give out as they land.
	 */
	public final Map<Integer, Integer> knelt = new HashMap<>();
	/** The caught it let go of in the air, until they land (or it is too long since). */
	public final Set<Integer> falling = new HashSet<>();
	/** How long a jolt at a step down takes to die away, in ticks. */
	private static final double JOLT = 3.5;

	public ClientDomain(int id, @Nullable UUID caster, boolean mine, Vec3d center, float radius, int hold, float yaw, int age) {
		this.id = id;
		this.caster = caster;
		this.mine = mine;
		this.center = center;
		this.radius = radius;
		this.hold = hold;
		this.yaw = yaw;
		this.age = age;
	}

	/** Its age in ticks, between ticks too. */
	public double time(float tickDelta) {
		return age + MathHelper.clamp(tickDelta, 0.0F, 1.0F);
	}

	public int collapse() {
		return DomainTimeline.collapse(hold);
	}

	public int end() {
		return DomainTimeline.end(hold);
	}

	/** Whether the barrier stands at tick {@code t}: closed and not yet shattering. */
	public boolean standing(double t) {
		return t >= DomainTimeline.CLOSE && t < collapse();
	}

	/** How hard the flood jolts whoever it holds at tick {@code t}, from 0 to 1: at each step down, dying away fast. */
	public float jolt(double t) {
		if (shattered && t >= collapse()) {
			return 0.0F;
		}
		double sum = 0.0;
		int steps = DomainTimeline.steps(hold);
		for (int k = 1; k < steps; k++) {
			double x = t - DomainTimeline.step(hold, k);
			if (x >= 0.0 && x < JOLT * 5.0) {
				sum += Math.min(1.0, x * 2.0) * Math.exp(-x / JOLT);
			}
		}
		return (float) Math.min(1.0, sum);
	}

	/**
	 * For {@code id}, let go of on their knees, how far down they are at tick {@code t}, from 0 (standing) to 1: their
	 * knees giving out as the ringing snaps off (falling, faster and faster, onto them at SNAP), there until they come to
	 * at UP, and up again by RISEN (all counted from when their going down is counted from, {@link #knelt}).
	 */
	public float kneel(int id, double t) {
		Integer from = knelt.get(id);
		double x = from != null ? t - from : -1.0;
		if (shattered || x < 0.0 || x >= DomainTimeline.RISEN) {
			return 0.0F;
		}
		double down = MathHelper.clamp((x - (DomainTimeline.SNAP - 3.5)) / 3.5, 0.0, 1.0);
		double up = MathHelper.clamp((x - DomainTimeline.UP) / (DomainTimeline.RISEN - DomainTimeline.UP), 0.0, 1.0);
		return (float) Math.min(down * down, 1.0 - up * up * (3.0 - 2.0 * up));
	}

	/**
	 * For {@code id}, let go of on their knees from where they stood: whether they are still as it held them in the moment
	 * before it snaps (the ringing climbing).
	 */
	public boolean snapping(int id, double t) {
		Integer from = knelt.get(id);
		double x = t - collapse();
		return !shattered && from != null && from == collapse() && x >= 0.0 && x < DomainTimeline.SNAP;
	}

	/** For {@code id}, let go of on their knees: the age they are up again by, or -1 if they never went down. */
	public int risen(int id) {
		Integer from = knelt.get(id);
		return from != null ? from + DomainTimeline.RISEN : -1;
	}

	/** For {@code id}, let go of on their knees: the age they come to (and may move again), or -1 if they never went down. */
	public int comeTo(int id) {
		Integer from = knelt.get(id);
		return from != null ? from + DomainTimeline.UP : -1;
	}

	public boolean contains(Vec3d point) {
		return point.squaredDistanceTo(center) < (double) radius * radius;
	}

	/** The way the caster faced, as a flat unit vector. */
	public Vec3d facing() {
		float r = yaw * MathHelper.RADIANS_PER_DEGREE;
		return new Vec3d(-MathHelper.sin(r), 0.0, MathHelper.cos(r));
	}
}
