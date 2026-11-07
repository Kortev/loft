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
	public static final int SNAP = 3;
	private static final int FALL = 8;
	private static final int KEEP = 20;
	/** How far the hand is from the shoulder (blocks): the arm's length to the grip. */
	private static final double ARM = 0.62;
	/** The ticks of the pull the engine coughs at (each a sputter of the sound). */
	private static final int[] COUGHS = {5, 17, 27, 36, 44, 54, 59};

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

	/**
	 * How far the friend has drawn their hand away from the chest (0 to 1): up over the three seconds, falling back when
	 * they let go, held at the yank and then dropped.
	 */
	public static float draw(Pull pull, long now, float tickDelta) {
		float p = progress(pull, now, tickDelta);
		float e = p * p * (3.0F - 2.0F * p);
		float since = age(pull, now, tickDelta);
		return switch (pull.how()) {
			case LET_GO -> e * (1.0F - ease(since / SLIDE));
			case YANKED -> 1.0F - ease((since - 1.0F) / FALL);
			default -> e;
		};
	}

	/** How far out the cord's far end is toward the hand (1: at it; 0: all in the chest): it slides in, snaps in, otherwise stays out. */
	public static float out(Pull pull, long now, float tickDelta) {
		float since = age(pull, now, tickDelta);
		return switch (pull.how()) {
			case LET_GO -> 1.0F - ease(since / SLIDE);
			case YANKED -> 1.0F - MathHelper.clamp(since / SNAP, 0.0F, 1.0F);
			default -> 1.0F;
		};
	}

	/** Whether the cord still shows. */
	public static boolean showing(Pull pull, long now, float tickDelta) {
		return out(pull, now, tickDelta) > 0.001F;
	}

	/** How taut the cord is (0 slack, 1 straight and humming). */
	public static float taut(Pull pull, long now, float tickDelta) {
		float p = progress(pull, now, tickDelta);
		return pull.how() == LET_GO ? 0.0F : MathHelper.clamp(p * 1.6F, 0.0F, 1.0F);
	}

	/** Where {@code down}'s cord comes out, near enough, for aiming a hand at it (world). */
	public static Vec3d root(PlayerEntity down, float tickDelta) {
		float yaw = MathHelper.lerpAngleDegrees(tickDelta, down.prevBodyYaw, down.bodyYaw);
		return down.getLerpedPos(tickDelta).add(Vec3d.fromPolar(0.0F, yaw).multiply(0.3)).add(0.0, 0.2, 0.0);
	}

	/**
	 * Where the cord leaves the body for a hand at {@code from}: the chest's root slid out to the body's near edge (the body lies
	 * face down, so the cord comes out from under its side, toward the friend).
	 */
	public static Vec3d near(Vec3d root, Vec3d from) {
		Vec3d flat = from.subtract(root).multiply(1.0, 0.0, 1.0);
		return flat.lengthSquared() < 1.0E-6 ? root : root.add(flat.normalize().multiply(EDGE));
	}

	/** How far the cord's root is slid out to the edge of the body (blocks). */
	public static final double EDGE = 0.3;

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
			if (age % 4 == 0) {
				world.addParticle(RANDOM.nextInt(3) == 0 ? DARK : BLOOD, root.x + spread(0.15), root.y, root.z + spread(0.15), 0.0, -0.02, 0.0);
			}
			for (int cough : COUGHS) {
				if (age == cough) {
					Vec3d back = Vec3d.fromPolar(0.0F, down.bodyYaw).multiply(-0.25);
					for (int i = 0; i < 12; i++) {
						world.addParticle(ParticleTypes.SMOKE, root.x + back.x, root.y + 0.25, root.z + back.z, spread(0.03), 0.04 + RANDOM.nextDouble() * 0.04,
								spread(0.03));
					}
					if (cough >= 44) {
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
			for (int i = 0; i < 26; i++) {
				ParticleEffect p = i % 6 == 0 ? GORE : i % 3 == 0 ? DARK : BLOOD;
				world.addParticle(p, at.x, at.y, at.z, spread(0.3), 0.15 + RANDOM.nextDouble() * 0.35, spread(0.3));
			}
		}
	}

	private static double spread(double amount) {
		return (RANDOM.nextDouble() - 0.5) * 2.0 * amount;
	}

	/** How hard your own camera is shaken by a cord being pulled: you pulling, or yours pulled (blocks). */
	public static double shake(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			return 0.0;
		}
		long now = client.world.getTime();
		double amplitude = 0.0;
		float since = now - yankedAt + tickDelta;
		if (since >= 0.0F && since < 12.0F) {
			Pull mine = PULLS.get(client.player.getUuid());
			if (mine != null || by(client.player, null) != null) {
				amplitude = 0.07 * Math.exp(-since / 3.0);
			}
		}
		Pull mine = PULLS.get(client.player.getUuid());
		if (mine != null && mine.how() == PULLING) {
			amplitude = Math.max(amplitude, 0.004 + 0.016 * taut(mine, now, tickDelta));
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
