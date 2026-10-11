package io.github.kortev.chitty.client;

import io.github.kortev.chitty.ChittyEntity;
import io.github.kortev.chitty.client.AirshipHud.Row;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * Chitty's controls on screen, drawn as the airship's and the carriage's are (AirshipHud), so that nobody has to
 * remember them: at the wheel, what each key does just now (on the road, on the water, in the air), lit while held, and
 * how her engine is (not yet started, the handle swinging, or she didn't catch); in a passenger's seat, the horn and
 * getting out. (F1 hides it with the rest of the HUD.)
 */
final class ChittyHud {
	private ChittyHud() {
	}

	static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.getDebugHud().shouldShowDebugHud() || !(player.getVehicle() instanceof ChittyEntity car)) {
			return;
		}
		GameOptions o = client.options;
		List<Row> rows = new ArrayList<>();
		if (car.getControllingPassenger() == player) {
			boolean flying = car.isFlying();
			boolean aloft = flying && !car.isOnGround();
			boolean running = car.isEngineRunning();
			if (!running) {
				// Not yet going: the first press of forward swings her starting handle.
				boolean cranking = car.isCranking();
				rows.add(new Row(o.forwardKey.getBoundKeyLocalizedText(), cranking, ui("crank"), AirshipHud.TEXT));
				if (ChittyClient.stalled()) {
					rows.add(new Row(null, false, ui("stalled"), AirshipHud.WARN));
				} else if (cranking) {
					rows.add(new Row(null, false, ui("cranking"), AirshipHud.DIM));
				}
				rows.add(null);
			}
			rows.add(AirshipHud.row(AirshipHud.keys(o.forwardKey, o.backKey), o.forwardKey.isPressed() || o.backKey.isPressed(),
					ui(aloft ? "air_throttle" : "drive")));
			rows.add(AirshipHud.row(AirshipHud.keys(o.leftKey, o.rightKey), o.leftKey.isPressed() || o.rightKey.isPressed(), ui("steer")));
			if (aloft) {
				rows.add(AirshipHud.row(o.jumpKey, ui("climb")));
				rows.add(AirshipHud.row(o.sprintKey, ui("dive")));
			} else if (flying) {
				rows.add(AirshipHud.row(o.jumpKey, ui("take_off")));
			}
			rows.add(AirshipHud.row(ChittyClient.WINGS, ui(flying ? "wings_in" : "wings_out")));
			if (!aloft) {
				boolean standing = car.getSpeed() < 0.03;
				rows.add(new Row(ChittyClient.REV.getBoundKeyLocalizedText(), ChittyClient.REV.isPressed(), ui("rev"),
						running && standing ? AirshipHud.TEXT : AirshipHud.DIM));
			}
			rows.add(AirshipHud.row(ChittyClient.EJECT, ui("eject")));
			rows.add(AirshipHud.row(ChittyClient.HORN, ui("horn")));
			rows.add(AirshipHud.row(o.sneakKey, ui("get_out")));
			if (car.isFloating() && !aloft) {
				rows.add(new Row(null, false, ui("afloat"), AirshipHud.GOOD));
			}
		} else {
			rows.add(AirshipHud.row(ChittyClient.HORN, ui("horn")));
			rows.add(AirshipHud.row(o.sneakKey, ui("get_out")));
		}
		AirshipHud.draw(ctx, client.textRenderer, ui("title"), rows, null, counter.getTickDelta(false));
	}

	private static Text ui(String name) {
		return Text.translatable("hud.shootingstar.chitty.ui." + name);
	}
}
