package io.github.kortev.chitty.airship;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The airship's grapple's head, out in the world at its tines whenever the grapple is let down (the grapple itself is
 * drawn with her). Whatever the grapple has seized rides it, so that it swings along with the grapple as smoothly as a
 * passenger does.
 * <ul>
 * <li>Caught, a thing hangs by the back of its collar from the tines, limp, and kicks and jerks on the rope now and
 * then; a caught player cannot simply step off (PlayerEntity.shouldDismount): holding sneak, they struggle (kicking and
 * rattling the grapple, a bar filling as they go), and after ten seconds of it they wrench free and drop.</li>
 * <li>Someone who hangs on it by choice hangs from its ring by both hands, and lets go whenever they sneak.</li>
 * </ul>
 * Clients are told which (VOLUNTARY), and each kick (AGITATION), and draw them so (AirshipHangPoseMixin). Hanging empty,
 * someone on the ground can take hold of it (AirshipEntity.takeHoldOfGrapple). Never saved: she winds her grapple up
 * when she is unloaded.
 */
public class AirshipHookEntity extends Entity {
	/** Ticks of struggling (holding sneak) a player needs to get off the grapple. */
	public static final int STRUGGLE = 200;
	/** How far ahead of the tines a caught thing hangs (they hook it by the back of its collar). */
	static final double COLLAR = 0.22;
	private static final TrackedData<Integer> SHIP = DataTracker.registerData(AirshipHookEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Whether whoever is on it hangs on by choice (and so lets go when they like), rather than being caught. */
	private static final TrackedData<Boolean> VOLUNTARY = DataTracker.registerData(AirshipHookEntity.class,
			TrackedDataHandlerRegistry.BOOLEAN);
	/** Counts each kick of whatever is caught on it, for clients to draw. */
	private static final TrackedData<Integer> AGITATION = DataTracker.registerData(AirshipHookEntity.class,
			TrackedDataHandlerRegistry.INTEGER);
	private int struggle;
	/** The next tick a caught mob kicks. */
	private int nextFlail = 40;
	// On clients: the last kick seen, and until when it is drawn kicking.
	private int seenAgitation;
	private int kickingUntil;

	public AirshipHookEntity(EntityType<? extends AirshipHookEntity> type, World world) {
		super(type, world);
		noClip = true;
	}

	AirshipHookEntity(World world, AirshipEntity ship) {
		this(Airship.HOOK, world);
		dataTracker.set(SHIP, ship.getId());
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(SHIP, -1);
		builder.add(VOLUNTARY, false);
		builder.add(AGITATION, 0);
	}

	@Nullable
	public AirshipEntity getShip() {
		return getWorld().getEntityById(dataTracker.get(SHIP)) instanceof AirshipEntity ship && !ship.isRemoved() ? ship : null;
	}

	@Override
	public boolean hasNoGravity() {
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		AirshipEntity ship = getShip();
		if (!getWorld().isClient && (ship == null || ship.getHookEntity() != this)) {
			removeAllPassengers();
			discard();
			return;
		}
		if (ship != null) {
			// At the tines, wherever the grapple swings (on a client, where it is drawn there).
			Vec3d grip = ship.hookGrip();
			setPosition(grip.x, grip.y, grip.z);
			setVelocity(Vec3d.ZERO);
		}
		if (getWorld().isClient) {
			int agitation = dataTracker.get(AGITATION);
			if (agitation != seenAgitation) {
				seenAgitation = agitation;
				kickingUntil = age + 8;
			}
			return;
		}
		if (!hasPassengers() && isVoluntary()) {
			dataTracker.set(VOLUNTARY, false);
		}
		Entity load = getFirstPassenger();
		if (isVoluntary() || load == null) {
			return;
		}
		if (load instanceof ServerPlayerEntity player) {
			// Holding sneak, a caught player struggles, kicking; let go of it and they tire.
			if (player.isSneaking()) {
				struggle++;
				if (struggle % 8 == 0) {
					kick(ship, 0.05);
				}
				if (struggle % 4 == 0) {
					player.sendMessage(Text.translatable("hud.shootingstar.airship.struggle", bar(struggle)), true);
				}
				if (struggle >= STRUGGLE) {
					player.stopRiding();
					player.fallDistance = 0.0F;
					player.sendMessage(Text.translatable("hud.shootingstar.airship.free"), true);
					getWorld().playSound(null, getX(), getY(), getZ(), Airship.GRAB, SoundCategory.NEUTRAL, 1.0F, 0.6F);
				}
			} else {
				struggle = Math.max(0, struggle - 2);
			}
		} else if (load instanceof LivingEntity && age >= nextFlail) {
			// A caught mob kicks and jerks on the rope now and then.
			nextFlail = age + 30 + random.nextInt(60);
			kick(ship, 0.08);
		}
	}

	/** A kick or a jerk of whatever is caught: it rattles the grapple and jolts it on its rope. */
	private void kick(@Nullable AirshipEntity ship, double strength) {
		dataTracker.set(AGITATION, dataTracker.get(AGITATION) + 1);
		if (ship != null) {
			ship.jolt(strength);
		}
		getWorld().playSound(null, getX(), getY(), getZ(), Airship.GRAB, SoundCategory.NEUTRAL, 0.35F,
				1.5F + random.nextFloat() * 0.4F);
	}

	/** How far a struggle has got, as a bar: filled cells for the struggle so far, open ones for the rest. */
	private static String bar(int struggle) {
		int filled = Math.min(10, struggle * 10 / STRUGGLE);
		return "\u25AE".repeat(filled) + "\u25AF".repeat(10 - filled);
	}

	/** On a client: whether whatever is caught is kicking just now (for its pose). */
	public boolean isKicking() {
		return age < kickingUntil;
	}

	/**
	 * How far below the tines a load's feet hang: someone hanging on by choice holds the ring above the tines with
	 * their arms up; a caught thing hangs by the back of its collar.
	 */
	public static double hangBelow(Entity load, boolean voluntary) {
		return voluntary ? Math.max(0.0, load.getHeight() * 1.22 - AirshipEntity.HOOK_GRIP) : load.getHeight() * 0.85;
	}

	/** Whether a player riding this may get off: they hang on by choice, or have struggled long enough. */
	public boolean freed(PlayerEntity player) {
		return isVoluntary() || struggle >= STRUGGLE;
	}

	/** Whether whoever is on it hangs on by choice (on either side). */
	public boolean isVoluntary() {
		return dataTracker.get(VOLUNTARY);
	}

	void setVoluntary(boolean voluntary) {
		dataTracker.set(VOLUNTARY, voluntary);
		struggle = 0;
	}

	/** For tests: how long the caught player has struggled. */
	public int getStruggle() {
		return struggle;
	}

	/**
	 * Whoever hangs on by choice hangs from the ring by their hands; a caught thing hangs by the back of its collar from
	 * the tines, its body a little ahead of them.
	 */
	@Override
	protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
		if (!hasPassenger(passenger)) {
			return;
		}
		boolean voluntary = isVoluntary();
		Vec3d at = new Vec3d(getX(), getY() - hangBelow(passenger, voluntary), getZ());
		if (!voluntary) {
			float yaw = passenger instanceof LivingEntity living ? living.bodyYaw : passenger.getYaw();
			float yawRad = yaw * MathHelper.RADIANS_PER_DEGREE;
			at = at.add(-MathHelper.sin(yawRad) * COLLAR, 0.0, MathHelper.cos(yawRad) * COLLAR);
		}
		positionUpdater.accept(passenger, at.x, at.y, at.z);
	}

	/**
	 * Whatever gets off the grapple, set down or struggling free, gets off where it hangs: not on top of the holder (the
	 * game's way), which is up at the tines, over its head.
	 */
	@Override
	public Vec3d updatePassengerForDismount(LivingEntity passenger) {
		return passenger.getPos();
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return !hasPassengers();
	}

	/** Hanging empty, it can be taken hold of; not while someone holds it, nor with something on it. */
	@Override
	public boolean canHit() {
		AirshipEntity ship = getShip();
		return !hasPassengers() && ship != null && ship.getHookState() != AirshipEntity.Hook.HELD;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		return false;
	}

	/** Someone on the ground takes hold of it as it hangs empty. */
	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		AirshipEntity ship = getShip();
		if (ship == null || hasPassengers() || player.hasVehicle()) {
			return ActionResult.PASS;
		}
		if (!getWorld().isClient) {
			return ship.takeHoldOfGrapple(player) ? ActionResult.CONSUME : ActionResult.PASS;
		}
		return ActionResult.SUCCESS;
	}

	/** On a client it goes where the grapple is drawn, not where the server last put it. */
	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
		if (getShip() == null) {
			super.updateTrackedPositionAndAngles(x, y, z, yaw, pitch, interpolationSteps);
		}
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
