package io.github.kortev.chainsawman.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.kortev.chainsawman.client.MergeCinematic;
import net.minecraft.client.render.Camera;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Your merge's camera goes far from your body (the memories are high over it): you hear it all from where you lie
 * all the same, his voice and the dream's sounds, which are played there.
 */
@Mixin(SoundSystem.class)
public abstract class DreamHearingMixin {
	@WrapOperation(method = "updateListenerPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;getPos()Lnet/minecraft/util/math/Vec3d;"))
	private Vec3d chainsawman$hearFromYourBody(Camera camera, Operation<Vec3d> original) {
		Vec3d at = original.call(camera);
		Vec3d body = MergeCinematic.hearing(at);
		return body != null ? body : at;
	}
}
