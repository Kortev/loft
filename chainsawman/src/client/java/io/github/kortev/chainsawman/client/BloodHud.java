package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.PochitaItem;
import io.github.kortev.chainsawman.form.Blood;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Your blood ({@link Blood}), as a bar over the food bar: in the form, or whenever it isn't full and you have Pochita.
 * Flat red under a dark edge, a light line along its top, marks where the cord (to transform) and the frenzy need it.
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
		for (int need : new int[] {Blood.TRANSFORM, Blood.FRENZY}) {
			int at = x + WIDE - WIDE * need / Blood.MAX;
			ctx.fill(at, y - 1, at + 1, y + 5, EDGE);
		}
	}
}
