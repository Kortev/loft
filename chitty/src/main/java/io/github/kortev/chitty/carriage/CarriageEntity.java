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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.CamelEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
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
import net.minecraft.sound.SoundCategory;
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
 * The Child Catcher's carriage: a black iron cage on a high dray, drawn by a horse in its shafts, the driver up on the
 * box at the front.
 *
 * <p>It brings no horse of its own: lead one of yours up to it and use it, and the horse is hitched into the shafts
 * (any horse, donkey or mule, grown). Hitched, the horse is the game's own horse still, drawn as it is with its
 * harness on (CarriageHarnessFeature); it does nothing of its own accord (CarriageHorseMixin) but walk, trot and gallop
 * in the shafts as the carriage goes, its legs going as fast as she does; it pulls her as fast as it can run (its speed,
 * the same one a rider gets), faster on a road. Sneak and use it to unhitch it (back onto your lead, if you have one).
 * Killing it stops her where she is.
 *
 * <p>Two sit up on the box: the driver on the right, with the reins (on, back, left, right) and the whip (jump: a crack,
 * and the horse breaks into a gallop for a while), and one beside. The cage behind has standing room for four, and its
 * door in the back, which anyone outside may open or shut by using it. With it open, whatever you lead up to it on your
 * lead goes in (anything that fits: about the size of a player), and whatever stands at the door you can shove in by
 * hitting it; sneak and use the open door to climb in yourself. With it shut, nobody inside gets out: not by sneaking,
 * nor by hitting anyone outside through the bars. Opened, a mob inside makes a run for it; a player can get out.
 *
 * <p>The cage can be dressed as a sweet cart (the driver's disguise key, standing): painted boards over its bars and
 * lollipops on its roof, all free today, and with its door open the village's children come for the sweets and climb
 * in. Cracking the whip throws the lot off as she drives away, as in the film.
 *
 * <p>Like Chitty she is moved by whoever drives her (their client) and by the server when nobody does. Positions are in
 * blocks; local offsets are at yaw 0, x to her left, z forward.
 */
public class CarriageEntity extends Entity {
	private static final TrackedData<Integer> WOBBLE_TICKS = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> WOBBLE_SIDE = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Float> WOBBLE_STRENGTH = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** The hitched horse's entity id, or -1. */
	private static final TrackedData<Integer> HORSE = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> DOOR = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> DISGUISE = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** Counts the times the disguise has been thrown off, for clients to throw it. */
	private static final TrackedData<Integer> THROWN = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Counts the cracks of the whip, for clients to draw. */
	private static final TrackedData<Integer> WHIP = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Which place each passenger, in the order they are listed, is in: three bits each. */
	private static final TrackedData<Integer> SEATING = DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.INTEGER);

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
	public static final Vec3d HINGE = new Vec3d(-0.392, DECK_TOP, -1.6175);
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

	// --- how she goes ---------------------------------------------------------------------------------

	/** Her top speed (blocks a tick) for each point of the horse's speed: a horse pulling her goes a little over half as fast as a ridden one. */
	static final double PULL = 1.35;
	/** On a road, a little faster. */
	static final double ROAD = 1.2;
	/** After a crack of the whip, for a while, faster still: a gallop. */
	static final double GALLOP = 1.4;
	static final int GALLOP_TICKS = 70;
	static final int WHIP_COOLDOWN = 16;
	static final double ACCEL = 0.006;
	static final double BRAKE = 0.025;
	static final double REVERSE_TOP = 0.06;
	/** In water up to her axles she can only creep. */
	static final double WADE_TOP = 0.08;
	/** Degrees a tick she turns at most, once she is moving. */
	static final float TURN = 3.5F;
	static final double GRAVITY = 0.08;
	/** Slower than this (blocks a tick) she counts as standing, to dress or undress her. */
	static final double STANDING = 0.03;
	/** How far the gaits change, by speed (blocks a tick): a walk, a trot, then a gallop. */
	public static final double TROT = 0.14;
	public static final double CANTER = 0.3;
	/** How far you can be from what you are leading (a lead's length). */
	static final double LEAD_REACH = 10.0;
	/** What fits in the cage. */
	static final float CAGE_FITS_WIDTH = 1.0F;
	static final float CAGE_FITS_HEIGHT = 2.1F;
	/** How near the door (blocks, out from it along the ground) something must be to be shoved in, or to climb in. */
	static final double DOOR_REACH = 1.4;

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
	// The fore-carriage's turn (-1 to 1, left positive), from how she turns, on every side.
	private float steer;
	private float prevSteer;
	// Where her horse stands in the shafts and which way it faces, now and a tick ago, and how far above her it stands
	// (on the ground ahead of her).
	@Nullable
	private Vec3d horseAt;
	@Nullable
	private Vec3d prevHorseAt;
	private float horseYaw;
	private float prevHorseYaw;
	private double horseLift;

	// The server's view of her horse, her passengers and her cage.
	@Nullable
	private UUID horseUuid;
	@Nullable
	private AbstractHorseEntity horse;
	private final Entity[] seated = new Entity[PLACES.length];
	private int wantedPlace = -1;
	/** The places her passengers had when she was saved (by who they are), for them to have again when they get back in. */
	private final Map<UUID, Integer> savedPlaces = new HashMap<>();
	/** With the door open: how long each mob in the cage waits before it makes a run for it. */
	private final Map<Entity, Integer> escaping = new HashMap<>();
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

	// Looks, on clients: her wheels' turn (rear and front, radians), the door's swing (0 shut to 1 open), the disguise
	// thrown off (ticks since, where and which way she was then), the whip's crack (ticks since).
	private float rearSpin;
	private float prevRearSpin;
	private float frontSpin;
	private float prevFrontSpin;
	private float door;
	private float prevDoor;
	private int seenThrown;
	private int thrownAge = Integer.MAX_VALUE / 2;
	private Vec3d thrownAt = Vec3d.ZERO;
	private float thrownYaw;
	private int seenWhip;
	private int whipAge = Integer.MAX_VALUE / 2;

	public CarriageEntity(EntityType<? extends CarriageEntity> type, World world) {
		super(type, world);
		this.intersectionChecked = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(WOBBLE_TICKS, 0);
		builder.add(WOBBLE_SIDE, 1);
		builder.add(WOBBLE_STRENGTH, 0.0F);
		builder.add(HORSE, -1);
		builder.add(DOOR, false);
		builder.add(DISGUISE, false);
		builder.add(THROWN, 0);
		builder.add(WHIP, 0);
		builder.add(SEATING, 0);
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
				&& !(other instanceof CarriagePartEntity part && part.getCarriage() == this) && hitchedTo(other) != this;
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
			}
			discard();
		}
		return true;
	}

	/** Broken up (not merely unloaded), she lets her horse go. */
	@Override
	public void remove(RemovalReason reason) {
		if (reason.shouldDestroy() && !getWorld().isClient) {
			unhitch(null);
		}
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

	// --- her horse ---------------------------------------------------------------------------------------

	/** Her horse, while one is hitched (and there: loaded, alive). */
	@Nullable
	public AbstractHorseEntity getHorse() {
		if (getWorld().isClient) {
			return getWorld().getEntityById(dataTracker.get(HORSE)) instanceof AbstractHorseEntity h && h.isAlive() ? h : null;
		}
		return horse != null && horse.isAlive() && !horse.isRemoved() ? horse : null;
	}

	/** Whether she has a horse hitched, there or not (it may be in a part of the world not loaded). */
	public boolean hasHorse() {
		return getWorld().isClient ? dataTracker.get(HORSE) >= 0 : horseUuid != null;
	}

	/** The carriage a horse is hitched to, if it is. */
	@Nullable
	public static CarriageEntity hitchedTo(Entity entity) {
		if (entity instanceof CarriageHitch hitch && hitch.chitty$getCarriage() instanceof CarriageEntity carriage && !carriage.isRemoved()
				&& carriage.dataTracker.get(HORSE) == entity.getId()) {
			return carriage;
		}
		return null;
	}

	/** Whether this can be hitched into her shafts: a horse, donkey or mule (not a llama, nor a camel), grown. */
	public static boolean canPull(Entity entity) {
		return entity instanceof AbstractHorseEntity horse && !(horse instanceof LlamaEntity) && !(horse instanceof CamelEntity)
				&& !horse.isBaby() && horse.isAlive() && hitchedTo(horse) == null;
	}

	/** Hitches a horse into her shafts: it comes off its lead (which goes back to whoever led it) and stands there. */
	public boolean hitch(AbstractHorseEntity horse, @Nullable PlayerEntity by) {
		if (getWorld().isClient || hasHorse() || !canPull(horse)) {
			return false;
		}
		if (horse.isLeashed()) {
			horse.detachLeash(true, false);
			if (by != null && !by.getAbilities().creativeMode) {
				by.giveItemStack(new ItemStack(Items.LEAD));
			}
		}
		horse.removeAllPassengers();
		horse.setEatingGrass(false);
		this.horse = horse;
		this.horseUuid = horse.getUuid();
		dataTracker.set(HORSE, horse.getId());
		((CarriageHitch) horse).chitty$setCarriage(this);
		horseAt = null;
		placeHorse();
		holdHorse(horse);
		getWorld().playSound(null, horse.getX(), horse.getY(), horse.getZ(), SoundEvents.ENTITY_HORSE_SADDLE, SoundCategory.NEUTRAL,
				0.8F, 1.0F);
		if (by instanceof ServerPlayerEntity player) {
			ModCriteria.fire(player, "carriage_hitch");
			player.sendMessage(Text.translatable("hud.shootingstar.carriage.hitched", horse.getDisplayName()), true);
		}
		return true;
	}

	/**
	 * Lets her horse out of the shafts, where it stands: onto the lead of whoever unhitched it, if they have one (in
	 * creative they always do).
	 */
	public void unhitch(@Nullable PlayerEntity by) {
		AbstractHorseEntity was = getHorse();
		horse = null;
		horseUuid = null;
		dataTracker.set(HORSE, -1);
		speed = 0.0F;
		if (was == null) {
			return;
		}
		((CarriageHitch) was).chitty$setCarriage(null);
		was.setVelocity(Vec3d.ZERO);
		if (by != null && !getWorld().isClient) {
			if (by.getAbilities().creativeMode || takeLead(by)) {
				was.attachLeash(by, true);
			}
			getWorld().playSound(null, was.getX(), was.getY(), was.getZ(), SoundEvents.ENTITY_HORSE_ARMOR, SoundCategory.NEUTRAL,
					0.6F, 1.2F);
		}
	}

	private static boolean takeLead(PlayerEntity player) {
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (stack.isOf(Items.LEAD)) {
				stack.decrement(1);
				return true;
			}
		}
		return false;
	}

	/** On the server: finds her horse again (loaded later than she was, say), and lets it go if it has died. */
	private void keepHorse(ServerWorld world) {
		if (horseUuid == null) {
			if (dataTracker.get(HORSE) != -1) {
				dataTracker.set(HORSE, -1);
			}
			return;
		}
		if (horse == null || horse.isRemoved()) {
			RemovalReason gone = horse == null ? null : horse.getRemovalReason();
			horse = null;
			if (gone != null && gone.shouldDestroy()) {
				unhitch(null);
				return;
			}
			if (world.getEntity(horseUuid) instanceof AbstractHorseEntity found) {
				horse = found;
			}
		}
		if (horse != null && !horse.isAlive()) {
			unhitch(null);
			return;
		}
		dataTracker.set(HORSE, horse == null ? -1 : horse.getId());
	}

	/** Tells her horse whose it is (on every side: CarriageHorseMixin asks). */
	private void claimHorse() {
		AbstractHorseEntity h = getHorse();
		if (h != null) {
			((CarriageHitch) h).chitty$setCarriage(this);
		}
	}

	/**
	 * Where her horse stands in the shafts this tick, and which way it faces: ahead of the fore-carriage, turned with it
	 * about its turntable, on the ground there (a step up or down from hers at most, and easing onto it).
	 */
	private void placeHorse() {
		Vec3d local = new Vec3d(0.0, 0.0, HORSE_AHEAD - FRONT_AXLE).rotateY(steer * STEER_MAX * MathHelper.RADIANS_PER_DEGREE)
				.add(0.0, 0.0, FRONT_AXLE);
		Vec3d at = getPos().add(local.rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE));
		double lift = groundAt(at) - getY();
		horseLift = horseAt == null ? lift : horseLift + (lift - horseLift) * 0.4;
		Vec3d now = new Vec3d(at.x, getY() + horseLift, at.z);
		float yaw = getYaw() - steer * STEER_MAX;
		if (horseAt == null) {
			prevHorseAt = now;
			prevHorseYaw = yaw;
		} else {
			prevHorseAt = horseAt;
			prevHorseYaw = horseYaw;
		}
		horseAt = now;
		horseYaw = yaw;
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

	/**
	 * Puts her horse where it stands in the shafts (now, and a tick ago, for it to be drawn moving smoothly between),
	 * facing the way it pulls. Called as she moves and as it ticks (CarriageHorseMixin), whichever comes first, on every
	 * side: a client puts it there itself rather than where the server last said it was.
	 */
	public void holdHorse(AbstractHorseEntity h) {
		if (horseAt == null || prevHorseAt == null) {
			placeHorse();
		}
		h.setPosition(horseAt.x, horseAt.y, horseAt.z);
		h.prevX = prevHorseAt.x;
		h.prevY = prevHorseAt.y;
		h.prevZ = prevHorseAt.z;
		h.lastRenderX = prevHorseAt.x;
		h.lastRenderY = prevHorseAt.y;
		h.lastRenderZ = prevHorseAt.z;
		h.setYaw(horseYaw);
		h.prevYaw = prevHorseYaw;
		h.setBodyYaw(horseYaw);
		h.prevBodyYaw = prevHorseYaw;
		h.setHeadYaw(horseYaw);
		h.prevHeadYaw = prevHorseYaw;
		h.setVelocity(Vec3d.ZERO);
		h.fallDistance = 0.0F;
		h.setOnGround(true);
	}

	/** How far her horse's legs go this tick: as far as she went. */
	public float getStride() {
		return (float) getSpeed();
	}

	// --- passengers ------------------------------------------------------------------------------------

	/**
	 * Using her: leading something, it is hitched (a horse, if she has none) or goes into the cage (if the door is open);
	 * at the door, it opens or shuts (or, sneaking with it open, you climb in); anywhere else, you get up on the box, in
	 * the free place nearest where you clicked.
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
				if (player.shouldCancelInteraction() && isDoorOpen()) {
					if (!putInCage(player)) {
						player.sendMessage(Text.translatable("hud.shootingstar.carriage.full"), true);
					}
				} else {
					setDoor(!isDoorOpen(), player);
				}
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

	/** Whether a point on her (local) is at her door: the back of the cage, about the middle. */
	private static boolean atDoor(Vec3d local) {
		return local.z < CAGE_BACK + 0.45 && Math.abs(local.x) < DOOR_HALF_WIDTH + 0.15 && local.y > DECK_TOP - 0.3;
	}

	/** Whatever a player has on their lead near her. */
	private List<MobEntity> ledBy(PlayerEntity player) {
		return getWorld().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(LEAD_REACH),
				mob -> mob.getLeashHolder() == player);
	}

	/**
	 * What a player leads up to her: a horse (the first, if she has none) into her shafts; anything else into the cage,
	 * if its door is open and it fits, each coming off its lead (back into their hand).
	 */
	private void lead(PlayerEntity player, List<MobEntity> led) {
		boolean hitched = false;
		boolean caged = false;
		boolean refused = false;
		for (MobEntity mob : led) {
			if (!hitched && !hasHorse() && canPull(mob)) {
				hitched = hitch((AbstractHorseEntity) mob, player);
				if (hitched) {
					continue;
				}
			}
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
		if (refused && !hitched && !caged) {
			String why = !isDoorOpen() ? "door_shut" : hasHorse() && led.stream().allMatch(CarriageEntity::canPull) ? "has_horse"
					: "wont_fit";
			player.sendMessage(Text.translatable("hud.shootingstar.carriage." + why), true);
		}
	}

	/** Whether something could be put in her cage: alive, about a player's size at most, not riding or ridden, and not her horse. */
	public boolean fitsInCage(Entity entity) {
		return entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()
				&& living.getWidth() <= CAGE_FITS_WIDTH && living.getHeight() <= CAGE_FITS_HEIGHT && !living.hasVehicle()
				&& !living.hasPassengers() && hitchedTo(living) == null;
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
		if (getWorld() instanceof ServerWorld world) {
			keepHorse(world);
		}
		claimHorse();
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
		// The fore-carriage turns as she turns (on every side, from how she has turned), and her horse with it.
		prevSteer = steer;
		float turned = MathHelper.wrapDegrees(prevYaw - getYaw());
		float want = getSpeed() > 0.005 ? MathHelper.clamp(turned / TURN, -1.0F, 1.0F) : steer;
		steer += (want - steer) * 0.3F;
		AbstractHorseEntity h = getHorse();
		if (h != null) {
			placeHorse();
			holdHorse(h);
		} else {
			horseAt = null;
		}
		if (getWorld() instanceof ServerWorld world) {
			serverTick(world, h);
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

	/** Her top speed, for her horse, on the ground she is on (blocks a tick). */
	public double topSpeed(AbstractHorseEntity h) {
		double top = h.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) * PULL;
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
		AbstractHorseEntity h = getHorse();
		boolean driven = h != null && getControllingPassenger() != null;
		if (!driven) {
			in = ChittyControls.NONE;
		}
		if (galloping > 0) {
			galloping--;
		}
		Vec3d v = getVelocity();
		boolean ground = isOnGround();
		double depth = getFluidHeight(FluidTags.WATER);
		double top = h == null ? 0.0 : topSpeed(h);
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
			rate = (float) (in.turn() * TURN * MathHelper.clamp(s / 0.06, 0.0, 1.0) * Math.signum(speed));
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
		if (hasHorse()) {
			galloping = GALLOP_TICKS;
		}
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		dataTracker.set(WHIP, dataTracker.get(WHIP) + 1);
		Vec3d at = toWorld(HANDS.add(0.0, 0.8, 0.6));
		world.playSound(null, at.x, at.y, at.z, Carriage.WHIP, SoundCategory.NEUTRAL, 1.4F, 0.9F + random.nextFloat() * 0.2F);
		if (by != null) {
			by.swingHand(Hand.MAIN_HAND, true);
		}
		AbstractHorseEntity h = getHorse();
		if (h != null && random.nextInt(3) == 0) {
			h.playAngrySound();
		}
		if (isDisguised()) {
			throwDisguise(world, by);
		}
	}

	/** Off comes the disguise, all of it at once, as she drives away (clients throw its pieces, CarriageRenderer). */
	private void throwDisguise(ServerWorld world, @Nullable PlayerEntity by) {
		dataTracker.set(DISGUISE, false);
		dataTracker.set(THROWN, dataTracker.get(THROWN) + 1);
		Vec3d mid = toWorld(new Vec3d(0.0, DECK_TOP + 1.0, -0.3));
		world.playSound(null, mid.x, mid.y, mid.z, Carriage.DISGUISE_OFF, SoundCategory.NEUTRAL, 1.2F, 1.0F);
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BIRCH_PLANKS.getDefaultState()), mid.x, mid.y,
				mid.z, 30, 0.8, 0.8, 1.2, 0.1);
		if (by instanceof ServerPlayerEntity player) {
			ModCriteria.fire(player, "carriage_unmask");
		}
	}

	/** The disguise up or down, by the driver, standing (it takes a moment to put up). */
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
		world.playSound(null, mid.x, mid.y, mid.z, up ? Carriage.DISGUISE_ON : SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.NEUTRAL,
				1.0F, 1.0F);
	}

	// --- the server --------------------------------------------------------------------------------------

	private void serverTick(ServerWorld world, @Nullable AbstractHorseEntity h) {
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
		if (h != null) {
			// The horse does nothing of its own accord in the shafts: it does not graze.
			if (h.isEatingGrass()) {
				h.setEatingGrass(false);
			}
			hooves(world, h);
		}
		if (isDisguised() && isDoorOpen() && age % 20 == 0) {
			lure(world);
		}
	}

	/** Its hooves on the ground as it goes: a walk, a trot, a gallop (it is not moving itself, so the game makes none). */
	private void hooves(ServerWorld world, AbstractHorseEntity h) {
		double s = getSpeed();
		if (s < 0.01) {
			hoofDistance = 0.0;
			return;
		}
		hoofDistance += s;
		double stride = s > CANTER ? 1.5 : s > TROT ? 0.9 : 0.75;
		if (hoofDistance < stride) {
			return;
		}
		hoofDistance -= stride;
		BlockState under = world.getBlockState(h.getBlockPos().down());
		boolean wood = under.getSoundGroup() == net.minecraft.sound.BlockSoundGroup.WOOD;
		var sound = wood ? SoundEvents.ENTITY_HORSE_STEP_WOOD : s > CANTER ? SoundEvents.ENTITY_HORSE_GALLOP : SoundEvents.ENTITY_HORSE_STEP;
		world.playSound(null, h.getX(), h.getY(), h.getZ(), sound, SoundCategory.NEUTRAL, s > CANTER ? 0.3F : 0.2F,
				0.9F + random.nextFloat() * 0.2F);
	}

	/**
	 * Dressed as a sweet cart with its door open, she draws the village's children: they come running for the sweets,
	 * and climb in.
	 */
	private void lure(ServerWorld world) {
		Vec3d out = toWorld(DOOR_OUT);
		for (VillagerEntity child : world.getEntitiesByClass(VillagerEntity.class, getBoundingBox().expand(16.0, 4.0, 16.0),
				v -> v.isBaby() && v.isAlive() && !v.hasVehicle())) {
			if (nearDoor(child)) {
				putInCage(child);
			} else {
				child.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(out, 0.6F, 0));
			}
		}
	}

	// --- the look of her, on clients ----------------------------------------------------------------------

	private void clientTick() {
		if (client != null) {
			client.tick(this);
		}
		// Her wheels turn as far as she goes, the small front ones faster.
		double forward = getForwardSpeed();
		prevRearSpin = rearSpin;
		prevFrontSpin = frontSpin;
		rearSpin += (float) (forward / REAR_RADIUS);
		frontSpin += (float) (forward / FRONT_RADIUS);
		// The door swings slowly on its hinges.
		prevDoor = door;
		float wantDoor = isDoorOpen() ? 1.0F : 0.0F;
		door += MathHelper.clamp(wantDoor - door, -0.12F, 0.12F);
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
			if (!(getControllingPassenger() instanceof PlayerEntity driver && driver.isMainPlayer())) {
				galloping = GALLOP_TICKS;
			}
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

	public float getDoorOpen(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevDoor, door);
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
		horseUuid = nbt.containsUuid("Horse") ? nbt.getUuid("Horse") : null;
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
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (horseUuid != null) {
			nbt.putUuid("Horse", horseUuid);
		}
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
	}
}
