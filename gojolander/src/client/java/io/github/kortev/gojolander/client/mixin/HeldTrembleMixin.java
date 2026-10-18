package io.github.kortev.gojolander.client.mixin;

import io.github.kortev.gojolander.client.ClientDomain;
import io.github.kortev.gojolander.client.ClientDomains;
import io.github.kortev.gojolander.client.HandSign;
import io.github.kortev.gojolander.domain.DomainTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Whatever a Domain holds trembles where it hangs once the flood pours in: everything there is, all at once, is too much
 * to stand still under; at each step down it jolts. Let go of broken, it shakes harder as the ringing climbs, then
 * gives way: a player drops onto their knees (posed by {@link PlayerModelMixin}), anything else sags forward over its
 * legs, until they come to and get up. Only drawn so (it stays exactly where it is).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class HeldTrembleMixin {
	/** How far anything but a player sags (blocks), and pitches forward (radians), as its legs give. */
	private static final float SAG = 0.07F;
	private static final float SLUMP = 0.16F;

	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"))
	private void gojolander$tremble(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers,
			int light, CallbackInfo ci) {
		float delta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
		ClientDomain held = ClientDomains.holdingEntity(entity);
		if (held != null) {
			double t = held.time(delta);
			float k = (float) MathHelper.clamp((t - DomainTimeline.VOID) / 6.0, 0.0, 1.0) * 0.022F * (1.0F + 3.0F * held.jolt(t));
			shake(entity, matrices, t, k);
			return;
		}
		ClientDomain let = ClientDomains.kneeling(entity);
		if (let == null) {
			return;
		}
		double t = let.time(delta);
		if (let.snapping(entity.getId(), t)) {
			shake(entity, matrices, t, 0.022F * (1.0F + 1.5F * (float) ((t - let.collapse()) / DomainTimeline.SNAP)));
		}
		float kneel = let.kneel(entity.getId(), t);
		if (kneel <= 0.0F) {
			return;
		}
		if (entity instanceof PlayerEntity) {
			matrices.translate(0.0F, -HandSign.DROP * kneel, 0.0F);
		} else {
			// Forward over its legs: about the axis to its right, through its feet.
			float r = MathHelper.lerpAngleDegrees(delta, entity.prevBodyYaw, entity.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
			matrices.translate(0.0F, -SAG * kneel, 0.0F);
			matrices.multiply(new Quaternionf().rotateAxis(SLUMP * kneel, MathHelper.cos(r), 0.0F, MathHelper.sin(r)));
		}
	}

	private static void shake(LivingEntity entity, MatrixStack matrices, double t, float k) {
		if (k > 0.0F) {
			int id = entity.getId();
			matrices.translate(MathHelper.sin((float) t * 9.1F + id) * k, MathHelper.sin((float) t * 7.3F + id * 2.0F) * k * 0.6F,
					MathHelper.sin((float) t * 8.3F + id * 3.0F) * k);
		}
	}
}
