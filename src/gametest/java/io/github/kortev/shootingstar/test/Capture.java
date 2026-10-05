package io.github.kortev.shootingstar.test;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.client.gap.VoidMusic;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.DoubleFunction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.resource.Resource;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Deterministic video capture for the self test. While active, the client renders every frame at
 * an exact game time (30 frames to 20 ticks), the integrated server is held in lockstep with the
 * client's ticks, and each frame is piped to ffmpeg. Sounds the client plays are logged with their
 * time so the workflow can mix the mod's audio into the video. Real frame rate does not matter, so
 * a software renderer still produces a smooth video.
 */
public final class Capture {
	/** Game ticks per video frame. */
	public static final double STEP = 20.0 / 30.0;
	private static final long SERVER_WAIT_LIMIT_NANOS = TimeUnit.SECONDS.toNanos(60);

	public record Pose(double x, double y, double z, float yaw, float pitch) {
	}

	/** A sound in the log: when, which file and how loud; {@code offset} seconds into the file, rising over {@code fade} seconds. */
	private record Played(double time, String file, float gain, float pitch, double offset, double fade) {
	}

	private static volatile boolean active;
	/** Render time of the frame being drawn, in ticks since the capture started. */
	private static double clock;
	private static long clientTicks;
	private static volatile long clientTicksShared;
	private static final AtomicLong SERVER_TICKS = new AtomicLong();
	private static boolean recordFrame;
	private static long frames;
	private static long stalls;
	@Nullable
	private static Encoder encoder;
	private static Path folder;
	private static final List<Played> SOUNDS = new ArrayList<>();
	private static final Map<Identifier, String> SOUND_FILES = new HashMap<>();
	/** Optional camera override, as a function of capture time in ticks. */
	@Nullable
	public static volatile DoubleFunction<Pose> camera;

	private Capture() {
	}

	public static boolean active() {
		return active;
	}

	/** Capture time of the frame being drawn, in ticks. */
	public static double time() {
		return clock;
	}

	/** Starts capturing into {@code dir} (render thread). */
	public static void start(MinecraftClient client, Path dir) {
		if (active) {
			return;
		}
		folder = dir;
		try {
			Files.createDirectories(dir.resolve("sounds"));
			Framebuffer fb = client.getFramebuffer();
			encoder = new Encoder(dir.resolve("video.mp4"), dir.resolve("ffmpeg.log"), fb.textureWidth, fb.textureHeight);
		} catch (IOException e) {
			ShootingStar.LOGGER.error("[capture] could not start ffmpeg, capturing without video", e);
			encoder = null;
		}
		clock = client.getRenderTickCounter().getTickDelta(true);
		clientTicks = 0;
		clientTicksShared = 0;
		SERVER_TICKS.set(0);
		frames = 0;
		stalls = 0;
		SOUNDS.clear();
		active = true;
		ShootingStar.LOGGER.info("[capture] started into {}", dir);
	}

	/** Stops capturing and finishes the video (render thread). */
	public static void stop() {
		if (!active) {
			return;
		}
		active = false;
		camera = null;
		if (encoder != null) {
			encoder.finish();
			encoder = null;
		}
		writeSounds();
		ShootingStar.LOGGER.info("[capture] stopped after {} frames ({} s), {} stalled frames, {} sounds", frames,
				String.format(Locale.ROOT, "%.1f", frames / 30.0), stalls, SOUNDS.size());
	}

	// --- clock -----------------------------------------------------------------------------

	/**
	 * Called at the start of each frame instead of the real-time tick counter. Returns how many client
	 * ticks to run and puts the frame's partial tick in {@code delta[0]}, or returns -1 when inactive.
	 */
	public static int beginFrame(float[] delta) {
		if (!active) {
			return -1;
		}
		double next = clock + STEP;
		long need = (long) Math.floor(next) - clientTicks;
		// The client may run tick c+1 only once the server has finished tick c+2, so every packet for it is in.
		if (need > 0 && SERVER_TICKS.get() < clientTicks + need + 1) {
			recordFrame = false;
			stalls++;
			delta[0] = (float) (clock - Math.floor(clock));
			// Give the server thread the CPU while we wait for it.
			LockSupport.parkNanos(2_000_000L);
			return 0;
		}
		clock = next;
		clientTicks += need;
		clientTicksShared = clientTicks;
		recordFrame = true;
		delta[0] = (float) (clock - Math.floor(clock));
		return (int) need;
	}

	/** Server thread, start of each tick: never run more than two ticks ahead of the client. */
	public static void serverTickStart() {
		long start = System.nanoTime();
		while (active && SERVER_TICKS.get() - clientTicksShared > 1) {
			LockSupport.parkNanos(200_000L);
			if (System.nanoTime() - start > SERVER_WAIT_LIMIT_NANOS) {
				ShootingStar.LOGGER.error("[capture] the client stopped ticking; giving up the lockstep");
				active = false;
				return;
			}
		}
	}

	public static void serverTickEnd() {
		if (active) {
			SERVER_TICKS.incrementAndGet();
		}
	}

	/** Render thread, just before the frame is shown: hands the finished frame to the encoder. */
	public static void endFrame(MinecraftClient client) {
		if (!active || !recordFrame) {
			return;
		}
		recordFrame = false;
		frames++;
		if (frames % 150 == 0) {
			ShootingStar.LOGGER.info("[capture] {} frames ({} s of video), {} stalls", frames,
					String.format(Locale.ROOT, "%.1f", frames / 30.0), stalls);
		}
		if (encoder == null) {
			return;
		}
		Framebuffer fb = client.getFramebuffer();
		if (fb.textureWidth != encoder.width || fb.textureHeight != encoder.height) {
			ShootingStar.LOGGER.warn("[capture] window resized to {}x{}, frame dropped", fb.textureWidth, fb.textureHeight);
			return;
		}
		ByteBuffer pixels = encoder.borrow();
		RenderSystem.bindTexture(fb.getColorAttachment());
		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
		GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
		GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
		GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
		GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
		RenderSystem.bindTexture(0);
		encoder.submit(pixels);
	}

	// --- sound -----------------------------------------------------------------------------

	/** Logs a sound the client starts (or schedules {@code delay} ticks ahead). */
	public static void sound(SoundInstance instance, int delay) {
		if (!active) {
			return;
		}
		SoundCategory category = instance.getCategory();
		// Minecraft's own music would differ run to run; the void's song is the mod's and goes in.
		boolean song = instance.getId().equals(VoidMusic.ID);
		if (!song && (category == SoundCategory.MUSIC || category == SoundCategory.RECORDS || category == SoundCategory.AMBIENT
				|| category == SoundCategory.VOICE)) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		WeightedSoundSet set = instance.getSoundSet(client.getSoundManager());
		Sound sound = instance.getSound();
		if (set == null || sound == null) {
			return;
		}
		String file = extract(client, sound.getLocation());
		if (file == null) {
			return;
		}
		if (song) {
			// It starts silent and fades itself up, so log it at full with its start point and fade.
			SOUNDS.add(new Played((clock + delay) / 20.0, file, 1.0F, 1.0F, VoidMusic.FROM_SECONDS, VoidMusic.FADE_SECONDS));
			return;
		}
		float volume = instance.getVolume();
		float gain = MathHelper.clamp(volume, 0.0F, 1.0F);
		if (instance.getAttenuationType() == SoundInstance.AttenuationType.LINEAR && !instance.isRelative()) {
			Vec3d listener = client.gameRenderer.getCamera().getPos();
			double distance = listener.distanceTo(new Vec3d(instance.getX(), instance.getY(), instance.getZ()));
			double range = sound.getAttenuation() * Math.max(1.0F, volume);
			gain *= (float) MathHelper.clamp(1.0 - distance / range, 0.0, 1.0);
		}
		if (gain <= 0.01F) {
			return;
		}
		SOUNDS.add(new Played((clock + delay) / 20.0, file, gain, instance.getPitch(), 0.0, 0.0));
	}

	@Nullable
	private static String extract(MinecraftClient client, Identifier location) {
		String known = SOUND_FILES.get(location);
		if (known != null) {
			return known;
		}
		Optional<Resource> resource = client.getResourceManager().getResource(location);
		if (resource.isEmpty()) {
			return null;
		}
		String name = (location.getNamespace() + "_" + location.getPath()).replaceAll("[^a-zA-Z0-9_.-]", "_");
		try (InputStream in = resource.get().getInputStream()) {
			Files.copy(in, folder.resolve("sounds").resolve(name), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			ShootingStar.LOGGER.warn("[capture] could not extract {}", location, e);
			return null;
		}
		SOUND_FILES.put(location, name);
		return name;
	}

	private static void writeSounds() {
		StringBuilder json = new StringBuilder("[\n");
		for (int i = 0; i < SOUNDS.size(); i++) {
			Played p = SOUNDS.get(i);
			json.append(String.format(Locale.ROOT,
					"  {\"time\": %.4f, \"file\": \"%s\", \"gain\": %.4f, \"pitch\": %.4f, \"offset\": %.3f, \"fade\": %.3f}%s\n",
					p.time(), p.file(), p.gain(), p.pitch(), p.offset(), p.fade(), i + 1 < SOUNDS.size() ? "," : ""));
		}
		json.append("]\n");
		try {
			Files.writeString(folder.resolve("sounds.json"), json.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ShootingStar.LOGGER.warn("[capture] could not write the sound log", e);
		}
	}

	// --- encoder ---------------------------------------------------------------------------

	/** Raw RGBA frames into an ffmpeg process on a background thread. */
	private static final class Encoder {
		final int width;
		final int height;
		private final Process process;
		private final WritableByteChannel out;
		private final BlockingQueue<ByteBuffer> queue = new ArrayBlockingQueue<>(4);
		private final BlockingQueue<ByteBuffer> pool = new ArrayBlockingQueue<>(6);
		private final Thread writer;
		private static final ByteBuffer END = ByteBuffer.allocate(0);
		private volatile boolean failed;

		Encoder(Path file, Path log, int width, int height) throws IOException {
			this.width = width;
			this.height = height;
			process = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "warning", "-f", "rawvideo", "-pix_fmt", "rgba",
					"-s", width + "x" + height, "-r", "30", "-i", "-", "-vf", "vflip", "-c:v", "libx264", "-preset", "veryfast",
					"-crf", "12", "-pix_fmt", "yuv420p", file.toString())
					.redirectErrorStream(true)
					.redirectOutput(log.toFile())
					.start();
			OutputStream stream = process.getOutputStream();
			out = Channels.newChannel(stream);
			for (int i = 0; i < 6; i++) {
				pool.add(BufferUtils.createByteBuffer(width * height * 4));
			}
			writer = new Thread(this::write, "shootingstar-capture-writer");
			writer.setDaemon(true);
			writer.start();
		}

		ByteBuffer borrow() {
			try {
				ByteBuffer buffer = pool.take();
				buffer.clear();
				return buffer;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return BufferUtils.createByteBuffer(width * height * 4);
			}
		}

		void submit(ByteBuffer pixels) {
			if (failed) {
				pool.offer(pixels);
				return;
			}
			try {
				queue.put(pixels);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		private void write() {
			try {
				while (true) {
					ByteBuffer buffer = queue.take();
					if (buffer == END) {
						break;
					}
					buffer.rewind();
					buffer.limit(width * height * 4);
					try {
						while (buffer.hasRemaining()) {
							out.write(buffer);
						}
					} catch (IOException e) {
						if (!failed) {
							ShootingStar.LOGGER.error("[capture] ffmpeg stopped taking frames", e);
						}
						failed = true;
					}
					pool.offer(buffer);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		void finish() {
			try {
				queue.put(END);
				writer.join(TimeUnit.MINUTES.toMillis(2));
				out.close();
				boolean done = process.waitFor(10, TimeUnit.MINUTES);
				ShootingStar.LOGGER.info("[capture] ffmpeg finished: {}", done ? "exit " + process.exitValue() : "timed out");
			} catch (IOException | InterruptedException e) {
				ShootingStar.LOGGER.error("[capture] could not finish the video", e);
			}
		}
	}
}
