package io.github.kortev.chainsawman.contract;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * The Eternity Devil's hold on one locked target ({@link Forever}): it sits where the target froze and carries what the clients
 * need to draw the devil (the flesh, eyes, mouths and cracks round it, the stored hits shown as marks): who is locked, when it
 * began, which phase it is in, the stored hits' heights. It does nothing itself; the server owns the lock.
 */
public class ForeverHubEntity extends Entity {
	/** Phases: held, the moment ending (eyes shut, mouths bite, the copies snap in), the flesh sinking back. */
	public static final int HELD = 0;
	public static final int ENDING = 1;
	public static final int SINKING = 2;

	private static final TrackedData<Integer> TARGET = DataTracker.registerData(ForeverHubEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> START = DataTracker.registerData(ForeverHubEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> LASTS = DataTracker.registerData(ForeverHubEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Byte> PHASE = DataTracker.registerData(ForeverHubEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final TrackedData<Integer> PHASE_AT = DataTracker.registerData(ForeverHubEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> SEED = DataTracker.registerData(ForeverHubEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> OWNER = DataTracker.registerData(ForeverHubEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** One character for each stored hit: 32 plus its height as a share of the target's height (0 to 90). */
	private static final TrackedData<String> MARKS = DataTracker.registerData(ForeverHubEntity.class, TrackedDataHandlerRegistry.STRING);

	public ForeverHubEntity(EntityType<? extends ForeverHubEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.setNoGravity(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(TARGET, -1);
		builder.add(START, 0);
		builder.add(LASTS, 60);
		builder.add(PHASE, (byte) HELD);
		builder.add(PHASE_AT, 0);
		builder.add(SEED, 0);
		builder.add(OWNER, -1);
		builder.add(MARKS, "");
	}

	void begin(int target, int owner, int start, int lasts, int seed) {
		dataTracker.set(OWNER, owner);
		dataTracker.set(TARGET, target);
		dataTracker.set(START, start);
		dataTracker.set(LASTS, lasts);
		dataTracker.set(SEED, seed);
	}

	void phase(int phase, int at) {
		dataTracker.set(PHASE, (byte) phase);
		dataTracker.set(PHASE_AT, at);
	}

	void mark(float share) {
		String marks = dataTracker.get(MARKS);
		if (marks.length() >= 48) {
			marks = marks.substring(1);
		}
		dataTracker.set(MARKS, marks + (char) (32 + Math.round(Math.max(0.0F, Math.min(1.0F, share)) * 90.0F)));
	}

	public int targetId() {
		return dataTracker.get(TARGET);
	}

	/** The entity id of whoever called it. */
	public int ownerId() {
		return dataTracker.get(OWNER);
	}

	public int startedAt() {
		return dataTracker.get(START);
	}

	public int lasts() {
		return dataTracker.get(LASTS);
	}

	public int phase() {
		return dataTracker.get(PHASE);
	}

	public int phaseAt() {
		return dataTracker.get(PHASE_AT);
	}

	public int seed() {
		return dataTracker.get(SEED);
	}

	/** The heights (shares of the target's height) of the stored hits, oldest first. */
	public float[] marks() {
		String marks = dataTracker.get(MARKS);
		float[] out = new float[marks.length()];
		for (int i = 0; i < out.length; i++) {
			out[i] = (marks.charAt(i) - 32) / 90.0F;
		}
		return out;
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
		return distance < 128.0 * 128.0;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
