package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.form.MergeTimeline;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Pochita becoming your heart, as a cinematic: the anime's scene, long ({@link MergeTimeline}). Everyone round about sees
 * it play out and hears him; you see it from outside your own body, through black bars, his lines under the picture.
 *
 * <p>You're struck down: the view tips back with you as you fall to the sky, then cuts high over your body lying in the
 * grey and the quiet, a pool of blood spreading from under you. The world goes out round you, black but for you lying in
 * a last light. Low on the ground past your head, Pochita comes hobbling out of the dark; he sniffs at your face, nudges
 * you, whimpers; hops up onto your chest and settles. He talks to you: five lines (his voice, cut from clips kortev
 * chooses, and under the picture), the camera going back and forth between him over you and your face under him, and
 * round you both in the light. He bows his head to your chest and goes down into it: a white flash, impact frames, a
 * fountain of blood, the cord pushing out of you where he went. Your heart starts, six beats, closer and harder, your
 * body jolting with each, the edges of the screen throbbing red, the dark lifting and the colour coming back a beat at a
 * time; and you get to your feet, and stand there with the cord hanging out of your chest, "Pull the cord" in the bar,
 * until you pull it: the transformation.
 */
public final class MergeCinematic {
	/** Someone merging: when it started, and where Pochita comes from. */
	public record Merging(long at, Vec3d from, boolean waiting) {
	}

	/** Where Pochita is and how he's held at a moment: his feet, the way he faces, his nose up or down, his squash. */
	private record Pose(Vec3d at, float yaw, float pitch, float wide, float tall, boolean walking) {
	}

	private static final Map<UUID, Merging> MERGING = new ConcurrentHashMap<>();
	/** The way each merging body was last drawn lying (its body yaw as the renderer had it), so the camera and Pochita agree. */
	private static final Map<UUID, Float> DRAWN_YAW = new ConcurrentHashMap<>();
	private static final Identifier POCHITA = ChainsawMan.id("textures/entity/pochita.png");
	private static final Identifier POOL = ChainsawMan.id("textures/entity/blood_pool.png");
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.62F, 0.02F, 0.04F), 0.9F);
	private static final DustParticleEffect DARK_BLOOD = new DustParticleEffect(new Vector3f(0.35F, 0.0F, 0.02F), 1.1F);
	private static final Random RANDOM = Random.create();
	/** How far up you lie off the ground, where along you (from your feet) your chest and face are (blocks). */
	private static final double LIFT = 0.15;
	private static final double CHEST = 0.95;
	private static final double CHEST_UP = 0.28;
	private static final double FACE = 1.42;
	/** Where along you (from your feet) the middle of your face is, as the camera looks at it. */
	private static final double YOU = 1.64;
	/** How far the pool of blood spreads (blocks across from its middle). */
	private static final float POOL_SIZE = 1.1F;
	/** How tall the black bars are, as a share of the screen. */
	private static final float BARS = 0.11F;
	/** Pochita's lines: his voice for each, and its words under the picture. */
	private static final net.minecraft.sound.SoundEvent[] VOICES = io.github.kortev.chainsawman.ChainsawSounds.MERGE_VOICES;

	private static PochitaModel model;

	private MergeCinematic() {
	}

	static void setModel(PochitaModel pochita) {
		model = pochita;
	}

	static void started(UUID player, long at, Vec3d from) {
		MERGING.put(player, new Merging(at, from, false));
	}

	/** On your feet at the end of it, waiting to pull the cord. */
	static void waiting(UUID player) {
		MERGING.computeIfPresent(player, (id, m) -> new Merging(m.at(), m.from(), true));
	}

	static void ended(UUID player) {
		MERGING.remove(player);
		DRAWN_YAW.remove(player);
	}

	public static boolean merging(PlayerEntity player) {
		return MERGING.containsKey(player.getUuid());
	}

	/** How many ticks into their merge {@code player} is, or -1. */
	public static float age(PlayerEntity player, float tickDelta) {
		Merging m = MERGING.get(player.getUuid());
		if (m == null) {
			return -1.0F;
		}
		float t = player.getWorld().getTime() - m.at() + tickDelta;
		return t >= 0.0F && t <= MergeTimeline.END + io.github.kortev.chainsawman.form.Merge.WAIT + 40 ? t : -1.0F;
	}

	/** How far over {@code player} has gone, lying (0 standing, 1 flat on their back): falling hard, a bounce, rising. */
	public static float lying(PlayerEntity player, float tickDelta) {
		float t = age(player, tickDelta);
		if (t < 0.0F) {
			return 0.0F;
		}
		if (t < MergeTimeline.FELL) {
			float k = t / MergeTimeline.FELL;
			return k * k * k;
		}
		if (t < MergeTimeline.FELL + 4) {
			// The bounce off the ground.
			return 1.0F - 0.06F * MathHelper.sin((t - MergeTimeline.FELL) / 4.0F * MathHelper.PI);
		}
		return 1.0F - ease((t - MergeTimeline.RISE) / (MergeTimeline.END - MergeTimeline.RISE));
	}

	/** How far through getting up you are, {@code t} ticks in (0 to 1). */
	public static float rising(float t) {
		return MathHelper.clamp((t - MergeTimeline.RISE) / (MergeTimeline.END - MergeTimeline.RISE), 0.0F, 1.0F);
	}

	/** Whether {@code player} stands at the end of it, waiting to pull the cord. */
	public static boolean waiting(PlayerEntity player) {
		Merging m = MERGING.get(player.getUuid());
		return m != null && m.waiting();
	}

	/** How far {@code player}'s body is jolted up off the ground (blocks): hitting it, and each beat of the heart. */
	public static float jolt(PlayerEntity player, float tickDelta) {
		float t = age(player, tickDelta);
		if (t < 0.0F) {
			return 0.0F;
		}
		double j = 0.0;
		for (int beat : MergeTimeline.BEATS) {
			j = Math.max(j, pulse(t, beat, 0.09, 1.6));
		}
		return (float) j;
	}

	/** The way your head lies from your feet: back the way you faced (you went over backwards). */
	public static Vec3d headward(PlayerEntity player) {
		return Vec3d.fromPolar(0.0F, yaw(player) + 180.0F);
	}

	private static float yaw(PlayerEntity player) {
		return DRAWN_YAW.getOrDefault(player.getUuid(), player.getBodyYaw());
	}

	/** The renderer laying {@code player} down at {@code bodyYaw}. */
	public static void drawn(PlayerEntity player, float bodyYaw) {
		DRAWN_YAW.put(player.getUuid(), bodyYaw);
	}

	private static Vec3d along(PlayerEntity player, float tickDelta, double from, double up) {
		return player.getLerpedPos(tickDelta).add(headward(player).multiply(from)).add(0.0, up, 0.0);
	}

	private static Vec3d chest(PlayerEntity player, float tickDelta) {
		return along(player, tickDelta, CHEST, LIFT + CHEST_UP);
	}

	private static Vec3d face(PlayerEntity player, float tickDelta) {
		return along(player, tickDelta, FACE, LIFT + 0.25);
	}

	private static float mine(float tickDelta) {
		ClientPlayerEntity self = MinecraftClient.getInstance().player;
		return self != null ? age(self, tickDelta) : -1.0F;
	}

	/** Whether your own merge is playing. */
	public static boolean filming(float tickDelta) {
		return mine(tickDelta) >= 0.0F;
	}

	// The camera.

	/** Your camera's shot of your own merge, while it plays; otherwise null. */
	@Nullable
	public static FormCinematic.Shot shot(float tickDelta) {
		ClientPlayerEntity self = MinecraftClient.getInstance().player;
		float t = mine(tickDelta);
		Merging m = self != null ? MERGING.get(self.getUuid()) : null;
		if (self == null || m == null || t < 0.0F) {
			return null;
		}
		Vec3d chest = chest(self, tickDelta);
		Vec3d head = headward(self);
		Vec3d side = Vec3d.fromPolar(0.0F, yaw(self) + 90.0F);
		Vec3d feet = self.getLerpedPos(tickDelta);
		Pose pochita = pochita(self, m, t, tickDelta);
		if (t < MergeTimeline.FELL + 1) {
			// Through your own eyes as you go over: the world tipping away to the sky.
			double k = ease(t / (MergeTimeline.FELL + 1));
			Vec3d eye = feet.add(0.0, MathHelper.lerp(k, 1.62, 0.95), 0.0).add(head.multiply(0.8 * k));
			return new FormCinematic.Shot(eye.x, eye.y, eye.z, yaw(self), (float) MathHelper.lerp(k, 0.0, -85.0));
		}
		if (t < MergeTimeline.COMING) {
			// High over your body, turning slowly, coming down as the world goes out round you.
			double k = (t - MergeTimeline.FELL) / (MergeTimeline.COMING - MergeTimeline.FELL);
			Vec3d out = Vec3d.fromPolar(0.0F, yaw(self) + 30.0F + 70.0F * (float) k).multiply(0.35);
			return look(chest.add(out).add(0.0, MathHelper.lerp(ease(k), 6.0, 2.6), 0.0), chest);
		}
		// Pochita's eyes, and your own face (properly: FACE is where your chin is, for him to sniff at).
		Vec3d eyes = pochita.at().add(Vec3d.fromPolar(0.0F, pochita.yaw()).multiply(0.45)).add(0.0, 0.5, 0.0);
		Vec3d you = along(self, tickDelta, YOU, LIFT + 0.22);
		Vec3d up = new Vec3d(0.0, 1.0, 0.0);
		if (t < MergeTimeline.SNIFF) {
			// Down on the ground past your head, your head in the bottom of the picture, him coming at it out of the dark.
			Vec3d away = flat(you.subtract(pochita.at()));
			return look(you.add(away.multiply(1.4)).add(side.multiply(0.3)).add(0.0, 0.3, 0.0), pochita.at().add(0.0, 0.3, 0.0));
		}
		if (t < MergeTimeline.CLIMB) {
			// From the side, the two of you face to face: him sniffing at you.
			Vec3d to = flat(pochita.at().subtract(you));
			Vec3d across = new Vec3d(-to.z, 0.0, to.x);
			if (across.dotProduct(head) < 0.0) {
				across = across.negate();
			}
			Vec3d mid = you.lerp(pochita.at(), 0.5);
			double k = (t - MergeTimeline.SNIFF) / (MergeTimeline.CLIMB - MergeTimeline.SNIFF);
			return look(mid.add(across.multiply(MathHelper.lerp(k, 2.6, 2.3))).add(0.0, 0.6, 0.0), mid.add(0.0, 0.25, 0.0));
		}
		if (t < MergeTimeline.ON_CHEST) {
			// Off to the side, watching him hop up.
			return look(chest.add(side.multiply(-2.8)).add(head.multiply(-0.3)).add(0.0, 1.0, 0.0), chest.add(0.0, 0.35, 0.0));
		}
		if (t < MergeTimeline.BOW) {
			return talking(t, you, chest, head, side, up, eyes);
		}
		if (t < MergeTimeline.SINK) {
			// Low beside you: him bowing his head to your chest.
			double k = ease((t - MergeTimeline.BOW) / (MergeTimeline.SINK - MergeTimeline.BOW));
			return look(chest.add(side.multiply(MathHelper.lerp(k, 2.3, 2.0))).add(head.multiply(0.3)).add(0.0, 0.65, 0.0),
					chest.add(head.multiply(0.35)).add(0.0, 0.45, 0.0));
		}
		if (t < MergeTimeline.SUNK) {
			// Level with your chest from the side, as he goes down into it.
			return look(chest.add(side.multiply(1.9)).add(head.multiply(0.15)).add(0.0, 0.35, 0.0), chest.add(0.0, 0.15, 0.0));
		}
		if (t < MergeTimeline.BEAT) {
			// The cord pushing out of your chest.
			return look(chest.add(side.multiply(1.1)).add(head.multiply(-0.5)).add(0.0, 0.8, 0.0), chest.add(0.0, 0.05, 0.0));
		}
		if (t < MergeTimeline.RISE) {
			// Over your face as your heart starts, closer with each beat.
			double k = (t - MergeTimeline.BEAT) / (MergeTimeline.RISE - MergeTimeline.BEAT);
			return look(you.add(head.multiply(-0.15)).add(0.0, MathHelper.lerp(k, 1.4, 0.75), 0.0), you);
		}
		// Low and to the side as you get up, the whole of you, coming round in front as you stand; then low in front,
		// looking up at you standing there, the cord hanging out of your chest, slowly closing in until you pull it (the
		// transformation's own camera takes over from there).
		Vec3d front = Vec3d.fromPolar(0.0F, yaw(self));
		Vec3d eye = feet.add(0.0, 1.0, 0.0);
		if (t < MergeTimeline.END) {
			double k = ease(rising(t));
			return look(feet.add(side.multiply(MathHelper.lerp(k, 2.6, 0.0))).add(front.multiply(MathHelper.lerp(k, 1.2, 2.6)))
					.add(0.0, MathHelper.lerp(k, 0.8, 0.5), 0.0), feet.add(0.0, MathHelper.lerp(k, 0.4, 1.2), 0.0));
		}
		double k = 1.0 - Math.exp(-(t - MergeTimeline.END) / 200.0);
		return look(eye.add(front.multiply(MathHelper.lerp(k, 2.6, 2.1))).add(side.multiply(0.35 * k)).add(0.0, -0.5, 0.0),
				eye.add(0.0, 0.2, 0.0));
	}

	/** Which of Pochita's lines the camera is on at {@code t} (0 to 4), cutting a little before each starts. */
	private static int line(float t) {
		int line = 0;
		for (int i = 1; i < MergeTimeline.LINES.length; i++) {
			if (t >= MergeTimeline.LINES[i] - 6) {
				line = i;
			}
		}
		return line;
	}

	/**
	 * While he talks, each line its own shot, slowly pushing in: him looming over you, from past the top of your head;
	 * over his shoulder, down at your face under him; round the two of you in the last of the light; close on his face
	 * from beside yours; and high over the two of you, rising.
	 */
	private static FormCinematic.Shot talking(float t, Vec3d you, Vec3d chest, Vec3d head, Vec3d side, Vec3d up, Vec3d eyes) {
		int[] lines = MergeTimeline.LINES;
		int line = line(t);
		float from = line == 0 ? MergeTimeline.TALK : lines[line] - 6;
		float until = line + 1 < lines.length ? lines[line + 1] - 6 : MergeTimeline.BOW;
		double k = ease((t - from) / (until - from));
		Vec3d both = chest.lerp(you, 0.45);
		switch (line) {
			case 0 -> {
				return look(you.add(head.multiply(MathHelper.lerp(k, 1.4, 1.2))).add(side.multiply(0.5)).add(up.multiply(0.3)), eyes.lerp(you, 0.3));
			}
			case 1 -> {
				return look(chest.add(head.multiply(-0.3)).add(side.multiply(MathHelper.lerp(k, 1.7, 1.55))).add(up.multiply(1.6)),
						you.lerp(eyes, 0.35));
			}
			case 2 -> {
				float round = (float) MathHelper.lerp(k, -60.0, 15.0);
				Vec3d out = side.rotateY(round * MathHelper.RADIANS_PER_DEGREE).multiply(2.5);
				return look(both.add(out).add(0.0, 1.15, 0.0), both.add(0.0, 0.3, 0.0));
			}
			case 3 -> {
				return look(you.add(side.multiply(MathHelper.lerp(k, 1.2, 1.05))).add(head.multiply(0.4)).add(up.multiply(0.4)), eyes);
			}
			default -> {
				return look(both.add(side.multiply(0.2)).add(up.multiply(MathHelper.lerp(k, 3.0, 4.0))), both);
			}
		}
	}

	/** How long the lens is for each shot, as a share of your field of view: long on the close ones, kept clear of you both. */
	private static float lens(float t) {
		if (t < MergeTimeline.FELL + 1) {
			return 1.0F;
		}
		if (t < MergeTimeline.COMING) {
			return 0.65F;
		}
		if (t < MergeTimeline.SNIFF) {
			return 0.75F;
		}
		if (t < MergeTimeline.CLIMB) {
			return 0.6F;
		}
		if (t < MergeTimeline.ON_CHEST) {
			return 0.7F;
		}
		if (t < MergeTimeline.BOW) {
			return new float[] {0.6F, 0.6F, 0.45F, 0.45F, 0.55F}[line(t)];
		}
		if (t < MergeTimeline.SINK) {
			return 0.45F;
		}
		if (t < MergeTimeline.BEAT) {
			return 0.55F;
		}
		if (t < MergeTimeline.RISE) {
			return 0.5F;
		}
		return 0.75F;
	}

	private static Vec3d flat(Vec3d v) {
		Vec3d f = new Vec3d(v.x, 0.0, v.z);
		return f.lengthSquared() < 1.0E-6 ? new Vec3d(1.0, 0.0, 0.0) : f.normalize();
	}

	private static FormCinematic.Shot look(Vec3d at, Vec3d look) {
		Vec3d to = look.subtract(at);
		float yaw = (float) (MathHelper.atan2(to.z, to.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * MathHelper.DEGREES_PER_RADIAN);
		return new FormCinematic.Shot(at.x, at.y, at.z, yaw, MathHelper.clamp(pitch, -89.9F, 89.9F));
	}

	// What goes over the picture.

	/** How hard your camera shakes: hitting the ground, the sink, each beat. */
	public static double shake(float tickDelta) {
		float t = mine(tickDelta);
		if (t < 0.0F) {
			return 0.0;
		}
		double s = pulse(t, MergeTimeline.FELL, 0.14, 3.0);
		s = Math.max(s, pulse(t, MergeTimeline.SINK, 0.2, 7.0));
		for (int beat : MergeTimeline.BEATS) {
			s = Math.max(s, pulse(t, beat, 0.07, 2.5));
		}
		return s;
	}

	/** How far your view is punched in: the sink, each beat. */
	public static float fov(float tickDelta) {
		float t = mine(tickDelta);
		if (t < 0.0F) {
			return 1.0F;
		}
		double punch = pulse(t, MergeTimeline.SINK, 0.15, 6.0);
		for (int beat : MergeTimeline.BEATS) {
			punch = Math.max(punch, pulse(t, beat, 0.07, 3.0));
		}
		return lens(t) * (1.0F - (float) punch);
	}

	/** The red at the edges of the screen: throbbing with each beat, flooding at the sink. */
	public static float vignette(float tickDelta) {
		float t = mine(tickDelta);
		if (t < 0.0F) {
			return 0.0F;
		}
		double v = pulse(t, MergeTimeline.SINK, 1.0, 10.0);
		for (int beat : MergeTimeline.BEATS) {
			v = Math.max(v, pulse(t, beat, 0.85, 4.0));
		}
		return (float) v;
	}

	/** How grey the world is: drained as you die; Pochita comes to you in colour, out of the dark that follows. */
	public static float grey(float tickDelta) {
		float t = mine(tickDelta);
		if (t < 0.0F) {
			return 0.0F;
		}
		return Math.min(ease(t / 10.0F), 1.0F - ease((t - MergeTimeline.DARK) / 30.0F));
	}

	/** How far the world has gone out round you: black but for a last light on you, from DARK until your heart starts. */
	public static float darkness(float tickDelta) {
		float t = mine(tickDelta);
		if (t < 0.0F) {
			return 0.0F;
		}
		return Math.min(ease((t - MergeTimeline.DARK) / 30.0F), lifting(t));
	}

	/** What's left of the grey and the dark after the beats so far: each beat takes a share of it away. */
	private static float lifting(float t) {
		int[] beats = MergeTimeline.BEATS;
		float level = 1.0F;
		for (int i = 0; i < beats.length; i++) {
			if (t >= beats[i]) {
				level = MathHelper.lerp(ease((t - beats[i]) / 3.0F), 1.0F - (float) i / beats.length, 1.0F - (float) (i + 1) / beats.length);
			}
		}
		return level;
	}

	/** How white the screen flashes as he goes into you. */
	public static float flash(float tickDelta) {
		float t = mine(tickDelta);
		return t < MergeTimeline.SINK ? 0.0F : (float) pulse(t, MergeTimeline.SINK, 1.0, 3.0);
	}

	/** The impact frames as he sinks into you: 1 red and black, 2 its negative; 0 none. */
	public static int impactStyle(float tickDelta) {
		float t = mine(tickDelta);
		if (t < MergeTimeline.SINK || t >= MergeTimeline.SINK + 4) {
			return 0;
		}
		return ((int) (t - MergeTimeline.SINK)) % 2 == 0 ? 1 : 2;
	}

	/** The black bars closing in at the top and bottom as you die, and opening as you rise; his lines in the bottom one. */
	public static void bars(DrawContext ctx, float tickDelta) {
		float t = mine(tickDelta);
		if (t < 0.0F) {
			return;
		}
		int w = ctx.getScaledWindowWidth();
		int screen = ctx.getScaledWindowHeight();
		float white = flash(tickDelta);
		if (white > 0.01F) {
			ctx.fill(0, 0, w, screen, ((int) (Math.min(white, 1.0F) * 255) << 24) | 0xFFFFFF);
		}
		float k = ease(t / 8.0F);
		int h = (int) (screen * BARS * k);
		if (h <= 0) {
			return;
		}
		ctx.fill(0, 0, w, h, 0xFF000000);
		ctx.fill(0, screen - h, w, screen, 0xFF000000);
		int[] lines = MergeTimeline.LINES;
		MinecraftClient client = MinecraftClient.getInstance();
		for (int i = 0; i < lines.length; i++) {
			float in = t - lines[i];
			int length = MergeTimeline.LINE_LENGTHS[i];
			if (in < 0.0F || in > length) {
				continue;
			}
			float alpha = MathHelper.clamp(Math.min(in / 5.0F, (length - in) / 6.0F), 0.0F, 1.0F);
			if (alpha < 0.05F) {
				continue;
			}
			String name = client.player != null ? client.player.getGameProfile().getName() : "";
			net.minecraft.text.Text said = net.minecraft.text.Text.translatable("merge.chainsawman.line" + (i + 1), name);
			int a = (int) (alpha * 255) << 24;
			int y = screen - h + (h - client.textRenderer.fontHeight) / 2;
			// His lines warm, yours white.
			ctx.drawCenteredTextWithShadow(client.textRenderer, said, w / 2, y, a | (MergeTimeline.POCHITA_SAYS[i] ? 0xFFC98A : 0xF2F2F2));
		}
		Merging m = client.player != null ? MERGING.get(client.player.getUuid()) : null;
		if (m != null && m.waiting()) {
			// Yours to pull.
			float in = MathHelper.clamp((t - MergeTimeline.END) / 10.0F, 0.0F, 1.0F);
			float throb = 0.85F + 0.15F * MathHelper.sin(t * 0.25F);
			int a = (int) (in * throb * 255) << 24;
			net.minecraft.text.Text pull = net.minecraft.text.Text.translatable("merge.chainsawman.pull", ChainsawManClient.PULL_CORD.getBoundKeyLocalizedText());
			int y = screen - h + (h - client.textRenderer.fontHeight) / 2;
			ctx.drawCenteredTextWithShadow(client.textRenderer, pull, w / 2, y, a | 0xFFB25C);
		}
	}

	private static double pulse(float t, int at, double height, double fade) {
		return t >= at ? height * Math.exp(-(t - at) / fade) : 0.0;
	}

	// Pochita, and the blood.

	/** Pochita {@code t} ticks into {@code player}'s merge: coming, sniffing, climbing, on your chest, going into you. */
	private static Pose pochita(PlayerEntity player, Merging m, float t, float tickDelta) {
		Vec3d chest = chest(player, tickDelta);
		Vec3d face = face(player, tickDelta);
		Vec3d side = Vec3d.fromPolar(0.0F, yaw(player) + 90.0F);
		double ground = player.getLerpedPos(tickDelta).y;
		double which = side.dotProduct(m.from().subtract(face)) >= 0.0 ? 1.0 : -1.0;
		Vec3d bySide = new Vec3d(face.x, ground, face.z).add(side.multiply(0.75 * which)).add(headward(player).multiply(-0.15));
		float faceYaw = yawTo(face.subtract(bySide));
		if (t < MergeTimeline.SNIFF) {
			// Hobbling: a dip on every other step, slowing as he comes.
			double k = easeOut((t - MergeTimeline.COMING) / (MergeTimeline.SNIFF - MergeTimeline.COMING));
			Vec3d at = new Vec3d(m.from().x, ground, m.from().z).lerp(bySide, k);
			double hob = Math.abs(MathHelper.sin(t * 0.85F)) * 0.07 * (1.0 - k);
			float limp = MathHelper.sin(t * 0.85F) * 0.12F * (float) (1.0 - k);
			return new Pose(at.add(0.0, hob, 0.0), yawTo(bySide.subtract(m.from())), limp, 1.0F, 1.0F, true);
		}
		if (t < MergeTimeline.CLIMB) {
			// Sniffing at your face, nose down and up; a nudge at it.
			float sniff = MathHelper.sin((t - MergeTimeline.SNIFF) * 1.7F) * 0.22F + 0.15F;
			float nudge = (float) pulse(t, MergeTimeline.SNIFF + 7, 0.12, 2.0);
			Vec3d to = face.subtract(bySide).multiply(1.0, 0.0, 1.0).normalize();
			return new Pose(bySide.add(to.multiply(nudge)), faceYaw, sniff, 1.0F, 1.0F, false);
		}
		Vec3d top = chest.add(0.0, 0.01, 0.0);
		float onYaw = yawTo(headward(player));
		if (t < MergeTimeline.ON_CHEST) {
			// The hop up, and landing squashed.
			double k = ease((t - MergeTimeline.CLIMB) / (MergeTimeline.ON_CHEST - MergeTimeline.CLIMB - 2));
			Vec3d at = bySide.lerp(top, k).add(0.0, MathHelper.sin((float) Math.min(k, 1.0) * MathHelper.PI) * 0.45, 0.0);
			float yaw = MathHelper.lerpAngleDegrees((float) k, faceYaw, onYaw);
			float land = (float) pulse(t, MergeTimeline.ON_CHEST - 2, 0.25, 1.5);
			return new Pose(at, yaw, -0.25F * (float) Math.sin(k * Math.PI), 1.0F + land * 0.6F, 1.0F - land, false);
		}
		if (t < MergeTimeline.BOW) {
			// Standing on you, looking at your face; breathing; a nod as each line starts.
			float breath = MathHelper.sin(t * 0.3F) * 0.025F;
			float nod = 0.0F;
			for (int i = 0; i < MergeTimeline.LINES.length; i++) {
				int line = MergeTimeline.LINES[i];
				if (MergeTimeline.POCHITA_SAYS[i]) {
					nod = Math.max(nod, (float) (t >= line ? Math.sin(Math.min(1.0, (t - line) / 8.0) * Math.PI) * 0.18 : 0.0));
				}
			}
			return new Pose(top, onYaw, 0.18F + nod, 1.0F - breath, 1.0F + breath, false);
		}
		if (t < MergeTimeline.SINK) {
			// Bowing his head to your chest.
			float bow = ease((t - MergeTimeline.BOW) / (MergeTimeline.SINK - MergeTimeline.BOW));
			return new Pose(top, onYaw, 0.18F + 0.35F * bow, 1.0F, 1.0F, false);
		}
		// Down into you: squashing into your chest, spreading, and gone.
		double k = easeIn((t - MergeTimeline.SINK) / (MergeTimeline.SUNK - MergeTimeline.SINK));
		float tall = (float) (1.0 - k);
		float wide = (float) (1.0 + 0.5 * Math.sin(k * Math.PI)) * (float) (1.0 - k * k);
		return new Pose(top.subtract(0.0, 0.12 * k, 0.0), onYaw, 0.0F, wide, tall, false);
	}

	private static float yawTo(Vec3d d) {
		return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
	}

	/** Pochita, and the pool of blood, drawn into the world for everyone merging, as the entities are. */
	public static void render(WorldRenderContext context) {
		ClientWorld world = context.world();
		VertexConsumerProvider consumers = context.consumers();
		if (MERGING.isEmpty() || world == null || consumers == null || model == null) {
			return;
		}
		float tickDelta = context.tickCounter().getTickDelta(false);
		Vec3d cam = context.camera().getPos();
		MatrixStack matrices = context.matrixStack() != null ? context.matrixStack() : new MatrixStack();
		for (Map.Entry<UUID, Merging> e : MERGING.entrySet()) {
			PlayerEntity player = world.getPlayerByUuid(e.getKey());
			if (player == null) {
				continue;
			}
			float t = age(player, tickDelta);
			if (t < 0.0F) {
				continue;
			}
			pool(matrices, consumers, world, player, cam, t, tickDelta);
			if (t < MergeTimeline.COMING || t >= MergeTimeline.SUNK) {
				continue;
			}
			Pose pose = pochita(player, e.getValue(), t, tickDelta);
			int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(pose.at().add(0.0, 0.3, 0.0)));
			matrices.push();
			matrices.translate(pose.at().x - cam.x, pose.at().y - cam.y, pose.at().z - cam.z);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - pose.yaw()));
			// Nose down (sniffing) or up, round the middle of him.
			matrices.translate(0.0, 0.3, 0.0);
			matrices.multiply(RotationAxis.POSITIVE_X.rotation(pose.pitch()));
			matrices.translate(0.0, -0.3, 0.0);
			matrices.scale(pose.wide(), pose.tall(), pose.wide());
			matrices.scale(-1.0F, -1.0F, 1.0F);
			matrices.translate(0.0, -1.501, 0.0);
			model.animate(pose.walking() ? t * 1.1F : 0.0F, pose.walking() ? 0.7F : 0.0F, t, false);
			model.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(POCHITA)), light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
			matrices.pop();
		}
	}

	/** The pool of blood spreading out from under {@code player}, flat on the ground. */
	private static void pool(MatrixStack matrices, VertexConsumerProvider consumers, ClientWorld world, PlayerEntity player, Vec3d cam, float t,
			float tickDelta) {
		float spread = easeOut((t - MergeTimeline.FELL) / (MergeTimeline.COMING - MergeTimeline.FELL + 10));
		float more = 0.2F * easeOut((t - MergeTimeline.SINK) / 10.0F);
		float size = POOL_SIZE * (spread + more);
		if (size <= 0.01F) {
			return;
		}
		Vec3d mid = along(player, tickDelta, CHEST * 0.9, 0.0).add(0.0, 0.015, 0.0);
		int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(mid.add(0.0, 0.3, 0.0)));
		VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(POOL));
		matrices.push();
		matrices.translate(mid.x - cam.x, mid.y - cam.y, mid.z - cam.z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw(player) + 20.0F));
		Matrix4f m = matrices.peek().getPositionMatrix();
		float[][] corners = {{-size, -size, 0.0F, 0.0F}, {-size, size, 0.0F, 1.0F}, {size, size, 1.0F, 1.0F}, {size, -size, 1.0F, 0.0F}};
		for (float[] c : corners) {
			out.vertex(m, c[0], 0.0F, c[1]).color(0xFFFFFFFF).texture(c[2], c[3]).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0.0F, 1.0F, 0.0F);
		}
		matrices.pop();
	}

	/** Every tick: dust as he comes, the blood fountain as he goes in, a shiver of blood on each beat. */
	static void tick(ClientWorld world) {
		for (Map.Entry<UUID, Merging> e : MERGING.entrySet()) {
			PlayerEntity player = world.getPlayerByUuid(e.getKey());
			if (player == null) {
				continue;
			}
			int t = (int) (world.getTime() - e.getValue().at());
			Vec3d chest = chest(player, 0.0F);
			if (t == MergeTimeline.FELL) {
				Vec3d mid = along(player, 0.0F, CHEST, 0.1);
				for (int i = 0; i < 16; i++) {
					world.addParticle(ParticleTypes.POOF, mid.x + (RANDOM.nextDouble() - 0.5) * 1.2, mid.y, mid.z + (RANDOM.nextDouble() - 0.5) * 1.2,
							(RANDOM.nextDouble() - 0.5) * 0.06, 0.02, (RANDOM.nextDouble() - 0.5) * 0.06);
				}
			}
			for (int i = 0; i < MergeTimeline.LINES.length && i < VOICES.length; i++) {
				if (t == MergeTimeline.LINES[i]) {
					MinecraftClient.getInstance().getSoundManager().play(new net.minecraft.client.sound.PositionedSoundInstance(VOICES[i],
							net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F, Random.create(), chest.x, chest.y, chest.z));
				}
			}
			if (t >= MergeTimeline.COMING && t < MergeTimeline.SNIFF && t % 3 == 0) {
				Vec3d at = pochita(player, e.getValue(), t, 0.0F).at();
				world.addParticle(ParticleTypes.POOF, at.x, at.y + 0.05, at.z, 0.0, 0.01, 0.0);
			}
			if (t >= MergeTimeline.SINK && t < MergeTimeline.SUNK) {
				for (int i = 0; i < 14; i++) {
					world.addParticle(i % 3 == 0 ? DARK_BLOOD : BLOOD, chest.x, chest.y + 0.1, chest.z, (RANDOM.nextDouble() - 0.5) * 0.45,
							0.25 + RANDOM.nextDouble() * 0.55, (RANDOM.nextDouble() - 0.5) * 0.45);
				}
			}
			for (int beat : MergeTimeline.BEATS) {
				if (t == beat) {
					for (int i = 0; i < 10; i++) {
						float a = i / 10.0F * MathHelper.TAU;
						world.addParticle(BLOOD, chest.x, chest.y - 0.1, chest.z, MathHelper.cos(a) * 0.12, 0.05, MathHelper.sin(a) * 0.12);
					}
				}
			}
			if (t > MergeTimeline.END + io.github.kortev.chainsawman.form.Merge.WAIT + 40) {
				ended(e.getKey());
			}
		}
	}

	static void clear() {
		MERGING.clear();
		DRAWN_YAW.clear();
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	private static double ease(double x) {
		return ease((float) x);
	}

	private static float easeOut(double x) {
		float k = MathHelper.clamp((float) x, 0.0F, 1.0F);
		return 1.0F - (1.0F - k) * (1.0F - k);
	}

	private static float easeIn(double x) {
		float k = MathHelper.clamp((float) x, 0.0F, 1.0F);
		return k * k;
	}
}
