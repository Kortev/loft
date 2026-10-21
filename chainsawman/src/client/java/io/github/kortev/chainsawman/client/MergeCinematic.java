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
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Pochita becoming your heart, as a cinematic: the anime's scene, long, cut by cut as the storyboard kortev approved
 * ({@link MergeTimeline} lists the cuts). Everyone round about sees it play out and hears him; you see it through black
 * bars, his lines under the picture.
 *
 * <p>Low at the ground you collapse, the colour going out of the world; from straight above, rising, blood spreads from
 * under you and the world goes black round it. Pochita trots to you out of the dark (followed at his height), nudges your
 * cheek with his nose, hops up and stands on your chest. Over his head onto your face you ask if he took your body; your
 * own eyes look up at him sitting on you, head on one side; the memories; he leans in, close, and makes the deal. He
 * springs up off you into the light; you reach up after him, "POCHITA?!", and he dives at you; from straight above he
 * plunges into your chest, blood bursting, and all that's left is his tail, the cord, sticking out of you. In the black
 * your heart starts, ドクン, three beats; your eyes open on the sky; you get up; and the camera eases in on the cord until
 * you pull it: the transformation.
 *
 * <p>Every shot is kept out of the blocks round you (pulled in toward what it looks at) and a step off you and him; when
 * the camera is in your own eyes your head isn't drawn ({@link #ownEyes}).
 */
public final class MergeCinematic {
	/** Someone merging: when it started, and where Pochita comes from. */
	public record Merging(long at, Vec3d from, boolean waiting) {
	}

	/**
	 * Where Pochita is and how he's held at a moment: his feet, the way he faces, his nose up or down, his head on one side
	 * (the whole of him leaned over), his squash, and whether he's walking or sitting.
	 */
	private record Pose(Vec3d at, float yaw, float pitch, float roll, float wide, float tall, boolean walking, boolean sitting, float lie) {
	}

	private static final Map<UUID, Merging> MERGING = new ConcurrentHashMap<>();
	/** The way each merging body was last drawn lying (its body yaw as the renderer had it), so the camera and Pochita agree. */
	private static final Map<UUID, Float> DRAWN_YAW = new ConcurrentHashMap<>();
	private static final Identifier POCHITA = ChainsawMan.id("textures/entity/pochita.png");
	private static final Identifier POOL = ChainsawMan.id("textures/entity/blood_pool.png");
	/** The dream's floor: black, a pool of light in the middle (and, added on, the light he goes into you as). */
	private static final Identifier FLOOR = ChainsawMan.id("textures/entity/dream_floor.png");
	/** A soft round light, black at its edges (it's added onto the picture). */
	private static final Identifier GLOW = ChainsawMan.id("textures/entity/dream_glow.png");
	/** How far the dream's floor reaches round you (blocks). */
	private static final float FLOOR_SIZE = 14.0F;
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.62F, 0.02F, 0.04F), 0.9F);
	private static final DustParticleEffect MOTE = new DustParticleEffect(new Vector3f(1.0F, 0.93F, 0.75F), 0.45F);
	private static final DustParticleEffect DARK_BLOOD = new DustParticleEffect(new Vector3f(0.35F, 0.0F, 0.02F), 1.1F);
	private static final Random RANDOM = Random.create();
	/** How far up you lie off the ground, where along you (from your feet) your chest and face are (blocks). */
	private static final double LIFT = 0.15;
	private static final double CHEST = 0.95;
	private static final double CHEST_UP = 0.28;
	/** How far over the middle of your lying body your shirt is (blocks): what he stands on. */
	private static final double SURFACE = 0.14;
	/** Where along you he's over your chest as he dives into it (his feet's middle). */
	private static final double ON = 0.78;
	/** How far in front of his feet his snout reaches (blocks), and how far his feet are from your middle at your side. */
	private static final double SNOUT = 0.6;
	private static final double BESIDE = 0.9;
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
	/**
	 * How loud each voice is, tick by tick (0 to 9), measured off the clips themselves (form_merge_voice_1..5.ogg): his
	 * mouth moves with his, your head with yours. Measure again if the clips change.
	 */
	private static final String[] VOICE_LEVELS = {
			"0000000122143353100000000000000000079300000000298988318555830000000000000000017762598569702024410",
			"00000005940754555774100000000000019988836776557777774577844587211107898221220368930010",
			"00000000395788970135678811638300",
			"0000000000004606633588799639557012210000000000",
			"055567699765864442212111121111110"};

	private MergeCinematic() {
	}

	static void setModel(PochitaModel pochita) {
		model = pochita;
	}

	@Nullable
	static PochitaModel pochitaModel() {
		return model;
	}

	/**
	 * How far into the dream your own merge is (0 to 1): the world falling away into the black from DARK, coming back
	 * with the last beats (WAKE to RISE). The fog, the sky, the other things in the world all follow it.
	 */
	public static float dream(float tickDelta) {
		float t = mine(tickDelta);
		if (t < 0.0F) {
			return 0.0F;
		}
		return Math.min(ease((t - MergeTimeline.DARK) / 30.0F), 1.0F - ease((t - MergeTimeline.WAKE) / (MergeTimeline.EYES - MergeTimeline.WAKE)));
	}

	/** How loud line {@code line} of the talk is {@code t} ticks into the merge (0 to 1); 0 when it isn't playing. */
	public static float voice(int line, float t) {
		String levels = VOICE_LEVELS[line];
		float in = t - MergeTimeline.LINES[line];
		if (in < 0.0F || in >= levels.length() - 1) {
			return 0.0F;
		}
		int i = (int) in;
		float a = (levels.charAt(i) - '0') / 9.0F;
		float b = (levels.charAt(i + 1) - '0') / 9.0F;
		return MathHelper.lerp(in - i, a, b);
	}

	/** How open his mouth is at {@code t}: with his voice, on his lines. */
	private static float mouth(float t) {
		float open = 0.0F;
		for (int i = 0; i < MergeTimeline.LINES.length; i++) {
			if (MergeTimeline.POCHITA_SAYS[i]) {
				open = Math.max(open, voice(i, t));
			}
		}
		return open;
	}

	/** The screen's old-photograph browns, over the memories. */
	public static float sepia(float tickDelta) {
		float t = mine(tickDelta);
		return t < 0.0F ? 0.0F : MergeMemories.sepia(t);
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

	/** Your shirt at your chest, where his paws go. */
	private static Vec3d chestTop(PlayerEntity player, float tickDelta) {
		return along(player, tickDelta, CHEST, LIFT + SURFACE);
	}

	/** Which side of you he comes to (1 or -1, along the way your right hand lies): the side he comes from. */
	private static double side(PlayerEntity player, Merging m, float tickDelta) {
		Vec3d side = Vec3d.fromPolar(0.0F, yaw(player) + 90.0F);
		return side.dotProduct(m.from().subtract(along(player, tickDelta, YOU, 0.0))) >= 0.0 ? 1.0 : -1.0;
	}

	/** Where he sets off from: where he was, but never nearer than FROM_NEAR nor further than FROM, on your ground. */
	private static Vec3d start(PlayerEntity player, Merging m, float tickDelta) {
		Vec3d cheek = along(player, tickDelta, YOU, 0.0);
		Vec3d away = m.from().subtract(cheek).multiply(1.0, 0.0, 1.0);
		double far = MathHelper.clamp(away.length(), MergeTimeline.FROM_NEAR, MergeTimeline.FROM);
		Vec3d dir = away.lengthSquared() < 1.0E-4 ? Vec3d.fromPolar(0.0F, yaw(player) + 90.0F) : away.normalize();
		return new Vec3d(cheek.x, player.getLerpedPos(tickDelta).y, cheek.z).add(dir.multiply(far));
	}

	/**
	 * Whether your own camera is in your own eyes now (or just past the top of your head, looking up along you): your
	 * head isn't drawn then, or you'd see the inside of it.
	 */
	public static boolean ownEyes() {
		float t = mine(0.0F);
		return t >= MergeTimeline.SINK && t < MergeTimeline.PLUNGE || t >= MergeTimeline.WAKE && t < MergeTimeline.RISE;
	}

	private static float mine(float tickDelta) {
		ClientPlayerEntity self = MinecraftClient.getInstance().player;
		return self != null ? age(self, tickDelta) : -1.0F;
	}

	/** How far the camera may go from your body, in your merge, before you hear from your body instead (blocks). */
	private static final double HEARD = 8.0;

	/**
	 * Where you hear from while your own merge plays, with the camera at {@code cam}: your head, when the camera has gone
	 * far from it (the memories, high over you); otherwise null (the camera, as always).
	 */
	@Nullable
	public static Vec3d hearing(Vec3d cam) {
		ClientPlayerEntity self = MinecraftClient.getInstance().player;
		if (self == null || age(self, 0.0F) < 0.0F) {
			return null;
		}
		Vec3d head = self.getEyePos();
		return cam.squaredDistanceTo(head) > HEARD * HEARD ? head : null;
	}

	/** Whether your own merge is playing. */
	public static boolean filming(float tickDelta) {
		return mine(tickDelta) >= 0.0F;
	}

	// The camera.

	/** Your camera's shot of your own merge, cut by cut ({@link MergeTimeline}), while it plays; otherwise null. */
	@Nullable
	public static FormCinematic.Shot shot(float tickDelta) {
		ClientPlayerEntity self = MinecraftClient.getInstance().player;
		float t = mine(tickDelta);
		Merging m = self != null ? MERGING.get(self.getUuid()) : null;
		if (self == null || m == null || t < 0.0F) {
			return null;
		}
		Vec3d feet = self.getLerpedPos(tickDelta);
		Vec3d head = headward(self);
		Vec3d front = head.negate();
		Vec3d side = Vec3d.fromPolar(0.0F, yaw(self) + 90.0F);
		Vec3d chest = chestTop(self, tickDelta);
		Vec3d face = along(self, tickDelta, YOU, LIFT + 0.2);
		Pose pochita = pochita(self, m, t, tickDelta);
		Vec3d him = pochita.at().add(0.0, 0.4, 0.0);
		Vec3d forward = Vec3d.fromPolar(0.0F, pochita.yaw());
		Vec3d snout = pochita.at().add(forward.multiply(SNOUT - 0.1)).add(0.0, 0.42, 0.0);
		double which = side(self, m, tickDelta);
		// Past the top of your head, low: your own eyes, as near as the camera can be to them.
		Vec3d yours = along(self, tickDelta, 2.35, LIFT + 0.46);
		if (t < MergeTimeline.OVER) {
			// 1, Down: low at the ground off your side, you collapsing in front of it.
			double k = ease(t / MergeTimeline.OVER);
			float l = lying(self, tickDelta);
			Vec3d mid = feet.add(head.multiply(0.85 * l)).add(0.0, MathHelper.lerp(l, 0.95, 0.35), 0.0);
			return framed(feet.add(side.multiply(-which * MathHelper.lerp(k, 2.5, 2.1))).add(head.multiply(0.8)).add(0.0, MathHelper.lerp(k, 0.3, 0.8), 0.0), mid);
		}
		if (t < MergeTimeline.DARK) {
			// 2, Blood: straight down over you, rising slowly, turning a little.
			double k = (t - MergeTimeline.OVER) / (double) (MergeTimeline.DARK - MergeTimeline.OVER);
			return overhead(chest, head, MathHelper.lerp(ease(k), 2.3, 3.3), 12.0F * (float) k);
		}
		if (t < MergeTimeline.COMING) {
			// 3, The dark: higher still, the world going out round the pool.
			double k = (t - MergeTimeline.DARK) / (double) (MergeTimeline.COMING - MergeTimeline.DARK);
			return overhead(chest, head, MathHelper.lerp(ease(k), 3.3, 4.6), 12.0F + 12.0F * (float) k);
		}
		if (t < MergeTimeline.SNIFF) {
			// He comes: low and wide across you, your body in front in its light, him coming out of the dark beyond you.
			Vec3d mid = chest.lerp(pochita.at(), 0.5);
			return framed(chest.add(side.multiply(-which * 3.6)).add(front.multiply(0.8)).add(0.0, 0.55, 0.0), mid.add(0.0, 0.25, 0.0),
					chest.add(0.0, 0.3, 0.0));
		}
		if (t < MergeTimeline.LINES[0]) {
			// At your side, nosing at you, lying down against you: from past your feet, low, the two of you in your light.
			double k = ease((t - MergeTimeline.SNIFF) / (double) (MergeTimeline.LINES[0] - MergeTimeline.SNIFF));
			Vec3d both = chest.lerp(pochita.at(), 0.45);
			return framed(chest.add(front.multiply(MathHelper.lerp(k, 2.9, 2.4))).add(side.multiply(which * 0.9)).add(0.0, 0.75, 0.0),
					both.add(0.0, 0.18, 0.0));
		}
		if (MergeMemories.on(t)) {
			// Your life with him, the scenes his words call up.
			return MergeMemories.shot(feet, t);
		}
		if (t < MergeTimeline.MONTAGE) {
			// You ask him: from past your head, down your body at him lying against you, lifting his head to you; easing in.
			double k = ease((t - MergeTimeline.LINES[0]) / (double) (MergeTimeline.MONTAGE - MergeTimeline.LINES[0]));
			Vec3d both = face.lerp(him, 0.5);
			return framed(face.add(head.multiply(MathHelper.lerp(k, 2.3, 1.9))).add(side.multiply(which * 0.45)).add(0.0, MathHelper.lerp(k, 0.95, 0.8), 0.0),
					both, both.add(0.0, 0.1, 0.0));
		}
		if (t < MergeTimeline.LEAP) {
			// The deal: low across you, him at your side beyond you, the light behind him; easing in, never close.
			double k = ease((t - MergeTimeline.MONTAGE_END) / (double) (MergeTimeline.LEAP - MergeTimeline.MONTAGE_END));
			return framed(chest.add(side.multiply(-which * MathHelper.lerp(k, 3.0, 2.3))).add(head.multiply(0.45)).add(0.0, 0.5, 0.0),
					him.add(0.0, 0.05, 0.0), him);
		}
		if (t < MergeTimeline.SINK) {
			// The leap: wide and low across you, looking up as he springs up over you into the light.
			Vec3d by = along(self, tickDelta, CHEST, LIFT);
			return framed(by.add(side.multiply(-which * 2.8)).add(front.multiply(0.5)).add(0.0, 0.3, 0.0), him.add(0.0, 0.1, 0.0), him);
		}
		if (t < MergeTimeline.PLUNGE) {
			// 13, POCHITA?!: your own eyes, your hand reaching up after him, him diving down at you.
			return framed(yours.add(0.0, 0.1, 0.0), him.lerp(chest, 0.15), him);
		}
		if (t < MergeTimeline.WAKE) {
			// 14, Into your heart: down on your chest at a slant from beside your waist; him plunging in; the cord left in you.
			double k = ease((t - MergeTimeline.PLUNGE) / (double) (MergeTimeline.WAKE - MergeTimeline.PLUNGE));
			Vec3d slant = chest.add(front.multiply(MathHelper.lerp(k, 1.05, 0.85))).add(side.multiply(which * 0.4)).add(0.0, MathHelper.lerp(k, 1.25, 1.0), 0.0);
			return framed(slant, chest.add(0.0, 0.06, 0.0));
		}
		if (t < MergeTimeline.RISE) {
			// 15 and 16, the heartbeats in the black, and your eyes opening: your own eyes, looking straight up at the sky.
			Vec3d eye = along(self, tickDelta, YOU, LIFT + 0.3);
			float drift = MathHelper.sin(t * 0.13F) * 1.5F;
			return new FormCinematic.Shot(eye.x, eye.y, eye.z, yaw(self) + 180.0F + drift, -88.0F);
		}
		if (t < MergeTimeline.END) {
			// 17, Rise: low in front of you, as you sit up and get to your feet.
			double k = ease(rising(t));
			Vec3d look = feet.add(front.multiply(0.25)).add(0.0, MathHelper.lerp(k, 0.35, 1.1), 0.0);
			return framed(feet.add(front.multiply(2.4)).add(side.multiply(0.35)).add(0.0, 0.32, 0.0), look);
		}
		// 18, The cord: in front at chest height, easing in on its handle until you pull it (the transformation's own camera
		// takes over from there).
		double k = 1.0 - Math.exp(-(t - MergeTimeline.END) / 80.0);
		Vec3d cord = feet.add(front.multiply(0.2)).add(0.0, 1.0, 0.0);
		return framed(feet.add(front.multiply(MathHelper.lerp(k, 2.6, 1.9))).add(side.multiply(0.25)).add(0.0, 1.1, 0.0), cord);
	}

	/** Straight down on {@code at} from {@code high} over it, your head at the top of the picture, turned {@code turn} degrees. */
	private static FormCinematic.Shot overhead(Vec3d at, Vec3d head, double high, float turn) {
		Vec3d tip = head.rotateY(turn * MathHelper.RADIANS_PER_DEGREE).multiply(-0.06 * high);
		return framed(at.add(tip).add(0.0, high, 0.0), at, at.add(0.0, 0.2, 0.0));
	}

	/** How long the lens is for each cut, as a share of your field of view. */
	private static float lens(float t) {
		if (t < MergeTimeline.OVER) {
			return 0.7F;
		}
		if (t < MergeTimeline.COMING) {
			return 0.65F;
		}
		if (t < MergeTimeline.SNIFF) {
			return 0.9F;
		}
		if (MergeMemories.on(t)) {
			return 1.0F;
		}
		if (t < MergeTimeline.LEAP) {
			return 0.85F;
		}
		if (t < MergeTimeline.PLUNGE) {
			return 0.95F;
		}
		if (t < MergeTimeline.WAKE) {
			return 0.7F;
		}
		if (t < MergeTimeline.RISE) {
			return 1.1F;
		}
		if (t < MergeTimeline.END) {
			return 0.8F;
		}
		return 0.75F;
	}

	/** The camera at {@code at} looking at {@code look}, kept out of the blocks between it and what it looks at. */
	private static FormCinematic.Shot framed(Vec3d at, Vec3d look) {
		return framed(at, look, look);
	}

	/**
	 * The camera at {@code at} looking at {@code look}; if a block stands between it and {@code subject}, pulled in toward
	 * {@code subject} to this side of it.
	 */
	private static FormCinematic.Shot framed(Vec3d at, Vec3d look, Vec3d subject) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null && client.player != null) {
			HitResult hit = client.world.raycast(new RaycastContext(subject, at, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE,
					client.player));
			if (hit.getType() != HitResult.Type.MISS) {
				Vec3d back = at.subtract(subject);
				double reach = Math.max(0.35, subject.distanceTo(hit.getPos()) - 0.25);
				at = subject.add(back.multiply(reach / Math.max(back.length(), 1.0E-4)));
			}
		}
		return look(at, look);
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
		double s = Math.max(pulse(t, MergeTimeline.FELL, 0.14, 3.0), pulse(t, MergeTimeline.GASP, 0.1, 4.0));
		s = Math.max(s, pulse(t, MergeTimeline.PLUNGE, 0.2, 7.0));
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
		double punch = pulse(t, MergeTimeline.PLUNGE, 0.15, 6.0);
		for (int beat : MergeTimeline.BEATS) {
			punch = Math.max(punch, pulse(t, beat, 0.07, 3.0));
		}
		// Filmed tall (a reel, 9:16): the lens opened up, so what was framed for a wide picture still fits across it.
		net.minecraft.client.util.Window window = MinecraftClient.getInstance().getWindow();
		float tall = window.getFramebufferHeight() > window.getFramebufferWidth() ? 1.45F : 1.0F;
		return lens(t) * tall * (1.0F - (float) punch);
	}

	/** The red at the edges of the screen: throbbing with each beat, flooding as he plunges into you. */
	public static float vignette(float tickDelta) {
		float t = mine(tickDelta);
		if (t < 0.0F) {
			return 0.0F;
		}
		double v = pulse(t, MergeTimeline.PLUNGE, 1.0, 10.0);
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
		return 0.4F * Math.min(ease((t - MergeTimeline.DARK) / 30.0F), lifting(t));
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

	/**
	 * How open your eyes are, waking (0 shut to 1 open; 1 the rest of the time): shut through the heartbeats in the black,
	 * a flutter, then open wide with a gasp.
	 */
	public static float eyes(float tickDelta) {
		float t = mine(tickDelta);
		if (t < MergeTimeline.WAKE || t >= MergeTimeline.RISE) {
			return 1.0F;
		}
		float flutter = t >= MergeTimeline.EYES && t < MergeTimeline.EYES + 4 ? 0.3F * MathHelper.sin((t - MergeTimeline.EYES) / 4.0F * MathHelper.PI) : 0.0F;
		return Math.max(flutter, ease((t - MergeTimeline.GASP) / 4.0F));
	}

	/** How white the screen flashes as he goes into you. */
	public static float flash(float tickDelta) {
		float t = mine(tickDelta);
		if (t < 0.0F) {
			return 0.0F;
		}
		float memory = MergeMemories.flash(t);
		// White as he comes down on you, just before he plunges in.
		return Math.max(memory, (float) pulse(t, MergeTimeline.PLUNGE - 3, 1.0, 2.5));
	}

	/** The impact frames as he plunges into you: 1 red and black, 2 its negative; 0 none. */
	public static int impactStyle(float tickDelta) {
		float t = mine(tickDelta);
		if (t < MergeTimeline.PLUNGE || t >= MergeTimeline.PLUNGE + 4) {
			return 0;
		}
		return ((int) (t - MergeTimeline.PLUNGE)) % 2 == 0 ? 1 : 2;
	}

	/** The black bars closing in at the top and bottom as you die, and opening as you rise; his lines in the bottom one. */
	/** Whether the end shows "Pull the cord" in the bottom bar (not in a film: a reel's end isn't a game's). */
	public static boolean prompt = true;

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
		float open = eyes(tickDelta);
		if (open < 0.999F) {
			// Your eyelids, from the top and the bottom, the opening between them round like an eye's.
			float rx = w * 0.75F;
			float ry = screen * 0.5F * open * 1.35F;
			for (int x = 0; x < w; x += 2) {
				float across = (x + 1 - w / 2.0F) / rx;
				float half = across * across >= 1.0F ? 0.0F : ry * MathHelper.sqrt(1.0F - across * across);
				int top = (int) (screen / 2.0F - half);
				int bottom = (int) (screen / 2.0F + half);
				ctx.fill(x, 0, x + 2, Math.max(0, top), 0xFF000000);
				ctx.fill(x, Math.min(screen, bottom), x + 2, screen, 0xFF000000);
			}
		}
		// Each beat of your heart in the black, a flash of red.
		float red = 0.0F;
		for (int beat : MergeTimeline.BEATS) {
			red = Math.max(red, (float) pulse(t, beat, 0.7, 2.5));
		}
		if (red > 0.01F) {
			ctx.fill(0, 0, w, screen, ((int) (red * 255) << 24) | 0x8A0A12);
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
		if (prompt && m != null && m.waiting()) {
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

	/**
	 * Pochita {@code t} ticks into {@code player}'s merge: trotting to you on the ground out of the dark; nosing at you;
	 * lying down against your side, his chin on your chest, lifting his head at your voice; up at your side to make the
	 * deal, nodding with his words, bowing his head to your chest; springing up over you; diving; plunging into your chest.
	 */
	private static Pose pochita(PlayerEntity player, Merging m, float t, float tickDelta) {
		Vec3d head = headward(player);
		Vec3d side = Vec3d.fromPolar(0.0F, yaw(player) + 90.0F);
		double which = side(player, m, tickDelta);
		// At your side by your chest, on the ground, facing you.
		Vec3d byYou = along(player, tickDelta, CHEST, 0.0).add(side.multiply(which * BESIDE));
		Vec3d in = side.multiply(-which);
		float toYou = yawTo(in);
		float toFace = yawTo(along(player, tickDelta, YOU, 0.0).subtract(byYou));
		Vec3d top = along(player, tickDelta, ON, LIFT + SURFACE);
		float onYaw = yawTo(head);
		float said = mouth(t);
		if (t < MergeTimeline.SNIFF) {
			// Trotting to you out of the dark, a little hobble in it, slowing as he comes, turning to you at the end.
			Vec3d from = start(player, m, tickDelta);
			double k = easeOut((t - MergeTimeline.COMING) / (double) (MergeTimeline.SNIFF - MergeTimeline.COMING));
			Vec3d at = from.lerp(byYou, k);
			double hob = Math.abs(MathHelper.sin(t * 0.85F)) * 0.05 * (1.0 - k);
			float yaw = MathHelper.lerpAngleDegrees(ease((float) ((k - 0.85) / 0.15)), yawTo(byYou.subtract(from)), toYou);
			return new Pose(at.add(0.0, hob, 0.0), yaw, MathHelper.sin(t * 0.85F) * 0.06F * (float) (1.0 - k), 0.0F, 1.0F, 1.0F, k < 0.98, false, 0.0F);
		}
		if (t < MergeTimeline.LIES) {
			// At your side: nosing at you, twice.
			float nudge = (float) (pulse(t, MergeTimeline.SNIFF + 5, 1.0, 3.0) + pulse(t, MergeTimeline.SNIFF + 15, 0.8, 3.0));
			return new Pose(byYou.add(in.multiply(0.06 * nudge)), toYou, 0.3F + 0.15F * nudge, 0.0F, 1.0F, 1.0F, false, false, 0.0F);
		}
		if (t < MergeTimeline.LINES[2]) {
			// Lying down against you, his chin on your chest; lifting his head to your face as you speak to him; up again
			// as his own words come.
			float down = ease((t - MergeTimeline.LIES) / 12.0F) * (1.0F - ease((t - MergeTimeline.LINES[2] + 12.0F) / 10.0F));
			float lift = ease((t - MergeTimeline.LINES[0]) / 10.0F)
					* (1.0F - ease((t - MergeTimeline.LINES[0] - MergeTimeline.LINE_LENGTHS[0]) / 15.0F));
			float breath = MathHelper.sin(t * 0.12F) * 0.02F;
			float yaw = MathHelper.lerpAngleDegrees(lift, toYou, toFace);
			return new Pose(byYou.add(in.multiply(0.12 * down)), yaw, 0.2F * down - 0.3F * lift, 0.0F, 1.0F - breath, 1.0F + breath, false, false,
					down);
		}
		if (t < MergeTimeline.LEAP) {
			// At your side to make the deal: nodding along with his words, his head bowing to your chest at the end.
			float bow = ease((t - MergeTimeline.LINES[3] - 24.0F) / 12.0F);
			float nod = said * (0.14F + 0.06F * MathHelper.sin(t * 0.6F));
			float puff = 0.03F * said;
			return new Pose(byYou.add(in.multiply(0.1 * bow)), toYou, 0.05F + nod + 0.38F * bow, 0.05F * said * MathHelper.sin(t * 0.45F),
					1.0F + puff * 0.5F, 1.0F + puff, false, false, 0.0F);
		}
		if (t >= MergeTimeline.PLUNGE) {
			// In: down into your chest nose first, swallowed, smaller as he goes.
			float sink = ease((t - MergeTimeline.PLUNGE) / (float) (MergeTimeline.SUNK - MergeTimeline.PLUNGE));
			return new Pose(top.add(0.0, -0.55 * sink, 0.0), onYaw, 1.55F, 0.0F, 1.0F - 0.5F * sink, 1.0F - 0.5F * sink, false, false, 0.0F);
		}
		// The leap: a crouch, up from your side over you into the light, slow at the top as he turns nose down, then the
		// dive into your chest.
		float crouch = ease((t - MergeTimeline.LEAP) / 6.0F) * (1.0F - ease((t - MergeTimeline.LEAP - 7.0F) / 2.0F));
		float rise = easeOut((t - MergeTimeline.LEAP - 8.0F) / 12.0F);
		float dive = easeIn((t - MergeTimeline.SINK - 4.0F) / (MergeTimeline.PLUNGE - MergeTimeline.SINK - 4.0F));
		Vec3d over = byYou.lerp(top, ease((t - MergeTimeline.LEAP - 8.0F) / 12.0F));
		double height = 1.8 * rise * (1.0 - dive);
		float pitch = 0.1F + 1.45F * ease((t - MergeTimeline.LEAP - 14.0F) / 10.0F);
		float yaw = MathHelper.lerpAngleDegrees(ease((t - MergeTimeline.LEAP - 8.0F) / 12.0F), toYou, onYaw);
		float stretch = 0.25F * rise * (1.0F - dive) + 0.35F * dive;
		return new Pose(over.add(0.0, height, 0.0), yaw, pitch, 0.0F, 1.0F + 0.2F * crouch - 0.1F * stretch, 1.0F - 0.25F * crouch + stretch, false,
				false, 0.0F);
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
			boolean mine = player == MinecraftClient.getInstance().player;
			if (mine) {
				floor(matrices, consumers, player, cam, tickDelta);
				if (MergeMemories.on(t) && player instanceof net.minecraft.client.network.AbstractClientPlayerEntity self) {
					MergeMemories.render(self, player.getLerpedPos(tickDelta), t, matrices, consumers, cam, world);
				}
			}
			pool(matrices, consumers, world, player, cam, t, tickDelta);
			light(matrices, consumers, player, cam, t, tickDelta);
			if (t < MergeTimeline.COMING || t >= MergeTimeline.SUNK) {
				continue;
			}
			Pose pose = pochita(player, e.getValue(), t, tickDelta);
			int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(pose.at().add(0.0, 0.3, 0.0)));
			float d = mine ? dream(tickDelta) : 0.0F;
			if (d > 0.01F) {
				// In your dream: dark out in it, warm in your light as he comes to you.
				double far = pose.at().distanceTo(chestTop(player, tickDelta));
				float near = MathHelper.clamp((float) (3.4 - far) / 2.4F, 0.0F, 1.0F);
				// The light behind him as he makes the deal: his shape against it, his front in shadow.
				float halo = ease((t - MergeTimeline.LINES[2] + 10.0F) / 15.0F) * (1.0F - ease((t - MergeTimeline.LEAP - 4.0F) / 8.0F));
				int lit = (int) MathHelper.lerp(d, 15.0F, MathHelper.lerp(halo, MathHelper.lerp(near, 2.0F, 14.0F), 6.0F));
				light = net.minecraft.client.render.LightmapTextureManager.pack(lit, 0);
				if (halo > 0.01F) {
					Vec3d c = pose.at().add(0.0, 0.4, 0.0);
					Vec3d back = new Vec3d(c.x - cam.x, 0.0, c.z - cam.z).normalize();
					glow(matrices, consumers, c.add(back.multiply(1.5)).add(0.0, 0.25, 0.0), cam, 1.9F * halo, 0.8F * halo);
				}
			}
			matrices.push();
			matrices.translate(pose.at().x - cam.x, pose.at().y - cam.y, pose.at().z - cam.z);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - pose.yaw()));
			// Nose down (sniffing, diving) or up, and leaned over to one side, round the middle of him.
			matrices.translate(0.0, 0.3, 0.0);
			matrices.multiply(RotationAxis.POSITIVE_X.rotation(pose.pitch()));
			matrices.multiply(RotationAxis.POSITIVE_Z.rotation(pose.roll()));
			matrices.translate(0.0, -0.3, 0.0);
			matrices.scale(pose.wide(), pose.tall(), pose.wide());
			// Lying down: as low as his legs were long.
			matrices.translate(0.0, -PochitaModel.LEGS * pose.lie(), 0.0);
			matrices.scale(-1.0F, -1.0F, 1.0F);
			matrices.translate(0.0, -1.501, 0.0);
			boolean talking = t >= MergeTimeline.TALK && t < MergeTimeline.LEAP;
			// His tail going faster while he talks to you.
			model.animate(pose.walking() ? t * 1.1F : 0.0F, pose.walking() ? 0.7F : 0.0F, talking ? t * 1.8F : t, pose.sitting());
			model.lie(pose.lie());
			model.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(POCHITA)), light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
			matrices.pop();
		}
	}

	/** The dream's floor under you: black, and a pool of light where you lie, faded in and out with the dream. */
	private static void floor(MatrixStack matrices, VertexConsumerProvider consumers, PlayerEntity player, Vec3d cam, float tickDelta) {
		float d = dream(tickDelta);
		if (d <= 0.01F) {
			return;
		}
		Vec3d mid = chest(player, tickDelta);
		Vec3d at = new Vec3d(mid.x, player.getLerpedPos(tickDelta).y + 0.004, mid.z);
		VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityTranslucent(FLOOR));
		matrices.push();
		matrices.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		Matrix4f m = matrices.peek().getPositionMatrix();
		int colour = ((int) (d * 255) << 24) | 0xFFFFFF;
		float s = FLOOR_SIZE;
		float[][] corners = {{-s, -s, 0.0F, 0.0F}, {-s, s, 0.0F, 1.0F}, {s, s, 1.0F, 1.0F}, {s, -s, 1.0F, 0.0F}};
		for (float[] c : corners) {
			out.vertex(m, c[0], 0.0F, c[1]).color(colour).texture(c[2], c[3]).overlay(OverlayTexture.DEFAULT_UV)
					.light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0.0F, 1.0F, 0.0F);
		}
		matrices.pop();
	}

	/** The light he springs up into, high over you as he leaps; and its burst at your chest as he plunges in. */
	private static void light(MatrixStack matrices, VertexConsumerProvider consumers, PlayerEntity player, Vec3d cam, float t, float tickDelta) {
		Vec3d chest = chestTop(player, tickDelta);
		float high = ease((t - MergeTimeline.LEAP - 4.0F) / 10.0F) * (1.0F - ease((t - MergeTimeline.SINK - 4.0F) / 12.0F));
		glow(matrices, consumers, chest.add(0.0, 2.4, 0.0), cam, 1.7F * high, 0.9F * high);
		float burst = (float) pulse(t, MergeTimeline.PLUNGE, 1.0, 3.0);
		glow(matrices, consumers, chest.add(0.0, 0.08, 0.0), cam, 1.0F * burst, burst);
	}

	/** A soft glow, added onto the picture, facing the camera. */
	static void glow(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d at, Vec3d cam, float size, float bright) {
		if (size <= 0.01F || bright <= 0.01F) {
			return;
		}
		org.joml.Quaternionf facing = MinecraftClient.getInstance().gameRenderer.getCamera().getRotation();
		Vector3f right = facing.transform(new Vector3f(size, 0.0F, 0.0F));
		Vector3f up = facing.transform(new Vector3f(0.0F, size, 0.0F));
		VertexConsumer out = consumers.getBuffer(RenderLayer.getEyes(GLOW));
		Matrix4f m = matrices.peek().getPositionMatrix();
		int c = (int) (255 * MathHelper.clamp(bright, 0.0F, 1.0F));
		int colour = 0xFF000000 | c << 16 | (int) (c * 0.92F) << 8 | (int) (c * 0.8F);
		float x = (float) (at.x - cam.x);
		float y = (float) (at.y - cam.y);
		float z = (float) (at.z - cam.z);
		float[][] corners = {{-1, -1, 0, 1}, {-1, 1, 0, 0}, {1, 1, 1, 0}, {1, -1, 1, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}, {-1, -1, 0, 1}};
		for (float[] q : corners) {
			out.vertex(m, x + right.x * q[0] + up.x * q[1], y + right.y * q[0] + up.y * q[1], z + right.z * q[0] + up.z * q[1]).color(colour)
					.texture(q[2], q[3]).overlay(OverlayTexture.DEFAULT_UV)
					.light(net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0.0F, 1.0F, 0.0F);
		}
	}

	/** The pool of blood spreading out from under {@code player}, flat on the ground. */
	private static void pool(MatrixStack matrices, VertexConsumerProvider consumers, ClientWorld world, PlayerEntity player, Vec3d cam, float t,
			float tickDelta) {
		float spread = easeOut((t - MergeTimeline.FELL) / (MergeTimeline.COMING - MergeTimeline.FELL + 10));
		float more = 0.2F * easeOut((t - MergeTimeline.PLUNGE) / 10.0F);
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
				// A little dust where you hit (not so much it hides you).
				for (int i = 0; i < 5; i++) {
					world.addParticle(ParticleTypes.POOF, mid.x + (RANDOM.nextDouble() - 0.5) * 1.4, mid.y - 0.05, mid.z + (RANDOM.nextDouble() - 0.5) * 1.4,
							(RANDOM.nextDouble() - 0.5) * 0.08, 0.01, (RANDOM.nextDouble() - 0.5) * 0.08);
				}
			}
			for (int i = 0; i < MergeTimeline.LINES.length && i < VOICES.length; i++) {
				if (t == MergeTimeline.LINES[i]) {
					MinecraftClient.getInstance().getSoundManager().play(new net.minecraft.client.sound.PositionedSoundInstance(VOICES[i],
							net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F, Random.create(), chest.x, chest.y, chest.z));
				}
			}
			if (player == MinecraftClient.getInstance().player && dream(0.0F) > 0.5F && !MergeMemories.on(t)) {
				// Motes of light drifting up in the dark round you, small and warm.
				for (int i = 0; i < 2; i++) {
					double a = RANDOM.nextDouble() * Math.PI * 2.0;
					double r = 0.6 + RANDOM.nextDouble() * 4.0;
					world.addParticle(MOTE, chest.x + Math.cos(a) * r, chest.y - 0.3 + RANDOM.nextDouble() * 2.4, chest.z + Math.sin(a) * r, 0.0, 0.2,
							0.0);
				}
			}
			if (t >= MergeTimeline.PLUNGE && t < MergeTimeline.PLUNGE + 5) {
				// He's in: blood bursting up out of you.
				Vec3d in = chestTop(player, 0.0F);
				for (int i = 0; i < 14; i++) {
					world.addParticle(i % 3 == 0 ? DARK_BLOOD : BLOOD, in.x, in.y + 0.05, in.z, (RANDOM.nextDouble() - 0.5) * 0.45,
							0.25 + RANDOM.nextDouble() * 0.55, (RANDOM.nextDouble() - 0.5) * 0.45);
				}
			}
			if (player == MinecraftClient.getInstance().player) {
				for (int beat : MergeTimeline.BEATS) {
					if (t == beat) {
						// Your heart starting, lettered as the manga letters it.
						Sfx.show(Sfx.HEARTBEAT, 0.16F, 14, world.getTime());
					}
				}
			}
			for (int beat : MergeTimeline.BEATS) {
				// (Not into the look inside your chest.)
				if (t == beat && t >= MergeTimeline.SUNK) {
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
