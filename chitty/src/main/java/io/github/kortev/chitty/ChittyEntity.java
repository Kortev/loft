package io.github.kortev.chitty;

import io.github.kortev.shootingstar.registry.ModCriteria;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
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
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Chitty Chitty Bang Bang. Four seats: the driver's on the right, one beside it and two (for children, really) in the
 * well in the deck behind; you get into whichever you click nearest. She drives on the road, floats on the water on a
 * pink raft she blows up under herself, her wheels turned flat, with a screw behind, and flies: the driver opens her
 * wings (G), which swing out from under the running boards and fan open while a mast stands up at the end of each with
 * a propeller turning flat on top, and at speed the lever (jump) lifts her.
 *
 * <p>As in the film she looks after her passengers herself. Driven off a cliff she falls, and only as the ground comes
 * up at her do the wings spring out and carry her up out of the dive (folding again once she is down). Driven into the
 * sea she wades and settles, and after a little while blows up her raft and rises onto it, whether or not anyone is
 * still aboard; coming down on the water from the air, the wings fold and the raft comes up at once; and she climbs out
 * up the bank onto land, letting the raft down again. The raft is all hers: it never comes up with the wings out. The
 * driver can also fire the back seat's passengers out into the air (the ejector), and the hamper on the back comes off
 * and on (sneak and use) and holds things.
 *
 * <p>Like a boat, the car is moved by whoever drives it (their client) and by the server when nobody does. Positions
 * are in blocks; local offsets are at yaw 0, x to the car's left, z forward.
 */
public class ChittyEntity extends Entity {
	private static final TrackedData<Integer> WOBBLE_TICKS = DataTracker.registerData(ChittyEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> WOBBLE_SIDE = DataTracker.registerData(ChittyEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Float> WOBBLE_STRENGTH = DataTracker.registerData(ChittyEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** Her wings and her raft, out or away and how (the STATE_ bits). */
	private static final TrackedData<Byte> STATE = DataTracker.registerData(ChittyEntity.class, TrackedDataHandlerRegistry.BYTE);
	/** Which seat each passenger, in the order they are listed, sits in: two bits each. */
	private static final TrackedData<Byte> SEATING = DataTracker.registerData(ChittyEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final TrackedData<Boolean> HAMPER = DataTracker.registerData(ChittyEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Byte> STEER = DataTracker.registerData(ChittyEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final TrackedData<Byte> THROTTLE = DataTracker.registerData(ChittyEntity.class, TrackedDataHandlerRegistry.BYTE);

	/** Where each seat is (the driver's, the one beside it, then the back two), from tools/chitty_model.py. */
	private static final Vec3d[] SEATS = {new Vec3d(-0.30, 0.75, -0.10), new Vec3d(0.30, 0.75, -0.10),
			new Vec3d(-0.20, 0.75, -1.30), new Vec3d(0.20, 0.75, -1.30)};
	// The order seats are filled in when nobody chose one: players from the driver's back, anyone else from the back.
	private static final int[] FRONT_FIRST = {0, 1, 2, 3};
	private static final int[] BACK_FIRST = {2, 3, 1, 0};
	/** The entity status that tells clients the ejector went off. */
	private static final byte EJECT_STATUS = 90;
	private static final int EJECT_SETTLED = 60;
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
	/** Ticks in the water without her raft before she blows it up: with a driver, and left to herself. */
	static final int FLOAT_DELAY = 40;
	static final int FLOAT_DELAY_ALONE = 60;
	/** Ticks back on land before the raft goes away again. */
	static final int BEACHED = 20;
	/** Ticks for the wings to be out far enough to bear her up. */
	static final int WINGS_BITE = 36;
	/** The seats: the driver's, the one beside it, and the back two. */
	public static final int DRIVER = 0;
	static final int BACK_SEAT = 2;
	/** How far she must have fallen before she will catch herself, and for how long she pulls out of the dive. */
	static final double RESCUE_FALL = 5.0;
	static final int SWOOP = 14;

	static final int STATE_WINGS = 1;
	/** The driver opened the wings by hand: they stay out on the ground. */
	static final int STATE_WINGS_HELD = 2;
	static final int STATE_FLOATS = 4;

	enum Mode { ROAD, WATER, WADE, AIR, FALL }

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
	private boolean wingsHeld;
	private boolean floats;
	private int wingTicks;
	private boolean takeoff;
	private int wetTicks;
	private int dryTicks;
	private int swoop;
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
	private float gearLever;
	private float prevGearLever;
	private float brakeLever;
	private float prevBrakeLever;
	private float crankSpin;
	private float prevCrankSpin;
	private int crankTicks;
	// The needles on the dashboard, on the client (0 to 1 round each dial): her speed, her height above the sea and
	// the engine's revs.
	private final float[] dials = new float[3];
	private final float[] prevDials = new float[3];
	// On the client: whether she was in the water last tick (for the splash going in).
	private boolean wasWet;
	// Ticks since the ejector last went off, on the client: the back seat springs up and bounces back down.
	private int ejectTicks = EJECT_SETTLED;

	// Who sits where (by seat), and the seat a player has asked for as they get in.
	private final Entity[] seated = new Entity[SEATS.length];
	private int wantedSeat = -1;
	// Her hitboxes along her length (ChittyPartEntity), on the server.
	private final ChittyPartEntity[] parts = new ChittyPartEntity[ChittyPartEntity.COUNT];
	private final SimpleInventory hamper = new SimpleInventory(27) {
		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return !isRemoved() && hasHamper() && player.squaredDistanceTo(ChittyEntity.this) < 64.0;
		}
	};

	public ChittyEntity(EntityType<? extends ChittyEntity> type, World world) {
		super(type, world);
		this.intersectionChecked = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(WOBBLE_TICKS, 0);
		builder.add(WOBBLE_SIDE, 1);
		builder.add(WOBBLE_STRENGTH, 0.0F);
		builder.add(STATE, (byte) 0);
		builder.add(SEATING, (byte) 0);
		builder.add(HAMPER, true);
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
		return (other.isCollidable() || other.isPushable()) && !isConnectedThroughVehicle(other)
				&& !(other instanceof ChittyPartEntity part && part.getCar() == this);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	/** The car is longer than her box, her wings far wider, and her lamps shine further still. */
	@Override
	public Box getVisibilityBoundingBox() {
		return getBoundingBox().expand(9.0, 2.0, 9.0);
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
			ItemScatterer.spawn(getWorld(), this, hamper);
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

	/** Whether her wings are out (in the air or not). */
	public boolean isFlying() {
		return getWorld().isClient && !isLogicalSideForUpdatingMovement() ? (dataTracker.get(STATE) & STATE_WINGS) != 0 : flying;
	}

	/** Whether her raft is blown up (on the water or not). */
	public boolean isFloating() {
		return getWorld().isClient && !isLogicalSideForUpdatingMovement() ? (dataTracker.get(STATE) & STATE_FLOATS) != 0 : floats;
	}

	/** Her wings and raft as the STATE_ bits, as whoever moves her has them. */
	public byte getState() {
		int bits = (flying ? STATE_WINGS : 0) | (wingsHeld ? STATE_WINGS_HELD : 0) | (floats ? STATE_FLOATS : 0);
		return (byte) bits;
	}

	private void setState(int bits) {
		setFlying((bits & STATE_WINGS) != 0);
		wingsHeld = (bits & STATE_WINGS_HELD) != 0;
		setFloats((bits & STATE_FLOATS) != 0);
		if (!getWorld().isClient) {
			dataTracker.set(STATE, getState());
		}
	}

	/** Whether the hamper is strapped on the back. */
	public boolean hasHamper() {
		return dataTracker.get(HAMPER);
	}

	/** What is in the hamper. */
	public SimpleInventory getHamper() {
		return hamper;
	}

	/** Takes the hamper off (spilling what is in it) or straps it back on. */
	public void toggleHamper() {
		if (getWorld().isClient) {
			return;
		}
		boolean on = !hasHamper();
		dataTracker.set(HAMPER, on);
		if (!on) {
			// Out over the grass behind her, where the hamper was.
			Vec3d at = getPos().add(new Vec3d(0.0, 0.8, -3.3).rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE));
			for (int i = 0; i < hamper.size(); i++) {
				ItemScatterer.spawn(getWorld(), at.x, at.y, at.z, hamper.removeStack(i));
			}
		}
		getWorld().playSound(null, getX(), getY(), getZ(), on ? SoundEvents.BLOCK_BARREL_CLOSE : SoundEvents.BLOCK_BARREL_OPEN,
				SoundCategory.NEUTRAL, 1.0F, 0.8F);
	}

	public boolean isEngineRunning() {
		return getControllingPassenger() != null;
	}

	/** Forward speed in blocks per tick, as last moved (any side). */
	public double getSpeed() {
		return motion.horizontalLength();
	}

	/** How fast she is going through the air, any way at all (blocks a tick): her speed, falling included. */
	public double getAirSpeed() {
		return motion.length();
	}

	// --- passengers ------------------------------------------------------------------------------------

	/**
	 * Using her: sneaking, the hamper comes off or goes back on; at the hamper, it opens; anywhere else, you get into
	 * the free seat nearest where you clicked.
	 */
	@Override
	public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
		Vec3d local = hitPos.rotateY(getYaw() * MathHelper.RADIANS_PER_DEGREE);
		if (player.shouldCancelInteraction()) {
			toggleHamper();
			return ActionResult.success(getWorld().isClient);
		}
		if (hasHamper() && local.z < -2.6 && local.y < 1.2) {
			if (!getWorld().isClient) {
				player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
						(syncId, inventory, who) -> GenericContainerScreenHandler.createGeneric9x3(syncId, inventory, hamper),
						Text.translatable("container.shootingstar.chitty_hamper")));
			}
			return ActionResult.success(getWorld().isClient);
		}
		int seat = nearestFreeSeat(local);
		if (seat < 0) {
			return ActionResult.PASS;
		}
		if (!getWorld().isClient) {
			return seat(player, seat) ? ActionResult.CONSUME : ActionResult.PASS;
		}
		return ActionResult.SUCCESS;
	}

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

	/** The free seat nearest a point beside or in her (local), or -1 if every seat is taken. */
	private int nearestFreeSeat(Vec3d local) {
		int best = -1;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < SEATS.length; i++) {
			if (seated[i] != null && seated[i].isAlive() && seated[i].getVehicle() == this) {
				continue;
			}
			double dx = SEATS[i].x - local.x;
			double dz = SEATS[i].z - local.z;
			double distance = dx * dx + dz * dz;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return best;
	}

	/** Puts a passenger in a particular seat (0, the driver's, to 3), if it is free. */
	public boolean seat(Entity passenger, int seat) {
		if (seat < 0 || seat >= SEATS.length || seated[seat] != null && seated[seat].getVehicle() == this) {
			return false;
		}
		wantedSeat = seat;
		boolean in = passenger.startRiding(this);
		wantedSeat = -1;
		return in;
	}

	/** The seat a passenger is in (0, the driver's, to 3), or -1 if they are not aboard. */
	public int seatOf(Entity passenger) {
		if (!getWorld().isClient) {
			for (int i = 0; i < SEATS.length; i++) {
				if (seated[i] == passenger) {
					return i;
				}
			}
			return -1;
		}
		int i = getPassengerList().indexOf(passenger);
		return i < 0 ? -1 : dataTracker.get(SEATING) >> (2 * i) & 3;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().size() < SEATS.length;
	}

	/** The driver: whoever is in the driver's seat, if it is a player. */
	@Override
	@Nullable
	public LivingEntity getControllingPassenger() {
		for (Entity passenger : getPassengerList()) {
			if (seatOf(passenger) == DRIVER) {
				return passenger instanceof PlayerEntity player ? player : null;
			}
		}
		return null;
	}

	@Override
	protected void addPassenger(Entity passenger) {
		boolean starting = !hasPassengers() && passenger instanceof PlayerEntity;
		super.addPassenger(passenger);
		if (!getWorld().isClient) {
			int seat = wantedSeat >= 0 && seated[wantedSeat] == null ? wantedSeat : -1;
			// Nobody chose a seat: a player takes the first free one from the driver's back, anyone else (a mob put in
			// her, a child) the back seat first.
			int[] order = passenger instanceof PlayerEntity ? FRONT_FIRST : BACK_FIRST;
			for (int k = 0; seat < 0 && k < order.length; k++) {
				if (seated[order[k]] == null) {
					seat = order[k];
				}
			}
			if (seat >= 0) {
				seated[seat] = passenger;
			}
			publishSeating();
		} else if (starting) {
			// Someone climbs in and swings the starting handle.
			crankTicks = 24;
		}
		if (starting && getWorld() instanceof ServerWorld world) {
			// The crank, two coughs and the bangs she is named for.
			world.playSound(null, getX(), getY(), getZ(), Chitty.START, SoundCategory.NEUTRAL, 1.0F, 1.0F);
			startTicks = 0;
			award("chitty_start");
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!getWorld().isClient) {
			for (int i = 0; i < SEATS.length; i++) {
				if (seated[i] == passenger) {
					seated[i] = null;
				}
			}
			publishSeating();
		}
	}

	/** Tells clients who sits where: each passenger's seat, in the order they are listed. */
	private void publishSeating() {
		int bits = 0;
		List<Entity> passengers = getPassengerList();
		for (int i = 0; i < passengers.size() && i < SEATS.length; i++) {
			int seat = Math.max(0, seatOf(passengers.get(i)));
			bits |= seat << (2 * i);
		}
		dataTracker.set(SEATING, (byte) bits);
	}

	/**
	 * The ejector: whoever is in the back seat is fired straight up out of it, to come down gently under the slow
	 * falling it gives them. For the driver (the X key).
	 */
	public void ejectBackSeat() {
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		boolean any = false;
		for (int i = BACK_SEAT; i < SEATS.length; i++) {
			Entity passenger = seated[i];
			if (passenger == null || passenger.getVehicle() != this) {
				continue;
			}
			passenger.stopRiding();
			Vec3d seat = getPos().add(SEATS[i].rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE)).add(0.0, 0.6, 0.0);
			passenger.requestTeleport(seat.x, seat.y, seat.z);
			passenger.setVelocity(getVelocity().add(0.0, 1.25, 0.0));
			passenger.velocityModified = true;
			passenger.fallDistance = 0.0F;
			// Players float down; anything else (an unwanted passenger) takes its chances.
			if (passenger instanceof PlayerEntity player) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100, 0, false, false));
			}
			world.spawnParticles(ParticleTypes.POOF, seat.x, seat.y - 0.4, seat.z, 12, 0.2, 0.1, 0.2, 0.05);
			any = true;
		}
		// The seat springs up whether or not anyone is on it.
		world.playSound(null, getX(), getY(), getZ(), Chitty.EJECT, SoundCategory.NEUTRAL, any ? 1.4F : 1.0F, 1.0F);
		world.sendEntityStatus(this, EJECT_STATUS);
	}

	@Override
	protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
		int i = seatOf(passenger);
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
			// Everyone aboard turns with her, by however far she turned this tick: on the road, on the water and in the
			// air, and on every client, not only the driver's, which alone knows how fast she is being steered.
			float turn = MathHelper.wrapDegrees(getYaw() - prevYaw);
			passenger.setYaw(passenger.getYaw() + turn);
			passenger.setHeadYaw(passenger.getHeadYaw() + turn);
			clampPassengerYaw(passenger);
			// And they look up and down with her as her nose rises and falls in the air (on clients, where she is
			// tipped), so that what is ahead of her stays where it was in their view, and what they aim at with it.
			float nod = pitch - prevPitch;
			if (nod != 0.0F) {
				passenger.setPitch(MathHelper.clamp(passenger.getPitch() - nod, -90.0F, 90.0F));
			}
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
		int bits = dataTracker.get(STATE);
		flying = (bits & STATE_WINGS) != 0;
		wingsHeld = (bits & STATE_WINGS_HELD) != 0;
		floats = (bits & STATE_FLOATS) != 0;
		setVelocity(motion);
	}

	/** Sets her going: forward speed in blocks a tick, and whether her wings are out. For tests and commands. */
	public void launch(float speed, boolean wings) {
		this.speed = speed;
		setFlying(wings);
	}

	/**
	 * Opens her wings, or folds them, by hand: opened, they stay out until folded. (The driver's G key.) Opened on the
	 * water, she lets the raft down and lifts off it.
	 */
	public void toggleWings() {
		setFlying(!flying);
		wingsHeld = flying;
		swoop = 0;
		settled = 0;
		if (flying && floats) {
			setFloats(false);
			takeoff = true;
		}
		publish();
	}

	/** Put down on the water: her raft is already up. */
	public void blowUpRaft() {
		setFloats(true);
		publish();
	}

	private void publish() {
		if (!getWorld().isClient) {
			dataTracker.set(STATE, getState());
		}
	}

	private void setFlying(boolean value) {
		if (value && !flying && !getWorld().isClient) {
			award("chitty_fly");
		}
		flying = value;
		publish();
	}

	private void setFloats(boolean value) {
		floats = value;
		publish();
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
		boolean wet = depth > 0.05 && !isInLava();
		boolean ground = isOnGround();

		// Coming down on the water from the air (not lifting off it), the wings fold and the raft comes up at once.
		boolean splashdown = flying && wet && !takeoff && !in.up() && v.y <= 0.05;
		if (splashdown) {
			setFlying(false);
			wingsHeld = false;
		}
		// The raft is hers alone: in the water, wings away, she wades and settles a while, then blows it up herself; out
		// of the water (or lifting off it) she lets it down again.
		if (!wet || flying) {
			wetTicks = 0;
		}
		if (wet && !floats && !flying) {
			if (splashdown || ++wetTicks >= (driven ? FLOAT_DELAY : FLOAT_DELAY_ALONE)) {
				setFloats(true);
				wetTicks = 0;
			}
		}
		if (floats && (flying || ground && !wet)) {
			if (flying || ++dryTicks > BEACHED) {
				setFloats(false);
			}
		} else {
			dryTicks = 0;
		}
		boolean afloat = wet && floats;
		wingTicks = flying ? wingTicks + 1 : 0;
		if (takeoff && (!flying || wingTicks >= WINGS_BITE)) {
			takeoff = false;
		}

		// The wings: by hand; or, falling with people aboard, by themselves as the ground comes up at her, folding
		// again once she has been down a while.
		if (!flying) {
			if (hasPassengers() && !ground && !wet && v.y < -0.3 && fallDistance > RESCUE_FALL && aboutToHit(world, v.y)) {
				setFlying(true);
				settled = 0;
				swoop = SWOOP;
				if (driven) {
					speed = Math.max(speed, 0.6F);
				}
			}
		} else if (!wingsHeld && (ground || afloat) && !in.up()) {
			if (++settled > 16) {
				setFlying(false);
			}
		} else {
			settled = 0;
		}
		if (swoop > 0 && --swoop == 0 || !flying) {
			swoop = 0;
		}

		if (flying && (swoop > 0 || takeoff || !(ground || afloat) || in.up() && speed > AIR_MIN)) {
			mode = Mode.AIR;
		} else if (afloat) {
			mode = Mode.WATER;
		} else if (wet) {
			mode = Mode.WADE;
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
			// Wading, and up on dry land on her raft till it goes down, she can only creep.
			case WADE -> speed = (float) pedal(speed, throttle, 0.1, 0.004, 0.85);
			case ROAD -> speed = floats ? (float) pedal(speed, throttle, 0.15, 0.006, 0.9) : (float) pedal(speed, throttle, ROAD_TOP, ROAD_ACCEL, 0.97);
			case FALL -> speed *= 0.995F;
		}

		float rate = 0.0F;
		int turn = in.turn();
		double s = Math.abs(speed);
		switch (mode) {
			case ROAD -> rate = (float) (turn * 4.5 * MathHelper.clamp(s / 0.15, 0.0, 1.0) * (1.0 - 0.35 * s / ROAD_TOP)
					* Math.signum(speed));
			case WATER -> rate = (float) (turn * 3.0 * (0.35 + 0.65 * MathHelper.clamp(s / 0.2, 0.0, 1.0)) * (speed < -0.01F ? -1 : 1));
			case WADE -> rate = (float) (turn * 1.5 * MathHelper.clamp(s / 0.05, 0.0, 1.0) * Math.signum(speed));
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
				// The wings bear her up only as they come out.
				double lift = MathHelper.clamp((speed - AIR_MIN) / 0.25, 0.0, 1.0) * MathHelper.clamp(wingTicks / (double) WINGS_BITE, 0.0, 1.0);
				if (takeoff) {
					// Lifting off the water: up off the raft.
					speed = Math.max(speed, (float) TAKEOFF + 0.1F);
					lift = 1.0;
				}
				double climb = !driven ? -0.15 : in.up() ? 0.32 : in.down() ? -0.5 : 0.0;
				double target = lift * climb - (1.0 - lift) * 0.45;
				if (swoop > 0) {
					// Caught at the last moment: the wings bite and pull her up out of the dive.
					vy += (0.12 - vy) * 0.35;
				} else if (takeoff) {
					vy = Math.max(vy, wet ? 0.3 : 0.1);
				} else {
					vy += (target - vy) * 0.12;
				}
			}
			case WATER -> vy = vy * 0.8 + (depth - FLOAT_DEPTH) * 0.1;
			// Without her raft she settles slowly through the water to the bottom.
			case WADE -> vy = ground ? -GRAVITY : vy * 0.8 - 0.008;
			default -> vy = (vy - GRAVITY) * 0.98;
		}
		// Driving into the bank from the water, she climbs out up it onto the land.
		if ((mode == Mode.WATER || mode == Mode.WADE) && speed > 0.03 && bankAhead(world, ahead)) {
			vy = Math.max(vy, 0.42);
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
		if (flying || mode == Mode.WATER || mode == Mode.WADE) {
			fallDistance = 0.0F;
		}
	}

	/** In the water, whether there is a bank in front of her she could climb: solid at her keel, clear above it. */
	private boolean bankAhead(World world, Vec3d ahead) {
		Vec3d c = getPos().add(ahead.multiply(NOSE + 0.3));
		Box low = new Box(c.x - 0.4, getY() + 0.05, c.z - 0.4, c.x + 0.4, getY() + 1.4, c.z + 0.4);
		Box high = new Box(c.x - 0.4, getY() + 1.6, c.z - 0.4, c.x + 0.4, getY() + 3.0, c.z + 0.4);
		return !world.isSpaceEmpty(this, low) && world.isSpaceEmpty(this, high);
	}

	/** Falling this fast, whether she will hit the ground (or the water) in the next few ticks. */
	private boolean aboutToHit(World world, double vy) {
		double reach = -vy * 6.0 + 2.0;
		Vec3d from = getPos().add(0.0, 0.2, 0.0);
		BlockHitResult hit = world.raycast(new RaycastContext(from, from.add(0.0, -reach, 0.0), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.ANY, this));
		return hit.getType() == HitResult.Type.BLOCK;
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

	/** What the driver's client says they are doing, and how her wings and raft are (STATE_ bits). */
	public void applyInput(ServerPlayerEntity player, ChittyControls controls, byte state) {
		if (getControllingPassenger() != player) {
			return;
		}
		int before = input.forward();
		input = controls;
		setState(state);
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
		// Her hitboxes along her length (the hamper's only while she has her hamper: it goes by itself when not).
		for (int i = 0; i < parts.length; i++) {
			if (ChittyPartEntity.wanted(this, i) && (parts[i] == null || parts[i].isRemoved())) {
				parts[i] = new ChittyPartEntity(world, this, i);
				world.spawnEntity(parts[i]);
			}
		}
		if (isLogicalSideForUpdatingMovement()) {
			input = ChittyControls.NONE;
			dataTracker.set(STATE, getState());
			dataTracker.set(STEER, (byte) 0);
			dataTracker.set(THROTTLE, (byte) 0);
		}
		if (backfireCooldown > 0) {
			backfireCooldown--;
		}
		// The start-up's (or a backfire's) two bangs, each a tongue of flame shot back out of the fishtail, a spatter of
		// sparks and a puff of dark smoke.
		if (startTicks >= 0) {
			startTicks++;
			if (startTicks == START_BANG_1 || startTicks == START_BANG_2) {
				Vec3d at = exhaust();
				float yawRad = getYaw() * MathHelper.RADIANS_PER_DEGREE;
				Vec3d back = new Vec3d(MathHelper.sin(yawRad), 0.0, -MathHelper.cos(yawRad));
				for (int i = 0; i < 14; i++) {
					double s = 0.10 + random.nextDouble() * 0.22;
					world.spawnParticles(i % 3 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME, at.x, at.y, at.z, 0,
							back.x * s + random.nextGaussian() * 0.02, 0.01 + random.nextGaussian() * 0.012,
							back.z * s + random.nextGaussian() * 0.02, 1.0);
				}
				world.spawnParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 3, 0.05, 0.02, 0.05, 0.0);
				Vec3d puff = at.add(back.multiply(0.5));
				world.spawnParticles(ParticleTypes.LARGE_SMOKE, puff.x, puff.y, puff.z, 14, 0.18, 0.08, 0.18, 0.03);
				world.spawnParticles(ParticleTypes.POOF, at.x, at.y, at.z, 4, 0.05, 0.05, 0.05, 0.03);
			}
			if (startTicks > START_BANG_2) {
				startTicks = -1;
			}
		}
		// Every so often as she runs, chitty chitty chitty ... bang bang.
		if (isEngineRunning() && backfireCooldown == 0 && random.nextInt(400) == 0) {
			bangBang();
		}
		// Settled so deep that the water closes over the seats: whoever is aboard is washed out of them.
		if (!isFloating()) {
			for (Entity passenger : List.copyOf(getPassengerList())) {
				if (passenger instanceof LivingEntity living && living.isSubmergedIn(FluidTags.WATER)) {
					passenger.stopRiding();
				}
			}
		}
		boolean afloat = getFluidHeight(FluidTags.WATER) > 0.05 && isFloating() && !isFlying();
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
		boolean raft = isFloating();
		boolean wet = getFluidHeight(FluidTags.WATER) > 0.05;
		// The wings come out and go away slowly, a stage at a time (the renderer poses them from this), except when
		// they spring out to catch her as she falls; the raft blows up as slowly.
		prevWingOpen = wingOpen;
		boolean falling = fly && !isOnGround() && motion.y < -0.3;
		wingOpen = MathHelper.clamp(wingOpen + (fly ? 1.0F : -1.0F) / (falling ? 10.0F : 48.0F), 0.0F, 1.0F);
		prevFloatOpen = floatOpen;
		floatOpen = MathHelper.clamp(floatOpen + (raft ? 1.0F : -1.0F) / 40.0F, 0.0F, 1.0F);
		if (fly != wingsShown) {
			wingsShown = fly;
			world.playSound(getX(), getY(), getZ(), fly ? Chitty.WINGS_OUT : Chitty.WINGS_IN, SoundCategory.NEUTRAL, 1.2F, 1.0F, false);
			// A puff of dust off the running boards as the wings start to swing.
			for (int i = 0; i < 12; i++) {
				Vec3d at = getPos().add(new Vec3d((random.nextBoolean() ? 1 : -1) * 0.9, 0.5, 0.2 + random.nextDouble() * 0.8)
						.rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE));
				world.addParticle(ParticleTypes.POOF, at.x, at.y, at.z, 0.0, 0.02, 0.0);
			}
		}
		if (raft != floatsShown) {
			floatsShown = raft;
			world.playSound(getX(), getY(), getZ(), raft ? Chitty.FLOATS : Chitty.FLOATS_DOWN, SoundCategory.NEUTRAL, 1.2F, 1.0F, false);
			for (int i = 0; raft && wet && i < 40; i++) {
				world.addParticle(ParticleTypes.SPLASH, getX() + (random.nextDouble() - 0.5) * 3.0, getY() + 0.4,
						getZ() + (random.nextDouble() - 0.5) * 3.0, 0.0, 0.1, 0.0);
			}
		}
		// Into the water fast: a splash, as big as she is going fast, and spray all round her.
		double through = motion.length();
		if (wet && !wasWet && through > 0.12 && age > 5) {
			float size = (float) MathHelper.clamp(through * 1.2, 0.3, 1.5);
			world.playSound(getX(), getY(), getZ(), through > 0.5 ? SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED : SoundEvents.ENTITY_GENERIC_SPLASH,
					SoundCategory.NEUTRAL, size, 0.75F + random.nextFloat() * 0.2F, false);
			for (int i = 0; i < 20 + (int) (60 * size); i++) {
				world.addParticle(ParticleTypes.SPLASH, getX() + (random.nextDouble() - 0.5) * 3.5, getY() + 0.3,
						getZ() + (random.nextDouble() - 0.5) * 3.5, (random.nextDouble() - 0.5) * 0.3, 0.2 + random.nextDouble() * 0.3 * size,
						(random.nextDouble() - 0.5) * 0.3);
			}
		}
		wasWet = wet;
		// While the raft blows up in the water, bubbles round its edge.
		if (raft && wet && floatOpen < 1.0F && age % 2 == 0) {
			Vec3d at = getPos().add(new Vec3d((random.nextDouble() - 0.5) * 3.0, 0.3, (random.nextDouble() - 0.5) * 6.0)
					.rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE));
			world.addParticle(ParticleTypes.BUBBLE_POP, at.x, at.y, at.z, 0.0, 0.05, 0.0);
		}

		float yawRad = getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d ahead = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
		double forward = motion.dotProduct(ahead);
		prevWheelSpin = wheelSpin;
		// On the ground the wheels roll with the road; in the air, or laid flat on the raft, they turn over slowly.
		wheelSpin += fly && wingOpen > 0.5F && !isOnGround() || floatOpen > 0.5F ? 0.06F : (float) (forward / 0.46);
		prevPropSpin = propSpin;
		// The propellers spin up as the masts come up, and run down as they go.
		float raise = MathHelper.clamp(wingOpen * 2.0F - 1.0F, 0.0F, 1.0F);
		propSpin += raise * raise * (0.9F + (float) motion.length() * 0.6F);
		prevScrewSpin = screwSpin;
		int throttle = isLogicalSideForUpdatingMovement() ? clientControls.forward() : dataTracker.get(THROTTLE);
		screwSpin += wet && raft ? 0.15F + Math.abs(throttle) * 0.6F + (float) Math.abs(forward) : 0.0F;
		int turn = isLogicalSideForUpdatingMovement() ? clientControls.turn() : dataTracker.get(STEER);
		prevSteer = steer;
		steer += (turn - steer) * 0.3F;
		boolean aloft = fly && !isOnGround() && !(wet && raft);
		prevBank = bank;
		bank += ((aloft ? -turn * 20.0F : 0.0F) - bank) * 0.12F;
		prevPitch = pitch;
		double h = motion.horizontalLength();
		float climb = aloft && h > 0.05 ? (float) Math.toDegrees(Math.atan2(motion.y, h)) : 0.0F;
		pitch += (MathHelper.clamp(climb, -18.0F, 18.0F) - pitch) * 0.2F;

		// The gear lever forward or back with the pedals; the handbrake on when she stands with nobody pushing her.
		prevGearLever = gearLever;
		gearLever += (throttle - gearLever) * 0.25F;
		prevBrakeLever = brakeLever;
		float brake = throttle == 0 && Math.abs(forward) < 0.02 ? 1.0F : 0.0F;
		brakeLever += (brake - brakeLever) * 0.2F;
		// The starting handle, swung as she is started.
		prevCrankSpin = crankSpin;
		if (crankTicks > 0) {
			crankTicks--;
			crankSpin += 0.55F;
		}
		if (ejectTicks < EJECT_SETTLED) {
			ejectTicks++;
		}
		// The dials, each needle easing round to its reading; the rev counter's trembles with the engine.
		System.arraycopy(dials, 0, prevDials, 0, dials.length);
		float revs = isEngineRunning()
				? MathHelper.clamp(0.18F + 0.55F * (float) (h / ROAD_TOP) + 0.2F * Math.max(0, throttle) + (random.nextFloat() - 0.5F) * 0.04F,
						0.0F, 1.0F)
				: 0.0F;
		float[] readings = {(float) MathHelper.clamp(h / AIR_TOP, 0.0, 1.0),
				MathHelper.clamp((float) (getY() - world.getSeaLevel()) / 200.0F, 0.0F, 1.0F), revs};
		float[] rates = {0.2F, 0.1F, 0.3F};
		for (int i = 0; i < dials.length; i++) {
			dials[i] += (readings[i] - dials[i]) * rates[i];
		}

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

	/** The gear lever: -1 (back) to 1 (forward). */
	public float getGearLever(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevGearLever, gearLever);
	}

	/** The handbrake: 0 (off) to 1 (on). */
	public float getBrakeLever(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevBrakeLever, brakeLever);
	}

	/** How far round the starting handle has been swung (radians). */
	public float getCrankSpin(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevCrankSpin, crankSpin);
	}

	/** How far round its dial a needle on the dashboard is (0 to 1): 0, her speed; 1, her height; 2, the engine's revs. */
	public float getDial(int dial, float tickDelta) {
		return MathHelper.lerp(tickDelta, prevDials[dial], dials[dial]);
	}

	/**
	 * How far the back seat stands up on its springs (blocks): thrown up as the ejector goes off, then bouncing back
	 * down on them, each bounce smaller, until it settles.
	 */
	public float getEjectLift(float tickDelta) {
		float t = ejectTicks + tickDelta;
		if (t >= EJECT_SETTLED) {
			return 0.0F;
		}
		if (t < 2.0F) {
			return 0.85F * t / 2.0F;
		}
		float after = t - 2.0F;
		return Math.max(-0.02F, 0.85F * (float) Math.exp(-after / 7.0F) * MathHelper.cos(after * 0.55F));
	}

	@Override
	public void handleStatus(byte status) {
		if (status == EJECT_STATUS) {
			ejectTicks = 0;
		} else {
			super.handleStatus(status);
		}
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
		wingsHeld = nbt.getBoolean("WingsHeld");
		floats = nbt.getBoolean("Floats");
		dataTracker.set(HAMPER, !nbt.contains("Hamper") || nbt.getBoolean("Hamper"));
		hamper.clear();
		if (nbt.contains("HamperItems")) {
			hamper.readNbtList(nbt.getList("HamperItems", NbtElement.COMPOUND_TYPE), getRegistryManager());
		}
		speed = nbt.getFloat("Speed");
		dataTracker.set(STATE, getState());
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putBoolean("Flying", flying);
		nbt.putBoolean("WingsHeld", wingsHeld);
		nbt.putBoolean("Floats", floats);
		nbt.putBoolean("Hamper", hasHamper());
		nbt.put("HamperItems", hamper.toNbtList(getRegistryManager()));
		nbt.putFloat("Speed", speed);
	}

	/** For tests: the passengers in seat order. */
	public List<Entity> seats() {
		return getPassengerList();
	}
}
