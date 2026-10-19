package io.github.kortev.chainsawman.client.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The game's own first-person arm, drawn where it holds nothing: for the form's two saw arms ({@code FirstPersonSaws}). */
@Mixin(HeldItemRenderer.class)
public interface HeldItemRendererInvoker {
	@Invoker("renderArmHoldingItem")
	void chainsawman$renderArm(MatrixStack matrices, VertexConsumerProvider consumers, int light, float equipProgress, float swingProgress, Arm arm);
}
