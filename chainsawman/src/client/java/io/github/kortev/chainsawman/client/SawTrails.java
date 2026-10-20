package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The smear a slash leaves, as the anime draws one: a band of white going to blood red swept out behind each saw's tip
 * as it cuts, gone again in a few ticks. Where the tip and the middle of the bar are is taken each frame as the arm is
 * drawn ({@link FormFeature#draw}), and the band laid through the last of them: in the world for everyone you see
 * slashing, and in your own view, in front of you, for your own arms.
 */
public final class SawTrails {
	private static final Identifier TEXTURE = ChainsawMan.id("textures/entity/form_trail.png");
	/** How long the smear lasts behind the tip (ticks), and from how far down the bar it starts (a share of its length). */
	private static final float LASTS = 3.0F;
	private static final float INNER = 0.45F;
	/** In your own view: the bar stands up into the picture, so the band starts lower on it, and goes sooner. */
	private static final float VIEW_LASTS = 2.0F;
	private static final float VIEW_INNER = 0.1F;

	/** Where the tip and the inner edge were, and when (world time, in ticks with the frame's fraction). */
	private record Sample(float at, Vec3d tip, Vec3d inner) {
	}

	/** Others' arms (and your own, seen in third person), in the world; your own in first person, in your view. */
	private static final Map<String, Deque<Sample>> WORLD = new ConcurrentHashMap<>();
	private static final Map<String, Deque<Sample>> VIEW = new ConcurrentHashMap<>();
	/** Whether the arm being drawn now is your own in front of you (the view's space, not the world's). */
	public static boolean firstPerson;

	private SawTrails() {
	}

	/** Whether {@code player}'s {@code arm} (right_arm, left_arm, or the head) is cutting now: the one sweeping, both chopping, the head lunging. */
	private static boolean slashing(PlayerEntity player, String arm, float tickDelta) {
		ClientCombat.Swing swing = ClientCombat.swing(player);
		if (swing == null || ClientCombat.swingAge(player, tickDelta) < 0.0F) {
			return false;
		}
		return switch (swing.step()) {
			case 0 -> arm.equals("right_arm");
			case 1 -> arm.equals("left_arm");
			case 2 -> !arm.equals("head");
			case 3 -> arm.equals("head");
			default -> false;
		};
	}

	/**
	 * Takes where {@code player}'s saw on {@code arm} is, with the stack moved to the arm's bone: its tip, {@code reach}
	 * pixels out from {@code root} along {@code axis}.
	 */
	static void sample(AbstractClientPlayerEntity player, String arm, float tickDelta, MatrixStack.Entry entry, Vector3f root, Vector3f axis,
			float reach) {
		if (reach <= 0.0F || !slashing(player, arm, tickDelta)) {
			return;
		}
		Matrix4f m = entry.getPositionMatrix();
		Vector3f tip = m.transformPosition(new Vector3f(axis).mul(reach).add(root).div(16.0F));
		Vector3f inner = m.transformPosition(new Vector3f(axis).mul(reach * (firstPerson ? VIEW_INNER : INNER)).add(root).div(16.0F));
		float at = player.getWorld().getTime() + tickDelta;
		Vec3d offset = firstPerson ? Vec3d.ZERO : MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
		Map<String, Deque<Sample>> map = firstPerson ? VIEW : WORLD;
		Deque<Sample> samples = map.computeIfAbsent((firstPerson ? "" : player.getUuidAsString()) + arm, k -> new ArrayDeque<>());
		samples.addLast(new Sample(at, new Vec3d(tip.x, tip.y, tip.z).add(offset), new Vec3d(inner.x, inner.y, inner.z).add(offset)));
	}

	/** Everyone's smears in the world. */
	public static void render(WorldRenderContext context) {
		VertexConsumerProvider consumers = context.consumers();
		if (WORLD.isEmpty() || consumers == null || context.world() == null) {
			return;
		}
		float now = context.world().getTime() + context.tickCounter().getTickDelta(false);
		Vec3d cam = context.camera().getPos();
		MatrixStack matrices = context.matrixStack() != null ? context.matrixStack() : new MatrixStack();
		draw(WORLD, consumers, matrices.peek().getPositionMatrix(), now, cam, LASTS);
	}

	/** Your own smears, in front of you (with the stack as your arms are drawn). */
	static void renderView(VertexConsumerProvider consumers, PlayerEntity self, float tickDelta) {
		if (!VIEW.isEmpty()) {
			draw(VIEW, consumers, new Matrix4f(), self.getWorld().getTime() + tickDelta, Vec3d.ZERO, VIEW_LASTS);
		}
	}

	private static void draw(Map<String, Deque<Sample>> map, VertexConsumerProvider consumers, Matrix4f m, float now, Vec3d cam, float lasts) {
		VertexConsumer out = null;
		for (Deque<Sample> samples : map.values()) {
			samples.removeIf(s -> now - s.at() > lasts || s.at() > now + 1.0F);
			if (samples.size() < 2) {
				continue;
			}
			if (out == null) {
				out = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
			}
			Sample newer = null;
			for (Sample older : (Iterable<Sample>) samples::descendingIterator) {
				if (newer != null) {
					// u: from the tip's newest place (0) to the oldest (1); v: from the tip's edge (0) in to the bar.
					float u0 = (now - newer.at()) / lasts;
					float u1 = (now - older.at()) / lasts;
					corner(out, m, newer.tip(), cam, u0, 0.0F);
					corner(out, m, newer.inner(), cam, u0, 1.0F);
					corner(out, m, older.inner(), cam, u1, 1.0F);
					corner(out, m, older.tip(), cam, u1, 0.0F);
				}
				newer = older;
			}
		}
		map.values().removeIf(Deque::isEmpty);
	}

	private static void corner(VertexConsumer out, Matrix4f m, Vec3d at, Vec3d cam, float u, float v) {
		out.vertex(m, (float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z)).color(0xFFFFFFFF).texture(Math.min(u, 1.0F), v)
				.overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0.0F, 1.0F, 0.0F);
	}

	static void clear() {
		WORLD.clear();
		VIEW.clear();
	}
}
