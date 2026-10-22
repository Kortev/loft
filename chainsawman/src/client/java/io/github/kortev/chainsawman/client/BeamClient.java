package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.BeamEntity;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.form.Beam;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * What every client shows and hears of Beam besides his body ({@link BeamRenderer}): the ground he swims through thrown up
 * at his fin and his bow every tick he moves (the blocks it is made of, as particles), and the low churning of earth that
 * loops while he swims, louder the faster he goes.
 */
public final class BeamClient {
	private static final Random RANDOM = Random.create();
	/** The swimming loop on each Beam (by entity id). */
	private static final Map<Integer, SoundInstance> LOOPS = new ConcurrentHashMap<>();
	/** The loop's volume at his top speed, and the least it is while he is going at all. */
	private static final float LOUD = 1.1F;
	private static final float QUIET = 0.35F;

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
			spray(world, beam);
		}
		LOOPS.keySet().removeIf(id -> !(world.getEntityById(id) instanceof BeamEntity));
	}

	/** The ground thrown up: at his bow ahead of him and behind his fin, more the faster he goes. */
	private static void spray(ClientWorld world, BeamEntity beam) {
		float pace = beam.pace();
		if (pace < 0.08F && !beam.diving()) {
			return;
		}
		ParticleEffect effect = ground(world, beam);
		Vec3d ahead = beam.heading();
		Vec3d side = new Vec3d(ahead.z, 0.0, -ahead.x);
		int bits = 1 + (int) (pace * 2.5F);
		// (Not at the bow when it is you riding him and looking out of your own eyes: it would fill the view.)
		MinecraftClient client = MinecraftClient.getInstance();
		boolean yours = client.player != null && client.player.getVehicle() == beam && !client.gameRenderer.getCamera().isThirdPerson();
		for (int i = 0; i < bits; i++) {
			// At the bow, flung up and out to the sides.
			double across = (RANDOM.nextDouble() - 0.5) * 0.9;
			Vec3d at = beam.getPos().add(ahead.multiply(Beam.MOUTH + 0.1)).add(side.multiply(across));
			if (!yours) {
				world.addParticle(effect, at.x, beam.getY() + 0.1, at.z, side.x * across * 0.12 + ahead.x * 0.04, 0.12 + RANDOM.nextDouble() * 0.12,
						side.z * across * 0.12 + ahead.z * 0.04);
			}
			// Behind the fin, thrown back and up.
			Vec3d wake = beam.getPos().add(ahead.multiply(-0.6 - RANDOM.nextDouble() * 0.5)).add(side.multiply((RANDOM.nextDouble() - 0.5) * 0.4));
			world.addParticle(effect, wake.x, beam.getY() + 0.3, wake.z, -ahead.x * 0.08, 0.1 + RANDOM.nextDouble() * 0.08, -ahead.z * 0.08);
		}
	}

	/** The block of the ground he is in, as particles (water splashes). */
	private static ParticleEffect ground(ClientWorld world, BeamEntity beam) {
		for (int down = 1; down <= 3; down++) {
			BlockState state = world.getBlockState(BlockPos.ofFloored(beam.getX(), beam.getY() - down + 0.5, beam.getZ()));
			if (!state.getFluidState().isEmpty()) {
				return ParticleTypes.SPLASH;
			}
			if (!state.isAir() && state.getRenderType() != net.minecraft.block.BlockRenderType.INVISIBLE) {
				return new BlockStateParticleEffect(ParticleTypes.BLOCK, state);
			}
		}
		return new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DIRT.getDefaultState());
	}

	static void clear() {
		LOOPS.clear();
	}

	/** The churning of earth, a loop on him: louder as he speeds up, gone when he dives. */
	private static final class SwimSound extends MovingSoundInstance {
		private final BeamEntity beam;

		SwimSound(BeamEntity beam) {
			super(ChainsawSounds.BEAM_SWIM, SoundCategory.PLAYERS, Random.create());
			this.beam = beam;
			this.repeat = true;
			this.repeatDelay = 0;
			this.volume = QUIET;
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
			this.volume = QUIET + (LOUD - QUIET) * pace;
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
