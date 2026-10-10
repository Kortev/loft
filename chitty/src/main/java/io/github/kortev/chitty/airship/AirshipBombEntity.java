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
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.jetbrains.annotations.Nullable;

/**
 * One of the airship's bombs, falling: a black iron teardrop that drops nose down from the gondola, keeps a little of
 * her way, and goes off where it strikes the ground, the water or someone, smaller than TNT but enough to break the
 * ground. It does not go off among her own crew, nor against her own hull as it leaves, nor on what hangs from her
 * grapple or climbs her ladder; nor does its blast hurt her, her hull or her crew (dropped low, it would break her),
 * though anything else near enough it does. Whoever dropped it is to blame for what it does.
 */
public class AirshipBombEntity extends Entity {
	/** The blast, as a TNT block's is 4. */
	public static final float POWER = 2.5F;
	private static final TrackedData<Integer> SHIP = DataTracker.registerData(AirshipBombEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** On the server: whoever dropped it. */
	@Nullable
	private Entity bomber;

	public AirshipBombEntity(EntityType<? extends AirshipBombEntity> type, World world) {
		super(type, world);
	}

	AirshipBombEntity(World world, AirshipEntity ship, @Nullable Entity bomber, Vec3d at, Vec3d velocity) {
		this(Airship.BOMB_ENTITY, world);
		this.bomber = bomber;
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
		AirshipEntity ship = getWorld().getEntityById(dataTracker.get(SHIP)) instanceof AirshipEntity s ? s : null;
		boolean struck = horizontalCollision || verticalCollision || isOnGround() || isTouchingWater() || isInLava() || age > 600;
		if (!struck && age > 2) {
			List<Entity> hit = getWorld().getOtherEntities(this, getBoundingBox().expand(0.2),
					e -> e instanceof LivingEntity living && e.isAlive() && !e.isSpectator()
							&& (ship == null || !ship.isHers(e) && !ship.carries(living)));
			struck = !hit.isEmpty();
		}
		if (struck) {
			ExplosionBehavior sparing = new ExplosionBehavior() {
				@Override
				public boolean shouldDamage(Explosion explosion, Entity entity) {
					return ship == null || !ship.isHers(entity);
				}

				@Override
				public float getKnockbackModifier(Entity entity) {
					return ship != null && ship.isHers(entity) ? 0.0F : super.getKnockbackModifier(entity);
				}
			};
			getWorld().createExplosion(this, getDamageSources().explosion(this, bomber), sparing, getX(), getY(), getZ(), POWER, false,
					World.ExplosionSourceType.TNT);
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
