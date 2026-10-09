package io.github.kortev.chitty.airship;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * One of the airship's bombs, falling: a black iron teardrop that drops nose down from the gondola, keeps a little of
 * her way, and goes off where it strikes the ground, the water or someone, smaller than TNT but enough to break the
 * ground. It does not go off among her own crew, nor against her own hull as it leaves.
 */
public class AirshipBombEntity extends Entity {
	/** The blast, as a TNT block's is 4. */
	public static final float POWER = 2.5F;
	private static final TrackedData<Integer> SHIP = DataTracker.registerData(AirshipBombEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public AirshipBombEntity(EntityType<? extends AirshipBombEntity> type, World world) {
		super(type, world);
	}

	AirshipBombEntity(World world, AirshipEntity ship, Vec3d at, Vec3d velocity) {
		this(Airship.BOMB_ENTITY, world);
		dataTracker.set(SHIP, ship.getId());
		refreshPositionAndAngles(at.x, at.y, at.z, ship.getYaw(), 0.0F);
		setVelocity(velocity);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(SHIP, -1);
	}

	@Override
	public void tick() {
		super.tick();
		Vec3d v = getVelocity().multiply(0.99).add(0.0, -0.04, 0.0);
		setVelocity(v);
		move(MovementType.SELF, v);
		if (getWorld().isClient) {
			if (age % 2 == 0) {
				getWorld().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.5, getZ(), 0.0, 0.02, 0.0);
			}
			return;
		}
		boolean struck = horizontalCollision || verticalCollision || isOnGround() || isTouchingWater() || isInLava() || age > 600;
		if (!struck && age > 2) {
			Entity ship = getWorld().getEntityById(dataTracker.get(SHIP));
			List<Entity> hit = getWorld().getOtherEntities(this, getBoundingBox().expand(0.2),
					e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && (ship == null || e.getRootVehicle() != ship));
			struck = !hit.isEmpty();
		}
		if (struck) {
			getWorld().createExplosion(this, getX(), getY(), getZ(), POWER, World.ExplosionSourceType.TNT);
			discard();
		}
	}

	@Override
	public boolean canHit() {
		return false;
	}

	/** It falls through anyone's way; what it strikes is looked for each tick instead. */
	@Override
	public boolean collidesWith(Entity other) {
		return false;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
