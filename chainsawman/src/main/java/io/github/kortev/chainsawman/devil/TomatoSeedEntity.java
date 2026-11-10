package io.github.kortev.chainsawman.devil;

import java.util.List;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * One of a Tomato Devil's seeds, stuck in the ground where it landed after the burst, glowing faintly. Fire, lava or a lit campfire burns
 * it, and so does a player using a torch (used up), flint and steel or a fire charge on it. If any seed of a burst is left after
 * {@link #COMEBACK} ticks, one swells for {@link #SWELL} ticks (the tell) and the devil climbs out of it; the others wither. The devil that
 * comes back bursts into one seed fewer ({@link #next}), so a line dies out by itself. Seeds never despawn, and are saved with the world.
 */
public class TomatoSeedEntity extends Entity {
	/** How long the seeds lie before one brings the devil back (60 s), and how long it swells first (10 s). */
	public static final int COMEBACK = 1200;
	public static final int SWELL = 200;
	/** How long the others take to wither. */
	public static final int WITHER = 30;
	/** Its flight from the burst: up at this, down in about {@link #FLIGHT} ticks. */
	public static final double LAUNCH = 0.42;
	public static final double GRAVITY = 0.045;
	public static final double FLIGHT = 2.0 * LAUNCH / GRAVITY;

	private static final TrackedData<Integer> SWELLING = DataTracker.registerData(TomatoSeedEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> WITHERING = DataTracker.registerData(TomatoSeedEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> LANDED = DataTracker.registerData(TomatoSeedEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/** The burst it came from: its brothers share it. */
	public UUID brood = UUID.randomUUID();
	/** How many seeds the devil that comes out of this one will burst into. */
	public int next = TomatoDevilEntity.SEEDS - 1;
	/** Its place in the burst (the first one to land tells everyone). */
	public int index;
	/** Ticks it has lain in the ground. */
	public int ground;

	public TomatoSeedEntity(EntityType<? extends TomatoSeedEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(SWELLING, -1);
		builder.add(WITHERING, -1);
		builder.add(LANDED, false);
	}

	/** Ticks it has been swelling, or -1. */
	public int swelling() {
		return dataTracker.get(SWELLING);
	}

	/** Ticks it has been withering, or -1. */
	public int withering() {
		return dataTracker.get(WITHERING);
	}

	public boolean landed() {
		return dataTracker.get(LANDED);
	}

	/** For the tests and the film: how long it has lain, and how far into its swell it is. */
	public void lain(int ticks) {
		ground = ticks;
	}

	public void swellTo(int ticks) {
		dataTracker.set(SWELLING, ticks);
	}

	@Override
	public void tick() {
		super.tick();
		if (!landed()) {
			Vec3d v = getVelocity();
			setVelocity(v.x * 0.98, v.y - GRAVITY, v.z * 0.98);
			move(MovementType.SELF, getVelocity());
			if (isOnGround() || verticalCollision && getVelocity().y <= 0.0) {
				setVelocity(Vec3d.ZERO);
				if (!getWorld().isClient) {
					dataTracker.set(LANDED, true);
					if (getWorld() instanceof ServerWorld w) {
						w.spawnParticles(TomatoDevilEntity.JUICE, getX(), getY() + 0.1, getZ(), 6, 0.15, 0.05, 0.15, 0.05);
						if (index == 0) {
							TomatoDevilEntity.tellSeeds(w, getPos());
						}
					}
				}
			} else if (horizontalCollision) {
				setVelocity(0.0, getVelocity().y, 0.0);
			}
		} else if (!getWorld().isClient && !getWorld().getBlockState(BlockPos.ofFloored(getX(), getY() - 0.05, getZ())).isSolid() && getWorld().isAir(getBlockPos().down())) {
			// The ground went from under it.
			dataTracker.set(LANDED, false);
		}
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		if (burning()) {
			burn();
			return;
		}
		int w = withering();
		if (w >= 0) {
			dataTracker.set(WITHERING, w + 1);
			if (w + 1 >= WITHER) {
				world.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 6, 0.1, 0.05, 0.1, 0.01);
				discard();
			}
			return;
		}
		int s = swelling();
		if (s >= 0) {
			dataTracker.set(SWELLING, s + 1);
			if ((s + 1) % 40 == 1) {
				world.playSound(null, getX(), getY(), getZ(), Devils.Sounds.SWELL, SoundCategory.HOSTILE, 1.2F, 0.8F + 0.4F * (s + 1) / SWELL);
			}
			if (s + 1 >= SWELL) {
				hatch(world);
			}
			return;
		}
		if (landed()) {
			ground++;
			if (ground >= COMEBACK) {
				wake(world);
			}
		}
		if (age % 40 == 7) {
			Sniff.near(world, getPos());
		}
	}

	/** Whether fire, lava or a lit campfire touches it. */
	private boolean burning() {
		if (isOnFire() || isInLava()) {
			return true;
		}
		Box b = getBoundingBox().expand(0.05);
		for (BlockPos p : BlockPos.iterate(BlockPos.ofFloored(b.minX, b.minY - 0.1, b.minZ), BlockPos.ofFloored(b.maxX, b.maxY, b.maxZ))) {
			BlockState s = getWorld().getBlockState(p);
			if (s.isIn(BlockTags.FIRE) || s.getFluidState().isIn(FluidTags.LAVA) || s.isIn(BlockTags.CAMPFIRES) && s.get(CampfireBlock.LIT)) {
				return true;
			}
		}
		return false;
	}

	/** Burnt: a hiss, smoke and flame, gone. */
	public void burn() {
		if (getWorld() instanceof ServerWorld world && !isRemoved()) {
			world.playSound(null, getX(), getY(), getZ(), Devils.Sounds.HISS, SoundCategory.HOSTILE, 1.2F, 0.9F + random.nextFloat() * 0.2F);
			world.spawnParticles(ParticleTypes.FLAME, getX(), getY() + 0.15, getZ(), 10, 0.15, 0.1, 0.15, 0.03);
			world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.2, getZ(), 3, 0.12, 0.1, 0.12, 0.02);
			world.spawnParticles(ParticleTypes.LAVA, getX(), getY() + 0.1, getZ(), 1, 0.1, 0.05, 0.1, 0.0);
			discard();
		}
	}

	/** The time is up: this one swells, and its brothers wither (if one of them has not already). */
	private void wake(ServerWorld world) {
		List<TomatoSeedEntity> brothers = brothers(world);
		for (TomatoSeedEntity b : brothers) {
			if (b.swelling() >= 0) {
				// One is already coming back: this one withers.
				dataTracker.set(WITHERING, 0);
				return;
			}
		}
		dataTracker.set(SWELLING, 0);
		for (TomatoSeedEntity b : brothers) {
			b.dataTracker.set(WITHERING, 0);
		}
	}

	/** The other seeds of its burst still in the ground near it. */
	public List<TomatoSeedEntity> brothers(ServerWorld world) {
		return world.getEntitiesByClass(TomatoSeedEntity.class, getBoundingBox().expand(24.0, 16.0, 24.0),
				s -> s != this && s.isAlive() && s.brood.equals(brood) && s.withering() < 0);
	}

	/** The devil climbs out of it. */
	private void hatch(ServerWorld world) {
		TomatoDevilEntity devil = Devils.TOMATO_DEVIL.create(world);
		if (devil != null) {
			devil.refreshPositionAndAngles(getX(), getY(), getZ(), random.nextFloat() * 360.0F, 0.0F);
			devil.initialize(world, world.getLocalDifficulty(getBlockPos()), SpawnReason.REINFORCEMENT, null);
			devil.seeds = next;
			devil.setPersistent();
			world.spawnEntity(devil);
			devil.begin(TomatoDevilEntity.EMERGE);
		}
		world.playSound(null, getX(), getY(), getZ(), Devils.Sounds.BURST, SoundCategory.HOSTILE, 1.4F, 1.3F);
		world.spawnParticles(TomatoDevilEntity.JUICE, getX(), getY() + 0.5, getZ(), 40, 0.6, 0.4, 0.6, 0.2);
		discard();
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		boolean torch = stack.isOf(Items.TORCH) || stack.isOf(Items.SOUL_TORCH);
		boolean flint = stack.isOf(Items.FLINT_AND_STEEL);
		boolean charge = stack.isOf(Items.FIRE_CHARGE);
		if (!torch && !flint && !charge) {
			return ActionResult.PASS;
		}
		if (getWorld().isClient) {
			return ActionResult.SUCCESS;
		}
		if (flint) {
			stack.damage(1, player, hand == Hand.MAIN_HAND ? net.minecraft.entity.EquipmentSlot.MAINHAND : net.minecraft.entity.EquipmentSlot.OFFHAND);
		} else if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		burn();
		return ActionResult.SUCCESS;
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
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
	public boolean damage(net.minecraft.entity.damage.DamageSource source, float amount) {
		// Cutting or hitting it does nothing: only fire. (A burning arrow or a fire-aspect sword sets it alight, and it burns.)
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.containsUuid("Brood")) {
			brood = nbt.getUuid("Brood");
		}
		next = nbt.getInt("Next");
		index = nbt.getInt("Index");
		ground = nbt.getInt("Ground");
		dataTracker.set(LANDED, nbt.getBoolean("Landed"));
		dataTracker.set(SWELLING, nbt.contains("Swelling") ? nbt.getInt("Swelling") : -1);
		dataTracker.set(WITHERING, nbt.contains("Withering") ? nbt.getInt("Withering") : -1);
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putUuid("Brood", brood);
		nbt.putInt("Next", next);
		nbt.putInt("Index", index);
		nbt.putInt("Ground", ground);
		nbt.putBoolean("Landed", landed());
		nbt.putInt("Swelling", swelling());
		nbt.putInt("Withering", withering());
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 64.0 * 64.0;
	}

	/** Whether {@code e} is one of these (for the Pochita and the tests). */
	public static boolean is(Entity e) {
		return e instanceof TomatoSeedEntity;
	}
}
