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
	/** Fire the chain from the arm; call up Beam, the ult (the key was the blood frenzy's, so its id is). */
	public static KeyBinding CHAIN;
	public static KeyBinding FRENZY;
	/** The kick. */
	public static KeyBinding KICK;
	/** Use the power of the devil contract you hold (Forever, for the Eternity Devil's). */
	public static KeyBinding USE_CONTRACT;
	/** Switch to the next contract you hold (up to three). */
	public static KeyBinding SWITCH_CONTRACT;

	@Override
	public void onInitializeClient() {
		PULL_CORD = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chainsawman.pull_cord", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G,
				"key.categories.chainsawman"));
		CHAIN = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chainsawman.chain", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R,
				"key.categories.chainsawman"));
		FRENZY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chainsawman.frenzy", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B,
				"key.categories.chainsawman"));
		KICK = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chainsawman.kick", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V,
				"key.categories.chainsawman"));
		USE_CONTRACT = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chainsawman.use_contract", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U,
				"key.categories.chainsawman"));
		SWITCH_CONTRACT = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chainsawman.switch_contract", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O,
				"key.categories.chainsawman"));
		ClientCombat.init();
		// /chainsawhead boxes|mesh: the two heads to choose between, here on this client.
		net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("chainsawhead")
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("boxes").executes(context -> {
							FormHeadModel.on = true;
							context.getSource().sendFeedback(net.minecraft.text.Text.translatable("commands.chainsawhead.boxes"));
							return 1;
						}))
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("mesh").executes(context -> {
							FormHeadModel.on = false;
							context.getSource().sendFeedback(net.minecraft.text.Text.translatable("commands.chainsawhead.mesh"));
							return 1;
						}))));
		EntityModelLayerRegistry.registerModelLayer(PochitaRenderer.LAYER, PochitaModel::texturedModelData);
		EntityRendererRegistry.register(ChainsawMan.POCHITA_ENTITY, PochitaRenderer::new);
		EntityRendererRegistry.register(ChainsawMan.FOREVER_HUB, ForeverHubRenderer::new);
		EntityRendererRegistry.register(ChainsawMan.FOREVER_COPY, ForeverCopyRenderer::new);
		EntityRendererRegistry.register(ChainsawMan.ETERNITY_DEVIL, EternityDevilRenderer::new);
		EntityRendererRegistry.register(ChainsawMan.FLESH_PATCH, FleshPatchRenderer::new);
		EntityRendererRegistry.register(ChainsawMan.FLESH_GROWTH, FleshGrowthRenderer::new);
		EntityRendererRegistry.register(ChainsawMan.SQUAD_MEMBER, SquadRenderer::new);
		EntityRendererRegistry.register(ChainsawMan.FOREVER_CUBE, ForeverCubeRenderer::new);
		WorldRenderEvents.LAST.register(context -> {
			ClientForever.projection = new org.joml.Matrix4f(context.projectionMatrix());
			ClientForever.view = new org.joml.Matrix4f(context.positionMatrix());
			ClientForever.camera = context.camera().getPos();
		});
		ClientPlayNetworking.registerGlobalReceiver(io.github.kortev.chainsawman.network.ForeverLockPayload.ID, (payload, context) -> ClientForever.told(payload));
		EntityModelLayerRegistry.registerModelLayer(BeamRenderer.LAYER, BeamModel::texturedModelData);
		EntityRendererRegistry.register(ChainsawMan.BEAM_ENTITY, BeamRenderer::new);
		WorldRenderEvents.AFTER_ENTITIES.register(MergeCinematic::render);
		WorldRenderEvents.AFTER_ENTITIES.register(SawTrails::render);
		WorldRenderEvents.AFTER_ENTITIES.register(CutDown::render);
		WorldRenderEvents.AFTER_ENTITIES.register(Severed::render);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof PlayerEntityRenderer player) {
				MergeCinematic.setModel(new PochitaModel(context.getPart(PochitaRenderer.LAYER)));
				helper.register(new FormFeature(player));
				helper.register(new HeldPochita(player, new PochitaModel(context.getPart(PochitaRenderer.LAYER))));
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
		ClientPlayNetworking.registerGlobalReceiver(io.github.kortev.chainsawman.network.HotelPayloads.Fog.ID, (payload, context) -> ClientHotel.fog(payload.end()));
		ClientPlayNetworking.registerGlobalReceiver(io.github.kortev.chainsawman.network.HotelPayloads.Speech.ID, (payload, context) -> ClientHotel.speech(payload));
		ClientPlayNetworking.registerGlobalReceiver(io.github.kortev.chainsawman.network.HotelPayloads.Offer.ID, (payload, context) -> ClientHotel.offer(payload.ticks(), payload.hybrid()));
		ClientPlayNetworking.registerGlobalReceiver(io.github.kortev.chainsawman.network.HotelPayloads.Drop.ID,
				(payload, context) -> MinecraftClient.getInstance().setScreen(new DropScreen(payload.held())));
		ClientPlayNetworking.registerGlobalReceiver(io.github.kortev.chainsawman.network.HotelPayloads.Devil.ID, (payload, context) -> ClientHotel.devil(payload));
		HudRenderCallback.EVENT.register(HotelHud::render);
		ClientPlayNetworking.registerGlobalReceiver(FormPayload.ID, (payload, context) -> ClientForms.set(payload.player(), payload.on()));
		ClientPlayNetworking.registerGlobalReceiver(FormPullPayload.ID, (payload, context) -> {
			if (context.client().world != null) {
				PlayerEntity puller = context.client().world.getPlayerByUuid(payload.player());
				// (A friend's yank on someone who was down starts the transformation partway in.)
				ClientForms.pulled(payload.player(), context.client().world.getTime() - payload.skip(), puller != null ? puller.getYaw() : 0.0F);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(io.github.kortev.chainsawman.network.ContractPayload.ID,
				(payload, context) -> ClientContracts.set(payload.player(), io.github.kortev.chainsawman.contract.Contract.byOrdinal(payload.contract())));
		ClientPlayNetworking.registerGlobalReceiver(FormEventPayload.ID, (payload, context) -> ClientCombat.event(payload));
		WorldRenderEvents.AFTER_ENTITIES.register(ChainRender::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientForms.clear();
			ClientContracts.clear();
			ClientForever.clear();
			BeamClient.clear();
			ClientHotel.clear();
		});
		CoreShaderRegistrationCallback.EVENT.register(context -> context.register(ChainsawMan.id("csm_impact"), VertexFormats.BLIT_SCREEN,
				program -> FormHud.impact = program));
		CoreShaderRegistrationCallback.EVENT.register(context -> context.register(ChainsawMan.id("csm_drain"), VertexFormats.BLIT_SCREEN,
				program -> ForeverHud.drain = program));
		HudRenderCallback.EVENT.register(FormHud::render);
		HudRenderCallback.EVENT.register(ForeverHud::render);
		HudRenderCallback.EVENT.register(BloodHud::render);
		HudRenderCallback.EVENT.register(LensBlood::render);
		HudRenderCallback.EVENT.register(Sfx::render);
		HudRenderCallback.EVENT.register(DreamHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (PULL_CORD.wasPressed()) {
				pullCord(client);
			}
			while (USE_CONTRACT.wasPressed()) {
				if (client.player != null && ClientPlayNetworking.canSend(io.github.kortev.chainsawman.network.UseContractPayload.ID)) {
					ClientPlayNetworking.send(new io.github.kortev.chainsawman.network.UseContractPayload());
				}
			}
			while (SWITCH_CONTRACT.wasPressed()) {
				if (client.player != null && ClientPlayNetworking.canSend(io.github.kortev.chainsawman.network.SwitchContractPayload.ID)) {
					ClientPlayNetworking.send(new io.github.kortev.chainsawman.network.SwitchContractPayload());
				}
			}
			ClientForms.tick(client);
			ClientCombat.tick(client);
			DreamHud.tick(client);
			FormCinematic.tick(client);
			ReviveCord.tick(client);
			BeamClient.tick(client);
			ClientForever.tick(client);
			ClientHotel.tick();
			ForeverHud.tick(client);
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
