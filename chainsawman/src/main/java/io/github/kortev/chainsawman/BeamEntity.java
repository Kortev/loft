package io.github.kortev.chainsawman;

import io.github.kortev.chainsawman.form.Beam;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Beam, the shark fiend, in his big shark form: Chainsaw Man's ult ({@link Beam}). In the Reze arc he stands and runs on
 * four long thin legs like a crab's, a rider standing on his back; here he is a big body of about two blocks that walks on
 * the ground and steps up onto what is in his way (a block or two), the game's own collision doing the rest. He is nobody's
 * mount in the game's sense (nothing is steered by the rider on the client): the server moves him every tick from what the
 * rider asks of him, so {@link #getControllingPassenger} is nobody. He is not saved, and what he does is {@code Beam}'s to
 * say; this holds where he is, what the clients are to show of him (a bite, a leap, a charge, a dive, as the world time it
 * began; whether he is in the air) and the rider's footing on his back.
 *
 * <p>His hitbox ({@link #WIDTH} by {@link #HEIGHT}) is the body between the legs; the model draws his back {@link #BACK}
 * blocks above where he is (the ground, under his feet), and the rider stands on it.
 */
public class BeamEntity extends Entity {
	/** His hitbox (blocks): wide and tall enough that he does not squeeze through what a horse could not. */
	public static final float WIDTH = 2.0F;
	public static final float HEIGHT = 2.4F;
	/** How far above his feet the top of his back stands (blocks): the length of his legs and the depth of his body. */
	public static final float BACK = 48.0F / 16.0F;
	/** Where the rider stands: on the middle of his back, this far ahead of his middle (blocks; behind it). */
	public static final double SEAT_AHEAD = -0.5;
	/** One full cycle of his gait covers this much ground (blocks): the gait's phase advances with the distance he goes. */
	public static final float STRIDE = 5.5F;
	/** How high he steps up in one go (blocks): a block or two of what is in his way. */
	private static final float STEP = 2.0F;
	/** How many ticks the clients are told of his place ahead, so they glide him there between the server's word. */
	private static final int GLIDE = 3;

	/** The world time (as an int) of his last bite, his leap, his charge and the start of his dive; -1 for never. */
	private static final TrackedData<Integer> BITE_AT = DataTracker.registerData(BeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> LEAP_AT = DataTracker.registerData(BeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> DIVE_AT = DataTracker.registerData(BeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> CHARGE_AT = DataTracker.registerData(BeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Whether he is off the ground (leaping or falling): the clients draw his legs drawn up. */
	private static final TrackedData<Boolean> AIRBORNE = DataTracker.registerData(BeamEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

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

	public BeamEntity(EntityType<? extends BeamEntity> type, World world) {
		super(type, world);
		// He walks on the ground (the game's collision stops him at a wall, and he steps up what he can); what pulls him down
		// is Beam's to do, so he has no gravity of his own.
		this.setNoGravity(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(BITE_AT, -1);
		builder.add(LEAP_AT, -1);
		builder.add(DIVE_AT, -1);
		builder.add(CHARGE_AT, -1);
		builder.add(AIRBORNE, false);
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
		} else {
			Beam.tick(this);
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

	/** Off his back: down on the ground where he is. */
	@Override
	public Vec3d updatePassengerForDismount(LivingEntity passenger) {
		return new Vec3d(getX(), getY() + 0.05, getZ());
	}

	/** He steps up what is in his way, a block or two. */
	@Override
	public float getStepHeight() {
		return STEP;
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
