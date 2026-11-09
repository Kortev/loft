package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.shootingstar.test.TestIsolation;
import java.util.Collection;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTestState;
import net.minecraft.test.TestRunContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Each batch of game tests starts in a clean server ({@link TestIsolation}), with the chunks round its test boxes loaded. */
@Mixin(TestRunContext.class)
public abstract class TestIsolationMixin {
	@Shadow
	@org.spongepowered.asm.mixin.Final
	ServerWorld world;

	@Inject(method = "runBatch(I)V", at = @At("HEAD"))
	private void shootingstar$clean(int index, CallbackInfo ci) {
		TestIsolation.reset(this.world);
	}

	@Inject(method = "prepareStructures", at = @At("RETURN"))
	private void shootingstar$load(Collection<GameTestState> states, CallbackInfoReturnable<Collection<GameTestState>> cir) {
		TestIsolation.load(cir.getReturnValue());
	}
}
