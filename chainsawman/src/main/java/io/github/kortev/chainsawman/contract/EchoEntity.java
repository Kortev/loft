package io.github.kortev.chainsawman.contract;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * One copy of a player in {@link Echo}: you, from some other moment in eternity. A real moving body (it falls, steps up a
 * block, jumps, slides along walls, and is never left inside a block), but not a mob: {@link Echo} tells it each tick where
 * its place in the ring has got to and whether to jump, and it walks there. It can be a mob's target and be hit, but a hit
 * only makes it flicker. The clients draw it as its owner (the owner's skin; faded and trailing for a past copy, bright and
 * outlined for a future one) from the tracked data below.
 */
public class EchoEntity extends LivingEntity {
	public static final int PAST = 0;
	public static final int FUTURE = 1;
	/** How long the burst's streak out of the owner takes, and the finale's streak back in (ticks). */
	public static final int STREAK_OUT = 4;
	public static final int STREAK_IN = 5;
	/** The speed a copy walks at most (blocks a tick: a sprint is 0.28). */
	private static final double WALK = 0.6;

	private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(EchoEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
	private static final TrackedData<Byte> KIND = DataTracker.registerData(EchoEntity.class, TrackedDataHandlerRegistry.BYTE);
	/** The world time it was born, where it streaked out from, and when it was last hit (-1000: never). */
	private static final TrackedData<Integer> BORN = DataTracker.registerData(EchoEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Vector3f> ORIGIN = DataTracker.registerData(EchoEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
	private static final TrackedData<Integer> HURT_AT = DataTracker.registerData(EchoEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** When (world time) it began its streak back into the owner, and from where; -1 while it has not. */
	private static final TrackedData<Integer> RETURN_AT = DataTracker.registerData(EchoEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Vector3f> RETURN_FROM = DataTracker.registerData(EchoEntity.class, TrackedDataHandlerRegistry.VECTOR3F);

	/** What {@link Echo} wants of it this tick. */
	private double wantX;
	private double wantZ;
	private double speed;
	private boolean jump;
	/** The last place it stood safely, to go back to if it ends up in a block, in lava or out of the world. */
	private Vec3d safe;

	public EchoEntity(EntityType<? extends EchoEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return LivingEntity.createLivingAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0).add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(OWNER, Optional.empty());
		builder.add(KIND, (byte) PAST);
		builder.add(BORN, 0);
		builder.add(ORIGIN, new Vector3f());
		builder.add(HURT_AT, -1000);
		builder.add(RETURN_AT, -1);
		builder.add(RETURN_FROM, new Vector3f());
	}

	/** Set up when it is made, on the server. */
	void born(UUID owner, int kind, Vec3d from, int time) {
		dataTracker.set(OWNER, Optional.of(owner));
		dataTracker.set(KIND, (byte) kind);
		dataTracker.set(BORN, time);
		dataTracker.set(ORIGIN, from.toVector3f());
		safe = getPos();
	}

	/** Starts its streak back into its owner. */
	void goBack(int time) {
		dataTracker.set(RETURN_FROM, getPos().toVector3f());
		dataTracker.set(RETURN_AT, time);
	}

	public UUID owner() {
		return dataTracker.get(OWNER).orElse(null);
	}

	public int kind() {
		return dataTracker.get(KIND);
	}

	public boolean future() {
		return kind() == FUTURE;
	}

	public int bornAt() {
		return dataTracker.get(BORN);
	}

	public Vec3d origin() {
		return new Vec3d(dataTracker.get(ORIGIN));
	}

	public int hurtAt() {
		return dataTracker.get(HURT_AT);
	}

	public int returnAt() {
		return dataTracker.get(RETURN_AT);
	}

	public Vec3d returnFrom() {
		return new Vec3d(dataTracker.get(RETURN_FROM));
	}

	/** Where it is to walk to, how fast at most, and whether to jump (from the next tick). */
	void drive(double x, double z, double maxSpeed, boolean jumpNow) {
		wantX = x;
		wantZ = z;
		speed = maxSpeed;
		jump = jump || jumpNow;
	}

	/** Face {@code at}, flat. */
	void face(Vec3d at) {
		double dx = at.x - getX();
		double dz = at.z - getZ();
		if (dx * dx + dz * dz < 1.0E-4) {
			return;
		}
		float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
		setYaw(yaw);
		setBodyYaw(yaw);
		setHeadYaw(yaw);
		setPitch(0.0F);
	}

	Vec3d safeSpot() {
		return safe;
	}

	/** Whether it stands somewhere it may: not in a block. */
	boolean clear() {
		return getWorld().isSpaceEmpty(this);
	}

	@Override
	public void tickMovement() {
		if (getWorld().isClient) {
			super.tickMovement();
			return;
		}
		Vec3d walk = Vec3d.ZERO;
		double dx = wantX - getX();
		double dz = wantZ - getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d > 0.12 && speed > 0.0) {
			double s = Math.min(Math.min(speed, WALK), d * 0.5 + 0.05);
			walk = new Vec3d(dx / d * s, 0.0, dz / d * s);
		}
		boolean wall = horizontalCollision && walk.lengthSquared() > 0.0;
		double vy = getVelocity().y;
		if (isOnGround()) {
			vy = jump || wall ? 0.42 : -0.08;
		} else {
			vy = (vy - 0.08) * 0.98;
		}
		jump = false;
		setVelocity(walk.x, vy, walk.z);
		move(MovementType.SELF, getVelocity());
		if (isOnGround()) {
			fallDistance = 0.0F;
		}
		// Never in a block, in lava or out of the world: back to where it last stood.
		if (safe != null && (!clear() || isInLava() || isOnFire() || getY() < getWorld().getBottomY() + 2)) {
			refreshPositionAndAngles(safe.x, safe.y, safe.z, getYaw(), 0.0F);
			setVelocity(Vec3d.ZERO);
			extinguish();
		} else if (isOnGround() && !isTouchingWater()) {
			safe = getPos();
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
			return super.damage(source, amount);
		}
		if (!getWorld().isClient) {
			dataTracker.set(HURT_AT, (int) getWorld().getTime());
		}
		return false;
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canHit() {
		return true;
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean canUsePortals(boolean allowVehicles) {
		return false;
	}

	@Override
	public Iterable<ItemStack> getArmorItems() {
		return List.of();
	}

	@Override
	public ItemStack getEquippedStack(EquipmentSlot slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public void equipStack(EquipmentSlot slot, ItemStack stack) {
	}

	@Override
	public Arm getMainArm() {
		return Arm.RIGHT;
	}
}
