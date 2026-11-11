package io.github.kortev.chainsawman.devil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The Leech Devil: a big wrinkled, sagging body on four legs with two long tentacle arms, every limb ending in a round sucker mouth; its face
 * one big mouth of blocky teeth under long black hair. A step above the Tomato. Faster than a walk, slower than a sprint; it circles you
 * humming, and has three moves, never two at once, each with a tell and a gap after it in which it can be hit:
 * <ul>
 * <li>the tongue ({@link #TONGUE}): it rears, the mouth gapes, the tongue coils back ({@link #TONGUE_TELL}); the aim locks at the end of the
 * tell and the tongue lances out straight up to {@link #TONGUE_REACH} blocks, and stays out a moment (a cut on it then counts more);</li>
 * <li>the latch ({@link #LATCH}, {@link #LATCHED}): one tentacle coils back and its sucker opens; it whips out, and on a hit sticks to you,
 * drinking: you are slowed and lose health, it heals ({@link #LATCH_HEAL} a pulse: the only way it heals: the show's devils heal on blood).
 * Cut the arm ({@link LeechPartEntity}) to tear it loose ({@link #CUT_HITS} hits or {@link #CUT_DAMAGE} damage); it lets go by itself after
 * {@link #LATCH_MAX} ticks;</li>
 * <li>the trample ({@link #TRAMPLE_TELL}, {@link #TRAMPLE}): only at {@link #TRAMPLE_MIN} blocks or more: it lowers its body and paws the
 * ground, then runs a straight line; it skids and is open after ({@link #SKID}).</li>
 * </ul>
 * Killed, it sags and folds, its suckers opening and closing slower and slower ({@link #DEATH_TICKS}); it leaves a sucker
 * ({@link Devils#LEECH_SUCKER}).
 *
 * <p>Its animation is drawn from its state and the world time the state began (both sent to everyone); its legs' steps are the client's.
 */
public class LeechDevilEntity extends HostileEntity {
	public static final float WIDTH = 4.0F;
	public static final float HEIGHT = 3.0F;
	public static final float MAX_HEALTH = 160.0F;
	/** The body's shape on the ground (half its width, half its length nose to tail, without the hair): nobody stands inside it. */
	/** Its thick wrinkled hide (armour points): an iron sword's cut lands about 3.4, a stone one's about 2.7 (a fight of a minute or so). */
	public static final double HIDE = 14.0;
	public static final double BODY_HALF_WIDTH = 1.6;
	public static final double BODY_HALF_LENGTH = 2.9;
	/** About 5 blocks a second: faster than a player's walk (4.3), slower than a sprint (5.6). */
	public static final double SPEED = 0.42;

	public static final int WALK = 0;
	public static final int TONGUE = 1;
	public static final int LATCH = 2;
	public static final int LATCHED = 3;
	/** Torn loose: it shrieks and reels back, open. */
	public static final int RECOIL = 4;
	public static final int TRAMPLE_TELL = 5;
	public static final int TRAMPLE = 6;
	public static final int SKID = 7;

	/** The tongue: the tell (aim locked at its end), out in {@link #TONGUE_OUT} ticks, held out, drawn back. */
	public static final int TONGUE_TELL = 16;
	public static final int TONGUE_OUT = 3;
	public static final int TONGUE_HOLD = 8;
	public static final int TONGUE_BACK = 8;
	public static final int TONGUE_HIT = TONGUE_TELL + TONGUE_OUT;
	public static final int TONGUE_END = TONGUE_HIT + TONGUE_HOLD + TONGUE_BACK;
	public static final double TONGUE_REACH = 10.0;
	public static final float TONGUE_DAMAGE = 8.0F;
	/** How fat the tongue is for hitting (blocks either side of its line, besides the target's own half width). */
	public static final double TONGUE_RADIUS = 0.35;
	/** A cut on the tongue while it is out counts this much. */
	public static final float TONGUE_CUT_TAKES = 1.5F;
	/**
	 * The tongue's aim follows where you were this many ticks ago (it hears you, it does not see you): stand still and it has you; keep
	 * moving sideways through the tell and it lances where you were.
	 */
	public static final int TONGUE_LAG = 6;
	/** The stagger: slowed hard this long. */
	public static final int STAGGER = 16;

	/** The latch: the tell (it turns to aim in the first part), out in {@link #LATCH_OUT} ticks, drawn back on a miss. */
	public static final int LATCH_TELL = 14;
	public static final int LATCH_AIM = 7;
	public static final int LATCH_OUT = 3;
	public static final int LATCH_BACK = 10;
	public static final int LATCH_END = LATCH_TELL + LATCH_OUT + LATCH_BACK;
	public static final double LATCH_REACH = 7.0;
	public static final double LATCH_RADIUS = 0.5;
	/** Stuck: a pulse every {@link #LATCH_PULSE} ticks drains {@link #LATCH_DRAIN} and heals it {@link #LATCH_HEAL}; it lets go after {@link #LATCH_MAX}. */
	public static final int LATCH_PULSE = 10;
	public static final float LATCH_DRAIN = 1.0F;
	public static final float LATCH_HEAL = 4.0F;
	public static final int LATCH_MAX = 120;
	/** Cutting it loose: this many hits on the arm (a hit of at least {@link #CUT_HIT_MIN}), or this much damage. */
	public static final int CUT_HITS = 3;
	public static final float CUT_DAMAGE = 12.0F;
	public static final float CUT_HIT_MIN = 2.5F;
	/** The cut arm hangs stunned this long (no latch till then). */
	public static final int ARM_STUN = 120;
	public static final int RECOIL_TICKS = 24;
	/** No latch again this soon after one ends. */
	public static final int LATCH_COOLDOWN = 140;

	/** The trample: from this far or more; the tell (it turns to aim in the first part), the run, the skid. */
	public static final double TRAMPLE_MIN = 8.0;
	public static final int TRAMPLE_TELL_TICKS = 20;
	public static final int TRAMPLE_AIM = 14;
	public static final double TRAMPLE_SPEED = 0.8;
	public static final double TRAMPLE_DISTANCE = 14.0;
	public static final float TRAMPLE_DAMAGE = 6.0F;
	public static final double TRAMPLE_THROW = 1.1;
	/** How wide the trample runs you down: half its width across the legs. */
	public static final double TRAMPLE_HALF_WIDTH = 1.7;
	public static final int SKID_TICKS = 30;

	/** After a move: it stands still, open to hits, this long, then may circle; and no move comes for the gap. */
	public static final int REST = 14;
	public static final int GAP = 34;
	/** On first noticing a player: its face turns to you and it stands dead still this long. */
	public static final int NOTICE_STILL = 24;
	/** Dying: it sags and folds, the suckers slowing, for this long. */
	public static final int DEATH_TICKS = 90;
	/**
	 * It fights at reach: it keeps you about this far off (blocks between your middle and its), circling, and backs off to it after each
	 * move; nearer than {@link #TOO_NEAR} it backs away fast. How often it hums.
	 */
	public static final double CIRCLE = 7.0;
	public static final double TOO_NEAR = 5.0;
	/** A cut on the stunned arm lying on the ground counts this much (as on the tongue). */
	public static final float LIMP_TAKES = 1.5F;
	/** The stunned arm lies near you this long of its stun, then is drawn back out of reach. */
	public static final int LIMP_TICKS = 70;
	/** Its hurt grunt comes no oftener than this (ticks). */
	public static final int GRUNT_EVERY = 15;
	public static final int HUM_EVERY = 34;

	private static final TrackedData<Integer> STATE = DataTracker.registerData(LeechDevilEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Long> STATE_AT = DataTracker.registerData(LeechDevilEntity.class, TrackedDataHandlerRegistry.LONG);
	/** The way a move is aimed (degrees, a yaw) and its pitch (degrees, up positive). */
	private static final TrackedData<Float> AIM = DataTracker.registerData(LeechDevilEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> AIM_PITCH = DataTracker.registerData(LeechDevilEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** How far the tongue or the arm goes this time (to the first block in the way). */
	private static final TrackedData<Float> REACH = DataTracker.registerData(LeechDevilEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** Who the arm is stuck to (an entity id), or -1. */
	private static final TrackedData<Integer> STUCK = DataTracker.registerData(LeechDevilEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Which arm latches (0 its left, 1 its right). */
	private static final TrackedData<Integer> ARM = DataTracker.registerData(LeechDevilEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Till when (the world's time) the cut arm hangs stunned. */
	private static final TrackedData<Long> ARM_STUN_UNTIL = DataTracker.registerData(LeechDevilEntity.class, TrackedDataHandlerRegistry.LONG);
	/** When it noticed someone (the world's time), or -1. */
	private static final TrackedData<Long> NOTICED = DataTracker.registerData(LeechDevilEntity.class, TrackedDataHandlerRegistry.LONG);
	/** Where the stunned arm's end lies on the ground (the world), while it lies there. */
	private static final TrackedData<Vector3f> LIMP = DataTracker.registerData(LeechDevilEntity.class, TrackedDataHandlerRegistry.VECTOR3F);

	private long nextMoveAt;
	private long restUntil;
	private long lastTongueAt = -1000;
	private long lastTrampleAt = -1000;
	private long latchReadyAt;
	private long lastHumAt = -1000;
	private long lostSince = -1;
	private int lastMove = -1;
	private int moveStreak;
	private int circleDir = 1;
	private final Set<UUID> struck = new HashSet<>();
	@Nullable
	private Vec3d trampleFrom;
	@Nullable
	private Vec3d wanderTo;
	private long wanderAt;
	/** The arm's cuts this latch: hits and damage. */
	private int armHits;
	private float armDamage;
	/** Where its target was the last few ticks (the tongue aims late). */
	private final Vec3d[] heard = new Vec3d[TONGUE_LAG + 2];
	@Nullable
	private LeechPartEntity armPart;
	@Nullable
	private LeechPartEntity tonguePart;
	/** The held-out tongue's second box (its last 3 blocks are the hit area: two boxes). */
	@Nullable
	private LeechPartEntity tonguePart2;
	/** The stunned arm lying on the ground near whoever cut it, while it lies there. */
	@Nullable
	private LeechPartEntity limpPart;
	private boolean tongueCut;
	private long lastGruntAt = -1000;

	/** Somewhere it is told to walk to before anything else (the film's first seconds); null for its own mind. */
	@Nullable
	public Vec3d walkTo;
	/** Told of every move it makes (the tests). */
	@Nullable
	public java.util.function.IntConsumer moveListener;
	/** Drawn on the client: its legs' steps (owned by the renderer). */
	public Object clientState;

	public LeechDevilEntity(EntityType<? extends LeechDevilEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 20;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, TONGUE_DAMAGE).add(EntityAttributes.GENERIC_ARMOR, HIDE).add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(STATE, WALK);
		builder.add(STATE_AT, 0L);
		builder.add(AIM, 0.0F);
		builder.add(AIM_PITCH, 0.0F);
		builder.add(REACH, 0.0F);
		builder.add(STUCK, -1);
		builder.add(ARM, 0);
		builder.add(ARM_STUN_UNTIL, -1L);
		builder.add(NOTICED, -1L);
		builder.add(LIMP, new Vector3f());
	}

	// ---- What it is doing (both sides).

	public int state() {
		return dataTracker.get(STATE);
	}

	/** Ticks into the state (with the frame's part of a tick on the client). */
	public float stateTime(float delta) {
		return (float) (getWorld().getTime() - dataTracker.get(STATE_AT)) + delta;
	}

	public float aim() {
		return dataTracker.get(AIM);
	}

	public float aimPitch() {
		return dataTracker.get(AIM_PITCH);
	}

	public void aimAt(float yaw) {
		dataTracker.set(AIM, yaw);
	}

	public float reach() {
		return dataTracker.get(REACH);
	}

	/** The entity id the arm is stuck to, or -1. */
	public int stuckTo() {
		return dataTracker.get(STUCK);
	}

	/** Which arm latches: 0 its left, 1 its right. */
	public int arm() {
		return dataTracker.get(ARM);
	}

	public boolean armStunned() {
		return getWorld().getTime() < dataTracker.get(ARM_STUN_UNTIL);
	}

	public long armStunUntil() {
		return dataTracker.get(ARM_STUN_UNTIL);
	}

	/** Where the stunned arm's end lies (the world), or null when it is not lying out. */
	@Nullable
	public Vec3d limpAt() {
		if (!armStunned() || getWorld().getTime() > dataTracker.get(ARM_STUN_UNTIL) - ARM_STUN + LIMP_TICKS) {
			return null;
		}
		Vector3f v = dataTracker.get(LIMP);
		return new Vec3d(v.x, v.y, v.z);
	}

	@Nullable
	public LeechPartEntity limpPart() {
		return limpPart;
	}

	public long noticedAt() {
		return dataTracker.get(NOTICED);
	}

	/** Has it notice someone now (or forget them): for the self tests' stills. */
	public void notice(boolean on) {
		dataTracker.set(NOTICED, on ? getWorld().getTime() : -1L);
	}

	/** Starts a state now (the server). */
	public void begin(int state) {
		if (state != LATCHED) {
			unstick();
		}
		if (state != TONGUE) {
			dropPart(false);
		}
		dataTracker.set(STATE, state);
		dataTracker.set(STATE_AT, getWorld().getTime());
		if (state == TONGUE || state == LATCH || state == TRAMPLE_TELL) {
			// The hum stops dead at a tell.
			hush();
		}
	}

	public boolean allowed(int move) {
		return !(move == lastMove && moveStreak >= 2);
	}

	public int lastMove() {
		return lastMove;
	}

	public int armHits() {
		return armHits;
	}

	@Nullable
	public LeechPartEntity armPart() {
		return armPart;
	}

	@Nullable
	public LeechPartEntity tonguePart() {
		return tonguePart;
	}

	private void made(int move) {
		moveStreak = move == lastMove ? moveStreak + 1 : 1;
		lastMove = move;
		if (moveListener != null) {
			moveListener.accept(move);
		}
	}

	/** Whether the trample may come at this distance from its target. */
	public static boolean trampleFrom(double distance) {
		return distance >= TRAMPLE_MIN;
	}

	// ---- Where its parts are (the world), facing its aim.

	private static Vec3d forward(float yaw) {
		float r = yaw * MathHelper.RADIANS_PER_DEGREE;
		return new Vec3d(-MathHelper.sin(r), 0.0, MathHelper.cos(r));
	}

	private static Vec3d look(float yaw, float pitch) {
		float p = pitch * MathHelper.RADIANS_PER_DEGREE;
		return forward(yaw).multiply(MathHelper.cos(p)).add(0.0, MathHelper.sin(p), 0.0);
	}

	/** A point of its body's frame (+X its left, +Z its front) in the world, facing {@code yaw}. */
	public Vec3d bodyPoint(float yaw, double x, double y, double z) {
		Vec3d f = forward(yaw);
		Vec3d left = new Vec3d(f.z, 0.0, -f.x);
		return getPos().add(f.multiply(z)).add(left.multiply(x)).add(0.0, y, 0.0);
	}

	/** Where its mouth is (the world), facing its aim. */
	public Vec3d mouth() {
		return bodyPoint(aim(), 0.0, 1.55, 2.75);
	}

	/** Where the latching arm comes out of it (the world), facing its aim. */
	public Vec3d shoulder() {
		return bodyPoint(aim(), arm() == 0 ? 0.85 : -0.85, 1.55, 1.55);
	}

	// ---- The fight (the server).

	@Nullable
	private PlayerEntity target() {
		PlayerEntity p = getWorld().getClosestPlayer(getX(), getY(), getZ(), 32.0, EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR);
		return p != null && p.isAlive() ? p : null;
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		ServerWorld world = (ServerWorld) getWorld();
		long now = world.getTime();
		float t = stateTime(0.0F);
		PlayerEntity target = target();
		if (age < 3) {
			nextMoveAt = Math.max(nextMoveAt, now + 40);
		}
		heard[(int) Math.floorMod(now, (long) heard.length)] = target == null ? null : target.getPos().add(0.0, target.getHeight() * 0.55, 0.0);
		switch (state()) {
			case WALK -> walk(world, now, target);
			case TONGUE -> tongue(world, t, target);
			case LATCH -> latch(world, t, target);
			case LATCHED -> latched(world, t);
			case RECOIL -> {
				still();
				if (t >= RECOIL_TICKS) {
					end(now, GAP - 10);
				}
			}
			case TRAMPLE_TELL -> trampleTell(world, t, target);
			case TRAMPLE -> trample(world, t);
			case SKID -> skid(world, t);
			default -> begin(WALK);
		}
		if (now % 40 == 0) {
			Sniff.near(world, getPos());
		}
		shoveOut(world);
		tickLimp();
		// Noticing: the first sight of someone; forgetting them once nobody has been about a while.
		if (target != null && walkTo == null) {
			lostSince = -1;
			if (noticedAt() < 0) {
				dataTracker.set(NOTICED, now);
				nextMoveAt = Math.max(nextMoveAt, now + 40);
			}
		} else if (noticedAt() >= 0) {
			if (lostSince < 0) {
				lostSince = now;
			} else if (now - lostSince > 200) {
				dataTracker.set(NOTICED, -1L);
			}
		}
	}

	/** Its body is long (about 5 blocks nose to tail, 2.6 across): whoever stands inside that shape is eased out of it. */
	private void shoveOut(ServerWorld world) {
		if (!isAlive()) {
			return;
		}
		Vec3d f = forward(getBodyYaw());
		Vec3d left = new Vec3d(f.z, 0.0, -f.x);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(2.5, 0.0, 2.5), this::canHurt)) {
			Vec3d rel = e.getPos().subtract(getPos());
			double z = rel.dotProduct(f) - 0.2;
			double x = rel.dotProduct(left);
			double hx = BODY_HALF_WIDTH + e.getWidth() * 0.5;
			double hz = BODY_HALF_LENGTH + e.getWidth() * 0.5;
			double k = (x / hx) * (x / hx) + (z / hz) * (z / hz);
			if (k < 1.0) {
				Vec3d out = left.multiply(x / (hx * hx)).add(f.multiply(z / (hz * hz)));
				if (out.lengthSquared() < 1.0E-6) {
					out = f;
				}
				out = out.normalize().multiply(0.18 * (1.0 - k) + 0.04);
				e.addVelocity(out.x, 0.0, out.z);
				e.velocityModified = true;
			}
		}
	}

	private void end(long now, int gap) {
		begin(WALK);
		nextMoveAt = now + gap;
		restUntil = now + REST;
	}

	private void still() {
		navigation.stop();
		setVelocity(0.0, getVelocity().y, 0.0);
		face(aim());
	}

	private void face(float yaw) {
		setYaw(yaw);
		setBodyYaw(yaw);
		setHeadYaw(yaw);
	}

	private float yawTo(Vec3d at) {
		return (float) Math.toDegrees(Math.atan2(-(at.x - getX()), at.z - getZ()));
	}

	private static float approach(float from, float to, float step) {
		return from + MathHelper.clamp(MathHelper.wrapDegrees(to - from), -step, step);
	}

	private void walk(ServerWorld world, long now, @Nullable PlayerEntity target) {
		if (walkTo != null) {
			if (now % 10 == 0 || navigation.isIdle()) {
				navigation.startMovingTo(walkTo.x, walkTo.y, walkTo.z, 0.7);
			}
			// (humming to itself as it comes, before it has noticed anyone)
			if (target != null && now - lastHumAt > HUM_EVERY && target.squaredDistanceTo(this) < 30.0 * 30.0 && getPos().squaredDistanceTo(walkTo) > 2.0) {
				lastHumAt = now;
				playSound(LeechSounds.HUM, 1.0F, 0.95F + random.nextFloat() * 0.1F);
			}
			return;
		}
		if (target == null) {
			if (wanderTo == null || now - wanderAt > 160 || getPos().squaredDistanceTo(wanderTo) < 2.0) {
				wanderAt = now;
				wanderTo = getPos().add(random.nextDouble() * 16.0 - 8.0, 0.0, random.nextDouble() * 16.0 - 8.0);
				navigation.startMovingTo(wanderTo.x, wanderTo.y, wanderTo.z, 0.5);
			}
			return;
		}
		wanderTo = null;
		long noticed = noticedAt();
		float to = yawTo(target.getPos());
		double dist = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
		getLookControl().lookAt(target, 20.0F, 20.0F);
		if (noticed >= 0 && now - noticed < NOTICE_STILL) {
			// Just seen you: stands, and slowly turns its whole body to face you.
			navigation.stop();
			setVelocity(0.0, getVelocity().y, 0.0);
			face(approach(getYaw(), to, 5.0F));
			return;
		}
		if (dist > CIRCLE + 3.5) {
			if (now % 6 == 0 || navigation.isIdle()) {
				navigation.startMovingTo(target, 1.0);
			}
		} else {
			// At reach: round you sideways like a crab, its face always on you, in or out to keep its distance; after a move (and
			// whenever you come close) it backs off to it first.
			navigation.stop();
			if (random.nextFloat() < 0.012F) {
				circleDir = -circleDir;
			}
			face(approach(getYaw(), to, 14.0F));
			boolean resting = now < restUntil;
			float in = dist < TOO_NEAR ? -1.0F : dist < CIRCLE - 1.0 ? -0.6F : dist > CIRCLE + 1.2 ? 0.5F : 0.0F;
			getMoveControl().strafeTo(in, resting && in < 0.0F ? 0.0F : resting ? circleDir * 0.3F : circleDir * 0.8F);
		}
		if (now - lastHumAt > HUM_EVERY && dist < 16.0) {
			lastHumAt = now;
			playSound(LeechSounds.HUM, 0.9F, 0.95F + random.nextFloat() * 0.1F);
		}
		if (now < nextMoveAt || now % 4 != 0) {
			return;
		}
		boolean sees = canSee(target);
		boolean trample = sees && trampleFrom(dist) && dist <= TRAMPLE_DISTANCE + 4.0 && now - lastTrampleAt > 160 && allowed(TRAMPLE_TELL);
		boolean latch = sees && dist >= 1.5 && dist <= LATCH_REACH + 1.4 && now >= latchReadyAt && !armStunned() && allowed(LATCH);
		boolean tongue = sees && dist >= 2.0 && dist <= TONGUE_REACH + 1.5 && now - lastTongueAt > 50 && allowed(TONGUE);
		float wTrample = trample ? 3.0F : 0.0F;
		float wLatch = latch ? 2.2F : 0.0F;
		float wTongue = tongue ? 2.0F : 0.0F;
		float sum = wTrample + wLatch + wTongue;
		if (sum <= 0.0F || random.nextFloat() > 0.7F) {
			return;
		}
		float r = random.nextFloat() * sum;
		navigation.stop();
		dataTracker.set(AIM, to);
		if (r < wTrample) {
			made(TRAMPLE_TELL);
			lastTrampleAt = now;
			begin(TRAMPLE_TELL);
		} else if (r < wTrample + wLatch) {
			made(LATCH);
			dataTracker.set(ARM, random.nextInt(2));
			begin(LATCH);
			playSound(LeechSounds.SMACK, 1.2F, 0.9F + random.nextFloat() * 0.2F);
		} else {
			made(TONGUE);
			lastTongueAt = now;
			begin(TONGUE);
			playSound(LeechSounds.TELL, 1.3F, 1.0F);
		}
	}

	/** Its pitch toward someone's chest from {@code from}. */
	private static float pitchTo(Vec3d from, LivingEntity e) {
		Vec3d at = e.getPos().add(0.0, e.getHeight() * 0.55, 0.0);
		double dh = Math.max(0.5, Math.hypot(at.x - from.x, at.z - from.z));
		return MathHelper.clamp((float) Math.toDegrees(Math.atan2(at.y - from.y, dh)), -35.0F, 25.0F);
	}

	/** How far along {@code dir} from {@code from} it can go (to the first solid block), at most {@code max}. */
	private double clear(Vec3d from, Vec3d dir, double max) {
		BlockHitResult hit = getWorld().raycast(new RaycastContext(from, from.add(dir.multiply(max)), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE,
				this));
		return hit.getType() == HitResult.Type.MISS ? max : Math.max(0.5, hit.getPos().distanceTo(from));
	}

	private boolean canHurt(LivingEntity e) {
		return e != this && e.isAlive() && !(e instanceof LeechDevilEntity) && !e.isSpectator() && !(e instanceof PlayerEntity p && p.isCreative());
	}

	/** Who is on the segment {@code a} to {@code b} (within {@code r} of it, besides their own half width), nearest {@code a} first. */
	private LivingEntity onLine(ServerWorld world, Vec3d a, Vec3d b, double r) {
		LivingEntity best = null;
		double bestAlong = Double.MAX_VALUE;
		Vec3d ab = b.subtract(a);
		double len2 = Math.max(1.0E-4, ab.lengthSquared());
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, new net.minecraft.util.math.Box(a, b).expand(r + 1.5), this::canHurt)) {
			if (struck.contains(e.getUuid())) {
				continue;
			}
			// the nearest point of the segment to the middle of the body, and how far from the body's axis that is
			double lo = e.getY();
			double hi = e.getY() + e.getHeight();
			Vec3d c = new Vec3d(e.getX(), MathHelper.clamp(a.y, lo, hi), e.getZ());
			double k = MathHelper.clamp(c.subtract(a).dotProduct(ab) / len2, 0.0, 1.0);
			Vec3d p = a.add(ab.multiply(k));
			double dy = p.y < lo ? lo - p.y : p.y > hi ? p.y - hi : 0.0;
			double dxz = Math.hypot(p.x - e.getX(), p.z - e.getZ());
			if (dxz <= r + e.getWidth() * 0.5 && dy <= r) {
				double along = k * Math.sqrt(len2);
				if (along < bestAlong) {
					bestAlong = along;
					best = e;
				}
			}
		}
		return best;
	}

	private void tongue(ServerWorld world, float t, @Nullable PlayerEntity target) {
		still();
		if (t < TONGUE_TELL && target != null) {
			// It follows where it heard you, a moment late, till the end of the tell; then the aim is set.
			Vec3d was = heard[(int) Math.floorMod(world.getTime() - TONGUE_LAG, (long) heard.length)];
			if (was == null) {
				was = target.getPos().add(0.0, target.getHeight() * 0.55, 0.0);
			}
			dataTracker.set(AIM, approach(aim(), yawTo(was), 12.0F));
			Vec3d m = mouth();
			double dh = Math.max(0.5, Math.hypot(was.x - m.x, was.z - m.z));
			dataTracker.set(AIM_PITCH, MathHelper.clamp((float) Math.toDegrees(Math.atan2(was.y - m.y, dh)), -35.0F, 25.0F));
		}
		int ti = (int) t;
		if (ti == TONGUE_TELL) {
			struck.clear();
			Vec3d dir = look(aim(), aimPitch());
			dataTracker.set(REACH, (float) clear(mouth(), dir, TONGUE_REACH));
			playSound(LeechSounds.TONGUE, 1.5F, 0.95F + random.nextFloat() * 0.1F);
		}
		Vec3d dir = look(aim(), aimPitch());
		Vec3d from = mouth();
		if (t >= TONGUE_TELL && t < TONGUE_HIT + 1) {
			double len = reach() * MathHelper.clamp((t - TONGUE_TELL + 1.0F) / TONGUE_OUT, 0.0F, 1.0F);
			LivingEntity e = onLine(world, from, from.add(dir.multiply(len)), TONGUE_RADIUS);
			if (e != null) {
				struck.add(e.getUuid());
				if (e.damage(getDamageSources().mobAttack(this), TONGUE_DAMAGE)) {
					e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, STAGGER, 3, false, false));
					e.setVelocity(dir.x * 0.5, 0.25, dir.z * 0.5);
					e.velocityModified = true;
					world.spawnParticles(BLOOD, e.getX(), e.getY() + 1.0, e.getZ(), 14, 0.2, 0.3, 0.2, 0.1);
				}
			}
		}
		// Held out: the tongue can be cut where it is nearest whoever would cut it.
		if (t >= TONGUE_HIT && t < TONGUE_HIT + TONGUE_HOLD) {
			// Held out, the 3 blocks of it nearest you can be cut: two boxes along them, following you as you move.
			if (tonguePart == null || tonguePart.isRemoved()) {
				tongueCut = false;
				tonguePart = new LeechPartEntity(world, this, LeechPartEntity.TONGUE);
				tonguePart2 = new LeechPartEntity(world, this, LeechPartEntity.TONGUE);
				world.spawnEntity(tonguePart);
				world.spawnEntity(tonguePart2);
			}
			double len = reach();
			double k = target == null ? len - 1.5 : target.getPos().add(0.0, 1.0, 0.0).subtract(from).dotProduct(dir);
			k = MathHelper.clamp(k, Math.min(len, 2.25), Math.max(Math.min(len, 2.25), len - 0.75));
			placeOn(tonguePart, from.add(dir.multiply(Math.min(len, k + 0.75))));
			placeOn(tonguePart2, from.add(dir.multiply(Math.max(1.0, k - 0.75))));
		} else if (tonguePart != null) {
			dropPart(false);
		}
		if (ti == TONGUE_HIT + TONGUE_HOLD) {
			playSound(LeechSounds.TASTE, 1.0F, 0.8F);
		}
		if (t >= TONGUE_END) {
			end(world.getTime(), GAP);
		}
	}

	private static void placeOn(LeechPartEntity part, Vec3d p) {
		part.refreshPositionAndAngles(p.x, p.y - part.getHeight() * 0.5, p.z, 0.0F, 0.0F);
	}

	/** Puts a part where the line {@code a}-{@code b} is nearest {@code near} (no nearer {@code a} than {@code skip}). */
	private void placePart(LeechPartEntity part, Vec3d a, Vec3d b, @Nullable Entity near, double skip) {
		Vec3d ab = b.subtract(a);
		double len = ab.length();
		if (len < 1.0E-3) {
			part.refreshPositionAndAngles(a.x, a.y - 0.8, a.z, 0.0F, 0.0F);
			return;
		}
		Vec3d u = ab.multiply(1.0 / len);
		double k = near == null ? len * 0.5 : near.getPos().add(0.0, 1.0, 0.0).subtract(a).dotProduct(u);
		k = MathHelper.clamp(k, Math.min(skip, len), len);
		Vec3d p = a.add(u.multiply(k));
		part.refreshPositionAndAngles(p.x, p.y - 0.8, p.z, 0.0F, 0.0F);
	}

	private void latch(ServerWorld world, float t, @Nullable PlayerEntity target) {
		still();
		if (t < LATCH_AIM && target != null) {
			// aimed from the shoulder the arm comes out of, and set well before the tell ends: a step aside after that is enough
			Vec3d sh = shoulder();
			float fromShoulder = (float) Math.toDegrees(Math.atan2(-(target.getX() - sh.x), target.getZ() - sh.z));
			dataTracker.set(AIM, approach(aim(), fromShoulder, 12.0F));
			dataTracker.set(AIM_PITCH, pitchTo(sh, target));
		}
		int ti = (int) t;
		Vec3d from = shoulder();
		// (Its aim is the body's, turned in toward the side the arm is on, so the arm goes where you were when the tell ended.)
		Vec3d dir = armDir(target);
		if (ti == LATCH_TELL) {
			struck.clear();
			dataTracker.set(REACH, (float) clear(from, dir, LATCH_REACH));
			playSound(LeechSounds.WHIP, 1.3F, 0.9F + random.nextFloat() * 0.2F);
		}
		if (t >= LATCH_TELL && t < LATCH_TELL + LATCH_OUT + 1) {
			double len = reach() * MathHelper.clamp((t - LATCH_TELL + 1.0F) / LATCH_OUT, 0.0F, 1.0F);
			LivingEntity e = onLine(world, from, from.add(dir.multiply(len)), LATCH_RADIUS);
			if (e != null) {
				stick(world, e);
				return;
			}
		}
		if (t >= LATCH_END) {
			latchReadyAt = world.getTime() + LATCH_COOLDOWN / 2;
			end(world.getTime(), GAP - 8);
		}
	}

	/** The way the arm goes: from its shoulder along the aim (locked with the tell's end), pitched to where you were. */
	private Vec3d armDir(@Nullable PlayerEntity target) {
		return look(aim(), aimPitch());
	}

	private void stick(ServerWorld world, LivingEntity e) {
		armHits = 0;
		armDamage = 0.0F;
		begin(LATCHED);
		dataTracker.set(STUCK, e.getId());
		playSound(LeechSounds.SMACK, 1.6F, 0.7F);
		armPart = new LeechPartEntity(world, this, LeechPartEntity.ARM);
		placeArm(shoulder(), e.getPos().add(0.0, e.getHeight() * 0.55, 0.0));
		world.spawnEntity(armPart);
	}

	/** The stuck arm's cuttable box: up the arm from whoever it holds, where they can reach it with a blade. */
	private void placeArm(Vec3d sh, Vec3d at) {
		double d = sh.distanceTo(at);
		Vec3d p = d < 1.0E-3 ? at : at.add(sh.subtract(at).multiply(Math.min(2.4, d * 0.6) / d));
		armPart.refreshPositionAndAngles(p.x, p.y - 0.8, p.z, 0.0F, 0.0F);
	}

	/** The lying arm's box: along it from its end toward the body, where whoever is nearest can reach it. */
	private void placeLimp() {
		Vec3d tip = limpAt();
		if (limpPart == null || tip == null) {
			return;
		}
		Vec3d sh = shoulder();
		Vec3d groundSh = new Vec3d(sh.x, tip.y, sh.z);
		PlayerEntity p = target();
		double len = groundSh.distanceTo(tip);
		double k = 0.0;
		if (p != null && len > 0.01) {
			k = MathHelper.clamp(p.getPos().subtract(tip).dotProduct(groundSh.subtract(tip).multiply(1.0 / len)) + 1.0, 0.0, Math.max(0.0, len - 1.0));
		}
		Vec3d at = len > 0.01 ? tip.add(groundSh.subtract(tip).multiply(k / len)) : tip;
		limpPart.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
	}

	/** Each tick: the lying arm drags after the body if it moves off, and is drawn back once its time is up. */
	private void tickLimp() {
		Vec3d tip = limpAt();
		if (tip == null) {
			if (limpPart != null) {
				limpPart.discard();
				limpPart = null;
			}
			return;
		}
		Vec3d sh = shoulder();
		double d = Math.hypot(tip.x - sh.x, tip.z - sh.z);
		if (d > LATCH_REACH + 1.0) {
			double k = (LATCH_REACH + 1.0) / d;
			dataTracker.set(LIMP, new Vector3f((float) (sh.x + (tip.x - sh.x) * k), (float) tip.y, (float) (sh.z + (tip.z - sh.z) * k)));
		}
		placeLimp();
	}

	@Nullable
	private LivingEntity stuck() {
		int id = stuckTo();
		return id >= 0 && getWorld().getEntityById(id) instanceof LivingEntity e ? e : null;
	}

	private void latched(ServerWorld world, float t) {
		navigation.stop();
		setVelocity(0.0, getVelocity().y, 0.0);
		LivingEntity e = stuck();
		long now = world.getTime();
		if (e == null || !e.isAlive() || e.isRemoved() || e.squaredDistanceTo(this) > (LATCH_REACH + 5.0) * (LATCH_REACH + 5.0)) {
			letGo(now);
			return;
		}
		// Its face on whoever it drinks from.
		float to = yawTo(e.getPos());
		dataTracker.set(AIM, approach(aim(), to, 6.0F));
		face(aim());
		Vec3d sh = shoulder();
		Vec3d at = e.getPos().add(0.0, e.getHeight() * 0.55, 0.0);
		e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 6, 2, false, false));
		// A leash: pulled back in if they get further than the arm reaches.
		double d = at.distanceTo(sh);
		if (d > LATCH_REACH + 0.5) {
			Vec3d back = sh.subtract(at).normalize().multiply(Math.min(0.5, (d - LATCH_REACH) * 0.25));
			e.addVelocity(back.x, 0.0, back.z);
			e.velocityModified = true;
		}
		if (armPart == null || armPart.isRemoved()) {
			armPart = new LeechPartEntity(world, this, LeechPartEntity.ARM);
			world.spawnEntity(armPart);
		}
		placeArm(sh, at);
		if ((int) t % LATCH_PULSE == LATCH_PULSE - 1) {
			Vec3d v = e.getVelocity();
			e.timeUntilRegen = 0;
			if (e.damage(getDamageSources().mobAttack(this), LATCH_DRAIN)) {
				e.setVelocity(v);
				e.velocityModified = true;
				heal(LATCH_HEAL);
				playSound(LeechSounds.SUCK, 1.2F, 0.9F + random.nextFloat() * 0.2F);
				world.spawnParticles(BLOOD, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.05);
			}
		}
		if ((int) t % 3 == 0) {
			// blood running up the arm
			Vec3d mid = sh.lerp(at, 0.3 + 0.6 * random.nextDouble());
			world.spawnParticles(BLOOD, mid.x, mid.y, mid.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		if (t >= LATCH_MAX) {
			letGo(now);
		}
	}

	private void letGo(long now) {
		latchReadyAt = now + LATCH_COOLDOWN;
		end(now, GAP - 10);
	}

	private void unstick() {
		dataTracker.set(STUCK, -1);
		if (armPart != null) {
			armPart.discard();
			armPart = null;
		}
	}

	private void dropPart(boolean arm) {
		if (tonguePart != null) {
			tonguePart.discard();
			tonguePart = null;
		}
		if (tonguePart2 != null) {
			tonguePart2.discard();
			tonguePart2 = null;
		}
	}

	/** A blade on one of its parts (the server): the arm counts toward tearing it loose; the tongue counts more on it. */
	boolean partHit(LeechPartEntity part, DamageSource source, float amount) {
		if (!(getWorld() instanceof ServerWorld world) || !isAlive()) {
			return false;
		}
		if (part.kind() == LeechPartEntity.TONGUE) {
			if (state() != TONGUE) {
				return false;
			}
			boolean hurt = damage(source, amount * TONGUE_CUT_TAKES);
			if (hurt && !tongueCut) {
				tongueCut = true;
				playSound(LeechSounds.SHRIEK, 1.8F, 1.05F);
			}
			world.spawnParticles(BLOOD, part.getX(), part.getY() + 0.5, part.getZ(), 10, 0.15, 0.15, 0.15, 0.08);
			return hurt;
		}
		if (part == limpPart) {
			// the stunned arm lying near you: a cut there counts more
			world.spawnParticles(BLOOD, part.getX(), part.getY() + 0.5, part.getZ(), 6, 0.15, 0.15, 0.15, 0.1);
			return damage(source, amount * LIMP_TAKES);
		}
		if (state() != LATCHED) {
			return false;
		}
		armDamage += amount;
		if (amount >= CUT_HIT_MIN) {
			armHits++;
		}
		world.spawnParticles(BLOOD, part.getX(), part.getY() + 0.5, part.getZ(), 6, 0.15, 0.15, 0.15, 0.1);
		playSound(LeechSounds.SUCK, 0.8F, 1.6F);
		damage(source, amount);
		if (isAlive() && (armHits >= CUT_HITS || armDamage >= CUT_DAMAGE)) {
			tear(world);
		}
		return true;
	}

	/** Torn loose: the sucker drops off, the arm hangs stunned, it shrieks and reels back. */
	public void tear(ServerWorld world) {
		long now = world.getTime();
		LivingEntity e = stuck();
		Vec3d at = e != null ? e.getPos().add(0.0, e.getHeight() * 0.55, 0.0) : shoulder();
		dataTracker.set(ARM_STUN_UNTIL, now + ARM_STUN);
		// the arm drops where it held them, lying on the ground: cut it there
		Vec3d g = at.add(shoulder().subtract(at).multiply(1.0, 0.0, 1.0).normalize().multiply(0.6));
		dataTracker.set(LIMP, new Vector3f((float) g.x, (float) (e != null ? e.getY() : getY()), (float) g.z));
		if (limpPart != null) {
			limpPart.discard();
		}
		limpPart = new LeechPartEntity(world, this, LeechPartEntity.ARM);
		world.spawnEntity(limpPart);
		placeLimp();
		latchReadyAt = now + Math.max(ARM_STUN, LATCH_COOLDOWN);
		playSound(LeechSounds.TEAR, 1.6F, 1.0F);
		playSound(LeechSounds.SHRIEK, 2.0F, 0.85F);
		world.spawnParticles(BLOOD, at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0.2);
		begin(RECOIL);
	}

	private void trampleTell(ServerWorld world, float t, @Nullable PlayerEntity target) {
		still();
		if (t < TRAMPLE_AIM && target != null) {
			dataTracker.set(AIM, approach(aim(), yawTo(target.getPos()), 10.0F));
		}
		int ti = (int) t;
		if (ti == 4 || ti == 10 || ti == 16) {
			// a front sucker pawing the ground
			Vec3d paw = bodyPoint(aim(), ti == 10 ? -1.2 : 1.2, 0.0, 1.9);
			playSound(LeechSounds.PAW, 1.3F, 0.85F + random.nextFloat() * 0.2F);
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(BlockPos.ofFloored(paw).down())), paw.x, paw.y + 0.1, paw.z, 18, 0.3,
					0.05, 0.3, 0.15);
		}
		if (t >= TRAMPLE_TELL_TICKS) {
			struck.clear();
			trampleFrom = getPos();
			begin(TRAMPLE);
			playSound(LeechSounds.TRAMPLE, 1.6F, 1.0F);
		}
	}

	private void trample(ServerWorld world, float t) {
		navigation.stop();
		face(aim());
		Vec3d f = forward(aim());
		double run = trampleFrom == null ? 0.0 : Math.hypot(getX() - trampleFrom.x, getZ() - trampleFrom.z);
		if (t > 1.0F && horizontalCollision || run >= TRAMPLE_DISTANCE || t > TRAMPLE_DISTANCE / TRAMPLE_SPEED + 12) {
			begin(SKID);
			playSound(LeechSounds.SKID, 1.5F, 1.0F);
			return;
		}
		setVelocity(f.x * TRAMPLE_SPEED, getVelocity().y, f.z * TRAMPLE_SPEED);
		if ((int) t % 9 == 8) {
			playSound(LeechSounds.TRAMPLE, 1.4F, 0.95F + random.nextFloat() * 0.1F);
		}
		Vec3d side = new Vec3d(-f.z, 0.0, f.x);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(1.5, 0.0, 1.5), this::canHurt)) {
			// (what its body runs into: as wide as its body and its legs, its face out in front)
			Vec3d rel = e.getPos().subtract(getPos());
			double along = rel.dotProduct(f);
			if (Math.abs(rel.dotProduct(side)) > TRAMPLE_HALF_WIDTH + e.getWidth() * 0.5 || along < -1.5 || along > BODY_HALF_LENGTH + 0.5 + e.getWidth() * 0.5
					|| rel.y > 3.0 || rel.y < -1.5) {
				continue;
			}
			if (struck.add(e.getUuid()) && e.damage(getDamageSources().mobAttack(this), TRAMPLE_DAMAGE)) {
				double s = Math.signum(e.getPos().subtract(getPos()).dotProduct(side));
				Vec3d v = side.multiply((s == 0 ? 1 : s) * TRAMPLE_THROW).add(f.multiply(0.6)).add(0.0, 0.5, 0.0);
				e.addVelocity(v.x, v.y, v.z);
				e.velocityModified = true;
			}
		}
	}

	private void skid(ServerWorld world, float t) {
		navigation.stop();
		face(aim());
		Vec3d f = forward(aim());
		double k = Math.max(0.0, 1.0 - t / 10.0);
		setVelocity(f.x * TRAMPLE_SPEED * 0.6 * k, getVelocity().y, f.z * TRAMPLE_SPEED * 0.6 * k);
		if ((int) t < 10 && (int) t % 2 == 0) {
			Vec3d p = bodyPoint(aim(), 0.0, 0.0, 1.6);
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(BlockPos.ofFloored(p).down())), p.x, p.y + 0.1, p.z, 12, 1.0, 0.05,
					0.4, 0.2);
		}
		if (t >= SKID_TICKS) {
			end(world.getTime(), 12);
		}
	}

	/** Stops the hum for everyone near (it cuts off dead at a tell). */
	private void hush() {
		if (getWorld() instanceof ServerWorld world) {
			StopSoundS2CPacket stop = new StopSoundS2CPacket(LeechSounds.HUM.getId(), SoundCategory.HOSTILE);
			for (ServerPlayerEntity p : world.getPlayers(p -> p.squaredDistanceTo(this) < 48.0 * 48.0)) {
				p.networkHandler.sendPacket(stop);
			}
		}
	}

	// ---- Being hurt and dying.

	static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.45F, 0.02F, 0.03F), 0.7F);

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean hurt = super.damage(source, amount);
		if (hurt && getWorld() instanceof ServerWorld world) {
			Entity by = source.getAttacker();
			Vec3d c = getPos().add(0.0, 1.8, 0.0);
			Vec3d at = by == null ? c : c.add(by.getPos().subtract(getPos()).multiply(1.0, 0.0, 1.0).normalize().multiply(1.2));
			world.spawnParticles(BLOOD, at.x, at.y, at.z, 14, 0.25, 0.3, 0.25, 0.1);
		}
		return hurt;
	}

	private void dropLimp() {
		if (limpPart != null) {
			limpPart.discard();
			limpPart = null;
		}
	}

	@Override
	public void onDeath(DamageSource source) {
		unstick();
		dropPart(false);
		dropLimp();
		super.onDeath(source);
	}

	@Override
	public void remove(RemovalReason reason) {
		unstick();
		dropPart(false);
		dropLimp();
		super.remove(reason);
	}

	@Override
	protected void updatePostDeath() {
		deathTime++;
		navigation.stop();
		setVelocity(0.0, getVelocity().y, 0.0);
		if (deathTime >= DEATH_TICKS && !getWorld().isClient() && !isRemoved()) {
			if (getWorld() instanceof ServerWorld world) {
				world.spawnParticles(BLOOD, getX(), getY() + 1.0, getZ(), 20, 1.0, 0.4, 1.4, 0.05);
			}
			remove(RemovalReason.KILLED);
		}
	}

	@Override
	protected void dropLoot(DamageSource damageSource, boolean causedByPlayer) {
		dropStack(new ItemStack(Devils.LEECH_SUCKER), 1.0F);
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return isAlive();
	}

	@Override
	public void pushAwayFrom(Entity other) {
		if (other.noClip || other instanceof LeechDevilEntity || other instanceof LeechPartEntity || other.isConnectedThroughVehicle(this)) {
			return;
		}
		double dx = other.getX() - getX();
		double dz = other.getZ() - getZ();
		double d = Math.max(0.05, Math.hypot(dx, dz));
		double k = 0.12 * Math.min(1.0, (WIDTH * 0.5 + other.getWidth() * 0.5 - d + 0.3));
		if (k > 0.0) {
			other.addVelocity(dx / d * k, 0.0, dz / d * k);
		}
	}

	@Override
	protected void pushAway(Entity entity) {
	}

	@Override
	protected void playStepSound(BlockPos pos, net.minecraft.block.BlockState state) {
		// (Its suckers make their own sound as they come down: the client's.)
	}

	@Override
	protected SoundEvent getAmbientSound() {
		// (The hum is its own, when it has someone to circle.)
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return LeechSounds.GRUNT;
	}

	@Override
	protected void playHurtSound(DamageSource source) {
		// A short wet grunt, and not on every hit: the shriek is kept for an arm torn loose and the tongue cut.
		long now = getWorld().getTime();
		if (now - lastGruntAt >= GRUNT_EVERY) {
			lastGruntAt = now;
			super.playHurtSound(source);
		}
	}

	@Override
	protected SoundEvent getDeathSound() {
		return LeechSounds.DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 1.2F;
	}

	@Override
	public boolean tryAttack(Entity target) {
		return false;
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean canImmediatelyDespawn(double distance) {
		return false;
	}
}
