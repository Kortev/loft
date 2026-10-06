package io.github.kortev.shootingstar.client.gap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** What the client knows about one Ginnungagap event. Ages on client ticks, in step with the server. */
public final class ClientGap {
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
	/** When the shooter used the key again and the rebuild began, or -1; and where they were stood for it, out of the hole. */
	public int rebuildAt = -1;
	@Nullable
	public Vec3d rebuildFrom;
	/** How far the rebuild has got, in its own ticks; and whether the shooter has hurried it on with the skip key. */
	public double rebuildClock;
	public boolean rebuildHurried;
	@Nullable
	RebuildSound rebuildSound;
	/** Someone else's, released: when this player started watching its tree grow out of the hole, or -1. */
	public int spectateAt = -1;
	/** The shooter skipped the feed: they watch the bridge come down from the world instead. */
	public boolean feedSkipped;
	/** The feed's sounds, stopped if it is skipped. */
	public final List<SoundInstance> feedSounds = new ArrayList<>();
	/** Where the witness shot stands, found once so it can see the target over the hills. */
	@Nullable
	public Vec3d wideEye;

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

	/** Ticks into the rebuild, or -1 before it: six to a tick once the shooter has hurried it on. */
	public double rebuild(float tickDelta) {
		return rebuildAt < 0 ? -1.0 : rebuildClock + tickDelta * rebuildRate();
	}

	public int rebuildRate() {
		return rebuildHurried ? 6 : 1;
	}

	/** The shooter's own camera shots and black screen play only for them. */
	public boolean cinematic() {
		return mine && !ended;
	}
}
