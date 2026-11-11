package io.github.kortev.chainsawman.client.devil;

import io.github.kortev.chainsawman.devil.Devils;
import io.github.kortev.chainsawman.devil.LeechDevilEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/** The Leech Devil on the client: its renderer, and the red at the edge of your screen while its arm is stuck to you, drinking. */
public final class LeechClient {
	private LeechClient() {
	}

	/** Called once from the client's init. */
	public static void init() {
		EntityRendererRegistry.register(Devils.LEECH_DEVIL, LeechDevilRenderer::new);
		EntityRendererRegistry.register(Devils.LEECH_PART, EmptyEntityRenderer::new);
		HudRenderCallback.EVENT.register(LeechClient::hud);
	}

	/** The devil whose arm is stuck to you, or null. */
	static LeechDevilEntity drinking(MinecraftClient c) {
		if (c.world == null || c.player == null) {
			return null;
		}
		for (Entity e : c.world.getEntities()) {
			if (e instanceof LeechDevilEntity d && d.isAlive() && d.state() == LeechDevilEntity.LATCHED && d.stuckTo() == c.player.getId()) {
				return d;
			}
		}
		return null;
	}

	private static void hud(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient c = MinecraftClient.getInstance();
		LeechDevilEntity d = drinking(c);
		if (d == null || c.options.hudHidden) {
			return;
		}
		float t = d.stateTime(counter.getTickDelta(true));
		// Red creeping in from the edges, throbbing with each pulse it drinks.
		float pulse = (t % LeechDevilEntity.LATCH_PULSE) / LeechDevilEntity.LATCH_PULSE;
		float beat = (float) Math.exp(-pulse * 4.0);
		float in = MathHelper.clamp(t / 6.0F, 0.0F, 1.0F);
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		int depth = (int) (Math.min(w, h) * (0.16F + 0.05F * beat));
		int alpha = (int) (in * (110 + 90 * beat));
		int steps = 16;
		for (int k = 0; k < steps; k++) {
			float f = 1.0F - k / (float) steps;
			int a = (int) (alpha * f * f);
			int col = (a << 24) | 0x8A0A0E;
			int d0 = depth * k / steps;
			int d1 = depth * (k + 1) / steps;
			ctx.fill(d0, d0, w - d0, d1, col);
			ctx.fill(d0, h - d1, w - d0, h - d0, col);
			ctx.fill(d0, d1, d1, h - d1, col);
			ctx.fill(w - d1, d1, w - d0, h - d1, col);
		}
	}
}
