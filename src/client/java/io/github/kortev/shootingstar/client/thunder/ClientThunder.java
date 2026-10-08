package io.github.kortev.shootingstar.client.thunder;

import io.github.kortev.shootingstar.thunder.Lichtenberg;
import io.github.kortev.shootingstar.thunder.ThunderTimeline;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** What the client knows about one Mjölnir strike. Ages on client ticks, in step with the server. */
public final class ClientThunder {
	/** One arc off the bolt: from, to, and the tick after the stroke when it lands. */
	public record Arc(Vec3d from, Vec3d to, int delay, long seed) {
	}

	public final int id;
	public final BlockPos target;
	/** The ground the bolt comes down on: the top of the struck block. */
	public final Vec3d center;
	public final UUID shooter;
	public final boolean mine;
	public final long seed;
	/** Height of the storm's cloud base, where the bolt comes out of. */
	public final double cloudBase;
	public int radius;
	public int age;
	public boolean struck;
	public boolean terrain = true;
	public int boltHeight;
	/** The shooter pressed skip, or the feed is off: no feed and no camera shots. */
	public boolean feedSkipped;
	/** Ticks spent holding just before the stroke while waiting for the server. */
	public int holdTicks;
	/** Where the call left the hammer, taken from the shooter as it fires (null if they were never seen). */
	@Nullable
	public Vec3d callFrom;
	/** Where the shooter's camera watches the bolt come down from, chosen once. */
	@Nullable
	public Vec3d witness;
	/** The arcs, once the server has sent them. */
	public final List<Arc> arcs = new ArrayList<>();
	/** The feed's long sounds, stopped if the shooter skips the feed. */
	public final List<SoundInstance> feedSounds = new ArrayList<>();
	@Nullable
	private Lichtenberg figure;
	/** Ground height under each end of each of the scar's segments, sampled when the bolt lands. */
	@Nullable
	public float[] scarHeights;
	/** Ground heights round the edge of the zone and of the scar, for the warning rings. */
	@Nullable
	public float[] zoneHeights;
	@Nullable
	public float[] scarEdgeHeights;
	/** The bolt's own channel and branches, grown from the seed the first time they are drawn. */
	@Nullable
	public BoltPath path;

	public ClientThunder(int id, BlockPos target, UUID shooter, boolean mine, int age, int radius, long seed, int topY) {
		this.id = id;
		this.target = target;
		this.center = Vec3d.ofBottomCenter(target.up());
		this.shooter = shooter;
		this.mine = mine;
		this.age = age;
		this.radius = radius;
		this.seed = seed;
		this.cloudBase = ThunderTimeline.cloudBase(target.getY(), topY);
	}

	public double time(float tickDelta) {
		return age + tickDelta;
	}

	/** Ticks since the stroke, with the partial tick; negative before it. */
	public double sinceStroke(float tickDelta) {
		return age + tickDelta - ThunderTimeline.STROKE;
	}

	/** True while the shooter's feed or camera shots own the screen. */
	public boolean cinematic() {
		return mine && !feedSkipped;
	}

	public Lichtenberg figure() {
		if (figure == null) {
			figure = Lichtenberg.grow(seed, radius);
		}
		return figure;
	}

	/** The storm's base straight over the target. */
	public Vec3d top() {
		return new Vec3d(center.x, cloudBase, center.z);
	}

	/** How far the wall cloud hangs under the storm's base: a seventh of the storm's height, 6 to 18 blocks. */
	public double wallDrop() {
		return MathHelper.clamp((cloudBase - center.y) * 0.15, 6.0, 18.0);
	}

	/** Where the bolt leaves the storm: the bottom of the wall cloud, straight over the target. */
	public Vec3d wallBase() {
		return new Vec3d(center.x, cloudBase - wallDrop(), center.z);
	}
}
