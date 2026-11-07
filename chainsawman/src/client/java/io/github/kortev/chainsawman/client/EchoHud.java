package io.github.kortev.chainsawman.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.client.gfx.Post;
import io.github.kortev.chainsawman.client.gfx.Shaders;
import io.github.kortev.chainsawman.client.gfx.Target;
import io.github.kortev.chainsawman.contract.Echo;
import io.github.kortev.chainsawman.contract.EchoEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Echo's atmosphere while it lasts: a soft time-warp look at the screen's edges ({@code csm_warp}: a colour fringe, a ghosting, a cool
 * drained tint; strong for the owner, light for anyone near), swelling into the finale and flashing at its blow, a low drone with a
 * clock ticking under it (a one-shot played again every 2.4 s) and faint whispered echoes when the owner swings. Kept subtle enough to
 * play through.
 */
public final class EchoHud {
	public static ShaderProgram warp;
	private static final Target COPY = new Target(false, false);
	/** The look's strength now (eased toward what it should be), and the world time of the last finale's blow. */
	private static float level;
	private static float previous;
	private static long blowAt = -1000;
	private static boolean sawFinale;
	private static long finaleAt = -1;
	private static int humIn;
	private static int lastSwing = -1;

	private EchoHud() {
	}

	/** The ticks from the finale's beginning to its blow: the hold, then the streak in. */
	private static final int TO_BLOW = Echo.HOLD + EchoEntity.STREAK_IN;

	public static void tick(MinecraftClient client) {
		previous = level;
		if (client.world == null || client.player == null) {
			level = 0.0F;
			return;
		}
		float want = ClientEcho.strength(client);
		level += (want - level) * (want > level ? 0.25F : 0.08F);
		if (level < 0.002F) {
			level = 0.0F;
		}
		long now = client.world.getTime();
		// The finale's beat, for the owner: it began, and it blew when it ended.
		ClientEcho.Info mine = ClientEcho.of(client.player.getUuid());
		if (mine != null && mine.finaleAt() >= 0) {
			sawFinale = true;
			finaleAt = mine.finaleAt();
		} else if (mine == null && sawFinale) {
			sawFinale = false;
			blowAt = finaleAt >= 0 && now - finaleAt < TO_BLOW + 6 ? now : Math.max(now, finaleAt + TO_BLOW);
			finaleAt = -1;
		}
		// The drone and its clock, and the whispers.
		if (mine != null) {
			if (--humIn <= 0) {
				humIn = 46;
				client.getSoundManager().play(PositionedSoundInstance.master(ChainsawSounds.ECHO_HUM, 1.0F, 0.55F));
			}
			PlayerEntity p = client.player;
			if (p.handSwinging && p.handSwingTicks == 0 && lastSwing != p.age) {
				lastSwing = p.age;
				client.getSoundManager().play(PositionedSoundInstance.master(ChainsawSounds.ECHO_WHISPER, 0.9F + client.world.random.nextFloat() * 0.3F, 0.6F));
			}
		} else if (level > 0.05F) {
			// Anyone near a crowd hears a little of the drone.
			if (--humIn <= 0) {
				humIn = 46;
				client.getSoundManager().play(PositionedSoundInstance.master(ChainsawSounds.ECHO_HUM, Math.min(1.0F, level * 2.2F), 0.55F));
			}
		} else {
			humIn = 0;
		}
	}

	/** The finale's swell and the blow's flash: 0 normally. */
	private static float pulse(MinecraftClient client, float tickDelta) {
		if (client.world == null) {
			return 0.0F;
		}
		float now = client.world.getTime() + tickDelta;
		float pulse = 0.0F;
		if (sawFinale && finaleAt >= 0) {
			pulse = MathHelper.clamp((now - finaleAt) / TO_BLOW, 0.0F, 1.0F) * 0.7F;
		}
		float since = now - blowAt;
		if (since >= 0.0F && since < 24.0F) {
			pulse = Math.max(pulse, 1.9F * (float) Math.exp(-since / 5.5F));
		}
		return pulse;
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		float tickDelta = counter.getTickDelta(true);
		float strength = MathHelper.lerp(tickDelta, previous, level);
		float pulse = pulse(client, tickDelta);
		if (warp == null || client.player == null || client.world == null || strength < 0.01F && pulse < 0.01F) {
			return;
		}
		ctx.draw();
		Framebuffer main = client.getFramebuffer();
		int w = main.textureWidth;
		int h = main.textureHeight;
		COPY.ensure(w, h);
		COPY.copyColorFrom(main);
		main.beginWrite(true);
		Post.begin();
		RenderSystem.setShaderTexture(0, COPY.color());
		Shaders.set(warp, "ScreenSize", (float) w, (float) h);
		Shaders.set(warp, "Strength", strength);
		Shaders.set(warp, "Pulse", pulse);
		Shaders.set(warp, "Time", (float) (client.world.getTime() % 100000L) + tickDelta);
		Post.quad(warp);
		RenderSystem.setShaderTexture(0, 0);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
	}
}
