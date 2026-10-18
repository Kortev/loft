package io.github.kortev.shootingstar.client.gfx;

import io.github.kortev.shootingstar.ShootingStar;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether an Iris shader pack is drawing the world now, asked of Iris's own API (if Iris is there at all), without
 * needing Iris to build: then the passes over the world are drawn after the pack has finished each frame
 * ({@link WorldPasses}).
 */
public final class ShaderPacks {
	private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");
	private static Object api;
	private static Method inUse;
	private static boolean broken;

	private ShaderPacks() {
	}

	public static boolean inUse() {
		if (!IRIS || broken) {
			return false;
		}
		try {
			if (api == null) {
				Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				api = type.getMethod("getInstance").invoke(null);
				inUse = type.getMethod("isShaderPackInUse");
			}
			return (Boolean) inUse.invoke(api);
		} catch (ReflectiveOperationException | RuntimeException e) {
			ShootingStar.LOGGER.warn("Could not ask Iris whether a shader pack is in use; drawing as without one", e);
			broken = true;
			return false;
		}
	}
}
