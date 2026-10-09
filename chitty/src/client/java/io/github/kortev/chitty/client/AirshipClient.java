package io.github.kortev.chitty.client;

import io.github.kortev.chitty.ChittyControls;
import io.github.kortev.chitty.airship.Airship;
import io.github.kortev.chitty.airship.AirshipActionPayload;
import io.github.kortev.chitty.airship.AirshipEntity;
import io.github.kortev.chitty.airship.AirshipInputPayload;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The airship on the client: how she is drawn and heard, the pilot's controls, and the crew's keys for the grapple,
 * the ladder, the bombs and (the pilot's) overboard. Set up from Chitty's client initializer.
 */
public final class AirshipClient {
	public static KeyBinding GRAPPLE;
	public static KeyBinding LADDER;
	public static KeyBinding BOMB;
	public static KeyBinding OVERBOARD;

	private static final Set<AirshipEntity> SOUNDING = Collections.newSetFromMap(new WeakHashMap<>());
	private static byte lastControls = -1;
	private static int sinceSent;
	/** What the rider was last shown on the action bar: 0 nothing, 1 crew, 2 pilot, 3 overloaded. */
	private static int hint;

	private AirshipClient() {
	}

	public static void init() {
		EntityRendererRegistry.register(Airship.ENTITY, AirshipRenderer::new);
		EntityRendererRegistry.register(Airship.PART, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(Airship.HOOK, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(Airship.BOMB_ENTITY, AirshipBombRenderer::new);
		GRAPPLE = key("airship_grapple", GLFW.GLFW_KEY_R);
		LADDER = key("airship_ladder", GLFW.GLFW_KEY_K);
		BOMB = key("airship_bomb", GLFW.GLFW_KEY_B);
		OVERBOARD = key("airship_overboard", GLFW.GLFW_KEY_O);
		AirshipEntity.client = new AirshipEntity.ClientHooks() {
			@Override
			public ChittyControls controls(AirshipEntity ship) {
				return AirshipClient.controls(ship);
			}

			@Override
			public void sync(AirshipEntity ship, ChittyControls controls) {
				AirshipClient.sync(controls);
			}

			@Override
			public void tick(AirshipEntity ship) {
				if (SOUNDING.add(ship)) {
					MinecraftClient client = MinecraftClient.getInstance();
					for (AirshipSound.Layer layer : AirshipSound.Layer.values()) {
						client.getSoundManager().play(new AirshipSound(ship, layer));
					}
				}
			}
		};
		ClientTickEvents.END_CLIENT_TICK.register(AirshipClient::tick);
	}

	private static KeyBinding key(String name, int code) {
		return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shootingstar." + name, InputUtil.Type.KEYSYM, code,
				"key.categories.shootingstar"));
	}

	private static ChittyControls controls(AirshipEntity ship) {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null || ship.getControllingPassenger() != player) {
			return ChittyControls.NONE;
		}
		Input in = player.input;
		int forward = (in.pressingForward ? 1 : 0) - (in.pressingBack ? 1 : 0);
		int turn = (in.pressingLeft ? 1 : 0) - (in.pressingRight ? 1 : 0);
		return new ChittyControls(forward, turn, in.jumping, MinecraftClient.getInstance().options.sprintKey.isPressed());
	}

	/** Tells the server what the pilot is doing whenever it changes, and once a second regardless. */
	private static void sync(ChittyControls controls) {
		byte packed = controls.pack();
		if ((packed != lastControls || ++sinceSent >= 20) && ClientPlayNetworking.canSend(AirshipInputPayload.ID)) {
			ClientPlayNetworking.send(new AirshipInputPayload(packed));
			lastControls = packed;
			sinceSent = 0;
		}
	}

	private static void act(int action) {
		if (ClientPlayNetworking.canSend(AirshipActionPayload.ID)) {
			ClientPlayNetworking.send(new AirshipActionPayload(action));
		}
	}

	private static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		AirshipEntity ship = player != null && player.getVehicle() instanceof AirshipEntity s ? s : null;
		boolean piloting = ship != null && ship.getControllingPassenger() == player;
		while (GRAPPLE.wasPressed()) {
			if (ship != null) {
				act(AirshipEntity.ACTION_GRAPPLE);
			}
		}
		while (LADDER.wasPressed()) {
			if (ship != null) {
				act(AirshipEntity.ACTION_LADDER);
			}
		}
		while (BOMB.wasPressed()) {
			if (ship != null) {
				act(AirshipEntity.ACTION_BOMB);
			}
		}
		while (OVERBOARD.wasPressed()) {
			if (piloting) {
				act(AirshipEntity.ACTION_OVERBOARD);
			}
		}
		// A word on the controls when someone comes aboard or takes the wheel, and a warning when she is overloaded.
		int now = ship == null ? 0 : ship.isOverloaded() ? 3 : piloting ? 2 : 1;
		if (now != hint) {
			if (now != 0 && player != null) {
				String key = now == 3 ? "hud.shootingstar.airship.heavy" : now == 2 ? "hud.shootingstar.airship.pilot" : "hud.shootingstar.airship.crew";
				player.sendMessage(Text.translatable(key, GRAPPLE.getBoundKeyLocalizedText(), LADDER.getBoundKeyLocalizedText(),
						BOMB.getBoundKeyLocalizedText(), OVERBOARD.getBoundKeyLocalizedText(), AirshipEntity.LIFT), true);
			}
			hint = now;
		}
		if (ship == null) {
			lastControls = -1;
		}
	}
}
