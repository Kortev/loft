package io.github.kortev.chainsawman.hotel;

import io.github.kortev.chainsawman.ChainsawSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * Flesh creeping across the floor from the Eternity Devil ({@link EternityDevilEntity}): a low lump of it, which comes up, stays a while
 * and sinks back. Whoever stands on it is slowed; stand on it for a second and it bites, again and again while you do.
 */
public class FleshPatchEntity extends Entity {
	/** How long a patch lasts (ticks) from when it begins, and how long it takes to come up and to sink. */
	public static final int LIFE = 180;
	public static final int RISE = 12;
	public static final int SINK = 20;
	public static final int SLOW_LEVEL = 1;
	public static final float BITE = 3.0F;
	/** Ticks of standing on it before the first bite, and between bites after. */
	public static final int BITES_AFTER = 20;
	public static final int BITE_EVERY = 20;
	public static final double RADIUS = 1.1;

	private static final TrackedData<Integer> START = DataTracker.registerData(FleshPatchEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> SPAN = DataTracker.registerData(FleshPatchEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> BIT_AT = DataTracker.registerData(FleshPatchEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private final Map<UUID, Integer> standing = new HashMap<>();

	public FleshPatchEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	/** It lasts {@code life} ticks, beginning {@code delayTicks} from now. */
	public void setLife(int life, int delayTicks) {
		this.dataTracker.set(START, (int) this.getWorld().getTime() + Math.max(0, delayTicks));
		this.dataTracker.set(SPAN, life);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(START, 0);
		builder.add(SPAN, LIFE);
		builder.add(BIT_AT, -1000);
	}

	/** The world tick it begins to come up, how long it lasts, and when it last bit. */
	public int start() {
		return this.dataTracker.get(START);
	}

	public int span() {
		return this.dataTracker.get(SPAN);
	}

	public int bitAt() {
		return this.dataTracker.get(BIT_AT);
	}

	/** Whether it is up enough to be stood on, at world tick {@code now}. */
	public boolean active(long now) {
		return now >= start() + RISE && now < start() + span() - SINK;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		long now = world.getTime();
		if (now >= start() + span()) {
			this.discard();
			return;
		}
		if (!active(now) || now % 5 != 0) {
			return;
		}
		Box box = new Box(this.getX() - RADIUS, this.getY() - 0.5, this.getZ() - RADIUS, this.getX() + RADIUS, this.getY() + 1.6, this.getZ() + RADIUS);
		java.util.Set<UUID> now5 = new java.util.HashSet<>();
		for (ServerPlayerEntity p : world.getEntitiesByClass(ServerPlayerEntity.class, box, e -> e.isAlive() && !e.isSpectator() && !e.isCreative())) {
			now5.add(p.getUuid());
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, SLOW_LEVEL, false, false, true));
			int on = standing.merge(p.getUuid(), 5, Integer::sum);
			if (on >= BITES_AFTER && (on - BITES_AFTER) % BITE_EVERY < 5) {
				this.dataTracker.set(BIT_AT, (int) now);
				world.playSound(null, p.getX(), p.getY(), p.getZ(), ChainsawSounds.DEVIL_BITE, SoundCategory.HOSTILE, 0.9F, 1.15F);
				p.damage(this.getDamageSources().generic(), BITE);
			}
		}
		standing.keySet().retainAll(now5);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
