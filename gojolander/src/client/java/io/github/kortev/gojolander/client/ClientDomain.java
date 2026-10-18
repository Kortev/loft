package io.github.kortev.gojolander.client;

import io.github.kortev.gojolander.domain.DomainTimeline;
import java.util.HashSet;
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
	/** The void's sounds, stopped when it shatters. */
	@Nullable
	SoundInstance voidSound;
	@Nullable
	SoundInstance floodSound;
	/** Whether this player's camera was inside it when it closed, for which sounds they hear. */
	boolean heardInside;

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

	public boolean contains(Vec3d point) {
		return point.squaredDistanceTo(center) < (double) radius * radius;
	}

	/** The way the caster faced, as a flat unit vector. */
	public Vec3d facing() {
		float r = yaw * MathHelper.RADIANS_PER_DEGREE;
		return new Vec3d(-MathHelper.sin(r), 0.0, MathHelper.cos(r));
	}
}
