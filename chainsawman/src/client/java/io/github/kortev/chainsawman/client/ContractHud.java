package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.contract.Bond;
import io.github.kortev.chainsawman.contract.Contract;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * The contract on your screen: the selected contract's name, its bond step and how far to the next (a small bar), and at the deepest step
 * the hold-the-switch-key hint for "Not yet". A bond-up is a moment: the devil's crowd voice says its line big across the screen, trembling
 * as a crowd's would (the grey drain and the sound come with it).
 */
public final class ContractHud {
	private ContractHud() {
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || client.options.hudHidden) {
			return;
		}
		float tickDelta = counter.getTickDelta(true);
		Contract held = ClientContracts.get(client.player.getUuid());
		if (held != Contract.NONE) {
			panel(ctx, client, held);
		}
		banner(ctx, client, tickDelta);
	}

	private static void panel(DrawContext ctx, MinecraftClient client, Contract held) {
		int uses = ClientBonds.uses(held);
		int step = Bond.step(uses);
		int next = Bond.next(uses);
		int from = step == 1 ? 0 : step == 2 ? Bond.TWO : Bond.THREE;
		int x = 8;
		int y = ctx.getScaledWindowHeight() - 52;
		var font = client.textRenderer;
		ctx.drawTextWithShadow(font, Text.translatable("contract.chainsawman." + held.id).append("  ").append(Text.translatable("chainsawman.bond.step", step)), x, y, 0xFFD8D2C0);
		int width = 70;
		ctx.fill(x, y + 11, x + width, y + 14, 0xAA101010);
		float fill = next == 0 ? 1.0F : MathHelper.clamp((uses - from) / (float) (next - from), 0.0F, 1.0F);
		ctx.fill(x, y + 11, x + (int) (width * fill), y + 14, step == 3 ? 0xFFC03030 : 0xFFB8A878);
		// The pips of the three steps.
		for (int i = 0; i < 3; i++) {
			ctx.fill(x + width + 5 + i * 6, y + 10, x + width + 9 + i * 6, y + 15, i < step ? 0xFFC03030 : 0xAA303030);
		}
		if (next == 0) {
			if (held == Contract.ETERNITY) {
				ctx.drawTextWithShadow(font, Text.translatable("chainsawman.bond.not_yet", ChainsawManClient.SWITCH_CONTRACT.getBoundKeyLocalizedText()), x, y + 17, 0xFFA09888);
			}
		} else {
			ctx.drawTextWithShadow(font, Text.translatable("chainsawman.bond.next", uses, next), x, y + 17, 0xFFA09888);
		}
	}

	private static void banner(DrawContext ctx, MinecraftClient client, float tickDelta) {
		float since = ClientBonds.sinceUp(tickDelta);
		if (since < 0.0F || since > ClientBonds.MOMENT) {
			return;
		}
		float in = MathHelper.clamp(since / 8.0F, 0.0F, 1.0F);
		float out = MathHelper.clamp((ClientBonds.MOMENT - since) / 20.0F, 0.0F, 1.0F);
		float alpha = in * out;
		var font = client.textRenderer;
		Text title = Text.translatable("chainsawman.bond.up", ClientBonds.upStep());
		Text line = Text.translatable("chainsawman.bond." + ClientBonds.upContract().id + "." + ClientBonds.upStep());
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		ctx.getMatrices().push();
		ctx.getMatrices().translate(w / 2.0F, h * 0.34F, 0.0F);
		ctx.getMatrices().scale(3.0F, 3.0F, 1.0F);
		ctx.drawCenteredTextWithShadow(font, title, 0, -12, ((int) (255 * alpha) << 24) | 0xC83030);
		ctx.getMatrices().pop();
		// The crowd: the line said by many voices at once, each a little off from the others.
		for (int i = 0; i < 7; i++) {
			float jx = MathHelper.sin(since * 0.9F + i * 2.1F) * (1.0F + i * 0.5F);
			float jy = MathHelper.cos(since * 0.7F + i * 1.3F) * (0.6F + i * 0.4F);
			int a = (int) (255 * alpha * (i == 0 ? 1.0F : 0.28F));
			ctx.getMatrices().push();
			ctx.getMatrices().translate(w / 2.0F + jx, h * 0.34F + 12 + jy, 0.0F);
			ctx.getMatrices().scale(2.0F, 2.0F, 1.0F);
			ctx.drawCenteredTextWithShadow(font, line, 0, 0, (a << 24) | 0xE8E0D0);
			ctx.getMatrices().pop();
		}
	}
}
