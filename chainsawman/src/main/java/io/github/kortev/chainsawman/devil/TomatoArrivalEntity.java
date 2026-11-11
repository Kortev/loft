package io.github.kortev.chainsawman.devil;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A Tomato Devil coming up out of the ground ({@link TomatoArrivals}): nothing for two seconds (only the hush the spawner played), then its
 * eyes open low in the dark and it breathes ({@link #EYES}); at {@link #BULGE} the ground bulges, cracking and throwing dirt, as the swelling
 * tomato pushes up; at {@link #BURST} it bursts and the devil stands there, every eye on the nearest player. Nothing about it can be hit and
 * it hurts nobody; no block is changed. It is saved with the world (and goes on where it was).
 */
public class TomatoArrivalEntity extends Entity {
	/** The eyes open (2 s), the ground bulges (4 s), it bursts (7 s). */
	public static final int EYES = 40;
	public static final int BULGE = 80;
	public static final int BURST = 140;

	/** The world's time it began (-1 until its first tick). */
	private static final TrackedData<Long> START = DataTracker.registerData(TomatoArrivalEntity.class, TrackedDataHandlerRegistry.LONG);

	public TomatoArrivalEntity(EntityType<? extends TomatoArrivalEntity> type, World world) {
		super(type, world);
		this.noClip = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(START, -1L);
	}

	/** Ticks since it began (with the frame's part of a tick on the client). */
	public float elapsed(float delta) {
		long s = dataTracker.get(START);
		return s < 0 ? 0.0F : (float) (getWorld().getTime() - s) + delta;
	}

	/** For the tests and the film: put it {@code ticks} into its arrival. */
	public void skipTo(int ticks) {
		dataTracker.set(START, getWorld().getTime() - ticks);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		long now = world.getTime();
		if (dataTracker.get(START) < 0) {
			dataTracker.set(START, now);
		}
		int t = (int) (now - dataTracker.get(START));
		BlockState ground = world.getBlockState(getBlockPos().down());
		if (ground.isAir()) {
			ground = Blocks.DIRT.getDefaultState();
		}
		if (t == EYES) {
			// Eyes open in the dark, and a wet breath from there: someone facing away turns round.
			world.playSound(null, getX(), getY() + 0.3, getZ(), Devils.Sounds.BREATH, SoundCategory.HOSTILE, 2.2F, 0.85F);
			world.playSound(null, getX(), getY() + 0.3, getZ(), Devils.Sounds.CLICK, SoundCategory.HOSTILE, 1.0F, 0.7F);
		}
		if (t >= BULGE && t < BURST) {
			float k = (t - BULGE) / (float) (BURST - BULGE);
			if (t == BULGE) {
				world.playSound(null, getX(), getY(), getZ(), Devils.Sounds.THUD, SoundCategory.HOSTILE, 1.0F, 0.6F);
			}
			if ((t - BULGE) % 20 == 0) {
				world.playSound(null, getX(), getY(), getZ(), Devils.Sounds.SWELL, SoundCategory.HOSTILE, 1.6F, 0.7F + 0.6F * k);
			}
			// The ground cracking round it, more and wider as it swells.
			if (t % 2 == 0) {
				double r = 0.6 + 1.2 * k;
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), getX(), getY() + 0.1, getZ(), 4 + (int) (10 * k), r, 0.05, r, 0.15);
			}
			// Clods of dirt thrown up.
			if (t % 7 == 0) {
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DIRT.getDefaultState()), getX(), getY() + 0.3, getZ(), 10 + (int) (14 * k),
						0.5 + 0.6 * k, 0.2, 0.5 + 0.6 * k, 0.35);
				world.spawnParticles(ParticleTypes.POOF, getX(), getY() + 0.2, getZ(), 2, 0.6, 0.1, 0.6, 0.01);
			}
		}
		if (t >= BURST) {
			hatch(world, ground);
		}
	}

	/** It bursts: the devil stands where it was, every eye on the nearest player, and the fight is on. */
	@Nullable
	public TomatoDevilEntity hatch(ServerWorld world, BlockState ground) {
		if (isRemoved()) {
			return null;
		}
		PlayerEntity near = world.getClosestPlayer(getX(), getY(), getZ(), 48.0, EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR);
		float yaw = near == null ? getYaw() : (float) Math.toDegrees(Math.atan2(-(near.getX() - getX()), near.getZ() - getZ()));
		TomatoDevilEntity devil = Devils.TOMATO_DEVIL.create(world);
		if (devil != null) {
			devil.refreshPositionAndAngles(getX(), getY(), getZ(), yaw, 0.0F);
			devil.setBodyYaw(yaw);
			devil.setHeadYaw(yaw);
			devil.initialize(world, world.getLocalDifficulty(getBlockPos()), SpawnReason.EVENT, null);
			devil.aimAt(yaw);
			world.spawnEntity(devil);
			if (near != null) {
				devil.notice(true);
			}
		}
		world.playSound(null, getX(), getY() + 1.0, getZ(), Devils.Sounds.BURST, SoundCategory.HOSTILE, 2.0F, 0.85F);
		world.playSound(null, getX(), getY() + 2.0, getZ(), Devils.Sounds.CLICK, SoundCategory.HOSTILE, 1.2F, 0.8F);
		world.spawnParticles(TomatoDevilEntity.JUICE, getX(), getY() + 1.4, getZ(), 50, 1.0, 0.8, 1.0, 0.3);
		world.spawnParticles(TomatoDevilEntity.PULP, getX(), getY() + 1.4, getZ(), 24, 0.9, 0.6, 0.9, 0.25);
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), getX(), getY() + 0.2, getZ(), 80, 1.6, 0.2, 1.6, 0.3);
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DIRT.getDefaultState()), getX(), getY() + 0.6, getZ(), 40, 1.2, 0.5, 1.2, 0.4);
		discard();
		return devil;
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
	public boolean damage(DamageSource source, float amount) {
		return false;
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
	public boolean shouldRender(double distance) {
		return distance < 96.0 * 96.0;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.contains("Elapsed")) {
			dataTracker.set(START, getWorld().getTime() - nbt.getInt("Elapsed"));
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("Elapsed", (int) elapsed(0.0F));
	}
}
