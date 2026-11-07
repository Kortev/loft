package io.github.kortev.chainsawman.client.gfx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/** Full-screen pass helpers: the state and the quad the screen effects are drawn with. */
public final class Post {
	private static final Matrix4f IDENTITY = new Matrix4f();

	private Post() {
	}

	/** State for screen passes: no depth, no blending, no culling. */
	public static void begin() {
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableBlend();
		RenderSystem.disableCull();
	}

	/** Draws a quad covering the bound target with {@code program} (positions are in clip space). */
	public static void quad(ShaderProgram program) {
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.BLIT_SCREEN);
		b.vertex(-1.0F, -1.0F, 0.0F);
		b.vertex(1.0F, -1.0F, 0.0F);
		b.vertex(1.0F, 1.0F, 0.0F);
		b.vertex(-1.0F, 1.0F, 0.0F);
		draw(b, program, IDENTITY, IDENTITY);
	}

	/** Uploads and draws an immediate buffer with explicit matrices, bypassing the global ones. */
	public static void draw(BufferBuilder builder, ShaderProgram program, Matrix4f modelView, Matrix4f projection) {
		BuiltBuffer built = builder.endNullable();
		if (built == null) {
			return;
		}
		VertexBuffer vb = built.getDrawParameters().format().getBuffer();
		vb.bind();
		vb.upload(built);
		vb.draw(modelView, projection, program);
		VertexBuffer.unbind();
	}
}
