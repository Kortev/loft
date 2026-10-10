package io.github.kortev.chitty.carriage;

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
 * One of the hitboxes along the carriage, as Chitty has (ChittyPartEntity): her own box covers only her middle, so her
 * box at the front (the driver's) and the back of her cage (its door) get invisible ones of their own, which keep to
 * her as she moves. They are as solid as she is, using one is using her there and hitting one is hitting her, unless
 * you are riding in her. Never saved: she puts out new ones when she is loaded.
 */
public class CarriagePartEntity extends Entity {
	public static final int FRONT = 0;
	public static final int BACK = 1;
	static final int COUNT = 2;
	/** Where the bottom middle of each box is on her, in her own axes, and its size. */
	private static final Vec3d[] OFFSETS = {new Vec3d(0.0, 0.0, 1.45), new Vec3d(0.0, 0.0, -1.0)};
	private static final EntityDimensions[] SIZES = {EntityDimensions.changing(1.0F, 2.3F), EntityDimensions.changing(1.6F, 3.4F)};
	private static final TrackedData<Integer> CARRIAGE = DataTracker.registerData(CarriagePartEntity.class,
			TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Byte> PART = DataTracker.registerData(CarriagePartEntity.class, TrackedDataHandlerRegistry.BYTE);

	public CarriagePartEntity(EntityType<? extends CarriagePartEntity> type, World world) {
		super(type, world);
		noClip = true;
	}

	CarriagePartEntity(World world, CarriageEntity carriage, int part) {
		this(Carriage.PART, world);
		dataTracker.set(CARRIAGE, carriage.getId());
		dataTracker.set(PART, (byte) part);
		calculateDimensions();
		follow(carriage);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(CARRIAGE, -1);
		builder.add(PART, (byte) FRONT);
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (PART.equals(data)) {
			calculateDimensions();
		}
	}

	/** Which of her boxes this is: FRONT or BACK. */
	public int getPart() {
		return MathHelper.clamp(dataTracker.get(PART), 0, COUNT - 1);
	}

	@Override
	public EntityDimensions getDimensions(EntityPose pose) {
		return SIZES[getPart()];
	}

	/** The carriage this is part of, while she is there. */
	@Nullable
	public CarriageEntity getCarriage() {
		return getWorld().getEntityById(dataTracker.get(CARRIAGE)) instanceof CarriageEntity carriage && !carriage.isRemoved()
				? carriage : null;
	}

	@Override
	public void tick() {
		super.tick();
		CarriageEntity carriage = getCarriage();
		if (carriage == null) {
			if (!getWorld().isClient) {
				discard();
			}
			return;
		}
		follow(carriage);
	}

	private void follow(CarriageEntity carriage) {
		Vec3d at = carriage.toWorld(OFFSETS[getPart()]);
		setPosition(at.x, at.y, at.z);
		setVelocity(Vec3d.ZERO);
	}

	@Override
	public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
		CarriageEntity carriage = getCarriage();
		return carriage == null ? ActionResult.PASS : carriage.interactAt(player, hitPos.add(getPos()).subtract(carriage.getPos()), hand);
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		CarriageEntity carriage = getCarriage();
		return carriage == null ? ActionResult.PASS : carriage.interact(player, hand);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		CarriageEntity carriage = getCarriage();
		if (carriage == null || source.getAttacker() != null && source.getAttacker().getRootVehicle() == carriage) {
			return false;
		}
		return carriage.damage(source, amount);
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	@Override
	public boolean isCollidable() {
		return true;
	}

	/** Not to her own horse, which stands in her shafts against her front. */
	@Override
	public boolean collidesWith(Entity other) {
		CarriageEntity carriage = getCarriage();
		return super.collidesWith(other) && (carriage == null || CarriageEntity.hitchedTo(other) != carriage);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return false;
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(Carriage.ITEM);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
