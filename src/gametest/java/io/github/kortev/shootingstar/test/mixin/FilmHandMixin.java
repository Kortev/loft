package io.github.kortev.shootingstar.test.mixin;

import io.github.kortev.shootingstar.test.FilmDomainKortev;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A film is a tall picture (1080x1920), and the game holds what is in hand out to the side, past the edge of it: in
 * Kortev's film ({@link FilmDomainKortev}), where his sword is half of the fight, his hands are drawn in from the
 * sides, as far as a tall picture needs, so they are seen.
 */
@Mixin(HeldItemRenderer.class)
public abstract class FilmHandMixin {
	/** How far in (of the game's 0.56 out to the side) each hand is drawn. */
	private static final float IN = 0.3F;

	@Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER,
			ordinal = 0))
	private void shootingstarTest$handsIn(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item,
			float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (FilmDomainKortev.active()) {
			Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
			matrices.translate(arm == Arm.RIGHT ? -IN : IN, 0.04F, 0.0F);
		}
	}
}
