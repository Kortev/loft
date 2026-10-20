package io.github.kortev.chainsawman.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.chainsawman.ChainsawMan;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Blood on the lens from a kill up close, in your own eyes: a few chunky splats thrown onto the edges of the picture
 * (never over the middle, where you're looking), sliding a little way down and fading. kortev kept the idea from the
 * transformation's lens blood, which looked arcade-y there; this is quieter: few, dark, at the edges, gone in a couple
 * of seconds.
 */
public final class LensBlood {
	private static final Identifier SPLATS = ChainsawMan.id("textures/gui/lens_blood.png");
	/** How long a splat stays (ticks), how near a kill must be to throw any (blocks), and how many at most. */
	private static final int LASTS = 45;
	private static final double NEAR = 4.5;
	private static final int MOST = 4;
	private static final Random RANDOM = Random.create();

	/** One splat: when, where (shares of the screen), how big (a share of its height), which, and whether mirrored. */
	private record Splat(long at, float x, float y, float size, int which, boolean flip) {
	}

	private static final List<Splat> ON = new CopyOnWriteArrayList<>();

	private LensBlood() {
	}

	/** You killed something {@code distance} away: a few splats, more the nearer it was. */
	static void killed(double distance, long now) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (distance > NEAR || client.gameRenderer.getCamera().isThirdPerson()) {
			return;
		}
		int count = 1 + (int) ((MOST - 1) * (1.0 - distance / NEAR) + RANDOM.nextFloat() * 0.8F);
		for (int i = 0; i < count; i++) {
			// Out at the edges, left or right, high or low.
			float x = RANDOM.nextBoolean() ? 0.05F + RANDOM.nextFloat() * 0.22F : 0.73F + RANDOM.nextFloat() * 0.22F;
			float y = 0.05F + RANDOM.nextFloat() * 0.6F;
			ON.add(new Splat(now, x, y, 0.12F + RANDOM.nextFloat() * 0.1F, RANDOM.nextInt(3), RANDOM.nextBoolean()));
		}
	}

	/** Your own arm off: blood thrown up over that side of the view, big. */
	static void lost(boolean right, long now) {
		if (MinecraftClient.getInstance().gameRenderer.getCamera().isThirdPerson()) {
			return;
		}
		for (int i = 0; i < 3; i++) {
			float x = right ? 0.68F + RANDOM.nextFloat() * 0.27F : 0.05F + RANDOM.nextFloat() * 0.27F;
			float y = 0.3F + RANDOM.nextFloat() * 0.6F;
			ON.add(new Splat(now, x, y, 0.2F + RANDOM.nextFloat() * 0.12F, RANDOM.nextInt(3), RANDOM.nextBoolean()));
		}
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ON.isEmpty() || client.player == null) {
			return;
		}
		long now = client.player.getWorld().getTime();
		float tickDelta = counter.getTickDelta(true);
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		RenderSystem.enableBlend();
		for (Splat s : ON) {
			float t = now - s.at() + tickDelta;
			if (t > LASTS || client.gameRenderer.getCamera().isThirdPerson()) {
				ON.remove(s);
				continue;
			}
			float alpha = 0.95F * Math.min(1.0F, t / 1.5F) * (1.0F - MathHelper.clamp((t - LASTS * 0.5F) / (LASTS * 0.5F), 0.0F, 1.0F));
			int size = (int) (h * s.size());
			// Sliding a little way down, slower and slower, as it runs.
			int x = (int) (w * s.x()) - size / 2;
			int y = (int) (h * s.y() + h * 0.05F * (1.0F - (float) Math.exp(-t / 15.0F))) - size / 2;
			ctx.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
			float u = s.which() * 16.0F;
			if (s.flip()) {
				ctx.drawTexture(SPLATS, x, y, size, size, u + 16.0F, 0.0F, -16, 16, 48, 16);
			} else {
				ctx.drawTexture(SPLATS, x, y, size, size, u, 0.0F, 16, 16, 48, 16);
			}
		}
		ctx.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.disableBlend();
	}

	static void clear() {
		ON.clear();
	}
}
