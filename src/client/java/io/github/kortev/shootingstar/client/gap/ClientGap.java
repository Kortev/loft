package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.gap.GapTimeline;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** What the client knows about one Ginnungagap event. Ages on client ticks, in step with the server. */
public final class ClientGap {
	/** A block that traded places with its twin: where it was, the colour it had, when, and how (0 single, 1 whole). */
	public record Swap(BlockPos pos, int color, int age, int kind) {
	}

	public final int id;
	public final BlockPos target;
	/** Top of the target block: the point of contact. */
	public final int surface;
	public final Vec3d contact;
	public final UUID shooter;
	public final boolean mine;
	public final int radius;
	public final boolean terrain;
	public final BlockPos swapSpot;
	public final boolean tree;
	/** Horizontal unit vectors: {@code along} from the shooter towards the target, {@code across} to its right. */
	public final Vec3d along;
	public final Vec3d across;
	/** Where the shooter stood when the key turned. */
	public final Vec3d shooterPos;
	public int age;
	public boolean ended;
	/** Where the wide shot stands, found once so it can see the target over the hills. */
	@Nullable
	public Vec3d wideEye;
	/** Where the swap and under-the-shard shots stand, and which side of the shooter the close shots take. */
	@Nullable
	public Vec3d swapEye;
	@Nullable
	public Vec3d underEye;
	public int side;
	public final List<Swap> swaps = new ArrayList<>();

	public ClientGap(int id, BlockPos target, UUID shooter, boolean mine, int age, int radius, boolean terrain, BlockPos swapSpot,
			boolean tree, Vec3d shooterPos) {
		this.id = id;
		this.target = target;
		this.surface = target.getY() + 1;
		this.contact = new Vec3d(target.getX() + 0.5, surface, target.getZ() + 0.5);
		this.shooter = shooter;
		this.mine = mine;
		this.age = age;
		this.radius = radius;
		this.terrain = terrain;
		this.swapSpot = swapSpot;
		this.tree = tree;
		this.shooterPos = shooterPos;
		Vec3d d = new Vec3d(contact.x - shooterPos.x, 0, contact.z - shooterPos.z);
		this.along = d.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : d.normalize();
		this.across = new Vec3d(-along.z, 0, along.x);
	}

	public double time(float tickDelta) {
		return age + tickDelta;
	}

	/** Where the falling shard's tip is. */
	public Vec3d shardTip(double t) {
		return contact.add(0, GapTimeline.shardTip(t), 0);
	}

	/** Where a block swapped out of our universe goes: up into the broken sky. */
	public Vec3d swappedTo(BlockPos pos) {
		return Vec3d.ofCenter(pos).add(0, 90, 0);
	}

	/** The shooter's own camera shots and black screen play only for them. */
	public boolean cinematic() {
		return mine && !ended;
	}
}
