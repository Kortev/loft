package io.github.kortev.chainsawman.client.mixin;

import io.github.kortev.chainsawman.client.ClientEcho;
import io.github.kortev.chainsawman.client.EchoLook;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Echo's disguise: while someone's Echo is going, everyone else sees them drawn exactly as one of the copies (the same tint,
 * trail or haze) and with no name tag, so which one is real is not obvious. The player themselves sees nothing different.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class EchoDisguiseMixin {
	private static boolean chainsawman$inside;

	@Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"), cancellable = true)
	private void chainsawman$disguise(AbstractClientPlayerEntity player, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider,
			int light, CallbackInfo ci) {
		if (chainsawman$inside || !ClientEcho.disguised(player)) {
			return;
		}
		ClientEcho.Info info = ClientEcho.of(player.getUuid());
		Vec3d cur = player.getLerpedPos(delta);
		List<Vec3d> behind = new ArrayList<>();
		List<Vec3d> trail = ClientEcho.trail(player);
		for (Vec3d old : trail) {
			behind.add(old.subtract(cur));
		}
		Vec3d heading = trail.size() > 3 ? trail.get(0).subtract(trail.get(3)).multiply(1.0 / 3.0) : Vec3d.ZERO;
		PlayerEntityRenderer self = (PlayerEntityRenderer) (Object) this;
		chainsawman$inside = true;
		try {
			EchoLook.draw(info.kind() == 1, player.getSkinTextures().texture(), false, false, Vec3d.ZERO, behind, heading, matrices, provider, light,
					(m, p, l) -> self.render(player, yaw, delta, m, p, l));
		} finally {
			chainsawman$inside = false;
		}
		ci.cancel();
	}

	@Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IF)V",
			at = @At("HEAD"), cancellable = true)
	private void chainsawman$noName(AbstractClientPlayerEntity player, Text text, MatrixStack matrices, VertexConsumerProvider provider, int light,
			float delta, CallbackInfo ci) {
		if (ClientEcho.disguised(player)) {
			ci.cancel();
		}
	}
}
