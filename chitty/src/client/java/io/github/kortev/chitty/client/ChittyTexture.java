package io.github.kortev.chitty.client;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/**
 * A vehicle's baked texture, with mipmaps, so that it blends as she moves off into the distance rather than sparkles.
 * Chitty's and the airship's are pixelated close to, as Minecraft draws its blocks and mobs (both are faceted); a
 * smoothly filtered one suits a detailed picture wrapped round a smooth model.
 */
public class ChittyTexture extends ResourceTexture {
	private static final int LEVELS = 4;
	private final boolean pixelated;

	public ChittyTexture(Identifier location) {
		this(location, false);
	}

	public ChittyTexture(Identifier location, boolean pixelated) {
		super(location);
		this.pixelated = pixelated;
	}

	@Override
	public void load(ResourceManager manager) throws IOException {
		NativeImage[] mips = new NativeImage[LEVELS + 1];
		try (InputStream in = manager.open(this.location)) {
			mips[0] = NativeImage.read(in);
		}
		for (int level = 1; level <= LEVELS; level++) {
			mips[level] = half(mips[level - 1]);
		}
		if (RenderSystem.isOnRenderThreadOrInit()) {
			upload(mips);
		} else {
			RenderSystem.recordRenderCall(() -> upload(mips));
		}
	}

	private void upload(NativeImage[] mips) {
		TextureUtil.prepareImage(getGlId(), LEVELS, mips[0].getWidth(), mips[0].getHeight());
		for (int level = 0; level <= LEVELS; level++) {
			NativeImage image = mips[level];
			image.upload(level, 0, 0, 0, 0, image.getWidth(), image.getHeight(), !pixelated, false, true, true);
		}
	}

	/** Half the size, each pixel the average of the four under it. */
	private static NativeImage half(NativeImage src) {
		int w = Math.max(1, src.getWidth() / 2);
		int h = Math.max(1, src.getHeight() / 2);
		NativeImage out = new NativeImage(w, h, false);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int a = src.getColor(x * 2, y * 2);
				int b = src.getColor(x * 2 + 1, y * 2);
				int c = src.getColor(x * 2, y * 2 + 1);
				int d = src.getColor(x * 2 + 1, y * 2 + 1);
				int mixed = 0;
				for (int shift = 0; shift < 32; shift += 8) {
					int sum = (a >>> shift & 255) + (b >>> shift & 255) + (c >>> shift & 255) + (d >>> shift & 255);
					mixed |= (sum + 2) / 4 << shift;
				}
				out.setColor(x, y, mixed);
			}
		}
		return out;
	}
}
