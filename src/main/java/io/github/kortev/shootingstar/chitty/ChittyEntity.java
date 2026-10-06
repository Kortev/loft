package io.github.kortev.shootingstar.chitty;

import io.github.kortev.shootingstar.registry.ModCriteria;
import java.util.List;
import net.minecraft.entity.Dismounting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Chitty Chitty Bang Bang. Four seats, the driver's on the right. She drives on the road, floats on the water on a pink
 * raft she blows up under herself, her wheels turned flat, with a screw behind, and flies: pull the lever (jump) at speed and the wings swing
 * out from under the running boards and fan open, and a mast stands up at the end of each with a propeller turning
 * flat on top; drive her off a cliff and the wings spring out by themselves. She folds her wings away again once she
 * has been down a moment.
 *
 * <p>Like a boat, the car is moved by whoever drives it (their client) and by the server when nobody does. Positions
 * are in blocks; local offsets are at yaw 0, x to the car's left, z forward.
 */
public class ChittyEntity extends Entity {
	private static final TrackedData<Integer> WOBBLE_TICKS = DataTracker.registerData(ChittyEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> WOBBLE_SIDE = DataTracker.registerData(ChittyEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Float> WOBBLE_STRENGTH = DataTracker.registerData(ChittyEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Boolean> WINGS = DataTracker.registerData(ChittyEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Byte> STEER = DataTracker.registerData(ChittyEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final TrackedData<Byte> THROTTLE = DataTracker.registerData(ChittyEntity.class, TrackedDataHandlerRegistry.BYTE);

	/** Where each passenger sits (driver first, then beside them, then the back seat), from tools/chitty_model.py. */
	private static final Vec3d[] SEATS = {new Vec3d(-0.30, 0.75, -0.10), new Vec3d(0.30, 0.75, -0.10),
			new Vec3d(-0.20, 0.75, -1.30), new Vec3d(0.20, 0.75, -1.30)};
	/** The mouth of the exhaust, beside the driver's running board. */
	public static final Vec3d EXHAUST = new Vec3d(-0.83, 0.63, -1.02);
	/** The point the car pitches and rolls about. */
	public static final double TILT_PIVOT = 0.8;

	// Speeds in blocks per tick.
	static final double ROAD_TOP = 0.75;
	static final double ROAD_ACCEL = 0.012;
	static final double BRAKE = 0.035;
	static final double REVERSE_TOP = 0.18;
	static final double WATER_TOP = 0.42;
	static final double WATER_ACCEL = 0.008;
	static final double AIR_TOP = 1.3;
	static final double AIR_ACCEL = 0.014;
	/** Below this the wings stop holding her up. */
	static final double AIR_MIN = 0.28;
	/** How fast she must be going for the lever to lift her. */
	static final double TAKEOFF = 0.45;
	static final double GRAVITY = 0.08;
	/** How deep the bottom of her box sits in the water when she floats. */
	static final double FLOAT_DEPTH = 0.3;
	/** From the middle to the front of the bonnet and to the point of the tail. */
	static final double NOSE = 1.5;
	static final double TAIL = 1.75;
	/** Ticks into the start-up sound at which its two bangs go off. */
	static final int START_BANG_1 = 15;
	static final int START_BANG_2 = 20;
	/** The height of the cloud deck. */
	static final double CLOUDS = 192.0;

	enum Mode { ROAD, WATER, AIR, FALL }

	/** Client-only behaviour the common code calls into: input, sounds. Set by the client initializer. */
	public interface ClientHooks {
		/** The local player's controls, if they are driving this car. */
		ChittyControls controls(ChittyEntity car);

		/** After the driver's client has moved the car: tell the server what the driver is doing. */
		void sync(ChittyEntity car, ChittyControls controls);

		/** The car is ticking on a client: start its sounds if they have not been. */
		void tick(ChittyEntity car);
	}

	@Nullable
	public static ClientHooks client;

	// Movement, on whichever side is moving the car.
	private float speed;
	private boolean flying;
	private float yawVelocity;
	private int settled;
	private Mode mode = Mode.ROAD;
	private boolean wasMoving;
	private Vec3d lastPos;
	private Vec3d motion = Vec3d.ZERO;

	// The server's view of the driver.
	private ChittyControls input = ChittyControls.NONE;
	private int startTicks = -1;
	private int backfireCooldown;
	private double lastSpeed;
	private boolean wasAfloat;

	// Interpolation of a car someone else is moving.
	private int lerpTicks;
	private double lerpX;
	private double lerpY;
	private double lerpZ;
	private double lerpYaw;
	private double lerpPitch;

	// Looks, on clients.
	private ChittyControls clientControls = ChittyControls.NONE;
	private float wingOpen;
	private float prevWingOpen;
	private float floatOpen;
	private float prevFloatOpen;
	private float wheelSpin;
	private float prevWheelSpin;
	private float propSpin;
	private float prevPropSpin;
	private float screwSpin;
	private float prevScrewSpin;
	private float steer;
	private float prevSteer;
	private float bank;
	private float prevBank;
	private float pitch;
	private float prevPitch;
	private boolean wingsShown;
	private boolean floatsShown;

	public ChittyEntity(EntityType<? extends ChittyEntity> type, World world) {
		super(type, world);
		this.intersectionChecked = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(WOBBLE_TICKS, 0);
		builder.add(WOBBLE_SIDE, 1);
		builder.add(WOBBLE_STRENGTH, 0.0F);
		builder.add(WINGS, false);
		builder.add(STEER, (byte) 0);
		builder.add(THROTTLE, (byte) 0);
	}

	// --- what she is ---------------------------------------------------------------------------------

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entry) {
		return new EntitySpawnS2CPacket(this, entry);
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(Chitty.ITEM);
	}

	/** She keeps herself up (or doesn't); vanilla gravity would also have the server kick a driver for "floating". */
	@Override
	public boolean hasNoGravity() {
		return true;
	}

	@Override
	public float getStepHeight() {
		return 1.05F;
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean isCollidable() {
		return true;
	}

	@Override
	public boolean collidesWith(Entity other) {
		return (other.isCollidable() || other.isPushable()) && !isConnectedThroughVehicle(other);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	/** The car is longer than her box, and her wings far wider. */
	@Override
	public Box getVisibilityBoundingBox() {
		return getBoundingBox().expand(3.0, 1.0, 3.0);
	}

	/** Hit, she rocks; hit hard enough (or by anyone in creative), she comes apart and drops herself, as a boat does. */
	@Override
	public boolean damage(DamageSource source, float amount) {
		if (getWorld().isClient || isRemoved()) {
			return true;
		}
		if (isInvulnerableTo(source)) {
			return false;
		}
		setDamageWobbleSide(-getDamageWobbleSide());
		setDamageWobbleTicks(10);
		scheduleVelocityUpdate();
		// A car takes more beating than a boat before it comes apart.
		setDamageWobbleStrength(getDamageWobbleStrength() + amount * 3.5F);
		emitGameEvent(GameEvent.ENTITY_DAMAGE, source.getAttacker());
		boolean creative = source.getAttacker() instanceof PlayerEntity player && player.getAbilities().creativeMode;
		if (creative || getDamageWobbleStrength() > 40.0F) {
			if (!creative && getWorld().getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
				ItemStack stack = new ItemStack(Chitty.ITEM);
				if (hasCustomName()) {
					stack.set(DataComponentTypes.CUSTOM_NAME, getCustomName());
				}
				dropStack(stack);
			}
			discard();
		}
		return true;
	}

	public int getDamageWobbleTicks() {
		return dataTracker.get(WOBBLE_TICKS);
	}

	public void setDamageWobbleTicks(int ticks) {
		dataTracker.set(WOBBLE_TICKS, ticks);
	}

	public int getDamageWobbleSide() {
		return dataTracker.get(WOBBLE_SIDE);
	}

	public void setDamageWobbleSide(int side) {
		dataTracker.set(WOBBLE_SIDE, side);
	}

	public float getDamageWobbleStrength() {
		return dataTracker.get(WOBBLE_STRENGTH);
	}

	public void setDamageWobbleStrength(float strength) {
		dataTracker.set(WOBBLE_STRENGTH, strength);
	}

	public boolean isFlying() {
		return getWorld().isClient && !isLogicalSideForUpdatingMovement() ? dataTracker.get(WINGS) : flying;
	}

	public boolean isEngineRunning() {
		return getControllingPassenger() != null;
	}

	/** Forward speed in blocks per tick, as last moved (any side). */
	public double getSpeed() {
		return motion.horizontalLength();
	}

	// --- passengers ------------------------------------------------------------------------------------

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (player.shouldCancelInteraction() || !canAddPassenger(player)) {
			return ActionResult.PASS;
		}
		if (!getWorld().isClient) {
			return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.PASS;
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().size() < SEATS.length;
	}

	@Override
	@Nullable
	public LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof PlayerEntity player ? player : null;
	}

	@Override
	protected void addPassenger(Entity passenger) {
		boolean starting = !hasPassengers() && passenger instanceof PlayerEntity;
		super.addPassenger(passenger);
		if (starting && getWorld() instanceof ServerWorld world) {
			// The crank, two coughs and the bangs she is named for.
			world.playSound(null, getX(), getY(), getZ(), Chitty.START, SoundCategory.NEUTRAL, 1.0F, 1.0F);
			startTicks = 0;
			award("chitty_start");
		}
	}

	@Override
	protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
		int i = getPassengerList().indexOf(passenger);
		Vec3d seat = SEATS[MathHelper.clamp(i, 0, SEATS.length - 1)];
		if (getWorld().isClient) {
			seat = tilt(seat, getTilt(1.0F), getBank(1.0F));
		}
		return seat.rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE);
	}

	/** A local point as the car pitches (nose up, degrees) and banks (right side down), the way the renderer turns her. */
	public static Vec3d tilt(Vec3d p, float pitchDeg, float bankDeg) {
		double b = bankDeg * MathHelper.RADIANS_PER_DEGREE;
		double y = p.y - TILT_PIVOT;
		double x = p.x * Math.cos(b) - y * Math.sin(b);
		y = p.x * Math.sin(b) + y * Math.cos(b);
		double q = pitchDeg * MathHelper.RADIANS_PER_DEGREE;
		double z = p.z * Math.cos(q) - y * Math.sin(q);
		y = p.z * Math.sin(q) + y * Math.cos(q);
		return new Vec3d(x, y + TILT_PIVOT, z);
	}

	@Override
	protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
		super.updatePassengerPosition(passenger, positionUpdater);
		if (passenger instanceof LivingEntity) {
			passenger.setYaw(passenger.getYaw() - yawVelocity);
			passenger.setHeadYaw(passenger.getHeadYaw() - yawVelocity);
			clampPassengerYaw(passenger);
		}
	}

	@Override
	public void onPassengerLookAround(Entity passenger) {
		clampPassengerYaw(passenger);
	}

	private void clampPassengerYaw(Entity passenger) {
		passenger.setBodyYaw(getYaw());
		float f = MathHelper.wrapDegrees(passenger.getYaw() - getYaw());
		float g = MathHelper.clamp(f, -130.0F, 130.0F);
		passenger.prevYaw += g - f;
		passenger.setYaw(passenger.getYaw() + g - f);
		passenger.setHeadYaw(passenger.getYaw());
	}

	/** Out over the side they sat on, onto the running board's side of the road; if that is blocked, the other. */
	@Override
	public Vec3d updatePassengerForDismount(LivingEntity passenger) {
		float yawRad = getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d left = new Vec3d(MathHelper.cos(yawRad), 0.0, MathHelper.sin(yawRad));
		Vec3d ahead = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
		Vec3d rel = passenger.getPos().subtract(getPos());
		double side = rel.dotProduct(left) >= 0 ? 1.0 : -1.0;
		double along = MathHelper.clamp(rel.dotProduct(ahead), -1.0, 0.6);
		for (double s : new double[] {side, -side}) {
			Vec3d at = getPos().add(left.multiply(s * 1.45)).add(ahead.multiply(along));
			for (int dy : new int[] {0, 1, -1}) {
				BlockPos pos = BlockPos.ofFloored(at.x, getY() + dy, at.z);
				double floor = getWorld().getDismountHeight(pos);
				if (!Dismounting.canDismountInBlock(floor)) {
					continue;
				}
				Vec3d spot = new Vec3d(at.x, pos.getY() + floor, at.z);
				for (EntityPose pose : passenger.getPoses()) {
					if (Dismounting.canPlaceEntityAt(getWorld(), spot, passenger, pose)) {
						passenger.setPose(pose);
						return spot;
					}
				}
			}
		}
		return super.updatePassengerForDismount(passenger);
	}

	// --- moving ----------------------------------------------------------------------------------------

	@Override
	public void tick() {
		if (getDamageWobbleTicks() > 0) {
			setDamageWobbleTicks(getDamageWobbleTicks() - 1);
		}
		if (getDamageWobbleStrength() > 0.0F) {
			setDamageWobbleStrength(getDamageWobbleStrength() - 1.0F);
		}
		super.tick();
		boolean moving = isLogicalSideForUpdatingMovement();
		if (moving) {
			lerpTicks = 0;
			updateTrackedPosition(getX(), getY(), getZ());
			// Taking over from whoever moved her before (not a fresh car on the server, which keeps what it was saved with).
			if (!wasMoving && (getWorld().isClient || age > 1)) {
				adopt();
			}
			ChittyControls controls = getWorld().isClient && client != null ? client.controls(this) : ChittyControls.NONE;
			clientControls = controls;
			drive(controls);
			if (getWorld().isClient && client != null) {
				client.sync(this, controls);
			}
		} else {
			lerp();
			setVelocity(Vec3d.ZERO);
		}
		wasMoving = moving;
		Vec3d pos = getPos();
		motion = lastPos == null ? Vec3d.ZERO : pos.subtract(lastPos);
		lastPos = pos;
		if (getWorld() instanceof ServerWorld world) {
			serverTick(world);
		} else {
			clientTick();
		}
	}

	/** Taking over a car that was moving without us: carry on as she was going. */
	private void adopt() {
		float yawRad = getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d ahead = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
		speed = (float) motion.dotProduct(ahead);
		flying = dataTracker.get(WINGS);
		setVelocity(motion);
	}

	/** Sets her going: forward speed in blocks a tick, and whether her wings are out. For tests and commands. */
	public void launch(float speed, boolean wings) {
		this.speed = speed;
		setFlying(wings);
	}

	private void setFlying(boolean value) {
		flying = value;
		if (!getWorld().isClient) {
			dataTracker.set(WINGS, value);
			if (value) {
				award("chitty_fly");
			}
		}
	}

	/** An advancement for everyone aboard. */
	private void award(String event) {
		for (Entity passenger : getPassengerList()) {
			if (passenger instanceof ServerPlayerEntity player) {
				ModCriteria.fire(player, event);
			}
		}
	}

	private void drive(ChittyControls in) {
		World world = getWorld();
		boolean driven = getControllingPassenger() != null;
		if (!driven) {
			in = ChittyControls.NONE;
		}
		Vec3d v = getVelocity();
		double depth = getFluidHeight(FluidTags.WATER);
		boolean afloat = depth > 0.05 && !isInLava();
		boolean ground = isOnGround();

		// The wings: the lever at speed, or by themselves off a cliff; folded again once she has been down a while.
		if (!flying) {
			boolean lever = driven && in.up() && Math.abs(speed) >= TAKEOFF && (ground || afloat);
			boolean cliff = driven && !ground && !afloat && v.y < -0.42;
			if (lever || cliff) {
				setFlying(true);
				settled = 0;
			}
		} else if ((ground || afloat) && !in.up()) {
			if (++settled > 16) {
				setFlying(false);
			}
		} else {
			settled = 0;
		}

		if (flying && (!(ground || afloat) || in.up() && speed > AIR_MIN)) {
			mode = Mode.AIR;
		} else if (afloat) {
			mode = Mode.WATER;
		} else if (ground) {
			mode = Mode.ROAD;
		} else {
			mode = Mode.FALL;
		}

		int throttle = in.forward();
		switch (mode) {
			case AIR -> {
				if (throttle > 0) {
					speed = (float) Math.min(AIR_TOP, speed + AIR_ACCEL);
				} else if (throttle < 0) {
					speed = (float) Math.max(AIR_MIN * 0.6, speed - AIR_ACCEL * 1.5);
				} else {
					speed *= driven ? 0.998F : 0.99F;
				}
				speed = Math.max(speed, 0.0F);
			}
			case WATER -> speed = (float) pedal(speed, throttle, WATER_TOP, WATER_ACCEL, 0.95);
			case ROAD -> speed = (float) pedal(speed, throttle, ROAD_TOP, ROAD_ACCEL, 0.97);
			case FALL -> speed *= 0.995F;
		}

		float rate = 0.0F;
		int turn = in.turn();
		double s = Math.abs(speed);
		switch (mode) {
			case ROAD -> rate = (float) (turn * 4.5 * MathHelper.clamp(s / 0.15, 0.0, 1.0) * (1.0 - 0.35 * s / ROAD_TOP)
					* Math.signum(speed));
			case WATER -> rate = (float) (turn * 3.0 * (0.35 + 0.65 * MathHelper.clamp(s / 0.2, 0.0, 1.0)) * (speed < -0.01F ? -1 : 1));
			case AIR -> rate = turn * 3.2F;
			case FALL -> rate = 0.0F;
		}
		yawVelocity += (rate - yawVelocity) * 0.5F;
		setYaw(getYaw() - yawVelocity);

		float yawRad = getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d ahead = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
		double vy = v.y;
		switch (mode) {
			case AIR -> {
				double lift = MathHelper.clamp((speed - AIR_MIN) / 0.25, 0.0, 1.0);
				double climb = !driven ? -0.15 : in.up() ? 0.32 : in.down() ? -0.5 : 0.0;
				double target = lift * climb - (1.0 - lift) * 0.45;
				vy += (target - vy) * 0.12;
			}
			case WATER -> vy = vy * 0.8 + (depth - FLOAT_DEPTH) * 0.1;
			default -> vy = (vy - GRAVITY) * 0.98;
		}
		// Off the ground without wings she keeps going the way she was (and loses a little), as off a ramp.
		Vec3d horizontal = ahead.multiply(speed);
		Vec3d step = new Vec3d(horizontal.x, vy, horizontal.z);

		// The box is square and shorter than the car: look ahead of the bonnet (or behind the tail) for anything she
		// cannot climb, so she stops at walls instead of driving her nose into them.
		if (Math.abs(speed) > 0.02 && mode != Mode.FALL && noseHits(world, ahead, step)) {
			speed = -speed * 0.15F;
			step = new Vec3d(0.0, vy, 0.0);
		}
		setVelocity(step);
		move(MovementType.SELF, step);
		if (horizontalCollision && Math.abs(speed) > 0.05F) {
			speed *= 0.4F;
		}
		if (flying || mode == Mode.WATER) {
			fallDistance = 0.0F;
		}
	}

	private static double pedal(double speed, int throttle, double top, double accel, double drag) {
		// Faster than she can go here (down out of the air, say): she sheds the rest quickly, though not all at once.
		if (Math.abs(speed) > top) {
			return Math.signum(speed) * Math.max(top, Math.abs(speed) * 0.92);
		}
		if (throttle > 0) {
			return speed < 0 ? Math.min(0.0, speed + BRAKE) : Math.min(top, speed + accel * (1.0 - 0.5 * speed / top));
		}
		if (throttle < 0) {
			return speed > 0 ? Math.max(0.0, speed - BRAKE) : Math.max(-REVERSE_TOP, speed - accel * 0.6);
		}
		speed *= drag;
		return Math.abs(speed) < 0.004 ? 0.0 : speed;
	}

	private boolean noseHits(World world, Vec3d ahead, Vec3d step) {
		double dir = Math.signum(speed);
		Vec3d c = getPos().add(ahead.multiply(dir * (dir > 0 ? NOSE : TAIL))).add(step.x, 0.0, step.z);
		double lift = Math.max(0.0, step.y);
		double bottom = getY() + lift + (mode == Mode.AIR ? 0.3 : getStepHeight() + 0.05);
		double top = getY() + lift + 1.3;
		return !world.isSpaceEmpty(this, new Box(c.x - 0.45, bottom, c.z - 0.45, c.x + 0.45, top, c.z + 0.45));
	}

	// --- someone else is moving her: follow their updates smoothly ------------------------------------

	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
		lerpX = x;
		lerpY = y;
		lerpZ = z;
		lerpYaw = yaw;
		lerpPitch = pitch;
		lerpTicks = 3;
	}

	private void lerp() {
		if (lerpTicks > 0) {
			lerpPosAndRotation(lerpTicks, lerpX, lerpY, lerpZ, lerpYaw, lerpPitch);
			lerpTicks--;
		}
	}

	@Override
	public double getLerpTargetX() {
		return lerpTicks > 0 ? lerpX : getX();
	}

	@Override
	public double getLerpTargetY() {
		return lerpTicks > 0 ? lerpY : getY();
	}

	@Override
	public double getLerpTargetZ() {
		return lerpTicks > 0 ? lerpZ : getZ();
	}

	@Override
	public float getLerpTargetPitch() {
		return lerpTicks > 0 ? (float) lerpPitch : getPitch();
	}

	@Override
	public float getLerpTargetYaw() {
		return lerpTicks > 0 ? (float) lerpYaw : getYaw();
	}

	// --- the server: the driver's wishes, the bangs -----------------------------------------------------

	/** What the driver's client says they are doing. */
	public void applyInput(ServerPlayerEntity player, ChittyControls controls, boolean wings) {
		if (getControllingPassenger() != player) {
			return;
		}
		int before = input.forward();
		input = controls;
		if (wings != flying) {
			setFlying(wings);
		}
		dataTracker.set(STEER, (byte) controls.turn());
		dataTracker.set(THROTTLE, (byte) controls.forward());
		// Lifting off the throttle at speed: she backfires, often.
		if (before > 0 && controls.forward() <= 0 && lastSpeed > 0.35 && backfireCooldown == 0 && random.nextFloat() < 0.45F) {
			bangBang();
		}
	}

	public void honk(ServerPlayerEntity player) {
		if (player.getVehicle() == this && getWorld() instanceof ServerWorld world) {
			world.playSound(null, getX(), getY(), getZ(), Chitty.HORN, SoundCategory.NEUTRAL, 1.6F, 1.0F);
		}
	}

	private void bangBang() {
		if (getWorld() instanceof ServerWorld world) {
			world.playSound(null, getX(), getY(), getZ(), Chitty.BANG, SoundCategory.NEUTRAL, 1.8F, 0.95F + random.nextFloat() * 0.1F);
			startTicks = START_BANG_1 - 5;
			backfireCooldown = 80;
		}
	}

	private void serverTick(ServerWorld world) {
		if (isLogicalSideForUpdatingMovement()) {
			input = ChittyControls.NONE;
			dataTracker.set(WINGS, flying);
			dataTracker.set(STEER, (byte) 0);
			dataTracker.set(THROTTLE, (byte) 0);
		}
		if (backfireCooldown > 0) {
			backfireCooldown--;
		}
		// The start-up's (or a backfire's) two bangs, each with a puff of smoke out of the exhaust.
		if (startTicks >= 0) {
			startTicks++;
			if (startTicks == START_BANG_1 || startTicks == START_BANG_2) {
				Vec3d at = exhaust();
				world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 8, 0.12, 0.08, 0.12, 0.02);
				world.spawnParticles(ParticleTypes.POOF, at.x, at.y, at.z, 4, 0.05, 0.05, 0.05, 0.03);
				world.spawnParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 2, 0.02, 0.02, 0.02, 0.01);
			}
			if (startTicks > START_BANG_2) {
				startTicks = -1;
			}
		}
		// Now and then while she idles along, a lone backfire.
		if (isEngineRunning() && backfireCooldown == 0 && random.nextInt(900) == 0) {
			bangBang();
		}
		boolean afloat = getFluidHeight(FluidTags.WATER) > 0.05 && !flying;
		if (afloat && !wasAfloat) {
			award("chitty_float");
		}
		wasAfloat = afloat;
		if (flying && age % 20 == 0 && getY() > CLOUDS) {
			award("chitty_clouds");
		}
		// Hitting something hard: a crunch for everyone.
		double now = motion.horizontalLength();
		if (age > 20 && lastSpeed > 0.45 && now < lastSpeed * 0.3) {
			world.playSound(null, getX(), getY(), getZ(), Chitty.CRASH, SoundCategory.NEUTRAL, 1.2F, 0.9F + random.nextFloat() * 0.2F);
		}
		lastSpeed = now;
	}

	/** Where the exhaust comes out, in the world. */
	public Vec3d exhaust() {
		return getPos().add(EXHAUST.rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE));
	}

	// --- the look of her, on clients ----------------------------------------------------------------------

	private void clientTick() {
		World world = getWorld();
		boolean fly = isFlying();
		boolean wet = getFluidHeight(FluidTags.WATER) > 0.05 && !fly;
		prevWingOpen = wingOpen;
		wingOpen = MathHelper.clamp(wingOpen + (fly ? 1.0F : -1.0F) / 18.0F, 0.0F, 1.0F);
		prevFloatOpen = floatOpen;
		floatOpen = MathHelper.clamp(floatOpen + (wet ? 1.0F : -1.0F) / 14.0F, 0.0F, 1.0F);
		if (fly != wingsShown) {
			wingsShown = fly;
			world.playSound(getX(), getY(), getZ(), fly ? Chitty.WINGS_OUT : Chitty.WINGS_IN, SoundCategory.NEUTRAL, 1.2F, 1.0F, false);
		}
		if (wet != floatsShown) {
			floatsShown = wet;
			if (wet) {
				world.playSound(getX(), getY(), getZ(), Chitty.FLOATS, SoundCategory.NEUTRAL, 1.2F, 1.0F, false);
				for (int i = 0; i < 30; i++) {
					world.addParticle(ParticleTypes.SPLASH, getX() + (random.nextDouble() - 0.5) * 3.0, getY() + 0.4,
							getZ() + (random.nextDouble() - 0.5) * 3.0, 0.0, 0.1, 0.0);
				}
			}
		}

		float yawRad = getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d ahead = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
		double forward = motion.dotProduct(ahead);
		prevWheelSpin = wheelSpin;
		// On the ground the wheels roll with the road; in the air, or laid flat on the raft, they turn over slowly.
		wheelSpin += fly && wingOpen > 0.5F || floatOpen > 0.5F ? 0.06F : (float) (forward / 0.46);
		prevPropSpin = propSpin;
		propSpin += fly ? 0.9F + (float) motion.length() * 0.6F : 0.0F;
		prevScrewSpin = screwSpin;
		int throttle = isLogicalSideForUpdatingMovement() ? clientControls.forward() : dataTracker.get(THROTTLE);
		screwSpin += wet ? 0.15F + Math.abs(throttle) * 0.6F + (float) Math.abs(forward) : 0.0F;
		int turn = isLogicalSideForUpdatingMovement() ? clientControls.turn() : dataTracker.get(STEER);
		prevSteer = steer;
		steer += (turn - steer) * 0.3F;
		boolean aloft = fly && !isOnGround() && !wet;
		prevBank = bank;
		bank += ((aloft ? -turn * 20.0F : 0.0F) - bank) * 0.12F;
		prevPitch = pitch;
		double h = motion.horizontalLength();
		float climb = aloft && h > 0.05 ? (float) Math.toDegrees(Math.atan2(motion.y, h)) : 0.0F;
		pitch += (MathHelper.clamp(climb, -18.0F, 18.0F) - pitch) * 0.2F;

		// Smoke out of the exhaust while she runs, more when she pulls.
		if (isEngineRunning() && age % (throttle > 0 ? 2 : 4) == 0) {
			Vec3d at = exhaust();
			Vec3d back = ahead.multiply(-0.04);
			world.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, back.x, 0.02, back.z);
		}
		if (client != null) {
			client.tick(this);
		}
	}

	public float getWingOpen(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevWingOpen, wingOpen);
	}

	public float getFloatOpen(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevFloatOpen, floatOpen);
	}

	public float getWheelSpin(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevWheelSpin, wheelSpin);
	}

	public float getPropSpin(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevPropSpin, propSpin);
	}

	public float getScrewSpin(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevScrewSpin, screwSpin);
	}

	/** -1 (full right) to 1 (full left). */
	public float getSteer(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevSteer, steer);
	}

	/** Degrees, right side down. */
	public float getBank(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevBank, bank);
	}

	/** Degrees, nose up: how she is drawn, not where she faces. */
	public float getTilt(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevPitch, pitch);
	}

	/** The pedal the driver is on, as everyone sees it: -1, 0 or 1. */
	public int getThrottle() {
		return isLogicalSideForUpdatingMovement() ? clientControls.forward() : dataTracker.get(THROTTLE);
	}

	// --- saving ------------------------------------------------------------------------------------------

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		flying = nbt.getBoolean("Flying");
		speed = nbt.getFloat("Speed");
		dataTracker.set(WINGS, flying);
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putBoolean("Flying", flying);
		nbt.putFloat("Speed", speed);
	}

	/** For tests: the passengers in seat order. */
	public List<Entity> seats() {
		return getPassengerList();
	}
}
