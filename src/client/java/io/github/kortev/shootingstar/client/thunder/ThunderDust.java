package io.github.kortev.shootingstar.client.thunder;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.gfx.NoiseTex;
import io.github.kortev.shootingstar.client.gfx.Post;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.thunder.ThunderTimeline;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Dust and steam in the world after Mjölnir's stroke, for everyone in sight of it: the shockwave, a ring of dust thrown
 * up off the ground racing out from the strike and slowing as it spreads, the colour of whatever ground it came off;
 * and steam boiling up off the fused crater for as long as the strike lasts. Soft puffs (ss_dust), moved once a tick and drawn back to
 * front.
 */
public final class ThunderDust {
	/** Steam rises off the crater until the strike is forgotten; the last of it drifts on and thins out after. */
	private static final int STEAM_TICKS = ThunderTimeline.END - ThunderTimeline.STROKE - 2;
	private static final List<Puff> PUFFS = new ArrayList<>();
	private static final Random RANDOM = new Random();

	private static final class Puff {
		double x;
		double y;
		double z;
		double px;
		double py;
		double pz;
		double vx;
		double vy;
		double vz;
		/** How fast it slows: its speed is kept by this much a tick. */
		double drag;
		float size;
		float prevSize;
		float grow;
		int age;
		int life;
		float r;
		float g;
		float b;
		float alpha;
		float seed;
	}

	private ThunderDust() {
	}

	/** The stroke has landed: the shockwave's ring of dust. */
	public static void onStroke(ClientWorld world, ClientThunder thunder) {
		double core = ThunderTimeline.coreRadius(thunder.radius);
		int count = MathHelper.clamp(thunder.radius, 24, 100);
		float big = (float) MathHelper.clamp(thunder.radius * 0.14, 3.0, 14.0);
		for (int i = 0; i < count; i++) {
			double a = Math.PI * 2.0 * (i + RANDOM.nextDouble() * 0.6) / count;
			double from = core * (0.75 + 0.2 * RANDOM.nextDouble());
			double x = thunder.center.x + Math.cos(a) * from;
			double z = thunder.center.z + Math.sin(a) * from;
			int ground = ground(world, x, z, thunder.center.y);
			Puff p = new Puff();
			double speed = (2.4 + 1.2 * RANDOM.nextDouble()) * Math.sqrt(thunder.radius / 64.0);
			p.vx = Math.cos(a) * speed;
			p.vz = Math.sin(a) * speed;
			p.vy = 0.05 + 0.1 * RANDOM.nextDouble();
			p.drag = 0.955;
			p.size = big * (0.5F + 0.3F * RANDOM.nextFloat());
			p.grow = big * 0.03F;
			p.life = 70 + RANDOM.nextInt(40);
			place(p, x, ground + p.size * 0.35, z);
			tint(p, world, BlockPos.ofFloored(x, ground - 1, z), 0.85F);
			PUFFS.add(p);
		}
	}

	/** Once a tick: the steam off each crater, and every puff moved on. */
	public static void tick(ClientWorld world) {
		for (ClientThunder thunder : ClientThunders.all()) {
			double e = thunder.age - ThunderTimeline.STROKE;
			if (!thunder.struck || !thunder.terrain || e < 6 || e > STEAM_TICKS) {
				continue;
			}
			double core = ThunderTimeline.coreRadius(thunder.radius);
			double depth = Math.max(3, Math.round(core * 0.5));
			double rate = 1.6 * Math.exp(-e / 220.0) * Math.sqrt(thunder.radius / 64.0);
			int n = (int) rate + (RANDOM.nextDouble() < rate - (int) rate ? 1 : 0);
			for (int i = 0; i < n; i++) {
				double a = RANDOM.nextDouble() * Math.PI * 2.0;
				double r = Math.sqrt(RANDOM.nextDouble()) * core * 0.85;
				Puff p = new Puff();
				p.vx = (RANDOM.nextDouble() - 0.5) * 0.06;
				p.vz = (RANDOM.nextDouble() - 0.5) * 0.06;
				p.vy = 0.18 + 0.2 * RANDOM.nextDouble();
				p.drag = 0.995;
				p.size = 1.5F + RANDOM.nextFloat() * 2.0F;
				p.grow = 0.06F;
				p.life = 90 + RANDOM.nextInt(60);
				// Off the floor of the bowl, deeper in the middle.
				double floor = thunder.center.y - depth * (1.0 - r / core * 0.7) + 1.0;
				place(p, thunder.center.x + Math.cos(a) * r, floor, thunder.center.z + Math.sin(a) * r);
				float grey = 0.72F + 0.1F * RANDOM.nextFloat();
				p.r = grey;
				p.g = grey + 0.02F;
				p.b = grey + 0.05F;
				p.alpha = 0.45F;
				PUFFS.add(p);
			}
		}
		for (Iterator<Puff> it = PUFFS.iterator(); it.hasNext(); ) {
			Puff p = it.next();
			p.px = p.x;
			p.py = p.y;
			p.pz = p.z;
			p.prevSize = p.size;
			p.x += p.vx;
			p.y += p.vy;
			p.z += p.vz;
			p.vx *= p.drag;
			p.vz *= p.drag;
			p.size += p.grow;
			if (++p.age >= p.life) {
				it.remove();
			}
		}
	}

	public static boolean isEmpty() {
		return PUFFS.isEmpty();
	}

	public static void clear() {
		PUFFS.clear();
	}

	/**
	 * Draws every puff into the bound framebuffer, back to front, cut softly against the world's depth ({@code depth}),
	 * lit by {@code daylight} and flashed by the stroke ({@code flash}).
	 */
	public static void render(Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right, Vector3f up, float tickDelta, int depth,
			int w, int h, float projA, float daylight, float flash) {
		if (PUFFS.isEmpty()) {
			return;
		}
		List<Puff> sorted = new ArrayList<>(PUFFS);
		sorted.sort((a, b) -> Double.compare(distance(b, cam, tickDelta), distance(a, cam, tickDelta)));
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
				GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
		RenderSystem.setShaderTexture(0, NoiseTex.get());
		RenderSystem.setShaderTexture(1, depth);
		Shaders.set(Shaders.dust, "ScreenSize", w, h);
		Shaders.set(Shaders.dust, "ProjA", projA);
		Shaders.set(Shaders.dust, "ProjB", proj.m32());
		Shaders.set(Shaders.dust, "Softness", 2.0F);
		Shaders.set(Shaders.dust, "Flash", flash);
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
		float light = 0.25F + 0.75F * daylight;
		for (Puff p : sorted) {
			float x = (float) (MathHelper.lerp(tickDelta, p.px, p.x) - cam.x);
			float y = (float) (MathHelper.lerp(tickDelta, p.py, p.y) - cam.y);
			float z = (float) (MathHelper.lerp(tickDelta, p.pz, p.z) - cam.z);
			float size = MathHelper.lerp(tickDelta, p.prevSize, p.size);
			float age = p.age + tickDelta;
			// In quickly, thinning out over the last half of its life; out of the way of a camera that comes too near.
			float fade = MathHelper.clamp(age / 3.0F, 0.0F, 1.0F) * MathHelper.clamp((p.life - age) / (p.life * 0.5F), 0.0F, 1.0F);
			float near = MathHelper.clamp((MathHelper.sqrt(x * x + y * y + z * z) - size) / (size * 2.0F), 0.0F, 1.0F);
			float a = p.alpha * fade * near;
			if (a < 0.01F) {
				continue;
			}
			float rx = right.x * size;
			float ry = right.y * size;
			float rz = right.z * size;
			float ux = up.x * size;
			float uy = up.y * size;
			float uz = up.z * size;
			vertex(b, x - rx - ux, y - ry - uy, z - rz - uz, -1.0F, -1.0F, p, light, a);
			vertex(b, x + rx - ux, y + ry - uy, z + rz - uz, 1.0F, -1.0F, p, light, a);
			vertex(b, x + rx + ux, y + ry + uy, z + rz + uz, 1.0F, 1.0F, p, light, a);
			vertex(b, x - rx + ux, y - ry + uy, z - rz + uz, -1.0F, 1.0F, p, light, a);
		}
		Post.draw(b, Shaders.dust, view, proj);
		RenderSystem.setShaderTexture(1, 0);
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
	}

	private static void vertex(BufferBuilder b, float x, float y, float z, float u, float v, Puff p, float light, float a) {
		b.vertex(x, y, z).texture(u, v).color(p.r * light, p.g * light, p.b * light, a).normal(p.seed, 0.0F, 0.0F);
	}

	private static double distance(Puff p, Vec3d cam, float tickDelta) {
		double dx = MathHelper.lerp(tickDelta, p.px, p.x) - cam.x;
		double dy = MathHelper.lerp(tickDelta, p.py, p.y) - cam.y;
		double dz = MathHelper.lerp(tickDelta, p.pz, p.z) - cam.z;
		return dx * dx + dy * dy + dz * dz;
	}

	private static void place(Puff p, double x, double y, double z) {
		p.x = x;
		p.y = y;
		p.z = z;
		p.px = x;
		p.py = y;
		p.pz = z;
		p.prevSize = p.size;
		p.seed = RANDOM.nextFloat() * 2.0F - 1.0F;
	}

	/** Takes the colour of the ground at {@code pos}, mixed with dusty grey, as dust does. */
	private static void tint(Puff p, ClientWorld world, BlockPos pos, float alpha) {
		BlockState state = world.getBlockState(pos);
		int rgb = state.getMapColor(world, pos).color;
		float r = (rgb >> 16 & 255) / 255.0F;
		float g = (rgb >> 8 & 255) / 255.0F;
		float b = (rgb & 255) / 255.0F;
		if (rgb == 0) {
			r = g = b = 0.5F;
		}
		p.r = MathHelper.lerp(0.45F, r, 0.5F);
		p.g = MathHelper.lerp(0.45F, g, 0.47F);
		p.b = MathHelper.lerp(0.45F, b, 0.43F);
		p.alpha = alpha;
	}

	private static int ground(ClientWorld world, double x, double z, double fallback) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
			return MathHelper.floor(fallback);
		}
		return world.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
	}
}
