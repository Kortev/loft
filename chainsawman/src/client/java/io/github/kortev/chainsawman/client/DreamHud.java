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
 * Down, now and then you dream: the screen goes dark and Pochita is there in the middle of it, a while, then gone; or
 * (as in season 1) you're in a dark corridor, a door at its end with light leaking out round it, Pochita in front of
 * it: don't open it. A slow heartbeat under it. He's still in there.
 */
public final class DreamHud {
	private static final Identifier POCHITA = ChainsawMan.id("textures/item/pochita.png");
	private static final Identifier DOOR = ChainsawMan.id("textures/gui/dream_door.png");
	/** Whether the dream now is the door's. */
	private static boolean door;
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
			// The first of the door; after that, either.
			door = !door || RANDOM.nextBoolean();
			next = now + LASTS + APART_MIN + RANDOM.nextInt(APART_MAX - APART_MIN);
			client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.6F, 0.45F));
		}
		if (dreaming >= 0 && now - dreaming > LASTS) {
			dreaming = -1;
		}
	}

	/** A dream now (for the film): the door's, or his. */
	public static void dreamNow(MinecraftClient client, boolean ofTheDoor) {
		if (client.player != null) {
			dreaming = client.player.getWorld().getTime();
			next = dreaming + LASTS + APART_MIN;
			door = ofTheDoor;
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
		// (The door's is all you see: nothing round it.)
		ctx.fill(0, 0, w, h, ((int) (k * (door ? 255 : 200)) << 24));
		if (door) {
			door(ctx, client, t, k, w, h);
			return;
		}
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

	/** The door's dream: drifting slowly toward the door, the light round it flickering; his warning under it. */
	private static void door(DrawContext ctx, MinecraftClient client, float t, float k, int w, int h) {
		float push = 1.0F + 0.15F * t / LASTS;
		int pw = (int) (w * 0.62F * push);
		int ph = pw * 40 / 64;
		float flicker = 0.85F + 0.15F * MathHelper.sin(t * 0.9F) * MathHelper.sin(t * 0.37F);
		RenderSystem.enableBlend();
		RenderSystem.setShaderColor(flicker, flicker, flicker, k);
		ctx.drawTexture(DOOR, (w - pw) / 2, (h - ph) / 2, pw, ph, 0.0F, 0.0F, 64, 40, 64, 40);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.disableBlend();
		float said = MathHelper.clamp(Math.min((t - 15.0F) / 8.0F, (LASTS - t) / 10.0F), 0.0F, 1.0F);
		if (said > 0.05F) {
			ctx.drawCenteredTextWithShadow(client.textRenderer, net.minecraft.text.Text.translatable("dream.chainsawman.door"), w / 2,
					(h + ph) / 2 + 8, ((int) (said * 255) << 24) | 0xFFC98A);
		}
	}
}
