package io.github.kortev.chitty.airship;

import io.github.kortev.chitty.ChittyControls;
import io.github.kortev.chitty.ChittyPartEntity;
import io.github.kortev.chitty.mixin.AirshipJumper;
import io.github.kortev.shootingstar.registry.ModCriteria;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
import net.minecraft.particle.BlockStateParticleEffect;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Baron Bomburst's airship, from the film: a gas envelope 34 blocks long over a little gilded gondola hung from it on
 * wires. Eight can stand in the gondola and walk about in it (they cannot fall out); whoever walks up to the wheel in
 * the bow takes it and flies her, and sneaks to let it go. She lifts six, and with more aboard (a load on her grapple
 * counts as one) she cannot climb and sinks slowly, as she does in the film. She hovers where she is left. Her crew
 * work her grapple's winch, letting it down on its rope and winding it in (it swings and trails as a weight on a rope
 * does, and goes up and down only as they work it): going, it seizes what it meets (a mob, a player, a dropped item, a
 * boat or a car), which they can then wind up to carry, or let down again until it stands on the ground and is let go;
 * someone on the ground can take hold of it and hook it onto someone. They can let down her rope ladder (anyone can climb it, and climbing off its top boards her)
 * and drop bombs from the rack in her gondola; the pilot can throw a passenger overboard. Sneaking gets anyone else
 * off: beside her when she is down, or onto her rope ladder in the air, which lets itself down for them.
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
	/** Where the grapple's ring is, from where its rope comes out under her keel (world axes). */
	private static final TrackedData<Vector3f> HOOK_AT = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
	/** Who holds the grapple on the ground (their entity id), or -1. */
	private static final TrackedData<Integer> HOOK_HELD = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** The grapple's head (its entity id) while the grapple is out, or -1. */
	private static final TrackedData<Integer> HOOK_HEAD = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Which way the crew are working the grapple's winch: out (1), in (-1) or not (0). */
	private static final TrackedData<Byte> WINCH = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.BYTE);
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
	public static final double WALK = 0.15;
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
	public static final double LINE_MAX = 64.0;
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

	// Speeds in blocks per tick: heavy and stately to handle, as an airship is, but quick enough to be worth flying (she
	// tops a galloping horse; Chitty in the air still outruns her).
	static final double TOP = 0.8;
	static final double ACCEL = 0.018;
	static final double REVERSE_TOP = 0.25;
	static final double RISE = 0.28;
	static final double SINK = 0.32;
	/** How fast she sinks overloaded. */
	static final double HEAVY_SINK = 0.035;
	/** Degrees a tick she turns at speed, and standing (her propellers turn her a little even then). */
	static final float TURN = 2.8F;
	static final float TURN_STANDING = 1.4F;
	/**
	 * The grapple's winch, worked by the crew: how fast it lets the rope out and winds it in (blocks a tick), empty and
	 * with a load; how quickly it runs up to speed and slows to a stop (the share of the difference each tick); how
	 * much slack it lets out onto the grapple once that rests on the ground.
	 */
	static final double HOOK_DOWN = 0.45;
	static final double HOOK_UP = 0.3;
	static final double LOADED_DOWN = 0.22;
	static final double LOADED_UP = 0.2;
	static final double WINCH_EASE = 0.2;
	static final double SLACK = 2.0;
	/**
	 * When the grapple takes hold of what it meets: let down onto it by the winch, swept into it as she flies (faster
	 * than SWEEP), or thrown at it (for THROWN_FLIGHT ticks after it leaves the hand). Hanging still or swinging idly
	 * under her, it catches nothing, so that it never hooks someone just for being near it.
	 */
	static final double SWEEP = 0.05;
	static final int THROWN_FLIGHT = 40;
	/** How close under her keel the grapple's ring may swing: it never swings up through her gondola. */
	public static final double KEEL_CLEARANCE = 0.3;
	/**
	 * How much further than its rope the grapple may be held back by something it has snagged on (a hill she flies
	 * away from) before the rope pulls it free over it.
	 */
	static final double SNAG = 1.5;
	/** How long the grapple will not take hold again of whoever has just got off it, let go of it or thrown it. */
	static final int SPARE = 60;
	/**
	 * The grapple swinging on its rope: how fast it gathers speed falling (blocks a tick, each tick), and how much of
	 * its speed it keeps each tick through the air, empty and with a load.
	 */
	static final double HOOK_GRAVITY = 0.05;
	static final double HOOK_DAMPING = 0.985;
	static final double HOOK_LOADED_DAMPING = 0.97;
	/** How far someone holding the grapple can reach to hook it onto something. */
	public static final double HOOK_REACH = 4.0;
	/** How hard the grapple is thrown (blocks a tick, the way they look, and a little up), and how much rope it takes. */
	static final double THROW_SPEED = 1.0;
	static final double THROW_LIFT = 0.12;
	static final double THROW_SLACK = 16.0;
	/** How much rope is left out when someone hanging on it is wound up to her keel and climbs aboard. */
	static final double HOOK_ABOARD = 0.6;
	/** How much someone hanging on the grapple can swing it by leaning (blocks a tick, each tick). */
	static final double HOOK_PUMP = 0.025;
	/** How fast someone hanging on the grapple climbs its rope, holding jump (blocks a tick). */
	static final double ROPE_CLIMB = 0.15;
	/**
	 * Taking hold to hang on, someone pulls themselves up the rope (at most PULL_UP, PULL_STEP a tick) until the bottom
	 * of their swing, under her keel, clears the ground there by HANG_CLEAR: they swing free, not drag their feet.
	 */
	static final double HANG_CLEAR = 0.6;
	static final double PULL_UP = 2.5;
	static final double PULL_STEP = 0.25;
	/** How far from their eyes someone can take hold of the grapple (as it hangs over them, or leaping for it). */
	static final double GRAB_REACH = 4.5;
	static final int BOMB_COOLDOWN = 30;

	/**
	 * The grapple: stowed under her keel, out on its rope (empty, or with something caught on it or someone hanging on
	 * it), or in the hands of someone on the ground. It goes up and down only as the crew work its winch.
	 */
	public enum Hook { UP, OUT, HELD }

	/** What a crew member can ask of her (AirshipActionPayload). */
	public static final int ACTION_WINCH_STOP = 0;
	public static final int ACTION_LADDER = 1;
	public static final int ACTION_BOMB = 2;
	public static final int ACTION_OVERBOARD = 3;
	/** Someone holding her grapple on the ground throws it (their grapple key). */
	public static final int ACTION_THROW = 4;
	/** The crew working the grapple's winch: letting the rope out, winding it in (ACTION_WINCH_STOP when they let go). */
	public static final int ACTION_PAY_OUT = 5;
	public static final int ACTION_WIND_IN = 6;

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
	/** The airships whose grapples someone on the ground holds, on each side (grappleHeldBy). */
	private static final Set<AirshipEntity> HELD_SERVER = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Set<AirshipEntity> HELD_CLIENT = Collections.newSetFromMap(new WeakHashMap<>());

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
	/** How much rope the grapple has out. */
	private double hookDrop;
	/** Where the grapple's ring is in the world, and was the tick before (its swing). */
	@Nullable
	private Vec3d hookPos;
	private Vec3d hookPrev;
	private boolean hookGrounded;
	@Nullable
	private PlayerEntity hookHolder;
	/** How high whoever holds the grapple stood last tick (they jump to hang on it). */
	private double holderLastY;
	/** The grapple's winch: letting rope out (1), winding it in (-1) or still (0), who works it, and how fast it turns. */
	private int winch;
	@Nullable
	private Entity winchBy;
	private double winchSpeed;
	/** How much further whoever has just taken hold to hang on is pulling themselves up the rope (HANG_CLEAR). */
	private double pullUp;
	/** Whoever the grapple will not take hold of just now (they have just got off it, let go of it or thrown it), and till when. */
	@Nullable
	private Entity spared;
	private int sparedUntil;
	/** When the grapple was last thrown. */
	private int thrownAt = -1000;
	/** What was on the grapple last tick (to know when it gets off). */
	@Nullable
	private Entity lastLoad;
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
	private float spinRate;
	private float steer;
	private float prevSteer;
	private float climbLook;
	private float prevClimbLook;
	private float shownDrop;
	private float prevShownDrop;
	private Vec3d shownHook = Vec3d.ZERO;
	private Vec3d prevShownHook = Vec3d.ZERO;
	private float shownLadder;
	/** How far her rope ladder trails behind her (radians from hanging straight down), as she goes; on both sides. */
	private float ladderLean;
	private float prevLadderLean;
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
	private final Map<Entity, Float> sinceStep = new WeakHashMap<>();
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
		builder.add(HOOK_AT, new Vector3f());
		builder.add(HOOK_HELD, -1);
		builder.add(HOOK_HEAD, -1);
		builder.add(WINCH, (byte) 0);
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
		return hurt(source, amount, false);
	}

	/**
	 * Hit (on her canvas, or on her wooden gondola): she rocks, with the sound of what was struck; hit hard enough, she
	 * comes down in a burst of canvas and wood and drops herself.
	 */
	public boolean hurt(DamageSource source, float amount, boolean canvas) {
		if (getWorld().isClient || isRemoved()) {
			return true;
		}
		if (isInvulnerableTo(source)) {
			return false;
		}
		Vec3d struck = canvas ? local(new Vec3d(0.0, 7.5, -3.0)) : getPos().add(0.0, 0.8, 0.0);
		getWorld().playSound(null, struck.x, struck.y, struck.z,
				canvas ? SoundEvents.BLOCK_WOOL_HIT : SoundEvents.BLOCK_WOOD_HIT, SoundCategory.NEUTRAL, 1.0F,
				0.8F + random.nextFloat() * 0.3F);
		setDamageWobbleSide(-getDamageWobbleSide());
		setDamageWobbleTicks(10);
		scheduleVelocityUpdate();
		setDamageWobbleStrength(getDamageWobbleStrength() + amount * 2.5F);
		emitGameEvent(GameEvent.ENTITY_DAMAGE, source.getAttacker());
		boolean creative = source.getAttacker() instanceof PlayerEntity player && player.getAbilities().creativeMode;
		if (creative || getDamageWobbleStrength() > 60.0F) {
			if (getWorld() instanceof ServerWorld world) {
				Vec3d envelope = local(new Vec3d(0.0, 7.5, -3.0));
				BlockStateParticleEffect canvasBits = new BlockStateParticleEffect(ParticleTypes.BLOCK,
						Blocks.WHITE_WOOL.getDefaultState());
				BlockStateParticleEffect woodBits = new BlockStateParticleEffect(ParticleTypes.BLOCK,
						Blocks.DARK_OAK_PLANKS.getDefaultState());
				world.spawnParticles(canvasBits, envelope.x, envelope.y, envelope.z, 120, 3.0, 2.5, 8.0, 0.2);
				world.spawnParticles(woodBits, getX(), getY() + 0.8, getZ(), 40, 0.9, 0.6, 1.6, 0.2);
				world.playSound(null, envelope.x, envelope.y, envelope.z, SoundEvents.BLOCK_WOOL_BREAK, SoundCategory.NEUTRAL,
						2.0F, 0.6F);
				world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.NEUTRAL, 1.5F, 0.8F);
			}
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
			discard();
		}
		return true;
	}

	@Override
	public void remove(RemovalReason reason) {
		stowHead();
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
		AirshipHookEntity head = getShownHookEntity();
		return getPassengerList().size() + (head != null && head.hasPassengers() ? 1 : 0);
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

	/** Where the grapple's rope comes out under her keel, in the world. */
	public Vec3d lineOut() {
		return local(LINE_OUT);
	}

	/** The top of the grapple's ring, where its rope ties on, in the world (on a client, where it is drawn). */
	public Vec3d hookTop() {
		if (getWorld().isClient) {
			return lineOut().add(getShownHook(1.0F));
		}
		return hookPos != null ? hookPos : lineOut();
	}

	/** Where the grapple's tines take hold, in the world: the length of it along its rope below its ring. */
	public Vec3d hookGrip() {
		return gripBelow(hookTop());
	}

	private Vec3d gripBelow(Vec3d top) {
		Vec3d rope = top.subtract(lineOut());
		double length = rope.length();
		return top.add(length > 0.3 ? rope.multiply(HOOK_GRIP / length) : new Vec3d(0.0, -HOOK_GRIP, 0.0));
	}

	/** Whoever holds her grapple on the ground, as any side knows it. */
	@Nullable
	public Entity getGrappleHolder() {
		int held = dataTracker.get(HOOK_HELD);
		return held >= 0 ? getWorld().getEntityById(held) : null;
	}

	/**
	 * Where the grapple's ring is drawn, from where its rope comes out (world axes): as the server has it, eased, or in
	 * the hand of whoever holds it.
	 */
	public Vec3d getShownHook(float tickDelta) {
		int held = dataTracker.get(HOOK_HELD);
		Entity holder = held >= 0 ? getWorld().getEntityById(held) : null;
		if (holder != null) {
			Vec3d out = getLerpedPos(tickDelta).add(LINE_OUT.rotateY(-getYaw(tickDelta) * MathHelper.RADIANS_PER_DEGREE));
			return handOf(holder, tickDelta).subtract(out);
		}
		return prevShownHook.lerp(shownHook, tickDelta);
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
		if (getWorld().isClient && passenger instanceof PlayerEntity player) {
			// Their view bobs as they walk about her, as it does walking anywhere (a rider's otherwise never does).
			float stride = strideOf(player);
			player.strideDistance = player.prevStrideDistance + (Math.min(0.1F, stride) - player.prevStrideDistance) * 0.4F;
			player.horizontalSpeed = player.prevHorizontalSpeed + stride * 0.6F;
		}
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
			return ladderAt(1.8);
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
		// Her rope ladder trails behind her as she goes, and swings back under her as she slows.
		float yawRad = getYaw() * MathHelper.RADIANS_PER_DEGREE;
		double forward = -motion.x * MathHelper.sin(yawRad) + motion.z * MathHelper.cos(yawRad);
		prevLadderLean = ladderLean;
		ladderLean += ((float) Math.atan(forward * 1.2) - ladderLean) * 0.06F;
		Set<AirshipEntity> ladders = getWorld().isClient ? LADDERS_CLIENT : LADDERS_SERVER;
		if (getLadder() > 0.5 && !isRemoved()) {
			ladders.add(this);
		} else {
			ladders.remove(this);
		}
		Set<AirshipEntity> held = getWorld().isClient ? HELD_CLIENT : HELD_SERVER;
		if (dataTracker.get(HOOK_HELD) >= 0 && !isRemoved()) {
			held.add(this);
		} else {
			held.remove(this);
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
		yawVelocity += (rate - yawVelocity) * 0.12F;
		setYaw(getYaw() - yawVelocity);

		double target = in.up() ? RISE : in.down() ? -SINK : 0.0;
		if (isOverloaded()) {
			// Too many aboard: she cannot climb, and settles slowly however hard she is flown.
			target = Math.min(target, 0.0) - HEAVY_SINK;
		}
		climbSpeed += (target - climbSpeed) * 0.08;
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
			case ACTION_PAY_OUT -> setWinch(player, 1);
			case ACTION_WIND_IN -> setWinch(player, -1);
			case ACTION_WINCH_STOP -> {
				if (winchBy == player) {
					setWinch(player, 0);
				}
			}
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
	 * The grapple's winch, worked by the crew (held keys, AirshipClient): letting the rope out (1), winding it in (-1) or
	 * holding it where it is (0). It runs up to speed and slows to a stop smoothly, and it does only what it is worked
	 * to do: the grapple stays wherever it is left. Winding in on someone holding the grapple on the ground takes them up
	 * hanging on it. `by` is whoever works it (null in tests): if they leave her, it stops.
	 */
	public void setWinch(@Nullable Entity by, int way) {
		if (way == winch && (way == 0 || by == winchBy)) {
			return;
		}
		if (way < 0 && hook == Hook.HELD && hookHolder != null && !hangOn(hookHolder)) {
			letGoOfGrapple();
		}
		if (way < 0 && hook == Hook.UP) {
			way = 0;
		}
		if (way > 0 && hook == Hook.UP) {
			setHook(Hook.OUT);
		}
		boolean started = winch == 0 && way != 0;
		winch = way;
		dataTracker.set(WINCH, (byte) way);
		winchBy = way == 0 ? null : by;
		if (started || way == 0) {
			// The winch's pawl clacks as it is put in gear or let go.
			getWorld().playSound(null, getX(), getY(), getZ(), Airship.WINCH, SoundCategory.NEUTRAL, 0.8F,
					way > 0 ? 1.1F : way < 0 ? 0.9F : 1.3F);
		}
	}

	/** Which way the grapple's winch is turning: out (1), in (-1) or still (0). */
	public int getWinch() {
		return winch;
	}

	/** Which way the crew are working the grapple's winch, as any side knows it: out (1), in (-1) or not (0). */
	public int getShownWinch() {
		return dataTracker.get(WINCH);
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
		Vec3d out = lineOut();
		if (hookPos == null) {
			hookPos = out;
			hookPrev = out;
		}
		Entity load = hookEntity != null && !hookEntity.isRemoved() && hookEntity.hasPassengers() ? hookEntity.getFirstPassenger() : null;
		if (load == null && lastLoad != null) {
			// It got away (struggled free, let go, was set down, died or was taken off): the grapple stays where it is,
			// and leaves it be a while.
			spare(lastLoad);
		}
		lastLoad = load;
		if (load == null) {
			pullUp = 0.0;
		}
		if (winchBy != null && (winchBy.isRemoved() || winchBy.getVehicle() != this)) {
			// Whoever worked the winch has left her: it stops.
			setWinch(null, 0);
		}
		if (hook == Hook.HELD && !stillHeld()) {
			boolean pulled = hookHolder != null && hookHolder.isAlive() && hookHolder.getWorld() == getWorld()
					&& handOf(hookHolder, 1.0F).distanceTo(out) > LINE_MAX;
			if (pulled && hookHolder instanceof ServerPlayerEntity player) {
				player.sendMessage(Text.translatable("hud.shootingstar.airship.yanked"), true);
			}
			letGoOfGrapple();
		}
		if (hook == Hook.HELD && hookHolder != null) {
			// Jumping with it in hand, they hang on it.
			if (!hookHolder.isOnGround() && hookHolder.getY() > holderLastY + 0.05) {
				hangOn(hookHolder);
			} else {
				holderLastY = hookHolder.getY();
			}
		}
		double before = hookDrop;
		if (hook == Hook.UP) {
			hookDrop = 0.0;
			winchSpeed = 0.0;
		} else if (hook == Hook.OUT) {
			winchRope(world, out, load);
		}
		Vec3d gripBefore = gripBelow(hookPos);
		swingGrapple(world, out, load);
		// It takes hold of what it meets as it is let down onto it, swept into it as she flies, or thrown at it; hanging
		// still or swinging idly, it catches nothing.
		boolean going = winchSpeed > 0.02 || getSpeed() > SWEEP || age - thrownAt < THROWN_FLIGHT;
		if (hook == Hook.OUT && load == null && going) {
			Entity caught = catchable(world, gripBefore);
			if (caught != null) {
				grab(world, caught);
			}
		}
		keepHead(world);
		// The winch's ratchet clicks as it turns, quicker and higher the faster it goes.
		double turned = Math.abs(hookDrop - before);
		if (turned > 0.01 && age % (turned > 0.2 ? 3 : 5) == 0) {
			world.playSound(null, getX(), getY(), getZ(), Airship.WINCH, SoundCategory.NEUTRAL, 0.5F,
					(float) (0.85 + turned * 0.8) + random.nextFloat() * 0.05F);
		}
		// The rope creaks under a load as it swings.
		if (load != null && age % 37 == 0 && hookPos.subtract(hookPrev).lengthSquared() > 0.002) {
			world.playSound(null, hookPos.x, hookPos.y, hookPos.z, Airship.CREAK, SoundCategory.NEUTRAL, 0.5F,
					1.5F + random.nextFloat() * 0.3F);
		}
		dataTracker.set(HOOK_DROP, (float) hookDrop);
		Vec3d at = hookPos.subtract(out);
		dataTracker.set(HOOK_AT, new Vector3f((float) at.x, (float) at.y, (float) at.z));
	}

	/**
	 * The grapple's winch turns as the crew work it, running up to speed and slowing to a stop, slower with a load. Let
	 * out, the rope stops paying out once the grapple rests on the ground with a little slack; a load let down until it
	 * stands on the ground (or in water) is let go there. Wound in, a load comes up as far as under her keel (CARRY) and
	 * no further; someone hanging on is wound right up and climbs aboard; and the empty grapple, wound all the way in, is
	 * stowed.
	 */
	private void winchRope(ServerWorld world, Vec3d out, @Nullable Entity load) {
		boolean byChoice = load instanceof PlayerEntity && hookEntity != null && hookEntity.isVoluntary();
		// Someone hanging on climbs the rope, holding jump, while the winch is still.
		boolean climbing = winch == 0 && byChoice && load instanceof AirshipJumper rider && rider.isChittyJumping();
		double speed = winch > 0 ? load != null ? LOADED_DOWN : HOOK_DOWN : winch < 0 ? -(load != null ? LOADED_UP : HOOK_UP)
				: climbing ? -ROPE_CLIMB : 0.0;
		winchSpeed += (speed - winchSpeed) * WINCH_EASE;
		if (winch == 0 && Math.abs(winchSpeed) < 0.004) {
			winchSpeed = 0.0;
		}
		hookDrop += winchSpeed;
		if (byChoice && winch == 0 && pullUp > 0.0) {
			// Taking hold, they pull themselves up it, clear of the ground.
			double step = Math.min(pullUp, PULL_STEP);
			hookDrop = Math.max(HOOK_ABOARD, hookDrop - step);
			pullUp -= step;
		} else {
			pullUp = 0.0;
		}
		if (hookDrop >= LINE_MAX) {
			hookDrop = LINE_MAX;
			winchSpeed = 0.0;
		}
		if (winchSpeed > 0.0 && hookGrounded) {
			// Resting on the ground: a little slack, and no more rope comes off the drum.
			double slack = hookPos.distanceTo(out) + SLACK;
			if (hookDrop > slack) {
				hookDrop = slack;
				winchSpeed = 0.0;
			}
		}
		if (load != null && winchSpeed > 0.0 && (hookGrounded || load.isTouchingWater() || loadDown(world, load))) {
			// Not load.isOnGround(): a rider never moves itself, so that is still what it was when it was caught.
			releaseLoad();
		} else if (load != null && !byChoice && winchSpeed < 0.0 && hookDrop < CARRY) {
			hookDrop = CARRY;
			winchSpeed = Math.max(0.0, winchSpeed);
		} else if (byChoice && hookDrop <= HOOK_ABOARD) {
			if (!comeAboard(load)) {
				hookDrop = HOOK_ABOARD;
				winchSpeed = Math.max(0.0, winchSpeed);
			}
		} else if (load == null && hookDrop <= 0.0) {
			hookDrop = 0.0;
			winchSpeed = 0.0;
			setHook(Hook.UP);
			setWinch(null, 0);
		}
	}

	/** The grapple will not take hold of this one for a while (they have just got off it, let go of it or thrown it). */
	private void spare(Entity e) {
		spared = e;
		sparedUntil = age + SPARE;
	}

	/**
	 * The grapple is a weight on a rope: it falls, swings and trails behind her as she goes, never further from where
	 * its rope comes out than the rope that is paid out, and comes to rest on whatever its tines (or the feet of what it
	 * carries) come down on. In someone's hands it goes where their hand goes, the rope paying out after it.
	 */
	private void swingGrapple(ServerWorld world, Vec3d out, @Nullable Entity load) {
		boolean resting = hookGrounded;
		hookGrounded = false;
		if (hook == Hook.UP) {
			hookPos = out;
			hookPrev = out;
			return;
		}
		if (hook == Hook.HELD && hookHolder != null) {
			Vec3d hand = handOf(hookHolder, 1.0F);
			hookPrev = hookPos;
			hookPos = hand;
			hookDrop = Math.max(hookDrop, hand.distanceTo(out));
			return;
		}
		boolean byChoice = load instanceof PlayerEntity && hookEntity != null && hookEntity.isVoluntary();
		// A load drags on its swing; someone hanging on by choice keeps it going.
		Vec3d swing = hookPos.subtract(hookPrev).multiply(load != null && !byChoice ? HOOK_LOADED_DAMPING : HOOK_DAMPING);
		if (byChoice && load instanceof PlayerEntity rider) {
			// Someone hanging on by choice swings it by leaning the way they press; with their feet on the ground they
			// run with it, pushing off harder, until it swings them off their feet.
			double pump = resting ? HOOK_PUMP * 2.0 : HOOK_PUMP;
			float yawRad = rider.getYaw() * MathHelper.RADIANS_PER_DEGREE;
			Vec3d ahead = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
			Vec3d left = new Vec3d(MathHelper.cos(yawRad), 0.0, MathHelper.sin(yawRad));
			swing = swing.add(ahead.multiply(rider.forwardSpeed * pump)).add(left.multiply(rider.sidewaysSpeed * pump));
		}
		Vec3d next = hookPos.add(swing).add(0.0, -HOOK_GRAVITY, 0.0);
		Vec3d rope = next.subtract(out);
		double length = rope.length();
		if (length > hookDrop) {
			next = out.add(rope.multiply(hookDrop / length));
		}
		// Swinging up as she stops or turns, it comes up against her keel and no further.
		double under = Math.min(KEEL_CLEARANCE, hookDrop);
		boolean keel = next.y > out.y - under;
		if (keel) {
			// Along under her, still within its rope.
			Vec3d flat = new Vec3d(next.x - out.x, 0.0, next.z - out.z);
			double room = Math.sqrt(Math.max(0.0, hookDrop * hookDrop - under * under));
			if (flat.length() > room) {
				flat = flat.length() > 1.0E-6 ? flat.multiply(room / flat.length()) : Vec3d.ZERO;
			}
			next = out.add(flat).add(0.0, -under, 0.0);
		}
		// From its ring down to the lowest point of it: its tines, or the feet of what it carries.
		double hang = load != null ? AirshipHookEntity.hangBelow(load, byChoice) : 0.0;
		Vec3d low = new Vec3d(0.0, -(HOOK_GRIP + hang), 0.0);
		if (hookPos.distanceTo(out) > hookDrop + SNAG) {
			// Snagged on something as she flies away from it: the rope drags it free, over whatever held it.
			hookPrev = hookPos;
			hookPos = next;
			outOfTheGround(world, low);
			return;
		}
		BlockHitResult hit = world.raycast(new RaycastContext(hookPos.add(low), next.add(low),
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, this));
		if (hit.getType() == HitResult.Type.BLOCK && !hit.isInsideBlock()) {
			Vec3d normal = Vec3d.of(hit.getSide().getVector());
			Vec3d moved = next.subtract(hookPos);
			Vec3d stop = hit.getPos().add(normal.multiply(0.01)).subtract(low);
			// It stops against what it struck, sliding on a little along it.
			Vec3d slide = moved.subtract(normal.multiply(moved.dotProduct(normal))).multiply(0.5);
			if (normal.y > 0.5 && moved.y < -0.2) {
				thud(world, hit.getBlockPos(), stop.add(low), load != null);
			} else if (load == null && slide.horizontalLengthSquared() > 0.0016 && age % 3 == 0) {
				// Dragged along the ground, it rattles and strikes sparks.
				Vec3d at = stop.add(low);
				world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.05, at.z, 3, 0.15, 0.05, 0.15, 0.08);
				world.playSound(null, at.x, at.y, at.z, Airship.GRAB, SoundCategory.NEUTRAL, 0.3F,
						1.5F + random.nextFloat() * 0.4F);
			}
			hookGrounded = normal.y > 0.5;
			hookPos = stop;
			hookPrev = stop.subtract(slide);
			return;
		}
		// Against her keel it loses the speed it came up with.
		hookPrev = keel ? new Vec3d(hookPos.x, next.y, hookPos.z) : hookPos;
		hookPos = next;
		outOfTheGround(world, low);
	}

	/**
	 * Neither the grapple nor what hangs from it ever ends up in the ground (as a mob snatched by the legs could, or a
	 * load dragged into a hillside): if its lowest point is inside a block, it is lifted out onto the top of it, a
	 * block a tick until it is clear.
	 */
	private void outOfTheGround(World world, Vec3d low) {
		Vec3d foot = hookPos.add(low);
		BlockPos at = BlockPos.ofFloored(foot);
		VoxelShape shape = world.getBlockState(at).getCollisionShape(world, at);
		if (shape.isEmpty()) {
			return;
		}
		double top = at.getY() + shape.getMax(Direction.Axis.Y);
		if (foot.y < top) {
			Vec3d up = new Vec3d(0.0, top - foot.y + 0.01, 0.0);
			hookPos = hookPos.add(up);
			hookPrev = hookPrev.add(up);
			hookGrounded = true;
		}
	}

	/** The grapple comes down hard on the ground: a clank and a puff of what it struck. */
	private void thud(ServerWorld world, BlockPos on, Vec3d at, boolean loaded) {
		BlockState state = world.getBlockState(on);
		if (!state.isAir()) {
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 10, 0.3, 0.05, 0.3,
					0.15);
		}
		world.playSound(null, at.x, at.y, at.z, Airship.GRAB, SoundCategory.NEUTRAL, loaded ? 0.6F : 0.9F, loaded ? 0.5F : 0.7F);
	}

	/** Where someone holding the grapple holds it: in their right hand, a little ahead of them. */
	static Vec3d handOf(Entity holder, float tickDelta) {
		float yaw = holder instanceof LivingEntity living ? MathHelper.lerp(tickDelta, living.prevBodyYaw, living.bodyYaw)
				: holder.getYaw(tickDelta);
		float yawRad = yaw * MathHelper.RADIANS_PER_DEGREE;
		Vec3d right = new Vec3d(-MathHelper.cos(yawRad), 0.0, -MathHelper.sin(yawRad));
		Vec3d ahead = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
		return holder.getLerpedPos(tickDelta).add(0.0, holder.getHeight() * 0.62, 0.0).add(right.multiply(0.38))
				.add(ahead.multiply(0.25));
	}

	/**
	 * The grapple's head (AirshipHookEntity) is in the world whenever the grapple is out of her: something to take hold
	 * of, and what a load rides.
	 */
	private void keepHead(ServerWorld world) {
		boolean out = hook != Hook.UP && hookDrop > 0.6 || hookEntity != null && hookEntity.hasPassengers();
		if (out && (hookEntity == null || hookEntity.isRemoved())) {
			Vec3d grip = hookGrip();
			hookEntity = new AirshipHookEntity(world, this);
			hookEntity.refreshPositionAndAngles(grip.x, grip.y, grip.z, getYaw(), 0.0F);
			world.spawnEntity(hookEntity);
			dataTracker.set(HOOK_HEAD, hookEntity.getId());
		} else if (!out && hookEntity != null) {
			stowHead();
		}
	}

	/** Whether a load being set down has its feet on the ground. */
	private boolean loadDown(World world, Entity load) {
		Vec3d from = load.getPos().add(0.0, 0.1, 0.0);
		BlockHitResult hit = world.raycast(new RaycastContext(from, from.add(0.0, -0.3, 0.0), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.ANY, load));
		return hit.getType() == HitResult.Type.BLOCK;
	}

	/** Something the grapple's tines touched since the last tick (a thrown one goes fast) that it can take hold of. */
	@Nullable
	private Entity catchable(World world, Vec3d gripBefore) {
		Vec3d grip = hookGrip();
		Box reach = new Box(grip.x - 0.7, grip.y - 0.9, grip.z - 0.7, grip.x + 0.7, grip.y + 0.5, grip.z + 0.7)
				.union(new Box(gripBefore.x - 0.7, gripBefore.y - 0.9, gripBefore.z - 0.7, gripBefore.x + 0.7, gripBefore.y + 0.5,
						gripBefore.z + 0.7));
		List<Entity> found = world.getOtherEntities(this, reach, e -> canGrab(whole(e)) && !(whole(e) == spared && age < sparedUntil));
		return found.isEmpty() ? null : whole(found.get(0));
	}

	/** What the grapple takes hold of when it meets this: Chitty herself, not one of the hitboxes along her length. */
	private static Entity whole(Entity e) {
		return e instanceof ChittyPartEntity part && part.getCar() != null ? part.getCar() : e;
	}

	private boolean canGrab(Entity e) {
		if (!e.isAlive() || e.isSpectator() || e.hasVehicle() || e instanceof AirshipPartEntity || e instanceof AirshipHookEntity
				|| e instanceof AirshipBombEntity || e instanceof AirshipEntity || e == hookHolder) {
			return false;
		}
		if (e instanceof PlayerEntity player && (player.isCreative() && player.getAbilities().flying)) {
			return false;
		}
		return e instanceof LivingEntity || e instanceof ItemEntity || e.isCollidable() || e.hasPassengers();
	}

	private void grab(ServerWorld world, Entity target) {
		keepHead(world);
		AirshipHookEntity head = hookEntity;
		if (head == null) {
			return;
		}
		// Taken by the back of its collar where it stands, wherever the tines met it (a thrown grapple often meets
		// something about the legs): it is not dragged down into the ground to hang from where they struck it.
		Vec3d before = hookPos;
		hookPos = target.getPos().add(0.0, AirshipHookEntity.hangBelow(target, false) + HOOK_GRIP, 0.0);
		hookPrev = hookPos;
		hookDrop = Math.max(hookDrop, hookPos.distanceTo(lineOut()));
		Vec3d grip = hookGrip();
		head.refreshPositionAndAngles(grip.x, grip.y, grip.z, getYaw(), 0.0F);
		if (!target.startRiding(head, true)) {
			hookPos = before;
			hookPrev = before;
			return;
		}
		// The rope draws tight on it, and the winch stops: what happens to it now is the crew's to say.
		setWinch(null, 0);
		winchSpeed = 0.0;
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

	/**
	 * Someone uses the grapple's head as it hangs empty (AirshipHookEntity.interact). Standing, they take hold of it, and
	 * it goes where their hand goes, its rope paying out after them. Leaping (or falling) for it, they catch it and hang
	 * on at once, swinging from where they caught it; so too if the crew are winding it in as they take hold.
	 */
	public boolean takeHoldOfGrapple(PlayerEntity player) {
		if (player.hasVehicle() || player.isSpectator() || hook != Hook.OUT || hookEntity == null || hookEntity.hasPassengers()
				|| player.getEyePos().squaredDistanceTo(hookGrip()) > GRAB_REACH * GRAB_REACH) {
			return false;
		}
		hookHolder = player;
		holderLastY = player.getY();
		dataTracker.set(HOOK_HELD, player.getId());
		setHook(Hook.HELD);
		if (!player.isOnGround() || winch < 0) {
			return hangOn(player);
		}
		Vec3d grip = hookGrip();
		getWorld().playSound(null, grip.x, grip.y, grip.z, Airship.GRAB, SoundCategory.NEUTRAL, 0.8F, 1.3F);
		player.sendMessage(Text.translatable("hud.shootingstar.airship.holding",
				Text.keybind("key.shootingstar.airship_grapple")), true);
		return true;
	}

	/**
	 * Whoever holds the grapple hangs on it (they jump with it in hand, leap to catch it, or the crew wind it in): they
	 * ride it, pulling themselves up it clear of the ground (HANG_CLEAR), swing under her, climb it, and let go when they
	 * sneak (AirshipHookEntity.freed). Wound in, they come up to her keel, and aboard.
	 */
	public boolean hangOn(PlayerEntity player) {
		if (hook != Hook.HELD || hookHolder != player || !(getWorld() instanceof ServerWorld world)) {
			return false;
		}
		letGoOfGrapple();
		keepHead(world);
		AirshipHookEntity head = hookEntity;
		// The crown in their raised hands, so that they hang from it where they are, on a rope drawn taut (no slack to
		// leave them standing on the ground): from there they swing.
		hookPos = player.getPos().add(0.0, AirshipHookEntity.hangBelow(player, true) + HOOK_GRIP, 0.0);
		hookPrev = hookPos.subtract(player.getVelocity());
		hookDrop = hookPos.distanceTo(lineOut());
		if (head == null || !player.startRiding(head, true)) {
			return false;
		}
		head.setVoluntary(true);
		setHook(Hook.OUT);
		// As when it catches something, the rope draws tight and the winch stops, unless the crew are winding them in.
		if (winch >= 0) {
			setWinch(null, 0);
			winchSpeed = 0.0;
		}
		// They pull themselves up it as they take hold, so that they swing clear of the ground under her.
		double most = Math.min(PULL_UP, hookDrop - HOOK_ABOARD - 0.5);
		pullUp = most > 0.0 ? MathHelper.clamp(hookDrop - clearDrop(world, player), 0.0, most) : 0.0;
		player.sendMessage(Text.translatable("hud.shootingstar.airship.hanging"), true);
		world.playSound(null, hookPos.x, hookPos.y, hookPos.z, Airship.GRAB, SoundCategory.NEUTRAL, 0.7F, 1.1F);
		return true;
	}

	/**
	 * The most rope on which someone hanging on the grapple swings clear of the ground under her keel, by HANG_CLEAR
	 * (or LINE_MAX, with no ground in reach of it).
	 */
	private double clearDrop(World world, Entity load) {
		Vec3d out = lineOut();
		BlockHitResult ground = world.raycast(new RaycastContext(out, out.add(0.0, -(LINE_MAX + 8.0), 0.0),
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, this));
		if (ground.getType() != HitResult.Type.BLOCK) {
			return LINE_MAX;
		}
		return out.y - ground.getPos().y - HANG_CLEAR - HOOK_GRIP - AirshipHookEntity.hangBelow(load, true);
	}

	/**
	 * Whoever holds the grapple throws it the way they look: it flies, swinging out on its rope, and takes hold of the
	 * first thing it touches (not them), or lies where it lands.
	 */
	public boolean throwGrapple(PlayerEntity player) {
		if (hook != Hook.HELD || hookHolder != player) {
			return false;
		}
		Vec3d hand = handOf(player, 1.0F);
		Vec3d fling = player.getRotationVector().multiply(THROW_SPEED).add(0.0, THROW_LIFT, 0.0);
		letGoOfGrapple();
		hookPos = hand;
		hookPrev = hand.subtract(fling);
		hookDrop = Math.min(LINE_MAX, Math.max(hookDrop, hand.distanceTo(lineOut()) + THROW_SLACK));
		thrownAt = age;
		getWorld().playSound(null, hand.x, hand.y, hand.z, Airship.WINCH, SoundCategory.NEUTRAL, 0.9F, 1.6F);
		return true;
	}

	/** Someone wound up to her keel on the grapple climbs aboard, into the free place nearest it. */
	private boolean comeAboard(Entity rider) {
		if (getPassengerList().size() >= PLACES.length) {
			return false;
		}
		int place = nearestFreePlace(LINE_OUT);
		wantedPlace = place;
		boolean in = rider.startRiding(this, true);
		wantedPlace = -1;
		return in;
	}

	/**
	 * Whoever holds the grapple hooks it onto something within their reach (using the grapple on it, as on a mob): it
	 * takes hold, and there it stays until the crew wind it up.
	 */
	public boolean hookOnto(PlayerEntity player, Entity target) {
		target = whole(target);
		if (hook != Hook.HELD || hookHolder != player || target == player || !canGrab(target)
				|| player.squaredDistanceTo(target) > HOOK_REACH * HOOK_REACH || !(getWorld() instanceof ServerWorld world)) {
			return false;
		}
		letGoOfGrapple();
		grab(world, target);
		boolean hooked = target.getVehicle() == hookEntity && hookEntity != null;
		if (hooked && player instanceof ServerPlayerEntity holder) {
			ModCriteria.fire(holder, "airship_grab");
		}
		return hooked;
	}

	/** Whoever holds the grapple still can: alive, on foot, not sneaking (which lets go), and within reach of her rope. */
	private boolean stillHeld() {
		return hookHolder != null && hookHolder.isAlive() && !hookHolder.isRemoved() && !hookHolder.hasVehicle()
				&& !hookHolder.isSneaking() && hookHolder.getWorld() == getWorld()
				&& handOf(hookHolder, 1.0F).distanceTo(lineOut()) <= LINE_MAX;
	}

	/** Whoever holds the grapple lets go of it (or throws it): it is out on its rope, and leaves them be a while. */
	private void letGoOfGrapple() {
		if (hookHolder != null) {
			spare(hookHolder);
		}
		hookHolder = null;
		dataTracker.set(HOOK_HELD, -1);
		if (hook == Hook.HELD) {
			setHook(Hook.OUT);
		}
	}

	/** The grapple someone is holding on the ground, if they are holding one. */
	@Nullable
	public static AirshipEntity grappleHeldBy(PlayerEntity player) {
		for (AirshipEntity ship : player.getWorld().isClient ? HELD_CLIENT : HELD_SERVER) {
			if (!ship.isRemoved() && ship.getWorld() == player.getWorld()
					&& ship.dataTracker.get(HOOK_HELD) == player.getId()) {
				return ship;
			}
		}
		return null;
	}

	/** Lets go of whatever is on the grapple (it drops from there). */
	public void releaseLoad() {
		if (hookEntity != null) {
			for (Entity load : List.copyOf(hookEntity.getPassengerList())) {
				load.stopRiding();
				load.fallDistance = 0.0F;
				load.setVelocity(Vec3d.ZERO);
			}
		}
	}

	/** Lets go of any load and takes the grapple's head out of the world (it is wound up, or she is gone). */
	private void stowHead() {
		releaseLoad();
		if (hookEntity != null) {
			hookEntity.discard();
			hookEntity = null;
		}
		dataTracker.set(HOOK_HEAD, -1);
	}

	/** The grapple's head, while the grapple is out. */
	@Nullable
	public AirshipHookEntity getHookEntity() {
		return hookEntity;
	}

	/** The grapple's head as a client knows it, while the grapple is out. */
	@Nullable
	public AirshipHookEntity getShownHookEntity() {
		int id = dataTracker.get(HOOK_HEAD);
		return id >= 0 && getWorld().getEntityById(id) instanceof AirshipHookEntity head ? head : null;
	}

	/** Whatever is caught on the grapple kicks or jerks: the grapple jolts on its rope. */
	void jolt(double strength) {
		if (hookPos != null && hookPrev != null) {
			hookPrev = hookPrev.add((random.nextDouble() - 0.5) * strength, -random.nextDouble() * strength * 0.5,
					(random.nextDouble() - 0.5) * strength);
		}
	}

	/** For tests: where the grapple's ring is, from where its rope comes out under her keel (world axes). */
	public Vec3d getHookOffset() {
		return hookPos == null ? Vec3d.ZERO : hookPos.subtract(lineOut());
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
		return ladderOf(entity) != null;
	}

	/**
	 * Which way is into the rope ladder an entity is on (towards her, level), or null if it is on none: the ladder is
	 * solid that way, as a ladder against a wall is, so that walking into it climbs it (AirshipLadderMixin).
	 */
	@Nullable
	public static Vec3d intoLadder(LivingEntity entity) {
		AirshipEntity ship = ladderOf(entity);
		if (ship == null) {
			return null;
		}
		float yawRad = ship.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		return new Vec3d(-MathHelper.cos(yawRad), 0.0, -MathHelper.sin(yawRad));
	}

	/** Where on her rope ladder a climber hangs, so far down it: trailing behind her as she goes (world). */
	private Vec3d ladderAt(double depth) {
		double down = Math.max(0.0, depth);
		return local(new Vec3d(LADDER_LINE.x, LADDER_LINE.y - down, LADDER_LINE.z - down * Math.tan(ladderLean)));
	}

	/** How far she moved the rope ladder an entity is on this tick, which carries it along; null if it is on none. */
	@Nullable
	public static Vec3d ladderCarry(LivingEntity entity) {
		AirshipEntity ship = ladderOf(entity);
		return ship == null ? null : ship.motion;
	}

	/** How far her rope ladder trails behind her (radians), as drawn. */
	public float getLadderLean(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevLadderLean, ladderLean);
	}

	@Nullable
	private static AirshipEntity ladderOf(LivingEntity entity) {
		if (entity.hasVehicle()) {
			return null;
		}
		Set<AirshipEntity> ladders = entity.getWorld().isClient ? LADDERS_CLIENT : LADDERS_SERVER;
		if (ladders.isEmpty()) {
			return null;
		}
		for (AirshipEntity ship : ladders) {
			if (ship.isRemoved() || ship.getWorld() != entity.getWorld()) {
				continue;
			}
			Vec3d top = ship.local(LADDER_LINE);
			Vec3d at = ship.ladderAt(top.y - entity.getY());
			double dx = entity.getX() - at.x;
			double dz = entity.getZ() - at.z;
			if (dx * dx + dz * dz < 0.45 * 0.45 && entity.getY() < top.y + 0.5 && entity.getY() > top.y - ship.getLadder() - 0.3) {
				return ship;
			}
		}
		return null;
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
				// Their footsteps on the boards of her floor.
				float walked = sinceStep.getOrDefault(passenger, 0.0F) + stride;
				if (walked > 0.7F) {
					walked = 0.0F;
					world.playSound(passenger.getX(), passenger.getY(), passenger.getZ(), SoundEvents.BLOCK_WOOD_STEP, SoundCategory.PLAYERS,
							0.18F, 0.9F + random.nextFloat() * 0.2F, false);
				}
				sinceStep.put(passenger, walked);
			}
		}
		prevPropSpin = propSpin;
		int throttle = getThrottle();
		// The propellers tick over while she is piloted and race with the throttle.
		float spin = isEngineRunning() ? 0.35F + 0.5F * Math.abs(throttle) + (float) getSpeed() * 1.5F : (float) getSpeed() * 0.5F;
		// They run up and run down, not start and stop at once.
		spinRate += (spin - spinRate) * 0.04F;
		propSpin += spinRate;
		prevSteer = steer;
		int turn = isLogicalSideForUpdatingMovement() ? clientControls.turn() : dataTracker.get(STEER);
		steer += (turn - steer) * 0.15F;
		prevClimbLook = climbLook;
		int climb = isLogicalSideForUpdatingMovement() ? clientControls.up() ? 1 : clientControls.down() ? -1 : 0 : dataTracker.get(CLIMB);
		climbLook += (climb - climbLook) * 0.12F;
		prevShownDrop = shownDrop;
		shownDrop += ((float) getHookDrop() - shownDrop) * 0.5F;
		prevShownHook = shownHook;
		Vector3f hookAt = dataTracker.get(HOOK_AT);
		shownHook = shownHook.add(new Vec3d(hookAt.x(), hookAt.y(), hookAt.z()).subtract(shownHook).multiply(0.6));
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
		hookPos = null;
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

	/** For tests: lets the grapple down at once to the given depth, the winch still letting it out. */
	public void lowerGrappleTo(double drop) {
		hangGrappleAt(drop);
		setWinch(null, 1);
	}

	/** For tests: the grapple hanging still at the given depth, the winch still. */
	public void hangGrappleAt(double drop) {
		hookDrop = drop;
		hookPos = lineOut().add(0.0, -drop, 0.0);
		hookPrev = hookPos;
		setHook(Hook.OUT);
		setWinch(null, 0);
		winchSpeed = 0.0;
	}

	/** For tests: whether the ladder is let down (or being). */
	public boolean isLadderDown() {
		return ladderDown;
	}
}
