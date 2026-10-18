package io.github.kortev.gojolander.client;

import io.github.kortev.gojolander.BlindfoldItem;
import io.github.kortev.gojolander.Gojolander;
import io.github.kortev.gojolander.domain.DomainHold;
import io.github.kortev.gojolander.network.DomainCastPayload;
import io.github.kortev.gojolander.network.DomainCaughtPayload;
import io.github.kortev.gojolander.network.DomainEndPayload;
import io.github.kortev.gojolander.network.DomainLockPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Gojolander on the client: the Domain key, what it is told of Domains, and their pictures and sounds; and the
 * blindfold drawn on whoever wears it.
 */
public final class GojolanderClient implements ClientModInitializer {
	/** Domain Expansion: cast with the blindfold on. */
	public static KeyBinding DOMAIN;

	@Override
	public void onInitializeClient() {
		DOMAIN = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.gojolander.domain", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z,
				"key.categories.gojolander"));
		CoreShaderRegistrationCallback.EVENT.register(context -> {
			context.register(Gojolander.id("gl_domain"), VertexFormats.BLIT_SCREEN, program -> DomainRender.domain = program);
			context.register(Gojolander.id("gl_flood"), VertexFormats.BLIT_SCREEN, program -> DomainHud.flood = program);
		});
		DomainHold.client = ClientDomains::heldHere;
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof PlayerEntityRenderer player) {
				helper.register(new BlindfoldFeature(player));
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(DomainLockPayload.ID, (payload, context) -> ClientDomains.onLock(payload, context.client()));
		ClientPlayNetworking.registerGlobalReceiver(DomainCaughtPayload.ID,
				(payload, context) -> ClientDomains.onCaught(payload, context.client()));
		ClientPlayNetworking.registerGlobalReceiver(DomainEndPayload.ID, (payload, context) -> ClientDomains.onEnd(payload, context.client()));
		ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> ClientDomains.clear(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientDomains.clear(client));

		// Before anything reads the keys this tick.
		ClientTickEvents.START_CLIENT_TICK.register(ClientDomains::holdInput);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (DOMAIN.wasPressed()) {
				cast(client);
			}
			ClientDomains.tick(client);
		});
		WorldRenderEvents.LAST.register(DomainRender::render);
		HudRenderCallback.EVENT.register(DomainHud::render);
	}

	/** The Domain key: only worth asking the server with the blindfold on; the server says whether it is theirs to cast. */
	private static void cast(MinecraftClient client) {
		if (client.player != null && BlindfoldItem.wearing(client.player) && ClientPlayNetworking.canSend(DomainCastPayload.ID)) {
			ClientPlayNetworking.send(new DomainCastPayload());
		}
	}
}
