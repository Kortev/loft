package io.github.kortev.chitty;

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
 * One of the hitboxes along Chitty. An entity's box is a square about its middle, and hers covers only her middle:
 * her bonnet, her back seat and stern, and the hamper on the end, get these invisible ones of their own, which keep to
 * her as she moves. They are as solid as she is (nothing walks through her nose), using one is using her there (the
 * seat nearest where you click; the hamper, to open it; sneak to take it off) and hitting one is hitting her, unless
 * you are riding in her. They are never saved: she puts out new ones when she is loaded, the hamper's only while she
 * has it.
 */
public class ChittyPartEntity extends Entity {
	public static final int FRONT = 0;
	public static final int BACK = 1;
	public static final int HAMPER = 2;
	static final int COUNT = 3;
	/** Where the bottom middle of each box is on her, in her own axes (as ChittyEntity's seats), and its size. */
	private static final Vec3d[] OFFSETS = {new Vec3d(0.0, 0.0, 1.72), new Vec3d(0.0, 0.0, -1.80), new Vec3d(0.0, 0.58, -2.98)};
	private static final EntityDimensions[] SIZES = {EntityDimensions.changing(1.45F, 1.6F), EntityDimensions.changing(1.6F, 1.45F),
			EntityDimensions.changing(0.8F, 0.5F)};
	private static final TrackedData<Integer> CAR = DataTracker.registerData(ChittyPartEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Byte> PART = DataTracker.registerData(ChittyPartEntity.class, TrackedDataHandlerRegistry.BYTE);

	public ChittyPartEntity(EntityType<? extends ChittyPartEntity> type, World world) {
		super(type, world);
		noClip = true;
	}

	ChittyPartEntity(World world, ChittyEntity car, int part) {
		this(Chitty.PART, world);
		dataTracker.set(CAR, car.getId());
		dataTracker.set(PART, (byte) part);
		calculateDimensions();
		follow(car);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(CAR, -1);
		builder.add(PART, (byte) FRONT);
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (PART.equals(data)) {
			calculateDimensions();
		}
	}

	/** Which of her boxes this is: FRONT, BACK or HAMPER. */
	public int getPart() {
		return MathHelper.clamp(dataTracker.get(PART), 0, COUNT - 1);
	}

	@Override
	public EntityDimensions getDimensions(EntityPose pose) {
		return SIZES[getPart()];
	}

	/** The car this is part of, while she is there. */
	@Nullable
	public ChittyEntity getCar() {
		return getWorld().getEntityById(dataTracker.get(CAR)) instanceof ChittyEntity car && !car.isRemoved() ? car : null;
	}

	/** Whether she should have this box: the hamper's only while she has her hamper. */
	static boolean wanted(ChittyEntity car, int part) {
		return part != HAMPER || car.hasHamper();
	}

	@Override
	public void tick() {
		super.tick();
		ChittyEntity car = getCar();
		if (car == null || !wanted(car, getPart())) {
			if (!getWorld().isClient) {
				discard();
			}
			return;
		}
		follow(car);
	}

	private void follow(ChittyEntity car) {
		Vec3d at = car.getPos().add(OFFSETS[getPart()].rotateY(-car.getYaw() * MathHelper.RADIANS_PER_DEGREE));
		setPosition(at.x, at.y, at.z);
		setVelocity(Vec3d.ZERO);
	}

	@Override
	public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
		ChittyEntity car = getCar();
		return car == null ? ActionResult.PASS : car.interactAt(player, hitPos.add(getPos()).subtract(car.getPos()), hand);
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		ChittyEntity car = getCar();
		return car == null ? ActionResult.PASS : car.interact(player, hand);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		ChittyEntity car = getCar();
		if (car == null || source.getAttacker() != null && source.getAttacker().getRootVehicle() == car) {
			return false;
		}
		return car.damage(source, amount);
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
	protected boolean canAddPassenger(Entity passenger) {
		return false;
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(Chitty.ITEM);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
