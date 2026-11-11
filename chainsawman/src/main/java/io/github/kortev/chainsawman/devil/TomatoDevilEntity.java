package io.github.kortev.chainsawman.devil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The Tomato Devil: a big red tomato covered in eyes, riding about 1.5 blocks up on eight human arms. Weak; a first-night fight. It keeps
 * coming at a walk (a player can outrun it) and has three moves, each shown well before it lands:
 * <ul>
 * <li>the slap ({@link #SLAP}): it rears up on its back four arms, the front four go up, then slap down over the ground in front of it;</li>
 * <li>the roll ({@link #ROLL_TELL}, {@link #ROLL}): it tucks its arms in and rocks back, then rolls straight ahead; into a wall or a tree it
 * lies on its back ({@link #ON_BACK}), arms waving, and every hit on it counts half again;</li>
 * <li>the seed spit ({@link #SPIT}): its mouth opens wide, then five seeds fly out in a fan.</li>
 * </ul>
 * Killed, it curls up and bursts ({@link #BURST_AT}): its carcass and its seeds ({@link TomatoSeedEntity}) fly out and stick in the ground;
 * left unburned, one of them brings it back, with one seed fewer each time.
 *
 * <p>Its animation is drawn from its state and the world time the state began (both sent to everyone); its arms' steps are the client's.
 */
public class TomatoDevilEntity extends HostileEntity {
	public static final float WIDTH = 2.4F;
	public static final float HEIGHT = 3.5F;
	public static final float MAX_HEALTH = 90.0F;
	/** Its walk: about 3.2 blocks a second, slower than a player's walk (4.3). */
	public static final double SPEED = 0.27;

	public static final int WALK = 0;
	public static final int SLAP = 1;
	public static final int ROLL_TELL = 2;
	public static final int ROLL = 3;
	public static final int ON_BACK = 4;
	public static final int GET_UP = 5;
	public static final int UNFURL = 6;
	public static final int SPIT = 7;
	/** Climbing out of a swollen seed. */
	public static final int EMERGE = 8;
	/** The hop: squatting low on all eight arms (the tell), springing up, landing on whoever hugs it. */
	public static final int HOP = 9;

	/** The slap: rearing (the tell), the arms whipping down, the hit, and settling. */
	public static final int SLAP_TELL = 16;
	public static final int SLAP_HIT = SLAP_TELL + 3;
	public static final int SLAP_END = SLAP_HIT + 14;
	public static final float SLAP_DAMAGE = 4.0F;
	/** How far in front of its middle the slap reaches (3 blocks past its body: further than a sword reaches it), and how wide either side. */
	public static final double SLAP_REACH = 4.2;
	public static final double SLAP_HALF_WIDTH = 1.7;
	/** The roll: tucking and rocking back (the tell; it turns to face you only in the first part), rolling, and how far and fast. */
	public static final int ROLL_TELL_TICKS = 18;
	public static final int ROLL_AIM = 10;
	public static final double ROLL_SPEED = 0.6;
	public static final double ROLL_DISTANCE = 10.0;
	public static final float ROLL_DAMAGE = 5.0F;
	/** On its back after a wall: how long, and how much more a hit counts then. */
	public static final int ON_BACK_TICKS = 40;
	public static final float ON_BACK_TAKES = 1.5F;
	public static final int GET_UP_TICKS = 14;
	public static final int UNFURL_TICKS = 12;
	/** The spit: the mouth opening (the tell; it turns to aim only in the first part), the five seeds in a fan, the mouth closing. */
	public static final int SPIT_TELL = 18;
	public static final int SPIT_AIM = 6;
	public static final int SPIT_END = SPIT_TELL + 14;
	public static final int SPIT_COUNT = 5;
	/**
	 * Wide gaps between the seeds: from 5 blocks a sidestep of about a block at a walk slips between two. (Round 1's 13 degrees left gaps
	 * narrower than a player: the only way out was past the whole fan, nearly 3 blocks in 0.7 s.)
	 */
	public static final float SPIT_FAN = 26.0F;
	/**
	 * The hop: who it answers (anyone within {@link #HUG} blocks of its skin for {@link #HUG_TICKS}), the squat (the tell), the leap, the
	 * landing's reach from its skin, its damage and how hard it throws (about 5 blocks back).
	 */
	public static final double HUG = 3.0;
	public static final int HUG_TICKS = 26;
	public static final int HOP_TELL = 18;
	public static final int HOP_AIR = 10;
	public static final int HOP_LAND = HOP_TELL + HOP_AIR;
	public static final int HOP_END = HOP_LAND + 14;
	public static final double HOP_REACH = 3.5;
	public static final float HOP_DAMAGE = 3.0F;
	public static final double HOP_THROW = 1.05;
	/** On first noticing a player: every eye on them, and it stands dead still this long before it moves. */
	public static final int NOTICE_STILL = 24;
	/** A comeback climbs out with this much of its health. */
	public static final float COMEBACK_HEALTH = 0.6F;
	public static final float SPIT_DAMAGE = 2.0F;
	public static final double SPIT_RANGE = 8.0;
	public static final int EMERGE_TICKS = 30;
	/** Dying: it curls up and swells for this long (a tell of its own: step back), then bursts. */
	public static final int BURST_AT = 30;
	/** How many seeds a devil from nothing scatters. */
	public static final int SEEDS = 5;
	/** How near a villager sees it as danger and runs (blocks; a zombie is 8). */
	public static final double VILLAGER_FLEE = 12.0;
	/** How far the seeds fly at most (blocks across the ground). */
	public static final double SEED_FLY = 6.0;

	private static final TrackedData<Integer> STATE = DataTracker.registerData(TomatoDevilEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Long> STATE_AT = DataTracker.registerData(TomatoDevilEntity.class, TrackedDataHandlerRegistry.LONG);
	/** The way a move is aimed (degrees, as a yaw): where the roll goes, where the fan of seeds is centred. */
	private static final TrackedData<Float> AIM = DataTracker.registerData(TomatoDevilEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** When it noticed someone (the world's time), or -1 while it has noticed nobody: its eyes wander till then. */
	private static final TrackedData<Long> NOTICED = DataTracker.registerData(TomatoDevilEntity.class, TrackedDataHandlerRegistry.LONG);

	/** How many seeds it bursts into when killed. */
	public int seeds = SEEDS;
	private long nextMoveAt;
	private long lastRollAt = -1000;
	private long lastSpitAt = -1000;
	private double rolled;
	private Vec3d rollFrom;
	private final Set<UUID> rolledOver = new HashSet<>();
	private boolean burst;
	@Nullable
	private Vec3d wanderTo;
	private long wanderAt;
	private float spitPitch;
	/** The last move and how many times running (no move three times running). */
	private int lastMove = -1;
	private int moveStreak;
	/** How long someone has hugged it (ticks, falling off when nobody does). */
	private int hugged;
	/** After a hop: the roll may follow straight away, cooldown or not. */
	private long rollMayFollowUntil = -1;
	private long lostSince = -1;

	/** The client's burst (the splat on the ground), set by the client. */
	public static Consumer<TomatoDevilEntity> clientBurst = e -> {
	};
	/** Drawn on the client: its arms' steps and the wounds it has taken (owned by the renderer). */
	public Object clientState;

	public TomatoDevilEntity(EntityType<? extends TomatoDevilEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 10;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, SLAP_DAMAGE).add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(STATE, WALK);
		builder.add(STATE_AT, 0L);
		builder.add(AIM, 0.0F);
		builder.add(NOTICED, -1L);
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

	/** Sets the way its next move is aimed (degrees, a yaw). */
	public void aimAt(float yaw) {
		dataTracker.set(AIM, yaw);
	}

	/** Starts a state now (the server). */
	public void begin(int state) {
		dataTracker.set(STATE, state);
		dataTracker.set(STATE_AT, getWorld().getTime());
	}

	/** When it noticed someone (the world's time), or -1. */
	public long noticedAt() {
		return dataTracker.get(NOTICED);
	}

	/** Has it notice someone now (or forget them): for the self tests' stills. */
	public void notice(boolean on) {
		dataTracker.set(NOTICED, on ? getWorld().getTime() : -1L);
	}

	/** Whether {@code move} may come now: no move three times running. */
	public boolean allowed(int move) {
		return !(move == lastMove && moveStreak >= 2);
	}

	/** The last move it made (for the tests). */
	public int lastMove() {
		return lastMove;
	}

	private void made(int move) {
		moveStreak = move == lastMove ? moveStreak + 1 : 1;
		lastMove = move;
		if (moveListener != null) {
			moveListener.accept(move);
		}
	}

	/** Told of every move it makes (the tests). */
	@Nullable
	public java.util.function.IntConsumer moveListener;

	/** How long someone has been hugging it (for the tests and the film). */
	public int hugged() {
		return hugged;
	}

	/** Every hit on it counts half again: it lies on its back. */
	public boolean exposed() {
		return state() == ON_BACK;
	}

	// ---- Spawning.

	// (The world never just puts one down: it comes out of the ground in front of someone, see TomatoArrivals. A spawn egg or /summon puts
	// one down at once.)

	/** Night by the clock (dusk to dawn). */
	public static boolean night(World world) {
		long t = Math.floorMod(world.getTimeOfDay(), 24000L);
		return t >= 13000L && t <= 23000L;
	}

	@Override
	public boolean canImmediatelyDespawn(double distance) {
		return !isPersistent() && super.canImmediatelyDespawn(distance);
	}

	// ---- The fight (the server).

	@Override
	protected void mobTick() {
		super.mobTick();
		ServerWorld world = (ServerWorld) getWorld();
		long now = world.getTime();
		int st = state();
		float t = stateTime(0.0F);
		PlayerEntity target = target();
		if (age < 3) {
			// Just come: it looks about a moment before its first move.
			nextMoveAt = Math.max(nextMoveAt, now + 40);
			lastRollAt = now - 50;
		}
		switch (st) {
			case WALK -> walk(world, now, target);
			case SLAP -> slap(world, t, target);
			case ROLL_TELL -> rollTell(world, t, target);
			case ROLL -> roll(world, t);
			case ON_BACK -> {
				still();
				if (t >= ON_BACK_TICKS) {
					begin(GET_UP);
				}
			}
			case GET_UP -> {
				still();
				if (t >= GET_UP_TICKS) {
					end(now, 14);
				}
			}
			case UNFURL -> {
				still();
				if (t >= UNFURL_TICKS) {
					end(now, 10);
				}
			}
			case SPIT -> spit(world, t, target);
			case HOP -> hop(world, t);
			case EMERGE -> {
				still();
				if (t >= EMERGE_TICKS) {
					end(now, 30);
				}
			}
			default -> begin(WALK);
		}
		if (now % 40 == 0) {
			Sniff.near(world, getPos());
		}
		// Who hugs it: anyone near its skin for long brings on the hop.
		if (target != null && Math.hypot(target.getX() - getX(), target.getZ() - getZ()) <= WIDTH * 0.5 + HUG) {
			hugged++;
		} else {
			hugged = Math.max(0, hugged - 1);
		}
		// Noticing: the first sight of someone; forgetting them once nobody has been about a while.
		if (target != null && walkTo == null) {
			lostSince = -1;
			if (noticedAt() < 0) {
				dataTracker.set(NOTICED, now);
				nextMoveAt = Math.max(nextMoveAt, now + 40);
				playSound(Devils.Sounds.CLICK, 0.8F, 0.8F);
			}
		} else if (noticedAt() >= 0) {
			if (lostSince < 0) {
				lostSince = now;
			} else if (now - lostSince > 200) {
				dataTracker.set(NOTICED, -1L);
			}
		}
	}

	/** The nearest player it can go for. */
	@Nullable
	private PlayerEntity target() {
		PlayerEntity p = getWorld().getClosestPlayer(getX(), getY(), getZ(), 24.0, EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR);
		return p != null && p.isAlive() ? p : null;
	}

	private void end(long now, int gap) {
		begin(WALK);
		nextMoveAt = now + gap;
	}

	/** Stands where it is, facing the way it was set to. */
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

	private Vec3d forward(float yaw) {
		float r = yaw * MathHelper.RADIANS_PER_DEGREE;
		return new Vec3d(-MathHelper.sin(r), 0.0, MathHelper.cos(r));
	}

	/** Somewhere it is told to walk to, before anything else (the self tests' takes of its walk); null for its own mind. */
	@Nullable
	public Vec3d walkTo;

	private void walk(ServerWorld world, long now, @Nullable PlayerEntity target) {
		if (walkTo != null) {
			if (now % 10 == 0 || navigation.isIdle()) {
				navigation.startMovingTo(walkTo.x, walkTo.y, walkTo.z, 1.0);
			}
			return;
		}
		if (target == null) {
			// Nobody about: it wanders, slowly.
			if (wanderTo == null || now - wanderAt > 140 || getPos().squaredDistanceTo(wanderTo) < 2.0) {
				wanderAt = now;
				wanderTo = getPos().add(random.nextDouble() * 16.0 - 8.0, 0.0, random.nextDouble() * 16.0 - 8.0);
				navigation.startMovingTo(wanderTo.x, wanderTo.y, wanderTo.z, 0.6);
			}
			return;
		}
		wanderTo = null;
		long noticed = noticedAt();
		if (noticed >= 0 && now - noticed < NOTICE_STILL) {
			// It has just seen you: every eye on you, and not a twitch more.
			navigation.stop();
			setVelocity(0.0, getVelocity().y, 0.0);
			return;
		}
		double dist = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
		float to = yawTo(target.getPos());
		float off = Math.abs(MathHelper.wrapDegrees(to - getBodyYaw()));
		if (now % 8 == 0 || navigation.isIdle()) {
			navigation.startMovingTo(target, 1.0);
		}
		getLookControl().lookAt(target, 30.0F, 30.0F);
		boolean sees = canSee(target);
		if (now <= rollMayFollowUntil && sees && dist >= 4.5 && dist <= ROLL_DISTANCE - 0.5 && allowed(ROLL_TELL)) {
			// Thrown off it by the hop, you are where the roll goes: it comes at once.
			rollMayFollowUntil = -1;
			startRoll(now, to);
			return;
		}
		if (now < nextMoveAt || now % 4 != 0) {
			return;
		}
		if (hugged >= HUG_TICKS && allowed(HOP)) {
			hugged = 0;
			navigation.stop();
			made(HOP);
			dataTracker.set(AIM, to);
			begin(HOP);
			playSound(Devils.Sounds.TUCK, 1.1F, 0.7F);
		} else if (dist <= SLAP_REACH + 0.3 && off < 55.0F && allowed(SLAP)) {
			made(SLAP);
			dataTracker.set(AIM, to);
			navigation.stop();
			begin(SLAP);
			playSound(Devils.Sounds.GURGLE, 1.0F, 0.8F);
		} else if (sees && dist >= 5.5 && dist <= ROLL_DISTANCE - 1.5 && now - lastRollAt > 110 && random.nextFloat() < 0.35F && allowed(ROLL_TELL)) {
			startRoll(now, to);
		} else if (sees && dist <= SPIT_RANGE + 0.5 && now - lastSpitAt > 90 && allowed(SPIT)
				&& (!allowed(SLAP) && dist <= SLAP_REACH + 0.3 && off < 55.0F || dist >= 3.5 && random.nextFloat() < 0.4F)) {
			// (Slapped at twice running, it spits in your face instead.)
			made(SPIT);
			dataTracker.set(AIM, to);
			navigation.stop();
			lastSpitAt = now;
			begin(SPIT);
			playSound(Devils.Sounds.GURGLE, 1.3F, 1.15F);
		}
	}

	private void startRoll(long now, float to) {
		made(ROLL_TELL);
		dataTracker.set(AIM, to);
		navigation.stop();
		lastRollAt = now;
		begin(ROLL_TELL);
		playSound(Devils.Sounds.TUCK, 1.2F, 1.0F);
	}

	/** The hop: down low on all eight arms, up, and down on whoever is near; thrown off it, they are where the roll goes. */
	private void hop(ServerWorld world, float t) {
		still();
		if ((int) t == HOP_TELL) {
			playSound(Devils.Sounds.ROLL, 1.0F, 1.4F);
		}
		if ((int) t == HOP_LAND) {
			playSound(Devils.Sounds.THUD, 1.8F, 0.75F);
			playSound(Devils.Sounds.SLAP, 1.4F, 0.7F);
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(getBlockPos().down())), getX(), getY() + 0.1, getZ(), 60,
					2.2, 0.05, 2.2, 0.25);
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(HOP_REACH + 1.0, 1.5, HOP_REACH + 1.0), this::canHurt)) {
				Vec3d rel = e.getPos().subtract(getPos()).multiply(1.0, 0.0, 1.0);
				double d = rel.length();
				if (d <= WIDTH * 0.5 + HOP_REACH + e.getWidth() * 0.5 && Math.abs(e.getY() - getY()) < 2.5) {
					Vec3d away = d < 0.05 ? forward(aim()) : rel.multiply(1.0 / d);
					if (e.damage(getDamageSources().mobAttack(this), HOP_DAMAGE)) {
						e.setVelocity(away.x * HOP_THROW, 0.48, away.z * HOP_THROW);
						e.velocityModified = true;
					}
				}
			}
			rollMayFollowUntil = world.getTime() + HOP_END - HOP_LAND + 30;
		}
		if (t >= HOP_END) {
			end(world.getTime(), 4);
		}
	}

	private void slap(ServerWorld world, float t, @Nullable PlayerEntity target) {
		still();
		if (target != null && t < SLAP_TELL - 6) {
			dataTracker.set(AIM, approach(aim(), yawTo(target.getPos()), 12.0F));
		}
		if ((int) t == SLAP_HIT) {
			playSound(Devils.Sounds.SLAP, 1.4F, 0.9F + random.nextFloat() * 0.2F);
			Vec3d f = forward(aim());
			Vec3d side = new Vec3d(-f.z, 0.0, f.x);
			Vec3d c = getPos().add(f.multiply(SLAP_REACH * 0.7));
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(BlockPos.ofFloored(c).down())), c.x, c.y + 0.1, c.z, 40, 1.2, 0.05, 1.2, 0.2);
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(SLAP_REACH + 1.0, 1.0, SLAP_REACH + 1.0), this::canHurt)) {
				Vec3d rel = e.getPos().subtract(getPos());
				double along = rel.dotProduct(f);
				double across = Math.abs(rel.dotProduct(side));
				if (along > -0.3 && along < SLAP_REACH + e.getWidth() * 0.5 && across < SLAP_HALF_WIDTH + e.getWidth() * 0.5 && rel.y < 2.5 && rel.y > -1.5) {
					if (e.damage(getDamageSources().mobAttack(this), SLAP_DAMAGE)) {
						push(e, f.multiply(0.7).add(0.0, 0.35, 0.0));
					}
				}
			}
		}
		if (t >= SLAP_END) {
			end(world.getTime(), 6);
		}
	}

	private void rollTell(ServerWorld world, float t, @Nullable PlayerEntity target) {
		still();
		if (target != null && t < ROLL_AIM) {
			dataTracker.set(AIM, approach(aim(), yawTo(target.getPos()), 15.0F));
		}
		if (t >= ROLL_TELL_TICKS) {
			rolled = 0.0;
			rollFrom = getPos();
			rolledOver.clear();
			begin(ROLL);
			playSound(Devils.Sounds.ROLL, 1.4F, 1.0F);
		}
	}

	private void roll(ServerWorld world, float t) {
		navigation.stop();
		face(aim());
		Vec3d f = forward(aim());
		if (t > 1.0F && horizontalCollision) {
			// Into a wall or a tree: on its back, arms waving.
			playSound(Devils.Sounds.THUD, 1.6F, 0.9F);
			world.spawnParticles(JUICE, getX() + f.x * 1.2, getY() + 1.0, getZ() + f.z * 1.2, 30, 0.4, 0.5, 0.4, 0.1);
			setVelocity(f.multiply(-0.15).add(0.0, 0.25, 0.0));
			begin(ON_BACK);
			return;
		}
		if (rollFrom != null) {
			rolled = Math.hypot(getX() - rollFrom.x, getZ() - rollFrom.z);
		}
		if (rolled >= ROLL_DISTANCE || t > ROLL_DISTANCE / ROLL_SPEED + 12) {
			setVelocity(f.multiply(0.1).add(0.0, getVelocity().y, 0.0));
			begin(UNFURL);
			return;
		}
		setVelocity(f.x * ROLL_SPEED, getVelocity().y, f.z * ROLL_SPEED);
		if ((int) t % 6 == 0) {
			playSound(Devils.Sounds.ROLL, 1.0F, 0.9F + random.nextFloat() * 0.2F);
		}
		Vec3d side = new Vec3d(-f.z, 0.0, f.x);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(0.3, 0.0, 0.3), this::canHurt)) {
			if (rolledOver.add(e.getUuid()) && e.damage(getDamageSources().mobAttack(this), ROLL_DAMAGE)) {
				// Knocked aside, off its line.
				double s = Math.signum(e.getPos().subtract(getPos()).dotProduct(side));
				push(e, side.multiply((s == 0 ? 1 : s) * 1.1).add(f.multiply(0.35)).add(0.0, 0.42, 0.0));
			}
		}
	}

	private void spit(ServerWorld world, float t, @Nullable PlayerEntity target) {
		still();
		if (target != null && t < SPIT_AIM) {
			dataTracker.set(AIM, approach(aim(), yawTo(target.getPos()), 20.0F));
			Vec3d mouth = mouth();
			double dy = target.getY() + 1.0 - mouth.y;
			double dh = Math.max(1.0, Math.hypot(target.getX() - mouth.x, target.getZ() - mouth.z));
			spitPitch = MathHelper.clamp((float) Math.toDegrees(Math.atan2(dy, dh)), -25.0F, 15.0F);
		}
		if ((int) t == SPIT_TELL) {
			playSound(Devils.Sounds.SPIT, 1.3F, 1.0F);
			Vec3d mouth = mouth();
			for (int i = 0; i < SPIT_COUNT; i++) {
				float yaw = aim() + (i - (SPIT_COUNT - 1) / 2.0F) * SPIT_FAN;
				float p = spitPitch * MathHelper.RADIANS_PER_DEGREE;
				Vec3d d = forward(yaw).multiply(MathHelper.cos(p)).add(0.0, MathHelper.sin(p) + 0.05, 0.0);
				TomatoSpitEntity s = new TomatoSpitEntity(Devils.TOMATO_SPIT, world);
				s.shoot(this, mouth, d.multiply(TomatoSpitEntity.SPEED));
				world.spawnEntity(s);
			}
			world.spawnParticles(JUICE, mouth.x, mouth.y, mouth.z, 16, 0.2, 0.2, 0.2, 0.15);
		}
		if (t >= SPIT_END) {
			end(world.getTime(), 12);
		}
	}

	/** Where its mouth is (the world), facing its aim. */
	public Vec3d mouth() {
		Vec3d f = forward(aim());
		return getPos().add(f.multiply(1.25)).add(0.0, 2.4, 0.0);
	}

	private static float approach(float from, float to, float step) {
		return from + MathHelper.clamp(MathHelper.wrapDegrees(to - from), -step, step);
	}

	private boolean canHurt(LivingEntity e) {
		return e != this && e.isAlive() && !(e instanceof TomatoDevilEntity) && !spares(e) && !e.isSpectator() && !(e instanceof PlayerEntity p && p.isCreative());
	}

	/** Who its blows and seeds pass over: villagers (they run from it instead) and iron golems (who leave it alone in turn). */
	public static boolean spares(Entity e) {
		return e instanceof net.minecraft.entity.passive.MerchantEntity || e instanceof net.minecraft.entity.passive.IronGolemEntity;
	}

	private static void push(LivingEntity e, Vec3d v) {
		e.addVelocity(v.x, v.y, v.z);
		e.velocityModified = true;
	}

	// ---- Being hurt.

	static final DustParticleEffect JUICE = new DustParticleEffect(new Vector3f(0.82F, 0.08F, 0.04F), 1.1F);
	static final DustParticleEffect PULP = new DustParticleEffect(new Vector3f(0.93F, 0.75F, 0.42F), 0.9F);

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (exposed()) {
			amount *= ON_BACK_TAKES;
		}
		boolean hurt = super.damage(source, amount);
		if (hurt && getWorld() instanceof ServerWorld world) {
			// Juice sprays out where it was cut, toward whoever cut it.
			Entity by = source.getAttacker();
			Vec3d c = getBoundingBox().getCenter().add(0.0, 0.6, 0.0);
			Vec3d at = by == null ? c : c.add(by.getPos().subtract(getPos()).multiply(1.0, 0.0, 1.0).normalize().multiply(1.1));
			world.spawnParticles(JUICE, at.x, at.y, at.z, 18, 0.25, 0.3, 0.25, 0.12);
			world.spawnParticles(PULP, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.08);
		}
		return hurt;
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean isPushable() {
		// (So that what walks into it is pushed out of it; it is not moved itself: see pushAwayFrom.)
		return isAlive();
	}

	@Override
	public void pushAwayFrom(Entity other) {
		// Whatever is inside its body is shoved out sideways; the devil does not budge.
		if (other.noClip || other instanceof TomatoDevilEntity || other.isConnectedThroughVehicle(this)) {
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
		// (Its hands make their own sound as they come down: the client's.)
	}

	@Override
	protected SoundEvent getAmbientSound() {
		// (Quiet is scarier: a slow wet breath, not a roar.)
		return Devils.Sounds.BREATH;
	}

	@Override
	public int getMinAmbientSoundDelay() {
		return 160;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Devils.Sounds.HURT;
	}

	@Nullable
	@Override
	protected SoundEvent getDeathSound() {
		return null;
	}

	@Override
	protected float getSoundVolume() {
		return 1.2F;
	}

	// ---- Dying: curled up, swelling, then the burst.

	@Override
	protected void updatePostDeath() {
		deathTime++;
		navigation.stop();
		setVelocity(0.0, getVelocity().y, 0.0);
		if (deathTime == BURST_AT) {
			if (getWorld() instanceof ServerWorld world) {
				burst(world);
			} else {
				clientBurst.accept(this);
			}
		}
		if (deathTime >= BURST_AT + 4 && !getWorld().isClient() && !isRemoved()) {
			remove(RemovalReason.KILLED);
		}
	}

	/** Bursts: juice and pulp everywhere, its carcass, and its seeds flying out to stick in the ground. */
	public void burst(ServerWorld world) {
		if (burst) {
			return;
		}
		burst = true;
		Vec3d c = getPos().add(0.0, 2.2, 0.0);
		world.playSound(null, c.x, c.y, c.z, Devils.Sounds.BURST, SoundCategory.HOSTILE, 2.0F, 1.0F);
		// (The skin's big chunks flying, landing and fading are the client's: here only a third of round 1's spray, and no red squares.)
		world.spawnParticles(new DustParticleEffect(new Vector3f(0.62F, 0.04F, 0.03F), 1.8F), c.x, c.y, c.z, 14, 0.8, 0.6, 0.8, 0.3);
		world.spawnParticles(JUICE, c.x, c.y, c.z, 30, 1.2, 0.8, 1.2, 0.3);
		world.spawnParticles(PULP, c.x, c.y, c.z, 20, 1.0, 0.6, 1.0, 0.25);
		dropStack(new ItemStack(Devils.TOMATO_CARCASS), 1.0F);
		scatter(world, c, seeds, getUuid());
	}

	/** {@code count} seeds of one brood flying out from {@code from}, each to come back with one seed fewer. */
	public static List<TomatoSeedEntity> scatter(ServerWorld world, Vec3d from, int count, UUID brood) {
		List<TomatoSeedEntity> out = new java.util.ArrayList<>();
		Random r = world.getRandom();
		double start = r.nextDouble() * Math.PI * 2.0;
		for (int i = 0; i < count; i++) {
			double a = start + i * Math.PI * 2.0 / Math.max(1, count) + (r.nextDouble() - 0.5) * 0.7;
			// Up, then down to the ground 3 to 6 blocks off: in the air about 20 ticks.
			double h = (3.0 + r.nextDouble() * (SEED_FLY - 3.0)) / TomatoSeedEntity.FLIGHT;
			TomatoSeedEntity s = new TomatoSeedEntity(Devils.TOMATO_SEED, world);
			s.refreshPositionAndAngles(from.x, from.y, from.z, (float) Math.toDegrees(-a), 0.0F);
			s.setVelocity(Math.cos(a) * h, TomatoSeedEntity.LAUNCH, Math.sin(a) * h);
			s.brood = brood;
			s.next = Math.max(0, count - 1);
			s.index = i;
			world.spawnEntity(s);
			out.add(s);
		}
		return out;
	}

	/** Tells everyone near that the seeds are in the ground. */
	static void tellSeeds(ServerWorld world, Vec3d at) {
		for (ServerPlayerEntity p : world.getPlayers(p -> p.squaredDistanceTo(at) < 48.0 * 48.0)) {
			p.sendMessage(Text.translatable("message.chainsawman.tomato_seeds").formatted(Formatting.RED), true);
		}
	}

	@Override
	public boolean isFireImmune() {
		return false;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Seeds", seeds);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Seeds")) {
			seeds = nbt.getInt("Seeds");
		}
	}

	@Override
	public boolean tryAttack(Entity target) {
		// (Its hits are its moves, not a touch.)
		return false;
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean canHit() {
		return !isRemoved() && deathTime < BURST_AT;
	}

	@Override
	protected void dropLoot(DamageSource damageSource, boolean causedByPlayer) {
		// (Its carcass comes out when it bursts.)
	}
}
