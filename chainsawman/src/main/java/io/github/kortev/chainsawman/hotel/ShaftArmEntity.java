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
 * One of the Eternity Devil's arms ({@link Shaft}), in one of three ways:
 * <ul>
 * <li>{@link #RISE}: up the shaft from the mouth, to take anyone who caught a ledge down into the mouth.</li>
 * <li>{@link #SLAM}: in the arena, a lane {@link #LANE} wide swells along the floor from a wall with a lit seam for {@link #SLAM_TELL} ticks
 * (its back breaking the surface), then the arm slams down the lane ({@link #SLAM_DAMAGE}, a shove toward the middle) and lies there
 * {@link #LIE} ticks: if it missed, stunned and glowing where to cut, worth half as much again.</li>
 * <li>{@link #GRAB}: a hand rises open at a wall for {@link #GRAB_TELL} ticks, then sweeps low across the floor on that side within
 * {@link #REACH_GRAB}. Whoever it catches (feet on the floor: jumping it works) is lifted and crushed ({@link #CRUSH} a second) until the arm
 * is cut (by them or a friend). A miss leaves the hand lying open on the floor, stunned.</li>
 * </ul>
 * Its box is what can be hit: the column (rising), the arm along its lane (slam), the hand (grab). Drawn by {@code ShaftArmRenderer}.
 */
public class ShaftArmEntity extends Entity {
	public static final int RISE = 0;
	public static final int SLAM = 1;
	public static final int GRAB = 2;

	public static final float HEALTH = 14.0F;
	public static final double WIDTH = 1.0;
	/** Rising: blocks a tick the tip rises, is pulled down with a player, and sinks; how far across the hand catches. */
	public static final double RISE_SPEED = 0.85;
	public static final double PULL = 0.55;
	public static final double SINK = 0.8;
	public static final double REACH = 2.9;
	public static final float GRAB_DAMAGE = 4.0F;
	/** The slam: its lane's width, its tell, the slam itself, how long it lies after, how long it takes back; damage; what it takes to cut down. */
	public static final double LANE = 3.0;
	public static final int SLAM_TELL = 22;
	public static final int SLAM_FALL = 3;
	/** After a miss it lies stuck this long: the time to tear it off. */
	public static final int LIE = Shaft.STUCK;
	public static final int BACK = 10;
	public static final float SLAM_DAMAGE = 6.0F;
	public static final float SLAM_HEALTH = 30.0F;
	/** The grab: the hand's tell, its sweep (ticks, and the angle each side of straight out), its reach; the crush; what frees you. */
	public static final int GRAB_TELL = 22;
	public static final int SWEEP = 16;
	public static final float SWEEP_ANGLE = 80.0F;
	public static final double REACH_GRAB = 7.0;
	public static final double HAND = 1.35;
	public static final float CRUSH = 2.0F;
	public static final float GRAB_HEALTH = 22.0F;
	/** The longest a hand holds anyone (ticks): it crushes and throws them down (a limb that hit you pulls back), sooner if it is cut. */
	public static final int HOLD_MAX = 30;

	private static final TrackedData<Float> TIP = DataTracker.registerData(ShaftArmEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Boolean> HOLDING = DataTracker.registerData(ShaftArmEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Integer> MODE = DataTracker.registerData(ShaftArmEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> START = DataTracker.registerData(ShaftArmEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** The lane's direction (slam) or straight out from the wall (grab), as a world yaw-free vector's x and z; its length (slam). */
	private static final TrackedData<Float> DX = DataTracker.registerData(ShaftArmEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> DZ = DataTracker.registerData(ShaftArmEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> LEN = DataTracker.registerData(ShaftArmEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** Grab: which way it sweeps (+1 / -1), and the angle it stopped at (holding someone, or lying after a miss). */
	private static final TrackedData<Float> SWEEP_DIR = DataTracker.registerData(ShaftArmEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> STOP_ANGLE = DataTracker.registerData(ShaftArmEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** Grab: how far out along the arm it closed on whoever it caught (the whole arm sweeps the floor, not only its hand). */
	private static final TrackedData<Float> HOLD_R = DataTracker.registerData(ShaftArmEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** What came of it (0 not yet, 1 it hit or caught, 2 it missed, 3 cut down) and the tick it came of it. */
	private static final TrackedData<Integer> OUTCOME = DataTracker.registerData(ShaftArmEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> OUT_AT = DataTracker.registerData(ShaftArmEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public static final int PENDING = 0;
	public static final int HIT = 1;
	public static final int MISSED = 2;
	public static final int CUT = 3;

	public BlockPos origin = BlockPos.ORIGIN;
	public int rot;
	/** Who it was sent at. */
	public UUID target;
	private double floorY;
	private double maxTip;
	private int state;
	private UUID held;
	private float health = HEALTH;
	/** Cuts taken while it lies stuck (it is torn off at {@link Shaft#TEAR}), and on its grip while it holds someone. */
	private float tearing;
	private float grip;

	public ShaftArmEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
		this.setNoGravity(true);
	}

	/** Rises up the shaft from {@code at} (world) in the hotel at {@code origin} turned {@code rot}. */
	public void start(BlockPos origin, int rot, Vec3d at, double maxTipY) {
		this.origin = origin;
		this.rot = rot;
		this.floorY = at.y;
		this.maxTip = maxTipY;
		this.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
		this.dataTracker.set(MODE, RISE);
		this.dataTracker.set(TIP, (float) at.y + 0.5F);
		this.dataTracker.set(START, (int) this.getWorld().getTime());
		this.setBoundingBox(this.calculateBoundingBox());
	}

	/** A slam down the lane from {@code at} (world, at the wall, on the floor) along {@code dir} for {@code len} blocks, at {@code target}. */
	public void slam(BlockPos origin, int rot, Vec3d at, Vec3d dir, double len, UUID target) {
		this.origin = origin;
		this.rot = rot;
		this.target = target;
		this.floorY = at.y;
		this.health = SLAM_HEALTH;
		this.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
		this.dataTracker.set(MODE, SLAM);
		this.dataTracker.set(DX, (float) dir.x);
		this.dataTracker.set(DZ, (float) dir.z);
		this.dataTracker.set(LEN, (float) len);
		this.dataTracker.set(START, (int) this.getWorld().getTime());
		this.setBoundingBox(this.calculateBoundingBox());
	}

	/** A hand at {@code at} (world, the wall's foot), reaching out along {@code in}, sweeping first from the side opposite {@code toward}. */
	public void grab(BlockPos origin, int rot, Vec3d at, Vec3d in, Vec3d toward, UUID target) {
		this.origin = origin;
		this.rot = rot;
		this.target = target;
		this.floorY = at.y;
		this.health = GRAB_HEALTH;
		this.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
		this.dataTracker.set(MODE, GRAB);
		this.dataTracker.set(DX, (float) in.x);
		this.dataTracker.set(DZ, (float) in.z);
		this.dataTracker.set(LEN, (float) REACH_GRAB);
		// (The angle is measured from straight out, positive turning from "in" toward "in" turned a quarter left: see handAt.)
		double cross = in.x * toward.z - in.z * toward.x;
		this.dataTracker.set(SWEEP_DIR, cross >= 0.0 ? -1.0F : 1.0F);
		this.dataTracker.set(START, (int) this.getWorld().getTime());
		this.setBoundingBox(this.calculateBoundingBox());
	}

	/**
	 * An arm already lying stuck on the floor from {@code at} (world, at the wall) along {@code dir} for {@code len}: what a squeeze that caught
	 * nobody leaves behind (its clench thrust out of the end wall, and stuck there).
	 */
	public void lying(BlockPos origin, int rot, Vec3d at, Vec3d dir, double len) {
		slam(origin, rot, at, dir, len, null);
		long now = this.getWorld().getTime();
		this.dataTracker.set(START, (int) now - SLAM_TELL - SLAM_FALL);
		this.dataTracker.set(OUTCOME, MISSED);
		this.dataTracker.set(OUT_AT, (int) now);
		this.setBoundingBox(this.calculateBoundingBox());
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(TIP, 0.0F);
		builder.add(HOLDING, false);
		builder.add(MODE, RISE);
		builder.add(START, 0);
		builder.add(DX, 1.0F);
		builder.add(DZ, 0.0F);
		builder.add(LEN, 6.0F);
		builder.add(SWEEP_DIR, 1.0F);
		builder.add(STOP_ANGLE, 0.0F);
		builder.add(HOLD_R, (float) REACH_GRAB);
		builder.add(OUTCOME, PENDING);
		builder.add(OUT_AT, 0);
	}

	public int mode() {
		return this.dataTracker.get(MODE);
	}

	/** The world y of its tip (rising). */
	public float tip() {
		return this.dataTracker.get(TIP);
	}

	public boolean holding() {
		return this.dataTracker.get(HOLDING);
	}

	public UUID heldId() {
		return held;
	}

	public int start() {
		return this.dataTracker.get(START);
	}

	public Vec3d dir() {
		return new Vec3d(this.dataTracker.get(DX), 0.0, this.dataTracker.get(DZ));
	}

	public float len() {
		return this.dataTracker.get(LEN);
	}

	public float sweepDir() {
		return this.dataTracker.get(SWEEP_DIR);
	}

	public float stopAngle() {
		return this.dataTracker.get(STOP_ANGLE);
	}

	public int outcome() {
		return this.dataTracker.get(OUTCOME);
	}

	public int outAt() {
		return this.dataTracker.get(OUT_AT);
	}

	/** Ticks since it began (with the frame's part). */
	public float age(float delta) {
		return (float) (this.getWorld().getTime() - start()) + delta;
	}

	/** It missed and lies stuck now: it can be torn off. */
	public boolean stuck() {
		if (outcome() != MISSED) {
			return false;
		}
		long since = this.getWorld().getTime() - outAt();
		return since >= 0 && since < LIE;
	}

	/** The old name for {@link #stuck}. */
	public boolean stunned() {
		return stuck();
	}

	/** How far it is to being torn off (0 to 1), for the tests. */
	public float torn() {
		return outcome() == CUT && mode() != RISE ? 1.0F : tearing / Shaft.TEAR;
	}

	/** Still a threat (its strike not yet resolved, or holding someone): the next attack waits for it. */
	public boolean threatening() {
		if (mode() == GRAB) {
			return outcome() == PENDING || holding();
		}
		return outcome() == PENDING;
	}

	/** Grab: the angle of the arm now (degrees from straight out), {@code t} ticks from its start. */
	public float angleAt(float t) {
		if (outcome() == HIT || outcome() == MISSED || outcome() == CUT && stopAngle() != 0.0F) {
			return stopAngle();
		}
		float k = MathHelper.clamp((t - GRAB_TELL) / SWEEP, 0.0F, 1.0F);
		return sweepDir() * (-SWEEP_ANGLE + 2.0F * SWEEP_ANGLE * k);
	}

	/** Grab: where the hand is (world), {@code t} ticks from its start: at the wall while it rises, then out along the sweep, low. */
	public Vec3d handAt(float t) {
		Vec3d in = dir();
		Vec3d left = new Vec3d(in.z, 0.0, -in.x);
		if (t < GRAB_TELL && outcome() == PENDING) {
			// Rising open at the wall, a step out from it.
			float up = MathHelper.clamp(t / 8.0F, 0.0F, 1.0F);
			double angle = Math.toRadians(-SWEEP_ANGLE * sweepDir());
			Vec3d out = in.multiply(Math.cos(angle)).add(left.multiply(Math.sin(angle)));
			return this.getPos().add(out.multiply(1.2)).add(0.0, -1.0 + 3.2 * up, 0.0);
		}
		double a = Math.toRadians(angleAt(t));
		Vec3d out = in.multiply(Math.cos(a)).add(left.multiply(Math.sin(a)));
		double lift = holding() ? 1.8 : 0.45;
		if (outcome() == MISSED || outcome() == CUT) {
			lift = 0.25;
		}
		double r = outcome() == HIT ? this.dataTracker.get(HOLD_R) : REACH_GRAB;
		return this.getPos().add(out.multiply(r)).add(0.0, lift, 0.0);
	}

	/** Slam: how far down it is (0 still in the floor showing its back, 1 lying on it). */
	@Override
	protected Box calculateBoundingBox() {
		if (this.dataTracker == null) {
			return super.calculateBoundingBox();
		}
		int mode = this.dataTracker.get(MODE);
		if (mode == SLAM) {
			Vec3d d = dir();
			Vec3d end = this.getPos().add(d.multiply(len()));
			double half = 0.75;
			return new Box(Math.min(this.getX(), end.x) - (Math.abs(d.z) > 0.5 ? half : 0.0), this.getY(), Math.min(this.getZ(), end.z) - (Math.abs(d.x) > 0.5 ? half : 0.0),
					Math.max(this.getX(), end.x) + (Math.abs(d.z) > 0.5 ? half : 0.0), this.getY() + 1.1, Math.max(this.getZ(), end.z) + (Math.abs(d.x) > 0.5 ? half : 0.0));
		}
		if (mode == GRAB) {
			Vec3d h = this.getWorld() == null ? this.getPos() : handAt(age(0.0F));
			return new Box(h.x - 0.8, h.y - 0.5, h.z - 0.8, h.x + 0.8, h.y + 1.0, h.z + 0.8);
		}
		double top = Math.max(this.getY() + 0.5, tip() + 0.6);
		return new Box(this.getX() - WIDTH / 2.0, this.getY(), this.getZ() - WIDTH / 2.0, this.getX() + WIDTH / 2.0, top, this.getZ() + WIDTH / 2.0);
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (TIP.equals(data) || MODE.equals(data) || LEN.equals(data) || DX.equals(data) || OUTCOME.equals(data)) {
			this.setBoundingBox(this.calculateBoundingBox());
		}
	}

	@Override
	public boolean canHit() {
		return outcome() != CUT && !this.isRemoved();
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
		if (!(this.getWorld() instanceof ServerWorld world) || outcome() == CUT || mode() == RISE && state == 2) {
			return false;
		}
		if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
			this.discard();
			return true;
		}
		Vec3d at = mode() == GRAB ? handAt(age(0.0F)) : mode() == SLAM ? nearestOnArm(source) : new Vec3d(this.getX(), tip() - 0.3, this.getZ());
		if (mode() == RISE) {
			// Up the shaft (taking someone off a ledge): enough blows and it lets go.
			Shaft.limbCut(world, origin, at, source, false);
			health -= amount;
			if (health <= 0.0F) {
				release();
				state = 2;
			}
			return true;
		}
		if (holding()) {
			// Its grip: cut hard enough and it lets go of whoever it holds (it is not torn off: it hit, and pulls back).
			Shaft.limbCut(world, origin, at, source, false);
			grip += amount;
			if (grip >= Shaft.TEAR) {
				release();
				this.dataTracker.set(OUT_AT, (int) world.getTime());
			}
			return true;
		}
		if (!stuck()) {
			// Not stuck: a flinch, nothing more.
			Shaft.flinch(world, origin, at, source);
			return true;
		}
		// Stuck after its miss: torn at, and at enough, torn off for good.
		tearing += amount;
		Shaft.limbCut(world, origin, at, source, true);
		if (tearing >= Shaft.TEAR) {
			tearOff(world, source);
		}
		return true;
	}

	/** Where it grows out of the room (world): at the wall's foot (a slam, a grab). */
	public Vec3d root() {
		return this.getPos();
	}

	/** Torn off: it falls and sinks, a stump stays in the wall it came out of. */
	private void tearOff(ServerWorld world, DamageSource source) {
		if (mode() == GRAB && this.dataTracker.get(STOP_ANGLE) == 0.0F) {
			this.dataTracker.set(STOP_ANGLE, angleAt(age(0.0F)) + 0.001F);
		}
		cutDown(world);
		Shaft.torn(world, origin, root(), dir(), Shaft.LIMB_ARM, source.getAttacker());
	}

	/** The point on a slammed arm nearest whoever struck it. */
	private Vec3d nearestOnArm(DamageSource source) {
		Vec3d d = dir();
		Vec3d from = source.getAttacker() != null ? source.getAttacker().getPos() : this.getPos();
		double s = MathHelper.clamp(from.subtract(this.getPos()).dotProduct(d), 0.0, len());
		return this.getPos().add(d.multiply(s)).add(0.0, 0.6, 0.0);
	}

	/** Cut down: it lets go and sinks back into the flesh (it grows back as another). */
	private void cutDown(ServerWorld world) {
		if (mode() == GRAB && this.dataTracker.get(STOP_ANGLE) == 0.0F) {
			this.dataTracker.set(STOP_ANGLE, angleAt(age(0.0F)) + 0.001F);
		}
		release();
		this.dataTracker.set(OUTCOME, CUT);
		this.dataTracker.set(OUT_AT, (int) world.getTime());
		world.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), ChainsawSounds.DEVIL_HURT, SoundCategory.HOSTILE, 2.0F, 0.7F);
	}

	private void release() {
		held = null;
		this.dataTracker.set(HOLDING, false);
	}

	private ServerPlayerEntity heldPlayer(ServerWorld world) {
		return held == null ? null : world.getServer().getPlayerManager().getPlayer(held);
	}

	private void resolve(ServerWorld world, int outcome) {
		this.dataTracker.set(OUTCOME, outcome);
		this.dataTracker.set(OUT_AT, (int) world.getTime());
	}

	@Override
	public void tick() {
		super.tick();
		int mode = mode();
		if (mode != RISE) {
			this.setBoundingBox(this.calculateBoundingBox());
		}
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		if (mode == SLAM) {
			slamTick(world);
		} else if (mode == GRAB) {
			grabTick(world);
		} else {
			riseTick(world);
		}
	}

	private static boolean fair(ServerPlayerEntity p) {
		return p.isAlive() && !p.isSpectator() && !p.isCreative();
	}

	private void slamTick(ServerWorld world) {
		long t = world.getTime() - start();
		int out = outcome();
		if (out == PENDING && t == SLAM_TELL + SLAM_FALL - 1) {
			// It lands: anyone in the lane is hit and shoved toward the middle.
			Vec3d d = dir();
			boolean hit = false;
			Vec3d mid = Shaft.at(origin, rot, Shaft.MID_X, floorY - origin.getY(), Shaft.MID_Z);
			for (ServerPlayerEntity p : world.getPlayers()) {
				if (!fair(p)) {
					continue;
				}
				Vec3d rel = p.getPos().subtract(this.getPos());
				double along = rel.dotProduct(d);
				double across = Math.abs(rel.x * d.z - rel.z * d.x);
				if (along >= -0.3 && along <= len() + 0.3 && across <= LANE / 2.0 + 0.3 && rel.y > -0.5 && rel.y < 1.6) {
					p.damage(this.getDamageSources().generic(), SLAM_DAMAGE);
					Vec3d push = mid.subtract(p.getPos()).multiply(1.0, 0.0, 1.0);
					push = push.lengthSquared() > 0.01 ? push.normalize().multiply(0.9) : new Vec3d(d.z, 0.0, -d.x).multiply(0.9);
					p.setVelocity(push.x, 0.35, push.z);
					p.velocityModified = true;
					Shaft.hurt(world, origin, p);
					hit = true;
				}
			}
			resolve(world, hit ? HIT : MISSED);
			Vec3d c = this.getPos().add(d.multiply(len() * 0.5));
			world.playSound(null, c.x, c.y, c.z, ChainsawSounds.DEVIL_SLAM, SoundCategory.HOSTILE, 3.0F, 0.75F);
			world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, net.minecraft.block.Blocks.NETHER_WART_BLOCK.getDefaultState()), c.x, c.y + 0.3,
					c.z, 50, Math.abs(d.x) * len() * 0.4 + 0.5, 0.2, Math.abs(d.z) * len() * 0.4 + 0.5, 0.15);
		}
		// (A limb that hit pulls back at once; one that missed lies stuck first.)
		if (out != PENDING && world.getTime() - outAt() >= (out == MISSED ? LIE + BACK : BACK)) {
			this.discard();
		}
	}

	private void grabTick(ServerWorld world) {
		long t = world.getTime() - start();
		int out = outcome();
		if (out == PENDING && t >= GRAB_TELL) {
			Vec3d hand = handAt(t);
			Vec3d in = dir();
			Vec3d left = new Vec3d(in.z, 0.0, -in.x);
			float from = angleAt(t - 1.0F);
			float to = angleAt(t);
			for (ServerPlayerEntity p : world.getPlayers()) {
				if (!fair(p) || io.github.kortev.chainsawman.form.Downed.is(p)) {
					continue;
				}
				// The arm sweeps the floor from the wall out to its hand: whoever it passes over this tick is caught (where on it they are).
				Vec3d rel = p.getPos().subtract(this.getPos()).multiply(1.0, 0.0, 1.0);
				double dist = rel.length();
				float a = (float) Math.toDegrees(Math.atan2(rel.dotProduct(left), rel.dotProduct(in)));
				float slack = (float) Math.toDegrees(HAND * 0.6 / Math.max(1.0, dist));
				boolean crossed = a >= Math.min(from, to) - slack && a <= Math.max(from, to) + slack;
				// (Low: feet on the floor, or near it. A jump clears it.)
				if (crossed && dist >= 0.8 && dist <= REACH_GRAB + HAND * 0.6 && p.getY() - floorY < 0.9) {
					held = p.getUuid();
					this.dataTracker.set(HOLD_R, (float) Math.min(dist, REACH_GRAB));
					this.dataTracker.set(STOP_ANGLE, a + 0.001F);
					this.dataTracker.set(HOLDING, true);
					resolve(world, HIT);
					p.damage(this.getDamageSources().generic(), CRUSH);
					Shaft.hurt(world, origin, p);
					world.playSound(null, p.getX(), p.getY(), p.getZ(), ChainsawSounds.DEVIL_BITE, SoundCategory.HOSTILE, 2.0F, 0.6F);
					break;
				}
			}
			if (outcome() == PENDING && t >= GRAB_TELL + SWEEP) {
				this.dataTracker.set(STOP_ANGLE, angleAt(t) + 0.001F);
				resolve(world, MISSED);
				world.playSound(null, hand.x, hand.y, hand.z, ChainsawSounds.DEVIL_SLAM, SoundCategory.HOSTILE, 2.0F, 1.1F);
			}
			if (t == GRAB_TELL) {
				world.playSound(null, hand.x, hand.y, hand.z, ChainsawSounds.DEVIL_SLAM, SoundCategory.HOSTILE, 2.0F, 0.6F);
			}
		}
		if (holding()) {
			ServerPlayerEntity p = heldPlayer(world);
			if (p == null || !fair(p) || io.github.kortev.chainsawman.form.Downed.is(p) || world.getTime() - outAt() > HOLD_MAX) {
				release();
				this.dataTracker.set(OUT_AT, (int) world.getTime());
				if (p != null) {
					// Thrown down beside it.
					p.setVelocity(-dir().x * 0.5, 0.2, -dir().z * 0.5);
					p.velocityModified = true;
				}
			} else {
				// Held up in the hand and squeezed: carried by velocity, so the game does not think you moved too fast.
				Vec3d hand = handAt(t);
				Vec3d want = hand.add(0.0, -0.4, 0.0);
				p.setVelocity((want.x - p.getX()) * 0.45, (want.y - p.getY()) * 0.45, (want.z - p.getZ()) * 0.45);
				p.velocityModified = true;
				p.fallDistance = 0.0F;
				if ((world.getTime() - outAt()) % 10 == 9) {
					p.damage(this.getDamageSources().generic(), CRUSH / 2.0F);
				}
			}
			return;
		}
		if (out != PENDING) {
			long since = world.getTime() - outAt();
			if (since >= (out == CUT ? BACK : out == MISSED ? LIE + BACK : 8 + BACK)) {
				this.discard();
			}
		}
	}

	private void riseTick(ServerWorld world) {
		double tip = tip();
		if (state == 0) {
			tip += RISE_SPEED;
			ServerPlayerEntity catches = null;
			for (ServerPlayerEntity p : world.getPlayers()) {
				if (!fair(p) || io.github.kortev.chainsawman.form.Downed.is(p)) {
					continue;
				}
				double dx = p.getX() - this.getX();
				double dz = p.getZ() - this.getZ();
				double dy = p.getY() + 0.9 - tip;
				// (Anyone the tip is level with, near enough across, on a ledge: the hand is wide.)
				if (dx * dx + dz * dz <= REACH * REACH && dy <= 0.6 && dy >= -RISE_SPEED - 1.6 && p.getY() > floorY + 4.0) {
					catches = p;
					break;
				}
			}
			if (catches != null) {
				held = catches.getUuid();
				state = 1;
				this.dataTracker.set(HOLDING, true);
				catches.damage(this.getDamageSources().generic(), GRAB_DAMAGE);
				world.playSound(null, catches.getX(), catches.getY(), catches.getZ(), ChainsawSounds.DEVIL_BITE, SoundCategory.HOSTILE, 1.4F, 0.7F);
			} else if (tip >= maxTip) {
				state = 2;
			}
		} else if (state == 1) {
			ServerPlayerEntity p = heldPlayer(world);
			if (p == null || !p.isAlive()) {
				release();
				state = 2;
			} else {
				tip -= PULL;
				// Pulled in to its column and down at the arm's speed, toward the mouth's middle.
				Vec3d mouth = Shaft.at(origin, rot, Shaft.MID_X, 0.0, Shaft.MID_Z);
				boolean low = tip < floorY + 5.0;
				p.setVelocity(((low ? mouth.x : this.getX()) - p.getX()) * 0.3, -PULL + (tip - 1.0 - p.getY()) * 0.3, ((low ? mouth.z : this.getZ()) - p.getZ()) * 0.3);
				p.velocityModified = true;
				p.fallDistance = 0.0F;
				if (tip <= floorY + 1.5) {
					// Over the mouth: let go, and they drop through it.
					release();
					state = 2;
				}
			}
		} else {
			if (holding()) {
				release();
			}
			tip -= SINK;
			if (tip <= floorY) {
				this.discard();
				return;
			}
		}
		this.dataTracker.set(TIP, (float) tip);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
