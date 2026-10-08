package io.github.kortev.shootingstar.client.thunder;

import io.github.kortev.shootingstar.client.feed.Feed;
import io.github.kortev.shootingstar.client.gfx.Timings;
import io.github.kortev.shootingstar.client.render.Gfx;
import io.github.kortev.shootingstar.client.render.WorldProjector;
import io.github.kortev.shootingstar.item.MjolnirItem;
import io.github.kortev.shootingstar.registry.ModItems;
import io.github.kortev.shootingstar.thunder.ThunderTimeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * What Mjölnir puts over the HUD: the shooter's storm feed while it runs; for everyone, a marker over a gathering storm's
 * target with the time left; for the shooter, a readout of what the stroke did; and, with the hammer in hand, where it is
 * pointing and whether it will strike there.
 */
public final class ThunderHud {
	private static final int BLUE = 0xFF7FD8FF;
	private static final int PALE = 0xFFCFEAFF;
	private static final int WHITE = 0xFFEDEDED;
	private static final int GREY = 0xFFA8A8A8;
	private static final int RED = 0xFFFF4A32;

	private record Words(float x, float y, String text, int color, float scale, boolean centered) {
	}

	/** Where the hammer pointed on the last tick it was aimed: one ray a tick, not one a frame. */
	@Nullable
	private static BlockPos aimed;
	private static long aimedAt = Long.MIN_VALUE;

	private ThunderHud() {
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) {
			return;
		}
		float tickDelta = counter.getTickDelta(false);
		ClientThunder cinematic = ClientThunders.cinematic();
		if (cinematic != null && ClientThunders.feedActive(cinematic, cinematic.time(tickDelta))) {
			Timings.begin("thunder.feed");
			Feed.renderThunder(ctx, cinematic.time(tickDelta));
			Timings.end();
			return;
		}
		boolean shot = cinematic != null && ClientThunders.shotActive(cinematic, cinematic.time(tickDelta));
		boolean holding = holding(client.player);
		if (ClientThunders.all().isEmpty() && !holding) {
			return;
		}
		float w = ctx.getScaledWindowWidth();
		float h = ctx.getScaledWindowHeight();
		Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
		TextRenderer font = client.textRenderer;
		List<Words> words = new ArrayList<>();

		ctx.draw();
		Gfx.begin2d();
		for (ClientThunder thunder : ClientThunders.all()) {
			double t = thunder.time(tickDelta);
			if (t < ThunderTimeline.STROKE) {
				// Not over the shooter's own camera shots: the storm is right there in them.
				if (!(thunder == cinematic && shot)) {
					lockMarker(m, w, h, thunder, t, words, client);
				}
			} else if (thunder.mine && t >= ThunderTimeline.FRAMES_END + 4) {
				readout(words, w, h, thunder, t);
			}
		}
		if (holding && !shot && !client.options.hudHidden) {
			aimInfo(words, w, h, client.player);
		}
		Gfx.end2d();

		for (Words word : words) {
			ctx.getMatrices().push();
			ctx.getMatrices().translate(word.x(), word.y(), 0);
			ctx.getMatrices().scale(word.scale(), word.scale(), 1.0F);
			int x = word.centered() ? -font.getWidth(word.text()) / 2 : 0;
			ctx.drawText(font, word.text(), x, 0, word.color(), true);
			ctx.getMatrices().pop();
		}
		if (holding && !shot && !client.options.hudHidden) {
			statusCard(ctx, font, h);
		}
		ctx.draw();
	}

	private static boolean holding(ClientPlayerEntity player) {
		return player.getMainHandStack().isOf(ModItems.MJOLNIR) || player.getOffHandStack().isOf(ModItems.MJOLNIR);
	}

	/** Over the target of a gathering storm, for anyone near enough: who it is coming for, and how long they have. */
	private static void lockMarker(Matrix4f m, float w, float h, ClientThunder thunder, double t, List<Words> words, MinecraftClient client) {
		Vec3d c = thunder.center;
		double distance = client.player.getPos().distanceTo(c);
		if (distance > 720 || t < ThunderTimeline.CALL) {
			return;
		}
		Vector3f p = WorldProjector.project(c.x, c.y + 0.5, c.z, w, h);
		if (p == null || p.x < -40 || p.y < -40 || p.x > w + 40 || p.y > h + 40) {
			return;
		}
		double left = (ThunderTimeline.STROKE - t) / 20.0;
		// Faster and faster as the stroke nears.
		double rate = left > 6 ? 0.25 : left > 2 ? 0.5 : 1.0;
		float blink = ((int) (t * rate)) % 2 == 0 ? 1.0F : 0.45F;
		BufferBuilder b = Gfx.quads();
		Gfx.brackets(b, m, p.x, p.y, 6, 3, 1, Gfx.fade(BLUE, blink));
		Gfx.diamond(b, m, p.x, p.y, 1.5F, Gfx.fade(BLUE, blink));
		Gfx.draw(b);
		boolean inside = Math.hypot(client.player.getX() - c.x, client.player.getZ() - c.z) <= thunder.radius;
		words.add(new Words(p.x + 9, p.y - 5, inside ? "MJÖLNIR · YOU ARE UNDER IT" : "MJÖLNIR · STORM GATHERING",
				Gfx.fade(inside ? RED : BLUE, blink), 0.75F, false));
		words.add(new Words(p.x + 9, p.y + 3, String.format(Locale.ROOT, "%d M · T-%.1fs", (int) distance, left), GREY, 0.6F, false));
	}

	/** What the stroke did, for the shooter, once the impact frames are over. */
	private static void readout(List<Words> words, float w, float h, ClientThunder thunder, double t) {
		float alpha = (float) MathHelper.clamp((ThunderTimeline.END - t) / 30.0, 0.0, 1.0);
		alpha *= (float) MathHelper.clamp((t - ThunderTimeline.FRAMES_END - 4) / 4.0, 0.0, 1.0);
		if (alpha <= 0.02F) {
			return;
		}
		int zone = thunder.radius * 2;
		int scar = ThunderTimeline.scarRadius(thunder.radius) * 2;
		String detail;
		if (!thunder.terrain) {
			detail = String.format(Locale.ROOT, "ZONE %04d · TERRAIN HELD", zone);
		} else if (thunder.boltHeight > 0) {
			detail = String.format(Locale.ROOT, "ZONE %04d · SCAR %04d M · BOLT STANDING · %d M", zone, scar, thunder.boltHeight);
		} else {
			detail = String.format(Locale.ROOT, "ZONE %04d · SCAR %04d M", zone, scar);
		}
		words.add(new Words(w / 2, h - 58, "[ STROKE CONFIRMED ]", Gfx.fade(BLUE, alpha), 1.0F, true));
		words.add(new Words(w / 2, h - 46, detail, Gfx.fade(WHITE, alpha), 0.75F, true));
		if (!thunder.arcs.isEmpty()) {
			words.add(new Words(w / 2, h - 36, String.format(Locale.ROOT, "%d ARCS", thunder.arcs.size()), Gfx.fade(PALE, alpha), 0.6F,
					true));
		}
	}

	/** Where the hammer is pointing, and whether it will strike there. */
	private static void aimInfo(List<Words> words, float w, float h, ClientPlayerEntity player) {
		ClientThunder mine = ClientThunders.mine();
		float cooldown = player.getItemCooldownManager().getCooldownProgress(ModItems.MJOLNIR, 0.0F);
		String text;
		int color;
		if (mine != null && mine.age < ThunderTimeline.STROKE) {
			text = String.format(Locale.ROOT, "STORM GATHERING · T-%.1fs", (ThunderTimeline.STROKE - mine.age) / 20.0);
			color = BLUE;
		} else if (cooldown > 0.0F) {
			text = "RECHARGING";
			color = GREY;
		} else {
			long now = player.getWorld().getTime();
			if (now != aimedAt) {
				aimedAt = now;
				aimed = MjolnirItem.aim(player);
			}
			BlockPos target = aimed;
			if (target == null) {
				text = "NO TARGET";
				color = GREY;
			} else {
				double distance = Math.sqrt(Vec3d.ofCenter(target).squaredDistanceTo(player.getEyePos()));
				if (distance < ThunderTimeline.minRange(ThunderTimeline.DEFAULT_RADIUS)) {
					text = "TOO CLOSE";
					color = RED;
				} else {
					text = String.format(Locale.ROOT, "RANGE %d M", (int) distance);
					color = BLUE;
				}
			}
		}
		words.add(new Words(w / 2 + 8, h / 2 + 6, text, color, 0.6F, false));
	}

	/** A small card in the corner while the hammer is in hand. */
	private static void statusCard(DrawContext ctx, TextRenderer font, float h) {
		int x = 6;
		int y = (int) h - 26;
		ctx.fill(x - 2, y - 2, x + 76, y + 16, 0xCC060A14);
		ctx.drawBorder(x - 2, y - 2, 78, 18, 0xFF4A9CFF);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x + 2, y + 1, 0);
		ctx.getMatrices().scale(0.75F, 0.75F, 1.0F);
		ctx.drawText(font, "Þ-01 · MJÖLNIR", 0, 0, BLUE, true);
		ctx.drawText(font, "GLOBAL CIRCUIT", 0, 10, GREY, true);
		ctx.getMatrices().pop();
	}
}
