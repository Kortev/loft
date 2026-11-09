package io.github.kortev.chainsawman.hotel;

import io.github.kortev.chainsawman.ChainsawSounds;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.world.World;

/**
 * The Eternity Devil's core: the figure-8 lump (an eye and a mouth to each loop) it pushes up out of its mouth when it can bear no more pain
 * and begs ({@link EternityDevilEntity#startBeg}). It cannot be hurt while the deal is on offer; when nobody takes the deal ({@link
 * EternityDevilEntity#giveUp}) it can be cut, and cutting it in two kills the devil ({@link EternityDevilEntity#cutCore}). Drawn by
 * {@code ShaftCoreRenderer} (a placeholder: two lumps, eyes and mouths).
 */
public class ShaftCoreEntity extends Entity {
	/** What it takes to cut it in two, once it can be cut (any weapon: the saws in three or four blows, a sword in about six). */
	public static final float HEALTH = 40.0F;
	public static final float WIDTH = 2.8F;
	public static final float HEIGHT = 1.5F;
	/** How long it takes to rise out of the mouth, and how long the two halves take to fall apart once it is cut (ticks). */
	public static final int RISE = 30;
	public static final int FALL = 30;

	private static final TrackedData<Boolean> CUTTABLE = DataTracker.registerData(ShaftCoreEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Integer> CUT_AT = DataTracker.registerData(ShaftCoreEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> BORN = DataTracker.registerData(ShaftCoreEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public UUID devil;
	private float health = HEALTH;
	private int cutTick = -1;

	public ShaftCoreEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
		this.setNoGravity(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(CUTTABLE, false);
		builder.add(CUT_AT, -1);
		builder.add(BORN, 0);
	}

	/** Whether the deal is off and it can be cut. */
	public boolean cuttable() {
		return this.dataTracker.get(CUTTABLE);
	}

	public void setCuttable(boolean on) {
		this.dataTracker.set(CUTTABLE, on);
	}

	/** The world tick it began to rise, and the tick it was cut (-1 while whole). */
	public int born() {
		return this.dataTracker.get(BORN);
	}

	public int cutAt() {
		return this.dataTracker.get(CUT_AT);
	}

	public void markBorn() {
		this.dataTracker.set(BORN, (int) this.getWorld().getTime());
	}

	@Override
	public boolean canHit() {
		return cutAt() < 0;
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
		if (!(this.getWorld() instanceof ServerWorld world) || cutAt() >= 0) {
			return false;
		}
		if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
			return false;
		}
		if (!cuttable()) {
			// (Begging: it is held out, not cut.)
			world.spawnParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.8, this.getZ(), 4, 0.5, 0.3, 0.5, 0.01);
			return false;
		}
		health -= amount;
		world.playSound(null, this.getX(), this.getY() + 0.8, this.getZ(), ChainsawSounds.DEVIL_HURT, SoundCategory.HOSTILE, 1.0F, 1.3F);
		world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, this.getX(), this.getY() + 0.8, this.getZ(), 3, 0.4, 0.3, 0.4, 0.1);
		if (health <= 0.0F) {
			cut(world, source.getAttacker());
		}
		return true;
	}

	/** Cut in two: the halves fall apart, and the devil dies with it. */
	private void cut(ServerWorld world, Entity by) {
		this.dataTracker.set(CUT_AT, (int) world.getTime());
		cutTick = this.age;
		world.playSound(null, this.getX(), this.getY() + 0.8, this.getZ(), ChainsawSounds.HOTEL_CUT, SoundCategory.HOSTILE, 3.0F, 0.7F);
		world.spawnParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.8, this.getZ(), 2, 0.5, 0.3, 0.5, 0.0);
		if (devil != null && world.getEntity(devil) instanceof EternityDevilEntity d) {
			d.cutCore(by);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld() instanceof ServerWorld && cutTick >= 0 && this.age - cutTick > FALL + 10) {
			this.discard();
		}
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
