package io.github.kortev.shootingstar.client.gfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.FogShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * The passes drawn over the finished world: the strike's fire and its grading, the Gap's, Mjölnir's storm and light, a
 * Domain. Without a shader pack they are drawn at the end of the world's drawing, as they always were. An Iris shader
 * pack draws the world into pictures of its own and lays them over the screen only as the world's drawing returns,
 * over anything drawn at its end; so with one in use the frame is kept, and the passes are drawn once the pack is done
 * ({@code AfterWorldMixin}, before the depth is cleared for the hand), with the world's projection and fog put back as
 * they were at its end (the world clears its fog as it returns, and some passes fade with it).
 *
 * <p>Glowing things drawn into the world itself (the strike's beam and the reticle on the ground) go with them under a
 * pack: drawn in the world, the pack would take them for its own blocks, drawn and lit its way, and the glow goes dull
 * or is lost. Then they are drawn after the pack too, first, against the world's depth as the pack left it.
 */
public final class WorldPasses {
	private static final List<Consumer<WorldFrame>> PASSES = new ArrayList<>();
	private static final List<Consumer<WorldFrame>> IN_WORLD = new ArrayList<>();

	/** The frame held back for after the pack, and the state the passes found at the end of the world's drawing. */
	private record Deferred(WorldFrame frame, Matrix4f projection, VertexSorter sorting, Fog fog) {
	}

	private record Fog(float start, float end, float[] color, FogShape shape) {
		static Fog now() {
			return new Fog(RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), RenderSystem.getShaderFogColor().clone(),
					RenderSystem.getShaderFogShape());
		}

		void set() {
			RenderSystem.setShaderFogStart(start);
			RenderSystem.setShaderFogEnd(end);
			RenderSystem.setShaderFogColor(color[0], color[1], color[2], color[3]);
			RenderSystem.setShaderFogShape(shape);
		}
	}

	@Nullable
	private static Deferred deferred;

	private WorldPasses() {
	}

	/** Draws {@code pass} over the finished world, every frame, after those registered before it. */
	public static void register(Consumer<WorldFrame> pass) {
		PASSES.add(pass);
	}

	/**
	 * Draws {@code pass} into the world once its translucent things are drawn ({@link #afterTranslucent}); with a shader
	 * pack in use, after the pack instead, before the passes over the world.
	 */
	public static void registerInWorld(Consumer<WorldFrame> pass) {
		IN_WORLD.add(pass);
	}

	/** Once the world's translucent things are drawn ({@code WorldRenderEvents.AFTER_TRANSLUCENT}), without a shader pack. */
	public static void afterTranslucent(WorldRenderContext context) {
		if (!IN_WORLD.isEmpty() && !ShaderPacks.inUse()) {
			WorldFrame frame = WorldFrame.of(context);
			for (Consumer<WorldFrame> pass : IN_WORLD) {
				pass.accept(frame);
			}
		}
	}

	/**
	 * At the end of the world's drawing: draws the passes now, over the world as it stands; or, with a shader pack in
	 * use, keeps what they need and draws them once the pack is done ({@link #afterWorld}).
	 */
	public static void last(WorldRenderContext context) {
		WorldFrame frame = WorldFrame.of(context);
		if (ShaderPacks.inUse()) {
			deferred = new Deferred(frame, new Matrix4f(RenderSystem.getProjectionMatrix()), RenderSystem.getVertexSorting(), Fog.now());
			return;
		}
		deferred = null;
		draw(frame);
	}

	/** After the whole world (a shader pack's last pass and all) is on the screen: the passes held back for it. */
	public static void afterWorld() {
		Deferred held = deferred;
		deferred = null;
		if (held == null) {
			return;
		}
		Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
		VertexSorter sorting = RenderSystem.getVertexSorting();
		Fog fog = Fog.now();
		RenderSystem.setProjectionMatrix(held.projection(), held.sorting());
		held.fog().set();
		for (Consumer<WorldFrame> pass : IN_WORLD) {
			pass.accept(held.frame());
		}
		draw(held.frame());
		fog.set();
		RenderSystem.setProjectionMatrix(projection, sorting);
	}

	private static void draw(WorldFrame frame) {
		for (Consumer<WorldFrame> pass : PASSES) {
			pass.accept(frame);
		}
	}
}
