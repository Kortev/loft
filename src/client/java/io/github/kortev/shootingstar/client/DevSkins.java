package io.github.kortev.shootingstar.client;

import io.github.kortev.shootingstar.ShootingStar;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Players' own skins in the dev game (./gradlew runClient), which plays offline and so can't fetch them: with
 * -Dshootingstar.devskins (set by the dev game's run), a player named NAME wears run/skins/NAME.png if it is there (and
 * NAME.model, "slim" or "default"). Nothing happens without that flag, so on a server or in a real install this is inert.
 */
public final class DevSkins {
	private static final Map<String, SkinTextures> LOADED = new HashMap<>();
	private static final Set<String> MISSING = new HashSet<>();

	private DevSkins() {
	}

	@Nullable
	public static SkinTextures get(String name) {
		if (System.getProperty("shootingstar.devskins") == null) {
			return null;
		}
		String key = name.toLowerCase(Locale.ROOT);
		SkinTextures known = LOADED.get(key);
		if (known != null || MISSING.contains(key)) {
			return known;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		Path folder = client.runDirectory.toPath().resolve("skins");
		Path png = folder.resolve(key + ".png");
		if (!Files.exists(png)) {
			MISSING.add(key);
			return null;
		}
		try (InputStream in = Files.newInputStream(png)) {
			NativeImage image = NativeImage.read(in);
			if (image.getWidth() != 64 || image.getHeight() != 64) {
				ShootingStar.LOGGER.warn("[dev] {}'s skin is {}x{}, not 64x64; left as the default", name, image.getWidth(), image.getHeight());
				image.close();
				MISSING.add(key);
				return null;
			}
			Identifier id = ShootingStar.id("dev_skin/" + key);
			client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
			Path model = folder.resolve(key + ".model");
			String kind = Files.exists(model) ? Files.readString(model).trim() : "default";
			SkinTextures skin = new SkinTextures(id, null, null, null, SkinTextures.Model.fromName(kind), true);
			LOADED.put(key, skin);
			ShootingStar.LOGGER.info("[dev] {} wears run/skins/{}.png ({})", name, key, kind);
			return skin;
		} catch (IOException e) {
			ShootingStar.LOGGER.warn("[dev] could not read {}'s skin", name, e);
			MISSING.add(key);
			return null;
		}
	}
}
