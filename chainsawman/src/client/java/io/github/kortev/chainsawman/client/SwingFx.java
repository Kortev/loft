package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.form.Saw;
import io.github.kortev.chainsawman.form.SwingRhythm;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

/**
 * What a slash of the saws sounds and feels like round its swing ({@link SawPose} and {@link FirstPersonSaws} are how it looks): the engine
 * climbing through a flurry of slashes in a row and dropping back when they stop ({@link SwingRhythm#pitch}); the air torn by the blade, and
 * a deeper rip under the big cut; sparks and blood thrown off whatever the blade passes through, the way it was going; and, for your own,
 * a very small kick of the view the way the cut went (none when sneaking). For everyone slashing that this game sees: your own as you click,
 * others' as the server tells it.
 */
public final class SwingFx {
	/** When in a swing the blade is through what is in front (ticks): a sweep, the big cut (it comes down later). */
	private static final int BITE = 3;
	private static final int BIG_BITE = 4;
	/** How long after a slash the engine drops back if no other has come (ticks), and how many in a row it takes to have climbed. */
	private static final int REV_DOWN = Saw.SLASH_EVERY + 6;
	private static final int REV_DOWN_FROM = 3;
	/** The view's kick per cut (degrees): sideways the way a sweep goes, and down; the big cut's, straight down. */
	private static final float KICK_YAW = 0.7F;
	private static final float KICK_PITCH = 0.45F;
	private static final float BIG_KICK_PITCH = 1.2F;
	/** How far round the blade bites (blocks from the eyes), and how wide (the cosine of half the angle). */
	private static final double REACH = Saw.SLASH_REACH + 0.3;
	private static final double WIDE = 0.4;

	/** Each slasher's flurry: the last step, when, how many in a row, and the step before it (or -1). */
	private record Flurry(int step, long at, int count, int prev) {
	}

	private static final Map<UUID, Flurry> FLURRIES = new ConcurrentHashMap<>();
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.7F, 0.02F, 0.04F), 1.2F);
	private static final DustParticleEffect DARK = new DustParticleEffect(new Vector3f(0.4F, 0.0F, 0.02F), 1.5F);
	private static final Random RANDOM = Random.create();
	/** Your own last kick: when (world time), sideways and down (degrees). */
	private static long kickAt = -1000L;
	private static float kickYaw;
	private static float kickPitch;

	private SwingFx() {
	}

	/**
	 * A slash begun by {@code player} ({@code step} of the combo, 0 to 2) at {@code now}: yours as you click ({@code own}: the engine's
	 * snarl played here, and the view kicked), others' as the server says (the server plays their snarl).
	 */
	static void swung(ClientWorld world, PlayerEntity player, int step, long now, boolean own) {
		Flurry last = FLURRIES.get(player.getUuid());
		boolean fresh = last == null || SwingRhythm.fresh(last.at(), now);
		int count = SwingRhythm.flurry(fresh, last == null ? 0 : last.count());
		FLURRIES.put(player.getUuid(), new Flurry(step, now, count, fresh || last == null ? -1 : last.step()));
		float pitch = SwingRhythm.pitch(count);
		double y = player.getY() + 1.2;
		if (own) {
			world.playSound(player.getX(), y, player.getZ(), ChainsawSounds.SLASH, SoundCategory.PLAYERS, 1.0F, pitch, false);
			if (!player.isSneaking()) {
				int side = SwingRhythm.side(step);
				kickAt = now;
				// A sweep's blade goes from its own side across: the view nudged that way, and down; the big cut, straight down.
				kickYaw = -side * KICK_YAW;
				kickPitch = SwingRhythm.big(step) ? BIG_KICK_PITCH : KICK_PITCH;
			}
		}
		// The blade through the air, a little different each way round; under the big cut, a deeper rip.
		float air = (step == 1 ? 0.92F : 1.0F) * (0.96F + RANDOM.nextFloat() * 0.08F) * (0.9F + 0.1F * pitch);
		world.playSound(player.getX(), y, player.getZ(), ChainsawSounds.SLASH_AIR, SoundCategory.PLAYERS, 0.9F, air, false);
		if (SwingRhythm.big(step)) {
			world.playSound(player.getX(), y, player.getZ(), ChainsawSounds.SLASH_HEAVY, SoundCategory.PLAYERS, 1.0F,
					0.95F + 0.1F * (pitch - 1.0F), false);
		}
	}

	/** The server's word that your own slash, swung here, was another step (an arm gone since). */
	static void corrected(PlayerEntity player, int step) {
		Flurry last = FLURRIES.get(player.getUuid());
		if (last != null) {
			FLURRIES.put(player.getUuid(), new Flurry(step, last.at(), last.count(), last.prev()));
		}
	}

	/** The step before {@code player}'s last slash, in the same flurry, or -1: the arm that swung then is still carrying through. */
	public static int prev(PlayerEntity player) {
		Flurry last = FLURRIES.get(player.getUuid());
		return last == null ? -1 : last.prev();
	}

	/** How many slashes in a row {@code player}'s last made, or 0. */
	public static int count(PlayerEntity player) {
		Flurry last = FLURRIES.get(player.getUuid());
		return last == null ? 0 : last.count();
	}

	/** Your view's kick now from your own last cut: {sideways, down} in degrees (a quick nudge, gone in a few ticks). */
	public static float[] kick(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) {
			return new float[] {0.0F, 0.0F};
		}
		float t = client.world.getTime() - kickAt + tickDelta;
		if (t < 0.0F || t > 10.0F) {
			return new float[] {0.0F, 0.0F};
		}
		// In over a tick, out over a few.
		float k = t < 1.0F ? t : (float) Math.exp(-(t - 1.0F) / 1.8F);
		return new float[] {kickYaw * k, kickPitch * k};
	}

	static void tick(ClientWorld world, long now) {
		for (AbstractClientPlayerEntity player : world.getPlayers()) {
			Flurry f = FLURRIES.get(player.getUuid());
			if (f == null) {
				continue;
			}
			long age = now - f.at();
			if (age == (SwingRhythm.big(f.step()) ? BIG_BITE : BITE)) {
				bite(world, player, f.step());
			}
			if (age == REV_DOWN && f.count() >= REV_DOWN_FROM && ClientForms.on(player)) {
				// The flurry over: the engine falling back from as high as it had climbed.
				world.playSound(player.getX(), player.getY() + 1.2, player.getZ(),
						ChainsawSounds.REV_DOWN, SoundCategory.PLAYERS, 0.8F, SwingRhythm.pitch(f.count()), false);
			}
		}
		FLURRIES.entrySet().removeIf(e -> now - e.getValue().at() > Saw.COMBO_GAP * 3);
	}

	/**
	 * The blade through whatever is in front of {@code player} as the swing passes it: on each, where the blade goes in, a burst of sparks
	 * off the teeth and blood thrown the way the cut went (across for a sweep, down for the big cut).
	 */
	private static void bite(ClientWorld world, PlayerEntity player, int step) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0F);
		Vec3d right = Vec3d.fromPolar(0.0F, player.getYaw() + 90.0F);
		int side = SwingRhythm.side(step);
		// The way the blade is going as it bites: a sweep from its own side across (and down a little), the big cut down.
		Vec3d way = side == 0 ? new Vec3d(0.0, -1.0, 0.0).add(look.multiply(0.4)) : right.multiply(-side).add(0.0, -0.35, 0.0);
		way = way.normalize();
		for (Entity e : world.getOtherEntities(player, player.getBoundingBox().expand(REACH + 1.0),
				e -> e instanceof LivingEntity l && l.isAlive() && !(e instanceof ArmorStandEntity) && !e.isSpectator())) {
			Box b = e.getBoundingBox();
			Vec3d near = new Vec3d(MathHelper.clamp(eye.x, b.minX, b.maxX), MathHelper.clamp(eye.y, b.minY, b.maxY), MathHelper.clamp(eye.z, b.minZ, b.maxZ));
			if (near.distanceTo(eye) > REACH || b.getCenter().subtract(eye).normalize().dotProduct(look) < WIDE && near.subtract(eye).normalize().dotProduct(look) < WIDE) {
				continue;
			}
			// On its near face, at the height the blade crosses it (the chest, a little lower for the big cut).
			Vec3d to = eye.subtract(b.getCenter()).multiply(1.0, 0.0, 1.0);
			Vec3d face = to.lengthSquared() < 1.0E-4 ? b.getCenter() : b.getCenter().add(to.normalize().multiply(Math.min(b.getLengthX(), b.getLengthZ()) * 0.5));
			double y = MathHelper.clamp(player.getY() + (side == 0 ? 0.9 : 1.15), b.minY + 0.15, b.maxY - 0.1);
			Vec3d at = new Vec3d(face.x, y, face.z);
			spray(world, at, way, e instanceof PlayerEntity || SwingRhythm.big(step) ? 1.5F : 1.0F);
		}
	}

	/** A bite's burst at {@code at}: sparks off the teeth every way, blood thrown along {@code way}. */
	private static void spray(ClientWorld world, Vec3d at, Vec3d way, float size) {
		int sparks = (int) (7 * size);
		for (int i = 0; i < sparks; i++) {
			Vec3d v = way.multiply(0.25 + RANDOM.nextDouble() * 0.35).add((RANDOM.nextDouble() - 0.5) * 0.5, RANDOM.nextDouble() * 0.35,
					(RANDOM.nextDouble() - 0.5) * 0.5);
			world.addParticle(i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.CRIT, at.x, at.y, at.z, v.x, v.y, v.z);
		}
		int drops = (int) (8 * size);
		for (int i = 0; i < drops; i++) {
			Vec3d v = way.multiply(0.2 + RANDOM.nextDouble() * 0.45).add((RANDOM.nextDouble() - 0.5) * 0.2, RANDOM.nextDouble() * 0.2,
					(RANDOM.nextDouble() - 0.5) * 0.2);
			world.addParticle(i % 3 == 0 ? DARK : BLOOD, at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	static void clear() {
		FLURRIES.clear();
		kickAt = -1000L;
	}
}
