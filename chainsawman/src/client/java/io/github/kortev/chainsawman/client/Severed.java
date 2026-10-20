package io.github.kortev.chainsawman.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

/**
 * Arms and legs coming off: going down, whatever you still had flies off you in a spray of blood (an arm lost in the
 * fight, the same), spinning, and lands, and lies there a few seconds before it's gone in a puff. Each is the player's
 * own arm or leg, their skin and sleeve, drawn on its own.
 */
public final class Severed {
	/** How long a limb lies there (ticks). */
	private static final int LASTS = 80;
	private static final float GRAVITY = 0.045F;
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.62F, 0.02F, 0.04F), 1.0F);
	private static final Random RANDOM = Random.create();

	/** Which limb: the right arm, the left arm, the right leg, the left leg. */
	public static final int RIGHT_ARM = 0;
	public static final int LEFT_ARM = 1;
	public static final int RIGHT_LEG = 2;
	public static final int LEFT_LEG = 3;

	/** A limb in the air or on the ground: whose, which, where, how fast, its turn; when it came off; the ground under it. */
	private static final class Limb {
		final AbstractClientPlayerEntity owner;
		final int which;
		final long at;
		final double ground;
		Vec3d pos;
		Vec3d prev;
		Vec3d vel;
		float pitch;
		float roll;
		float yaw;
		float spin;
		boolean landed;

		Limb(AbstractClientPlayerEntity owner, int which, long at, Vec3d pos, Vec3d vel, float spin) {
			this.owner = owner;
			this.which = which;
			this.at = at;
			this.ground = owner.getY();
			this.pos = pos;
			this.prev = pos;
			this.vel = vel;
			this.yaw = owner.getBodyYaw();
			this.spin = spin;
		}
	}

	private static final List<Limb> LIMBS = new CopyOnWriteArrayList<>();

	private Severed() {
	}

	/** {@code player}'s {@code which} limb comes off now. */
	static void off(AbstractClientPlayerEntity player, int which, long now) {
		boolean arm = which <= LEFT_ARM;
		float side = which == RIGHT_ARM || which == RIGHT_LEG ? -1.0F : 1.0F;
		Vec3d right = Vec3d.fromPolar(0.0F, player.getBodyYaw() + 90.0F).multiply(side);
		Vec3d from = player.getPos().add(right.multiply(arm ? 0.34 : 0.12)).add(0.0, arm ? 1.3 : 0.45, 0.0);
		Vec3d out = right.multiply(0.12 + RANDOM.nextDouble() * 0.12).add((RANDOM.nextDouble() - 0.5) * 0.12, 0.22 + RANDOM.nextDouble() * 0.18,
				(RANDOM.nextDouble() - 0.5) * 0.12);
		LIMBS.add(new Limb(player, which, now, from, out, (RANDOM.nextFloat() - 0.5F) * 70.0F));
		ClientWorld world = (ClientWorld) player.getWorld();
		for (int i = 0; i < 10; i++) {
			world.addParticle(BLOOD, from.x, from.y, from.z, out.x * 4.0 + (RANDOM.nextDouble() - 0.5), 0.5, out.z * 4.0 + (RANDOM.nextDouble() - 0.5));
		}
	}

	/** Every tick: the limbs flying, landing, and gone after a while. */
	static void tick(ClientWorld world) {
		long now = world.getTime();
		for (Limb limb : LIMBS) {
			limb.prev = limb.pos;
			if (!limb.landed) {
				limb.vel = limb.vel.add(0.0, -GRAVITY, 0.0).multiply(0.98);
				limb.pos = limb.pos.add(limb.vel);
				limb.pitch += limb.spin;
				limb.roll += limb.spin * 0.6F;
				if (now % 2 == 0) {
					world.addParticle(BLOOD, limb.pos.x, limb.pos.y, limb.pos.z, 0.0, 0.0, 0.0);
				}
				if (limb.pos.y <= limb.ground + 0.12) {
					// Down, lying along the ground.
					limb.pos = new Vec3d(limb.pos.x, limb.ground + 0.12, limb.pos.z);
					limb.landed = true;
					limb.pitch = 90.0F;
					limb.roll = 0.0F;
					world.addParticle(BLOOD, limb.pos.x, limb.pos.y, limb.pos.z, 0.0, 0.0, 0.0);
				}
			}
			if (now - limb.at > LASTS) {
				world.addParticle(ParticleTypes.POOF, limb.pos.x, limb.pos.y, limb.pos.z, 0.0, 0.02, 0.0);
				LIMBS.remove(limb);
			}
		}
	}

	/** Draws them, as the entities are drawn. */
	public static void render(WorldRenderContext context) {
		ClientWorld world = context.world();
		VertexConsumerProvider consumers = context.consumers();
		if (LIMBS.isEmpty() || world == null || consumers == null) {
			return;
		}
		float tickDelta = context.tickCounter().getTickDelta(false);
		Vec3d cam = context.camera().getPos();
		MatrixStack matrices = context.matrixStack() != null ? context.matrixStack() : new MatrixStack();
		for (Limb limb : LIMBS) {
			if (!(MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(limb.owner) instanceof PlayerEntityRenderer renderer)) {
				continue;
			}
			PlayerEntityModel<AbstractClientPlayerEntity> model = renderer.getModel();
			ModelPart[] parts = switch (limb.which) {
				case RIGHT_ARM -> new ModelPart[] {model.rightArm, model.rightSleeve};
				case LEFT_ARM -> new ModelPart[] {model.leftArm, model.leftSleeve};
				case RIGHT_LEG -> new ModelPart[] {model.rightLeg, model.rightPants};
				default -> new ModelPart[] {model.leftLeg, model.leftPants};
			};
			Identifier skin = limb.owner.getSkinTextures().texture();
			VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityTranslucent(skin));
			Vec3d at = limb.prev.lerp(limb.pos, tickDelta);
			int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(at));
			float spin = limb.landed ? 0.0F : limb.spin * tickDelta;
			matrices.push();
			matrices.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - limb.yaw));
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(limb.pitch + spin));
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(limb.roll + spin * 0.6F));
			matrices.scale(-0.9375F, -0.9375F, 0.9375F);
			// Its middle at the point: an arm hangs from 2 pixels above where it's pinned on, a leg from where it is.
			matrices.translate(0.0F, (limb.which <= LEFT_ARM ? -4.0F : -6.0F) / 16.0F, 0.0F);
			for (ModelPart part : parts) {
				ModelTransform was = part.getTransform();
				boolean shown = part.visible;
				float sx = part.xScale;
				float sy = part.yScale;
				float sz = part.zScale;
				part.setTransform(ModelTransform.NONE);
				part.visible = true;
				part.xScale = 1.0F;
				part.yScale = 1.0F;
				part.zScale = 1.0F;
				part.render(matrices, out, light, OverlayTexture.DEFAULT_UV);
				part.setTransform(was);
				part.visible = shown;
				part.xScale = sx;
				part.yScale = sy;
				part.zScale = sz;
			}
			matrices.pop();
		}
	}

	static void clear() {
		LIMBS.clear();
	}
}
