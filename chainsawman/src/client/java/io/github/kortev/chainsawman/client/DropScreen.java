package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.contract.Contracts;
import io.github.kortev.chainsawman.network.HotelPayloads;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Every slot is taken and another devil offers its contract: a button for each contract held (the one you give up), and one to keep what you have. */
public class DropScreen extends Screen {
	private final List<String> held;

	public DropScreen(List<String> held) {
		super(Text.translatable("contract.chainsawman.drop.title"));
		this.held = held;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	private void answer(int slot) {
		if (ClientPlayNetworking.canSend(HotelPayloads.DropAnswer.ID)) {
			ClientPlayNetworking.send(new HotelPayloads.DropAnswer(slot));
		}
		MinecraftClient.getInstance().setScreen(null);
	}

	@Override
	protected void init() {
		int w = 200;
		int y = this.height / 2 - 12 * held.size();
		for (int i = 0; i < held.size(); i++) {
			int slot = i;
			this.addDrawableChild(ButtonWidget.builder(Text.translatable("contract.chainsawman.drop.give", i + 1, Contracts.name(held.get(i))), b -> answer(slot))
					.dimensions((this.width - w) / 2, y + 24 * i, w, 20).build());
		}
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("contract.chainsawman.drop.keep"), b -> answer(-1))
				.dimensions((this.width - w) / 2, y + 24 * held.size() + 6, w, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, 0xC0000000);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 12 * held.size() - 26, 0xFFE8DDC4);
		super.render(context, mouseX, mouseY, delta);
	}
}
