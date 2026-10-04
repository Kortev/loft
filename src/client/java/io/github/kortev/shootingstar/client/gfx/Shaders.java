package io.github.kortev.shootingstar.client.gfx;

import io.github.kortev.shootingstar.ShootingStar;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The mod's GLSL programs (assets/shootingstar/shaders/core), reloaded with the vanilla shaders. */
public final class Shaders {
	public static ShaderProgram mesh;
	public static ShaderProgram planet;
	public static ShaderProgram gas;
	public static ShaderProgram atmo;
	public static ShaderProgram sky;
	public static ShaderProgram stars;
	public static ShaderProgram glow;
	public static ShaderProgram plasma;
	public static ShaderProgram bright;
	public static ShaderProgram blur;
	public static ShaderProgram composite;
	public static ShaderProgram blit;
	public static ShaderProgram impact;
	public static ShaderProgram debris;
	public static ShaderProgram smoke;
	public static ShaderProgram fxcomp;
	public static ShaderProgram shell;
	public static ShaderProgram light;

	private Shaders() {
	}

	public static void register() {
		CoreShaderRegistrationCallback.EVENT.register(context -> {
			context.register(ShootingStar.id("ss_mesh"), VertexFormats.POSITION_TEXTURE_COLOR_NORMAL, p -> mesh = p);
			context.register(ShootingStar.id("ss_planet"), VertexFormats.POSITION_TEXTURE_COLOR_NORMAL, p -> planet = p);
			context.register(ShootingStar.id("ss_gas"), VertexFormats.POSITION_TEXTURE_COLOR_NORMAL, p -> gas = p);
			context.register(ShootingStar.id("ss_atmo"), VertexFormats.POSITION_TEXTURE_COLOR_NORMAL, p -> atmo = p);
			context.register(ShootingStar.id("ss_sky"), VertexFormats.BLIT_SCREEN, p -> sky = p);
			context.register(ShootingStar.id("ss_stars"), VertexFormats.POSITION_TEXTURE_COLOR, p -> stars = p);
			context.register(ShootingStar.id("ss_glow"), VertexFormats.POSITION_TEXTURE_COLOR, p -> glow = p);
			context.register(ShootingStar.id("ss_plasma"), VertexFormats.POSITION_TEXTURE_COLOR_NORMAL, p -> plasma = p);
			context.register(ShootingStar.id("ss_bright"), VertexFormats.BLIT_SCREEN, p -> bright = p);
			context.register(ShootingStar.id("ss_blur"), VertexFormats.BLIT_SCREEN, p -> blur = p);
			context.register(ShootingStar.id("ss_composite"), VertexFormats.BLIT_SCREEN, p -> composite = p);
			context.register(ShootingStar.id("ss_blit"), VertexFormats.BLIT_SCREEN, p -> blit = p);
			context.register(ShootingStar.id("ss_impact"), VertexFormats.BLIT_SCREEN, p -> impact = p);
			context.register(ShootingStar.id("ss_debris"), VertexFormats.POSITION_TEXTURE_COLOR_NORMAL, p -> debris = p);
			context.register(ShootingStar.id("ss_smoke"), VertexFormats.POSITION_TEXTURE_COLOR_NORMAL, p -> smoke = p);
			context.register(ShootingStar.id("ss_fxcomp"), VertexFormats.BLIT_SCREEN, p -> fxcomp = p);
			context.register(ShootingStar.id("ss_shell"), VertexFormats.POSITION_TEXTURE_COLOR_NORMAL, p -> shell = p);
			context.register(ShootingStar.id("ss_light"), VertexFormats.BLIT_SCREEN, p -> light = p);
		});
	}

	/** True once every program has loaded; effects fall back or skip until then. */
	public static boolean ready() {
		return mesh != null && planet != null && gas != null && atmo != null && sky != null && stars != null && glow != null
				&& plasma != null && bright != null && blur != null && composite != null && blit != null && impact != null
				&& debris != null && smoke != null && fxcomp != null && shell != null && light != null;
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
