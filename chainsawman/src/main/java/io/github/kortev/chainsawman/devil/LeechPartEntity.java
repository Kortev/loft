package io.github.kortev.chainsawman.devil;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A piece of a Leech Devil you can cut while it is out of its body: the arm stuck to someone ({@link #ARM}: cut it to tear it loose) or the
 * tongue held out after it lances ({@link #TONGUE}: a cut there counts more). Not drawn (the devil's renderer draws the arm and the tongue);
 * only a box where the blade lands, put each tick by the devil where the piece is nearest whoever would cut it. Never saved.
 */
public class LeechPartEntity extends Entity {
	public static final int ARM = 0;
	public static final int TONGUE = 1;

	private static final TrackedData<Integer> KIND = DataTracker.registerData(LeechPartEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> OWNER = DataTracker.registerData(LeechPartEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public LeechPartEntity(EntityType<? extends LeechPartEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		setNoGravity(true);
	}

	public LeechPartEntity(World world, LeechDevilEntity owner, int kind) {
		this(Devils.LEECH_PART, world);
		dataTracker.set(KIND, kind);
		dataTracker.set(OWNER, owner.getId());
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(KIND, ARM);
		builder.add(OWNER, -1);
	}

	public int kind() {
		return dataTracker.get(KIND);
	}

	@Nullable
	public LeechDevilEntity owner() {
		return getWorld().getEntityById(dataTracker.get(OWNER)) instanceof LeechDevilEntity d ? d : null;
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient()) {
			LeechDevilEntity d = owner();
			if (d == null || d.isRemoved() || !d.isAlive() || age > 400) {
				discard();
			}
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (getWorld().isClient() || isRemoved() || isInvulnerableTo(source)) {
			return false;
		}
		LeechDevilEntity d = owner();
		return d != null && d.partHit(this, source, amount);
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	@Override
	public boolean isAttackable() {
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
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
