package io.github.kortev.chitty.airship;

import io.github.kortev.chitty.ChittyControls;
import io.github.kortev.shootingstar.registry.ModCriteria;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Dismounting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
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
 * Baron Bomburst's airship, from the film: a gas envelope 34 blocks long over a little gilded gondola hung from it on
 * wires. Eight can stand in the gondola and walk about in it (they cannot fall out); whoever walks up to the wheel in
 * the bow takes it and flies her, and sneaks to let it go. She lifts six, and with more aboard (a load on her grapple
 * counts as one) she cannot climb and sinks slowly, as she does in the film. She hovers where she is left. Her crew can
 * let her grapple down to seize what it touches (a mob, a player, a dropped item, a boat or a car) and wind it up to
 * carry it, let down her rope ladder (anyone can climb it, and climbing off its top boards her) and drop bombs from the
 * rack in her gondola; the pilot can throw a passenger overboard. Sneaking gets anyone else off: beside her when she is
 * down, or onto her rope ladder in the air, which lets itself down for them.
 *
 * <p>Like Chitty she is moved by her pilot's client, and by the server when nobody pilots her. Positions are in blocks;
 * local offsets are at yaw 0, x to her left, z forward, from the middle of the bottom of her gondola (as in
 * tools/airship_model.py, whose Blender axes map to these as (x, y, z) to (-x, z, y)).
 */
public class AirshipEntity extends Entity {
	private static final TrackedData<Integer> WOBBLE_TICKS = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> WOBBLE_SIDE = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Float> WOBBLE_STRENGTH = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** Where each passenger stands on the floor of the gondola: by entity id, x and z in thousandths packed in an int. */
	private static final TrackedData<NbtCompound> STANDS = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
	/** The entity id of whoever has the wheel, or -1. */
	private static final TrackedData<Integer> HELM = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Byte> STEER = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final TrackedData<Byte> THROTTLE = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final TrackedData<Byte> CLIMB = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final TrackedData<Float> HOOK_DROP = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Byte> HOOK_STATE = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final TrackedData<Float> LADDER = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Byte> BOMBS = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.BYTE);

	/**
	 * Where people come aboard (the stand_ markers): at the wheel, then two, two and three across. From there they walk
	 * where they like on the floor of the gondola.
	 */
	public static final Vec3d[] PLACES = {new Vec3d(0.0, 0.42, 1.02), new Vec3d(0.45, 0.42, 0.52), new Vec3d(-0.45, 0.42, 0.52),
			new Vec3d(0.45, 0.42, 0.02), new Vec3d(-0.45, 0.42, 0.02), new Vec3d(0.55, 0.42, -0.46), new Vec3d(0.0, 0.42, -0.46),
			new Vec3d(-0.55, 0.42, -0.46)};
	public static final int PILOT = 0;
	/** Where the pilot stands, at the wheel: walk up to it to take it. */
	public static final Vec3d HELM_SPOT = PLACES[PILOT];
	/** How near the wheel's place someone must come to take it. */
	static final double HELM_REACH = 0.22;
	/** The floor of the gondola as far as people's middles can go: across it, and from the engine section to the wheel. */
	public static final double FLOOR_HALF_WIDTH = 0.6;
	public static final double FLOOR_AFT = -0.46;
	public static final double FLOOR_FORE = 1.02;
	public static final double FLOOR_Y = 0.42;
	/** How fast people walk about in the gondola (blocks a tick), and how close they come to one another. */
	public static final double WALK = 0.12;
	public static final double ELBOW_ROOM = 0.5;
	/** How far down her ladder must hang for someone getting off in the air to climb onto it. */
	static final double LADDER_OFF = 2.0;
	/** What the third-person camera turns about while riding her (the middle of her), and how far back it stands. */
	public static final Vec3d VIEW_CENTRE = new Vec3d(0.0, 5.0, -3.5);
	public static final float VIEW_DISTANCE = 24.0F;
	/** How many she lifts: one more and she sinks. */
	public static final int LIFT = 6;
	/** Where the grapple's rope comes out under her keel (the line marker). */
	public static final Vec3d LINE_OUT = new Vec3d(0.0, 0.0, 0.25);
	/** From the top of the grapple's ring to its tines, where it takes hold. */
	public static final double HOOK_GRIP = 0.95;
	/** How far below her keel she carries a load. */
	public static final double CARRY = 3.0;
	/** How far the grapple's rope and the ladder let down. */
	public static final double LINE_MAX = 32.0;
	/** Where the rope ladder hangs from the rail on her left (ladder_top), and the line a climber hangs on, just outside it. */
	public static final Vec3d LADDER_TOP = new Vec3d(1.05, 1.44, -0.15);
	public static final Vec3d LADDER_LINE = new Vec3d(1.3, 1.44, -0.15);
	public static final float LADDER_PITCH = 0.32F;
	/** The rack: six bombs across the front of the engine section, and where they fall out under her. */
	public static final int RACK = 6;
	static final double RACK_Z = -0.59;
	/** Where the engine's exhaust comes out, at the stern (the exhaust marker). */
	public static final Vec3d EXHAUST = new Vec3d(-0.6, 1.8, -1.55);
	/** Where the propellers turn (prop_r, prop_l). */
	public static final Vec3d PROP_R = new Vec3d(-1.85, 2.3, -2.7);
	public static final Vec3d PROP_L = new Vec3d(1.85, 2.3, -2.7);
	/**
	 * Her envelope and tail as boxes along her (each z, the height of its bottom, its width and height), for her
	 * hitboxes (AirshipPartEntity) and to keep her envelope out of hills and trees. From the envelope's profile in
	 * tools/airship_model.py.
	 */
	static final double[][] ENVELOPE = {{9.5, 6.45, 4.0, 4.3}, {4.5, 4.7, 6.4, 6.8}, {-1.0, 3.8, 7.6, 8.1},
			{-6.5, 3.8, 7.6, 8.1}, {-12.0, 4.7, 6.4, 6.8}, {-17.0, 6.6, 3.8, 4.0}, {-17.0, 0.0, 3.0, 4.4}};

	// Speeds in blocks per tick: slow, as an airship is.
	static final double TOP = 0.42;
	static final double ACCEL = 0.005;
	static final double REVERSE_TOP = 0.12;
	static final double RISE = 0.11;
	static final double SINK = 0.14;
	/** How fast she sinks overloaded. */
	static final double HEAVY_SINK = 0.035;
	/** Degrees a tick she turns at speed, and standing (her propellers turn her a little even then). */
	static final float TURN = 1.6F;
	static final float TURN_STANDING = 0.7F;
	static final double HOOK_DOWN = 0.32;
	static final double HOOK_UP = 0.2;
	static final int BOMB_COOLDOWN = 30;

	/** The grapple: stowed, paying out, waiting down, lifting a load, holding it, setting it down, winding up empty. */
	public enum Hook { UP, LOWERING, DOWN, LIFTING, HOLDING, SETTING, RAISING }

	/** What a crew member can ask of her (AirshipActionPayload). */
	public static final int ACTION_GRAPPLE = 0;
	public static final int ACTION_LADDER = 1;
	public static final int ACTION_BOMB = 2;
	public static final int ACTION_OVERBOARD = 3;

	/** Client-only behaviour the common code calls into. Set by the client initializer. */
	public interface ClientHooks {
		ChittyControls controls(AirshipEntity ship);

		void sync(AirshipEntity ship, ChittyControls controls);

		void tick(AirshipEntity ship);

		/** Where this client's player stands in her as they walk about (theirs to move), or null for anyone else. */
		@Nullable
		Vec3d stand(AirshipEntity ship, Entity passenger);
	}

	@Nullable
	public static ClientHooks client;

	/** The airships with their ladders down, on each side, for climbers (LivingEntity.isClimbing). */
	private static final Set<AirshipEntity> LADDERS_SERVER = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Set<AirshipEntity> LADDERS_CLIENT = Collections.newSetFromMap(new WeakHashMap<>());

	// Movement, on whichever side is moving her.
	private float speed;
	private double climbSpeed;
	private float yawVelocity;
	private boolean wasMoving;
	private Vec3d lastPos;
	private Vec3d motion = Vec3d.ZERO;
	private ChittyControls clientControls = ChittyControls.NONE;

	// The server's view of the pilot, the grapple, the ladder and the rack.
	private ChittyControls input = ChittyControls.NONE;
	private Hook hook = Hook.UP;
	private double hookDrop;
	@Nullable
	private AirshipHookEntity hookEntity;
	private boolean ladderDown;
	private double ladderTarget;
	private int bombCooldown;
	private int creakTicks = 200;

	// Interpolation of an airship someone else is moving.
	private int lerpTicks;
	private double lerpX;
	private double lerpY;
	private double lerpZ;
	private double lerpYaw;
	private double lerpPitch;

	// Looks, on clients.
	private float propSpin;
	private float prevPropSpin;
	private float steer;
	private float prevSteer;
	private float climbLook;
	private float prevClimbLook;
	private float shownDrop;
	private float prevShownDrop;
	private float shownLadder;
	private float prevShownLadder;
	private float bank;
	private float prevBank;
	private float tilt;
	private float prevTilt;

	// Where everyone stands on the floor of the gondola (the server's), who has the wheel, who may take it (they have
	// been away from it since they last let go), who let go by sneaking and is sneaking still, and the place a player
	// has asked for as they board.
	private final Map<Entity, Vec3d> stands = new LinkedHashMap<>();
	@Nullable
	private Entity helm;
	private final Set<Entity> mayTakeHelm = Collections.newSetFromMap(new WeakHashMap<>());
	private final Set<Entity> stillSneaking = Collections.newSetFromMap(new WeakHashMap<>());
	private int wantedPlace = -1;
	// On clients: where everyone is shown standing, eased towards where the server has them, and how far they stepped.
	private final Map<Entity, Vec3d> shown = new WeakHashMap<>();
	private final Map<Entity, Float> strides = new WeakHashMap<>();
	private final AirshipPartEntity[] parts = new AirshipPartEntity[AirshipPartEntity.COUNT];
	// When each player last stepped off her (her age then), so that one who has just stepped onto the ladder is not
	// taken straight back aboard.
	private final Map<UUID, Integer> steppedOff = new HashMap<>();

	public AirshipEntity(EntityType<? extends AirshipEntity> type, World world) {
		super(type, world);
		this.intersectionChecked = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(WOBBLE_TICKS, 0);
		builder.add(WOBBLE_SIDE, 1);
		builder.add(WOBBLE_STRENGTH, 0.0F);
		builder.add(STANDS, new NbtCompound());
		builder.add(HELM, -1);
		builder.add(STEER, (byte) 0);
		builder.add(THROTTLE, (byte) 0);
		builder.add(CLIMB, (byte) 0);
		builder.add(HOOK_DROP, 0.0F);
		builder.add(HOOK_STATE, (byte) 0);
		builder.add(LADDER, 0.0F);
		builder.add(BOMBS, (byte) 0);
	}

	// --- what she is ---------------------------------------------------------------------------------

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entry) {
		return new EntitySpawnS2CPacket(this, entry);
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(Airship.ITEM);
	}

	/** Her gas holds her up: she rises, sinks and hovers as she is flown, never falls. */
	@Override
	public boolean hasNoGravity() {
		return true;
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
				&& !(other instanceof AirshipPartEntity part && part.getShip() == this)
				&& !(other instanceof AirshipHookEntity);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	/** She is far longer and taller than her box: the envelope and tail reach 21 blocks behind it and 13 above. */
	@Override
	public Box getVisibilityBoundingBox() {
		return getBoundingBox().expand(22.0, 0.0, 22.0).stretch(0.0, 14.0, 0.0).stretch(0.0, -LINE_MAX - 2.0, 0.0);
	}

	/** Seen from as far off as something her size should be. */
	@Override
	public boolean shouldRender(double distance) {
		double d = 34.0 * 64.0 * getRenderDistanceMultiplier();
		return distance < d * d;
	}

	/** Hit, she rocks; hit hard enough (or by anyone in creative), she comes down and drops herself, as a boat does. */
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
		setDamageWobbleStrength(getDamageWobbleStrength() + amount * 2.5F);
		emitGameEvent(GameEvent.ENTITY_DAMAGE, source.getAttacker());
		boolean creative = source.getAttacker() instanceof PlayerEntity player && player.getAbilities().creativeMode;
		if (creative || getDamageWobbleStrength() > 60.0F) {
			if (!creative && getWorld().getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
				ItemStack stack = new ItemStack(Airship.ITEM);
				if (hasCustomName()) {
					stack.set(DataComponentTypes.CUSTOM_NAME, getCustomName());
				}
				dropStack(stack);
				if (getBombs() > 0) {
					dropStack(new ItemStack(Airship.BOMB, getBombs()));
				}
			}
			releaseLoad();
			discard();
		}
		return true;
	}

	@Override
	public void remove(RemovalReason reason) {
		releaseLoad();
		super.remove(reason);
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

	public boolean isEngineRunning() {
		return getControllingPassenger() != null;
	}

	/** Forward speed in blocks per tick, as last moved (any side). */
	public double getSpeed() {
		return motion.horizontalLength();
	}

	public double getAirSpeed() {
		return motion.length();
	}

	/** How many she is carrying: everyone aboard, and a load on the grapple. */
	public int getLoad() {
		return getPassengerList().size() + (getHookState() == Hook.HOLDING || getHookState() == Hook.LIFTING
				|| getHookState() == Hook.SETTING ? 1 : 0);
	}

	/** Carrying more than she lifts: she cannot climb, and sinks. */
	public boolean isOverloaded() {
		return getLoad() > LIFT;
	}

	public int getBombs() {
		return dataTracker.get(BOMBS);
	}

	public Hook getHookState() {
		return Hook.values()[MathHelper.clamp(dataTracker.get(HOOK_STATE), 0, Hook.values().length - 1)];
	}

	/** How far her grapple hangs below her keel, as the server last had it. */
	public double getHookDrop() {
		return dataTracker.get(HOOK_DROP);
	}

	/** How far her ladder hangs down, as the server last had it. */
	public double getLadder() {
		return dataTracker.get(LADDER);
	}

	private Vec3d local(Vec3d offset) {
		return getPos().add(offset.rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE));
	}

	/** The top of the grapple's ring, where its rope ties on, in the world. */
	public Vec3d hookTop() {
		return local(LINE_OUT).add(0.0, -hookDrop, 0.0);
	}

	/** Where the grapple's tines take hold, in the world. */
	public Vec3d hookGrip() {
		return hookTop().add(0.0, -HOOK_GRIP, 0.0);
	}

	// --- passengers ------------------------------------------------------------------------------------

	/**
	 * Using her: with bombs in hand, they go into her rack (six at most); otherwise you board her at the free place
	 * nearest where you clicked.
	 */
	@Override
	public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
		ItemStack held = player.getStackInHand(hand);
		if (held.isOf(Airship.BOMB)) {
			int room = RACK - getBombs();
			if (room <= 0) {
				return ActionResult.PASS;
			}
			if (!getWorld().isClient) {
				int n = Math.min(room, held.getCount());
				dataTracker.set(BOMBS, (byte) (getBombs() + n));
				if (!player.getAbilities().creativeMode) {
					held.decrement(n);
				}
				getWorld().playSound(null, getX(), getY(), getZ(), Airship.LOAD, SoundCategory.NEUTRAL, 1.0F, 1.0F);
			}
			return ActionResult.success(getWorld().isClient);
		}
		Vec3d localHit = hitPos.rotateY(getYaw() * MathHelper.RADIANS_PER_DEGREE);
		int place = nearestFreePlace(localHit);
		if (place < 0) {
			return ActionResult.PASS;
		}
		if (!getWorld().isClient) {
			return board(player, place) ? ActionResult.CONSUME : ActionResult.PASS;
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

	/** Whether someone stands at (or close by) one of her places. */
	private boolean taken(int place) {
		for (Map.Entry<Entity, Vec3d> e : stands.entrySet()) {
			if (e.getKey().getVehicle() == this && apart(e.getValue(), PLACES[place]) < ELBOW_ROOM * 0.9) {
				return true;
			}
		}
		return false;
	}

	private static double apart(Vec3d a, Vec3d b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** The free place nearest a point in or beside her (local), or -1 if every place is taken. */
	private int nearestFreePlace(Vec3d local) {
		int best = -1;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < PLACES.length; i++) {
			if (taken(i)) {
				continue;
			}
			double dx = PLACES[i].x - local.x;
			double dz = PLACES[i].z - local.z;
			double distance = dx * dx + dz * dz;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return best;
	}

	/** The place with the most room round it, for someone coming aboard when people stand about in all of them. */
	private int roomiestPlace() {
		int best = PLACES.length - 1;
		double bestRoom = -1.0;
		for (int i = 0; i < PLACES.length; i++) {
			double room = Double.MAX_VALUE;
			for (Vec3d at : stands.values()) {
				room = Math.min(room, apart(at, PLACES[i]));
			}
			if (room > bestRoom) {
				bestRoom = room;
				best = i;
			}
		}
		return best;
	}

	/** Puts a passenger aboard at a particular place (0, at the wheel, to 7), if it is free. */
	public boolean board(Entity passenger, int place) {
		if (place < 0 || place >= PLACES.length || taken(place)) {
			return false;
		}
		wantedPlace = place;
		boolean in = passenger.startRiding(this);
		wantedPlace = -1;
		return in;
	}

	/** Where a passenger stands on the floor of the gondola (local), or null if they are not aboard. */
	@Nullable
	public Vec3d standOf(Entity passenger) {
		if (!hasPassenger(passenger)) {
			return null;
		}
		if (!getWorld().isClient) {
			return stands.get(passenger);
		}
		Vec3d mine = client != null ? client.stand(this, passenger) : null;
		if (mine != null) {
			return mine;
		}
		Vec3d seen = shown.get(passenger);
		return seen != null ? seen : syncedStandOf(passenger);
	}

	/** Where the server has a passenger standing, as it last said (null until it has). */
	@Nullable
	public Vec3d syncedStandOf(Entity passenger) {
		NbtCompound all = dataTracker.get(STANDS);
		String key = Integer.toString(passenger.getId());
		return all.contains(key) ? unpack(all.getInt(key)) : null;
	}

	private static int pack(Vec3d stand) {
		return (int) Math.round(stand.x * 1000.0) << 16 | (int) Math.round(stand.z * 1000.0) & 0xFFFF;
	}

	private static Vec3d unpack(int packed) {
		return new Vec3d((short) (packed >> 16) / 1000.0, FLOOR_Y, (short) packed / 1000.0);
	}

	/** A point kept on the floor of the gondola (local), where people can stand. */
	public static Vec3d onFloor(double x, double z) {
		return new Vec3d(MathHelper.clamp(x, -FLOOR_HALF_WIDTH, FLOOR_HALF_WIDTH), FLOOR_Y, MathHelper.clamp(z, FLOOR_AFT, FLOOR_FORE));
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().size() < PLACES.length;
	}

	/** The pilot: whoever has the wheel, if it is a player still aboard. */
	@Override
	@Nullable
	public LivingEntity getControllingPassenger() {
		if (!getWorld().isClient) {
			return helm instanceof PlayerEntity player && player.getVehicle() == this ? player : null;
		}
		int id = dataTracker.get(HELM);
		if (id >= 0) {
			for (Entity passenger : getPassengerList()) {
				if (passenger.getId() == id) {
					return passenger instanceof PlayerEntity player ? player : null;
				}
			}
		}
		return null;
	}

	private void setHelm(@Nullable Entity who) {
		helm = who;
		dataTracker.set(HELM, who == null ? -1 : who.getId());
	}

	private void takeHelm(Entity player) {
		setHelm(player);
		stands.put(player, HELM_SPOT);
		mayTakeHelm.remove(player);
	}

	@Override
	protected void addPassenger(Entity passenger) {
		super.addPassenger(passenger);
		if (!getWorld().isClient) {
			int place = wantedPlace >= 0 && !taken(wantedPlace) ? wantedPlace : -1;
			// Nobody chose a place: a player takes the first free one from the wheel back, anything else from the back.
			for (int k = 0; place < 0 && k < PLACES.length; k++) {
				int i = passenger instanceof PlayerEntity ? k : PLACES.length - 1 - k;
				if (!taken(i)) {
					place = i;
				}
			}
			if (place < 0) {
				place = roomiestPlace();
			}
			stands.put(passenger, PLACES[place]);
			if (passenger instanceof PlayerEntity) {
				if (place == PILOT && getControllingPassenger() == null) {
					takeHelm(passenger);
				} else {
					mayTakeHelm.add(passenger);
				}
			}
			publishStands();
			if (passenger instanceof ServerPlayerEntity player) {
				ModCriteria.fire(player, "airship_board");
			}
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!getWorld().isClient) {
			stands.remove(passenger);
			mayTakeHelm.remove(passenger);
			stillSneaking.remove(passenger);
			if (helm == passenger) {
				setHelm(null);
			}
			publishStands();
			if (passenger instanceof PlayerEntity) {
				steppedOff.put(passenger.getUuid(), age);
			}
		} else {
			shown.remove(passenger);
			strides.remove(passenger);
		}
	}

	private void publishStands() {
		NbtCompound all = new NbtCompound();
		for (Map.Entry<Entity, Vec3d> e : stands.entrySet()) {
			all.putInt(Integer.toString(e.getKey().getId()), pack(e.getValue()));
		}
		dataTracker.set(STANDS, all);
	}

	/**
	 * Someone aboard has walked (their client says where to, AirshipWalkPayload): kept to the floor of the gondola and
	 * to a walker's pace. Walking up to the wheel takes it, if nobody has it and they have been away from it since they
	 * last let go.
	 */
	public void walk(ServerPlayerEntity player, double x, double z) {
		Vec3d from = stands.get(player);
		if (from == null || player.getVehicle() != this || helm == player) {
			return;
		}
		Vec3d to = onFloor(x, z);
		Vec3d step = to.subtract(from);
		double most = WALK * 5.0;
		if (step.lengthSquared() > most * most) {
			// No further than a few steps at once (a late packet), whatever the client says.
			to = from.add(step.normalize().multiply(most));
		}
		stands.put(player, to);
		double fromHelm = apart(to, HELM_SPOT);
		if (fromHelm > HELM_REACH + 0.15) {
			mayTakeHelm.add(player);
		} else if (fromHelm <= HELM_REACH && mayTakeHelm.contains(player) && getControllingPassenger() == null) {
			takeHelm(player);
		}
		publishStands();
	}

	/** Everyone stands, their feet on the floor of the gondola where they are in it, and turns with her as she turns. */
	@Override
	protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
		if (!hasPassenger(passenger)) {
			return;
		}
		Vec3d stand = standOf(passenger);
		if (stand == null) {
			stand = PLACES[Math.max(0, getPassengerList().indexOf(passenger)) % PLACES.length];
		}
		Vec3d at = local(stand);
		positionUpdater.accept(passenger, at.x, at.y, at.z);
		if (passenger instanceof LivingEntity) {
			float turn = MathHelper.wrapDegrees(getYaw() - prevYaw);
			passenger.setYaw(passenger.getYaw() + turn);
			passenger.setHeadYaw(passenger.getHeadYaw() + turn);
		}
	}

	/** How far a passenger stepped last tick as they walked about her, for their legs (AirshipStrideMixin). */
	public float strideOf(Entity passenger) {
		return strides.getOrDefault(passenger, 0.0F);
	}

	/**
	 * Someone aboard is sneaking (PlayerEntity.shouldDismount): whether they get off now. At the wheel, sneaking lets go
	 * of it instead (and sneaking again, once they have stopped, gets them off). Off they get beside her when she is down
	 * or nearly; in the air, onto her rope ladder, which lets itself down for them if it is up, while they keep sneaking.
	 */
	public boolean letsGo(PlayerEntity player) {
		boolean down = isOnGround() || heightAboveGround() < 2.0;
		if (getWorld().isClient) {
			return down || getLadder() > LADDER_OFF;
		}
		if (stillSneaking.contains(player)) {
			return false;
		}
		if (getControllingPassenger() == player) {
			setHelm(null);
			stillSneaking.add(player);
			return false;
		}
		if (down || getLadder() > LADDER_OFF) {
			return true;
		}
		if (!ladderDown) {
			toggleLadder();
			player.sendMessage(Text.translatable("hud.shootingstar.airship.ladder_off"), true);
		}
		return false;
	}

	/** Off onto the ground beside the gondola if she is down; up in the air, onto the top of her ladder. */
	@Override
	public Vec3d updatePassengerForDismount(LivingEntity passenger) {
		if (!isOnGround() && heightAboveGround() >= 2.0 && getLadder() > 1.0) {
			return local(LADDER_LINE).add(0.0, -1.8, 0.0);
		}
		float yawRad = getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d left = new Vec3d(MathHelper.cos(yawRad), 0.0, MathHelper.sin(yawRad));
		Vec3d rel = passenger.getPos().subtract(getPos());
		double side = rel.dotProduct(left) >= 0 ? 1.0 : -1.0;
		for (double s : new double[] {side, -side}) {
			Vec3d at = getPos().add(left.multiply(s * 1.6));
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

	private double heightAboveGround() {
		Vec3d from = getPos().add(0.0, 0.05, 0.0);
		BlockHitResult hit = getWorld().raycast(new RaycastContext(from, from.add(0.0, -LINE_MAX, 0.0),
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, this));
		return hit.getType() == HitResult.Type.BLOCK ? from.y - hit.getPos().y : LINE_MAX;
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
		Set<AirshipEntity> ladders = getWorld().isClient ? LADDERS_CLIENT : LADDERS_SERVER;
		if (getLadder() > 0.5 && !isRemoved()) {
			ladders.add(this);
		} else {
			ladders.remove(this);
		}
		if (getWorld() instanceof ServerWorld world) {
			serverTick(world);
		} else {
			clientTick();
		}
	}

	private void adopt() {
		float yawRad = getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d ahead = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
		speed = (float) motion.dotProduct(ahead);
		climbSpeed = motion.y;
	}

	/** Sets her going (blocks a tick, forward). For tests. */
	public void launch(float speed) {
		this.speed = speed;
	}

	private void drive(ChittyControls in) {
		boolean piloted = getControllingPassenger() != null;
		if (!piloted) {
			in = ChittyControls.NONE;
		}
		int throttle = in.forward();
		if (throttle > 0) {
			speed = (float) (speed < 0 ? Math.min(0.0, speed + ACCEL * 2) : Math.min(TOP, speed + ACCEL * (1.0 - 0.6 * speed / TOP)));
		} else if (throttle < 0) {
			speed = (float) (speed > 0 ? Math.max(0.0, speed - ACCEL * 2) : Math.max(-REVERSE_TOP, speed - ACCEL * 0.7));
		} else {
			speed *= 0.985F;
			if (Math.abs(speed) < 0.002F) {
				speed = 0.0F;
			}
		}
		float rate = in.turn() * (TURN_STANDING + (TURN - TURN_STANDING) * (float) MathHelper.clamp(Math.abs(speed) / TOP, 0.0, 1.0));
		yawVelocity += (rate - yawVelocity) * 0.08F;
		setYaw(getYaw() - yawVelocity);

		double target = in.up() ? RISE : in.down() ? -SINK : 0.0;
		if (isOverloaded()) {
			// Too many aboard: she cannot climb, and settles slowly however hard she is flown.
			target = Math.min(target, 0.0) - HEAVY_SINK;
		}
		climbSpeed += (target - climbSpeed) * 0.06;
		if (isOnGround() && climbSpeed < 0.0) {
			climbSpeed = 0.0;
		}
		float yawRad = getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d ahead = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
		Vec3d step = ahead.multiply(speed).add(0.0, climbSpeed, 0.0);
		step = clearEnvelope(step);
		setVelocity(step);
		move(MovementType.SELF, step);
		if (horizontalCollision) {
			speed *= 0.3F;
		}
		if (verticalCollision) {
			climbSpeed = 0.0;
		}
		fallDistance = 0.0F;
	}

	/** The step as far as her envelope and tail can take it without running into the ground, a hill or a tree. */
	private Vec3d clearEnvelope(Vec3d step) {
		if (step.lengthSquared() < 1.0E-8 || !envelopeFree(Vec3d.ZERO)) {
			// Standing still, or already in among the trees (put down there): she does not wedge herself.
			return step;
		}
		if (envelopeFree(step)) {
			return step;
		}
		Vec3d flat = new Vec3d(step.x, 0.0, step.z);
		if (step.y != 0.0 && envelopeFree(flat)) {
			climbSpeed = 0.0;
			return flat;
		}
		Vec3d upright = new Vec3d(0.0, step.y, 0.0);
		if (envelopeFree(upright)) {
			speed *= 0.2F;
			return upright;
		}
		speed = 0.0F;
		climbSpeed = 0.0;
		return Vec3d.ZERO;
	}

	private boolean envelopeFree(Vec3d step) {
		World world = getWorld();
		float yawRad = getYaw() * MathHelper.RADIANS_PER_DEGREE;
		for (double[] box : ENVELOPE) {
			Vec3d c = getPos().add(step).add(new Vec3d(0.0, 0.0, box[0]).rotateY(-yawRad));
			double half = box[2] * 0.42;
			Box b = new Box(c.x - half, c.y + box[1] + 0.3, c.z - half, c.x + half, c.y + box[1] + box[3] - 0.3, c.z + half);
			if (hasSolid(world, b)) {
				return false;
			}
		}
		return true;
	}

	private boolean hasSolid(World world, Box box) {
		for (var shape : world.getBlockCollisions(this, box)) {
			if (!shape.isEmpty()) {
				return true;
			}
		}
		return false;
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

	// --- the server: the pilot's wishes, the grapple, the ladder, the bombs ----------------------------

	/** What the pilot's client says they are doing. */
	public void applyInput(ServerPlayerEntity player, ChittyControls controls) {
		if (getControllingPassenger() != player) {
			return;
		}
		input = controls;
		dataTracker.set(STEER, (byte) controls.turn());
		dataTracker.set(THROTTLE, (byte) controls.forward());
		dataTracker.set(CLIMB, (byte) (controls.up() ? 1 : controls.down() ? -1 : 0));
	}

	/** A crew member's request (the ACTION_ codes): the grapple, the ladder and the bombs for anyone aboard, the pilot alone throws people overboard. */
	public void act(ServerPlayerEntity player, int action) {
		if (player.getVehicle() != this) {
			return;
		}
		switch (action) {
			case ACTION_GRAPPLE -> workGrapple();
			case ACTION_LADDER -> toggleLadder();
			case ACTION_BOMB -> dropBomb(player);
			case ACTION_OVERBOARD -> {
				if (getControllingPassenger() == player) {
					overboard();
				}
			}
			default -> {
			}
		}
	}

	/**
	 * The grapple's one lever: stowed, it is let down; going down or waiting down, it is wound up; with a load on it, the
	 * load is set down on the ground and let go; setting one down, it is lifted again.
	 */
	public void workGrapple() {
		Hook next = switch (hook) {
			case UP, RAISING -> Hook.LOWERING;
			case LOWERING, DOWN -> Hook.RAISING;
			case LIFTING, HOLDING -> Hook.SETTING;
			case SETTING -> Hook.LIFTING;
		};
		setHook(next);
		getWorld().playSound(null, getX(), getY(), getZ(), Airship.WINCH, SoundCategory.NEUTRAL, 1.0F, next == Hook.LOWERING
				|| next == Hook.SETTING ? 1.1F : 0.9F);
	}

	private void setHook(Hook state) {
		hook = state;
		dataTracker.set(HOOK_STATE, (byte) state.ordinal());
	}

	public void toggleLadder() {
		ladderDown = !ladderDown;
		getWorld().playSound(null, getX(), getY(), getZ(), Airship.LADDER, SoundCategory.NEUTRAL, 1.0F, ladderDown ? 1.0F : 0.85F);
	}

	/** Drops the next bomb from the rack, straight down through the floor of the gondola. */
	public void dropBomb(@Nullable ServerPlayerEntity by) {
		if (!(getWorld() instanceof ServerWorld world) || bombCooldown > 0 || getBombs() <= 0) {
			return;
		}
		int i = getBombs() - 1;
		dataTracker.set(BOMBS, (byte) i);
		bombCooldown = BOMB_COOLDOWN;
		Vec3d at = local(new Vec3d(0.7 - 0.28 * i, -0.75, RACK_Z));
		AirshipBombEntity bomb = new AirshipBombEntity(world, this, at, motion.add(0.0, -0.05, 0.0));
		world.spawnEntity(bomb);
		world.playSound(null, at.x, at.y, at.z, Airship.BOMB_DROP, SoundCategory.NEUTRAL, 1.6F, 1.0F);
		if (by != null) {
			ModCriteria.fire(by, "airship_bomb");
		}
	}

	/** The pilot's last resort: whoever stands furthest aft goes over the side. */
	public void overboard() {
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		Entity passenger = null;
		Vec3d stand = null;
		for (Map.Entry<Entity, Vec3d> e : stands.entrySet()) {
			if (e.getKey() != helm && e.getKey().getVehicle() == this && (stand == null || e.getValue().z < stand.z)) {
				passenger = e.getKey();
				stand = e.getValue();
			}
		}
		if (passenger == null) {
			return;
		}
		passenger.stopRiding();
		double side = stand.x >= 0 ? 1.0 : -1.0;
		Vec3d out = local(new Vec3d(side * 1.6, 1.6, stand.z));
		passenger.requestTeleport(out.x, out.y, out.z);
		float yawRad = getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d push = new Vec3d(MathHelper.cos(yawRad), 0.0, MathHelper.sin(yawRad)).multiply(side * 0.4);
		passenger.setVelocity(motion.add(push).add(0.0, 0.25, 0.0));
		passenger.velocityModified = true;
		world.playSound(null, out.x, out.y, out.z, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.NEUTRAL, 1.0F, 0.8F);
	}

	private void serverTick(ServerWorld world) {
		for (int i = 0; i < parts.length; i++) {
			if (parts[i] == null || parts[i].isRemoved()) {
				parts[i] = new AirshipPartEntity(world, this, i);
				world.spawnEntity(parts[i]);
			}
		}
		if (isLogicalSideForUpdatingMovement()) {
			input = ChittyControls.NONE;
			dataTracker.set(STEER, (byte) 0);
			dataTracker.set(THROTTLE, (byte) 0);
			dataTracker.set(CLIMB, (byte) 0);
		}
		if (bombCooldown > 0) {
			bombCooldown--;
		}
		// Whoever let go of the wheel by sneaking can sneak again (to get off) once they have stopped.
		stillSneaking.removeIf(e -> !e.isSneaking() || e.getVehicle() != this);
		tickGrapple(world);
		tickLadder(world);
		// Now and then the great envelope creaks and its rigging groans.
		if (--creakTicks <= 0) {
			creakTicks = 160 + random.nextInt(400);
			Vec3d at = local(new Vec3d(0.0, 4.0, -3.7));
			world.playSound(null, at.x, at.y, at.z, Airship.CREAK, SoundCategory.NEUTRAL, 1.4F, 0.85F + random.nextFloat() * 0.3F);
		}
	}

	private void tickGrapple(ServerWorld world) {
		Entity load = hookEntity != null && !hookEntity.isRemoved() && hookEntity.hasPassengers() ? hookEntity.getFirstPassenger() : null;
		if ((hook == Hook.LIFTING || hook == Hook.HOLDING || hook == Hook.SETTING) && load == null) {
			// It got away (struggled free, died or was taken off): wind the grapple back up.
			releaseLoad();
			setHook(Hook.RAISING);
		}
		double before = hookDrop;
		switch (hook) {
			case UP -> hookDrop = 0.0;
			case LOWERING, DOWN -> {
				if (hook == Hook.LOWERING) {
					hookDrop = Math.min(LINE_MAX, hookDrop + HOOK_DOWN);
					if (hookDrop >= LINE_MAX || groundUnderHook(world)) {
						setHook(Hook.DOWN);
					}
				}
				Entity caught = catchable(world);
				if (caught != null) {
					grab(world, caught);
				}
			}
			case RAISING -> {
				hookDrop = Math.max(0.0, hookDrop - HOOK_UP * 1.3);
				if (hookDrop <= 0.0) {
					setHook(Hook.UP);
				}
			}
			case LIFTING -> {
				hookDrop += MathHelper.clamp(CARRY - hookDrop, -HOOK_UP, HOOK_UP);
				if (Math.abs(hookDrop - CARRY) < 1.0E-3) {
					setHook(Hook.HOLDING);
				}
			}
			case HOLDING -> hookDrop = CARRY;
			case SETTING -> {
				hookDrop = Math.min(LINE_MAX, hookDrop + HOOK_UP);
				// Not load.isOnGround(): a rider never moves itself, so that is still what it was when it was caught.
				if (load != null && (load.isTouchingWater() || hookDrop >= LINE_MAX || loadDown(world, load))) {
					releaseLoad();
					setHook(Hook.RAISING);
				}
			}
		}
		if (hookDrop != before && age % 8 == 0) {
			world.playSound(null, getX(), getY(), getZ(), Airship.WINCH, SoundCategory.NEUTRAL, 0.6F, 1.0F);
		}
		dataTracker.set(HOOK_DROP, (float) hookDrop);
	}

	/** Whether the grapple's tines have come down onto something solid (or the water). */
	private boolean groundUnderHook(World world) {
		Vec3d from = hookGrip();
		BlockHitResult hit = world.raycast(new RaycastContext(from, from.add(0.0, -0.4, 0.0), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.ANY, this));
		return hit.getType() == HitResult.Type.BLOCK;
	}

	/** Whether a load being set down has its feet on the ground. */
	private boolean loadDown(World world, Entity load) {
		Vec3d from = load.getPos().add(0.0, 0.1, 0.0);
		BlockHitResult hit = world.raycast(new RaycastContext(from, from.add(0.0, -0.3, 0.0), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.ANY, load));
		return hit.getType() == HitResult.Type.BLOCK;
	}

	/** Something the grapple's tines are touching that it can take hold of. */
	@Nullable
	private Entity catchable(World world) {
		Vec3d grip = hookGrip();
		Box reach = new Box(grip.x - 0.7, grip.y - 0.9, grip.z - 0.7, grip.x + 0.7, grip.y + 0.5, grip.z + 0.7);
		List<Entity> found = world.getOtherEntities(this, reach, e -> canGrab(e));
		return found.isEmpty() ? null : found.get(0);
	}

	private boolean canGrab(Entity e) {
		if (!e.isAlive() || e.isSpectator() || e.hasVehicle() || e instanceof AirshipPartEntity || e instanceof AirshipHookEntity
				|| e instanceof AirshipBombEntity || e instanceof AirshipEntity) {
			return false;
		}
		if (e instanceof PlayerEntity player && (player.isCreative() && player.getAbilities().flying)) {
			return false;
		}
		return e instanceof LivingEntity || e instanceof ItemEntity || e.isCollidable() || e.hasPassengers();
	}

	private void grab(ServerWorld world, Entity target) {
		AirshipHookEntity holder = new AirshipHookEntity(world, this);
		Vec3d grip = hookGrip();
		holder.refreshPositionAndAngles(grip.x, grip.y, grip.z, getYaw(), 0.0F);
		world.spawnEntity(holder);
		if (!target.startRiding(holder, true)) {
			holder.discard();
			return;
		}
		hookEntity = holder;
		setHook(Hook.LIFTING);
		world.playSound(null, grip.x, grip.y, grip.z, Airship.GRAB, SoundCategory.NEUTRAL, 1.2F, 1.0F);
		world.spawnParticles(ParticleTypes.CRIT, grip.x, grip.y, grip.z, 10, 0.3, 0.3, 0.3, 0.1);
		for (Entity passenger : getPassengerList()) {
			if (passenger instanceof ServerPlayerEntity player) {
				ModCriteria.fire(player, "airship_grab");
			}
		}
		if (target instanceof ServerPlayerEntity player) {
			player.sendMessage(Text.translatable("hud.shootingstar.airship.grabbed"), true);
		}
	}

	/** Lets go of whatever is on the grapple (it drops from there). */
	public void releaseLoad() {
		if (hookEntity != null) {
			for (Entity load : List.copyOf(hookEntity.getPassengerList())) {
				load.stopRiding();
				load.fallDistance = 0.0F;
				load.setVelocity(Vec3d.ZERO);
			}
			hookEntity.discard();
			hookEntity = null;
		}
	}

	/** The grapple's holder, while it holds something. */
	@Nullable
	public AirshipHookEntity getHookEntity() {
		return hookEntity;
	}

	private void tickLadder(ServerWorld world) {
		if (age % 10 == 0 || ladderTarget == 0.0 && ladderDown) {
			Vec3d top = local(LADDER_LINE);
			BlockHitResult hit = world.raycast(new RaycastContext(top, top.add(0.0, -LINE_MAX, 0.0), RaycastContext.ShapeType.COLLIDER,
					RaycastContext.FluidHandling.ANY, this));
			ladderTarget = hit.getType() == HitResult.Type.BLOCK ? Math.max(1.0, top.y - hit.getPos().y) : LINE_MAX;
		}
		double ladder = getLadder();
		double target = ladderDown ? ladderTarget : 0.0;
		ladder += MathHelper.clamp(target - ladder, -0.5, 0.4);
		dataTracker.set(LADDER, (float) ladder);
		// Whoever climbs up to the top of the ladder steps aboard into a free place.
		if (ladder > 0.5) {
			Vec3d top = local(LADDER_LINE);
			Box box = new Box(top.x - 0.8, top.y - 1.0, top.z - 0.8, top.x + 0.8, top.y + 0.8, top.z + 0.8);
			for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, box, p -> !p.hasVehicle() && !p.isSpectator()
					&& p.getY() > top.y - 1.0 && age - steppedOff.getOrDefault(p.getUuid(), -1000) > 60)) {
				int place = nearestFreePlace(LADDER_TOP);
				if (place >= 0) {
					board(player, place);
				}
			}
		}
	}

	/**
	 * Whether an entity is on one of the airships' rope ladders, which it then climbs as it would a ladder (jump to go
	 * up, sneak to hold on). Asked by LivingEntity.isClimbing on each side.
	 */
	public static boolean onLadder(LivingEntity entity) {
		if (entity.hasVehicle()) {
			return false;
		}
		Set<AirshipEntity> ladders = entity.getWorld().isClient ? LADDERS_CLIENT : LADDERS_SERVER;
		if (ladders.isEmpty()) {
			return false;
		}
		for (AirshipEntity ship : ladders) {
			if (ship.isRemoved() || ship.getWorld() != entity.getWorld()) {
				continue;
			}
			Vec3d top = ship.local(LADDER_LINE);
			double dx = entity.getX() - top.x;
			double dz = entity.getZ() - top.z;
			if (dx * dx + dz * dz < 0.45 * 0.45 && entity.getY() < top.y + 0.5 && entity.getY() > top.y - ship.getLadder() - 0.3) {
				return true;
			}
		}
		return false;
	}

	// --- the look of her, on clients ----------------------------------------------------------------------

	private void clientTick() {
		World world = getWorld();
		if (client != null) {
			client.tick(this);
		}
		// Everyone aboard is shown walking smoothly to where the server has them (this client's player where they
		// walk), their legs going as they step.
		for (Entity passenger : getPassengerList()) {
			Vec3d before = shown.get(passenger);
			Vec3d mine = client != null ? client.stand(this, passenger) : null;
			Vec3d target = mine != null ? mine : syncedStandOf(passenger);
			if (target == null) {
				continue;
			}
			Vec3d now = before == null || mine != null ? target : before.add(target.subtract(before).multiply(0.5));
			shown.put(passenger, now);
			float stride = before == null ? 0.0F : (float) apart(now, before);
			strides.put(passenger, stride);
			if (passenger instanceof LivingEntity living) {
				living.limbAnimator.updateLimbs(Math.min(stride * 4.0F, 1.0F), 0.4F);
			}
		}
		prevPropSpin = propSpin;
		int throttle = getThrottle();
		// The propellers tick over while she is piloted and race with the throttle.
		float spin = isEngineRunning() ? 0.35F + 0.5F * Math.abs(throttle) + (float) getSpeed() * 1.5F : (float) getSpeed() * 0.5F;
		propSpin += spin;
		prevSteer = steer;
		int turn = isLogicalSideForUpdatingMovement() ? clientControls.turn() : dataTracker.get(STEER);
		steer += (turn - steer) * 0.15F;
		prevClimbLook = climbLook;
		int climb = isLogicalSideForUpdatingMovement() ? clientControls.up() ? 1 : clientControls.down() ? -1 : 0 : dataTracker.get(CLIMB);
		climbLook += (climb - climbLook) * 0.12F;
		prevShownDrop = shownDrop;
		shownDrop += ((float) getHookDrop() - shownDrop) * 0.5F;
		prevShownLadder = shownLadder;
		shownLadder += ((float) getLadder() - shownLadder) * 0.5F;
		prevBank = bank;
		bank += (-turn * 3.0F * (float) MathHelper.clamp(getSpeed() / TOP, 0.0, 1.0) - bank) * 0.05F;
		prevTilt = tilt;
		tilt += ((float) MathHelper.clamp(motion.y * 30.0, -4.0, 4.0) - tilt) * 0.05F;
		// Smoke from the engine's exhaust while she runs.
		if (isEngineRunning() && age % 3 == 0) {
			Vec3d at = local(EXHAUST);
			world.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0.0, 0.03, 0.0);
		}
	}

	/** What the third-person camera turns about while riding her: the middle of her (ChittyCameraMixin). */
	public Vec3d viewCentre(float tickDelta) {
		return getLerpedPos(tickDelta).add(VIEW_CENTRE.rotateY(-getYaw(tickDelta) * MathHelper.RADIANS_PER_DEGREE));
	}

	/** The pedal the pilot is on, as everyone sees it: -1, 0 or 1. */
	public int getThrottle() {
		return isLogicalSideForUpdatingMovement() ? clientControls.forward() : dataTracker.get(THROTTLE);
	}

	public float getPropSpin(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevPropSpin, propSpin);
	}

	/** -1 (full right) to 1 (full left). */
	public float getSteer(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevSteer, steer);
	}

	/** -1 (diving) to 1 (climbing): the elevator. */
	public float getClimb(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevClimbLook, climbLook);
	}

	/** How far the grapple hangs below her keel, as drawn. */
	public float getShownDrop(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevShownDrop, shownDrop);
	}

	/** How far the ladder hangs down, as drawn. */
	public float getShownLadder(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevShownLadder, shownLadder);
	}

	/** Degrees, right side down. */
	public float getBank(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevBank, bank);
	}

	/** Degrees, nose up. */
	public float getTilt(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevTilt, tilt);
	}

	// --- saving ------------------------------------------------------------------------------------------

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		dataTracker.set(BOMBS, (byte) MathHelper.clamp(nbt.getInt("Bombs"), 0, RACK));
		ladderDown = nbt.getBoolean("LadderDown");
		dataTracker.set(LADDER, nbt.getFloat("Ladder"));
		speed = nbt.getFloat("Speed");
		// A load on the grapple is not kept: she comes back with it wound up.
		hook = Hook.UP;
		hookDrop = 0.0;
		dataTracker.set(HOOK_STATE, (byte) 0);
		dataTracker.set(HOOK_DROP, 0.0F);
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("Bombs", getBombs());
		nbt.putBoolean("LadderDown", ladderDown);
		nbt.putFloat("Ladder", (float) getLadder());
		nbt.putFloat("Speed", speed);
	}

	/** For tests: loads the rack. */
	public void loadBombs(int count) {
		dataTracker.set(BOMBS, (byte) MathHelper.clamp(count, 0, RACK));
	}

	/** For tests: lets the grapple down at once to the given depth, still going down. */
	public void lowerGrappleTo(double drop) {
		hookDrop = drop;
		setHook(Hook.LOWERING);
	}

	/** For tests: whether the ladder is let down (or being). */
	public boolean isLadderDown() {
		return ladderDown;
	}
}
