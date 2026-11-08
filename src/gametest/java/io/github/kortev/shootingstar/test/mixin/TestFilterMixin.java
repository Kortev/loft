package io.github.kortev.shootingstar.test.mixin;

import java.util.Collection;
import java.util.stream.Collectors;
import net.minecraft.test.TestFunction;
import net.minecraft.test.TestFunctions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@code ./gradlew runGametest -Ponly=hotel}: runs only the game tests whose name has this in it (any case); nothing set, all of them. */
@Mixin(TestFunctions.class)
public class TestFilterMixin {
	@Inject(method = "getTestFunctions()Ljava/util/Collection;", at = @At("RETURN"), cancellable = true)
	private static void shootingstar$only(CallbackInfoReturnable<Collection<TestFunction>> cir) {
		String only = System.getProperty("shootingstar.gametest.only", "");
		if (!only.isEmpty()) {
			String want = only.toLowerCase(java.util.Locale.ROOT);
			cir.setReturnValue(cir.getReturnValue().stream().filter(f -> f.templatePath().toLowerCase(java.util.Locale.ROOT).contains(want)
					|| f.toString().toLowerCase(java.util.Locale.ROOT).contains(want)).collect(Collectors.toList()));
		}
	}
}
