package io.github.kortev.shootingstar.client.chitty;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.chitty.Chitty;
import io.github.kortev.shootingstar.chitty.ChittyControls;
import io.github.kortev.shootingstar.chitty.ChittyEjectPayload;
import io.github.kortev.shootingstar.chitty.ChittyEntity;
import io.github.kortev.shootingstar.chitty.ChittyHornPayload;
import io.github.kortev.shootingstar.chitty.ChittyInputPayload;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Chitty on the client: how she is drawn and heard, the driver's controls, the horn, the wings and the ejector. */
public final class ChittyClient {
	public static KeyBinding HORN;
	public static KeyBinding WINGS;
	public static KeyBinding EJECT;

	private static final Set<ChittyEntity> SOUNDING = Collections.newSetFromMap(new WeakHashMap<>());
	private static byte lastControls = -1;
	private static byte lastState;
	private static int sinceSent;
	private static int hornCooldown;
	/** What the driver was last shown on the action bar: 0 nothing, 1 driving, 2 flying. */
	private static int hint;

	private ChittyClient() {
	}

	public static void init() {
		EntityRendererRegistry.register(Chitty.ENTITY, ChittyRenderer::new);
		// Her hitboxes along her length are drawn as part of her.
		EntityRendererRegistry.register(Chitty.PART, EmptyEntityRenderer::new);
		HORN = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shootingstar.chitty_horn", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_H, "key.categories.shootingstar"));
		WINGS = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shootingstar.chitty_wings", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_G, "key.categories.shootingstar"));
		EJECT = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shootingstar.chitty_eject", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_X, "key.categories.shootingstar"));
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return ShootingStar.id("chitty_mesh");
			}

			@Override
			public void reload(ResourceManager manager) {
				ChittyMesh.reload();
			}
		});
		ChittyEntity.client = new ChittyEntity.ClientHooks() {
			@Override
			public ChittyControls controls(ChittyEntity car) {
				return ChittyClient.controls(car);
			}

			@Override
			public void sync(ChittyEntity car, ChittyControls controls) {
				ChittyClient.sync(car, controls);
			}

			@Override
			public void tick(ChittyEntity car) {
				if (SOUNDING.add(car)) {
					MinecraftClient client = MinecraftClient.getInstance();
					for (ChittySound.Layer layer : ChittySound.Layer.values()) {
						client.getSoundManager().play(new ChittySound(car, layer));
					}
				}
			}
		};
		ClientTickEvents.END_CLIENT_TICK.register(ChittyClient::tick);
	}

	private static ChittyControls controls(ChittyEntity car) {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null || car.getControllingPassenger() != player) {
			return ChittyControls.NONE;
		}
		Input in = player.input;
		int forward = (in.pressingForward ? 1 : 0) - (in.pressingBack ? 1 : 0);
		int turn = (in.pressingLeft ? 1 : 0) - (in.pressingRight ? 1 : 0);
		return new ChittyControls(forward, turn, in.jumping, MinecraftClient.getInstance().options.sprintKey.isPressed());
	}

	/** Tells the server what the driver is doing whenever it changes, and once a second regardless. */
	private static void sync(ChittyEntity car, ChittyControls controls) {
		byte packed = controls.pack();
		byte state = car.getState();
		if ((packed != lastControls || state != lastState || ++sinceSent >= 20) && ClientPlayNetworking.canSend(ChittyInputPayload.ID)) {
			ClientPlayNetworking.send(new ChittyInputPayload(packed, state));
			lastControls = packed;
			lastState = state;
			sinceSent = 0;
		}
	}

	private static void tick(MinecraftClient client) {
		if (hornCooldown > 0) {
			hornCooldown--;
		}
		ClientPlayerEntity player = client.player;
		ChittyEntity car = player != null && player.getVehicle() instanceof ChittyEntity c ? c : null;
		while (HORN.wasPressed()) {
			if (car != null && hornCooldown == 0 && ClientPlayNetworking.canSend(ChittyHornPayload.ID)) {
				ClientPlayNetworking.send(new ChittyHornPayload());
				hornCooldown = 12;
			}
		}
		// The wings, whenever the driver likes: the driver's client moves her, so it opens them itself and tells the
		// server with the next input. (The raft is hers: it comes and goes by itself.)
		boolean driving = car != null && car.getControllingPassenger() == player;
		while (WINGS.wasPressed()) {
			if (driving) {
				car.toggleWings();
			}
		}
		while (EJECT.wasPressed()) {
			if (driving && ClientPlayNetworking.canSend(ChittyEjectPayload.ID)) {
				ClientPlayNetworking.send(new ChittyEjectPayload());
			}
		}
		// A word on the controls when someone takes the wheel, and again when she first takes to the air.
		int now = !driving ? 0 : car.isFlying() && !car.isOnGround() ? 2 : 1;
		if (now != hint) {
			if (now != 0 && player != null) {
				player.sendMessage(Text.translatable(now == 2 ? "hud.shootingstar.chitty.air" : "hud.shootingstar.chitty.road",
						WINGS.getBoundKeyLocalizedText(), EJECT.getBoundKeyLocalizedText(), HORN.getBoundKeyLocalizedText()), true);
			}
			hint = now;
		}
		if (car == null) {
			lastControls = -1;
		}
	}
}
