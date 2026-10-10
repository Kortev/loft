package io.github.kortev.chitty.carriage;

import io.github.kortev.chitty.ChittyControls;
import io.github.kortev.shootingstar.registry.ModCriteria;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Dismounting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Child Catcher's carriage: a black iron cage on a high dray, drawn by a black horse in its shafts, the driver up on
 * the box at the front.
 *
 * <p>Her horse is hers: it comes with her, a fast black horse in a plumed harness, drawn as the game draws its own
 * horses (CarriageRenderer), walking, trotting and galloping as she goes. It is no mob of its own, so it never strays,
 * dies on its own or is left behind in a part of the world not loaded; it is solid (a hitbox of hers) and hitting it is
 * hitting her.
 *
 * <p>Two sit up on the box: the driver on the right, with the reins (on, back, left, right) and the whip (jump: a crack,
 * and the horse breaks into a gallop for a while), and one beside. The cage behind has standing room for four, and its
 * door in the back, which anyone outside may open or shut by using it. With it open, whatever you lead up to it on your
 * lead goes in (anything about a player's size), and whatever stands at the door you can shove in by hitting it; sneak
 * and use the open door to climb in yourself. With it shut, nobody inside gets out: not by sneaking, nor by hitting
 * anyone outside through the bars. Opened, a mob inside makes a run for it; a player can get out.
 *
 * <p>The disguise (the driver's key, standing): the cage dressed as a wandering trader's wagon, in his colours, with a
 * counter on its door. Set bait out on the counter (sneak and use the door with it in hand) and whoever reaches for it
 * (uses the door) is pulled in and the door slams on them; villagers come for food set out there (as they come for
 * bread on the ground) and are caught the same way. Nobody outside sees who is in the cage while she wears it: the
 * cloths hide them, and their names. Cracking the whip throws it all off as she drives away, as in the film, the bait
 * spilling into the road. Whoever set the bait (or anyone on the box) takes it back by using the door.
 *
 * <p>Like Chitty she is moved by whoever drives her (their client) and by the server when nobody does. Positions are in
 * blocks; local offsets are at yaw 0, x to her left, z forward.
 */
public class CarriageEntity extends Entity {
	private static final TrackedData<Integer> WOBBLE_TICKS = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> WOBBLE_SIDE = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Float> WOBBLE_STRENGTH = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Boolean> DOOR = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> DISGUISE = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** Counts the times the disguise has been thrown off, for clients to throw it. */
	private static final TrackedData<Integer> THROWN = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Counts the cracks of the whip, for clients to draw. */
	private static final TrackedData<Integer> WHIP = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Counts the times the trap has sprung, for clients to swing the door open and slam it. */
	private static final TrackedData<Integer> SNAP = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Which place each passenger, in the order they are listed, is in: three bits each. */
	private static final TrackedData<Integer> SEATING = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** The bait on the counter, for clients to draw. */
	@SuppressWarnings("unchecked")
	private static final TrackedData<ItemStack>[] BAIT = new TrackedData[] {
			DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.ITEM_STACK),
			DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.ITEM_STACK),
			DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.ITEM_STACK)};

	// --- what she is: from tools/carriage_model.py (Blender's (x, y, z) is our (-x, z, y)) -----------------------

	/** The top of her deck, the floor of the cage. */
	public static final double DECK_TOP = 1.20;
	/** Her deck, from its back to its front edge, and the cage on it, front wall to back, and its half width inside. */
	public static final double DECK_FRONT = 1.95;
	public static final double DECK_BACK = -1.78;
	public static final double CAGE_FRONT = 1.00;
	public static final double CAGE_BACK = -1.62;
	public static final double CAGE_HALF_WIDTH = 0.875;
	public static final double CAGE_HEIGHT = 2.0;
	/** The door: half its width, and the hinge, on her right at the back. */
	public static final double DOOR_HALF_WIDTH = 0.39;
	public static final Vec3d HINGE = new Vec3d(-0.402, DECK_TOP, -1.6175);
	/** Where someone let out of the cage is set down: on the ground behind the door. */
	public static final Vec3d DOOR_OUT = new Vec3d(0.0, 0.0, -2.48);
	/**
	 * Her places: the driver's on the box (on her right, with the reins), the one beside it, and four standing in the
	 * cage (where their feet are). The box's are where the rider's seat is, a little into the cushion, as Chitty's.
	 */
	public static final Vec3d[] PLACES = {new Vec3d(-0.22, 1.57, 1.34), new Vec3d(0.32, 1.57, 1.34),
			new Vec3d(-0.42, DECK_TOP, 0.55), new Vec3d(0.42, DECK_TOP, 0.55), new Vec3d(-0.42, DECK_TOP, -0.95),
			new Vec3d(0.42, DECK_TOP, -0.95)};
	public static final int DRIVER = 0;
	public static final int BOX = 1;
	/** The first of the places in the cage. */
	public static final int CAGE = 2;
	/** Where the driver holds the reins. */
	public static final Vec3d HANDS = new Vec3d(-0.22, 2.02, 1.66);
	/** Her axles (along her) and the radii of their wheels; the fore-carriage turns about the front one. */
	public static final double REAR_AXLE = -0.98;
	public static final double FRONT_AXLE = 0.95;
	public static final float REAR_RADIUS = 0.52F;
	public static final float FRONT_RADIUS = 0.43F;
	/** How far the fore-carriage, and the horse in its shafts, turn either way at most (degrees). */
	public static final float STEER_MAX = 22.0F;
	/** Where the horse stands in the shafts, ahead of her middle (and of the fore-carriage's turntable). */
	public static final double HORSE_AHEAD = 3.08;
	/** The splinter bar its traces pull on: across the fore-carriage, ahead of the axle. */
	public static final Vec3d SPLINTER = new Vec3d(0.0, 0.74, FRONT_AXLE + 0.42);
	/**
	 * Where the bait stands on the door's counter (the middle one; the others BAIT_APART either side), shut: the counter
	 * swings with the door.
	 */
	public static final Vec3d BAIT_AT = new Vec3d(0.0, 1.305, -1.87);
	public static final double BAIT_APART = 0.22;

	// --- how she goes ---------------------------------------------------------------------------------

	/** Her top speed (blocks a tick): a fast horse's trot pulling her; on a road, faster than a ridden horse. */
	static final double TOP = 0.40;
	static final double ROAD = 1.4;
	/** After a crack of the whip, for a while, a gallop. */
	static final double GALLOP = 1.15;
	static final int GALLOP_TICKS = 70;
	static final int WHIP_COOLDOWN = 16;
	static final double ACCEL = 0.007;
	static final double BRAKE = 0.03;
	static final double REVERSE_TOP = 0.06;
	/** In water up to her axles she can only creep. */
	static final double WADE_TOP = 0.08;
	/** Degrees a tick she turns at most, once she is moving (less at her fastest). */
	static final float TURN = 3.5F;
	static final double GRAVITY = 0.08;
	/** Slower than this (blocks a tick) she counts as standing, to dress or undress her. */
	static final double STANDING = 0.03;
	/** How far the gaits change, by speed (blocks a tick): a walk, a trot, then a gallop. */
	public static final double TROT = 0.14;
	public static final double CANTER = 0.36;
	/** How far you can be from what you are leading (a lead's length). */
	static final double LEAD_REACH = 10.0;
	/** What fits in the cage. */
	static final float CAGE_FITS_WIDTH = 1.0F;
	static final float CAGE_FITS_HEIGHT = 2.1F;
	/** How near the door (blocks, out from it along the ground) something must be to be shoved in, or caught by the bait. */
	static final double DOOR_REACH = 1.4;
	/** How far villagers come from for food set out as bait. */
	static final double LURE_REACH = 16.0;

	public static final int ACTION_WHIP = 0;
	public static final int ACTION_DISGUISE = 1;

	/** Client-only behaviour the common code calls into: the driver's reins, sounds. Set by the client initializer. */
	public interface ClientHooks {
		/** The local player's controls, if they are driving this carriage. */
		ChittyControls controls(CarriageEntity carriage);

		/** The carriage is ticking on a client: start its sounds if they have not been. */
		void tick(CarriageEntity carriage);
	}

	@Nullable
	public static ClientHooks client;

	// Moving, on whichever side is moving her.
	private float speed;
	private float yawVelocity;
	private int galloping;
	private boolean wasMoving;
	@Nullable
	private Vec3d lastPos;
	private Vec3d motion = Vec3d.ZERO;
	// The fore-carriage's turn (-1 to 1, left positive), from how she turns, on every side; and how far above her the
	// horse stands (on the ground ahead of her), easing onto it.
	private float steer;
	private float prevSteer;
	private float horseLift;
	private float prevHorseLift;

	// The server's view of her passengers, her cage and its bait.
	private final Entity[] seated = new Entity[PLACES.length];
	private int wantedPlace = -1;
	/** The places her passengers had when she was saved (by who they are), for them to have again when they get back in. */
	private final Map<UUID, Integer> savedPlaces = new HashMap<>();
	/** With the door open: how long each mob in the cage waits before it makes a run for it. */
	private final Map<Entity, Integer> escaping = new HashMap<>();
	private final SimpleInventory bait = new SimpleInventory(BAIT.length);
	/** Whoever set the bait out, who may take it back (and is not caught by it). */
	@Nullable
	private UUID baiter;
	private int whipCooldown;
	private double hoofDistance;
	private final CarriagePartEntity[] parts = new CarriagePartEntity[CarriagePartEntity.COUNT];

	// Interpolation of a carriage someone else is moving.
	private int lerpTicks;
	private double lerpX;
	private double lerpY;
	private double lerpZ;
	private double lerpYaw;
	private double lerpPitch;

	// Looks, on clients: her wheels' turn (rear and front, radians), the door's swing (0 shut to 1 open), the trap
	// springing (ticks since), the disguise thrown off (ticks since, where and which way she was then), the whip's crack
	// (ticks since), and the horse's legs (how far they have gone, and how fast).
	private float rearSpin;
	private float prevRearSpin;
	private float frontSpin;
	private float prevFrontSpin;
	private float door;
	private float prevDoor;
	private int seenSnap;
	private int snapAge = Integer.MAX_VALUE / 2;
	private int seenThrown;
	private int thrownAge = Integer.MAX_VALUE / 2;
	private Vec3d thrownAt = Vec3d.ZERO;
	private float thrownYaw;
	private int seenWhip;
	private int whipAge = Integer.MAX_VALUE / 2;
	private float limbPos;
	private float prevLimbPos;
	private float limbSpeed;
	private float prevLimbSpeed;

	public CarriageEntity(EntityType<? extends CarriageEntity> type, World world) {
		super(type, world);
		this.intersectionChecked = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(WOBBLE_TICKS, 0);
		builder.add(WOBBLE_SIDE, 1);
		builder.add(WOBBLE_STRENGTH, 0.0F);
		builder.add(DOOR, false);
		builder.add(DISGUISE, false);
		builder.add(THROWN, 0);
		builder.add(WHIP, 0);
		builder.add(SNAP, 0);
		builder.add(SEATING, 0);
		for (TrackedData<ItemStack> slot : BAIT) {
			builder.add(slot, ItemStack.EMPTY);
		}
	}

	// --- what she is ---------------------------------------------------------------------------------

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entry) {
		return new EntitySpawnS2CPacket(this, entry);
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(Carriage.ITEM);
	}

	/** She keeps herself on the ground; vanilla gravity would also have the server kick a driver for "floating". */
	@Override
	public boolean hasNoGravity() {
		return true;
	}

	@Override
	public float getStepHeight() {
		return 1.0F;
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
				&& !(other instanceof CarriagePartEntity part && part.getCarriage() == this);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	/** She is longer than her box, with her horse out ahead. */
	@Override
	public Box getVisibilityBoundingBox() {
		return getBoundingBox().expand(5.0, 1.0, 5.0);
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
		setDamageWobbleStrength(getDamageWobbleStrength() + amount * 3.5F);
		emitGameEvent(GameEvent.ENTITY_DAMAGE, source.getAttacker());
		boolean creative = source.getAttacker() instanceof PlayerEntity player && player.getAbilities().creativeMode;
		if (creative || getDamageWobbleStrength() > 40.0F) {
			if (!creative && getWorld().getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
				ItemStack stack = new ItemStack(Carriage.ITEM);
				if (hasCustomName()) {
					stack.set(DataComponentTypes.CUSTOM_NAME, getCustomName());
				}
				dropStack(stack);
				spillBait(Vec3d.ZERO);
			}
			discard();
		}
		return true;
	}

	/** Her horse is hit (its hitbox, CarriagePartEntity.HORSE): it squeals, and she takes the blow. */
	public boolean damageHorse(DamageSource source, float amount) {
		if (!getWorld().isClient) {
			Vec3d at = horseAt();
			getWorld().playSound(null, at.x, at.y + 1.0, at.z, SoundEvents.ENTITY_HORSE_HURT, SoundCategory.NEUTRAL, 1.0F,
					0.9F + random.nextFloat() * 0.2F);
		}
		return damage(source, amount);
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

	public boolean isDoorOpen() {
		return dataTracker.get(DOOR);
	}

	public boolean isDisguised() {
		return dataTracker.get(DISGUISE);
	}

	/** How fast she is going, any way along the ground (blocks a tick). */
	public double getSpeed() {
		return motion.horizontalLength();
	}

	/** How fast she is going forward (blocks a tick; backing, less than nothing). */
	public double getForwardSpeed() {
		float yawRad = getYaw() * MathHelper.RADIANS_PER_DEGREE;
		return motion.x * -MathHelper.sin(yawRad) + motion.z * MathHelper.cos(yawRad);
	}

	// --- her horse -----------------------------------------------------------------------------------------

	/** Where her horse stands, on her (local): ahead of the fore-carriage, turned with it about its turntable. */
	public Vec3d horseLocal(float steer, float lift) {
		return new Vec3d(0.0, 0.0, HORSE_AHEAD - FRONT_AXLE).rotateY(steer * STEER_MAX * MathHelper.RADIANS_PER_DEGREE)
				.add(0.0, lift, FRONT_AXLE);
	}

	/** Where her horse stands in the world. */
	public Vec3d horseAt() {
		return toWorld(horseLocal(steer, horseLift));
	}

	/** Her horse's height over her as it is drawn (on the ground ahead of her, a step up or down at most). */
	public float getHorseLift(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevHorseLift, horseLift);
	}

	/** The top of the ground at a point beside her: from a block above her wheels to a block below (else hers). */
	private double groundAt(Vec3d at) {
		World world = getWorld();
		for (int dy = 1; dy >= -1; dy--) {
			BlockPos pos = BlockPos.ofFloored(at.x, getY() + dy, at.z);
			VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
			if (!shape.isEmpty()) {
				double top = pos.getY() + shape.getMax(Direction.Axis.Y);
				if (top <= getY() + 1.05) {
					return top;
				}
			}
		}
		return getY();
	}

	/** How far its legs have gone (for their swing) and how fast they go, on clients, as LivingEntity's limbs do. */
	public float getLimbPos(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevLimbPos, limbPos);
	}

	public float getLimbSpeed(float tickDelta) {
		return Math.min(1.0F, MathHelper.lerp(tickDelta, prevLimbSpeed, limbSpeed));
	}

	// --- passengers ------------------------------------------------------------------------------------

	/**
	 * Using her: leading something, it goes into the cage (if the door is open); at the back, the disguise's counter
	 * (bait set out, taken back, or reached for) or the door (opened, shut, or, sneaking with it open, climbed in);
	 * anywhere else, you get up on the box, in the free place nearest where you clicked.
	 */
	@Override
	public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
		if (player.getRootVehicle() == this || player.isSpectator()) {
			return ActionResult.PASS;
		}
		Vec3d local = hitPos.rotateY(getYaw() * MathHelper.RADIANS_PER_DEGREE);
		boolean client = getWorld().isClient;
		List<MobEntity> led = ledBy(player);
		if (!led.isEmpty()) {
			if (!client) {
				lead(player, led);
			}
			return ActionResult.success(client);
		}
		if (atDoor(local)) {
			if (!client) {
				useBack(player, hand);
			}
			return ActionResult.success(client);
		}
		if (player.shouldCancelInteraction()) {
			return ActionResult.PASS;
		}
		int place = nearestFreeSeat(local);
		if (place < 0) {
			return ActionResult.PASS;
		}
		if (!client) {
			return seat(player, place) ? ActionResult.CONSUME : ActionResult.PASS;
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (player.shouldCancelInteraction() || player.getRootVehicle() == this) {
			return ActionResult.PASS;
		}
		int place = seated[DRIVER] == null ? DRIVER : seated[BOX] == null ? BOX : -1;
		if (!getWorld().isClient) {
			return place >= 0 && seat(player, place) ? ActionResult.CONSUME : ActionResult.PASS;
		}
		return ActionResult.SUCCESS;
	}

	/**
	 * The back of her, used. Disguised: sneaking, with something in hand you set one of it out on the counter as bait,
	 * and with nothing in hand you open or shut the door (carefully, not touching the bait); not sneaking, with bait
	 * out, whoever set it takes it back and anyone else reaching for it is caught. Undisguised, the door: sneaking with
	 * it open, you climb in; else it opens or shuts.
	 */
	private void useBack(PlayerEntity player, Hand hand) {
		ItemStack held = player.getStackInHand(hand);
		if (isDisguised()) {
			if (player.shouldCancelInteraction()) {
				if (!held.isEmpty()) {
					setBait(player, held);
				} else {
					setDoor(!isDoorOpen(), player);
				}
				return;
			}
			if (hasBait()) {
				if (mayTakeBait(player)) {
					takeBait(player);
				} else {
					spring(player);
				}
				return;
			}
		}
		if (player.shouldCancelInteraction() && isDoorOpen()) {
			if (!putInCage(player)) {
				player.sendMessage(Text.translatable("hud.shootingstar.carriage.full"), true);
			}
		} else {
			setDoor(!isDoorOpen(), player);
		}
	}

	/** Whether a point on her (local) is at her door: the back of the cage, about the middle. */
	private static boolean atDoor(Vec3d local) {
		return local.z < CAGE_BACK + 0.45 && Math.abs(local.x) < DOOR_HALF_WIDTH + 0.15 && local.y > DECK_TOP - 0.3;
	}

	/** Whatever a player has on their lead near her. */
	private List<MobEntity> ledBy(PlayerEntity player) {
		return getWorld().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(LEAD_REACH),
				mob -> mob.getLeashHolder() == player);
	}

	/** What a player leads up to her goes into the cage, if its door is open and it fits, off its lead (back into their hand). */
	private void lead(PlayerEntity player, List<MobEntity> led) {
		boolean caged = false;
		boolean refused = false;
		for (MobEntity mob : led) {
			if (!isDoorOpen() || !fitsInCage(mob)) {
				refused = true;
				continue;
			}
			mob.detachLeash(true, false);
			if (!player.getAbilities().creativeMode) {
				player.giveItemStack(new ItemStack(Items.LEAD));
			}
			if (putInCage(mob)) {
				caged = true;
			} else {
				refused = true;
			}
		}
		if (refused && !caged) {
			player.sendMessage(Text.translatable("hud.shootingstar.carriage." + (!isDoorOpen() ? "door_shut" : "wont_fit")), true);
		}
	}

	/** Whether something could be put in her cage: alive, about a player's size at most, and not riding or ridden. */
	public boolean fitsInCage(Entity entity) {
		return entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()
				&& living.getWidth() <= CAGE_FITS_WIDTH && living.getHeight() <= CAGE_FITS_HEIGHT && !living.hasVehicle()
				&& !living.hasPassengers();
	}

	/** Whether her cage has room. */
	public boolean cageHasRoom() {
		for (int i = CAGE; i < PLACES.length; i++) {
			if (!taken(i)) {
				return true;
			}
		}
		return false;
	}

	/** Puts something in a free place in the cage (whatever the door; it is the door that keeps it in). */
	public boolean putInCage(Entity entity) {
		if (getWorld().isClient || !fitsInCage(entity)) {
			return false;
		}
		for (int i = CAGE; i < PLACES.length; i++) {
			if (!taken(i)) {
				if (entity instanceof MobEntity mob && mob.isLeashed()) {
					mob.detachLeash(true, true);
				}
				wantedPlace = i;
				boolean in = entity.startRiding(this, true);
				wantedPlace = -1;
				if (in) {
					getWorld().playSound(null, getX(), getY() + DECK_TOP, getZ(), SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.NEUTRAL,
							0.8F, 0.8F);
				}
				return in;
			}
		}
		return false;
	}

	/**
	 * Something hit by a player as it stands at her open door is shoved in instead of hurt: true if it was (and the hit
	 * goes no further). For whoever is outside, not in the cage themselves.
	 */
	public boolean shove(PlayerEntity by, LivingEntity target) {
		if (!isDoorOpen() || by.getVehicle() == this || target == by || !nearDoor(target) || !fitsInCage(target)) {
			return false;
		}
		if (!putInCage(target)) {
			by.sendMessage(Text.translatable("hud.shootingstar.carriage.full"), true);
			return true;
		}
		if (target instanceof PlayerEntity caught) {
			caught.sendMessage(Text.translatable("hud.shootingstar.carriage.shoved", by.getDisplayName()), true);
		}
		return true;
	}

	/** Whether something stands at her door, outside, on the ground behind it. */
	public boolean nearDoor(Entity entity) {
		Vec3d out = toWorld(DOOR_OUT.add(0.0, 0.0, 0.35));
		double dx = entity.getX() - out.x;
		double dz = entity.getZ() - out.z;
		return dx * dx + dz * dz < DOOR_REACH * DOOR_REACH && Math.abs(entity.getY() - getY()) < 1.6;
	}

	/** The carriage whose open door something stands at, if any. */
	@Nullable
	public static CarriageEntity doorNear(Entity entity) {
		for (CarriageEntity carriage : entity.getWorld().getEntitiesByClass(CarriageEntity.class, entity.getBoundingBox().expand(4.0),
				CarriageEntity::isDoorOpen)) {
			if (carriage.nearDoor(entity)) {
				return carriage;
			}
		}
		return null;
	}

	/**
	 * Opens or shuts the cage door. Shut on someone, they are caught; opened, every mob inside makes a run for it, one
	 * after another.
	 */
	public void setDoor(boolean open, @Nullable PlayerEntity by) {
		if (open == isDoorOpen() || getWorld().isClient) {
			return;
		}
		dataTracker.set(DOOR, open);
		Vec3d at = toWorld(HINGE.add(0.4, 1.0, 0.0));
		getWorld().playSound(null, at.x, at.y, at.z, open ? SoundEvents.BLOCK_IRON_DOOR_OPEN : SoundEvents.BLOCK_IRON_DOOR_CLOSE,
				SoundCategory.NEUTRAL, 1.0F, 0.8F);
		if (!open) {
			getWorld().playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.NEUTRAL, 1.0F, 1.3F);
		}
		escaping.clear();
		int caught = 0;
		for (int i = CAGE; i < PLACES.length; i++) {
			Entity inside = seated[i];
			if (inside == null || inside.getVehicle() != this) {
				continue;
			}
			caught++;
			if (open && !(inside instanceof PlayerEntity)) {
				escaping.put(inside, 10 + random.nextInt(30) + 15 * escaping.size());
			}
		}
		if (!open && caught > 0 && by instanceof ServerPlayerEntity player) {
			ModCriteria.fire(player, "carriage_catch");
		}
	}

	/** On the server: a mob in the cage with the door open gets out, when its turn comes. */
	private void escapes() {
		if (!isDoorOpen() || escaping.isEmpty()) {
			return;
		}
		for (Map.Entry<Entity, Integer> e : List.copyOf(escaping.entrySet())) {
			Entity inside = e.getKey();
			if (inside.getVehicle() != this) {
				escaping.remove(inside);
			} else if (e.getValue() <= 1) {
				escaping.remove(inside);
				inside.stopRiding();
			} else {
				escaping.put(inside, e.getValue() - 1);
			}
		}
	}

	// --- the bait -------------------------------------------------------------------------------------

	public boolean hasBait() {
		return !bait.isEmpty();
	}

	/** On clients too: the bait on the counter, slot by slot. */
	public ItemStack getBait(int slot) {
		return dataTracker.get(BAIT[slot]);
	}

	/** Whoever set the bait out, or anyone up on the box, may take it back. */
	public boolean mayTakeBait(PlayerEntity player) {
		return player.getUuid().equals(baiter) || player.getVehicle() == this && seatOf(player) < CAGE;
	}

	/** Sets one of what a player holds out on the counter as bait, in its first empty place. */
	public boolean setBait(PlayerEntity player, ItemStack held) {
		for (int i = 0; i < bait.size(); i++) {
			if (bait.getStack(i).isEmpty()) {
				if (baiter == null || bait.isEmpty()) {
					baiter = player.getUuid();
				}
				bait.setStack(i, held.copyWithCount(1));
				held.decrementUnlessCreative(1, player);
				publishBait();
				Vec3d at = baitAt(i);
				getWorld().playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.NEUTRAL, 0.8F, 1.0F);
				return true;
			}
		}
		player.sendMessage(Text.translatable("hud.shootingstar.carriage.counter_full"), true);
		return false;
	}

	/** Takes the last bait set out back into a player's hands. */
	private void takeBait(PlayerEntity player) {
		for (int i = bait.size() - 1; i >= 0; i--) {
			ItemStack stack = bait.getStack(i);
			if (!stack.isEmpty()) {
				bait.setStack(i, ItemStack.EMPTY);
				player.giveItemStack(stack);
				publishBait();
				Vec3d at = baitAt(i);
				getWorld().playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.NEUTRAL, 0.8F, 1.0F);
				return;
			}
		}
	}

	/** Every piece of bait off the counter into the road, thrown along `push`. */
	private void spillBait(Vec3d push) {
		for (int i = 0; i < bait.size(); i++) {
			ItemStack stack = bait.removeStack(i);
			if (!stack.isEmpty()) {
				Vec3d at = baitAt(i);
				ItemEntity item = new ItemEntity(getWorld(), at.x, at.y + 0.1, at.z, stack, push.x, 0.15, push.z);
				item.setToDefaultPickupDelay();
				getWorld().spawnEntity(item);
			}
		}
		publishBait();
	}

	private void publishBait() {
		for (int i = 0; i < BAIT.length; i++) {
			dataTracker.set(BAIT[i], bait.getStack(i).copy());
		}
	}

	/** Where a piece of bait stands on the counter, in the world (the door shut). */
	public Vec3d baitAt(int slot) {
		return toWorld(BAIT_AT.add((slot - 1) * BAIT_APART, 0.0, 0.0));
	}

	/**
	 * The trap: someone reaching for the bait is pulled in, and the door slams on them (it springs open and shut, and
	 * is left shut). Nothing happens if the cage is full or they do not fit.
	 */
	public boolean spring(Entity victim) {
		if (getWorld().isClient || !fitsInCage(victim) || !cageHasRoom()) {
			return false;
		}
		dataTracker.set(DOOR, false);
		escaping.clear();
		if (!putInCage(victim)) {
			return false;
		}
		dataTracker.set(SNAP, dataTracker.get(SNAP) + 1);
		Vec3d at = toWorld(HINGE.add(0.4, 1.0, 0.0));
		getWorld().playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.NEUTRAL, 1.2F, 1.1F);
		getWorld().playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.NEUTRAL, 1.0F, 1.4F);
		if (victim instanceof PlayerEntity caught) {
			caught.sendMessage(Text.translatable("hud.shootingstar.carriage.trapped"), true);
		}
		if (baiter != null && getWorld().getPlayerByUuid(baiter) instanceof ServerPlayerEntity setter) {
			ModCriteria.fire(setter, "carriage_trap");
		}
		return true;
	}

	/** Whether there is food out on the counter that villagers come for (as they come for it lying on the ground). */
	private boolean villagerFood() {
		for (int i = 0; i < bait.size(); i++) {
			if (VillagerEntity.ITEM_FOOD_VALUES.containsKey(bait.getStack(i).getItem())) {
				return true;
			}
		}
		return false;
	}

	/** Food set out on her counter draws the villagers round about to it, and whoever reaches it is caught. */
	private void lure(ServerWorld world) {
		Vec3d out = toWorld(DOOR_OUT);
		for (VillagerEntity villager : world.getEntitiesByClass(VillagerEntity.class, getBoundingBox().expand(LURE_REACH, 4.0, LURE_REACH),
				v -> v.isAlive() && !v.hasVehicle() && !v.isSleeping())) {
			if (!cageHasRoom()) {
				return;
			}
			if (nearDoor(villager)) {
				spring(villager);
			} else {
				villager.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(out, 0.6F, 0));
			}
		}
	}

	// --- places ---------------------------------------------------------------------------------------

	/** The free place on the box nearest a point on her (local), or -1 if both are taken. */
	private int nearestFreeSeat(Vec3d local) {
		int best = -1;
		double bestDistance = Double.MAX_VALUE;
		for (int i = DRIVER; i < CAGE; i++) {
			if (taken(i)) {
				continue;
			}
			double dx = PLACES[i].x - local.x;
			double distance = dx * dx;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return best;
	}

	private boolean taken(int place) {
		return seated[place] != null && seated[place].isAlive() && seated[place].getVehicle() == this;
	}

	/** Puts a passenger in a particular place (DRIVER, BOX, or one in the cage), if it is free. */
	public boolean seat(Entity passenger, int place) {
		if (place < 0 || place >= PLACES.length || taken(place)) {
			return false;
		}
		wantedPlace = place;
		boolean in = passenger.startRiding(this, place >= CAGE);
		wantedPlace = -1;
		return in;
	}

	/** The place a passenger is in (DRIVER, BOX, or CAGE and on), or -1 if they are not aboard. */
	public int seatOf(Entity passenger) {
		if (!getWorld().isClient) {
			for (int i = 0; i < PLACES.length; i++) {
				if (seated[i] == passenger) {
					return i;
				}
			}
			return -1;
		}
		int i = getPassengerList().indexOf(passenger);
		return i < 0 || i >= PLACES.length ? -1 : dataTracker.get(SEATING) >> (3 * i) & 7;
	}

	/** Whether a passenger is in her cage. */
	public boolean inCage(Entity passenger) {
		return seatOf(passenger) >= CAGE;
	}

	/** Whether someone is hidden from outside: in her cage while she wears the disguise. */
	public static boolean hidden(Entity entity) {
		return entity.getVehicle() instanceof CarriageEntity carriage && carriage.isDisguised() && carriage.inCage(entity);
	}

	/** How many are in her cage. */
	public int prisoners() {
		int n = 0;
		for (Entity passenger : getPassengerList()) {
			if (inCage(passenger)) {
				n++;
			}
		}
		return n;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().size() < PLACES.length;
	}

	/** The driver: whoever is up on the box with the reins, if it is a player. */
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
		super.addPassenger(passenger);
		if (getWorld().isClient) {
			return;
		}
		int place = wantedPlace >= 0 && !taken(wantedPlace) ? wantedPlace : -1;
		Integer saved = savedPlaces.remove(passenger.getUuid());
		if (place < 0 && saved != null && saved >= 0 && saved < PLACES.length && !taken(saved)) {
			place = saved;
		}
		// Nobody chose a place: a player gets up on the box; anything else goes in the cage.
		int from = passenger instanceof PlayerEntity ? DRIVER : CAGE;
		for (int i = from; place < 0 && i < PLACES.length; i++) {
			if (!taken(i)) {
				place = i;
			}
		}
		if (place >= 0) {
			seated[place] = passenger;
		}
		publishSeating();
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!getWorld().isClient) {
			for (int i = 0; i < PLACES.length; i++) {
				if (seated[i] == passenger) {
					seated[i] = null;
				}
			}
			escaping.remove(passenger);
			publishSeating();
		}
	}

	private void publishSeating() {
		int bits = 0;
		List<Entity> passengers = getPassengerList();
		for (int i = 0; i < passengers.size() && i < PLACES.length; i++) {
			int place = Math.max(0, seatOf(passengers.get(i)));
			bits |= place << (3 * i);
		}
		dataTracker.set(SEATING, bits);
	}

	/**
	 * Someone in her is sneaking (PlayerEntity.shouldDismount): whether they get off. Up on the box, always; in the cage,
	 * only with the door open.
	 */
	public boolean letsOut(PlayerEntity player) {
		if (!inCage(player) || isDoorOpen()) {
			return true;
		}
		if (!getWorld().isClient && age % 10 == 0) {
			player.sendMessage(Text.translatable("hud.shootingstar.carriage.locked"), true);
		}
		return false;
	}

	@Override
	protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
		int place = MathHelper.clamp(seatOf(passenger), 0, PLACES.length - 1);
		return PLACES[place].rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE);
	}

	/** Up on the box they sit; in the cage they stand, their feet on its floor; and they all turn with her. */
	@Override
	protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
		if (!hasPassenger(passenger)) {
			return;
		}
		int place = seatOf(passenger);
		if (place >= CAGE) {
			Vec3d at = toWorld(PLACES[place]);
			positionUpdater.accept(passenger, at.x, at.y, at.z);
		} else {
			super.updatePassengerPosition(passenger, positionUpdater);
		}
		if (passenger instanceof LivingEntity) {
			float turn = MathHelper.wrapDegrees(getYaw() - prevYaw);
			passenger.setYaw(passenger.getYaw() + turn);
			passenger.setHeadYaw(passenger.getHeadYaw() + turn);
			if (place < CAGE) {
				clampPassengerYaw(passenger);
			}
		}
	}

	@Override
	public void onPassengerLookAround(Entity passenger) {
		if (!inCage(passenger)) {
			clampPassengerYaw(passenger);
		}
	}

	private void clampPassengerYaw(Entity passenger) {
		passenger.setBodyYaw(getYaw());
		float f = MathHelper.wrapDegrees(passenger.getYaw() - getYaw());
		float g = MathHelper.clamp(f, -130.0F, 130.0F);
		passenger.prevYaw += g - f;
		passenger.setYaw(passenger.getYaw() + g - f);
		passenger.setHeadYaw(passenger.getYaw());
	}

	/**
	 * Out of the cage, by its door onto the ground behind her; off the box, down her side, the driver's on her right;
	 * if that is blocked, the other side.
	 */
	@Override
	public Vec3d updatePassengerForDismount(LivingEntity passenger) {
		Vec3d rel = passenger.getPos().subtract(getPos()).rotateY(getYaw() * MathHelper.RADIANS_PER_DEGREE);
		boolean fromCage = rel.z < CAGE_FRONT && rel.y > DECK_TOP - 0.3;
		List<Vec3d> tries = new ArrayList<>();
		if (fromCage) {
			tries.add(DOOR_OUT);
			tries.add(DOOR_OUT.add(0.0, 0.0, -0.8));
		}
		double side = rel.x >= 0 ? 1.0 : -1.0;
		double along = fromCage ? -0.6 : PLACES[DRIVER].z;
		tries.add(new Vec3d(side * 1.45, 0.0, along));
		tries.add(new Vec3d(-side * 1.45, 0.0, along));
		for (Vec3d local : tries) {
			Vec3d at = toWorld(local);
			for (int dy : new int[] {0, 1, -1, -2}) {
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

	/** A point on her (local) in the world. */
	public Vec3d toWorld(Vec3d local) {
		return getPos().add(local.rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE));
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
			drive(getWorld().isClient && client != null ? client.controls(this) : ChittyControls.NONE);
		} else {
			lerp();
			setVelocity(Vec3d.ZERO);
		}
		wasMoving = moving;
		Vec3d pos = getPos();
		motion = lastPos == null ? Vec3d.ZERO : pos.subtract(lastPos);
		lastPos = pos;
		// The fore-carriage turns as she turns (on every side, from how she has turned), and her horse with it, onto the
		// ground ahead of her.
		prevSteer = steer;
		float turned = MathHelper.wrapDegrees(prevYaw - getYaw());
		float want = getSpeed() > 0.005 ? MathHelper.clamp(turned / TURN, -1.0F, 1.0F) : steer;
		steer += (want - steer) * 0.3F;
		prevHorseLift = horseLift;
		float lift = (float) (groundAt(toWorld(horseLocal(steer, 0.0F))) - getY());
		horseLift = age < 2 ? lift : horseLift + (lift - horseLift) * 0.4F;
		if (getWorld() instanceof ServerWorld world) {
			serverTick(world);
		} else {
			clientTick();
		}
	}

	/** Taking over a carriage that was moving without us: carry on as she was going. */
	private void adopt() {
		speed = (float) getForwardSpeed();
		setVelocity(motion);
	}

	/** Sets her going: forward speed in blocks a tick. For tests and commands. */
	public void launch(float speed) {
		this.speed = speed;
	}

	/** Her top speed on the ground she is on (blocks a tick). */
	public double topSpeed() {
		double top = TOP;
		if (onRoad()) {
			top *= ROAD;
		}
		if (galloping > 0) {
			top *= GALLOP;
		}
		return top;
	}

	/** Whether she is on a road: a path, gravel, or anything paved. */
	public boolean onRoad() {
		BlockState under = getWorld().getBlockState(getVelocityAffectingPos());
		if (under.isAir()) {
			under = getWorld().getBlockState(getBlockPos().down());
		}
		return under.isOf(Blocks.DIRT_PATH) || under.isOf(Blocks.GRAVEL) || under.isOf(Blocks.COBBLESTONE)
				|| under.isOf(Blocks.MOSSY_COBBLESTONE) || under.isOf(Blocks.SMOOTH_STONE) || under.isOf(Blocks.BRICKS)
				|| under.isIn(BlockTags.STONE_BRICKS) || under.isIn(BlockTags.SLABS) || under.isOf(Blocks.POLISHED_ANDESITE)
				|| under.isOf(Blocks.POLISHED_DIORITE) || under.isOf(Blocks.POLISHED_GRANITE) || under.isOf(Blocks.MUD_BRICKS)
				|| under.isOf(Blocks.DEEPSLATE_BRICKS) || under.isOf(Blocks.DEEPSLATE_TILES) || under.isOf(Blocks.POLISHED_BLACKSTONE_BRICKS);
	}

	private void drive(ChittyControls in) {
		World world = getWorld();
		boolean driven = getControllingPassenger() != null;
		if (!driven) {
			in = ChittyControls.NONE;
		}
		if (galloping > 0) {
			galloping--;
		}
		Vec3d v = getVelocity();
		boolean ground = isOnGround();
		double depth = getFluidHeight(FluidTags.WATER);
		double top = topSpeed();
		if (depth > 0.4) {
			top = Math.min(top, WADE_TOP);
		}
		if (ground || depth > 0.05) {
			speed = (float) pedal(speed, in.forward(), top);
		} else {
			speed *= 0.995F;
		}
		float rate = 0.0F;
		double s = Math.abs(speed);
		if (ground || depth > 0.05) {
			rate = (float) (in.turn() * TURN * MathHelper.clamp(s / 0.06, 0.0, 1.0) * (1.0 - 0.3 * Math.min(1.0, s / (TOP * ROAD)))
					* Math.signum(speed));
		}
		yawVelocity += (rate - yawVelocity) * 0.4F;
		setYaw(getYaw() - yawVelocity);

		float yawRad = getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d ahead = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
		double vy = depth > 0.4 ? (ground ? -GRAVITY : v.y * 0.8 - 0.01) : (v.y - GRAVITY) * 0.98;
		Vec3d horizontal = ahead.multiply(speed);
		Vec3d step = new Vec3d(horizontal.x, vy, horizontal.z);
		// Her box is square and shorter than she is, and her horse goes ahead of her: look ahead of it (or behind her
		// tail) for anything she cannot climb, so that she stops at a wall instead of walking her horse into it.
		if (Math.abs(speed) > 0.01 && blocked(world, ahead, step)) {
			speed = 0.0F;
			step = new Vec3d(0.0, vy, 0.0);
		}
		setVelocity(step);
		move(MovementType.SELF, step);
		if (horizontalCollision && Math.abs(speed) > 0.05F) {
			speed *= 0.4F;
		}
	}

	/**
	 * The reins: on (her speed up to the top), back (a stop, then backing slowly); let go, she slows to a stop. Faster
	 * than she can go here (off a road, say, or the gallop over), she sheds the rest gently.
	 */
	private static double pedal(double speed, int reins, double top) {
		if (speed > top) {
			return Math.max(top, speed - BRAKE * 0.5);
		}
		if (reins > 0) {
			return speed < 0 ? Math.min(0.0, speed + BRAKE) : Math.min(top, speed + ACCEL * (1.0 - 0.5 * speed / Math.max(top, 0.01)));
		}
		if (reins < 0) {
			return speed > 0 ? Math.max(0.0, speed - BRAKE) : Math.max(-Math.min(REVERSE_TOP, top), speed - ACCEL * 0.5);
		}
		speed *= 0.94;
		return Math.abs(speed) < 0.004 ? 0.0 : speed;
	}

	private boolean blocked(World world, Vec3d ahead, Vec3d step) {
		double reach = speed > 0 ? HORSE_AHEAD + 1.0 : DECK_BACK - 0.2;
		Vec3d c = getPos().add(ahead.multiply(reach)).add(step.x, 0.0, step.z);
		double lift = Math.max(0.0, step.y);
		double bottom = getY() + lift + getStepHeight() + 0.05;
		double top = getY() + lift + 1.8;
		return !world.isSpaceEmpty(this, new Box(c.x - 0.4, bottom, c.z - 0.4, c.x + 0.4, top, c.z + 0.4));
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

	// --- the driver: the whip and the disguise --------------------------------------------------------------

	/** The driver does something: ACTION_WHIP or ACTION_DISGUISE. */
	public void act(ServerPlayerEntity player, int action) {
		if (getControllingPassenger() != player) {
			return;
		}
		switch (action) {
			case ACTION_WHIP -> crackWhip(player);
			case ACTION_DISGUISE -> toggleDisguise(player);
			default -> {
			}
		}
	}

	/**
	 * A crack of the whip: the horse breaks into a gallop for a while (on the driver's client, which moves her; the
	 * server hears it and throws off the disguise, if she wears it).
	 */
	public void crackWhip(@Nullable PlayerEntity by) {
		if (whipCooldown > 0) {
			return;
		}
		whipCooldown = WHIP_COOLDOWN;
		galloping = GALLOP_TICKS;
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		dataTracker.set(WHIP, dataTracker.get(WHIP) + 1);
		Vec3d at = toWorld(HANDS.add(0.0, 0.8, 0.6));
		world.playSound(null, at.x, at.y, at.z, Carriage.WHIP, SoundCategory.NEUTRAL, 1.4F, 0.9F + random.nextFloat() * 0.2F);
		if (by != null) {
			by.swingHand(Hand.MAIN_HAND, true);
		}
		if (random.nextInt(3) == 0) {
			Vec3d horse = horseAt();
			world.playSound(null, horse.x, horse.y + 1.4, horse.z, SoundEvents.ENTITY_HORSE_ANGRY, SoundCategory.NEUTRAL, 1.0F,
					0.9F + random.nextFloat() * 0.2F);
		}
		if (isDisguised()) {
			throwDisguise(world, by);
		}
	}

	/**
	 * Off comes the disguise, all of it at once, as she drives away (clients throw its pieces, CarriageRenderer), and the
	 * bait spills into the road.
	 */
	private void throwDisguise(ServerWorld world, @Nullable PlayerEntity by) {
		dataTracker.set(DISGUISE, false);
		dataTracker.set(THROWN, dataTracker.get(THROWN) + 1);
		Vec3d mid = toWorld(new Vec3d(0.0, DECK_TOP + 1.0, -0.3));
		world.playSound(null, mid.x, mid.y, mid.z, Carriage.DISGUISE_OFF, SoundCategory.NEUTRAL, 1.2F, 1.0F);
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BLUE_WOOL.getDefaultState()), mid.x, mid.y,
				mid.z, 30, 0.8, 0.8, 1.2, 0.1);
		spillBait(toWorld(new Vec3d(0.0, 0.0, -1.0)).subtract(getPos()).multiply(0.15));
		if (by instanceof ServerPlayerEntity player) {
			ModCriteria.fire(player, "carriage_unmask");
		}
	}

	/** The disguise up or down, by the driver, standing (it takes a moment to put up); taken down, the bait comes back to them. */
	public void toggleDisguise(@Nullable PlayerEntity by) {
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		if (getSpeed() > STANDING) {
			if (by != null) {
				by.sendMessage(Text.translatable("hud.shootingstar.carriage.disguise_stop"), true);
			}
			return;
		}
		boolean up = !isDisguised();
		dataTracker.set(DISGUISE, up);
		Vec3d mid = toWorld(new Vec3d(0.0, DECK_TOP + 1.0, -0.3));
		world.playSound(null, mid.x, mid.y, mid.z, up ? Carriage.DISGUISE_ON : SoundEvents.BLOCK_WOOL_BREAK, SoundCategory.NEUTRAL,
				1.0F, 1.0F);
		if (up && by != null) {
			by.sendMessage(Text.translatable("hud.shootingstar.carriage.disguised"), true);
		}
		if (!up) {
			for (int i = 0; i < bait.size(); i++) {
				ItemStack stack = bait.removeStack(i);
				if (!stack.isEmpty()) {
					if (by != null) {
						by.giveItemStack(stack);
					} else {
						dropStack(stack);
					}
				}
			}
			publishBait();
		}
	}

	// --- the server --------------------------------------------------------------------------------------

	private void serverTick(ServerWorld world) {
		for (int i = 0; i < parts.length; i++) {
			if (parts[i] == null || parts[i].isRemoved()) {
				parts[i] = new CarriagePartEntity(world, this, i);
				world.spawnEntity(parts[i]);
			}
		}
		if (whipCooldown > 0) {
			whipCooldown--;
		}
		escapes();
		hooves(world);
		if (isDisguised() && hasBait() && age % 20 == 0 && villagerFood()) {
			lure(world);
		}
	}

	/** Its hooves on the ground as it goes: a walk, a trot, a gallop; and now and then a snort, standing. */
	private void hooves(ServerWorld world) {
		Vec3d horse = horseAt();
		double s = getSpeed();
		if (s < 0.01) {
			hoofDistance = 0.0;
			if (random.nextInt(600) == 0) {
				world.playSound(null, horse.x, horse.y + 1.4, horse.z, SoundEvents.ENTITY_HORSE_AMBIENT, SoundCategory.NEUTRAL, 0.6F,
						0.9F + random.nextFloat() * 0.2F);
			}
			return;
		}
		hoofDistance += s;
		double stride = s > CANTER ? 1.5 : s > TROT ? 0.9 : 0.75;
		if (hoofDistance < stride) {
			return;
		}
		hoofDistance -= stride;
		BlockState under = world.getBlockState(BlockPos.ofFloored(horse.x, horse.y - 0.2, horse.z));
		boolean wood = under.getSoundGroup() == BlockSoundGroup.WOOD;
		SoundEvent sound = wood ? SoundEvents.ENTITY_HORSE_STEP_WOOD : s > CANTER ? SoundEvents.ENTITY_HORSE_GALLOP : SoundEvents.ENTITY_HORSE_STEP;
		world.playSound(null, horse.x, horse.y, horse.z, sound, SoundCategory.NEUTRAL, s > CANTER ? 0.3F : 0.2F,
				0.9F + random.nextFloat() * 0.2F);
		if (s > CANTER && random.nextInt(12) == 0) {
			world.playSound(null, horse.x, horse.y + 1.4, horse.z, SoundEvents.ENTITY_HORSE_BREATHE, SoundCategory.NEUTRAL, 0.5F,
					1.0F);
		}
	}

	// --- the look of her, on clients ----------------------------------------------------------------------

	private void clientTick() {
		if (client != null) {
			client.tick(this);
		}
		// Her wheels turn as far as she goes, the small front ones faster; the horse's legs go as LivingEntity's do.
		double forward = getForwardSpeed();
		prevRearSpin = rearSpin;
		prevFrontSpin = frontSpin;
		rearSpin += (float) (forward / REAR_RADIUS);
		frontSpin += (float) (forward / FRONT_RADIUS);
		prevLimbSpeed = limbSpeed;
		prevLimbPos = limbPos;
		limbSpeed += (Math.min(1.0F, (float) getSpeed() * 4.0F) - limbSpeed) * 0.4F;
		limbPos += limbSpeed;
		// The door swings slowly on its hinges.
		prevDoor = door;
		float wantDoor = isDoorOpen() ? 1.0F : 0.0F;
		door += MathHelper.clamp(wantDoor - door, -0.12F, 0.12F);
		int snap = dataTracker.get(SNAP);
		if (snap != seenSnap) {
			snapAge = age > 2 ? 0 : snapAge;
			seenSnap = snap;
		} else {
			snapAge++;
		}
		// The disguise thrown off: where she was when it went, for its pieces to fall there.
		int thrown = dataTracker.get(THROWN);
		if (thrown != seenThrown) {
			if (age > 2) {
				thrownAge = 0;
				thrownAt = getPos();
				thrownYaw = getYaw();
			}
			seenThrown = thrown;
		} else {
			thrownAge++;
		}
		int whip = dataTracker.get(WHIP);
		if (whip != seenWhip) {
			whipAge = age > 2 ? 0 : whipAge;
			seenWhip = whip;
		} else {
			whipAge++;
		}
		if (whipCooldown > 0) {
			whipCooldown--;
		}
		// Dust off her wheels, at a trot and over.
		double s = getSpeed();
		if (s > TROT && isOnGround() && random.nextFloat() < s) {
			BlockState under = getWorld().getBlockState(getBlockPos().down());
			if (!under.isAir()) {
				for (double side : new double[] {-0.74, 0.74}) {
					Vec3d at = toWorld(new Vec3d(side, 0.05, REAR_AXLE - 0.3));
					getWorld().addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, under), at.x, at.y, at.z,
							motion.x * -0.5, 0.05, motion.z * -0.5);
				}
			}
		}
	}

	public float getSteer(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevSteer, steer);
	}

	public float getRearSpin(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevRearSpin, rearSpin);
	}

	public float getFrontSpin(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevFrontSpin, frontSpin);
	}

	/** The door's swing, 0 shut to 1 open; the trap springing flings it open and slams it again. */
	public float getDoorOpen(float tickDelta) {
		float d = MathHelper.lerp(tickDelta, prevDoor, door);
		float t = snapAge + tickDelta;
		float snap = t < 3.0F ? t / 3.0F : t < 9.0F ? 1.0F - (t - 3.0F) / 6.0F : 0.0F;
		return Math.max(d, snap * 0.8F);
	}

	/** Ticks since the disguise was thrown off (on clients), and where she was and which way she faced then. */
	public float getThrownAge(float tickDelta) {
		return thrownAge + tickDelta;
	}

	public Vec3d getThrownAt() {
		return thrownAt;
	}

	public float getThrownYaw() {
		return thrownYaw;
	}

	/** Ticks since the whip last cracked (on clients). */
	public float getWhipAge(float tickDelta) {
		return whipAge + tickDelta;
	}

	public boolean isGalloping() {
		return galloping > 0;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		dataTracker.set(DOOR, nbt.getBoolean("DoorOpen"));
		dataTracker.set(DISGUISE, nbt.getBoolean("Disguise"));
		savedPlaces.clear();
		NbtList places = nbt.getList("Places", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < places.size(); i++) {
			NbtCompound place = places.getCompound(i);
			if (place.containsUuid("Who")) {
				savedPlaces.put(place.getUuid("Who"), place.getInt("Place"));
			}
		}
		bait.clear();
		if (nbt.contains("Bait", NbtElement.LIST_TYPE)) {
			bait.readNbtList(nbt.getList("Bait", NbtElement.COMPOUND_TYPE), getRegistryManager());
		}
		baiter = nbt.containsUuid("Baiter") ? nbt.getUuid("Baiter") : null;
		publishBait();
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putBoolean("DoorOpen", isDoorOpen());
		nbt.putBoolean("Disguise", isDisguised());
		NbtList places = new NbtList();
		for (int i = 0; i < PLACES.length; i++) {
			if (seated[i] != null && seated[i].getVehicle() == this) {
				NbtCompound place = new NbtCompound();
				place.putUuid("Who", seated[i].getUuid());
				place.putInt("Place", i);
				places.add(place);
			}
		}
		nbt.put("Places", places);
		nbt.put("Bait", bait.toNbtList(getRegistryManager()));
		if (baiter != null) {
			nbt.putUuid("Baiter", baiter);
		}
	}
}
