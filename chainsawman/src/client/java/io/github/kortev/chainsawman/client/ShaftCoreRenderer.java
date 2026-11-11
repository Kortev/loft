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
		if (core.window()) {
			window(core, world, now, matrices);
			return;
		}
		float rise = ease((now - core.born()) / ShaftCoreEntity.RISE);
		float cut = core.cutAt() < 0 ? 0.0F : ease((now - core.cutAt()) / ShaftCoreEntity.FALL);
		float beat = 1.0F + 0.05F * MathHelper.sin(now * 0.35F) * (core.cutAt() < 0 ? 1.0F : 0.0F);
		boolean shaft = inShaft(core, world);
		Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
		if (shaft) {
			// It turns its face to the nearest player, held up about chest high in the middle of the arena (seen whole, not filling the view), and
			// tips up toward whoever is above it.
			net.minecraft.entity.player.PlayerEntity near = world.getClosestPlayer(core, 24.0);
			float lift = 1.0F;
			float yawTo = 0.0F;
			float pitchTo = 1.0F;
			if (near != null) {
				net.minecraft.util.math.Vec3d eye = near.getEyePos();
				double hd = Math.hypot(eye.x - core.getX(), eye.z - core.getZ());
				if (hd < 7.0 && eye.y - core.getY() < 4.0) {
					lift = (float) MathHelper.clamp(eye.y - core.getY() - 0.4, 0.8, 1.4);
				}
				double dx = eye.x - core.getX();
				double dy = eye.y - (core.getY() + lift);
				double dz = eye.z - core.getZ();
				yawTo = (float) Math.atan2(-dz, dx);
				pitchTo = (float) Math.atan2(dy, Math.hypot(dx, dz));
			}
			base.translate(0.0F, -2.2F * (1.0F - rise) + lift, 0.0F).rotateY(yawTo).rotateZ(pitchTo);
		} else {
			base.rotateY(-(float) Math.toRadians(core.getYaw())).rotateY(-(float) Math.PI / 2.0F);
		}
		// (In the shaft at the room pixel size, 1/16 block, never scaled; in the corridor, the size of the heart.)
		base.translate(0.0F, 0.04F * (beat - 1.0F) * 20.0F, 0.0F);
		if (!shaft) {
			base.scale(1.5F);
		}
		FleshMesh mesh = FleshMesh.get("core");
		FleshMesh.Look look = shaft ? ArenaRenderer.lookNear(world, core, now) : new FleshMesh.Look().light(light, 0.85F, 1.3F);
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

	/**
	 * A window's core: forced out of its end wall on a stalk (a collar of flesh round where it splits the wall), chest high, its face turned on
	 * whoever cut it last (or the nearest player), its mouths working; flushed as it is cut; drawn back into the wall when its time is up.
	 */
	private void window(ShaftCoreEntity core, ClientWorld world, float now, MatrixStack matrices) {
		// Which way is out of its wall: the way it faces (its yaw), level.
		double yaw = Math.toRadians(core.getYaw());
		net.minecraft.util.math.Vec3d out = new net.minecraft.util.math.Vec3d(-Math.sin(yaw), 0.0, Math.cos(yaw));
		float k = ease((now - core.born()) / ShaftCoreEntity.RISE);
		if (core.backAt() >= 0) {
			k *= 1.0F - ease((now - core.backAt()) / ShaftCoreEntity.RISE);
		}
		// The wall is 1.7 behind it: it slides out of the wall along its stalk.
		net.minecraft.util.math.Vec3d wall = out.multiply(-1.7).add(0.0, 0.85, 0.0);
		net.minecraft.util.math.Vec3d body = wall.add(out.multiply(1.75 * k));
		Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
		FleshMesh.Look look = ArenaRenderer.lookNear(world, core, now);
		ShaftArmRenderer.collar(base, wall.add(0.0, -0.1, 0.0), out, "big_collar", look);
		ShaftArmRenderer.chain(base, wall.subtract(out.multiply(0.3)), body.subtract(out.multiply(0.55)), "neck_segment", false, out, 0.0F, look);
		// Its face on whoever is cutting it.
		net.minecraft.entity.Entity cutter = core.cutter() >= 0 ? world.getEntityById(core.cutter()) : null;
		net.minecraft.entity.Entity target = cutter != null ? cutter : world.getClosestPlayer(core, 24.0);
		float yawTo = (float) Math.atan2(-out.z, out.x);
		float pitchTo = 0.0F;
		if (target != null) {
			net.minecraft.util.math.Vec3d eye = target.getEyePos().subtract(core.getPos().add(body));
			yawTo = (float) Math.atan2(-eye.z, eye.x);
			pitchTo = (float) MathHelper.clamp(Math.atan2(eye.y, Math.hypot(eye.x, eye.z)), -0.6, 0.6);
		}
		float writhe = 0.08F * MathHelper.sin(now * 0.7F);
		Matrix4f m = new Matrix4f(base).translate((float) body.x, (float) body.y + 0.03F * MathHelper.sin(now * 0.35F), (float) body.z).rotateY(yawTo + writhe).rotateZ(pitchTo);
		FleshMesh.Look cl = ArenaRenderer.lookNear(world, core, now);
		cl.rim = 0.4F;
		long hurtAgo = core.hurtTime();
		cl.hurt = hurtAgo >= 0 && hurtAgo < 8 ? 0.5F * (1.0F - hurtAgo / 8.0F) : 0.0F;
		// (Its mouths gasp faster than they would at rest.)
		FleshMesh.get("core").draw(m, now * 2.2F, cl, null);
	}
}
