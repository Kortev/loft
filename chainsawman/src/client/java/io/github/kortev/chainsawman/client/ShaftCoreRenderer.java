package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.EternityDevilEntity;
import io.github.kortev.chainsawman.hotel.ShaftCoreEntity;
import java.util.List;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Eternity Devil's core ({@link ShaftCoreEntity}): the baked figure 8 ({@code eternity_core_*}: a lumpy grey body, a weary eye and a
 * mouth on each loop, two mouths at the waist, tears). In the shaft it rises out of the pit face up toward the players, beats while it is
 * offered, and when it is cut it comes apart along the waist (the same mesh drawn twice, each half kept by a clip plane through the middle,
 * sliding out and tipping away). In the corridor it stands at the heart's place facing the players.
 */
public class ShaftCoreRenderer extends EntityRenderer<ShaftCoreEntity> {
	public ShaftCoreRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ShaftCoreEntity entity) {
		return Identifier.of("chainsawman", "textures/entity/eternity_devil.png");
	}

	@Override
	public boolean shouldRender(ShaftCoreEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		return true;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** Whether this core is in a shaft (a shaft devil is near it). */
	private static boolean inShaft(ShaftCoreEntity core, ClientWorld world) {
		List<EternityDevilEntity> d = world.getEntitiesByClass(EternityDevilEntity.class, core.getBoundingBox().expand(14.0), EternityDevilEntity::shaft);
		return !d.isEmpty();
	}

	@Override
	public void render(ShaftCoreEntity core, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(core.getWorld() instanceof ClientWorld world)) {
			return;
		}
		float now = world.getTime() + delta;
		float rise = ease((now - core.born()) / ShaftCoreEntity.RISE);
		float cut = core.cutAt() < 0 ? 0.0F : ease((now - core.cutAt()) / ShaftCoreEntity.FALL);
		float beat = 1.0F + 0.05F * MathHelper.sin(now * 0.35F) * (core.cutAt() < 0 ? 1.0F : 0.0F);
		boolean shaft = inShaft(core, world);
		Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
		if (shaft) {
			// Face up, tipped toward the nearest player so the face reads from where they stand (the loops stay across).
			net.minecraft.entity.player.PlayerEntity near = world.getClosestPlayer(core, 24.0);
			float toward = near == null ? 0.0F : (float) Math.atan2(-(near.getZ() - core.getZ()), near.getX() - core.getX());
			base.translate(0.0F, -2.2F * (1.0F - rise) + 1.0F, 0.0F).rotateY(toward).rotateZ(1.0F);
		} else {
			base.rotateY(-(float) Math.toRadians(core.getYaw())).rotateY(-(float) Math.PI / 2.0F);
		}
		base.scale((shaft ? 2.0F : 1.5F) * beat);
		FleshMesh mesh = FleshMesh.get("core");
		FleshMesh.Look look = new FleshMesh.Look().light(light, 0.85F, 1.3F);
		look.rim = 0.4F;
		look.glowAt = shaft ? new Vector3f(1.0F, 0.0F, 0.0F) : null;
		look.glowRange = 3.0F;
		look.glowR = 0.1F;
		look.glowG = 0.06F;
		look.glowB = 0.03F;
		if (cut <= 0.0F) {
			mesh.draw(base, now, look, null);
			return;
		}
		// Cut: two halves along the waist (the loops are at +Z and -Z in the mesh), sliding out and tipping away.
		for (int side = -1; side <= 1; side += 2) {
			Matrix4f m = new Matrix4f(base).translate(0.0F, 0.0F, side * 0.45F * cut).rotateX(side * 0.6F * cut).translate(-0.5F * cut, 0.0F, 0.0F);
			FleshMesh.Look half = new FleshMesh.Look().light(light, 0.85F, 1.3F);
			half.rim = look.rim;
			half.glowAt = look.glowAt;
			half.glowRange = look.glowRange;
			half.glowR = look.glowR;
			half.glowG = look.glowG;
			half.glowB = look.glowB;
			half.clip = new Vector3f(0.0F, 0.0F, side);
			mesh.draw(m, now, half, null);
		}
	}
}
