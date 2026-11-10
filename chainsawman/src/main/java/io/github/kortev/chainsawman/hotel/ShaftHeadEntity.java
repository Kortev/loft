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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A head that lunges at a player in the Eternity Devil's arena ({@link Shaft}): it rises out of the floor {@link #FROM} blocks from them
 * ({@link #UP} ticks), rears back with its eyes on them and its line lit on the floor ({@link #REAR} ticks: the way it will go is fixed as it
 * starts to rear), then lunges straight {@link #REACH} blocks ({@link #LUNGE} ticks, {@link #DAMAGE}). Step off the line and it misses: it
 * hangs out {@link #HANG} ticks, stunned, worth half as much again to cut, and then draws back. Cut down ({@link #HEALTH}), it drops; another
 * grows. Its box is the head, wherever it is. Drawn by {@code ShaftHeadRenderer}.
 */
public class ShaftHeadEntity extends Entity {
	public static final float HEALTH = 16.0F;
	public static final float WIDTH = 1.4F;
	public static final float HEIGHT = 1.4F;
	/** How far from the player it rises (blocks), and how far it lunges. */
	public static final double FROM = 5.0;
	public static final double REACH = 6.5;
	/** The head's middle's height over the floor. */
	public static final double HEAD_Y = 1.25;
	/** Ticks: rising, rearing (the tell's second half), lunging, hanging after a miss, drawing back. */
	public static final int UP = 10;
	public static final int REAR = 14;
	public static final int LUNGE = 4;
	public static final int HANG = 25;
	public static final int BACK = 8;
	/** When it strikes: the tell is all of this. */
	public static final int STRIKE = UP + REAR;
	public static final float DAMAGE = 5.0F;
	/** How near its path a player must be to be bitten (from the middle of the head to the middle of the player, across). */
	public static final double BITE_RADIUS = 1.05;

	private static final TrackedData<Integer> START = DataTracker.registerData(ShaftHeadEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Float> DX = DataTracker.registerData(ShaftHeadEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> DZ = DataTracker.registerData(ShaftHeadEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Boolean> LOCKED = DataTracker.registerData(ShaftHeadEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Integer> OUTCOME = DataTracker.registerData(ShaftHeadEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> OUT_AT = DataTracker.registerData(ShaftHeadEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public static final int PENDING = 0;
	public static final int HIT = 1;
	public static final int MISSED = 2;
	public static final int CUT = 3;

	public BlockPos origin = BlockPos.ORIGIN;
	public int rot;
	public UUID target;
	private float health = HEALTH;

	public ShaftHeadEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
		this.setNoGravity(true);
	}

	/** Comes up at {@code at} (world, on the floor) in the hotel at {@code origin} turned {@code rot}, aimed at {@code target}. */
	public void start(BlockPos origin, int rot, Vec3d at, UUID target) {
		this.origin = origin;
		this.rot = rot;
		this.target = target;
		this.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
		this.dataTracker.set(START, (int) this.getWorld().getTime());
		this.setBoundingBox(this.calculateBoundingBox());
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(START, 0);
		builder.add(DX, 1.0F);
		builder.add(DZ, 0.0F);
		builder.add(LOCKED, false);
		builder.add(OUTCOME, PENDING);
		builder.add(OUT_AT, 0);
	}

	/** The world tick it began to come up. */
	public int start() {
		return this.dataTracker.get(START);
	}

	/** The way it will lunge (fixed as it begins to rear), and whether it is fixed yet. */
	public Vec3d dir() {
		return new Vec3d(this.dataTracker.get(DX), 0.0, this.dataTracker.get(DZ));
	}

	public boolean locked() {
		return this.dataTracker.get(LOCKED);
	}

	public int outcome() {
		return this.dataTracker.get(OUTCOME);
	}

	public int outAt() {
		return this.dataTracker.get(OUT_AT);
	}

	/** Struck back down (the old name). */
	public boolean struck() {
		return outcome() == CUT;
	}

	/** Missed, and hanging out stunned now. */
	public boolean stunned() {
		if (outcome() != MISSED) {
			return false;
		}
		long since = this.getWorld().getTime() - outAt();
		return since >= 0 && since < HANG;
	}

	/** Still a threat: it has not yet struck. */
	public boolean threatening() {
		return outcome() == PENDING;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/**
	 * How far out along its line the head is ({@code out}, blocks), how high ({@code up}, 0 sunk to 1 risen) at {@code t} ticks from its start:
	 * {x out, y rise}.
	 */
	public float[] pose(float t) {
		int out = outcome();
		float since = out == PENDING ? 0.0F : t - (outAt() - start());
		float rise = ease(t / UP);
		float along;
		if (out == CUT) {
			// Cut: it drops where it was and sinks.
			float at = poseBefore(outAt() - start());
			return new float[] {at, rise * (1.0F - ease(since / 8.0F))};
		}
		along = poseBefore(t);
		if (out == HIT) {
			along = (float) REACH - (float) (REACH + 0.8) * ease((since - 6.0F) / BACK);
			if (since > 6.0F + BACK) {
				return new float[] {-0.8F, 1.0F - ease((since - 6.0F - BACK) / 6.0F)};
			}
		} else if (out == MISSED) {
			along = since < HANG ? (float) REACH : (float) REACH - (float) (REACH + 0.8) * ease((since - HANG) / BACK);
			if (since > HANG + BACK) {
				return new float[] {-0.8F, 1.0F - ease((since - HANG - BACK) / 6.0F)};
			}
		}
		return new float[] {along, rise};
	}

	/** Out along its line before it struck: still, rearing back, lunging. */
	private static float poseBefore(float t) {
		if (t < UP) {
			return 0.0F;
		}
		if (t < STRIKE) {
			return -0.8F * ease((t - UP) / 6.0F);
		}
		return -0.8F + (float) (REACH + 0.8) * ease((t - STRIKE) / LUNGE);
	}

	/** Where the head's middle is (world) at {@code t}. */
	public Vec3d headAt(float t) {
		float[] p = pose(t);
		return this.getPos().add(dir().multiply(p[0])).add(0.0, -1.0 + (HEAD_Y + 1.0) * p[1], 0.0);
	}

	public float age(float delta) {
		return (float) (this.getWorld().getTime() - start()) + delta;
	}

	@Override
	protected Box calculateBoundingBox() {
		if (this.dataTracker == null || this.getWorld() == null) {
			return super.calculateBoundingBox();
		}
		Vec3d h = headAt(age(0.0F));
		return new Box(h.x - WIDTH / 2.0, h.y - HEIGHT / 2.0, h.z - WIDTH / 2.0, h.x + WIDTH / 2.0, h.y + HEIGHT / 2.0, h.z + WIDTH / 2.0);
	}

	@Override
	public boolean canHit() {
		return outcome() != CUT && age(0.0F) > 4.0F;
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
		if (!(this.getWorld() instanceof ServerWorld world) || outcome() == CUT) {
			return false;
		}
		if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
			this.discard();
			return true;
		}
		Vec3d at = headAt(age(0.0F));
		Shaft.partCut(world, origin, at, source, amount, stunned(), EternityDevilEntity.P_HEAD);
		health -= amount;
		world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 3, 0.3, 0.3, 0.3, 0.1);
		if (health <= 0.0F) {
			this.dataTracker.set(OUTCOME, CUT);
			this.dataTracker.set(OUT_AT, (int) world.getTime());
			world.playSound(null, at.x, at.y, at.z, ChainsawSounds.DEVIL_HURT, SoundCategory.HOSTILE, 1.6F, 1.3F);
		}
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		this.setBoundingBox(this.calculateBoundingBox());
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		long t = world.getTime() - start();
		int out = outcome();
		if (out == PENDING && t >= UP - 1 && !locked()) {
			// It fixes on where they are now, and rears: the line on the floor shows where it will go.
			ServerPlayerEntity p = target == null ? null : world.getServer().getPlayerManager().getPlayer(target);
			Vec3d to = p == null ? new Vec3d(1.0, 0.0, 0.0) : p.getPos().subtract(this.getPos()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSquared() < 0.01) {
				to = new Vec3d(1.0, 0.0, 0.0);
			}
			to = to.normalize();
			this.dataTracker.set(DX, (float) to.x);
			this.dataTracker.set(DZ, (float) to.z);
			this.dataTracker.set(LOCKED, true);
			world.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), ChainsawSounds.DEVIL_HURT, SoundCategory.HOSTILE, 1.2F, 0.6F);
		}
		if (out == PENDING && t >= STRIKE && t <= STRIKE + LUNGE) {
			if (t == STRIKE) {
				world.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), ChainsawSounds.DEVIL_BITE, SoundCategory.HOSTILE, 1.8F, 0.9F + this.random.nextFloat() * 0.2F);
			}
			// Along its path so far: whoever is near enough to the line is bitten, once.
			Vec3d d = dir();
			double reached = poseBefore(t);
			for (ServerPlayerEntity p : world.getPlayers()) {
				if (!p.isAlive() || p.isSpectator() || p.isCreative()) {
					continue;
				}
				Vec3d rel = p.getPos().subtract(this.getPos());
				double along = rel.dotProduct(d);
				double across = Math.abs(rel.x * d.z - rel.z * d.x);
				if (along >= 0.0 && along <= reached + 0.6 && across <= BITE_RADIUS + 0.3 && rel.y > -0.6 && rel.y < 2.0) {
					p.damage(this.getDamageSources().generic(), DAMAGE);
					Vec3d push = d.multiply(0.7);
					p.setVelocity(push.x, 0.35, push.z);
					p.velocityModified = true;
					this.dataTracker.set(OUTCOME, HIT);
					this.dataTracker.set(OUT_AT, (int) world.getTime());
					break;
				}
			}
			if (outcome() == PENDING && t == STRIKE + LUNGE) {
				this.dataTracker.set(OUTCOME, MISSED);
				this.dataTracker.set(OUT_AT, (int) world.getTime());
				world.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), ChainsawSounds.DEVIL_SLAM, SoundCategory.HOSTILE, 1.4F, 1.4F);
			}
		}
		out = outcome();
		long since = world.getTime() - outAt();
		if (out == CUT && since > 10 || out == HIT && since > 6 + BACK + 6 || out == MISSED && since > HANG + BACK + 6 || t > 200) {
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
