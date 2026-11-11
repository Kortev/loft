package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The smear a slash leaves, as the anime draws one: an arc of white going to blood red swept out behind each saw's tip as it cuts, hanging
 * a few ticks along the swing and thinning as it goes, with a hot white edge of light burning along the tip's path. Where the tip and the
 * middle of the bar are is taken each frame as the arm is drawn ({@link FormFeature#draw}), and the arc laid through them, smoothed into a
 * curve: in the world for everyone you see slashing, and in your own view, in front of you, for your own arms (there it goes quickly, so
 * nothing stays over the middle of the view).
 */
public final class SawTrails {
	private static final Identifier TEXTURE = ChainsawMan.id("textures/entity/form_trail.png");
	/** How long the smear lasts behind the tip (ticks), and from how far down the bar it starts (a share of its length). */
	private static final float LASTS = 5.0F;
	private static final float INNER = 0.4F;
	/** In your own view: the bar stands up into the picture, so the band starts lower on it, and goes sooner. */
	private static final float VIEW_LASTS = 2.0F;
	private static final float VIEW_INNER = 0.15F;
	/** How far the smear's inner edge closes in on the tip's path as it ages (a share of its width): it thins away, not just fades. */
	private static final float THIN = 0.75F;
	/** The hot edge along the tip's path: how wide a share of the smear it is, and how bright (0 to 255). */
	private static final float EDGE = 0.3F;
	private static final int EDGE_BRIGHT = 255;
	/** How many pieces each stretch between two samples is laid in, along the curve through them. */
	private static final int SMOOTH = 4;

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

	/**
	 * Whether {@code player}'s {@code arm} (right_arm, left_arm, or the head) is cutting now: the one sweeping, all three in the big cut (the
	 * head's saw nods down with the arms), the head lunging.
	 */
	private static boolean slashing(PlayerEntity player, String arm, float tickDelta) {
		ClientCombat.Swing swing = ClientCombat.swing(player);
		float age = ClientCombat.swingAge(player, tickDelta);
		if (swing == null || age < 0.0F) {
			return false;
		}
		float k = age / ClientCombat.SWING;
		return switch (swing.step()) {
			// (A sweep cuts from its wind-up to its follow-through; not while it is raised, nor carried on after.)
			case 0 -> arm.equals("right_arm") && k > 0.18F && k < 0.62F;
			case 1 -> arm.equals("left_arm") && k > 0.18F && k < 0.62F;
			case 2 -> k > 0.28F && k < 0.62F;
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
		Sample last = samples.peekLast();
		if (last != null && Math.abs(last.at() - at) < 1.0E-4F) {
			// (Drawn twice in one frame, as for an outline: once is enough.)
			return;
		}
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
		List<Sample[]> strips = new ArrayList<>();
		for (Deque<Sample> samples : map.values()) {
			samples.removeIf(s -> now - s.at() > lasts || s.at() > now + 1.0F);
			if (samples.size() >= 2) {
				strips.add(smooth(new ArrayList<>(samples)));
			}
		}
		map.values().removeIf(Deque::isEmpty);
		if (strips.isEmpty()) {
			return;
		}
		// The smear itself, white at the tip's edge going to blood red in toward the bar, thinning and fading as it ages.
		VertexConsumer band = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
		for (Sample[] strip : strips) {
			for (int i = strip.length - 1; i > 0; i--) {
				Sample newer = strip[i];
				Sample older = strip[i - 1];
				float u0 = MathHelper.clamp((now - newer.at()) / lasts, 0.0F, 1.0F);
				float u1 = MathHelper.clamp((now - older.at()) / lasts, 0.0F, 1.0F);
				Vec3d in0 = thinned(newer, u0);
				Vec3d in1 = thinned(older, u1);
				corner(band, m, newer.tip(), cam, u0, 0.0F, fade(u0));
				corner(band, m, in0, cam, u0, 1.0F, fade(u0));
				corner(band, m, in1, cam, u1, 1.0F, fade(u1));
				corner(band, m, older.tip(), cam, u1, 0.0F, fade(u1));
			}
		}
		// The hot edge, added onto the picture: a thin line of light burning along the tip's path, gone first.
		VertexConsumer edge = consumers.getBuffer(RenderLayer.getLightning());
		for (Sample[] strip : strips) {
			for (int i = strip.length - 1; i > 0; i--) {
				Sample newer = strip[i];
				Sample older = strip[i - 1];
				float a0 = glow((now - newer.at()) / lasts);
				float a1 = glow((now - older.at()) / lasts);
				if (a0 <= 0.0F && a1 <= 0.0F) {
					continue;
				}
				Vec3d e0 = newer.tip().lerp(newer.inner(), EDGE);
				Vec3d e1 = older.tip().lerp(older.inner(), EDGE);
				// Both ways round (the layer culls).
				light(edge, m, newer.tip(), cam, a0);
				light(edge, m, e0, cam, a0 * 0.15F);
				light(edge, m, e1, cam, a1 * 0.15F);
				light(edge, m, older.tip(), cam, a1);
				light(edge, m, older.tip(), cam, a1);
				light(edge, m, e1, cam, a1 * 0.15F);
				light(edge, m, e0, cam, a0 * 0.15F);
				light(edge, m, newer.tip(), cam, a0);
			}
		}
	}

	/** The samples, with {@link #SMOOTH} pieces laid between each two along a curve through them (the tip and the inner edge both). */
	private static Sample[] smooth(List<Sample> s) {
		List<Sample> out = new ArrayList<>();
		for (int i = 0; i < s.size() - 1; i++) {
			Sample p0 = s.get(Math.max(0, i - 1));
			Sample p1 = s.get(i);
			Sample p2 = s.get(i + 1);
			Sample p3 = s.get(Math.min(s.size() - 1, i + 2));
			for (int j = 0; j < SMOOTH; j++) {
				float t = j / (float) SMOOTH;
				out.add(new Sample(MathHelper.lerp(t, p1.at(), p2.at()), curve(p0.tip(), p1.tip(), p2.tip(), p3.tip(), t),
						curve(p0.inner(), p1.inner(), p2.inner(), p3.inner(), t)));
			}
		}
		out.add(s.get(s.size() - 1));
		return out.toArray(new Sample[0]);
	}

	/** A Catmull-Rom curve through p1 and p2 (with p0 and p3 either side), {@code t} of the way. */
	private static Vec3d curve(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, float t) {
		double t2 = t * t;
		double t3 = t2 * t;
		return p1.multiply(2.0).add(p2.subtract(p0).multiply(t)).add(p0.multiply(2.0).subtract(p1.multiply(5.0)).add(p2.multiply(4.0)).subtract(p3).multiply(t2))
				.add(p1.multiply(3.0).subtract(p0).subtract(p2.multiply(3.0)).add(p3).multiply(t3)).multiply(0.5);
	}

	/** The smear's inner edge {@code u} of the way through its life: closing in on the tip's path. */
	private static Vec3d thinned(Sample s, float u) {
		return s.inner().lerp(s.tip(), THIN * u * u);
	}

	/** How opaque the smear is {@code u} of the way through its life. */
	private static float fade(float u) {
		return (1.0F - u) * (1.0F - u * 0.4F);
	}

	/** How bright the hot edge is {@code u} of the way through the smear's life: full at first, gone by half. */
	private static float glow(float u) {
		return MathHelper.clamp(1.0F - u * 2.0F, 0.0F, 1.0F);
	}

	private static void corner(VertexConsumer out, Matrix4f m, Vec3d at, Vec3d cam, float u, float v, float alpha) {
		int a = MathHelper.clamp((int) (255 * alpha), 0, 255);
		out.vertex(m, (float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z)).color(255, 228, 214, a).texture(Math.min(u, 1.0F), v)
				.overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0.0F, 1.0F, 0.0F);
	}

	private static void light(VertexConsumer out, Matrix4f m, Vec3d at, Vec3d cam, float k) {
		int a = MathHelper.clamp((int) (EDGE_BRIGHT * k), 0, 255);
		out.vertex(m, (float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z)).color(255, 236, 214, a);
	}

	static void clear() {
		WORLD.clear();
		VIEW.clear();
	}
}
