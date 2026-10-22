package io.github.kortev.gojolander.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.kortev.gojolander.GojoDamage;
import io.github.kortev.gojolander.domain.DomainHold;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Whatever a Domain holds does not move: no walking, falling or being knocked about, and a mob held does not think. On
 * a client, only the local player is held this way (everyone else it sees where the server says they are).
 *
 * <p>And the void's steps down ({@link GojoDamage}) make no cry of pain, theirs or anyone's: nobody is struck. The
 * server plays the hurt sound for everyone else, the client their own; the Domain's clients play its own sound.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
	private void gojolander$held(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (DomainHold.held(self)) {
			self.setVelocity(Vec3d.ZERO);
			ci.cancel();
		}
	}

	@Inject(method = "playHurtSound", at = @At("HEAD"), cancellable = true)
	private void gojolander$quietOnServer(DamageSource source, CallbackInfo ci) {
		if (source.isOf(GojoDamage.UNLIMITED_VOID)) {
			ci.cancel();
		}
	}

	@ModifyExpressionValue(method = "onDamaged", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/LivingEntity;getHurtSound(Lnet/minecraft/entity/damage/DamageSource;)Lnet/minecraft/sound/SoundEvent;"))
	private SoundEvent gojolander$quietOnClient(SoundEvent sound, DamageSource source) {
		return source.isOf(GojoDamage.UNLIMITED_VOID) ? null : sound;
	}
}
