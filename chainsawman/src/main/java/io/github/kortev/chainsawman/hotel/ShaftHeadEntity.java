package io.github.kortev.chainsawman.hotel;

import io.github.kortev.chainsawman.ChainsawSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A head that lunges out of the mass at the Eternity Devil's feet-of-the-shaft and bites whoever is standing there: up in a third of a
 * second, a snap, down again. Small and quick (no big windup to read: it is the mass's constant nibbling), so it hurts a little and is
 * hard to stay clear of; a hit sends it back down at once ({@link #HEALTH}). Drawn by {@code ShaftHeadRenderer} (a placeholder: a lump on
 * a neck with a mouth).
 */
public class ShaftHeadEntity extends Entity {
	public static final float HEALTH = 8.0F;
	public static final float WIDTH = 1.6F;
	public static final float HEIGHT = 2.0F;
	/** Ticks it takes to come up, when it bites, and how long it lasts. */
	public static final int RISE = 6;
	public static final int BITE_AT = 8;
	public static final int LIFE = 26;
	public static final float BITE_DAMAGE = 5.0F;
	public static final double BITE_RADIUS = 1.9;

	private static final TrackedData<Integer> START = DataTracker.registerData(ShaftHeadEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> CUT = DataTracker.registerData(ShaftHeadEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	public BlockPos origin = BlockPos.ORIGIN;
	public int rot;
	private float health = HEALTH;
	private boolean bit;

	public ShaftHeadEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
		this.setNoGravity(true);
	}

	/** Comes up at {@code at} (world) in the hotel at {@code origin} turned {@code rot}. */
	public void start(BlockPos origin, int rot, Vec3d at) {
		this.origin = origin;
		this.rot = rot;
		this.refreshPositionAndAngles(at.x, at.y, at.z, this.random.nextFloat() * 360.0F, 0.0F);
		this.dataTracker.set(START, (int) this.getWorld().getTime());
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(START, 0);
		builder.add(CUT, false);
	}

	/** The world tick it began to come up. */
	public int start() {
		return this.dataTracker.get(START);
	}

	/** Struck back down. */
	public boolean struck() {
		return this.dataTracker.get(CUT);
	}

	@Override
	public boolean canHit() {
		return !struck();
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
	public boolean isAttackable() {
		return true;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (!(this.getWorld() instanceof ServerWorld world) || struck()) {
			return false;
		}
		if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
			this.discard();
			return true;
		}
		Hotel.fought(Hotel.at(world, origin), source);
		health -= amount;
		world.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), ChainsawSounds.DEVIL_HURT, SoundCategory.HOSTILE, 0.8F, 1.4F);
		world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, this.getX(), this.getY() + 1.2, this.getZ(), 3, 0.3, 0.3, 0.3, 0.1);
		if (health <= 0.0F) {
			this.dataTracker.set(CUT, true);
		}
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		int t = (int) world.getTime() - start();
		if (t >= LIFE || struck() && t > BITE_AT + 4) {
			this.discard();
			return;
		}
		if (!bit && !struck() && t >= BITE_AT) {
			bit = true;
			world.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), ChainsawSounds.DEVIL_BITE, SoundCategory.HOSTILE, 1.1F, 1.1F + this.random.nextFloat() * 0.2F);
			Vec3d pit = HotelBuilder.world(origin, rot, Shaft.PIT_X + 0.5, this.getY(), Shaft.PIT_Z + 0.5);
			for (ServerPlayerEntity p : world.getPlayers()) {
				if (!p.isAlive() || p.isSpectator() || p.isCreative()) {
					continue;
				}
				double dx = p.getX() - this.getX();
				double dz = p.getZ() - this.getZ();
				if (dx * dx + dz * dz <= BITE_RADIUS * BITE_RADIUS && Math.abs(p.getY() - this.getY()) <= 2.2) {
					p.damage(this.getDamageSources().generic(), BITE_DAMAGE);
					// The bite throws them a little toward the mouth.
					Vec3d to = pit.subtract(p.getPos());
					Vec3d flat = new Vec3d(to.x, 0.0, to.z);
					if (flat.lengthSquared() > 0.01) {
						flat = flat.normalize().multiply(0.45);
					}
					p.addVelocity(flat.x, 0.35, flat.z);
					p.velocityModified = true;
				}
			}
		}
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
