package io.github.kortev.shootingstar.client;

import io.github.kortev.shootingstar.client.render.HudEffects;
import io.github.kortev.shootingstar.client.render.WorldEffects;
import io.github.kortev.shootingstar.client.render.WorldProjector;
import io.github.kortev.shootingstar.network.StrikeCancelPayload;
import io.github.kortev.shootingstar.network.StrikeImpactPayload;
import io.github.kortev.shootingstar.network.StrikeLockPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ShootingStarClient implements ClientModInitializer {
	public static KeyBinding SKIP_FEED;

	@Override
	public void onInitializeClient() {
		ClientConfig.load();
		SKIP_FEED = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shootingstar.skip_feed",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_BACKSPACE, "key.categories.shootingstar"));

		ClientPlayNetworking.registerGlobalReceiver(StrikeLockPayload.ID,
				(payload, context) -> ClientStrikes.onLock(payload, context.client()));
		ClientPlayNetworking.registerGlobalReceiver(StrikeImpactPayload.ID,
				(payload, context) -> ClientStrikes.onImpact(payload, context.client()));
		ClientPlayNetworking.registerGlobalReceiver(StrikeCancelPayload.ID,
				(payload, context) -> ClientStrikes.onCancel(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientStrikes.clear(client));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (SKIP_FEED.wasPressed()) {
				ClientStrikes.skipFeed();
			}
			Aim.tick(client);
			ClientStrikes.tick(client);
		});

		WorldRenderEvents.AFTER_TRANSLUCENT.register(WorldEffects::render);
		WorldRenderEvents.LAST.register(WorldProjector::capture);
		HudRenderCallback.EVENT.register(HudEffects::render);
	}
}
