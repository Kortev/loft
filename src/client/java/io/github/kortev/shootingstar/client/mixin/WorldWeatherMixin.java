package io.github.kortev.shootingstar.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.thunder.ThunderWeather;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lends the client's world rain and thunder under Mjölnir's storm (see {@link ThunderWeather}); the server's worlds,
 * which share the class in single player, keep their own weather.
 */
@Mixin(World.class)
public abstract class WorldWeatherMixin {
	@Inject(method = "getRainGradient", at = @At("RETURN"), cancellable = true)
	private void shootingstar$stormRain(float delta, CallbackInfoReturnable<Float> cir) {
		shootingstar$storm(delta, cir);
	}

	@Inject(method = "getThunderGradient", at = @At("RETURN"), cancellable = true)
	private void shootingstar$stormThunder(float delta, CallbackInfoReturnable<Float> cir) {
		shootingstar$storm(delta, cir);
	}

	private void shootingstar$storm(float delta, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientWorld && RenderSystem.isOnRenderThread()) {
			float storm = ThunderWeather.storm(delta);
			if (storm > cir.getReturnValueF()) {
				cir.setReturnValue(storm);
			}
		}
	}
}
