package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.PochitaItem;
import io.github.kortev.chainsawman.network.FormPayload;
import io.github.kortev.chainsawman.network.FormPullPayload;
import io.github.kortev.chainsawman.network.PullCordPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Chainsaw Man on the client: the cord key, who is in the form and pulling the cord, and the form drawn and heard on them. */
public final class ChainsawManClient implements ClientModInitializer {
	/** Pull the cord: into the form with Pochita carried, or out of it again. */
	public static KeyBinding PULL_CORD;

	@Override
	public void onInitializeClient() {
		PULL_CORD = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chainsawman.pull_cord", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G,
				"key.categories.chainsawman"));
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof PlayerEntityRenderer player) {
				helper.register(new FormFeature(player));
			}
		});
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return ChainsawMan.id("form_mesh");
			}

			@Override
			public void reload(ResourceManager manager) {
				FormMesh.reset();
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(FormPayload.ID, (payload, context) -> ClientForms.set(payload.player(), payload.on()));
		ClientPlayNetworking.registerGlobalReceiver(FormPullPayload.ID, (payload, context) -> {
			if (context.client().world != null) {
				PlayerEntity puller = context.client().world.getPlayerByUuid(payload.player());
				ClientForms.pulled(payload.player(), context.client().world.getTime(), puller != null ? puller.getYaw() : 0.0F);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientForms.clear());
		CoreShaderRegistrationCallback.EVENT.register(context -> context.register(ChainsawMan.id("csm_impact"), VertexFormats.BLIT_SCREEN,
				program -> FormHud.impact = program));
		HudRenderCallback.EVENT.register(FormHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (PULL_CORD.wasPressed()) {
				pullCord(client);
			}
			ClientForms.tick(client);
			FormCinematic.tick(client);
		});
	}

	/** The cord key: only worth asking the server with Pochita carried, or to come back out of the form. */
	private static void pullCord(MinecraftClient client) {
		if (client.player != null && (PochitaItem.carried(client.player) || ClientForms.on(client.player))
				&& ClientPlayNetworking.canSend(PullCordPayload.ID)) {
			ClientPlayNetworking.send(new PullCordPayload());
		}
	}
}
