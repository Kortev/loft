package io.github.kortev.chainsawman.hotel;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * Flesh growing where the loop floor's walls meet its floor and ceiling as the laps go on (the Eternity Devil's sign that something is
 * wrong): a lump clinging to the corner, an eye on some. Only drawn (the home makes and takes them away: {@link Hotel}); it faces the
 * way it was put (its yaw is toward the middle of the corridor).
 */
public class FleshGrowthEntity extends Entity {
	/** A lump on the floor where it meets a wall, one hanging in the ceiling's corner, one bulging out of the middle of a wall, one hanging from the ceiling. */
	public static final int FLOOR = 0;
	public static final int CEILING = 1;
	public static final int WALL = 2;
	public static final int HANGING = 3;
	/** An eye in the wallpaper (open or shut: {@link #isOpen}), and a patch of wall that breathes: the signs of the devil hiding in the walls ({@link Hunt}). */
	public static final int EYE = 4;
	public static final int BREATH = 5;

	private static final TrackedData<Integer> KIND = DataTracker.registerData(FleshGrowthEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> SEED = DataTracker.registerData(FleshGrowthEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> BORN = DataTracker.registerData(FleshGrowthEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> OPEN = DataTracker.registerData(FleshGrowthEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	public FleshGrowthEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	public void set(int kind, int seed) {
		this.dataTracker.set(KIND, kind);
		this.dataTracker.set(SEED, seed);
		this.dataTracker.set(BORN, (int) this.getWorld().getTime());
	}

	public int kind() {
		return this.dataTracker.get(KIND);
	}

	public int seed() {
		return this.dataTracker.get(SEED);
	}

	/** The world tick it began to grow. */
	public int born() {
		return this.dataTracker.get(BORN);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(KIND, FLOOR);
		builder.add(SEED, 0);
		builder.add(BORN, 0);
		builder.add(OPEN, false);
	}

	/** Whether an eye is open. */
	public boolean isOpen() {
		return this.dataTracker.get(OPEN);
	}

	public void setOpen(boolean open) {
		this.dataTracker.set(OPEN, open);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
