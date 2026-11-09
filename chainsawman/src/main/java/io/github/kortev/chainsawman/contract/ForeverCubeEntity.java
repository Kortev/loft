package io.github.kortev.chainsawman.contract;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * A cube of frozen time ({@link Forever}): nine blocks across (thirteen once the bond is deeper) round where it was called. It does nothing itself; the clients use it to
 * drain the world inside it to grey, hold the particles still and show its faint shimmering edge from outside.
 */
public class ForeverCubeEntity extends Entity {
	private static final TrackedData<Integer> OWNER = DataTracker.registerData(ForeverCubeEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> START = DataTracker.registerData(ForeverCubeEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> LASTS = DataTracker.registerData(ForeverCubeEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** The caller's bond step: the cube is 9 blocks across at 1, 13 from 2. */
	private static final TrackedData<Byte> BOND = DataTracker.registerData(ForeverCubeEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final TrackedData<Byte> PHASE = DataTracker.registerData(ForeverCubeEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final TrackedData<Integer> PHASE_AT = DataTracker.registerData(ForeverCubeEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private int alive;

	public ForeverCubeEntity(EntityType<? extends ForeverCubeEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.setNoGravity(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(OWNER, -1);
		builder.add(START, 0);
		builder.add(LASTS, 60);
		builder.add(BOND, (byte) 1);
		builder.add(PHASE, (byte) ForeverHubEntity.HELD);
		builder.add(PHASE_AT, 0);
	}

	void begin(int owner, int start, int lasts, int bond) {
		dataTracker.set(BOND, (byte) bond);
		dataTracker.set(OWNER, owner);
		dataTracker.set(START, start);
		dataTracker.set(LASTS, lasts);
	}

	void phase(int phase, int at) {
		dataTracker.set(PHASE, (byte) phase);
		dataTracker.set(PHASE_AT, at);
	}

	public int ownerId() {
		return dataTracker.get(OWNER);
	}

	public int startedAt() {
		return dataTracker.get(START);
	}

	public int lasts() {
		return dataTracker.get(LASTS);
	}

	public int bond() {
		return dataTracker.get(BOND);
	}

	/** Half the side of the cube. */
	public double half() {
		return Forever.cubeHalf(bond());
	}

	public int phase() {
		return dataTracker.get(PHASE);
	}

	public int phaseAt() {
		return dataTracker.get(PHASE_AT);
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient && ++alive > 10 && !Forever.holds(this)) {
			discard();
		}
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 160.0 * 160.0;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
