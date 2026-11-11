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
	/** Hold, when you are down, to give up and die at once. */
	public static KeyBinding GIVE_UP;

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
		GIVE_UP = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chainsawman.give_up", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X,
				"key.categories.chainsawman"));
		ClientCombat.init();
		ClientTeams.init();
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
		EntityRendererRegistry.register(ChainsawMan.SHAFT_ARM, ShaftArmRenderer::new);
		EntityRendererRegistry.register(ChainsawMan.SHAFT_HEAD, ShaftHeadRenderer::new);
		EntityRendererRegistry.register(ChainsawMan.SHAFT_CORE, ShaftCoreRenderer::new);
		EntityRendererRegistry.register(ChainsawMan.FLESH_GROWTH, FleshGrowthRenderer::new);
		EntityRendererRegistry.register(ChainsawMan.SQUAD_MEMBER, SquadRenderer::new);
		EntityRendererRegistry.register(ChainsawMan.FOREVER_CUBE, ForeverCubeRenderer::new);
		io.github.kortev.chainsawman.client.devil.DevilsClient.init();
		// Public Safety: the licence key and card, the payday.
		io.github.kortev.chainsawman.client.safety.SafetyClient.init();
		io.github.kortev.chainsawman.client.devil.LeechClient.init();
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
		WorldRenderEvents.AFTER_ENTITIES.register(ClientNotYet::render);
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
		ClientPlayNetworking.registerGlobalReceiver(io.github.kortev.chainsawman.network.ForeverCatchUpPayload.ID, (payload, context) -> ForeverHud.catchUp(payload.ticks()));
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
		ClientPlayNetworking.registerGlobalReceiver(io.github.kortev.chainsawman.network.BondPayload.ID,
				(payload, context) -> ClientBonds.set(io.github.kortev.chainsawman.contract.Contract.byOrdinal(payload.contract()), payload.uses(), payload.up()));
		ClientPlayNetworking.registerGlobalReceiver(io.github.kortev.chainsawman.network.NotYetPayload.ID,
				(payload, context) -> ClientNotYet.set(payload.player(), payload.ticks()));
		WorldRenderEvents.AFTER_ENTITIES.register(ChainRender::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientForms.clear();
			ClientContracts.clear();
			ClientForever.clear();
			ClientBonds.clear();
			ClientNotYet.clear();
			BeamClient.clear();
			ClientHotel.clear();
			ClientTeams.clear();
		});
		CoreShaderRegistrationCallback.EVENT.register(context -> context.register(ChainsawMan.id("csm_impact"), VertexFormats.BLIT_SCREEN,
				program -> FormHud.impact = program));
		CoreShaderRegistrationCallback.EVENT.register(context -> context.register(ChainsawMan.id("csm_drain"), VertexFormats.BLIT_SCREEN,
				program -> ForeverHud.drain = program));
		CoreShaderRegistrationCallback.EVENT.register(context -> context.register(ChainsawMan.id("csm_flesh"), VertexFormats.POSITION_TEXTURE_COLOR_NORMAL,
				program -> FleshMesh.program = program));
		net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(context -> FleshMesh.flush());
		HudRenderCallback.EVENT.register(FormHud::render);
		HudRenderCallback.EVENT.register(ForeverHud::render);
		HudRenderCallback.EVENT.register(ContractHud::render);
		HudRenderCallback.EVENT.register(BloodHud::render);
		HudRenderCallback.EVENT.register(LensBlood::render);
		HudRenderCallback.EVENT.register(Sfx::render);
		HudRenderCallback.EVENT.register(DreamHud::render);
		HudRenderCallback.EVENT.register(TeamsHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (PULL_CORD.wasPressed()) {
				pullCord(client);
			}
			while (USE_CONTRACT.wasPressed()) {
				if (client.player != null && ClientPlayNetworking.canSend(io.github.kortev.chainsawman.network.UseContractPayload.ID)) {
					ClientPlayNetworking.send(new io.github.kortev.chainsawman.network.UseContractPayload());
				}
			}
			switchKey(client);
			ClientNotYet.tick(client);
			ClientForms.tick(client);
			ClientCombat.tick(client);
			DreamHud.tick(client);
			FormCinematic.tick(client);
			ReviveCord.tick(client);
			BeamClient.tick(client);
			ClientForever.tick(client);
			ClientHotel.tick();
			ForeverHud.tick(client);
			ClientTeams.tick(client);
		});
	}

	/** How many ticks the switch key held makes it "Not yet" (a tap is a switch). */
	private static final int HOLD = 8;
	/** How long the switch key has been held (-1 up; HOLD + 1 once "Not yet" has been asked). */
	private static int switchHeld = -1;

	/**
	 * The switch-contract key: a tap switches contract. With Forever selected at bond 3 a tap is only known on release, and holding it past
	 * {@link #HOLD} ticks asks for "Not yet" instead; at any other time the key acts the moment it is pressed, as it always did.
	 */
	private static void switchKey(MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		boolean canSend = ClientPlayNetworking.canSend(io.github.kortev.chainsawman.network.SwitchContractPayload.ID);
		if (!ClientBonds.canNotYet()) {
			switchHeld = -1;
			while (SWITCH_CONTRACT.wasPressed()) {
				if (canSend) {
					ClientPlayNetworking.send(new io.github.kortev.chainsawman.network.SwitchContractPayload());
				}
			}
			return;
		}
		while (SWITCH_CONTRACT.wasPressed()) {
			if (switchHeld < 0) {
				switchHeld = 0;
			}
		}
		if (SWITCH_CONTRACT.isPressed() && switchHeld >= 0) {
			switchHeld++;
			if (switchHeld == HOLD && ClientPlayNetworking.canSend(io.github.kortev.chainsawman.network.NotYetKeyPayload.ID)) {
				ClientPlayNetworking.send(new io.github.kortev.chainsawman.network.NotYetKeyPayload());
				switchHeld = HOLD + 1;
			}
		} else if (switchHeld >= 0) {
			if (switchHeld < HOLD && canSend) {
				ClientPlayNetworking.send(new io.github.kortev.chainsawman.network.SwitchContractPayload());
			}
			switchHeld = -1;
		}
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
