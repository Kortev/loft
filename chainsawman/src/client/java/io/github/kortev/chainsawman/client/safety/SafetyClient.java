package io.github.kortev.chainsawman.client.safety;

import io.github.kortev.chainsawman.safety.Safety;
import io.github.kortev.chainsawman.safety.SafetyPayloads;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/** Public Safety on the client: the licence key (J) and card, the payday on screen, and the devil parts' tooltip. */
public final class SafetyClient {
	public static KeyBinding LICENCE;
	/** The last licence the server told us of. */
	static long yen;
	static Map<String, Integer> hunted = Map.of();
	static List<String> contracts = List.of();

	private SafetyClient() {
	}

	/** Called once from the client's init. */
	public static void init() {
		LICENCE = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chainsawman.licence", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J,
				"key.categories.chainsawman"));
		ClientPlayNetworking.registerGlobalReceiver(SafetyPayloads.Licence.ID, (payload, context) -> {
			yen = payload.yen();
			hunted = payload.hunted();
			contracts = payload.contracts();
			// (A card already out reads these as it draws.)
			if (payload.open()) {
				context.client().setScreen(new LicenceScreen());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(SafetyPayloads.Payday.ID, (payload, context) -> PaydayHud.start(payload));
		HudRenderCallback.EVENT.register(PaydayHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (LICENCE.wasPressed()) {
				if (client.player != null && client.currentScreen == null) {
					ClientPlayNetworking.send(new SafetyPayloads.Ask());
				}
			}
			PaydayHud.tick(client);
		});
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (Safety.price(stack) > 0) {
				lines.add(Text.translatable("safety.chainsawman.tooltip").formatted(Formatting.GRAY));
			}
		});
	}

	/** Yen as the card writes it: ¥13,000. */
	static String yen(long amount) {
		return "¥" + String.format(java.util.Locale.ROOT, "%,d", amount);
	}
}
