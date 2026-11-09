package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.PochitaItem;
import io.github.kortev.chainsawman.form.Blood;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import io.github.kortev.chainsawman.form.HeartLevel;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Your blood ({@link Blood}), as a bar over the food bar: in the form, or whenever it isn't full and you have Pochita.
 * Flat red under a dark edge, a light line along its top, marks where the cord (to transform) and Beam need it.
 */
public final class BloodHud {
	private static final int WIDE = 81;
	private static final int EDGE = 0xFF1A0204;
	private static final int EMPTY = 0xFF3A0508;
	private static final int RED = 0xFFBA121A;
	private static final int SHINE = 0xFFE0404A;

	private BloodHud() {
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity self = client.player;
		if (self != null && !client.options.hudHidden) {
			banner(ctx, client, counter.getTickDelta(true));
		}
		int blood = ClientCombat.blood();
		if (self == null || blood < 0 || client.options.hudHidden || self.isSpectator() || self.isCreative()
				|| !(ClientForms.on(self) || blood < Blood.MAX || PochitaItem.carried(self))) {
			return;
		}
		int x = ctx.getScaledWindowWidth() / 2 + 10;
		int y = ctx.getScaledWindowHeight() - 59;
		ctx.fill(x - 1, y - 1, x + WIDE + 1, y + 5, EDGE);
		ctx.fill(x, y, x + WIDE, y + 4, EMPTY);
		int full = WIDE * blood / Blood.MAX;
		ctx.fill(x + WIDE - full, y, x + WIDE, y + 4, RED);
		ctx.fill(x + WIDE - full, y, x + WIDE, y + 1, SHINE);
		for (int need : new int[] {Blood.TRANSFORM, Blood.BEAM}) {
			int at = x + WIDE - WIDE * need / Blood.MAX;
			ctx.fill(at, y - 1, at + 1, y + 5, EDGE);
		}
		// The heart's level, and how far to the next.
		if (ClientCombat.heart(self)) {
			int level = ClientCombat.heartLevel(self);
			int xp = ClientCombat.heartXp();
			String progress = level >= 3 ? "" : "  " + xp + "/" + (level == 1 ? HeartLevel.LEVEL_2 : HeartLevel.LEVEL_3);
			ctx.drawTextWithShadow(client.textRenderer, Text.translatable("chainsawman.heart.level", level).append(progress), x, y - 10, 0xFFC03038);
		}
	}

	/** A level-up of the heart: the number big, the line under it, shaking, a blood-red wash fading. */
	private static void banner(DrawContext ctx, MinecraftClient client, float tickDelta) {
		float since = ClientCombat.heartUpSince(tickDelta);
		if (since < 0.0F || since > 80.0F) {
			return;
		}
		float in = MathHelper.clamp(since / 4.0F, 0.0F, 1.0F);
		float out = MathHelper.clamp((80.0F - since) / 20.0F, 0.0F, 1.0F);
		float alpha = in * out;
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		ctx.fill(0, 0, w, h, ((int) (70 * alpha * (1.0F - MathHelper.clamp(since / 40.0F, 0.0F, 1.0F))) << 24) | 0x7A0008);
		float shake = Math.max(0.0F, 1.0F - since / 24.0F) * 4.0F;
		float jx = MathHelper.sin(since * 2.7F) * shake;
		float jy = MathHelper.cos(since * 3.1F) * shake;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(w / 2.0F + jx, h * 0.3F + jy, 0.0F);
		ctx.getMatrices().scale(3.5F, 3.5F, 1.0F);
		ctx.drawCenteredTextWithShadow(client.textRenderer, Text.translatable("chainsawman.heart.up", ClientCombat.heartUpLevel()), 0, -10, ((int) (255 * alpha) << 24) | 0xD02028);
		ctx.getMatrices().pop();
		ctx.getMatrices().push();
		ctx.getMatrices().translate(w / 2.0F + jx, h * 0.3F + 22 + jy, 0.0F);
		ctx.getMatrices().scale(1.6F, 1.6F, 1.0F);
		ctx.drawCenteredTextWithShadow(client.textRenderer, Text.translatable("chainsawman.heart.level." + ClientCombat.heartUpLevel()), 0, 0, ((int) (255 * alpha) << 24) | 0xE8D8D0);
		ctx.getMatrices().pop();
	}
}
