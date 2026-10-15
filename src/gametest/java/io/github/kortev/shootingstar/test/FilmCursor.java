package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.test.mixin.FilmMouseAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.Window;

/**
 * The mouse pointer in a screen while filming. The real one is the system's, drawn over the window and not in the game's
 * frame, so the film would show a chest's slots lighting up and its items moving with nothing moving them. This one is
 * drawn into the frame (the usual white arrow), moved as a hand moves a mouse (fast in the middle, easing in and out),
 * and is where the game takes the mouse to be, so what it is over lights up and shows its name.
 */
public final class FilmCursor {
	private static boolean shown;
	private static double fromX;
	private static double fromY;
	private static double toX;
	private static double toY;
	private static double startedAt;
	private static double length = 1.0;

	/** The arrow, a row a line: X its black edge, O its white. */
	private static final String[] ARROW = {
		"X...........",
		"XX..........",
		"XOX.........",
		"XOOX........",
		"XOOOX.......",
		"XOOOOX......",
		"XOOOOOX.....",
		"XOOOOOOX....",
		"XOOOOOOOX...",
		"XOOOOOOOOX..",
		"XOOOOOOOOOX.",
		"XOOOOOOXXXXX",
		"XOOOXOOX....",
		"XOOXXOOX....",
		"XOX..XOOX...",
		"XX...XOOX...",
		"X.....XOOX..",
		"......XOOX..",
		".......XX..."};

	private FilmCursor() {
	}

	static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (FilmSelfTest.active() && screen instanceof HandledScreen<?>) {
				ScreenEvents.afterRender(screen).register(FilmCursor::draw);
			}
		});
	}

	static void show(boolean on) {
		shown = on;
	}

	/** Puts the pointer at (x, y), in the screen's (scaled) pixels. */
	static void place(double x, double y) {
		fromX = toX = x;
		fromY = toY = y;
		startedAt = Capture.time();
		length = 1.0;
	}

	/** Moves the pointer from where it is now to (x, y) over {@code ticks}. */
	static void moveTo(double x, double y, double ticks) {
		double[] now = at(Capture.time());
		fromX = now[0];
		fromY = now[1];
		toX = x;
		toY = y;
		startedAt = Capture.time();
		length = Math.max(1.0, ticks);
	}

	private static double[] at(double time) {
		double t = Math.min(1.0, Math.max(0.0, (time - startedAt) / length));
		double k = t * t * t * (10.0 - 15.0 * t + 6.0 * t * t);
		return new double[] {fromX + (toX - fromX) * k, fromY + (toY - fromY) * k};
	}

	private static void draw(Screen screen, DrawContext context, int mouseX, int mouseY, float delta) {
		if (!shown) {
			return;
		}
		double[] p = at(Capture.time());
		MinecraftClient client = MinecraftClient.getInstance();
		Window window = client.getWindow();
		// Where the game takes the mouse to be, in the window's own pixels.
		FilmMouseAccessor mouse = (FilmMouseAccessor) client.mouse;
		mouse.shootingstar$setX(p[0] * window.getWidth() / window.getScaledWidth());
		mouse.shootingstar$setY(p[1] * window.getHeight() / window.getScaledHeight());
		int x = (int) Math.round(p[0]);
		int y = (int) Math.round(p[1]);
		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, 500.0F);
		for (int row = 0; row < ARROW.length; row++) {
			String line = ARROW[row];
			for (int col = 0; col < line.length(); col++) {
				char c = line.charAt(col);
				if (c == '.') {
					continue;
				}
				context.fill(x + col, y + row, x + col + 1, y + row + 1, c == 'X' ? 0xFF000000 : 0xFFFFFFFF);
			}
		}
		context.getMatrices().pop();
	}
}
