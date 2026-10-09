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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The airship's grapple's head, out in the world at its tines whenever the grapple is let down (the grapple itself is
 * drawn with her). Whatever the grapple has seized rides it, so that it swings along with the grapple as smoothly as a
 * passenger does. A mob stays caught; a caught player cannot simply step off (PlayerEntity.shouldDismount): holding
 * sneak, they struggle, and after ten seconds of it they wrench free and drop. Someone who hangs on it by choice lets go
 * whenever they sneak. Hanging empty, someone on the ground can take hold of it (AirshipEntity.takeHoldOfGrapple). Never
 * saved: she winds her grapple up when she is unloaded.
 */
public class AirshipHookEntity extends Entity {
	/** Ticks of struggling (holding sneak) a player needs to get off the grapple. */
	public static final int STRUGGLE = 200;
	private static final TrackedData<Integer> SHIP = DataTracker.registerData(AirshipHookEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private int struggle;
	/** Whether whoever is on it hangs on by choice (and so lets go when they like), rather than being caught. */
	private boolean voluntary;

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
		if (!hasPassengers()) {
			voluntary = false;
		}
		if (!getWorld().isClient && !voluntary && getFirstPassenger() instanceof ServerPlayerEntity player) {
			// Holding sneak, a caught player struggles; let go of it and they tire.
			if (player.isSneaking()) {
				struggle++;
				if (struggle % 20 == 0) {
					player.sendMessage(Text.translatable("hud.shootingstar.airship.struggle", (STRUGGLE - struggle) / 20), true);
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
		}
	}

	/** Whether a player riding this may get off: they hang on by choice, or have struggled long enough. */
	public boolean freed(PlayerEntity player) {
		return voluntary || struggle >= STRUGGLE;
	}

	/** Whether whoever is on it hangs on by choice. */
	public boolean isVoluntary() {
		return voluntary;
	}

	void setVoluntary(boolean voluntary) {
		this.voluntary = voluntary;
		struggle = 0;
	}

	/** For tests: how long the caught player has struggled. */
	public int getStruggle() {
		return struggle;
	}

	/** Whatever is caught hangs by its shoulders from the tines. */
	@Override
	protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
		if (!hasPassenger(passenger)) {
			return;
		}
		positionUpdater.accept(passenger, getX(), getY() - passenger.getHeight() * 0.8, getZ());
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
