package io.github.kortev.shootingstar.client;

import io.github.kortev.shootingstar.ShootingStar;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Client options, kept in {@code config/shootingstar.properties}. */
public final class ClientConfig {
	/** Show the uplink feed (the space cinematic) when you fire. */
	public static boolean feed = true;
	/** Let the feed move your camera for the rise, sky and impact shots. */
	public static boolean cameraShots = true;
	/** Multiplier for impact screen shake; 0 turns it off. */
	public static float screenShake = 1.0F;

	private ClientConfig() {
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(ShootingStar.MOD_ID + ".properties");
		Properties props = new Properties();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				props.load(reader);
				feed = Boolean.parseBoolean(props.getProperty("feed", "true"));
				cameraShots = Boolean.parseBoolean(props.getProperty("cameraShots", "true"));
				screenShake = Math.max(0.0F, Float.parseFloat(props.getProperty("screenShake", "1.0")));
			} catch (IOException | NumberFormatException e) {
				ShootingStar.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}
		props.setProperty("feed", Boolean.toString(feed));
		props.setProperty("cameraShots", Boolean.toString(cameraShots));
		props.setProperty("screenShake", Float.toString(screenShake));
		try (Writer writer = Files.newBufferedWriter(path)) {
			props.store(writer, "The Shooting Star client options");
		} catch (IOException e) {
			ShootingStar.LOGGER.warn("Could not write {}", path, e);
		}
	}
}
