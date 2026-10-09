package io.github.kortev.chitty.client;

import io.github.kortev.chitty.airship.AirshipEntity;
import io.github.kortev.chitty.airship.AirshipHookEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * The airship's controls on screen, so that nobody has to remember them. Aboard, a panel at the right of the screen
 * lists the keys for what the player can do where they are (walking about her gondola, or at the wheel), each key lit
 * while it is held, and under them a gauge for the grapple: how far down it is, which way its winch is turning, and
 * what is on it. On the ground with her grapple in hand, hanging on it or caught on it, a smaller panel says what can be
 * done. (F1 hides it with the rest of the HUD.)
 */
final class AirshipHud {
	private static final int BACK = 0x99000000;
	private static final int TITLE = 0xFFE0B048;
	private static final int TEXT = 0xFFE8E8E8;
	private static final int DIM = 0xFF9A9A9A;
	private static final int WARN = 0xFFFF6A5A;
	private static final int GOOD = 0xFF8EE08E;
	private static final int CAP = 0xFF34343E;
	private static final int CAP_EDGE = 0xFF8A8A96;
	private static final int CAP_LIT = 0xFFE0B048;
	private static final int ROPE = 0xFFC8B48C;
	private static final int BAR = 0xFF202024;
	private static final int PAD = 5;
	private static final int ROW = 12;
	/** How wide the grapple's gauge is at least. */
	private static final int GAUGE = 110;

	/** One line of a panel: a key (or keys) to press and what it does; or, with no key, a line of its own. */
	private record Row(@Nullable Text key, boolean lit, Text label, int color) {
	}

	private AirshipHud() {
	}

	static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.getDebugHud().shouldShowDebugHud()) {
			return;
		}
		float tickDelta = counter.getTickDelta(false);
		GameOptions o = client.options;
		List<Row> rows = new ArrayList<>();
		Text title;
		AirshipEntity gauge = null;
		if (player.getVehicle() instanceof AirshipEntity ship) {
			title = ui("title");
			if (ship.getControllingPassenger() == player) {
				rows.add(row(keys(o.forwardKey, o.backKey), o.forwardKey.isPressed() || o.backKey.isPressed(), ui("throttle")));
				rows.add(row(keys(o.leftKey, o.rightKey), o.leftKey.isPressed() || o.rightKey.isPressed(), ui("steer")));
				rows.add(row(o.jumpKey, ui("rise")));
				rows.add(row(o.sprintKey, ui("sink")));
				rows.add(row(o.sneakKey, ui("leave_wheel")));
				rows.add(row(AirshipClient.OVERBOARD, ui("overboard")));
			} else {
				rows.add(row(keys(o.forwardKey, o.leftKey, o.backKey, o.rightKey), o.forwardKey.isPressed() || o.leftKey.isPressed()
						|| o.backKey.isPressed() || o.rightKey.isPressed(), ui("walk")));
				rows.add(new Row(null, false, ui("wheel"), DIM));
				rows.add(row(o.sneakKey, ui("get_off")));
			}
			rows.add(null);
			rows.add(row(AirshipClient.GRAPPLE, ui("grapple_down")));
			rows.add(row(AirshipClient.WIND, ui("grapple_up")));
			rows.add(row(AirshipClient.LADDER, ui(ship.getShownLadder(tickDelta) > 0.5F ? "ladder_up" : "ladder_down")));
			rows.add(new Row(AirshipClient.BOMB.getBoundKeyLocalizedText(), AirshipClient.BOMB.isPressed(),
					Text.translatable("hud.shootingstar.airship.ui.bomb", ship.getBombs()), ship.getBombs() > 0 ? TEXT : DIM));
			if (ship.isOverloaded()) {
				rows.add(new Row(null, false, Text.translatable("hud.shootingstar.airship.ui.heavy", AirshipEntity.LIFT), WARN));
			}
			gauge = ship;
		} else if (player.getVehicle() instanceof AirshipHookEntity head) {
			if (head.isVoluntary()) {
				title = ui("on_grapple");
				rows.add(row(keys(o.forwardKey, o.leftKey, o.backKey, o.rightKey), false, ui("swing")));
				rows.add(row(o.sneakKey, ui("let_go")));
			} else {
				title = ui("caught");
				rows.add(row(o.sneakKey, ui("struggle")));
			}
		} else if (AirshipEntity.grappleHeldBy(player) != null) {
			title = ui("in_hand");
			rows.add(row(AirshipClient.GRAPPLE, ui("throw")));
			rows.add(row(o.useKey, ui("hook_on")));
			rows.add(row(o.jumpKey, ui("hang_on")));
			rows.add(row(o.sneakKey, ui("let_go")));
		} else {
			return;
		}
		draw(ctx, client.textRenderer, title, rows, gauge, tickDelta);
	}

	private static void draw(DrawContext ctx, TextRenderer font, Text title, List<Row> rows, @Nullable AirshipEntity ship,
			float tickDelta) {
		int cap = 0;
		int label = font.getWidth(title);
		for (Row row : rows) {
			if (row == null) {
				continue;
			}
			if (row.key() != null) {
				cap = Math.max(cap, font.getWidth(row.key()) + 6);
			}
			label = Math.max(label, font.getWidth(row.label()));
		}
		cap = Math.max(cap, 13);
		int width = PAD + Math.max(cap + 5 + label, ship != null ? GAUGE : 0) + PAD;
		int height = PAD + 11;
		for (Row row : rows) {
			height += row == null ? 5 : ROW;
		}
		if (ship != null) {
			height += 4 + 30;
		}
		height += PAD - 2;
		int x = ctx.getScaledWindowWidth() - width - 6;
		int y = Math.max(4, (ctx.getScaledWindowHeight() - height) / 2 - 10);
		ctx.fill(x, y, x + width, y + height, BACK);
		ctx.drawTextWithShadow(font, title, x + PAD, y + PAD, TITLE);
		int at = y + PAD + 11;
		for (Row row : rows) {
			if (row == null) {
				ctx.fill(x + PAD, at + 2, x + width - PAD, at + 3, 0x40FFFFFF);
				at += 5;
				continue;
			}
			int textX = x + PAD;
			if (row.key() != null) {
				// A key cap: lit while it is held.
				ctx.fill(x + PAD, at, x + PAD + cap, at + ROW - 2, row.lit() ? CAP_LIT : CAP_EDGE);
				ctx.fill(x + PAD + 1, at + 1, x + PAD + cap - 1, at + ROW - 3, row.lit() ? 0xFF6A5420 : CAP);
				ctx.drawTextWithShadow(font, row.key(), x + PAD + (cap - font.getWidth(row.key())) / 2 + 1, at + 1, row.lit() ? 0xFFFFF4D0 : TEXT);
				textX += cap + 5;
			}
			ctx.drawTextWithShadow(font, row.label(), textX, at + 1, row.color());
			at += ROW;
		}
		if (ship != null) {
			grappleGauge(ctx, font, ship, x + PAD, at + 4, width - 2 * PAD, tickDelta);
		}
	}

	/**
	 * The grapple: how far down it is (a rope paid out across a bar, the grapple at its end, as far as it will go at
	 * the far side), which way the winch is turning, and what is on it.
	 */
	private static void grappleGauge(DrawContext ctx, TextRenderer font, AirshipEntity ship, int x, int y, int width, float tickDelta) {
		float drop = ship.getShownDrop(tickDelta);
		float turning = ship.getShownDrop(1.0F) - ship.getShownDrop(0.0F);
		boolean stowed = ship.getHookState() == AirshipEntity.Hook.UP && drop < 0.3F;
		Text state = stowed ? ui("stowed")
				: Text.translatable("hud.shootingstar.airship.ui.down", String.format(Locale.ROOT, "%.0f", drop));
		String arrow = turning > 0.02F ? " ▼" : turning < -0.02F ? " ▲" : "";
		ctx.drawTextWithShadow(font, ui("grapple"), x, y, TITLE);
		Text right = Text.empty().append(state).append(arrow);
		ctx.drawTextWithShadow(font, right, x + width - font.getWidth(right), y, stowed ? DIM : TEXT);
		int barY = y + 11;
		ctx.fill(x, barY, x + width, barY + 4, BAR);
		int reach = Math.round(MathHelper.clamp(drop / (float) AirshipEntity.LINE_MAX, 0.0F, 1.0F) * (width - 3));
		if (reach > 0) {
			ctx.fill(x, barY + 1, x + reach, barY + 3, ROPE);
		}
		ctx.fill(x + reach, barY - 1, x + reach + 3, barY + 5, TITLE);
		AirshipHookEntity head = ship.getShownHookEntity();
		Entity load = head == null ? null : head.getFirstPassenger();
		Entity holder = ship.getGrappleHolder();
		Text on = null;
		int color = DIM;
		if (load != null) {
			boolean byChoice = head.isVoluntary();
			on = Text.translatable(byChoice ? "hud.shootingstar.airship.ui.hanging" : "hud.shootingstar.airship.ui.on_it", load.getDisplayName());
			color = byChoice ? GOOD : WARN;
		} else if (holder != null) {
			on = Text.translatable("hud.shootingstar.airship.ui.held", holder.getDisplayName());
		}
		if (on != null) {
			ctx.drawTextWithShadow(font, on, x, barY + 8, color);
		}
	}

	private static Row row(KeyBinding key, Text label) {
		return new Row(key.getBoundKeyLocalizedText(), key.isPressed(), label, TEXT);
	}

	private static Row row(Text keys, boolean lit, Text label) {
		return new Row(keys, lit, label, TEXT);
	}

	/** Several keys in one cap: run together if each is a single letter (WASD), otherwise with slashes between. */
	private static Text keys(KeyBinding... bindings) {
		StringBuilder joined = new StringBuilder();
		boolean letters = true;
		for (KeyBinding key : bindings) {
			letters &= key.getBoundKeyLocalizedText().getString().length() == 1;
		}
		for (KeyBinding key : bindings) {
			if (!joined.isEmpty() && !letters) {
				joined.append('/');
			}
			joined.append(key.getBoundKeyLocalizedText().getString());
		}
		return Text.literal(joined.toString());
	}

	private static Text ui(String name) {
		return Text.translatable("hud.shootingstar.airship.ui." + name);
	}
}
