package io.github.kortev.shootingstar.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.Aim;
import io.github.kortev.shootingstar.client.ClientStrike;
import io.github.kortev.shootingstar.client.ClientStrikes;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4fStack;

/** The lock beam and the reticle draped over the terrain around the target. */
public final class WorldEffects {
	private static final int GROUND_RADIUS = 16;
	private static final int GROUND_SIZE = GROUND_RADIUS * 2 + 1;
	private static final int RETICLE = 0xFFFF8A2A;

	private WorldEffects() {
	}

	public static void render(WorldRenderContext context) {
		ClientWorld world = context.world();
		float tickDelta = context.tickCounter().getTickDelta(false);
		boolean anyStrike = false;
		for (ClientStrike strike : ClientStrikes.all()) {
			if (strike.time(tickDelta) < StrikeTimeline.IMPACT + 2) {
				anyStrike = true;
				break;
			}
		}
		boolean aim = Aim.holding && Aim.target != null && Aim.cooldown <= 0.0F;
		if (!anyStrike && !aim) {
			return;
		}
		Vec3d cam = context.camera().getPos();

		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.set(context.positionMatrix());
		RenderSystem.applyModelViewMatrix();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		Gfx.additive();

		BufferBuilder b = Gfx.quads();
		for (ClientStrike strike : ClientStrikes.all()) {
			double t = strike.time(tickDelta);
			if (t >= StrikeTimeline.IMPACT + 2) {
				continue;
			}
			if (strike.ground == null) {
				strike.ground = sampleGround(world, strike.target);
			}
			beam(b, cam, strike, t);
			reticle(b, cam, strike, t);
		}
		if (aim) {
			aimMarker(b, cam, Aim.target, Aim.dangerClose, world.getTime() + tickDelta);
		}
		Gfx.draw(b);

		Gfx.alpha();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		modelView.popMatrix();
		RenderSystem.applyModelViewMatrix();
	}

	// --- beam ------------------------------------------------------------------------------

	private static void beam(BufferBuilder b, Vec3d cam, ClientStrike strike, double t) {
		double x = strike.center.x - cam.x;
		double z = strike.center.z - cam.z;
		double ground = strike.target.getY() + 1.0 - cam.y;
		double grow = MathHelper.clamp(t / 7.0, 0.0, 1.0);
		double bottom = MathHelper.lerp(grow * grow * (3 - 2 * grow), ground + 700.0, ground);
		double top = ground + 1400.0;
		double inbound = MathHelper.clamp((t - StrikeTimeline.INBOUND) / (StrikeTimeline.IMPACT - StrikeTimeline.INBOUND), 0.0, 1.0);
		double fadeOut = MathHelper.clamp((StrikeTimeline.IMPACT + 2 - t) / 2.0, 0.0, 1.0);
		float flicker = 0.85F + 0.15F * MathHelper.sin((float) t * 1.7F) * MathHelper.sin((float) t * 0.63F);
		float k = (float) (fadeOut * flicker);

		double len = Math.sqrt(x * x + z * z);
		float px = len < 1.0E-3 ? 1.0F : (float) (-z / len);
		float pz = len < 1.0E-3 ? 0.0F : (float) (x / len);

		float core = (float) (0.12 + inbound * 0.35);
		layer(b, x, bottom, top, z, px, pz, core, Gfx.fade(0xFFFFF4FF, 0.95F * k), Gfx.fade(0xFFE8D8FF, 0.0F));
		layer(b, x, bottom, top, z, px, pz, core * 4.0F, Gfx.fade(0xFFB89CFF, 0.35F * k), Gfx.fade(0xFFB89CFF, 0.0F));
		layer(b, x, bottom, top, z, px, pz, core * 14.0F, Gfx.fade(0xFFFF7A2A, (0.08F + (float) inbound * 0.12F) * k),
				Gfx.fade(0xFFFF7A2A, 0.0F));

		// Glow pooled where the beam meets the ground.
		float y = (float) (ground + 0.05);
		float radius = (float) (2.2 + inbound * 3.0);
		int inner = Gfx.fade(0xFFFFB070, 0.45F * k);
		int outer = Gfx.fade(0xFFFF7A2A, 0.0F);
		for (int i = 0; i < 24; i++) {
			double a0 = Math.PI * 2 * i / 24;
			double a1 = Math.PI * 2 * (i + 1) / 24;
			b.vertex((float) x, y, (float) z).color(inner);
			b.vertex((float) (x + Math.cos(a0) * radius), y, (float) (z + Math.sin(a0) * radius)).color(outer);
			b.vertex((float) (x + Math.cos(a1) * radius), y, (float) (z + Math.sin(a1) * radius)).color(outer);
			b.vertex((float) x, y, (float) z).color(inner);
		}
	}

	private static void layer(BufferBuilder b, double x, double y0, double y1, double z, float px, float pz, float width,
			int bottom, int top) {
		b.vertex((float) (x - px * width), (float) y0, (float) (z - pz * width)).color(bottom);
		b.vertex((float) (x + px * width), (float) y0, (float) (z + pz * width)).color(bottom);
		b.vertex((float) (x + px * width), (float) y1, (float) (z + pz * width)).color(top);
		b.vertex((float) (x - px * width), (float) y1, (float) (z - pz * width)).color(top);
	}

	// --- reticle ---------------------------------------------------------------------------

	private static void reticle(BufferBuilder b, Vec3d cam, ClientStrike strike, double t) {
		float appear = (float) MathHelper.clamp((t - 2.0) / 14.0, 0.0, 1.0);
		appear = appear * appear * (3 - 2 * appear);
		double inbound = MathHelper.clamp((t - StrikeTimeline.INBOUND) / (StrikeTimeline.IMPACT - StrikeTimeline.INBOUND), 0.0, 1.0);
		float pulse = 0.75F + 0.25F * MathHelper.sin((float) t * (0.25F + (float) inbound * 1.2F));
		int color = Gfx.fade(RETICLE, 0.9F * pulse);
		int dim = Gfx.fade(RETICLE, 0.55F * pulse);
		double spin = t * 0.012;

		arc(b, cam, strike, 7.0, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * appear, 0.16, color);
		for (int k = 0; k < 24; k += 2) {
			double a0 = Math.PI * 2 * k / 24 - spin;
			double a1 = Math.PI * 2 * (k + 1) / 24 - spin;
			if (k / 24.0 < appear) {
				arc(b, cam, strike, 11.5, a0, a1, 0.11, dim);
			}
		}
		for (int k = 0; k < 6; k++) {
			if (k / 6.0 >= appear) {
				break;
			}
			double a0 = spin + Math.PI / 3 * k;
			double a1 = spin + Math.PI / 3 * (k + 1);
			segment(b, cam, strike, Math.cos(a0) * 9.0, Math.sin(a0) * 9.0, Math.cos(a1) * 9.0, Math.sin(a1) * 9.0, 0.12, color);
		}
		for (int k = 0; k < 4; k++) {
			double a = Math.PI / 2 * k + Math.PI / 4 - spin * 0.5;
			segment(b, cam, strike, Math.cos(a) * 2.5, Math.sin(a) * 2.5, Math.cos(a) * 5.5, Math.sin(a) * 5.5, 0.14, color);
		}
		for (int k = 0; k < 12; k++) {
			double a = Math.PI / 6 * k;
			segment(b, cam, strike, Math.cos(a) * 12.6, Math.sin(a) * 12.6, Math.cos(a) * 14.2, Math.sin(a) * 14.2, 0.1, dim);
		}
		arc(b, cam, strike, 1.1, 0, Math.PI * 2, 0.12, color);
	}

	private static void arc(BufferBuilder b, Vec3d cam, ClientStrike strike, double radius, double from, double to,
			double width, int color) {
		int steps = Math.max(4, (int) (Math.abs(to - from) * radius / 0.5));
		for (int i = 0; i < steps; i++) {
			double a0 = from + (to - from) * i / steps;
			double a1 = from + (to - from) * (i + 1) / steps;
			ribbon(b, cam, strike, Math.cos(a0) * radius, Math.sin(a0) * radius, Math.cos(a1) * radius, Math.sin(a1) * radius,
					width, color);
		}
	}

	private static void segment(BufferBuilder b, Vec3d cam, ClientStrike strike, double x0, double z0, double x1, double z1,
			double width, int color) {
		double len = Math.sqrt((x1 - x0) * (x1 - x0) + (z1 - z0) * (z1 - z0));
		int steps = Math.max(1, (int) Math.ceil(len / 0.5));
		for (int i = 0; i < steps; i++) {
			double ta = (double) i / steps;
			double tb = (double) (i + 1) / steps;
			ribbon(b, cam, strike, MathHelper.lerp(ta, x0, x1), MathHelper.lerp(ta, z0, z1), MathHelper.lerp(tb, x0, x1),
					MathHelper.lerp(tb, z0, z1), width, color);
		}
	}

	/** A short flat strip laid on the ground between two offsets from the target centre. */
	private static void ribbon(BufferBuilder b, Vec3d cam, ClientStrike strike, double x0, double z0, double x1, double z1,
			double width, int color) {
		double dx = x1 - x0;
		double dz = z1 - z0;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-5) {
			return;
		}
		double nx = -dz / len * width * 0.5;
		double nz = dx / len * width * 0.5;
		double y0 = groundAt(strike, x0, z0) + 0.07 - cam.y;
		double y1 = groundAt(strike, x1, z1) + 0.07 - cam.y;
		double ox = strike.center.x - cam.x;
		double oz = strike.center.z - cam.z;
		b.vertex((float) (ox + x0 + nx), (float) y0, (float) (oz + z0 + nz)).color(color);
		b.vertex((float) (ox + x0 - nx), (float) y0, (float) (oz + z0 - nz)).color(color);
		b.vertex((float) (ox + x1 - nx), (float) y1, (float) (oz + z1 - nz)).color(color);
		b.vertex((float) (ox + x1 + nx), (float) y1, (float) (oz + z1 + nz)).color(color);
	}

	private static double groundAt(ClientStrike strike, double dx, double dz) {
		int ix = MathHelper.floor(strike.center.x + dx) - strike.target.getX() + GROUND_RADIUS;
		int iz = MathHelper.floor(strike.center.z + dz) - strike.target.getZ() + GROUND_RADIUS;
		if (strike.ground == null || ix < 0 || iz < 0 || ix >= GROUND_SIZE || iz >= GROUND_SIZE) {
			return strike.target.getY() + 1.0;
		}
		return strike.ground[ix * GROUND_SIZE + iz];
	}

	/** Top surface height of every column around the target, ignoring grass, flowers and snow layers. */
	private static float[] sampleGround(ClientWorld world, BlockPos target) {
		float[] ground = new float[GROUND_SIZE * GROUND_SIZE];
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int ix = 0; ix < GROUND_SIZE; ix++) {
			for (int iz = 0; iz < GROUND_SIZE; iz++) {
				int x = target.getX() + ix - GROUND_RADIUS;
				int z = target.getZ() + iz - GROUND_RADIUS;
				int top = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
				float height = target.getY() + 1.0F;
				if (top > world.getBottomY()) {
					for (int y = top; y > top - 24 && y > world.getBottomY(); y--) {
						BlockState state = world.getBlockState(pos.set(x, y, z));
						if (!state.getFluidState().isEmpty()) {
							height = y + 0.9F;
							break;
						}
						if (!state.isAir() && !state.getCollisionShape(world, pos).isEmpty()) {
							height = (float) (y + state.getCollisionShape(world, pos).getMax(Direction.Axis.Y));
							break;
						}
					}
				}
				ground[ix * GROUND_SIZE + iz] = height;
			}
		}
		return ground;
	}

	// --- aim preview -----------------------------------------------------------------------

	private static void aimMarker(BufferBuilder b, Vec3d cam, BlockPos target, boolean dangerClose, double time) {
		int color = dangerClose ? 0xCCFF3030 : 0xBBFF9A3A;
		double cx = target.getX() + 0.5 - cam.x;
		double cy = target.getY() + 1.06 - cam.y;
		double cz = target.getZ() + 0.5 - cam.z;
		double radius = 2.2 + 0.25 * Math.sin(time * 0.3);
		int steps = 32;
		for (int i = 0; i < steps; i++) {
			double a0 = Math.PI * 2 * i / steps;
			double a1 = Math.PI * 2 * (i + 1) / steps;
			flat(b, cx + Math.cos(a0) * radius, cy, cz + Math.sin(a0) * radius, cx + Math.cos(a1) * radius, cy,
					cz + Math.sin(a1) * radius, 0.08, color);
		}
		for (int k = 0; k < 4; k++) {
			double a = Math.PI / 2 * k + time * 0.02;
			flat(b, cx + Math.cos(a) * 0.6, cy, cz + Math.sin(a) * 0.6, cx + Math.cos(a) * 1.6, cy, cz + Math.sin(a) * 1.6, 0.08, color);
		}
	}

	private static void flat(BufferBuilder b, double x0, double y0, double z0, double x1, double y1, double z1, double width,
			int color) {
		double dx = x1 - x0;
		double dz = z1 - z0;
		double len = Math.sqrt(dx * dx + dz * dz);
		double nx = -dz / len * width * 0.5;
		double nz = dx / len * width * 0.5;
		b.vertex((float) (x0 + nx), (float) y0, (float) (z0 + nz)).color(color);
		b.vertex((float) (x0 - nx), (float) y0, (float) (z0 - nz)).color(color);
		b.vertex((float) (x1 - nx), (float) y1, (float) (z1 - nz)).color(color);
		b.vertex((float) (x1 + nx), (float) y1, (float) (z1 + nz)).color(color);
	}
}
