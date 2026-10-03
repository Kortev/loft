package io.github.kortev.shootingstar.client;

import io.github.kortev.shootingstar.client.world.ImpactScene;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

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
	public int groundRadius;
	public int groundStep;
	/** The blast as this client sees it, once the round has hit. */
	@Nullable
	public ImpactScene scene;
	/** Where the shooter's camera watches the impact from, chosen once when the round comes in. */
	@Nullable
	public Vec3d witness;
	/** Unit vector from the target back along the round's path, chosen once so the fall stays in view. */
	@Nullable
	public Vec3d approach;

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
