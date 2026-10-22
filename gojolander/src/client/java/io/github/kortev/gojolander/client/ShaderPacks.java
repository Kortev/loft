package io.github.kortev.gojolander.client;

import io.github.kortev.gojolander.Gojolander;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether an Iris shader pack is drawing the world now, asked of Iris's own API (if Iris is there at all), without
 * needing Iris to build: then the Domain is drawn after the pack has finished each frame.
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
			Gojolander.LOGGER.warn("Could not ask Iris whether a shader pack is in use; drawing as without one", e);
			broken = true;
			return false;
		}
	}
}
