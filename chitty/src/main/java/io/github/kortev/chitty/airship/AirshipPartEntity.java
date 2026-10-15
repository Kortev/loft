package io.github.kortev.chitty.airship;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * One of the hitboxes along the airship. Her own box is only the middle of her gondola: its bow and stern, the
 * envelope (in six boxes along it, following its taper) and the tail get these, which keep to her as she flies. They
 * are as solid as she is (you can stand on her envelope), using one is using her there, and hitting one is hitting
 * her, unless you are aboard. They are never saved: she puts out new ones when she is loaded.
 */
public class AirshipPartEntity extends Entity {
	static final int COUNT = 2 + AirshipEntity.ENVELOPE.length;
	private static final TrackedData<Integer> SHIP = DataTracker.registerData(AirshipPartEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Byte> PART = DataTracker.registerData(AirshipPartEntity.class, TrackedDataHandlerRegistry.BYTE);

	public AirshipPartEntity(EntityType<? extends AirshipPartEntity> type, World world) {
		super(type, world);
		noClip = true;
	}

	AirshipPartEntity(World world, AirshipEntity ship, int part) {
		this(Airship.PART, world);
		dataTracker.set(SHIP, ship.getId());
		dataTracker.set(PART, (byte) part);
		calculateDimensions();
		follow(ship);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(SHIP, -1);
		builder.add(PART, (byte) 0);
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (PART.equals(data)) {
			calculateDimensions();
		}
	}

	/** Which box this is: 0 and 1 the gondola's bow and stern, then the envelope's and the tail's, nose to tail. */
	public int getPart() {
		return MathHelper.clamp(dataTracker.get(PART), 0, COUNT - 1);
	}

	/** Where the bottom middle of the box is on her (her own axes) and its width and height. */
	private static double[] shape(int part) {
		return switch (part) {
			case 0 -> new double[] {1.35, 0.0, 1.9, 1.5};
			case 1 -> new double[] {-1.35, 0.0, 1.9, 1.5};
			default -> AirshipEntity.ENVELOPE[part - 2];
		};
	}

	@Override
	public EntityDimensions getDimensions(EntityPose pose) {
		double[] s = shape(getPart());
		return EntityDimensions.changing((float) s[2], (float) s[3]);
	}

	@Nullable
	public AirshipEntity getShip() {
		return getWorld().getEntityById(dataTracker.get(SHIP)) instanceof AirshipEntity ship && !ship.isRemoved() ? ship : null;
	}

	@Override
	public void tick() {
		super.tick();
		AirshipEntity ship = getShip();
		if (ship == null) {
			if (!getWorld().isClient) {
				discard();
			}
			return;
		}
		follow(ship);
	}

	private void follow(AirshipEntity ship) {
		double[] s = shape(getPart());
		Vec3d at = ship.getPos().add(new Vec3d(0.0, s[1], s[0]).rotateY(-ship.getYaw() * MathHelper.RADIANS_PER_DEGREE));
		setPosition(at.x, at.y, at.z);
		setVelocity(Vec3d.ZERO);
	}

	@Override
	public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
		AirshipEntity ship = getShip();
		return ship == null ? ActionResult.PASS : ship.interactAt(player, hitPos.add(getPos()).subtract(ship.getPos()), hand);
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		AirshipEntity ship = getShip();
		return ship == null ? ActionResult.PASS : ship.interact(player, hand);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		AirshipEntity ship = getShip();
		if (ship == null || source.getAttacker() != null && source.getAttacker().getRootVehicle() == ship) {
			return false;
		}
		// The two parts of the gondola are wood; the rest, the envelope and her tail, canvas.
		return ship.hurt(source, amount, getPart() >= 2);
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	@Override
	public boolean isCollidable() {
		return true;
	}

	@Override
	public boolean collidesWith(Entity other) {
		return false;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return false;
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(Airship.ITEM);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
