package io.github.kortev.chainsawman.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.form.Downed;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Down, now and then you dream of him: the screen goes dark and Pochita is there in the middle of it, a while, then
 * gone; a slow heartbeat under it. He's still in there.
 */
public final class DreamHud {
	private static final Identifier POCHITA = ChainsawMan.id("textures/item/pochita.png");
	/** How long a dream lasts (ticks), and how long between them, at the least and the most. */
	private static final int LASTS = 70;
	private static final int APART_MIN = 300;
	private static final int APART_MAX = 700;
	private static final Random RANDOM = Random.create();

	private static long next = -1;
	private static long dreaming = -1;

	private DreamHud() {
	}

	static void tick(MinecraftClient client) {
		ClientPlayerEntity self = client.player;
		if (self == null || !Downed.is(self)) {
			next = -1;
			dreaming = -1;
			return;
		}
		long now = self.getWorld().getTime();
		if (next < 0) {
			next = now + 120 + RANDOM.nextInt(160);
		}
		if (now >= next && dreaming < 0) {
			dreaming = now;
			next = now + LASTS + APART_MIN + RANDOM.nextInt(APART_MAX - APART_MIN);
			client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.6F, 0.45F));
		}
		if (dreaming >= 0 && now - dreaming > LASTS) {
			dreaming = -1;
		}
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (dreaming < 0 || client.player == null) {
			return;
		}
		float t = client.player.getWorld().getTime() - dreaming + counter.getTickDelta(true);
		float k = MathHelper.clamp(Math.min(t / 15.0F, (LASTS - t) / 20.0F), 0.0F, 1.0F);
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		ctx.fill(0, 0, w, h, ((int) (k * 200) << 24));
		int size = Math.min(w, h) / 3;
		// Drifting a little, as things do in dreams.
		int x = (w - size) / 2 + (int) (MathHelper.sin(t * 0.05F) * size * 0.05F);
		int y = (h - size) / 2 - (int) (t * 0.1F);
		RenderSystem.enableBlend();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, k * 0.85F);
		ctx.drawTexture(POCHITA, x, y, size, size, 0.0F, 0.0F, 16, 16, 16, 16);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.disableBlend();
	}
}
