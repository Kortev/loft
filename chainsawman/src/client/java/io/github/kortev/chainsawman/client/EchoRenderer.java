package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.contract.EchoEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Draws a copy of a player ({@link EchoEntity}): the owner's skin on the player model, in the look of past or future ({@link EchoLook}). */
public class EchoRenderer extends LivingEntityRenderer<EchoEntity, PlayerEntityModel<EchoEntity>> {
	private final PlayerEntityModel<EchoEntity> wide;
	private final PlayerEntityModel<EchoEntity> slim;

	public EchoRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5F);
		this.wide = this.model;
		this.slim = new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER_SLIM), true);
	}

	static SkinTextures skin(UUID owner) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (owner != null && client.world != null && client.world.getPlayerByUuid(owner) instanceof AbstractClientPlayerEntity player) {
			return player.getSkinTextures();
		}
		return DefaultSkinHelper.getSkinTextures(owner != null ? owner : UUID.randomUUID());
	}

	@Override
	public Identifier getTexture(EchoEntity entity) {
		return skin(entity.owner()).texture();
	}

	@Override
	protected boolean hasLabel(EchoEntity entity) {
		return false;
	}

	@Override
	protected float getShadowRadius(EchoEntity entity) {
		return 0.4F;
	}

	@Override
	public void render(EchoEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		SkinTextures skin = skin(entity.owner());
		this.model = skin.model() == SkinTextures.Model.SLIM ? slim : wide;
		float now = entity.getWorld().getTime() + delta;
		Vec3d cur = entity.getLerpedPos(delta);
		Vec3d body = Vec3d.ZERO;
		List<Vec3d> behind = new ArrayList<>();
		boolean streak = false;
		Vec3d heading = Vec3d.ZERO;
		int back = entity.returnAt();
		float sinceBorn = now - entity.bornAt();
		if (back >= 0 && entity.getWorld().getPlayerByUuid(entity.owner()) instanceof PlayerEntity owner) {
			float k = MathHelper.clamp((now - back) / EchoEntity.STREAK_IN, 0.0F, 1.0F);
			Vec3d from = entity.returnFrom();
			Vec3d to = owner.getLerpedPos(delta);
			// Past ones swing in from one side, future ones from the other.
			Vec3d way = to.subtract(from);
			Vec3d side = new Vec3d(-way.z, 0.0, way.x).normalize().multiply(entity.future() ? -1.0 : 1.0);
			double bow = Math.min(2.4, way.horizontalLength() * 0.3);
			body = curved(from, to, side, bow, k).subtract(cur);
			for (int i = 1; i <= 5; i++) {
				behind.add(curved(from, to, side, bow, Math.max(0.0F, k - 0.11F * i)).subtract(cur));
			}
			streak = true;
		} else if (sinceBorn >= 0 && sinceBorn < EchoEntity.STREAK_OUT) {
			float k = sinceBorn / EchoEntity.STREAK_OUT;
			Vec3d from = entity.origin();
			body = path(from, cur, k).subtract(cur);
			for (int i = 1; i <= 5; i++) {
				behind.add(path(from, cur, Math.max(0.0F, k - 0.12F * i)).subtract(cur));
			}
			streak = true;
		} else {
			List<Vec3d> trail = ClientEcho.trail(entity);
			for (Vec3d old : trail) {
				behind.add(old.subtract(cur));
			}
			if (trail.size() > 3) {
				heading = trail.get(0).subtract(trail.get(3)).multiply(1.0 / 3.0);
			}
		}
		boolean flicker = now - entity.hurtAt() < 8.0F && (int) (now * 1.5F) % 2 == 0;
		EchoLook.draw(entity.future(), skin.texture(), flicker, streak, body, behind, heading, matrices, provider, light,
				(m, p, l) -> EchoRenderer.super.render(entity, yaw, delta, m, p, l));
	}

	/** From {@code from} to {@code to} bowed out to {@code side} by up to {@code bow} blocks, {@code k} of the way. */
	private static Vec3d curved(Vec3d from, Vec3d to, Vec3d side, double bow, float k) {
		double e = k * k * (3.0 - 2.0 * k);
		return from.lerp(to, e).add(side.multiply(bow * Math.sin(Math.PI * e)));
	}

	/** From {@code from} to {@code to}, {@code k} of the way, quickly at first. */
	private static Vec3d path(Vec3d from, Vec3d to, float k) {
		double e = 1.0 - (1.0 - k) * (1.0 - k);
		return from.lerp(to, e);
	}
}
