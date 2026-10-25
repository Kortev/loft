package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.BeamEntity;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.form.Beam;
import io.github.kortev.chainsawman.network.FormEventPayload;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * What every client hears of Beam besides what he does once ({@link BeamRenderer} draws him): the churning of the ground
 * while he swims through it, louder and higher the faster he goes, silent when he stands; and what the rider feels
 * of his speed: the view widening a little, the camera shaking slightly while he swims fast and harder when he crashes
 * back into the ground. Both are kept gentle, so it is not sickening in your own eyes.
 */
public final class BeamClient {
	/** When (his age on the client) each Beam's churn is next to sound (by entity id), how loud it is at his top speed, and how often it sounds (ticks). */
	private static final Map<Integer, Integer> NEXT = new ConcurrentHashMap<>();
	/** Which step of his gait each Beam is on (by entity id): a footfall sounds and kicks up the ground each time it changes. */
	private static final Map<Integer, Integer> STEPS = new ConcurrentHashMap<>();
	private static final float LOUD = 1.5F;
	private static final int EVERY = 6;
	/** How far the view widens at his top speed (a part of it), and how much the camera shakes at it, and as he crashes in from a breach (blocks); how fast the crash dies away (ticks). */
	private static final float WIDEN = 0.11F;
	private static final double RUMBLE = 0.009;
	private static final double CRASH = 0.07;
	private static final double CRASH_FADES = 5.0;
	/**
	 * Hits that land hard (phase 2). By their level (1 to 4): how hard the camera is shaken (blocks, fading with the time constant
	 * {@link #HIT_FADES} in ticks) and how many frames the picture is held still (a hit-stop: your view and everything in it, on your
	 * client alone, the server never stops). The big ones (a flash) get the anime's impact frames on the first two held frames, and a
	 * word lettered over the screen.
	 */
	private static final double[] HIT_SHAKE = {0.0, 0.03, 0.055, 0.1, 0.16};
	private static final int[] HIT_HOLD = {0, 1, 1, 3, 4};
	private static final double HIT_FADES = 3.0;
	/**
	 * Your eyes on his back (phase 2): your view is raised and drawn back along him, so his head, eyes and mouth are ahead of you in the
	 * lower part of the picture and the ground rushes past beyond them, not a flat slab of his back.
	 */
	public static double eyeUp = 1.2;
	public static double eyeBack = 3.5;
	/** And tilted this many degrees down (the crosshair is where your look says it is: only a little). */
	public static float eyeDown = 6.0F;

	private record Hit(long at, double size) {
	}

	private static final List<Hit> HITS = new CopyOnWriteArrayList<>();
	/** The hit-stop: frames left to hold still, how many have been held for this hit, which one is being drawn (-1 when none), and whether this hit has impact frames. */
	private static volatile int holdLeft;
	private static int heldCount;
	private static volatile int heldNow = -1;
	private static boolean flash;

	private BeamClient() {
	}

	static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null) {
			NEXT.clear();
			STEPS.clear();
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
		for (Entity e : world.getEntities()) {
			if (e instanceof BeamEntity beam && !beam.diving()) {
				footfalls(world, beam);
			}
		}
		STEPS.keySet().removeIf(id -> !(world.getEntityById(id) instanceof BeamEntity));
	}

	/** On his legs, each time a pair of feet comes down (twice in a cycle of his gait) a heavy thud and the ground kicked up under them. */
	private static void footfalls(ClientWorld world, BeamEntity beam) {
		int step = MathHelper.floor(beam.walk(1.0F) / MathHelper.PI);
		Integer last = STEPS.put(beam.getId(), step);
		float walking = beam.gait() * (1.0F - Math.max(beam.swim(1.0F), beam.air(1.0F)));
		if (last == null || last == step || walking < 0.35F) {
			return;
		}
		Vec3d ahead = beam.heading();
		Vec3d left = new Vec3d(ahead.z, 0.0, -ahead.x);
		double side = (step & 1) == 0 ? 1.0 : -1.0;
		// (The pair that lands: the front foot on its side, the back foot on the other.)
		double[][] feet = {{1.0, side}, {-1.2, -side}};
		BlockPos ground = BlockPos.ofFloored(beam.getX(), beam.getY() - 0.2, beam.getZ());
		BlockState state = world.getBlockState(ground);
		if (state.isAir()) {
			state = world.getBlockState(ground.down());
		}
		for (double[] foot : feet) {
			double fx = beam.getX() + ahead.x * foot[0] * 1.4 + left.x * foot[1] * 1.5;
			double fz = beam.getZ() + ahead.z * foot[0] * 1.4 + left.z * foot[1] * 1.5;
			if (!state.isAir()) {
				for (int i = 0; i < 4; i++) {
					world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), fx, beam.getY() + 0.1, fz, (world.random.nextDouble() - 0.5) * 0.25,
							0.12 + world.random.nextDouble() * 0.15, (world.random.nextDouble() - 0.5) * 0.25);
				}
			}
		}
		world.playSound(beam.getX(), beam.getY(), beam.getZ(), ChainsawSounds.BEAM_STEP, SoundCategory.PLAYERS, 1.1F * walking,
				0.65F + 0.15F * world.random.nextFloat(), false);
	}

	static void clear() {
		NEXT.clear();
		STEPS.clear();
		HITS.clear();
		holdLeft = 0;
		heldCount = 0;
		heldNow = -1;
		flash = false;
	}

	/** One of his hits landing, as the server tells it ({@link FormEventPayload#BEAM_HIT}): the screen shakes, holds still a few frames, and for the big ones flashes and is lettered. */
	public static void hit(int code, long now) {
		int level = MathHelper.clamp(code & 7, 1, 4);
		HITS.removeIf(h -> now - h.at() > 40);
		HITS.add(new Hit(now, HIT_SHAKE[level]));
		if (HIT_HOLD[level] > holdLeft) {
			holdLeft = HIT_HOLD[level];
			heldCount = 0;
		}
		if ((code & FormEventPayload.BEAM_FLASH) != 0) {
			flash = true;
			Sfx.show((code & FormEventPayload.BEAM_CHOP) != 0 ? Sfx.CHOP : Sfx.KILL, 0.15F, 14, now);
		}
	}

	/**
	 * Called at the start of every frame drawn: whether this one is to be held still (the clock of the world, the particles, the mobs and
	 * your own camera are not advanced). Per player: it is your own client only.
	 */
	public static boolean hold() {
		if (holdLeft > 0) {
			holdLeft--;
			heldNow = heldCount++;
			return true;
		}
		heldNow = -1;
		flash = false;
		return false;
	}

	/** The impact frame showing now, if any: 1 red and black, 2 its negative; 0 none. */
	public static int impactStyle() {
		return flash && heldNow >= 0 && heldNow < 2 ? 1 + heldNow : 0;
	}

	/**
	 * How far your eyes are moved from where the game puts them while you ride him in first person: up and back along him (world space),
	 * or null when you do not ride him.
	 */
	public static Vec3d eyes(float tickDelta) {
		BeamEntity beam = mount();
		if (beam == null) {
			return null;
		}
		double yaw = Math.toRadians(MathHelper.lerpAngleDegrees(tickDelta, beam.prevYaw, beam.getYaw()));
		return new Vec3d(Math.sin(yaw) * eyeBack, eyeUp, -Math.cos(yaw) * eyeBack);
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
		double hits = 0.0;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null) {
			long now = client.world.getTime();
			for (Hit h : HITS) {
				double age = now - h.at() + tickDelta;
				if (age >= 0.0 && age < 40.0) {
					hits = Math.max(hits, h.size() * Math.exp(-age / HIT_FADES));
				}
			}
		}
		BeamEntity beam = mount();
		if (beam == null) {
			return hits;
		}
		double rumble = RUMBLE * beam.pace() * beam.pace() * beam.swim(tickDelta);
		float since = beam.sinceLand(tickDelta);
		double crash = since >= 0.0F && since < 40.0F ? CRASH * Math.exp(-since / CRASH_FADES) : 0.0;
		return Math.max(hits, Math.max(rumble, crash));
	}
}
