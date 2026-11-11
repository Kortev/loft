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
 * it, and so does a player using a torch (used up), flint and steel or a fire charge on it; three hits from a player cut it up. If any seed of a burst is left after
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
	/** Ticks it has been burning (it catches, flares a second with a shriek, shrivels to ash), or -1. */
	private static final TrackedData<Integer> BURNING = DataTracker.registerData(TomatoSeedEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Ticks a Pochita has been chewing it, or -1. */
	private static final TrackedData<Integer> CHEWED = DataTracker.registerData(TomatoSeedEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** The world's time it last shuddered (a sword or a fist), for its drawing. */
	private static final TrackedData<Long> SHUDDER = DataTracker.registerData(TomatoSeedEntity.class, TrackedDataHandlerRegistry.LONG);
	/** How many times a player has hit it (a fist, a sword, an arrow): at {@link #CUTS} it pops. Cuts don't heal. */
	private static final TrackedData<Integer> CUT = DataTracker.registerData(TomatoSeedEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** How many hits end a seed. */
	public static final int CUTS = 3;
	/** How long a burning seed flares before it is ash, and how long Pochita chews one. */
	public static final int BURN = 22;
	public static final int CHEW = 30;
	/** How far off a set-down Pochita smells a seed and goes to eat it. */
	public static final double EAT_RANGE = 16.0;

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
		builder.add(BURNING, -1);
		builder.add(CHEWED, -1);
		builder.add(SHUDDER, -100L);
		builder.add(CUT, 0);
	}

	/** How many times a player has hit it. */
	public int cuts() {
		return dataTracker.get(CUT);
	}

	/**
	 * A player's hit: it squelches and shudders, and each one cuts it smaller (the second splits it open); the {@link #CUTS}th pops it with a
	 * small shriek, and a swelling one never comes back.
	 */
	public void cut() {
		if (!(getWorld() instanceof ServerWorld world) || ended()) {
			return;
		}
		int c = cuts() + 1;
		dataTracker.set(CUT, c);
		dataTracker.set(SHUDDER, world.getTime());
		if (c >= CUTS) {
			world.playSound(null, getX(), getY(), getZ(), Devils.Sounds.SHRIEK, SoundCategory.HOSTILE, 0.7F, 1.3F + random.nextFloat() * 0.2F);
			world.playSound(null, getX(), getY(), getZ(), Devils.Sounds.SQUELCH, SoundCategory.HOSTILE, 1.1F, 0.8F);
			world.spawnParticles(TomatoDevilEntity.JUICE, getX(), getY() + 0.2, getZ(), 18, 0.2, 0.12, 0.2, 0.18);
			world.spawnParticles(TomatoDevilEntity.PULP, getX(), getY() + 0.2, getZ(), 10, 0.18, 0.12, 0.18, 0.14);
			discard();
			return;
		}
		world.playSound(null, getX(), getY(), getZ(), Devils.Sounds.SQUELCH, SoundCategory.HOSTILE, 1.0F, (c == 1 ? 1.1F : 0.9F) + random.nextFloat() * 0.1F);
		world.spawnParticles(TomatoDevilEntity.JUICE, getX(), getY() + 0.2, getZ(), 6 + 4 * c, 0.12, 0.06, 0.12, 0.08);
		if (c >= 2) {
			world.spawnParticles(TomatoDevilEntity.PULP, getX(), getY() + 0.2, getZ(), 4, 0.1, 0.06, 0.1, 0.08);
		}
	}

	/** Ticks it has been burning, or -1. */
	public int burning() {
		return dataTracker.get(BURNING);
	}

	/** Ticks it has been chewed, or -1. */
	public int chewed() {
		return dataTracker.get(CHEWED);
	}

	public long shudderAt() {
		return dataTracker.get(SHUDDER);
	}

	/** Done for: burning, being eaten, withering. It will never bring anything back. */
	public boolean ended() {
		return isRemoved() || burning() >= 0 || chewed() >= 0 || withering() >= 0;
	}

	/** Pochita's teeth in it: called each tick he chews; at {@link #CHEW} it is gone. */
	public void chew() {
		if (!(getWorld() instanceof ServerWorld world) || isRemoved() || burning() >= 0) {
			return;
		}
		int c = chewed() + 1;
		dataTracker.set(CHEWED, c);
		if (c % 2 == 0) {
			world.spawnParticles(TomatoDevilEntity.JUICE, getX(), getY() + 0.2, getZ(), 3, 0.15, 0.1, 0.15, 0.12);
			world.spawnParticles(TomatoDevilEntity.PULP, getX(), getY() + 0.2, getZ(), 2, 0.1, 0.1, 0.1, 0.1);
		}
		if (c == 1 || c == 16) {
			world.playSound(null, getX(), getY(), getZ(), Devils.Sounds.CHEW, SoundCategory.NEUTRAL, 1.0F, 0.95F + random.nextFloat() * 0.1F);
		}
		if (c >= CHEW) {
			world.spawnParticles(TomatoDevilEntity.JUICE, getX(), getY() + 0.2, getZ(), 12, 0.2, 0.1, 0.2, 0.15);
			discard();
		}
	}

	/** Torn up by a chainsaw's teeth: gone at once, in a spray. */
	public void shred() {
		if (getWorld() instanceof ServerWorld world && !isRemoved()) {
			world.playSound(null, getX(), getY(), getZ(), Devils.Sounds.SQUELCH, SoundCategory.HOSTILE, 1.2F, 0.6F);
			world.spawnParticles(TomatoDevilEntity.JUICE, getX(), getY() + 0.2, getZ(), 24, 0.25, 0.15, 0.25, 0.2);
			world.spawnParticles(TomatoDevilEntity.PULP, getX(), getY() + 0.2, getZ(), 12, 0.2, 0.15, 0.2, 0.15);
			discard();
		}
	}

	/**
	 * A chainsaw's slash ({@code form/Saw}): every seed within {@code reach} of {@code player}'s eyes, in front of them within the cone whose half
	 * angle's cosine is {@code width} (seen from above: seeds lie at your feet), is shredded. How many.
	 */
	public static int shredInFront(net.minecraft.entity.player.PlayerEntity player, double reach, double width) {
		int n = 0;
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0F);
		Vec3d flat = new Vec3d(look.x, 0.0, look.z);
		flat = flat.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : flat.normalize();
		for (TomatoSeedEntity s : player.getWorld().getEntitiesByClass(TomatoSeedEntity.class, player.getBoundingBox().expand(reach + 1.0), s -> !s.ended())) {
			Vec3d to = s.getPos().add(0.0, 0.2, 0.0).subtract(eye);
			Vec3d toFlat = new Vec3d(to.x, 0.0, to.z);
			double h = toFlat.length();
			if (to.length() > reach + 0.6) {
				continue;
			}
			if (h < 0.8 || flat == Vec3d.ZERO || toFlat.normalize().dotProduct(flat) >= Math.min(width, 0.5)) {
				s.shred();
				n++;
			}
		}
		return n;
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
		int b = burning();
		if (b >= 0) {
			// Caught: it flares and shrieks, shrivels, and is ash.
			dataTracker.set(BURNING, b + 1);
			world.spawnParticles(ParticleTypes.FLAME, getX(), getY() + 0.15 + 0.2 * Math.min(1.0, b / 6.0), getZ(), b < 12 ? 3 : 1, 0.12, 0.12, 0.12, 0.02);
			if (b % 5 == 0) {
				world.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + 0.3, getZ(), 2, 0.08, 0.1, 0.08, 0.01);
			}
			if (b + 1 >= BURN) {
				world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.2, getZ(), 4, 0.12, 0.1, 0.12, 0.02);
				world.spawnParticles(ParticleTypes.WHITE_ASH, getX(), getY() + 0.2, getZ(), 30, 0.25, 0.2, 0.25, 0.02);
				world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, net.minecraft.block.Blocks.BLACK_CONCRETE_POWDER.getDefaultState()),
						getX(), getY() + 0.1, getZ(), 10, 0.1, 0.05, 0.1, 0.05);
				discard();
			}
			return;
		}
		if (chewed() >= 0) {
			return;
		}
		if (touchingFire()) {
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
	private boolean touchingFire() {
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

	/** Set alight: it catches with a burst of flame and a shriek, flares about a second ({@link #BURN}), and shrivels to ash. */
	public void burn() {
		if (getWorld() instanceof ServerWorld world && !isRemoved() && burning() < 0) {
			dataTracker.set(BURNING, 0);
			dataTracker.set(SWELLING, -1);
			world.playSound(null, getX(), getY(), getZ(), Devils.Sounds.SHRIEK, SoundCategory.HOSTILE, 1.1F, 0.9F + random.nextFloat() * 0.2F);
			world.playSound(null, getX(), getY(), getZ(), Devils.Sounds.HISS, SoundCategory.HOSTILE, 1.0F, 0.9F + random.nextFloat() * 0.2F);
			world.spawnParticles(ParticleTypes.FLAME, getX(), getY() + 0.15, getZ(), 14, 0.15, 0.15, 0.15, 0.05);
			world.spawnParticles(ParticleTypes.LAVA, getX(), getY() + 0.1, getZ(), 2, 0.1, 0.05, 0.1, 0.0);
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
				s -> s != this && s.isAlive() && s.brood.equals(brood) && !s.ended());
	}

	/** The devil climbs out of it. */
	private void hatch(ServerWorld world) {
		TomatoDevilEntity devil = Devils.TOMATO_DEVIL.create(world);
		if (devil != null) {
			devil.refreshPositionAndAngles(getX(), getY(), getZ(), random.nextFloat() * 360.0F, 0.0F);
			devil.initialize(world, world.getLocalDifficulty(getBlockPos()), SpawnReason.REINFORCEMENT, null);
			devil.seeds = next;
			devil.setHealth(devil.getMaxHealth() * TomatoDevilEntity.COMEBACK_HEALTH);
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
		if (!torch && !flint && !charge || ended()) {
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
		// Fire ends it, and a chainsaw's teeth do (Pochita held, or Chainsaw Man's saws). Any other hit from a player (a fist, a sword, their
		// arrow) cuts it, and the third pops it. Anything else only squelches into it and it shudders. (A burning arrow or a fire-aspect sword
		// sets it alight, and it burns.)
		if (!(getWorld() instanceof ServerWorld world) || ended()) {
			return false;
		}
		if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FIRE)) {
			burn();
			return true;
		}
		if (source.getAttacker() instanceof net.minecraft.server.network.ServerPlayerEntity p
				&& (io.github.kortev.chainsawman.form.Saw.wielding(p) || io.github.kortev.chainsawman.form.Form.fighting(p))) {
			shred();
			return true;
		}
		if (source.getAttacker() instanceof PlayerEntity) {
			cut();
			return true;
		}
		if (source.getAttacker() != null && world.getTime() - shudderAt() > 6) {
			dataTracker.set(SHUDDER, world.getTime());
			world.playSound(null, getX(), getY(), getZ(), Devils.Sounds.SQUELCH, SoundCategory.HOSTILE, 0.9F, 1.1F + random.nextFloat() * 0.2F);
			world.spawnParticles(TomatoDevilEntity.JUICE, getX(), getY() + 0.2, getZ(), 5, 0.1, 0.05, 0.1, 0.05);
		}
		return false;
	}

	@Override
	public boolean isAttackable() {
		return true;
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
		dataTracker.set(BURNING, nbt.contains("Burning") ? nbt.getInt("Burning") : -1);
		dataTracker.set(CUT, nbt.getInt("Cuts"));
		// (Half eaten when the world was saved: Pochita finishes it from the start.)
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
		nbt.putInt("Burning", burning());
		nbt.putInt("Cuts", cuts());
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
