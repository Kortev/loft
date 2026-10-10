package io.github.kortev.shootingstar.client.gap;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.gfx.Fx;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.network.GapWarpPayload;
import io.github.kortev.shootingstar.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * The floor of nothing made visible where people stand on it, and people carried from place to place by the event.
 * <p>
 * Everyone held in the void stands on a faint disc of light, and every step on it sends a ripple out over the
 * nothing, so it can be seen that they stand on something. Someone carried off (to the rim, and home again) is held
 * in a column of light while they go, flashes out, crosses the sky as a streak if it is far, and flashes in where
 * they land, with a ring breaking over the ground there. For the one carried, the picture goes white as they go.
 */
public final class VoidFx {
	private static final Fx BATCH = new Fx();
	private static final int WHITE = 0xFFFFFF;
	private static final int PALE = 0xD8C8FF;
	private static final int VIOLET = 0xA070FF;
	/** How long the arrival goes on after the move, in ticks. */
	private static final int ARRIVAL = 26;
	private static final int RIPPLE = 26;

	private record Warp(UUID player, Vec3d from, Vec3d to, int delay, long start) {
	}

	private record Ripple(Vec3d at, long start, float size) {
	}

	/** Where someone last stepped on the floor, and whether they were on it last tick. */
	private record Step(Vec3d at, boolean down) {
	}

	/** How far apart steps on the floor of nothing are: a long, slow stride. */
	private static final double STRIDE = 2.6;
	private static final List<Warp> WARPS = new ArrayList<>();
	private static final List<Ripple> RIPPLES = new ArrayList<>();
	private static final Map<UUID, Step> STEPS = new HashMap<>();
	/** This player's own last warp, for the picture going white: kept through the change of world it may make. */
	private static Warp self;
	private static long ticks;
	private static int stepCount;

	private VoidFx() {
	}

	public static void onWarp(GapWarpPayload payload, MinecraftClient client) {
		WARPS.removeIf(w -> w.player().equals(payload.player()));
		Warp warp = new Warp(payload.player(), payload.from(), payload.to(), payload.delay(), ticks);
		WARPS.add(warp);
		if (client.player != null && payload.player().equals(client.player.getUuid())) {
			self = warp;
			client.getSoundManager().play(PositionedSoundInstance.master(ModSounds.GAP_SWAP, 1.25F, 0.8F));
		} else if (client.world != null) {
			client.world.playSound(payload.from().x, payload.from().y, payload.from().z, ModSounds.GAP_SWAP, SoundCategory.PLAYERS, 0.9F,
					1.15F, false);
		}
	}

	public static void clear() {
		leftWorld();
		self = null;
	}

	/** Into another world: what was drawn in the last one goes (but the picture going white as they were carried here). */
	public static void leftWorld() {
		WARPS.clear();
		RIPPLES.clear();
		STEPS.clear();
	}

	public static void tick(MinecraftClient client) {
		ticks++;
		WARPS.removeIf(w -> ticks - w.start() > w.delay() + ARRIVAL + 2);
		RIPPLES.removeIf(r -> ticks - r.start() > RIPPLE);
		if (!ClientGaps.floating() || client.world == null) {
			STEPS.clear();
			return;
		}
		// Everyone here is on the floor of nothing with this player: a soft step and a ripple every long stride they take
		// on it, nothing while they are off it in a jump, and a heavier one where they come down.
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			Vec3d feet = player.getPos();
			boolean down = player.isOnGround();
			Step last = STEPS.get(player.getUuid());
			if (last == null) {
				STEPS.put(player.getUuid(), new Step(feet, down));
				continue;
			}
			if (down && !last.down()) {
				step(client, feet, true);
				STEPS.put(player.getUuid(), new Step(feet, true));
			} else if (down && Math.hypot(feet.x - last.at().x, feet.z - last.at().z) > STRIDE) {
				step(client, feet, false);
				STEPS.put(player.getUuid(), new Step(feet, true));
			} else if (down != last.down()) {
				STEPS.put(player.getUuid(), new Step(last.at(), down));
			}
		}
	}

	/** A step on the floor of nothing: a ripple out over it, and a faint ring of glass. */
	private static void step(MinecraftClient client, Vec3d feet, boolean landed) {
		RIPPLES.add(new Ripple(feet, ticks, landed ? 1.6F : 1.0F));
		if (client.world != null) {
			float pitch = landed ? 0.7F : 1.1F + 0.08F * (stepCount++ % 3 - 1);
			client.world.playSound(feet.x, feet.y, feet.z, landed ? SoundEvents.BLOCK_AMETHYST_BLOCK_FALL : SoundEvents.BLOCK_AMETHYST_BLOCK_STEP,
					SoundCategory.PLAYERS, landed ? 0.5F : 0.28F, pitch, false);
		}
	}

	/** How white the local player's picture is as they are carried off, 0 to 1 (GapHud draws it). */
	public static float flash(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return 0.0F;
		}
		Warp warp = self;
		if (warp == null || !warp.player().equals(client.player.getUuid())) {
			return 0.0F;
		}
		double e = ticks + tickDelta - warp.start();
		if (e < warp.delay()) {
			return (float) (0.9 * Math.pow(e / warp.delay(), 2.0));
		}
		return (float) (0.9 * Math.max(0.0, 1.0 - (e - warp.delay()) / 16.0));
	}

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!Shaders.ready() || client.world == null || client.player == null || WARPS.isEmpty() && RIPPLES.isEmpty() && !ClientGaps.floating()) {
			return;
		}
		float tickDelta = context.tickCounter().getTickDelta(false);
		Vec3d cam = context.camera().getPos();
		Matrix4f view = new Matrix4f(context.positionMatrix());
		Matrix4f proj = new Matrix4f(context.projectionMatrix());
		Vector3f right = new Vector3f(view.m00(), view.m10(), view.m20());
		Vector3f up = new Vector3f(view.m01(), view.m11(), view.m21());
		double now = ticks + tickDelta;
		client.getFramebuffer().beginWrite(true);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

		if (ClientGaps.floating()) {
			// The floor under everyone: a faint disc of light and a ring at its edge, breathing.
			BATCH.begin(Fx.BLOB, 0.0F, view, proj, right, up);
			for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
				if (player.isSpectator() || warping(player.getUuid(), now)) {
					continue;
				}
				Vec3d feet = player.getLerpedPos(tickDelta).add(0.0, 0.03, 0.0);
				float breathe = 0.8F + 0.2F * (float) Math.sin(now * 0.12 + player.getId());
				BATCH.flat(GapRender.rel(feet, cam), new Vector3f(1.5F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.5F), Fx.fade(VIOLET, 0.32F * breathe));
				BATCH.flat(GapRender.rel(feet, cam), new Vector3f(0.7F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 0.7F), Fx.fade(PALE, 0.28F * breathe));
			}
			BATCH.end(true, 1.6F);
		}
		if (!RIPPLES.isEmpty()) {
			BATCH.begin(Fx.RING, 0.06F, view, proj, right, up);
			for (Ripple ripple : RIPPLES) {
				double k = MathHelper.clamp((now - ripple.start()) / RIPPLE, 0.0, 1.0);
				float size = (float) ((0.7 + 3.2 * Math.sqrt(k)) * ripple.size());
				float alpha = (float) (0.65 * Math.pow(1.0 - k, 1.6));
				BATCH.flat(GapRender.rel(ripple.at().add(0.0, 0.04, 0.0), cam), new Vector3f(size, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, size),
						Fx.fade(k < 0.15 ? WHITE : PALE, alpha));
			}
			BATCH.end(true, 2.0F);
		}
		for (Warp warp : WARPS) {
			drawWarp(warp, now, cam, view, proj, right, up);
		}
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
	}

	private static boolean warping(UUID player, double now) {
		for (Warp warp : WARPS) {
			if (warp.player().equals(player) && now - warp.start() < warp.delay() + 2) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Held in a column of light where they stand, motes rising off them, until they flash out; then (if it is far) a
	 * streak over the sky to where they go, and a flash there, a ring breaking over the ground and the column fading.
	 */
	private static void drawWarp(Warp warp, double now, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right, Vector3f up) {
		double e = now - warp.start();
		double d = warp.delay();
		Vector3f eye = new Vector3f();
		Vec3d from = warp.from();
		Vec3d to = warp.to();
		if (e < d + 4) {
			double held = MathHelper.clamp(e / d, 0.0, 1.0);
			float fade = (float) (e < d ? 1.0 : 1.0 - (e - d) / 4.0);
			BATCH.begin(Fx.BEAM, 0.0F, view, proj, right, up);
			BATCH.beam(GapRender.rel(from, cam), GapRender.rel(from.add(0.0, 2.4 + 6.0 * held * held, 0.0), cam), eye,
					(float) (0.55 + 0.35 * held), Fx.fade(WHITE, 0.85F * fade * (float) held), Fx.fade(VIOLET, 0.0F));
			BATCH.end(true, 2.2F);
			BATCH.begin(Fx.BLOB, 0.0F, view, proj, right, up);
			for (int i = 0; i < 18; i++) {
				double a = i * 2.4 + e * 0.25;
				double rise = ((e * 0.09 + i * 0.37) % 1.0) * 2.6;
				Vec3d p = from.add(Math.cos(a) * 0.55, 0.1 + rise, Math.sin(a) * 0.55);
				BATCH.sprite(GapRender.rel(p, cam), 0.07F, 0.0F, Fx.fade(i % 3 == 0 ? WHITE : PALE, (float) (held * (1.0 - rise / 2.6)) * fade));
			}
			if (e > d - 3) {
				// The flash as they go.
				float out = (float) Math.exp(-Math.abs(e - d) / 1.6);
				BATCH.sprite(GapRender.rel(from.add(0.0, 1.0, 0.0), cam), 2.6F, 0.0F, Fx.fade(WHITE, out));
			}
			BATCH.end(true, 2.4F);
		}
		double after = e - d;
		if (after < 0.0) {
			return;
		}
		double far = from.distanceTo(to);
		if (far > 12.0 && after < 9.0) {
			// A streak over the sky from where they were to where they land.
			double head = MathHelper.clamp(after / 6.0, 0.0, 1.0);
			double tail = MathHelper.clamp((after - 2.0) / 6.0, 0.0, 1.0);
			double lift = Math.min(far * 0.25, 40.0);
			BATCH.begin(Fx.BEAM, 0.0F, view, proj, right, up);
			Vec3d last = arc(from, to, tail, lift);
			for (int i = 1; i <= 8; i++) {
				double s = MathHelper.lerp(i / 8.0, tail, head);
				Vec3d next = arc(from, to, s, lift);
				BATCH.beam(GapRender.rel(last, cam), GapRender.rel(next, cam), eye, 0.35F, Fx.fade(PALE, 0.7F), Fx.fade(WHITE, 0.9F));
				last = next;
			}
			BATCH.end(true, 2.0F);
		}
		if (after > ARRIVAL) {
			return;
		}
		double k = after / ARRIVAL;
		BATCH.begin(Fx.BLOB, 0.0F, view, proj, right, up);
		float flash = (float) Math.exp(-after / 2.5);
		BATCH.sprite(GapRender.rel(to.add(0.0, 1.0, 0.0), cam), (float) (3.2 - 1.8 * k), 0.0F, Fx.fade(WHITE, flash));
		BATCH.sprite(GapRender.rel(to.add(0.0, 1.0, 0.0), cam), 1.8F, 0.0F, Fx.fade(VIOLET, (float) (0.5 * (1.0 - k))));
		for (int i = 0; i < 18; i++) {
			// Motes settling in round them.
			double a = i * 2.1 + after * 0.2;
			double r = 2.4 * (1.0 - k) + 0.4;
			Vec3d p = to.add(Math.cos(a) * r, 0.2 + 2.2 * (1.0 - k) * ((i * 0.53) % 1.0), Math.sin(a) * r);
			BATCH.sprite(GapRender.rel(p, cam), 0.07F, 0.0F, Fx.fade(i % 3 == 0 ? WHITE : PALE, (float) (1.0 - k)));
		}
		BATCH.end(true, 2.4F);
		BATCH.begin(Fx.RING, 0.08F, view, proj, right, up);
		float ring = (float) (0.8 + 5.5 * Math.sqrt(k));
		BATCH.flat(GapRender.rel(to.add(0.0, 0.05, 0.0), cam), new Vector3f(ring, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, ring),
				Fx.fade(PALE, (float) (0.8 * Math.pow(1.0 - k, 1.5))));
		BATCH.end(true, 2.2F);
		BATCH.begin(Fx.BEAM, 0.0F, view, proj, right, up);
		BATCH.beam(GapRender.rel(to, cam), GapRender.rel(to.add(0.0, 7.0 * (1.0 - k) + 1.0, 0.0), cam), eye, (float) (0.8 * (1.0 - k) + 0.2),
				Fx.fade(WHITE, (float) (0.8 * (1.0 - k))), Fx.fade(VIOLET, 0.0F));
		BATCH.end(true, 2.2F);
	}

	/** Part way ({@code s}) along an arc from {@code a} up over to {@code b}, {@code lift} high at its top. */
	private static Vec3d arc(Vec3d a, Vec3d b, double s, double lift) {
		return a.lerp(b, s).add(0.0, 1.0 + lift * 4.0 * s * (1.0 - s), 0.0);
	}
}
