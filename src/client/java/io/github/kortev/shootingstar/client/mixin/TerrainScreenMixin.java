package io.github.kortev.shootingstar.client.mixin;

import io.github.kortev.shootingstar.client.gap.ClientGaps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Into the void and back out of it without the loading screen: the picture is black either way (all of it gone, or
 * not yet rebuilt), so the change of world goes unseen.
 */
@Mixin(MinecraftClient.class)
public abstract class TerrainScreenMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void shootingstar$noLoadingScreen(Screen screen, CallbackInfo ci) {
		if (screen instanceof DownloadingTerrainScreen && ClientGaps.changingWorlds()) {
			ci.cancel();
		}
	}
}
