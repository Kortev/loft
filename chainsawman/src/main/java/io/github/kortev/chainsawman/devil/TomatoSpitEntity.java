package io.github.kortev.chainsawman.devil;

import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** A seed the Tomato Devil spits: it flies {@link TomatoDevilEntity#SPIT_RANGE} blocks at most and stings whoever it hits. */
public class TomatoSpitEntity extends Entity {
	public static final double SPEED = 0.85;
	private static final double DROP = 0.025;

	@Nullable
	private UUID owner;
	private Vec3d from = Vec3d.ZERO;

	public TomatoSpitEntity(EntityType<? extends TomatoSpitEntity> type, World world) {
		super(type, world);
	}

	/** Sends it from {@code at} with {@code velocity}, spat by {@code by}. */
	public void shoot(Entity by, Vec3d at, Vec3d velocity) {
		owner = by.getUuid();
		from = at;
		refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
		setVelocity(velocity);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		Vec3d p = getPos();
		Vec3d v = getVelocity();
		Vec3d to = p.add(v);
		if (getWorld() instanceof ServerWorld world) {
			BlockHitResult block = world.raycast(new RaycastContext(p, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
			Vec3d end = block.getType() == HitResult.Type.MISS ? to : block.getPos();
			for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, getBoundingBox().stretch(v).expand(0.5), this::canHit)) {
				if (e.getBoundingBox().expand(0.1).raycast(p, end).isPresent() || e.getBoundingBox().expand(0.1).contains(p)) {
					Entity by = owner == null ? null : world.getEntity(owner);
					e.damage(by instanceof LivingEntity l ? getDamageSources().mobProjectile(this, l) : getDamageSources().generic(), TomatoDevilEntity.SPIT_DAMAGE);
					splat(world, e.getPos().add(0.0, e.getHeight() * 0.6, 0.0));
					return;
				}
			}
			if (block.getType() != HitResult.Type.MISS || p.squaredDistanceTo(from) > TomatoDevilEntity.SPIT_RANGE * TomatoDevilEntity.SPIT_RANGE || age > 40) {
				splat(world, end);
				return;
			}
		}
		setPosition(to);
		setVelocity(v.x * 0.99, v.y - DROP, v.z * 0.99);
	}

	private boolean canHit(LivingEntity e) {
		return e.isAlive() && !(e instanceof TomatoDevilEntity) && !e.isSpectator() && !(e instanceof PlayerEntity p && p.isCreative());
	}

	private void splat(ServerWorld world, Vec3d at) {
		world.spawnParticles(TomatoDevilEntity.JUICE, at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.08);
		world.spawnParticles(TomatoDevilEntity.PULP, at.x, at.y, at.z, 3, 0.1, 0.1, 0.1, 0.05);
		discard();
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 64.0 * 64.0;
	}
}
