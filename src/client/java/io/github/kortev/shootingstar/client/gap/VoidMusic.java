package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.ShootingStar;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The song in the black: Mice on Venus, Minecraft's own copy of it, coming in once the camera is back with the shooter.
 * It starts 35 seconds into the track and fades up over five, so it is at full volume at the 40 second mark. It plays
 * as music, under the music slider, and runs on to its end.
 */
public final class VoidMusic extends MovingSoundInstance {
	public static final Identifier ID = ShootingStar.id("gap.void_music");
	/** Where in the track it starts, in seconds. */
	public static final double FROM_SECONDS = 35.0;
	/** How long it takes to come up to full volume, in seconds. */
	public static final double FADE_SECONDS = 5.0;
	private static final int FADE_TICKS = (int) Math.round(FADE_SECONDS * 20.0);

	@Nullable
	private static VoidMusic current;
	private int age;

	private VoidMusic() {
		super(SoundEvent.of(ID), SoundCategory.MUSIC, SoundInstance.createRandom());
		relative = true;
		attenuationType = AttenuationType.NONE;
		repeat = false;
		volume = 0.0F;
	}

	public static void start() {
		stop();
		current = new VoidMusic();
		MinecraftClient.getInstance().getSoundManager().play(current);
	}

	public static void stop() {
		if (current != null) {
			MinecraftClient.getInstance().getSoundManager().stop(current);
			current = null;
		}
	}

	/** True from the moment it is started until the track runs out or it is stopped. */
	public static boolean playing() {
		if (current == null) {
			return false;
		}
		// Give the sound engine a moment to pick it up before asking whether it is still going.
		if (current.age < 10 || MinecraftClient.getInstance().getSoundManager().isPlaying(current)) {
			return true;
		}
		current = null;
		return false;
	}

	@Override
	public void tick() {
		age++;
		// A quadratic rise, which sounds even where a straight one would rush the first second.
		float k = Math.min(1.0F, age / (float) FADE_TICKS);
		volume = k * k;
	}

	/** It starts silent, and the sound engine would otherwise drop a sound that starts at no volume. */
	@Override
	public boolean shouldAlwaysPlay() {
		return true;
	}

	@Override
	public CompletableFuture<AudioStream> getAudioStream(SoundLoader loader, Identifier id, boolean repeatInstantly) {
		return loader.loadStreamed(id, repeatInstantly).thenApply(stream -> new Skipped(stream, FROM_SECONDS));
	}

	/** The track from a point in, decoded and dropped up to there on the loader's thread rather than the sound engine's. */
	private static final class Skipped implements AudioStream {
		private final AudioStream stream;
		@Nullable
		private ByteBuffer rest;

		Skipped(AudioStream stream, double seconds) {
			this.stream = stream;
			AudioFormat format = stream.getFormat();
			int frame = Math.max(1, format.getFrameSize());
			long skip = (long) (seconds * format.getSampleRate()) * frame;
			try {
				while (skip > 0) {
					ByteBuffer buffer = stream.read((int) Math.min(skip, 1 << 16));
					int n = buffer.remaining();
					if (n == 0) {
						break;
					}
					if (n > skip) {
						buffer.position(buffer.position() + (int) skip);
						rest = buffer;
						break;
					}
					skip -= n;
				}
			} catch (IOException e) {
				ShootingStar.LOGGER.warn("Could not skip into the void's song", e);
			}
		}

		@Override
		public AudioFormat getFormat() {
			return stream.getFormat();
		}

		@Override
		public ByteBuffer read(int size) throws IOException {
			if (rest != null) {
				ByteBuffer buffer = rest;
				rest = null;
				return buffer;
			}
			return stream.read(size);
		}

		@Override
		public void close() throws IOException {
			stream.close();
		}
	}
}
