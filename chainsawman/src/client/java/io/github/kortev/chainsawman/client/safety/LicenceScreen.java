package io.github.kortev.chainsawman.client.safety;

import io.github.kortev.chainsawman.safety.Safety;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The hunter's licence, one card in Public Safety's look (black, white, one red line): the player's face and name, "Public Safety Devil
 * Hunter", the rank, yen, the devils hunted (each kind with a count) and the contracts held with their prices. Nothing that does not exist
 * yet. Does not pause the game.
 */
public class LicenceScreen extends Screen {
	static final int W = 236;
	static final int BLACK = 0xF0101010;
	static final int WHITE = 0xFFF2F2F2;
	static final int GREY = 0xFF9A9A9A;
	static final int RED = 0xFFD0201A;
	/** Ticks since it came out (it slides up into place). */
	private int age;

	public LicenceScreen() {
		super(Text.translatable("safety.chainsawman.licence.title"));
	}

	@Override
	protected void init() {
		if (age == 0 && client != null) {
			client.getSoundManager().play(PositionedSoundInstance.master(Safety.Sounds.PAPER, 1.15F, 0.8F));
		}
	}

	@Override
	public void tick() {
		age++;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (SafetyClient.LICENCE.matchesKey(keyCode, scanCode) || client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// (The world stays in view round the card, only a little darker.)
		context.fill(0, 0, width, height, 0x60000000);
	}

	/** The card's lines below the name block: label, then entries. */
	private List<Object[]> body(TextRenderer tr, int textW) {
		List<Object[]> out = new ArrayList<>();
		out.add(new Object[]{Text.translatable("safety.chainsawman.licence.hunted"), GREY});
		if (SafetyClient.hunted.isEmpty()) {
			out.add(new Object[]{Text.translatable("safety.chainsawman.licence.none_yet"), WHITE});
		} else {
			for (Map.Entry<String, Integer> e : SafetyClient.hunted.entrySet()) {
				Identifier id = Identifier.tryParse(e.getKey());
				EntityType<?> type = id == null ? null : Registries.ENTITY_TYPE.getOrEmpty(id).orElse(null);
				Text name = type == null ? Text.literal(e.getKey()) : type.getName();
				out.add(new Object[]{Text.translatable("safety.chainsawman.licence.count", name, e.getValue()), WHITE});
			}
		}
		out.add(null);
		out.add(new Object[]{Text.translatable("safety.chainsawman.licence.contracts"), GREY});
		if (SafetyClient.contracts.isEmpty()) {
			out.add(new Object[]{Text.translatable("safety.chainsawman.licence.none"), WHITE});
		} else {
			for (String id : SafetyClient.contracts) {
				out.add(new Object[]{Text.translatable("contract.chainsawman." + id), WHITE});
				for (Text price : prices(id)) {
					for (OrderedText line : tr.wrapLines(price, textW - 6)) {
						out.add(new Object[]{line, GREY});
					}
				}
			}
		}
		return out;
	}

	/** A contract's price, as the contract screen words it. */
	static List<Text> prices(String id) {
		if (id.equals("eternity")) {
			return List.of(Text.translatable("safety.chainsawman.licence.price.lasting", Text.translatable("contract.chainsawman.screen.lasting")),
					Text.translatable("safety.chainsawman.licence.price.use", Text.translatable("contract.chainsawman.screen.use")));
		}
		return List.of();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		MinecraftClient c = MinecraftClient.getInstance();
		if (c.player == null) {
			return;
		}
		TextRenderer tr = textRenderer;
		int pad = 10;
		int textW = W - pad * 2;
		List<Object[]> body = body(tr, textW);
		int h = 22 + 10 + 48 + 10 + body.size() * 11 + pad;
		int x = (width - W) / 2;
		float slide = Math.min(1.0F, (age + delta) / 5.0F);
		int y = (height - h) / 2 + (int) ((1.0F - slide * (2.0F - slide)) * 30.0F);
		// The card: black, a thin white rim.
		context.fill(x - 1, y - 1, x + W + 1, y + h + 1, 0xFFDADADA);
		context.fill(x, y, x + W, y + h, BLACK);
		// Header and the one red line.
		context.drawText(tr, Text.translatable("safety.chainsawman.licence.header"), x + pad, y + 8, WHITE, false);
		Text kind = Text.translatable("safety.chainsawman.licence.kind");
		context.drawText(tr, kind, x + W - pad - tr.getWidth(kind), y + 8, GREY, false);
		context.fill(x + pad, y + 21, x + W - pad, y + 23, RED);
		// The face, framed, and the name block beside it.
		int fy = y + 32;
		context.fill(x + pad - 1, fy - 1, x + pad + 49, fy + 49, WHITE);
		PlayerSkinDrawer.draw(context, c.player.getSkinTextures(), x + pad, fy, 48);
		int tx = x + pad + 58;
		context.getMatrices().push();
		context.getMatrices().translate(tx, fy, 0);
		context.getMatrices().scale(1.5F, 1.5F, 1.0F);
		context.drawText(tr, c.player.getName(), 0, 0, WHITE, false);
		context.getMatrices().pop();
		context.drawText(tr, Text.translatable("safety.chainsawman.licence.role"), tx, fy + 15, WHITE, false);
		context.drawText(tr, Text.translatable("safety.chainsawman.licence.rank"), tx, fy + 26, GREY, false);
		context.drawText(tr, Text.literal(SafetyClient.yen(SafetyClient.yen)), tx, fy + 38, WHITE, false);
		// Below: what they have hunted, and the contracts they hold.
		int by = fy + 48 + 10;
		for (Object[] line : body) {
			if (line != null) {
				int color = (Integer) line[1];
				int indent = color == GREY && !(line[0] instanceof Text t && isLabel(t)) ? 6 : 0;
				if (line[0] instanceof OrderedText ot) {
					context.drawText(tr, ot, x + pad + indent, by, color, false);
				} else {
					context.drawText(tr, (Text) line[0], x + pad + indent, by, color, false);
				}
			}
			by += 11;
		}
	}

	private static boolean isLabel(Text t) {
		return t.getContent() instanceof net.minecraft.text.TranslatableTextContent tc
				&& (tc.getKey().equals("safety.chainsawman.licence.hunted") || tc.getKey().equals("safety.chainsawman.licence.contracts"));
	}
}
