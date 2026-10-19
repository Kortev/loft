package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.form.FormTimeline;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The transformation as a cinematic ({@link FormTimeline}). Everyone round about sees it play out on whoever pulled the
 * cord: the yank ({@link CordPull}), smoke at each cough of the engine, the head bursting in blood as its saw punches
 * out, the arms' saws pushing out through the fists in more, and the roar. Whoever pulled it also has their camera taken for
 * it, their keys held:
 *
 * <ul>
 *   <li>GRAB to YANK: a cut round to the front of them, close on the chest, pulling back a little with the yank;</li>
 *   <li>YANK to BURST: pushing in on the face as the engine catches and coughs;</li>
 *   <li>BURST to ROAR: low, wide and to one side, looking up at them as the saws tear out (the poster's angle);</li>
 *   <li>ROAR: a hard cut to the face, close, punched in, the impact frames red and black; then pulling back;</li>
 *   <li>ROAR_END to END: easing back into their own eyes.</li>
 * </ul>
 *
 * Every cough, the burst and the roar jolt the camera; the roar shakes anyone near enough to feel it.
 */
public final class FormCinematic {
	/** Where the camera is and which way it looks. */
	public record Shot(double x, double y, double z, float yaw, float pitch) {
	}

	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.62F, 0.02F, 0.04F), 1.0F);
	private static final DustParticleEffect DARK_BLOOD = new DustParticleEffect(new Vector3f(0.35F, 0.0F, 0.02F), 1.3F);
	private static final ParticleEffect GORE = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
	/** How far off the roar is felt (blocks). */
	private static final double FELT = 16.0;

	/** The last age each puller's beats were played at, so each is played once. */
	private static final Map<UUID, Integer> PLAYED = new ConcurrentHashMap<>();
	private static final Random RANDOM = Random.create();

	private FormCinematic() {
	}

	/** Every tick: the beats' smoke and blood on whoever is transforming; and your keys held while it is yours. */
	static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null) {
			return;
		}
		for (AbstractClientPlayerEntity player : world.getPlayers()) {
			ClientForms.Pull pull = ClientForms.pull(player);
			if (pull == null) {
				continue;
			}
			int age = (int) (world.getTime() - pull.at());
			Integer last = PLAYED.put(player.getUuid(), age);
			if (last != null && last == age) {
				continue;
			}
			beat(world, player, age);
		}
		PLAYED.keySet().removeIf(id -> world.getPlayerByUuid(id) == null);
		holdInput(client);
	}

	/** What happens on {@code player} {@code age} ticks into their transformation. */
	private static void beat(ClientWorld world, AbstractClientPlayerEntity player, int age) {
		Vec3d head = player.getPos().add(0.0, 1.62, 0.0);
		if (age == FormTimeline.CATCH || age == FormTimeline.COUGH) {
			// The engine catching: a puff of exhaust from the back of the head.
			Vec3d back = head.add(Vec3d.fromPolar(0.0F, player.bodyYaw).multiply(-0.35));
			for (int i = 0; i < 14; i++) {
				world.addParticle(ParticleTypes.SMOKE, back.x, back.y + 0.1, back.z, spread(0.04), 0.05 + RANDOM.nextDouble() * 0.04, spread(0.04));
			}
		} else if (age == FormTimeline.BURST) {
			splash(world, head.add(Vec3d.fromPolar(0.0F, player.bodyYaw).multiply(0.25)), 70, 0.35);
		} else if (age == FormTimeline.ARMS) {
			for (Vec3d fist : fists(player)) {
				splash(world, fist, 40, 0.25);
			}
		}
	}

	/** Blood sprayed out from {@code at}: a fine red mist, darker drops, and chunks. */
	private static void splash(ClientWorld world, Vec3d at, int count, double speed) {
		for (int i = 0; i < count; i++) {
			ParticleEffect p = i % 6 == 0 ? GORE : i % 3 == 0 ? DARK_BLOOD : BLOOD;
			world.addParticle(p, at.x, at.y, at.z, spread(speed), spread(speed) + speed * 0.6, spread(speed));
		}
	}

	private static double spread(double speed) {
		return (RANDOM.nextDouble() - 0.5) * 2.0 * speed;
	}

	/** Where a player's fists are, near enough, with their arms down by their sides. */
	private static Vec3d[] fists(AbstractClientPlayerEntity player) {
		Vec3d side = Vec3d.fromPolar(0.0F, player.bodyYaw + 90.0F).multiply(0.37);
		Vec3d hips = player.getPos().add(0.0, 0.7, 0.0);
		return new Vec3d[] {hips.add(side), hips.subtract(side)};
	}

	/** How many ticks into your own transformation you are, or -1. */
	private static float mine(float tickDelta) {
		ClientPlayerEntity self = MinecraftClient.getInstance().player;
		return self != null ? ClientForms.pullAge(self, tickDelta) : -1.0F;
	}

	/** Your keys held while your transformation plays, and your look held to the way you faced, as the Domain holds its caster's. */
	private static void holdInput(MinecraftClient client) {
		ClientPlayerEntity self = client.player;
		float t = mine(0.0F);
		if (self == null || t < 0.0F || t >= FormTimeline.END) {
			return;
		}
		GameOptions o = client.options;
		for (KeyBinding key : new KeyBinding[] {o.forwardKey, o.backKey, o.leftKey, o.rightKey, o.jumpKey, o.sneakKey, o.sprintKey,
				o.attackKey, o.useKey, o.pickItemKey, o.dropKey, o.swapHandsKey}) {
			key.setPressed(false);
			while (key.wasPressed()) {
				// Swallowed.
			}
		}
		ClientForms.Pull pull = ClientForms.pull(self);
		if (pull != null) {
			self.setYaw(pull.yaw());
			self.setHeadYaw(pull.yaw());
			self.setBodyYaw(pull.yaw());
			self.setPitch(0.0F);
		}
	}

	/** The camera's shot of your own transformation, while it plays; otherwise null. */
	@Nullable
	public static Shot shot(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity self = client.player;
		float t = mine(tickDelta);
		ClientForms.Pull pull = self != null ? ClientForms.pull(self) : null;
		if (self == null || pull == null || t < 0.0F || t >= FormTimeline.END) {
			return null;
		}
		Vec3d eye = self.getLerpedPos(tickDelta).add(0.0, self.getStandingEyeHeight(), 0.0);
		Vec3d face = eye.add(0.0, -0.08, 0.0);
		Vec3d chest = eye.add(0.0, -0.45, 0.0);
		float yaw = pull.yaw();
		Shot s;
		if (t < FormTimeline.YANK) {
			double k = ease(t / FormTimeline.YANK);
			s = around(yaw, chest, MathHelper.lerp(k, 2.1, 2.7), MathHelper.lerp(k, 16.0, 30.0), 0.05,
					chest.lerp(face, 0.3 * k));
		} else if (t < FormTimeline.BURST) {
			double k = ease((t - FormTimeline.YANK) / (FormTimeline.BURST - FormTimeline.YANK));
			s = around(yaw, face, MathHelper.lerp(k, 2.6, 1.9), MathHelper.lerp(k, 28.0, 22.0), -0.05, face);
		} else if (t < FormTimeline.ROAR) {
			double k = ease((t - FormTimeline.BURST) / (FormTimeline.ROAR - FormTimeline.BURST));
			Vec3d look = chest.lerp(face, 0.6);
			s = around(yaw, eye.add(0.0, -1.0, 0.0), MathHelper.lerp(k, 3.6, 3.1), MathHelper.lerp(k, -32.0, -40.0),
					MathHelper.lerp(k, -0.3, -0.45), look);
		} else if (t < FormTimeline.ROAR_END) {
			double k = ease((t - FormTimeline.ROAR) / (FormTimeline.ROAR_END - FormTimeline.ROAR));
			s = around(yaw, face, MathHelper.lerp(k, 1.3, 2.4), MathHelper.lerp(k, 6.0, 14.0), MathHelper.lerp(k, 0.02, 0.15), face);
		} else {
			double k = ease((t - FormTimeline.ROAR_END) / (FormTimeline.END - FormTimeline.ROAR_END));
			Shot from = around(yaw, face, 2.4, 14.0, 0.15, face);
			Shot to = new Shot(eye.x, eye.y, eye.z, yaw, 0.0F);
			s = new Shot(MathHelper.lerp(k, from.x, to.x), MathHelper.lerp(k, from.y, to.y), MathHelper.lerp(k, from.z, to.z),
					from.yaw + MathHelper.wrapDegrees(to.yaw - from.yaw) * (float) k, MathHelper.lerp((float) k, from.pitch, to.pitch));
		}
		return clear(client, face, s);
	}

	/** How far the view is zoomed for the shot (1 for not at all): punched in on the roar. */
	public static float fovScale(float tickDelta) {
		float t = mine(tickDelta);
		if (t < FormTimeline.ROAR || t >= FormTimeline.ROAR_END) {
			return 1.0F;
		}
		float k = (float) ease((t - FormTimeline.ROAR) / (FormTimeline.ROAR_END - FormTimeline.ROAR));
		return MathHelper.lerp(k, 0.72F, 1.0F);
	}

	/** How hard the camera is being shaken now (blocks): the yank, each cough, the burst, the roar. */
	public static double shake(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		double amplitude = 0.0;
		float t = mine(tickDelta);
		if (t >= 0.0F) {
			if (t > FormTimeline.REACH && t < FormTimeline.YANK) {
				amplitude = 0.025;
			}
			amplitude = Math.max(amplitude, pulse(t, FormTimeline.CATCH, 0.06, 3.0));
			amplitude = Math.max(amplitude, pulse(t, FormTimeline.COUGH, 0.06, 3.0));
			amplitude = Math.max(amplitude, pulse(t, FormTimeline.BURST, 0.1, 4.0));
			amplitude = Math.max(amplitude, pulse(t, FormTimeline.ARMS, 0.05, 4.0));
			amplitude = Math.max(amplitude, pulse(t, FormTimeline.ROAR, 0.16, 9.0));
		}
		if (client.world != null && client.player != null) {
			for (AbstractClientPlayerEntity other : client.world.getPlayers()) {
				if (other == client.player) {
					continue;
				}
				float o = ClientForms.pullAge(other, tickDelta);
				double near = 1.0 - other.distanceTo(client.player) / FELT;
				if (o >= 0.0F && near > 0.0) {
					amplitude = Math.max(amplitude, pulse(o, FormTimeline.ROAR, 0.06 * near, 9.0));
				}
			}
		}
		return amplitude;
	}

	private static double pulse(float t, int at, double height, double fade) {
		return t >= at ? height * Math.exp(-(t - at) / fade) : 0.0;
	}

	/** The impact frame showing now, if any: 1 red and black, 2 its negative; 0 none. */
	public static int impactStyle(float tickDelta) {
		float t = mine(tickDelta);
		if (t < FormTimeline.ROAR || t >= FormTimeline.ROAR + FormTimeline.IMPACT) {
			return 0;
		}
		return ((int) (t - FormTimeline.ROAR)) % 2 == 0 ? 1 : 2;
	}

	/** How red the edges of the screen are: a pulse at the burst, and a flood of it at the roar, fading. */
	public static float vignette(float tickDelta) {
		float t = mine(tickDelta);
		if (t < 0.0F) {
			return 0.0F;
		}
		return (float) Math.max(pulse(t, FormTimeline.BURST, 0.5, 6.0), pulse(t, FormTimeline.ROAR, 1.0, 14.0));
	}

	/** The camera {@code distance} out in front of them (as they faced), {@code round} degrees round and {@code up} above {@code from}. */
	private static Shot around(float yaw, Vec3d from, double distance, double round, double up, Vec3d look) {
		float r = (yaw + (float) round) * MathHelper.RADIANS_PER_DEGREE;
		Vec3d out = new Vec3d(-MathHelper.sin(r), 0.0, MathHelper.cos(r));
		return look(from.add(out.multiply(distance)).add(0.0, up, 0.0), look);
	}

	private static Shot look(Vec3d at, Vec3d look) {
		Vec3d to = look.subtract(at);
		float lookYaw = (float) (MathHelper.atan2(to.z, to.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float lookPitch = (float) (-(MathHelper.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * MathHelper.DEGREES_PER_RADIAN));
		return new Shot(at.x, at.y, at.z, lookYaw, lookPitch);
	}

	/** The shot brought in short of anything solid between their face and where it would stand. */
	private static Shot clear(MinecraftClient client, Vec3d face, Shot shot) {
		if (client.world == null || client.player == null) {
			return shot;
		}
		Vec3d to = new Vec3d(shot.x, shot.y, shot.z);
		HitResult hit = client.world.raycast(new RaycastContext(face, to, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE,
				client.player));
		if (hit.getType() == HitResult.Type.MISS) {
			return shot;
		}
		Vec3d back = to.subtract(face);
		double reach = Math.max(0.6, face.distanceTo(hit.getPos()) - 0.4);
		Vec3d at = face.add(back.multiply(reach / Math.max(back.length(), 1.0E-4)));
		return new Shot(at.x, at.y, at.z, shot.yaw, shot.pitch);
	}

	private static double ease(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}
}
