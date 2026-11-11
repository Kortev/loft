package io.github.kortev.chainsawman.devil;

import io.github.kortev.chainsawman.PochitaEntity;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Pochita eats devils' seeds: a tamed Pochita set down (not sitting, not fainted) within {@link TomatoSeedEntity#EAT_RANGE} blocks of a
 * Tomato Devil's seed runs to the nearest one and chews it up ({@link TomatoSeedEntity#CHEW} ticks), then goes for the next. Devils eat
 * devils; there is nothing in it for anyone (no XP). Any seed, whoever's devil it was. Two Pochitas never go for the same seed.
 */
public class EatSeedGoal extends Goal {
	/** Which seed each Pochita is after. */
	private static final Map<UUID, UUID> AFTER = new ConcurrentHashMap<>();
	private final PochitaEntity pochita;
	@Nullable
	private TomatoSeedEntity seed;
	private int stuck;

	public EatSeedGoal(PochitaEntity pochita) {
		this.pochita = pochita;
		setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
	}

	private boolean able() {
		return pochita.isAlive() && pochita.isTamed() && !pochita.fainted() && !pochita.isSitting() && !pochita.isLeashed();
	}

	private boolean takenByAnother(TomatoSeedEntity s) {
		for (Map.Entry<UUID, UUID> e : AFTER.entrySet()) {
			if (e.getValue().equals(s.getUuid()) && !e.getKey().equals(pochita.getUuid())) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	private TomatoSeedEntity nearest() {
		List<TomatoSeedEntity> all = pochita.getWorld().getEntitiesByClass(TomatoSeedEntity.class,
				pochita.getBoundingBox().expand(TomatoSeedEntity.EAT_RANGE, 8.0, TomatoSeedEntity.EAT_RANGE),
				s -> s.landed() && !s.ended() && !takenByAnother(s) && s.squaredDistanceTo(pochita) <= TomatoSeedEntity.EAT_RANGE * TomatoSeedEntity.EAT_RANGE);
		TomatoSeedEntity best = null;
		double bd = Double.MAX_VALUE;
		for (TomatoSeedEntity s : all) {
			double d = s.squaredDistanceTo(pochita);
			if (d < bd) {
				bd = d;
				best = s;
			}
		}
		return best;
	}

	@Override
	public boolean canStart() {
		if (!able() || pochita.age % 5 != 0) {
			return false;
		}
		seed = nearest();
		return seed != null;
	}

	@Override
	public boolean shouldContinue() {
		return able() && seed != null && !seed.isRemoved() && seed.burning() < 0 && stuck < 200
				&& seed.squaredDistanceTo(pochita) <= (TomatoSeedEntity.EAT_RANGE + 8.0) * (TomatoSeedEntity.EAT_RANGE + 8.0);
	}

	@Override
	public void start() {
		stuck = 0;
		if (seed != null) {
			AFTER.put(pochita.getUuid(), seed.getUuid());
		}
	}

	@Override
	public void stop() {
		AFTER.remove(pochita.getUuid());
		seed = null;
		pochita.getNavigation().stop();
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (seed == null) {
			return;
		}
		Vec3d at = seed.getPos();
		pochita.getLookControl().lookAt(at.x, at.y + 0.2, at.z, 30.0F, 30.0F);
		double d = Math.hypot(at.x - pochita.getX(), at.z - pochita.getZ());
		if (d > 0.9 || Math.abs(at.y - pochita.getY()) > 1.2) {
			if (pochita.age % 5 == 0 || pochita.getNavigation().isIdle()) {
				pochita.getNavigation().startMovingTo(at.x, at.y, at.z, 1.5);
			}
			stuck++;
			return;
		}
		// At it: teeth in.
		pochita.getNavigation().stop();
		float yaw = (float) Math.toDegrees(Math.atan2(-(at.x - pochita.getX()), at.z - pochita.getZ()));
		pochita.setYaw(yaw);
		pochita.setBodyYaw(yaw);
		pochita.setHeadYaw(yaw);
		seed.chew();
		if (seed.isRemoved()) {
			// Gone: the next one, if there is one.
			TomatoSeedEntity next = nearest();
			seed = next;
			if (next != null) {
				AFTER.put(pochita.getUuid(), next.getUuid());
				stuck = 0;
			}
		}
	}
}
