package io.github.kortev.shootingstar.client;

import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** What the client knows about one strike. Ages on client ticks, in step with the server. */
public final class ClientStrike {
	public final int id;
	public final BlockPos target;
	public final Vec3d center;
	public final UUID shooter;
	public final boolean mine;
	public int age;
	public boolean impacted;
	public int impactAge = -1;
	public int radius = 64;
	public int zoneDiameter = 128;
	public int spireHeight;
	/** The shooter pressed skip, or the feed is off: no feed and no camera shots. */
	public boolean feedSkipped;
	/** Ticks spent holding just before impact while waiting for the server. */
	public int holdTicks;
	public int lastLap;
	/** Ground heights around the target for draping the reticle, sampled once. */
	public float[] ground;

	public ClientStrike(int id, BlockPos target, UUID shooter, boolean mine, int age) {
		this.id = id;
		this.target = target;
		this.center = Vec3d.ofBottomCenter(target.up());
		this.shooter = shooter;
		this.mine = mine;
		this.age = age;
	}

	public double time(float tickDelta) {
		return age + tickDelta;
	}

	/** True while the shooter's feed or camera shots own the screen. */
	public boolean cinematic() {
		return mine && !feedSkipped;
	}
}
