package io.github.kortev.chainsawman.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.chainsawman.client.gfx.Post;
import io.github.kortev.chainsawman.client.gfx.Shaders;
import io.github.kortev.chainsawman.client.gfx.Target;
import io.github.kortev.chainsawman.contract.Forever;
import io.github.kortev.chainsawman.contract.ForeverHubEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Forever on your own screen: while a moment is held the world within about 24 blocks drains toward grey-sepia ({@code csm_drain}),
 * strongest for the one who called it and the one who is held, whose screen also darkens at the edges and has the devil's eyes staring
 * in from every side; the release is a white flash. (The flesh, copies and marks are in the world: {@link ForeverHubRenderer}.)
 */
public final class ForeverHud {
	public static ShaderProgram drain;
	private static final Identifier EYE = Identifier.of("chainsawman", "textures/gui/forever_eye.png");
	private static final Target COPY = new Target(false, false);
	private static final Target DEPTH = new Target(true, false);
	/** How drained the screen is now (eased toward what it should be), and a tick ago. */
	private static float level;
	private static float previous;
	/** Whether the player here is held (eased), for the dark edges and the eyes. */
	private static float held;
	private static float heldPrevious;

	private ForeverHud() {
	}

	public static void tick(MinecraftClient client) {
		previous = level;
		heldPrevious = held;
		if (client.world == null || client.player == null) {
			level = 0.0F;
			held = 0.0F;
			return;
		}
		float want = 0.0F;
		boolean locked = false;
		float now = client.world.getTime();
		for (ForeverHubEntity hub : ClientForever.hubs()) {
			float d = (float) hub.getPos().distanceTo(client.player.getPos());
			boolean mine = hub.targetId() == client.player.getId() || hub.ownerId() == client.player.getId();
			float s = mine ? 0.6F : d > 24.0F ? 0.0F : 0.4F - 0.28F * MathHelper.clamp((d - 6.0F) / 18.0F, 0.0F, 1.0F);
			if (hub.phase() == ForeverHubEntity.SINKING) {
				s *= 1.0F - MathHelper.clamp((now - hub.phaseAt()) / (Forever.SINK + 8.0F), 0.0F, 1.0F);
			}
			want = Math.max(want, s);
			if (hub.targetId() == client.player.getId() && hub.phase() != ForeverHubEntity.SINKING) {
				locked = true;
			}
		}
		level += (want - level) * (want > level ? 0.35F : 0.12F);
		if (level < 0.003F) {
			level = 0.0F;
		}
		held += ((locked ? 1.0F : 0.0F) - held) * (locked ? 0.4F : 0.3F);
		if (held < 0.003F) {
			held = 0.0F;
		}
	}

	/** The release's flash for the player here: the strongest of the nearby holds that just ended. */
	private static float pulse(MinecraftClient client, float tickDelta) {
		float now = client.world.getTime() + tickDelta;
		float best = 0.0F;
		for (ForeverHubEntity hub : ClientForever.hubs()) {
			if (hub.phase() != ForeverHubEntity.SINKING) {
				continue;
			}
			float since = now - hub.phaseAt();
			if (since < 0.0F || since > 16.0F) {
				continue;
			}
			float d = (float) hub.getPos().distanceTo(client.player.getPos());
			boolean mine = hub.targetId() == client.player.getId() || hub.ownerId() == client.player.getId();
			float near = mine ? 1.0F : MathHelper.clamp(1.0F - d / 22.0F, 0.0F, 1.0F);
			best = Math.max(best, near * 0.62F * (float) Math.exp(-since / 2.0F));
		}
		return best;
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			return;
		}
		float tickDelta = counter.getTickDelta(true);
		float strength = MathHelper.lerp(tickDelta, previous, level);
		float mine = MathHelper.lerp(tickDelta, heldPrevious, held);
		float pulse = pulse(client, tickDelta);
		// The cube nearest the camera: the world inside it is drained, and outside it is not.
		net.minecraft.util.math.Vec3d cam = ClientForever.camera;
		io.github.kortev.chainsawman.contract.ForeverCubeEntity cube = null;
		double best = Double.MAX_VALUE;
		for (io.github.kortev.chainsawman.contract.ForeverCubeEntity c : ClientForever.cubes()) {
			double d = c.getPos().squaredDistanceTo(cam);
			if (d < best && d < 120.0 * 120.0) {
				best = d;
				cube = c;
			}
		}
		float cubeOn = 0.0F;
		if (cube != null) {
			float now = client.world.getTime() + tickDelta;
			cubeOn = cube.phase() == io.github.kortev.chainsawman.contract.ForeverHubEntity.SINKING ? 1.0F - MathHelper.clamp((now - cube.phaseAt()) / 8.0F, 0.0F, 1.0F)
					: MathHelper.clamp((now - cube.startedAt()) / 4.0F, 0.0F, 1.0F);
		}
		if (drain != null && (strength > 0.01F || pulse > 0.01F || cubeOn > 0.0F)) {
			ctx.draw();
			Framebuffer main = client.getFramebuffer();
			int w = main.textureWidth;
			int h = main.textureHeight;
			COPY.ensure(w, h);
			COPY.copyColorFrom(main);
			DEPTH.ensure(w, h);
			DEPTH.copyDepthFrom(main);
			main.beginWrite(true);
			Post.begin();
			RenderSystem.setShaderTexture(0, COPY.color());
			RenderSystem.setShaderTexture(1, DEPTH.depth());
			Shaders.set(drain, "ScreenSize", (float) w, (float) h);
			Shaders.set(drain, "Strength", strength);
			Shaders.set(drain, "Mine", mine);
			Shaders.set(drain, "Pulse", pulse);
			Shaders.set(drain, "Time", (float) (client.world.getTime() % 100000L) + tickDelta);
			Shaders.set(drain, "InvProj", new org.joml.Matrix4f(ClientForever.projection).invert());
			Shaders.set(drain, "InvView", new org.joml.Matrix4f(ClientForever.view).invert());
			Shaders.set(drain, "CubeOn", cubeOn);
			if (cube != null) {
				Shaders.set(drain, "CubeCentre", (float) (cube.getX() - cam.x), (float) (cube.getY() - cam.y), (float) (cube.getZ() - cam.z));
				double half = io.github.kortev.chainsawman.contract.Forever.CUBE_HALF;
				boolean inside = Math.abs(cam.x - cube.getX()) <= half && Math.abs(cam.y - cube.getY()) <= half && Math.abs(cam.z - cube.getZ()) <= half;
				Shaders.set(drain, "CubeHalf", (float) half);
				Shaders.set(drain, "CamInside", inside ? 1.0F : 0.0F);
			}
			Post.quad(drain);
			RenderSystem.setShaderTexture(1, 0);
			RenderSystem.setShaderTexture(0, 0);
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.enableDepthTest();
			RenderSystem.depthMask(true);
			RenderSystem.enableCull();
		}
		if (mine > 0.02F) {
			eyes(ctx, client, tickDelta, mine);
		}
	}

	/** The devil's eyes staring in from the edges of a held player's screen: they open one after another and shut at the release. */
	private static void eyes(DrawContext ctx, MinecraftClient client, float tickDelta, float held) {
		ForeverHubEntity mine = null;
		for (ForeverHubEntity hub : ClientForever.hubs()) {
			if (hub.targetId() == client.player.getId()) {
				mine = hub;
			}
		}
		if (mine == null) {
			return;
		}
		float now = client.world.getTime() + tickDelta;
		float t = now - mine.startedAt();
		float since = now - mine.phaseAt();
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		int count = 11;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		for (int i = 0; i < count; i++) {
			// Spread round the edge: along the top and bottom, and down both sides.
			float k = (i + 0.5F) / count;
			double perimeter = k * 2.0 * (w + h);
			double x;
			double y;
			float turn;
			if (perimeter < w) {
				x = perimeter;
				y = 0;
				turn = 0.0F;
			} else if (perimeter < w + h) {
				x = w;
				y = perimeter - w;
				turn = 90.0F;
			} else if (perimeter < 2 * w + h) {
				x = w - (perimeter - w - h);
				y = h;
				turn = 180.0F;
			} else {
				x = 0;
				y = h - (perimeter - 2 * w - h);
				turn = 270.0F;
			}
			float open;
			if (mine.phase() == ForeverHubEntity.HELD) {
				open = MathHelper.clamp((t - 4.0F - 1.1F * i) / 4.0F, 0.0F, 1.0F);
			} else if (mine.phase() == ForeverHubEntity.ENDING) {
				open = 1.0F - MathHelper.clamp(since / 1.2F, 0.0F, 1.0F);
			} else {
				open = 0.0F;
			}
			open *= held;
			if (open <= 0.02F) {
				continue;
			}
			int size = (int) (Math.min(w, h) * (0.3F + 0.06F * ((i * 7) % 3)));
			int ew = size;
			int eh = size * 28 / 48;
			ctx.getMatrices().push();
			ctx.getMatrices().translate(x, y, 0.0);
			ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(turn));
			ctx.getMatrices().scale(1.0F, open, 1.0F);
			// Peeking in: the eye sits half over the edge.
			ctx.drawTexture(EYE, -ew / 2, -(int) (eh * 0.15F), ew, eh, 0.0F, 0.0F, 48, 28, 48, 28);
			ctx.getMatrices().pop();
		}
	}
}
