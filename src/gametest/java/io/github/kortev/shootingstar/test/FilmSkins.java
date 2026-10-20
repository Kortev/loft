package io.github.kortev.shootingstar.test;

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
 * The players' own skins in the films ({@link FilmSelfTest}, {@link FilmDomain}) and the form's photographs
 * ({@link FormSelfTest}): the test runs offline, where everyone would get a default skin, so the workflow fetches each
 * named player's current skin into the run folder's skins/ (NAME.png, and NAME.model with "slim" or "default"), and
 * these are drawn instead. Anyone without one keeps the default skin.
 */
public final class FilmSkins {
	private static final Map<String, SkinTextures> LOADED = new HashMap<>();
	private static final Set<String> MISSING = new HashSet<>();

	private FilmSkins() {
	}

	@Nullable
	public static SkinTextures get(String name) {
		if (!FilmSelfTest.active() && !FilmDomain.active() && !FilmDomainKortev.active() && !FormSelfTest.active() && !ReelSelfTest.active()) {
			return null;
		}
		SkinTextures known = LOADED.get(name);
		if (known != null || MISSING.contains(name)) {
			return known;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		Path folder = client.runDirectory.toPath().resolve("skins");
		Path png = folder.resolve(name + ".png");
		if (!Files.exists(png)) {
			MISSING.add(name);
			return null;
		}
		try (InputStream in = Files.newInputStream(png)) {
			NativeImage image = NativeImage.read(in);
			if (image.getWidth() != 64 || image.getHeight() != 64) {
				ShootingStar.LOGGER.warn("[film] {}'s skin is {}x{}, not 64x64; left as the default", name, image.getWidth(), image.getHeight());
				image.close();
				MISSING.add(name);
				return null;
			}
			Identifier id = Identifier.of("shootingstar-gametest", "film_skin/" + name.toLowerCase(Locale.ROOT));
			client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
			Path model = folder.resolve(name + ".model");
			String kind = Files.exists(model) ? Files.readString(model).trim() : "default";
			SkinTextures skin = new SkinTextures(id, null, null, null, SkinTextures.Model.fromName(kind), true);
			LOADED.put(name, skin);
			ShootingStar.LOGGER.info("[film] {}'s skin ({})", name, kind);
			return skin;
		} catch (IOException e) {
			ShootingStar.LOGGER.warn("[film] could not read {}'s skin", name, e);
			MISSING.add(name);
			return null;
		}
	}
}
