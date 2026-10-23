package io.github.kortev.chainsawman;

import io.github.kortev.chainsawman.form.Beam;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Beam, the shark fiend, in his big shark form: Chainsaw Man's ult ({@link Beam}). In the Reze arc he stands on four long
 * thin legs like a crab's, a rider standing on his back, and swims through the ground and through buildings as if they were
 * water; here he is a big body of about two blocks that stands on the ground when he is still and sinks into it to swim when
 * he moves, tearing through what is in his way. He is nobody's mount in the game's sense (nothing is steered by the rider on
 * the client): the server moves him every tick from what the rider asks of him, so {@link #getControllingPassenger} is
 * nobody. He is not saved, and what he does is {@code Beam}'s to say; this holds where he is, what the clients are to show of
 * him (a bite, a leap, a charge, a dive, a kill, as the world time it began; whether he is swimming or off the ground) and
 * the rider's footing on his back. The client also gives him his life: his tail wagging, his eyes darting, his jaw
 * chattering, his head turning to the nearest mob.
 *
 * <p>His hitbox ({@link #WIDTH} by {@link #HEIGHT}) is the body between the legs; the model draws his back {@link #BACK}
 * blocks above where he is (the ground, under his feet when he stands), and the rider stands on it. Swimming, he is
 * {@code Beam.SINK} blocks lower: only his back, his spikes and the rider are above the ground.
 */
public class BeamEntity extends Entity {
	/** His hitbox (blocks): wide and tall enough that he does not squeeze through what a horse could not. */
	public static final float WIDTH = 2.0F;
	public static final float HEIGHT = 2.4F;
	/** How far above his feet the top of his back stands (blocks): the length of his legs and the depth of his body. */
	public static final float BACK = 62.0F / 16.0F;
	/** Where the rider stands: on his back just behind the first spike, this far ahead of his middle (blocks). */
	public static final double SEAT_AHEAD = 0.95;
	/** One full cycle of his gait covers this much ground (blocks): the gait's phase advances with the distance he goes. */
	public static final float STRIDE = 8.0F;
	/** How many ticks the clients are told of his place ahead, so they glide him there between the server's word. */
	private static final int GLIDE = 2;
	/** How far off (blocks) he notices a mob to look at, when he is idle. */
	private static final double NOTICE = 16.0;
	/** How far his head can turn from straight ahead (radians), round and up or down. */
	private static final float LOOK_YAW = 1.0F;
	private static final float LOOK_PITCH = 0.45F;
	/** How long a chatter of his jaw lasts (ticks). */
	public static final int CHATTER = 14;

	/** The world time (as an int) of his last bite, his leap, his charge and the start of his dive; -1 for never. */
	private static final TrackedData<Integer> BITE_AT = DataTracker.registerData(BeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> LEAP_AT = DataTracker.registerData(BeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> DIVE_AT = DataTracker.registerData(BeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> CHARGE_AT = DataTracker.registerData(BeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** When he last came down from a breach, and when he last killed something (world time); -1 for never. */
	private static final TrackedData<Integer> LAND_AT = DataTracker.registerData(BeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> HAPPY_AT = DataTracker.registerData(BeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Whether he is off the ground (leaping or falling): the clients draw his legs drawn up. */
	private static final TrackedData<Boolean> AIRBORNE = DataTracker.registerData(BeamEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** How far he has sunk into swimming (0 standing on his legs, 1 swimming in the ground): his legs fold up with it. */
	private static final TrackedData<Float> SWIM = DataTracker.registerData(BeamEntity.class, TrackedDataHandlerRegistry.FLOAT);

	/** Whose Beam he is (the server's only), and what his run is doing (the server's only). */
	private UUID owner;
	private final Beam.Ride ride = new Beam.Ride();
	/** Where the clients are gliding him to: the last word of the server, and how many ticks are left to get there. */
	private double glideX;
	private double glideY;
	private double glideZ;
	private float glideYaw;
	private float glidePitch;
	private int glideSteps;
	/** The client's: the phase of his gait (it advances with the ground he covers), the last tick's, the phase of the clock (his tail's sway), and how fast he is going (0 to 1 of his top speed). */
	private float walk;
	private float prevWalk;
	private float time;
	private float prevTime;
	private float pace;
	/** The client's: how far his legs are drawn up for the air (0 to 1), smoothed, and the last tick's. */
	private float air;
	private float prevAir;
	/** The client's: how far he has sunk into swimming (0 to 1), smoothed, and the last tick's. */
	private float swim;
	private float prevSwim;
	/** The client's: the phase of his tail's wag when he is idle, and how fast he is turning (degrees a tick, smoothed). */
	private float wag;
	private float prevWag;
	private float turn;
	/** The client's: where his head looks, from straight ahead (radians), the last tick's, and the id of what it looks at. */
	private float lookYaw;
	private float lookPitch;
	private float prevLookYaw;
	private float prevLookPitch;
	private int lookAt = -1;
	private int lookScan;
	/** The client's: his eyes darting (round and up, for each of three, radians), the last tick's, where each is going and when they next dart. */
	private final float[] eyeYaw = new float[3];
	private final float[] eyePitch = new float[3];
	private final float[] prevEyeYaw = new float[3];
	private final float[] prevEyePitch = new float[3];
	private final float[] eyeToYaw = new float[3];
	private final float[] eyeToPitch = new float[3];
	private int nextDart = 10;
	/** The client's: when (his age) his jaw last began to chatter, and when it next will. */
	private int chatterAt = -1000;
	private int nextChatter = 60;

	public BeamEntity(EntityType<? extends BeamEntity> type, World world) {
		super(type, world);
		// What puts him where he is, on the ground or swimming in it, is Beam's to do: the game's collision and gravity are not his.
		this.setNoGravity(true);
		this.noClip = false;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(BITE_AT, -1);
		builder.add(LEAP_AT, -1);
		builder.add(DIVE_AT, -1);
		builder.add(CHARGE_AT, -1);
		builder.add(LAND_AT, -1);
		builder.add(HAPPY_AT, -1);
		builder.add(AIRBORNE, false);
		builder.add(SWIM, 0.0F);
	}

	/** The world time (as an int) that {@code tracked} was last set, or -1. */
	private int stamp(TrackedData<Integer> tracked) {
		return dataTracker.get(tracked);
	}

	/** How long since his last bite, in ticks (a fraction with the tick's {@code delta}); a long time if never. */
	public float sinceBite(float delta) {
		return since(BITE_AT, delta);
	}

	/** How long since his leap began, in ticks; a long time if never. */
	public float sinceLeap(float delta) {
		return since(LEAP_AT, delta);
	}

	/** How long since his charge began, in ticks; a long time if never. */
	public float sinceCharge(float delta) {
		return since(CHARGE_AT, delta);
	}

	/** How long since he last came down from a breach, in ticks; a long time if never. */
	public float sinceLand(float delta) {
		return since(LAND_AT, delta);
	}

	/** How long since he last killed something (he is pleased), in ticks; a long time if never. */
	public float sinceHappy(float delta) {
		return since(HAPPY_AT, delta);
	}

	/** How long since he began to dive, in ticks; a long time if he has not. */
	public float sinceDive(float delta) {
		return since(DIVE_AT, delta);
	}

	private float since(TrackedData<Integer> tracked, float delta) {
		int at = stamp(tracked);
		return at < 0 ? 10000.0F : (int) getWorld().getTime() - at + delta;
	}

	/** Whether he is diving, on his way out of sight. */
	public boolean diving() {
		return stamp(DIVE_AT) >= 0;
	}

	public void bit() {
		dataTracker.set(BITE_AT, (int) getWorld().getTime());
	}

	public void leapt() {
		dataTracker.set(LEAP_AT, (int) getWorld().getTime());
	}

	public void dived() {
		dataTracker.set(DIVE_AT, (int) getWorld().getTime());
	}

	public void charged() {
		dataTracker.set(CHARGE_AT, (int) getWorld().getTime());
	}

	public void landed() {
		dataTracker.set(LAND_AT, (int) getWorld().getTime());
	}

	public void pleased() {
		dataTracker.set(HAPPY_AT, (int) getWorld().getTime());
	}

	/** How far he has sunk into swimming (the server's word, 0 to 1). */
	public float swimming() {
		return dataTracker.get(SWIM);
	}

	public void setSwimming(float swimming) {
		// (To a hundredth: the clients are not told of less.)
		float rounded = Math.round(swimming * 100.0F) / 100.0F;
		if (rounded != swimming()) {
			dataTracker.set(SWIM, rounded);
		}
	}

	/** Whether he is off the ground (the server's word). */
	public boolean airborne() {
		return dataTracker.get(AIRBORNE);
	}

	public void setAirborne(boolean airborne) {
		if (airborne != airborne()) {
			dataTracker.set(AIRBORNE, airborne);
		}
	}

	@Nullable
	public UUID owner() {
		return owner;
	}

	public void setOwner(UUID owner) {
		this.owner = owner;
	}

	/** What his run is doing now (the server's). */
	public Beam.Ride ride() {
		return ride;
	}

	/** Whoever is on his back, if it is his owner. */
	@Nullable
	public ServerPlayerEntity rider() {
		return getFirstPassenger() instanceof ServerPlayerEntity player && player.getUuid().equals(owner) ? player : null;
	}

	/** The phase of his gait, {@code delta} of the way through the tick (the client's). */
	public float walk(float delta) {
		return MathHelper.lerp(delta, prevWalk, walk);
	}

	/** The phase of the clock, for the sway of his tail (the client's). */
	public float time(float delta) {
		return MathHelper.lerp(delta, prevTime, time);
	}

	/** How far his legs are drawn up for the air (0 to 1), {@code delta} of the way through the tick (the client's). */
	public float air(float delta) {
		return MathHelper.lerp(delta, prevAir, air);
	}

	/** How fast he is going now, 0 to 1 of his top speed (the client's). */
	public float pace() {
		return pace;
	}

	/** How far he is sunk into swimming, 0 (on his legs) to 1 (swimming in the ground), {@code delta} of the way through the tick (the client's). */
	public float swim(float delta) {
		return MathHelper.lerp(delta, prevSwim, swim);
	}

	/** The phase of his idle tail wag (the client's). */
	public float wag(float delta) {
		return MathHelper.lerp(delta, prevWag, wag);
	}

	/** How fast he is turning, degrees a tick, round to the right (positive) or left (the client's). */
	public float turn() {
		return turn;
	}

	/** Where his head looks, from straight ahead (radians): round (positive: to his right) and up (positive: up). */
	public float lookYaw(float delta) {
		return MathHelper.lerp(delta, prevLookYaw, lookYaw);
	}

	public float lookPitch(float delta) {
		return MathHelper.lerp(delta, prevLookPitch, lookPitch);
	}

	/** Where eye {@code i} (0 the front one, the biggest, to 2 the rear one) looks, from straight out of his side (radians). */
	public float eyeYaw(int i, float delta) {
		return MathHelper.lerp(delta, prevEyeYaw[i], eyeYaw[i]);
	}

	public float eyePitch(int i, float delta) {
		return MathHelper.lerp(delta, prevEyePitch[i], eyePitch[i]);
	}

	/** How far through a chatter of his jaw he is, 0 to 1 (the client's); -1 when it is not chattering. */
	public float chattering(float delta) {
		float t = age + delta - chatterAt;
		return t >= 0.0F && t < CHATTER ? t / CHATTER : -1.0F;
	}

	/** How many ticks old he is on the client, with the part of a tick: the summoning's joy runs on it. */
	public float lived(float delta) {
		return age + delta;
	}

	/** The way he faces, flat. */
	public Vec3d heading() {
		return Vec3d.fromPolar(0.0F, getYaw());
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) {
			glide();
			double moved = Math.hypot(getX() - prevX, getZ() - prevZ);
			pace += (MathHelper.clamp((float) (moved / Beam.SPEED), 0.0F, 1.0F) - pace) * 0.3F;
			prevWalk = walk;
			walk += (float) (moved / STRIDE * (2.0F * MathHelper.PI));
			prevTime = time;
			time += 0.15F + 0.5F * pace;
			prevAir = air;
			air += ((airborne() ? 1.0F : 0.0F) - air) * 0.4F;
			prevSwim = swim;
			swim += (swimming() - swim) * 0.35F;
			prevWag = wag;
			wag += 0.62F * (1.0F - swim);
			turn += (MathHelper.wrapDegrees(getYaw() - prevYaw) - turn) * 0.3F;
			look();
		} else {
			Beam.tick(this);
		}
	}

	/**
	 * His face this tick (the client's): when he stands about his head turns to the nearest mob within {@link #NOTICE} blocks
	 * and his eyes dart about; his jaw chatters now and then. Swimming, he looks where he is going.
	 */
	private void look() {
		prevLookYaw = lookYaw;
		prevLookPitch = lookPitch;
		float wantYaw = 0.0F;
		float wantPitch = 0.0F;
		float idle = MathHelper.clamp(1.0F - swim * 1.4F - pace, 0.0F, 1.0F);
		if (idle > 0.0F && !diving() && age > 28) {
			if (--lookScan <= 0) {
				lookScan = 8;
				lookAt = -1;
				double best = NOTICE * NOTICE;
				List<MobEntity> near = getWorld().getEntitiesByClass(MobEntity.class, getBoundingBox().expand(NOTICE), m -> m.isAlive());
				for (MobEntity mob : near) {
					double d = mob.squaredDistanceTo(this);
					if (d < best) {
						best = d;
						lookAt = mob.getId();
					}
				}
			}
			Entity target = lookAt >= 0 ? getWorld().getEntityById(lookAt) : null;
			if (target != null && target.isAlive()) {
				Vec3d heading = heading();
				Vec3d head = getPos().add(heading.multiply(1.9)).add(0.0, 1.9, 0.0);
				Vec3d to = target.getPos().add(0.0, target.getHeight() * 0.7, 0.0).subtract(head);
				float away = (float) Math.toDegrees(Math.atan2(to.x, -to.z) - Math.atan2(heading.x, -heading.z));
				// (Positive: to his right, as a part's yaw is.)
				wantYaw = MathHelper.clamp(MathHelper.wrapDegrees(away) * MathHelper.RADIANS_PER_DEGREE, -LOOK_YAW, LOOK_YAW) * idle;
				wantPitch = MathHelper.clamp((float) Math.atan2(to.y, Math.hypot(to.x, to.z)), -LOOK_PITCH, LOOK_PITCH) * idle * 0.8F;
			} else {
				// Nothing to look at: a slow glance about.
				wantYaw = 0.3F * MathHelper.sin(age * 0.045F + getId()) * idle;
				wantPitch = 0.08F * MathHelper.sin(age * 0.07F + 2.0F) * idle;
			}
		}
		lookYaw += (wantYaw - lookYaw) * 0.14F;
		lookPitch += (wantPitch - lookPitch) * 0.14F;
		// His eyes dart: all three to nearly the same place, a snap, now and then.
		for (int i = 0; i < 3; i++) {
			prevEyeYaw[i] = eyeYaw[i];
			prevEyePitch[i] = eyePitch[i];
			eyeYaw[i] += (eyeToYaw[i] - eyeYaw[i]) * 0.55F;
			eyePitch[i] += (eyeToPitch[i] - eyePitch[i]) * 0.55F;
		}
		if (--nextDart <= 0) {
			float wild = 0.5F + 0.5F * idle;
			float yaw = (random.nextFloat() - 0.5F) * 1.5F * wild;
			float pitch = (random.nextFloat() - 0.5F) * 0.9F * wild;
			for (int i = 0; i < 3; i++) {
				eyeToYaw[i] = yaw + (random.nextFloat() - 0.5F) * 0.3F;
				eyeToPitch[i] = pitch + (random.nextFloat() - 0.5F) * 0.25F;
			}
			nextDart = 4 + random.nextInt(14);
		}
		// His jaw chatters, a quick clatter of the teeth, every few seconds when he stands about.
		if (idle > 0.8F && age >= nextChatter) {
			chatterAt = age;
			nextChatter = age + CHATTER + 35 + random.nextInt(60);
			getWorld().playSound(getX(), getY() + 1.5, getZ(), ChainsawSounds.BEAM_CHATTER, net.minecraft.sound.SoundCategory.PLAYERS, 0.8F,
					0.9F + random.nextFloat() * 0.3F, false);
		}
	}

	/** Glides here to where the server last put him, a step a tick (as a boat does), so he moves smoothly. */
	private void glide() {
		if (glideSteps <= 0) {
			return;
		}
		double x = getX() + (glideX - getX()) / glideSteps;
		double y = getY() + (glideY - getY()) / glideSteps;
		double z = getZ() + (glideZ - getZ()) / glideSteps;
		float yaw = getYaw() + MathHelper.wrapDegrees(glideYaw - getYaw()) / glideSteps;
		float pitch = getPitch() + (glidePitch - getPitch()) / glideSteps;
		glideSteps--;
		setPosition(x, y, z);
		setRotation(yaw, pitch);
	}

	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
		glideX = x;
		glideY = y;
		glideZ = z;
		glideYaw = yaw;
		glidePitch = pitch;
		glideSteps = GLIDE;
	}

	@Override
	public void onRemoved() {
		Beam.gone(this);
		super.onRemoved();
	}

	/** The rider stands on his back, a little ahead of his middle, their feet on the top of it (not sat: the game's seat is not used). */
	@Override
	protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater updater) {
		if (!hasPassenger(passenger)) {
			return;
		}
		Vec3d ahead = heading().multiply(SEAT_AHEAD);
		updater.accept(passenger, getX() + ahead.x, getY() + BACK - 0.05, getZ() + ahead.z);
	}

	/** Off his back: down on the ground where he is (he may be swimming a little under it). */
	@Override
	public Vec3d updatePassengerForDismount(LivingEntity passenger) {
		return new Vec3d(getX(), Math.max(getY(), ride.surface(getY())) + 0.05, getZ());
	}

	/** The game steers nothing of him: the server moves him. */
	@Nullable
	@Override
	public LivingEntity getControllingPassenger() {
		return null;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().isEmpty();
	}

	/** Under water the rider stays on: he wades. */
	@Override
	public boolean shouldDismountUnderwater() {
		return false;
	}

	@Override
	protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
		return new Vec3d(0.0, BACK, 0.0);
	}

	/** Nothing hurts him but the void: he is a fiend, not a mount to be killed. */
	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return !source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean isPushedByFluids() {
		return false;
	}

	/** He is not saved: gone if the chunk unloads or the world closes. */
	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	/** No-one but his owner is carried: using him does nothing. */
	@Override
	public net.minecraft.util.ActionResult interact(PlayerEntity player, net.minecraft.util.Hand hand) {
		return net.minecraft.util.ActionResult.PASS;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
