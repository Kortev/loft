package io.github.kortev.gojolander.client;

import io.github.kortev.gojolander.purple.PurpleTimeline;
import java.util.UUID;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** What a client knows about one Hollow Purple. It ages on client ticks, in step with the server. */
public final class ClientPurple {
	public final int id;
	/** Who cast it, or null for one an operator called. */
	@Nullable
	public final UUID caster;
	/** Whether this client's player cast it. */
	public final boolean mine;
	/** His eyes as he cast it, the way it flies (a unit vector) and where its sphere is released from. */
	public final Vec3d eye;
	public final Vec3d dir;
	public final Vec3d form;
	public final float radius;
	public final float range;
	public int age;
	/** Cut short before its release: nothing is drawn any more. */
	public boolean cancelled;
	/** The last tick whose dust was thrown, so each stretch of the tunnel is dusted once. */
	double dusted;

	public ClientPurple(int id, @Nullable UUID caster, boolean mine, Vec3d eye, Vec3d dir, float radius, float range, int age) {
		this.id = id;
		this.caster = caster;
		this.mine = mine;
		this.eye = eye;
		this.dir = dir;
		this.radius = radius;
		this.range = range;
		this.age = age;
		this.form = eye.add(dir.multiply(radius + 1.5));
	}

	/** Its age in ticks, between ticks too. */
	public double time(float tickDelta) {
		return age + MathHelper.clamp(tickDelta, 0.0F, 1.0F);
	}

	/** The tick its sphere has collapsed away by. */
	public int end() {
		return PurpleTimeline.end(range);
	}

	/** The tick its sphere reaches the end of its range (and starts to collapse). */
	public int arrives() {
		return PurpleTimeline.RELEASE + PurpleTimeline.flight(range);
	}

	/** How far its sphere has flown at {@code t}. */
	public double front(double t) {
		return PurpleTimeline.front(t, range);
	}

	/** Where its sphere is at {@code t}: swelling out of the collision between his hands, then flying. */
	public Vec3d sphereAt(double t, Vec3d merge) {
		if (t < PurpleTimeline.RELEASE) {
			double k = PurpleTimeline.sphere(t);
			return merge.lerp(form, k * k * (3.0 - 2.0 * k));
		}
		return form.add(dir.multiply(front(t)));
	}

	/** How big its sphere is at {@code t}: it swells, and at the end of its range collapses. */
	public double sphereRadius(double t) {
		double swell = PurpleTimeline.sphere(t);
		double c = collapse(t);
		return radius * 0.98 * swell * (1.0 - c * c * 0.95) * (1.0 + 0.03 * Math.sin(t * 1.7));
	}

	/** How far its collapse has got at {@code t}, from 0 to 1. */
	public double collapse(double t) {
		return MathHelper.clamp((t - arrives()) / PurpleTimeline.FADE, 0.0, 1.0);
	}

	/** The way he faced as a flat unit vector (or the cast's own yaw, if he looked straight up or down). */
	public Vec3d flat(float fallbackYaw) {
		double len = Math.hypot(dir.x, dir.z);
		if (len < 1.0E-4) {
			float r = fallbackYaw * MathHelper.RADIANS_PER_DEGREE;
			return new Vec3d(-MathHelper.sin(r), 0.0, MathHelper.cos(r));
		}
		return new Vec3d(dir.x / len, 0.0, dir.z / len);
	}

	/** The pitch he looked at (radians, down positive). */
	public float pitch() {
		return (float) Math.asin(MathHelper.clamp(-dir.y, -1.0, 1.0));
	}
}
