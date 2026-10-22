package io.github.kortev.gojolander.client.mixin;

import io.github.kortev.gojolander.client.ClientDomains;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * On their knees, their eyes are down in the middle of their own body, where its effects' swirls are made: from just
 * before the void lets them go until they are up again, none is made in the column they stand in (seen from there,
 * each would fill the screen).
 */
@Mixin(ParticleManager.class)
public abstract class KneelParticlesMixin {
	/** How far out from them (blocks, across) the column reaches. */
	private static final double CLEAR = 0.9;

	@Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"),
			cancellable = true)
	private void gojolander$clearOfTheEyes(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY,
			double velocityZ, CallbackInfoReturnable<Particle> cir) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player != null && !client.gameRenderer.getCamera().isThirdPerson() && ClientDomains.breakingMe()
				&& Math.abs(x - player.getX()) < CLEAR && Math.abs(z - player.getZ()) < CLEAR && y > player.getY() - 0.3
				&& y < player.getY() + player.getHeight() + 0.4) {
			cir.setReturnValue(null);
		}
	}
}
