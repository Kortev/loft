package io.github.kortev.chitty;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Someone let down gently from the air: thrown out of Chitty by her ejector, let go by a grapple that has lost its
 * airship, or back in the game in the air where their vehicle no longer is (Reboard). They fall slowly (slow falling,
 * which takes no fall damage), kept up until they are down, however far that is: on the ground, in water, or aboard
 * something again.
 */
public final class SoftLanding {
	/** The longest anyone is kept up (ticks): a minute, enough for the highest fall. */
	private static final int LONGEST = 1200;
	/** Ticks before being on the ground counts: an ejected passenger is still in their seat as they go. */
	private static final int SETTLE = 5;
	private static final Map<LivingEntity, Integer> FALLING = new WeakHashMap<>();

	private SoftLanding() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
	}

	/** Lets someone down gently, from now until they are down (on the server). */
	public static void letDown(LivingEntity entity) {
		if (entity.getWorld().isClient) {
			return;
		}
		entity.fallDistance = 0.0F;
		FALLING.put(entity, 0);
		keepUp(entity);
	}

	/** Whether someone is being let down gently just now. */
	public static boolean isFalling(LivingEntity entity) {
		return FALLING.containsKey(entity);
	}

	private static void keepUp(LivingEntity entity) {
		StatusEffectInstance effect = entity.getStatusEffect(StatusEffects.SLOW_FALLING);
		if (effect == null || effect.getDuration() < 20 && !effect.isInfinite()) {
			entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 40, 0, false, false, true));
		}
	}

	private static void tick() {
		Iterator<Map.Entry<LivingEntity, Integer>> it = FALLING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<LivingEntity, Integer> e = it.next();
			LivingEntity entity = e.getKey();
			int ticks = e.getValue() + 1;
			boolean down = ticks > SETTLE && (entity.isOnGround() || entity.isTouchingWater() || entity.isInLava() || entity.hasVehicle()
					|| entity.isClimbing());
			if (entity.isRemoved() || !entity.isAlive() || down || ticks > LONGEST) {
				it.remove();
				continue;
			}
			e.setValue(ticks);
			keepUp(entity);
		}
	}
}
