package io.github.kortev.shootingstar.client;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.client.gap.ClientGaps;
import io.github.kortev.shootingstar.client.gap.GapHud;
import io.github.kortev.shootingstar.client.gap.GapRender;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.render.HudEffects;
import io.github.kortev.shootingstar.client.render.WorldEffects;
import io.github.kortev.shootingstar.client.render.WorldProjector;
import io.github.kortev.shootingstar.client.world.WorldFx;
import io.github.kortev.shootingstar.item.GenesisKeyItem;
import io.github.kortev.shootingstar.client.gap.VoidFx;
import io.github.kortev.shootingstar.gap.VoidFloor;
import io.github.kortev.shootingstar.network.GapEndPayload;
import io.github.kortev.shootingstar.network.GapFloorPayload;
import io.github.kortev.shootingstar.network.GapWarpPayload;
import io.github.kortev.shootingstar.network.GapLockPayload;
import io.github.kortev.shootingstar.network.StrikeCancelPayload;
import io.github.kortev.shootingstar.network.StrikeImpactPayload;
import io.github.kortev.shootingstar.network.StrikeLockPayload;
import io.github.kortev.shootingstar.registry.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ShootingStarClient implements ClientModInitializer {
	public static KeyBinding SKIP_FEED;

	@Override
	public void onInitializeClient() {
		// The key cracks with the clunk of its first turn (the server marks it cracked as it starts turning).
		ModelPredicateProviderRegistry.register(ModItems.GENESIS_KEY, ShootingStar.id("cracked"), (stack, world, entity, seed) ->
				GenesisKeyItem.cracked(stack) && !ClientGaps.keyStillWhole() ? 1.0F : 0.0F);
		ClientConfig.load();
		Shaders.register();
		SKIP_FEED = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shootingstar.skip_feed",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_BACKSPACE, "key.categories.shootingstar"));

		ClientPlayNetworking.registerGlobalReceiver(StrikeLockPayload.ID,
				(payload, context) -> ClientStrikes.onLock(payload, context.client()));
		ClientPlayNetworking.registerGlobalReceiver(StrikeImpactPayload.ID,
				(payload, context) -> ClientStrikes.onImpact(payload, context.client()));
		ClientPlayNetworking.registerGlobalReceiver(StrikeCancelPayload.ID,
				(payload, context) -> ClientStrikes.onCancel(payload));
		ClientPlayNetworking.registerGlobalReceiver(GapLockPayload.ID, (payload, context) -> ClientGaps.onLock(payload, context.client()));
		ClientPlayNetworking.registerGlobalReceiver(GapEndPayload.ID, (payload, context) -> ClientGaps.onEnd(payload));
		ClientPlayNetworking.registerGlobalReceiver(GapFloorPayload.ID, (payload, context) -> ClientGaps.onFloor(payload, context.client()));
		ClientPlayNetworking.registerGlobalReceiver(GapWarpPayload.ID, (payload, context) -> VoidFx.onWarp(payload, context.client()));
		VoidFloor.client = ClientGaps::floorFor;
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientStrikes.clear(client);
			ClientGaps.clear(client);
		});

		// Before anything reads the keys this tick.
		ClientTickEvents.START_CLIENT_TICK.register(ClientGaps::holdInput);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (SKIP_FEED.wasPressed()) {
				ClientStrikes.skipFeed();
				ClientGaps.skipFeed();
			}
			Aim.tick(client);
			ClientStrikes.tick(client);
			ClientGaps.tick(client);
			VoidFx.tick(client);
		});

		WorldRenderEvents.AFTER_TRANSLUCENT.register(WorldEffects::render);
		WorldRenderEvents.LAST.register(WorldProjector::capture);
		WorldRenderEvents.LAST.register(WorldFx::render);
		WorldRenderEvents.LAST.register(GapRender::render);
		WorldRenderEvents.LAST.register(VoidFx::render);
		HudRenderCallback.EVENT.register(HudEffects::render);
		HudRenderCallback.EVENT.register(GapHud::render);
	}
}
