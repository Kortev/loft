package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Fallen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Teams on the screen: when you are down, the timer (and the key to give up, and who is picking you up); when a hybrid has their heart
 * torn out, the hold; an arrow to your buddy when they are down; and the legend's marker.
 */
public final class TeamsHud {
	private static final int RED = 0xFFD8202A;
	private static final int DARK = 0xFF1A0204;
	private static final int GREEN = 0xFF4CD964;
	private static final int GOLD = 0xFFF2B233;

	private TeamsHud() {
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity self = client.player;
		if (self == null || client.world == null || client.options.hudHidden) {
			return;
		}
		float tickDelta = counter.getTickDelta(true);
		long now = client.world.getTime();
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		if (Fallen.is(self)) {
			down(ctx, client, self, w, tickDelta);
		}
		ClientTeams.Lift mine = ClientTeams.on(self);
		if (mine != null && mine.kind() == ClientTeams.HEART && Downed.is(self)) {
			bar(ctx, w / 2 - 60, 62, 120, ClientTeams.progress(self, mine, tickDelta), RED);
			ctx.drawCenteredTextWithShadow(client.textRenderer, Text.translatable("message.chainsawman.theft.you", Text.keybind("key.jump")), w / 2, 50, RED);
		}
		// The one at the person on the ground sees a bar under the crosshair.
		PlayerEntity down = ClientTeams.lifting(self);
		if (down != null) {
			ClientTeams.Lift lift = ClientTeams.liftBy(self);
			if (lift != null) {
				bar(ctx, w / 2 - 40, h / 2 + 22, 80, ClientTeams.progress(down, lift, tickDelta), lift.kind() == ClientTeams.HEART ? RED : GREEN);
			}
		}
		Vec3d buddy = ClientTeams.buddyDown(now);
		if (buddy != null && !Fallen.is(self)) {
			PlayerEntity friend = ClientTeams.buddy();
			String name = friend != null ? friend.getGameProfile().getName() : "";
			arrow(ctx, client, buddy, RED, Text.translatable("message.chainsawman.buddy.arrow", name).getString(), w, h, tickDelta);
		}
		Vec3d legend = ClientTeams.legend(now);
		if (legend != null) {
			arrow(ctx, client, legend, GOLD, Text.translatable("message.chainsawman.legend.marker").getString(), w, h, tickDelta);
		}
	}

	/** You are down: DOWN, how long is left, the give-up key and who is lifting you. */
	private static void down(DrawContext ctx, MinecraftClient client, ClientPlayerEntity self, int w, float tickDelta) {
		float left = ClientTeams.left(self, tickDelta);
		if (left < 0.0F) {
			return;
		}
		int seconds = (int) Math.ceil(left / 20.0F);
		float pulse = 0.65F + 0.35F * MathHelper.sin((self.getWorld().getTime() + tickDelta) * (left < 200.0F ? 0.6F : 0.2F));
		int color = 0xFF000000 | ((int) (0xD8 * pulse) << 16) | ((int) (0x20 * pulse) << 8) | (int) (0x2A * pulse);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(w / 2.0F, 20.0F, 0.0F);
		ctx.getMatrices().scale(3.0F, 3.0F, 1.0F);
		ctx.drawCenteredTextWithShadow(client.textRenderer, Text.translatable("message.chainsawman.fallen.hud"), 0, 0, color);
		ctx.getMatrices().pop();
		ctx.getMatrices().push();
		ctx.getMatrices().translate(w / 2.0F, 50.0F, 0.0F);
		ctx.getMatrices().scale(2.0F, 2.0F, 1.0F);
		ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal(String.format("%d:%02d", seconds / 60, seconds % 60)), 0, 0, 0xFFFFFFFF);
		ctx.getMatrices().pop();
		bar(ctx, w / 2 - 60, 72, 120, left / io.github.kortev.chainsawman.form.Fallen.TIMER, RED);
		ClientTeams.Lift lift = ClientTeams.on(self);
		if (lift != null && lift.kind() == ClientTeams.PICKUP && lift.how() != ClientTeams.LET_GO) {
			net.minecraft.entity.Entity helper = client.world.getEntityById(lift.helper());
			Text who = helper != null ? helper.getDisplayName() : Text.literal("A friend");
			ctx.drawCenteredTextWithShadow(client.textRenderer, Text.translatable("message.chainsawman.fallen.lifted", who), w / 2, 86, GREEN);
			bar(ctx, w / 2 - 60, 98, 120, ClientTeams.progress(self, lift, tickDelta), GREEN);
		} else if (ClientTeams.giving() > 0.0F) {
			ctx.drawCenteredTextWithShadow(client.textRenderer, Text.translatable("message.chainsawman.fallen.giving"), w / 2, 86, RED);
			bar(ctx, w / 2 - 60, 98, 120, ClientTeams.giving(), RED);
		} else {
			ctx.drawCenteredTextWithShadow(client.textRenderer, Text.translatable("message.chainsawman.fallen.hold", Text.keybind("key.chainsawman.give_up")), w / 2, 86,
					0xFFB0B0B0);
		}
	}

	/** A bar {@code width} wide at ({@code x}, {@code y}), filled to {@code share}. */
	private static void bar(DrawContext ctx, int x, int y, int width, float share, int color) {
		ctx.fill(x - 1, y - 1, x + width + 1, y + 6, DARK);
		ctx.fill(x, y, x + width, y + 5, 0xFF3A0508);
		ctx.fill(x, y, x + (int) (width * MathHelper.clamp(share, 0.0F, 1.0F)), y + 5, color);
	}

	/**
	 * An arrow on a ring round the middle of the screen pointing toward {@code target} (turning as you do), with a label and how far it is.
	 */
	private static void arrow(DrawContext ctx, MinecraftClient client, Vec3d target, int color, String label, int w, int h, float tickDelta) {
		Vec3d from = client.gameRenderer.getCamera().getPos();
		double dx = target.x - from.x;
		double dz = target.z - from.z;
		float toward = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float rel = MathHelper.wrapDegrees(toward - client.gameRenderer.getCamera().getYaw());
		double meters = Math.sqrt(dx * dx + dz * dz);
		float cx = w / 2.0F;
		float cy = h / 2.0F - 16.0F;
		float radius = Math.min(h * 0.28F, 84.0F);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(cx, cy, 0.0F);
		ctx.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(rel));
		ctx.getMatrices().translate(0.0F, -radius, 0.0F);
		shape(ctx, 0xFF000000, 1.4F);
		shape(ctx, color, 1.0F);
		ctx.getMatrices().pop();
		// The label sits on the ring, kept upright, on the side the arrow points.
		float lx = cx + (float) Math.sin(Math.toRadians(rel)) * (radius + 20.0F);
		float ly = cy - (float) Math.cos(Math.toRadians(rel)) * (radius + 20.0F);
		String text = label + "  " + (int) meters + " m";
		int tw = client.textRenderer.getWidth(text);
		ctx.fill((int) (lx - tw / 2.0F) - 3, (int) (ly - 4.0F) - 2, (int) (lx + tw / 2.0F) + 3, (int) (ly + 6.0F), 0xB0000000);
		ctx.drawTextWithShadow(client.textRenderer, text, (int) (lx - tw / 2.0F), (int) (ly - 4.0F), color);
	}

	/** The arrow itself, pointing up, scaled about its middle: a head and a shaft. */
	private static void shape(DrawContext ctx, int color, float scale) {
		ctx.getMatrices().push();
		ctx.getMatrices().scale(scale, scale, 1.0F);
		for (int i = 0; i < 8; i++) {
			ctx.fill(-i - 1, -9 + i, i + 1, -8 + i, color);
		}
		ctx.fill(-2, -1, 2, 8, color);
		ctx.getMatrices().pop();
	}
}
