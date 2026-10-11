package io.github.kortev.chitty.client;

import io.github.kortev.chitty.Chitty;
import io.github.kortev.chitty.ChittyControls;
import io.github.kortev.chitty.ChittyEjectPayload;
import io.github.kortev.chitty.ChittyEntity;
import io.github.kortev.chitty.ChittyHornPayload;
import io.github.kortev.chitty.ChittyInputPayload;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.Arrays;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
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
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Chitty on the client: how she is drawn and heard, the driver's controls, the horn, the wings, the ejector and revving
 * her; the shimmer of heat off her bonnet; and the film's other vehicles, each set up from here (the airship:
 * {@link AirshipClient}; the Child Catcher's carriage: {@link CarriageClient}).
 */
public final class ChittyClient implements ClientModInitializer {
	public static KeyBinding HORN;
	public static KeyBinding WINGS;
	public static KeyBinding EJECT;
	public static KeyBinding REV;

	private static byte lastControls = -1;
	private static byte lastState;
	private static int sinceSent;
	private static int hornCooldown;
	/** Whether the driver has seen her being cranked since they took the wheel (so a stopped engine is a stall). */
	private static boolean cranked;
	/** Whether she was cranked and didn't catch, as the driver sees it (ChittyHud). */
	private static boolean stalled;

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(Chitty.ENTITY, ChittyRenderer::new);
		// Her hitboxes along her length are drawn as part of her.
		EntityRendererRegistry.register(Chitty.PART, EmptyEntityRenderer::new);
		HORN = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shootingstar.chitty_horn", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_H, "key.categories.shootingstar"));
		WINGS = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shootingstar.chitty_wings", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_G, "key.categories.shootingstar"));
		EJECT = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shootingstar.chitty_eject", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_X, "key.categories.shootingstar"));
		REV = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shootingstar.chitty_rev", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_V, "key.categories.shootingstar"));
		ParticleFactoryRegistry.getInstance().register(Chitty.HEAT, ChittyHeatParticle.Factory::new);
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return ShootingStar.id("vehicle_meshes");
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
				VehicleSounds.keep(car, () -> Arrays.stream(ChittySound.Layer.values()).map(layer -> new ChittySound(car, layer)).toList());
			}
		};
		ClientTickEvents.END_CLIENT_TICK.register(ChittyClient::tick);
		// The controls, on screen.
		HudRenderCallback.EVENT.register(ChittyHud::render);
		AirshipClient.init();
		CarriageClient.init();
	}

	/** Whether she was cranked and didn't catch, as the driver sees it. */
	static boolean stalled() {
		return stalled;
	}

	private static ChittyControls controls(ChittyEntity car) {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null || car.getControllingPassenger() != player) {
			return ChittyControls.NONE;
		}
		Input in = player.input;
		int forward = (in.pressingForward ? 1 : 0) - (in.pressingBack ? 1 : 0);
		int turn = (in.pressingLeft ? 1 : 0) - (in.pressingRight ? 1 : 0);
		return new ChittyControls(forward, turn, in.jumping, MinecraftClient.getInstance().options.sprintKey.isPressed(), REV.isPressed());
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
		// Whether she didn't catch when cranked, for the controls on screen (ChittyHud).
		cranked = driving && (cranked || car.isCranking());
		stalled = driving && cranked && !car.isCranking() && !car.isEngineRunning();
		if (car == null) {
			lastControls = -1;
		}
	}
}
