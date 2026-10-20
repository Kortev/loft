package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.network.FormEventPayload;
import io.github.kortev.chainsawman.network.FormPayload;
import io.github.kortev.chainsawman.network.FormPullPayload;
import io.github.kortev.chainsawman.network.PullCordPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
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

/**
 * Chainsaw Man on the client: the cord key and the fight's keys, who is in the form and pulling the cord and fighting,
 * and the form drawn and heard on them.
 */
public final class ChainsawManClient implements ClientModInitializer {
	/** Pull the cord: into the form with Pochita carried, or out of it again. */
	public static KeyBinding PULL_CORD;
	/** Fire the chain from the arm; call up the blood frenzy. */
	public static KeyBinding CHAIN;
	public static KeyBinding FRENZY;

	@Override
	public void onInitializeClient() {
		PULL_CORD = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chainsawman.pull_cord", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G,
				"key.categories.chainsawman"));
		CHAIN = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chainsawman.chain", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R,
				"key.categories.chainsawman"));
		FRENZY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chainsawman.frenzy", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B,
				"key.categories.chainsawman"));
		ClientCombat.init();
		EntityModelLayerRegistry.registerModelLayer(PochitaRenderer.LAYER, PochitaModel::texturedModelData);
		EntityRendererRegistry.register(ChainsawMan.POCHITA_ENTITY, PochitaRenderer::new);
		WorldRenderEvents.AFTER_ENTITIES.register(MergeCinematic::render);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof PlayerEntityRenderer player) {
				MergeCinematic.setModel(new PochitaModel(context.getPart(PochitaRenderer.LAYER)));
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
		ClientPlayNetworking.registerGlobalReceiver(FormEventPayload.ID, (payload, context) -> ClientCombat.event(payload));
		WorldRenderEvents.AFTER_ENTITIES.register(ChainRender::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientForms.clear());
		CoreShaderRegistrationCallback.EVENT.register(context -> context.register(ChainsawMan.id("csm_impact"), VertexFormats.BLIT_SCREEN,
				program -> FormHud.impact = program));
		HudRenderCallback.EVENT.register(FormHud::render);
		HudRenderCallback.EVENT.register(BloodHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (PULL_CORD.wasPressed()) {
				pullCord(client);
			}
			ClientForms.tick(client);
			ClientCombat.tick(client);
			FormCinematic.tick(client);
		});
	}

	/**
	 * The cord key: always asked of the server, which knows whether you have Pochita's heart (merged, he's no longer an
	 * item you carry) and the blood for it.
	 */
	private static void pullCord(MinecraftClient client) {
		if (client.player != null && ClientPlayNetworking.canSend(PullCordPayload.ID)) {
			ClientPlayNetworking.send(new PullCordPayload());
		}
	}
}
