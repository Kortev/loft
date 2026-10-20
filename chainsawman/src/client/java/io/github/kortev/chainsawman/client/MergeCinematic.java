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
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Pochita becoming your heart, as a cinematic ({@link MergeTimeline}). Everyone round about sees it: you go over
 * backwards and lie there dead; Pochita comes limping to your body, climbs onto your chest, looks at your face, and sinks
 * into you in a burst of blood; your heart starts, beat by beat, the cord comes out of your chest, and you get up. You
 * see it from outside your body: high overhead in the dark, down on the ground as he comes, close on him on your
 * chest, straight down into the burst (impact frames), over your face as the beats come, and back as you rise; the world
 * dark and silent to begin with, the edges of the screen throbbing red with each beat.
 */
public final class MergeCinematic {
	/** Someone merging: when it started, and where Pochita comes from. */
	public record Merging(long at, Vec3d from) {
	}

	private static final Map<UUID, Merging> MERGING = new ConcurrentHashMap<>();
	/** The way each merging body was last drawn lying (its body yaw as the renderer had it), so the camera and Pochita agree. */
	private static final Map<UUID, Float> DRAWN_YAW = new ConcurrentHashMap<>();
	private static final Identifier POCHITA = ChainsawMan.id("textures/entity/pochita.png");
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.62F, 0.02F, 0.04F), 1.2F);
	private static final Random RANDOM = Random.create();
	/** How far up you lie off the ground, and where your chest is along you from your feet (blocks). */
	private static final double LIFT = 0.15;
	private static final double CHEST = 0.95;
	private static final double CHEST_UP = 0.28;
	private static final double FACE = 1.42;

	private static PochitaModel model;

	private MergeCinematic() {
	}

	static void setModel(PochitaModel pochita) {
		model = pochita;
	}

	static void started(UUID player, long at, Vec3d from) {
		MERGING.put(player, new Merging(at, from));
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
		return t >= 0.0F && t <= MergeTimeline.END + 2 ? t : -1.0F;
	}

	/** How far over {@code player} has gone, lying (0 standing, 1 flat on their back). */
	public static float lying(PlayerEntity player, float tickDelta) {
		float t = age(player, tickDelta);
		if (t < 0.0F) {
			return 0.0F;
		}
		if (t < MergeTimeline.FELL) {
			float k = t / MergeTimeline.FELL;
			return k * k;
		}
		return 1.0F - ease((t - MergeTimeline.RISE) / (MergeTimeline.END - MergeTimeline.RISE));
	}

	/** The way your head lies from your feet: back the way you faced (you went over backwards). */
	public static Vec3d headward(PlayerEntity player) {
		return Vec3d.fromPolar(0.0F, yaw(player) + 180.0F);
	}

	/** The body's yaw as it was last drawn lying, or as it is. */
	private static float yaw(PlayerEntity player) {
		return DRAWN_YAW.getOrDefault(player.getUuid(), player.getBodyYaw());
	}

	/** The renderer laying {@code player} down at {@code bodyYaw}. */
	public static void drawn(PlayerEntity player, float bodyYaw) {
		DRAWN_YAW.put(player.getUuid(), bodyYaw);
	}

	private static Vec3d chest(PlayerEntity player, float tickDelta) {
		return player.getLerpedPos(tickDelta).add(headward(player).multiply(CHEST)).add(0.0, LIFT + CHEST_UP, 0.0);
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
		if (self == null || m == null || t < 0.0F || t >= MergeTimeline.END) {
			return null;
		}
		Vec3d chest = chest(self, tickDelta);
		Vec3d head = self.getLerpedPos(tickDelta).add(headward(self).multiply(FACE)).add(0.0, LIFT + 0.25, 0.0);
		Vec3d side = Vec3d.fromPolar(0.0F, yaw(self) + 90.0F);
		Vec3d pochita = pochitaAt(self, m, t);
		if (t < MergeTimeline.COMING) {
			// High over your body in the dark, turning slowly, coming down a little.
			double k = t / MergeTimeline.COMING;
			Vec3d out = Vec3d.fromPolar(0.0F, yaw(self) + 40.0F * (float) k).multiply(0.4);
			return look(chest.add(out).add(0.0, MathHelper.lerp(k, 4.5, 3.2), 0.0), chest);
		}
		if (t < MergeTimeline.CLIMB) {
			// Down on the ground on your far side from him, watching him come.
			Vec3d away = pochita.subtract(chest).multiply(1.0, 0.0, 1.0).normalize();
			return look(chest.subtract(away.multiply(2.0)).subtract(headward(self).multiply(0.9)).add(0.0, 0.35, 0.0), pochita.add(0.0, 0.25, 0.0));
		}
		if (t < MergeTimeline.SINK) {
			// Close beside you, on him on your chest.
			double k = ease((t - MergeTimeline.CLIMB) / (MergeTimeline.SINK - MergeTimeline.CLIMB));
			return look(chest.add(side.multiply(MathHelper.lerp(k, 2.3, 1.8))).add(headward(self).multiply(-0.4))
					.add(0.0, MathHelper.lerp(k, 0.8, 1.1), 0.0), pochita.add(0.0, 0.25, 0.0));
		}
		if (t < MergeTimeline.BEAT) {
			// Straight down into the burst.
			return look(chest.add(side.multiply(0.05)).add(0.0, 1.15, 0.0), chest);
		}
		if (t < MergeTimeline.RISE) {
			// Over your face as your heart starts, closer with each beat.
			double k = (t - MergeTimeline.BEAT) / (MergeTimeline.RISE - MergeTimeline.BEAT);
			return look(head.add(headward(self).multiply(-0.12)).add(0.0, MathHelper.lerp(k, 1.3, 0.75), 0.0), head);
		}
		// Back off as you get up; the transformation's own camera takes over from there.
		double k = ease((t - MergeTimeline.RISE) / (MergeTimeline.END - MergeTimeline.RISE));
		Vec3d front = Vec3d.fromPolar(0.0F, yaw(self));
		Vec3d eye = self.getLerpedPos(tickDelta).add(0.0, 1.0, 0.0);
		return look(eye.add(front.multiply(MathHelper.lerp(k, 1.5, 2.4))).add(0.0, MathHelper.lerp(k, 1.2, 0.4), 0.0), eye.add(0.0, 0.2 * k, 0.0));
	}

	private static FormCinematic.Shot look(Vec3d at, Vec3d look) {
		Vec3d to = look.subtract(at);
		float yaw = (float) (MathHelper.atan2(to.z, to.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * MathHelper.DEGREES_PER_RADIAN);
		return new FormCinematic.Shot(at.x, at.y, at.z, yaw, MathHelper.clamp(pitch, -89.9F, 89.9F));
	}

	/** How hard your camera shakes: the burst, and each beat. */
	public static double shake(float tickDelta) {
		float t = mine(tickDelta);
		if (t < 0.0F) {
			return 0.0;
		}
		double s = pulse(t, MergeTimeline.FELL, 0.05, 3.0);
		s = Math.max(s, pulse(t, MergeTimeline.SINK, 0.18, 7.0));
		for (int beat : MergeTimeline.BEATS) {
			s = Math.max(s, pulse(t, beat, 0.06, 2.5));
		}
		return s;
	}

	/** How far your view is punched in: each beat. */
	public static float fov(float tickDelta) {
		float t = mine(tickDelta);
		if (t < 0.0F) {
			return 1.0F;
		}
		double punch = pulse(t, MergeTimeline.SINK, 0.15, 6.0);
		for (int beat : MergeTimeline.BEATS) {
			punch = Math.max(punch, pulse(t, beat, 0.08, 3.0));
		}
		return 1.0F - (float) punch;
	}

	/** The red at the edges of the screen: throbbing with each beat, flooding at the burst. */
	public static float vignette(float tickDelta) {
		float t = mine(tickDelta);
		if (t < 0.0F) {
			return 0.0F;
		}
		double v = pulse(t, MergeTimeline.SINK, 1.0, 10.0);
		for (int beat : MergeTimeline.BEATS) {
			v = Math.max(v, pulse(t, beat, 0.8, 4.0));
		}
		return (float) v;
	}

	/** The impact frames as he sinks into you: 1 red and black, 2 its negative; 0 none. */
	public static int impactStyle(float tickDelta) {
		float t = mine(tickDelta);
		if (t < MergeTimeline.SINK || t >= MergeTimeline.SINK + 4) {
			return 0;
		}
		return ((int) (t - MergeTimeline.SINK)) % 2 == 0 ? 1 : 2;
	}

	/** The world going dark as you die, and coming back with your heart. */
	public static void darkness(DrawContext ctx, float tickDelta) {
		float t = mine(tickDelta);
		if (t < 0.0F) {
			return;
		}
		float dark;
		if (t < MergeTimeline.FELL) {
			dark = 0.9F * t / MergeTimeline.FELL;
		} else if (t < MergeTimeline.COMING) {
			dark = MathHelper.lerp((t - MergeTimeline.FELL) / (MergeTimeline.COMING - MergeTimeline.FELL), 0.9F, 0.55F);
		} else if (t < MergeTimeline.SINK) {
			dark = 0.55F;
		} else {
			dark = 0.4F * (1.0F - ease((t - MergeTimeline.SINK) / (MergeTimeline.RISE - MergeTimeline.SINK)));
		}
		if (dark > 0.01F) {
			ctx.fill(0, 0, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), ((int) (dark * 255) << 24));
		}
	}

	private static double pulse(float t, int at, double height, double fade) {
		return t >= at ? height * Math.exp(-(t - at) / fade) : 0.0;
	}

	// Pochita.

	/** Where Pochita is, {@code t} ticks into {@code player}'s merge: on his way, climbing, on your chest. */
	private static Vec3d pochitaAt(PlayerEntity player, Merging m, float t) {
		Vec3d chest = chest(player, 0.0F);
		Vec3d side = Vec3d.fromPolar(0.0F, yaw(player) + 90.0F);
		Vec3d ground = new Vec3d(chest.x, player.getY(), chest.z);
		Vec3d beside = ground.add(side.multiply(side.dotProduct(m.from().subtract(ground)) >= 0.0 ? 0.7 : -0.7));
		if (t < MergeTimeline.CLIMB) {
			// Limping: a hop every few ticks, slowing as he comes.
			double k = ease((t - MergeTimeline.COMING) / (MergeTimeline.CLIMB - MergeTimeline.COMING));
			Vec3d at = m.from().lerp(beside, k);
			return at.add(0.0, Math.abs(MathHelper.sin(t * 0.7F)) * 0.12 * (1.0 - k), 0.0);
		}
		Vec3d top = chest.subtract(0.0, 0.0, 0.0);
		if (t < MergeTimeline.ON_CHEST) {
			double k = ease((t - MergeTimeline.CLIMB) / (MergeTimeline.ON_CHEST - MergeTimeline.CLIMB));
			return beside.lerp(top, k).add(0.0, MathHelper.sin((float) k * MathHelper.PI) * 0.3, 0.0);
		}
		if (t < MergeTimeline.SINK) {
			return top;
		}
		// Into you.
		double k = ease((t - MergeTimeline.SINK) / (MergeTimeline.SUNK - MergeTimeline.SINK));
		return top.subtract(0.0, 0.3 * k, 0.0);
	}

	/** Pochita drawn into the world for everyone merging, as the entities are. */
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
			if (t < MergeTimeline.COMING || t >= MergeTimeline.SUNK) {
				continue;
			}
			Vec3d at = pochitaAt(player, e.getValue(), t);
			Vec3d chest = chest(player, tickDelta);
			// Facing where he's going, and on your chest, your face.
			Vec3d facing = t < MergeTimeline.ON_CHEST ? chest.subtract(at) : headward(player);
			float yaw = (float) Math.toDegrees(Math.atan2(-facing.x, facing.z));
			float size = t < MergeTimeline.SINK ? 1.0F : 1.0F - ease((t - MergeTimeline.SINK) / (MergeTimeline.SUNK - MergeTimeline.SINK));
			int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(at.add(0.0, 0.3, 0.0)));
			matrices.push();
			matrices.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
			matrices.scale(size, size, size);
			matrices.scale(-1.0F, -1.0F, 1.0F);
			matrices.translate(0.0, -1.501, 0.0);
			boolean walking = t < MergeTimeline.ON_CHEST;
			model.animate(walking ? t * 0.9F : 0.0F, walking ? 0.8F : 0.0F, t, t >= MergeTimeline.ON_CHEST);
			model.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(POCHITA)), light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
			matrices.pop();
		}
	}

	/** Every tick: the blood as he sinks into each body. */
	static void tick(ClientWorld world) {
		for (Map.Entry<UUID, Merging> e : MERGING.entrySet()) {
			PlayerEntity player = world.getPlayerByUuid(e.getKey());
			if (player == null) {
				continue;
			}
			int t = (int) (world.getTime() - e.getValue().at());
			Vec3d chest = chest(player, 0.0F);
			if (t >= MergeTimeline.SINK && t < MergeTimeline.SUNK) {
				for (int i = 0; i < 12; i++) {
					world.addParticle(BLOOD, chest.x, chest.y + 0.1, chest.z, (RANDOM.nextDouble() - 0.5) * 0.5, RANDOM.nextDouble() * 0.45,
							(RANDOM.nextDouble() - 0.5) * 0.5);
				}
			}
			if (t > MergeTimeline.END + 40) {
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
}
