package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.BeamEntity;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.form.Beam;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;

/**
 * What every client hears of Beam besides what he does once ({@link BeamRenderer} draws him): the low pounding of his
 * feet that loops while he walks, louder the faster he goes, silent when he stands.
 */
public final class BeamClient {
	/** The walking loop on each Beam (by entity id). */
	private static final Map<Integer, SoundInstance> LOOPS = new ConcurrentHashMap<>();
	/** The loop's volume at his top speed. */
	private static final float LOUD = 1.0F;

	private BeamClient() {
	}

	static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null) {
			LOOPS.clear();
			return;
		}
		for (Entity e : world.getEntities()) {
			if (!(e instanceof BeamEntity beam) || beam.diving() && beam.sinceDive(0.0F) > Beam.DIVE_TICKS) {
				continue;
			}
			SoundInstance playing = LOOPS.get(beam.getId());
			if (playing == null || !client.getSoundManager().isPlaying(playing)) {
				SwimSound loop = new SwimSound(beam);
				LOOPS.put(beam.getId(), loop);
				client.getSoundManager().play(loop);
			}
		}
		LOOPS.keySet().removeIf(id -> !(world.getEntityById(id) instanceof BeamEntity));
	}

	static void clear() {
		LOOPS.clear();
	}

	/** The pounding of his feet, a loop on him: louder as he speeds up, gone when he dives. */
	private static final class SwimSound extends MovingSoundInstance {
		private final BeamEntity beam;

		SwimSound(BeamEntity beam) {
			super(ChainsawSounds.BEAM_SWIM, SoundCategory.PLAYERS, Random.create());
			this.beam = beam;
			this.repeat = true;
			this.repeatDelay = 0;
			this.volume = 0.0F;
			this.attenuationType = SoundInstance.AttenuationType.LINEAR;
			follow();
		}

		@Override
		public void tick() {
			if (beam.isRemoved() || beam.diving()) {
				setDone();
				return;
			}
			float pace = beam.pace();
			this.volume = LOUD * pace;
			this.pitch = 0.85F + 0.35F * pace;
			follow();
		}

		private void follow() {
			x = beam.getX();
			y = beam.getY();
			z = beam.getZ();
		}
	}
}
