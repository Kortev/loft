package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ClientForever;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inside a cube of frozen time (Forever) dust and particles hang still: they are not ticked while they are in it. */
@Mixin(ParticleManager.class)
public abstract class ForeverParticleMixin {
	@Inject(method = "tickParticle", at = @At("HEAD"), cancellable = true)
	private void chainsawman$hang(Particle particle, CallbackInfo ci) {
		if (!ClientForever.cubes().isEmpty()) {
			Vec3d at = particle.getBoundingBox().getCenter();
			if (ClientForever.frozenAt(at.x, at.y, at.z)) {
				ci.cancel();
			}
		}
	}
}
