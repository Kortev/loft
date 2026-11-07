package io.github.kortev.chainsawman.client.gfx;

import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Uniform setters for the mod's GLSL programs (assets/chainsawman/shaders/core). */
public final class Shaders {
	private Shaders() {
	}

	public static void set(ShaderProgram program, String name, float value) {
		GlUniform uniform = program.getUniform(name);
		if (uniform != null) {
			uniform.set(value);
		}
	}

	public static void set(ShaderProgram program, String name, float x, float y) {
		GlUniform uniform = program.getUniform(name);
		if (uniform != null) {
			uniform.set(x, y);
		}
	}

	public static void set(ShaderProgram program, String name, float x, float y, float z) {
		GlUniform uniform = program.getUniform(name);
		if (uniform != null) {
			uniform.set(x, y, z);
		}
	}

	public static void set(ShaderProgram program, String name, float x, float y, float z, float w) {
		GlUniform uniform = program.getUniform(name);
		if (uniform != null) {
			uniform.set(x, y, z, w);
		}
	}

	public static void set(ShaderProgram program, String name, Vector3f v) {
		set(program, name, v.x, v.y, v.z);
	}

	public static void set(ShaderProgram program, String name, int color) {
		set(program, name, (color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F);
	}

	public static void setInt(ShaderProgram program, String name, int value) {
		GlUniform uniform = program.getUniform(name);
		if (uniform != null) {
			uniform.set(value);
		}
	}

	public static void set(ShaderProgram program, String name, Matrix4f matrix) {
		GlUniform uniform = program.getUniform(name);
		if (uniform != null) {
			uniform.set(matrix);
		}
	}
}
