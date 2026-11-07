package io.github.kortev.chainsawman.contract;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * One moment of a locked target ({@link Forever}): a body of the target's size, standing where it was or would have been, that
 * can be hit. Every copy is the target: a hit on one is not applied to it but stored on the target ({@link Forever#storeOnCopy}).
 * Clients draw it as the target's own model in its own pose for that moment (a different stride for each), drained and fading.
 */
public class ForeverCopyEntity extends Entity {
	private static final TrackedData<Integer> HUB = DataTracker.registerData(ForeverCopyEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> TARGET = DataTracker.registerData(ForeverCopyEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Which moment: -4 to -1 behind the target (the past), 1 to 4 ahead (the future). */
	private static final TrackedData<Byte> SLOT = DataTracker.registerData(ForeverCopyEntity.class, TrackedDataHandlerRegistry.BYTE);
	/** How far into a stride it is caught and how hard it is striding (the pose of its moment). */
	private static final TrackedData<Float> STRIDE = DataTracker.registerData(ForeverCopyEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> STRIDE_SPEED = DataTracker.registerData(ForeverCopyEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> WIDTH = DataTracker.registerData(ForeverCopyEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> HEIGHT = DataTracker.registerData(ForeverCopyEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** The world time the line began to snap into the target (-1 before). */
	private static final TrackedData<Integer> SNAP_AT = DataTracker.registerData(ForeverCopyEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public ForeverCopyEntity(EntityType<? extends ForeverCopyEntity> type, World world) {
		super(type, world);
		this.setNoGravity(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(HUB, -1);
		builder.add(TARGET, -1);
		builder.add(SLOT, (byte) 1);
		builder.add(STRIDE, 0.0F);
		builder.add(STRIDE_SPEED, 0.0F);
		builder.add(WIDTH, 0.6F);
		builder.add(HEIGHT, 1.8F);
		builder.add(SNAP_AT, -1);
	}

	void setup(int hub, int target, int slot, float stride, float strideSpeed, float width, float height) {
		dataTracker.set(HUB, hub);
		dataTracker.set(TARGET, target);
		dataTracker.set(SLOT, (byte) slot);
		dataTracker.set(STRIDE, stride);
		dataTracker.set(STRIDE_SPEED, strideSpeed);
		dataTracker.set(WIDTH, width);
		dataTracker.set(HEIGHT, height);
		calculateDimensions();
	}

	void snap(int time) {
		dataTracker.set(SNAP_AT, time);
	}

	public int hubId() {
		return dataTracker.get(HUB);
	}

	public int targetId() {
		return dataTracker.get(TARGET);
	}

	public int slot() {
		return dataTracker.get(SLOT);
	}

	public float stride() {
		return dataTracker.get(STRIDE);
	}

	public float strideSpeed() {
		return dataTracker.get(STRIDE_SPEED);
	}

	public int snapAt() {
		return dataTracker.get(SNAP_AT);
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (WIDTH.equals(data) || HEIGHT.equals(data)) {
			calculateDimensions();
		}
	}

	@Override
	public EntityDimensions getDimensions(EntityPose pose) {
		if (dataTracker == null) {
			return super.getDimensions(pose);
		}
		return EntityDimensions.fixed(dataTracker.get(WIDTH), dataTracker.get(HEIGHT));
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (getWorld().isClient) {
			return false;
		}
		if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
			discard();
			return true;
		}
		return Forever.storeOnCopy(this, source, amount);
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canUsePortals(boolean allowVehicles) {
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
