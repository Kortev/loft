package io.github.kortev.shootingstar.client.gfx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.ShootingStar;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Large photographic textures (the NASA planet and sky maps) shipped as JPEG and decoded with stb_image,
 * uploaded with mipmaps. Loaded on first use.
 */
public final class Tex {
	private static final Map<String, Integer> LOADED = new HashMap<>();

	private Tex() {
	}

	/** GL texture id of assets/shootingstar/textures/{path}; wraps horizontally when {@code repeat}. */
	public static int get(String path, boolean repeat) {
		Integer id = LOADED.get(path);
		if (id == null) {
			id = load(path, repeat);
			LOADED.put(path, id);
		}
		return id;
	}

	private static int load(String path, boolean repeat) {
		RenderSystem.assertOnRenderThread();
		byte[] bytes;
		try {
			Resource resource = MinecraftClient.getInstance().getResourceManager()
					.getResource(ShootingStar.id("textures/" + path)).orElseThrow(() -> new IOException("missing texture " + path));
			try (InputStream in = resource.getInputStream()) {
				bytes = in.readAllBytes();
			}
		} catch (IOException e) {
			ShootingStar.LOGGER.error("Could not read texture {}", path, e);
			return blank();
		}
		ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			data.put(bytes).flip();
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			IntBuffer channels = stack.mallocInt(1);
			ByteBuffer pixels = STBImage.stbi_load_from_memory(data, w, h, channels, 4);
			if (pixels == null) {
				ShootingStar.LOGGER.error("Could not decode texture {}: {}", path, STBImage.stbi_failure_reason());
				return blank();
			}
			int id = TextureUtil.generateTextureId();
			GlStateManager._bindTexture(id);
			resetUnpack();
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w.get(0), h.get(0), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
			GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, repeat ? GL11.GL_REPEAT : GL12.GL_CLAMP_TO_EDGE);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GlStateManager._bindTexture(0);
			STBImage.stbi_image_free(pixels);
			ShootingStar.LOGGER.info("Loaded {} ({}x{})", path, w.get(0), h.get(0));
			return id;
		} finally {
			MemoryUtil.memFree(data);
		}
	}

	private static void resetUnpack() {
		// Vanilla image uploads leave row length and skips set; plain RGBA rows need them cleared.
		RenderSystem.pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
		RenderSystem.pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
		RenderSystem.pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
		RenderSystem.pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
	}

	private static int blank() {
		int id = TextureUtil.generateTextureId();
		GlStateManager._bindTexture(id);
		resetUnpack();
		ByteBuffer pixel = MemoryUtil.memAlloc(4);
		pixel.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) -1).flip();
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
		MemoryUtil.memFree(pixel);
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GlStateManager._bindTexture(0);
		return id;
	}
}
