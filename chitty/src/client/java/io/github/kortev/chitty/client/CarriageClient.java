package io.github.kortev.chitty.client;

import io.github.kortev.chitty.ChittyControls;
import io.github.kortev.chitty.carriage.Carriage;
import io.github.kortev.chitty.carriage.CarriageActionPayload;
import io.github.kortev.chitty.carriage.CarriageEntity;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
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
 * The Child Catcher's carriage on the client: how she and her horse are drawn and heard, and the driver's reins, whip
 * (jump) and disguise (a key of its own). Set up from Chitty's client initializer.
 */
public final class CarriageClient {
	public static KeyBinding DISGUISE;

	private static final Set<CarriageEntity> SOUNDING = Collections.newSetFromMap(new WeakHashMap<>());
	private static boolean wasJumping;
	/** What the driver was last shown on the action bar: 0 nothing, 1 driving. */
	private static int hint;

	private CarriageClient() {
	}

	public static void init() {
		EntityRendererRegistry.register(Carriage.ENTITY, CarriageRenderer::new);
		EntityRendererRegistry.register(Carriage.PART, EmptyEntityRenderer::new);
		DISGUISE = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shootingstar.carriage_disguise", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_J, "key.categories.shootingstar"));
		// Her horse's harness (her horse is the game's own model, CarriageHorse).
		EntityModelLayerRegistry.registerModelLayer(CarriageHorse.HARNESS, CarriageHorse::getTexturedModelData);
		CarriageEntity.client = new CarriageEntity.ClientHooks() {
			@Override
			public ChittyControls controls(CarriageEntity carriage) {
				return CarriageClient.controls(carriage);
			}

			@Override
			public void tick(CarriageEntity carriage) {
				if (SOUNDING.add(carriage)) {
					MinecraftClient.getInstance().getSoundManager().play(new CarriageSound(carriage));
				}
			}
		};
		ClientTickEvents.END_CLIENT_TICK.register(CarriageClient::tick);
	}

	private static ChittyControls controls(CarriageEntity carriage) {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null || carriage.getControllingPassenger() != player) {
			return ChittyControls.NONE;
		}
		Input in = player.input;
		int forward = (in.pressingForward ? 1 : 0) - (in.pressingBack ? 1 : 0);
		int turn = (in.pressingLeft ? 1 : 0) - (in.pressingRight ? 1 : 0);
		return new ChittyControls(forward, turn, in.jumping, false);
	}

	private static void act(int action) {
		if (ClientPlayNetworking.canSend(CarriageActionPayload.ID)) {
			ClientPlayNetworking.send(new CarriageActionPayload(action));
		}
	}

	private static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		CarriageEntity carriage = player != null && player.getVehicle() instanceof CarriageEntity c ? c : null;
		boolean driving = carriage != null && carriage.getControllingPassenger() == player;
		// The whip: a crack each time jump goes down. The driver's client moves her, so it sets the horse galloping
		// itself; the server hears the crack and throws off the disguise.
		boolean jumping = driving && player.input.jumping;
		if (jumping && !wasJumping) {
			carriage.crackWhip(player);
			act(CarriageEntity.ACTION_WHIP);
		}
		wasJumping = jumping;
		while (DISGUISE.wasPressed()) {
			if (driving) {
				act(CarriageEntity.ACTION_DISGUISE);
			}
		}
		// A word on the reins when someone takes them.
		int now = driving ? 1 : 0;
		if (now != hint) {
			if (now != 0) {
				player.sendMessage(Text.translatable("hud.shootingstar.carriage.driving", client.options.jumpKey.getBoundKeyLocalizedText(),
						DISGUISE.getBoundKeyLocalizedText(), client.options.sneakKey.getBoundKeyLocalizedText()), true);
			}
			hint = now;
		}
	}
}
