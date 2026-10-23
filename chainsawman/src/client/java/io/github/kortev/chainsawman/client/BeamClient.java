package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.BeamEntity;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.form.Beam;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;

/**
 * What every client hears of Beam besides what he does once ({@link BeamRenderer} draws him): the churning of the ground
 * while he swims through it, louder and higher the faster he goes, silent when he stands; and what the rider feels
 * of his speed: the view widening a little, the camera shaking slightly while he swims fast and harder when he crashes
 * back into the ground. Both are kept gentle, so it is not sickening in your own eyes.
 */
public final class BeamClient {
	/** When (his age on the client) each Beam's churn is next to sound (by entity id), how loud it is at his top speed, and how often it sounds (ticks). */
	private static final Map<Integer, Integer> NEXT = new ConcurrentHashMap<>();
	private static final float LOUD = 1.5F;
	private static final int EVERY = 6;
	/** How far the view widens at his top speed (a part of it), and how much the camera shakes at it, and as he crashes in from a breach (blocks); how fast the crash dies away (ticks). */
	private static final float WIDEN = 0.11F;
	private static final double RUMBLE = 0.009;
	private static final double CRASH = 0.07;
	private static final double CRASH_FADES = 5.0;

	private BeamClient() {
	}

	static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null) {
			NEXT.clear();
			return;
		}
		for (Entity e : world.getEntities()) {
			if (!(e instanceof BeamEntity beam) || beam.diving()) {
				continue;
			}
			// The churn of the ground, again every few ticks while he swims, louder and higher the faster he goes.
			float pace = beam.pace();
			float swim = beam.swim(1.0F);
			if (swim > 0.4F && pace > 0.08F && beam.age >= NEXT.getOrDefault(beam.getId(), 0)) {
				NEXT.put(beam.getId(), beam.age + EVERY);
				world.playSound(beam.getX(), beam.getY() + 2.0, beam.getZ(), ChainsawSounds.BEAM_SWIM, SoundCategory.PLAYERS,
						LOUD * MathHelper.clamp(0.25F + 0.75F * pace, 0.0F, 1.0F), 0.8F + 0.45F * pace, false);
			}
		}
		NEXT.keySet().removeIf(id -> !(world.getEntityById(id) instanceof BeamEntity));
	}

	static void clear() {
		NEXT.clear();
	}

	/** The Beam you are riding, if you are. */
	private static BeamEntity mount() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.player != null && client.player.getVehicle() instanceof BeamEntity beam ? beam : null;
	}

	/** How far your view is widened by his speed (1 for not at all). */
	public static float fov(float tickDelta) {
		BeamEntity beam = mount();
		if (beam == null) {
			return 1.0F;
		}
		return 1.0F + WIDEN * beam.pace() * beam.swim(tickDelta);
	}

	/** How hard your camera is shaken now by riding him (blocks): a low rumble while he swims fast, a jolt as he crashes back in. */
	public static double shake(float tickDelta) {
		BeamEntity beam = mount();
		if (beam == null) {
			return 0.0;
		}
		double rumble = RUMBLE * beam.pace() * beam.pace() * beam.swim(tickDelta);
		float since = beam.sinceLand(tickDelta);
		double crash = since >= 0.0F && since < 40.0F ? CRASH * Math.exp(-since / CRASH_FADES) : 0.0;
		return Math.max(rumble, crash);
	}
}
