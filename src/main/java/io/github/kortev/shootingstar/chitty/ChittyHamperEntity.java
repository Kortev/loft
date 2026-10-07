package io.github.kortev.shootingstar.chitty;

import net.minecraft.entity.Entity;
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
 * The hamper's own hitbox. An entity's box is a square about its middle, and Chitty's is far too short to reach the
 * hamper on her stern, so while she has it she puts out this small invisible one that keeps to the hamper. Using it
 * is using her there (open the hamper; sneak to take it off); hitting it is hitting her, unless you are riding in her.
 * It is never saved: she puts out another when she is loaded.
 */
public class ChittyHamperEntity extends Entity {
	/** Where the bottom of the box is on her, in her own axes (as ChittyEntity's seats). */
	static final Vec3d OFFSET = new Vec3d(0.0, 0.58, -2.98);
	private static final TrackedData<Integer> CAR = DataTracker.registerData(ChittyHamperEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public ChittyHamperEntity(EntityType<? extends ChittyHamperEntity> type, World world) {
		super(type, world);
		noClip = true;
	}

	ChittyHamperEntity(World world, ChittyEntity car) {
		this(Chitty.HAMPER, world);
		dataTracker.set(CAR, car.getId());
		follow(car);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(CAR, -1);
	}

	/** The car whose hamper this is, while she is there. */
	@Nullable
	public ChittyEntity getCar() {
		return getWorld().getEntityById(dataTracker.get(CAR)) instanceof ChittyEntity car && !car.isRemoved() ? car : null;
	}

	@Override
	public void tick() {
		super.tick();
		ChittyEntity car = getCar();
		if (car == null || !car.hasHamper()) {
			if (!getWorld().isClient) {
				discard();
			}
			return;
		}
		follow(car);
	}

	private void follow(ChittyEntity car) {
		Vec3d at = car.getPos().add(OFFSET.rotateY(-car.getYaw() * MathHelper.RADIANS_PER_DEGREE));
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
