package io.github.kortev.chainsawman.devil;

import io.github.kortev.chainsawman.PochitaEntity;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Pochita smells a devil: a tamed Pochita within {@link #RANGE} blocks of a devil or one of its seeds barks and faces it (as he does when
 * he finds the first one), then again every {@link #EVERY} ticks while it is near.
 */
public final class Sniff {
	public static final double RANGE = 32.0;
	public static final int EVERY = 200;
	/** How long he keeps facing it after a bark. */
	public static final int FACE = 50;
	private static final Map<UUID, Long> BARKED = new ConcurrentHashMap<>();

	private Sniff() {
	}

	static void init() {
	}

	/** A devil (or a seed) at {@code at}: any Pochita near barks at it. */
	public static void near(ServerWorld world, Vec3d at) {
		long now = world.getTime();
		for (PochitaEntity p : world.getEntitiesByClass(PochitaEntity.class, new Box(at, at).expand(RANGE),
				p -> p.isAlive() && p.isTamed() && !p.fainted() && p.squaredDistanceTo(at) <= RANGE * RANGE)) {
			Long last = BARKED.get(p.getUuid());
			if (last == null || now - last >= EVERY) {
				BARKED.put(p.getUuid(), now);
				p.alertAt = at;
				p.alertUntil = now + FACE;
				world.playSound(null, p.getX(), p.getY(), p.getZ(), Devils.Sounds.BARK, SoundCategory.NEUTRAL, 1.0F, 0.95F + world.getRandom().nextFloat() * 0.1F);
			}
		}
	}
}
