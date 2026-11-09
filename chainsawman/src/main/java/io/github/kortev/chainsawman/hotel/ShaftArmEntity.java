package io.github.kortev.chainsawman.hotel;

import io.github.kortev.chainsawman.ChainsawSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * One of the arms the Eternity Devil sends up the shaft: it rises from the mass at the bottom, a hand of flesh at its tip, catches a player
 * standing on a ledge and pulls them down onto the mass. The player breaks free by hitting it ({@link #HEALTH}). Its box is the whole
 * column from the mass to its tip, so it can be hit anywhere along it. Drawn by {@code ShaftArmRenderer} (a placeholder: lumps up a column
 * and a hand).
 */
public class ShaftArmEntity extends Entity {
	public static final float HEALTH = 14.0F;
	public static final double WIDTH = 1.0;
	/** Blocks a tick the tip rises, is pulled down with a player, and sinks. */
	public static final double RISE = 0.85;
	public static final double PULL = 0.55;
	public static final double SINK = 0.8;
	/** How far from its column (blocks, across) the hand catches a player, and how far above or below the tip. */
	public static final double REACH = 2.9;
	public static final float GRAB_DAMAGE = 4.0F;

	private static final TrackedData<Float> TIP = DataTracker.registerData(ShaftArmEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Boolean> HOLDING = DataTracker.registerData(ShaftArmEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	public BlockPos origin = BlockPos.ORIGIN;
	public int rot;
	/** The hotel-local y of the mass's floor (where the arm stands) and the highest its tip goes. */
	private double floorY;
	private double maxTip;
	private int state;
	private java.util.UUID held;
	private float health = HEALTH;

	public ShaftArmEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
		this.setNoGravity(true);
	}

	/** Rises from {@code at} (world) in the hotel at {@code origin} turned {@code rot}. */
	public void start(BlockPos origin, int rot, Vec3d at, double maxTipY) {
		this.origin = origin;
		this.rot = rot;
		this.floorY = at.y;
		this.maxTip = maxTipY;
		this.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
		this.dataTracker.set(TIP, (float) at.y + 0.5F);
		this.setBoundingBox(this.calculateBoundingBox());
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(TIP, 0.0F);
		builder.add(HOLDING, false);
	}

	/** The world y of its tip. */
	public float tip() {
		return this.dataTracker.get(TIP);
	}

	public boolean holding() {
		return this.dataTracker.get(HOLDING);
	}

	@Override
	protected Box calculateBoundingBox() {
		if (this.dataTracker == null) {
			return super.calculateBoundingBox();
		}
		double top = Math.max(this.getY() + 0.5, tip() + 0.6);
		return new Box(this.getX() - WIDTH / 2.0, this.getY(), this.getZ() - WIDTH / 2.0, this.getX() + WIDTH / 2.0, top, this.getZ() + WIDTH / 2.0);
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (TIP.equals(data)) {
			this.setBoundingBox(this.calculateBoundingBox());
		}
	}

	@Override
	public boolean canHit() {
		return true;
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
	public boolean isAttackable() {
		return true;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (!(this.getWorld() instanceof ServerWorld world) || state == 2) {
			return false;
		}
		if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
			this.discard();
			return true;
		}
		health -= amount;
		world.playSound(null, this.getX(), tip(), this.getZ(), ChainsawSounds.DEVIL_HURT, SoundCategory.HOSTILE, 0.9F, 1.2F);
		world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, this.getX(), tip() - 0.3, this.getZ(), 3, 0.3, 0.3, 0.3, 0.1);
		if (health <= 0.0F) {
			release();
			state = 2;
		}
		return true;
	}

	private void release() {
		held = null;
		this.dataTracker.set(HOLDING, false);
	}

	private ServerPlayerEntity heldPlayer(ServerWorld world) {
		return held == null ? null : world.getServer().getPlayerManager().getPlayer(held);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		double tip = tip();
		if (state == 0) {
			tip += RISE;
			ServerPlayerEntity catches = null;
			for (ServerPlayerEntity p : world.getPlayers()) {
				if (!p.isAlive() || p.isSpectator() || p.isCreative() || io.github.kortev.chainsawman.form.Downed.is(p)) {
					continue;
				}
				double dx = p.getX() - this.getX();
				double dz = p.getZ() - this.getZ();
				double dy = p.getY() + 0.9 - tip;
				// (Anyone the tip is level with, near enough across, on a ledge: the hand is wide.)
				if (dx * dx + dz * dz <= REACH * REACH && dy <= 0.6 && dy >= -RISE - 1.6 && p.getY() > floorY + 4.0) {
					catches = p;
					break;
				}
			}
			if (catches != null) {
				held = catches.getUuid();
				state = 1;
				this.dataTracker.set(HOLDING, true);
				catches.damage(this.getDamageSources().generic(), GRAB_DAMAGE);
				world.playSound(null, catches.getX(), catches.getY(), catches.getZ(), ChainsawSounds.DEVIL_BITE, SoundCategory.HOSTILE, 1.4F, 0.7F);
			} else if (tip >= maxTip) {
				state = 2;
			}
		} else if (state == 1) {
			ServerPlayerEntity p = heldPlayer(world);
			if (p == null || !p.isAlive() || io.github.kortev.chainsawman.form.Downed.is(p) && false) {
				release();
				state = 2;
			} else {
				tip -= PULL;
				// Over the mass's last stretch it swings them out from over the mouth onto the floor beside it.
				Vec3d beside = HotelBuilder.world(origin, rot, 22.0, 0.0, 6.5);
				// The hand carries them down: pulled in to its column, dragged at the arm's speed.
				boolean low = tip < floorY + 5.0;
				p.setVelocity(((low ? beside.x : this.getX()) - p.getX()) * 0.3, -PULL + (tip - 1.0 - p.getY()) * 0.3, ((low ? beside.z : this.getZ()) - p.getZ()) * 0.3);
				p.velocityModified = true;
				p.fallDistance = 0.0F;
				if (tip <= floorY + 1.2) {
					// Down on the mass: let go, and sink.
					release();
					state = 2;
					p.addVelocity(0.0, 0.2, 0.0);
					p.velocityModified = true;
				}
			}
		} else {
			if (holding()) {
				release();
			}
			tip -= SINK;
			if (tip <= floorY) {
				this.discard();
				return;
			}
		}
		this.dataTracker.set(TIP, (float) tip);
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
