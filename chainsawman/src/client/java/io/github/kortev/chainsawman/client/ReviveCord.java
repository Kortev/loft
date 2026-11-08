package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.form.Downed;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A friend pulling the cord of someone who is down ({@link Downed#revive}), as everyone sees it. The friend's right arm
 * reaches to the downed player's chest and takes the handle, then draws back and up with it over {@link Downed#FEED}
 * ticks while the cord stretches out of the chest toward their hand (slack, then taut and humming), the engine coughing
 * smoke from the downed player's back at each sputter of its sound, blood dripping from the chest. Letting go early, the
 * cord slides back in; at the yank it snaps back and blood sprays from where the arms and legs will grow.
 *
 * <p>The server says only when (a grip, a let-go, a yank: {@code FormEventPayload.REVIVE}); the rest is worked out here
 * from the time since, the same on every client.
 */
public final class ReviveCord {
	/** One cord being pulled: the friend (entity id), when they took hold, when it ended (world time), and how (see below). */
	public record Pull(int puller, long from, long ended, int how) {
	}

	public static final int PULLING = 0;
	public static final int LET_GO = 1;
	public static final int YANKED = 2;
	/** How long the cord takes to slide back in, to snap back (ticks), how long the friend's arm takes to fall, and how long a finished pull is kept. */
	public static final int SLIDE = 6;
	public static final int SNAP = 2;
	/** The tugs: when each comes (ticks into the pull), how long the cord takes to shoot out and to snap back, and how long it is out at the top (blocks, root to fist). */
	public static final int[] TUGS = {20, 40};
	private static final float[] TUG_LENGTH = {0.4F, 0.5F};
	public static final int OUT = 3;
	public static final int BACK = 2;
	public static final float YANK_LENGTH = 0.8F;
	private static final int FALL = 8;
	private static final int KEEP = 20;
	/** How far the hand is from the shoulder (blocks): the arm's length to the grip. */
	private static final double ARM = 0.62;

	private static final Map<UUID, Pull> PULLS = new ConcurrentHashMap<>();
	private static final Random RANDOM = Random.create();
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.62F, 0.02F, 0.04F), 1.2F);
	private static final DustParticleEffect DARK = new DustParticleEffect(new Vector3f(0.35F, 0.0F, 0.02F), 1.5F);
	private static final ParticleEffect GORE = new BlockStateParticleEffect(ParticleTypes.BLOCK, net.minecraft.block.Blocks.REDSTONE_BLOCK.getDefaultState());
	private static long yankedAt = -1000;

	private ReviveCord() {
	}

	/** What the server says: {@code value} the friend's entity id (a grip), 0 (let go) or -1 (the yank), about {@code down}. */
	static void event(ClientWorld world, UUID down, int value, long now) {
		Pull was = PULLS.get(down);
		if (value > 0) {
			PULLS.put(down, new Pull(value, now, 0, PULLING));
		} else if (was != null && value == 0 && was.how() == PULLING) {
			PULLS.put(down, new Pull(was.puller(), was.from(), now, LET_GO));
		} else if (value < 0) {
			PULLS.put(down, new Pull(was != null ? was.puller() : -1, was != null ? was.from() : now, now, YANKED));
			yankedAt = now;
			PlayerEntity body = world.getPlayerByUuid(down);
			if (body != null) {
				spray(world, body);
			}
		}
	}

	@Nullable
	public static Pull of(PlayerEntity down) {
		return PULLS.get(down.getUuid());
	}

	/** The cord being pulled by {@code friend}, who is down; or null. */
	@Nullable
	public static Pull by(PlayerEntity friend, @Nullable UUID[] downOut) {
		for (Map.Entry<UUID, Pull> e : PULLS.entrySet()) {
			if (e.getValue().puller() == friend.getId() && age(e.getValue(), friend.getWorld().getTime(), 0.0F) < KEEP) {
				if (downOut != null) {
					downOut[0] = e.getKey();
				}
				return e.getValue();
			}
		}
		return null;
	}

	/** Ticks since the pull was last changed (took hold, ended). */
	private static float age(Pull pull, long now, float tickDelta) {
		return now - (pull.how() == PULLING ? pull.from() : pull.ended()) + tickDelta;
	}

	/** How far through its three seconds a pull is (0 to 1). */
	private static float progress(Pull pull, long now, float tickDelta) {
		long to = pull.how() == PULLING ? now : pull.ended();
		return MathHelper.clamp((to - pull.from() + (pull.how() == PULLING ? tickDelta : 0.0F)) / Downed.FEED, 0.0F, 1.0F);
	}

	/** A tug's shape: 0, shooting out over {@link #OUT} ticks to 1 at {@code dt} = 0, snapping back over {@link #BACK}. */
	private static float shape(float dt) {
		if (dt < -OUT || dt > BACK) {
			return 0.0F;
		}
		return dt <= 0.0F ? 1.0F - (float) Math.pow(-dt / OUT, 2.0) : 1.0F - dt / BACK;
	}

	/** How hard a tug is going now (0 none, 1 at its top) and how long it makes the cord at the top (blocks): the tugs while pulling, the yank after. */
	public static float[] tug(Pull pull, long now, float tickDelta) {
		if (pull.how() == YANKED) {
			return new float[] {shape(age(pull, now, tickDelta) - OUT), YANK_LENGTH};
		}
		if (pull.how() == LET_GO) {
			return new float[] {0.0F, 0.0F};
		}
		float t = now - pull.from() + tickDelta;
		float best = 0.0F;
		float length = TUG_LENGTH[0];
		for (int i = 0; i < TUGS.length; i++) {
			float k = shape(t - TUGS[i]);
			if (k > best) {
				best = k;
				length = TUG_LENGTH[i];
			}
		}
		return new float[] {best, length};
	}

	/** Where the fist is, how far the arm has swung from the chest toward back and up, how hard a tug is going, and how long the cord is. */
	public record Grip(Vec3d hand, float swing, float pulse, double length) {
	}

	/**
	 * The friend's fist for this frame (the same for the arm's pose and for the cord's end): at rest it grips the handle just
	 * over the chest; in a tug the arm swings back and up until the cord (root to fist) is as long as the tug makes it;
	 * after the yank it whips on back and falls.
	 */
	public static Grip grip(Pull pull, PlayerEntity friend, PlayerEntity down, long now, float tickDelta) {
		Vec3d shoulder = shoulder(friend, tickDelta);
		Vec3d root = root(down, tickDelta);
		float[] tug = tug(pull, now, tickDelta);
		float since = age(pull, now, tickDelta);
		double rest = hand(shoulder, aim(shoulder, root, 0.0F)).distanceTo(root);
		float swing = 0.0F;
		if (tug[0] > 0.0F) {
			swing = swingFor(shoulder, root, rest + (tug[1] - rest) * tug[0]);
		} else if (pull.how() == YANKED) {
			// The arm goes on back over the shoulder, and falls.
			swing = swingFor(shoulder, root, YANK_LENGTH) * (1.0F - ease((since - OUT - BACK) / FALL));
		} else if (pull.how() == LET_GO) {
			swing = 0.0F;
		}
		Vec3d hand = hand(shoulder, aim(shoulder, root, swing));
		return new Grip(hand, swing, tug[0], hand.distanceTo(root));
	}

	/** The swing (0 to 1) that puts the fist {@code length} blocks from the root. */
	private static float swingFor(Vec3d shoulder, Vec3d root, double length) {
		float lo = 0.0F;
		float hi = 1.0F;
		if (hand(shoulder, aim(shoulder, root, hi)).distanceTo(root) <= length) {
			return hi;
		}
		for (int i = 0; i < 10; i++) {
			float mid = (lo + hi) * 0.5F;
			if (hand(shoulder, aim(shoulder, root, mid)).distanceTo(root) < length) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return (lo + hi) * 0.5F;
	}

	/** How far out the cord's far end is toward the hand (1: at it; 0: all in the chest): it slides in on a let-go and snaps in once the yank has shot it out. */
	public static float out(Pull pull, long now, float tickDelta) {
		float since = age(pull, now, tickDelta);
		return switch (pull.how()) {
			case LET_GO -> 1.0F - ease(since / SLIDE);
			case YANKED -> 1.0F - MathHelper.clamp((since - OUT) / BACK, 0.0F, 1.0F);
			default -> 1.0F;
		};
	}

	/** Whether the cord still shows. */
	public static boolean showing(Pull pull, long now, float tickDelta) {
		return out(pull, now, tickDelta) > 0.001F;
	}

	/** How hard the cord is being pulled right now (0 slack, 1 at the top of a tug). */
	public static float taut(Pull pull, long now, float tickDelta) {
		return tug(pull, now, tickDelta)[0];
	}

	/** How hard {@code down}'s body is jolted now (0 to 1): by each tug, harder by the yank. */
	public static float jolt(PlayerEntity down, long now, float tickDelta) {
		Pull pull = PULLS.get(down.getUuid());
		if (pull == null || pull.how() == LET_GO) {
			return 0.0F;
		}
		float k = tug(pull, now, tickDelta)[0];
		return pull.how() == YANKED ? k : k * 0.55F;
	}

	/** Where {@code down}'s cord comes out, near enough, for aiming a hand at it (world). */
	public static Vec3d root(PlayerEntity down, float tickDelta) {
		float yaw = MathHelper.lerpAngleDegrees(tickDelta, down.prevBodyYaw, down.bodyYaw);
		return down.getLerpedPos(tickDelta).add(0.0, 0.2, 0.0);
	}

	/** Where {@code friend}'s right shoulder is (world), as the player model puts it. */
	public static Vec3d shoulder(PlayerEntity friend, float tickDelta) {
		float yaw = MathHelper.lerpAngleDegrees(tickDelta, friend.prevBodyYaw, friend.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
		double scale = 0.9375;
		double mx = -5.0 / 16.0;
		double my = (friend.isInSneakingPose() ? 5.2 : 2.0) / 16.0;
		double c = Math.cos(yaw);
		double s = Math.sin(yaw);
		double dy = scale * (1.501 - my) - (friend.isInSneakingPose() ? 0.125 : 0.0);
		return friend.getLerpedPos(tickDelta).add(scale * mx * c, dy, scale * mx * s);
	}

	/** The way {@code friend}'s arm points (a unit vector): at the chest, drawing away up and back by {@code draw}. */
	public static Vec3d aim(Vec3d shoulder, Vec3d root, float draw) {
		Vec3d toward = root.subtract(shoulder).normalize();
		Vec3d flat = shoulder.subtract(root).multiply(1.0, 0.0, 1.0);
		Vec3d back = flat.lengthSquared() < 1.0E-6 ? new Vec3d(0.0, 0.0, 0.0) : flat.normalize();
		Vec3d away = new Vec3d(0.0, 0.9, 0.0).add(back.multiply(0.5)).normalize();
		return toward.lerp(away, draw).normalize();
	}

	/** Where the friend's hand is (world), their arm pointing along {@code dir} from {@code shoulder}. */
	public static Vec3d hand(Vec3d shoulder, Vec3d dir) {
		return shoulder.add(dir.multiply(ARM));
	}

	/** The right arm's angles (pitch, yaw: radians, the model's) to point along the world direction {@code dir}, for a body turned {@code bodyYaw} degrees. */
	public static float[] angles(Vec3d dir, float bodyYaw) {
		double t = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
		double x = dir.x * Math.cos(t) + dir.z * Math.sin(t);
		double z = dir.x * Math.sin(t) - dir.z * Math.cos(t);
		double y = -dir.y;
		double s = Math.sqrt(x * x + z * z);
		float pitch = (float) -Math.acos(MathHelper.clamp(y, -1.0, 1.0));
		float yaw = s < 1.0E-4 ? 0.0F : (float) Math.atan2(-x, -z);
		return new float[] {pitch, yaw};
	}

	/** Every tick: the engine's smoke at each cough, blood dripping from the chest while it is pulled; finished pulls forgotten. */
	static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null) {
			PULLS.clear();
			return;
		}
		long now = world.getTime();
		for (Map.Entry<UUID, Pull> e : Map.copyOf(PULLS).entrySet()) {
			Pull pull = e.getValue();
			PlayerEntity down = world.getPlayerByUuid(e.getKey());
			if (down == null || pull.how() != PULLING && now - pull.ended() > KEEP) {
				PULLS.remove(e.getKey());
				continue;
			}
			if (pull.how() != PULLING) {
				continue;
			}
			int age = (int) (now - pull.from());
			Vec3d root = root(down, 0.0F);
			if (age % 6 == 0) {
				world.addParticle(RANDOM.nextInt(3) == 0 ? DARK : BLOOD, root.x + spread(0.15), root.y, root.z + spread(0.15), 0.0, -0.02, 0.0);
			}
			for (int i = 0; i < TUGS.length; i++) {
				if (age == TUGS[i]) {
					// The engine coughs at each tug: exhaust from the body's back, more the harder it is.
					Vec3d back = Vec3d.fromPolar(0.0F, down.bodyYaw).multiply(-0.25);
					for (int k = 0; k < 14 + 14 * i; k++) {
						world.addParticle(ParticleTypes.SMOKE, root.x + back.x, root.y + 0.25, root.z + back.z, spread(0.04), 0.05 + RANDOM.nextDouble() * 0.05,
								spread(0.04));
					}
					if (i > 0) {
						world.addParticle(ParticleTypes.ELECTRIC_SPARK, root.x, root.y + 0.1, root.z, spread(0.2), 0.1, spread(0.2));
					}
				}
			}
		}
	}

	/** Blood sprayed from the chest and the four places the limbs will grow back, as the cord is yanked. */
	private static void spray(ClientWorld world, PlayerEntity down) {
		Vec3d root = root(down, 0.0F);
		float yaw = down.bodyYaw;
		Vec3d along = Vec3d.fromPolar(0.0F, yaw);
		Vec3d side = Vec3d.fromPolar(0.0F, yaw + 90.0F);
		Vec3d feet = down.getPos();
		Vec3d[] places = {root, root.add(side.multiply(0.35)), root.add(side.multiply(-0.35)), feet.add(along.multiply(-0.4)).add(side.multiply(0.15)).add(0.0, 0.2, 0.0),
				feet.add(along.multiply(-0.4)).add(side.multiply(-0.15)).add(0.0, 0.2, 0.0)};
		for (Vec3d at : places) {
			for (int i = 0; i < (at == root ? 70 : 26); i++) {
				ParticleEffect p = i % 6 == 0 ? GORE : i % 3 == 0 ? DARK : BLOOD;
				world.addParticle(p, at.x, at.y, at.z, spread(0.3), 0.15 + RANDOM.nextDouble() * 0.35, spread(0.3));
			}
		}
	}

	private static double spread(double amount) {
		return (RANDOM.nextDouble() - 0.5) * 2.0 * amount;
	}

	/** How hard your camera is shaken by a cord being pulled near you (blocks): each tug a little, the yank a lot, less the farther off. */
	public static double shake(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			return 0.0;
		}
		long now = client.world.getTime();
		double amplitude = 0.0;
		for (Map.Entry<UUID, Pull> e : PULLS.entrySet()) {
			PlayerEntity down = client.world.getPlayerByUuid(e.getKey());
			if (down == null || e.getValue().how() == LET_GO) {
				continue;
			}
			double near = 1.0 - down.distanceTo(client.player) / 14.0;
			if (near > 0.0) {
				float k = tug(e.getValue(), now, tickDelta)[0];
				amplitude = Math.max(amplitude, near * k * (e.getValue().how() == YANKED ? 0.1 : 0.03));
			}
		}
		return amplitude;
	}

	/** An easing from 0 to 1. */
	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** The entity {@code id}, if it is a player. */
	@Nullable
	public static PlayerEntity player(ClientWorld world, int id) {
		Entity e = world.getEntityById(id);
		return e instanceof PlayerEntity p ? p : null;
	}

	static void clear() {
		PULLS.clear();
		yankedAt = -1000;
	}
}
