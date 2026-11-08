package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.client.mixin.LimbAnimatorAccessor;
import io.github.kortev.chainsawman.contract.Forever;
import io.github.kortev.chainsawman.contract.ForeverCopyEntity;
import io.github.kortev.chainsawman.contract.ForeverHubEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Draws one moment of a locked target ({@link ForeverCopyEntity}): the target's own model in its own pose for that moment (each in a
 * stride of its own), drained grey-green and see-through, fainter the further along the line, with a faint dark outline and the
 * red slashes of every stored hit. When the moment ends it streaks in to the target, stretched.
 */
public class ForeverCopyRenderer extends EntityRenderer<ForeverCopyEntity> {
	/** True while a copy is drawn with the target's own renderer (so the name tag is not drawn again). */
	public static boolean drawing;
	/** How solid the copies of each distance (1 to 4) along the line are. */
	private static final float[] ALPHA = {0.9F, 0.76F, 0.58F, 0.42F};
	private static final float[] DRAIN = {0.78F, 0.9F, 0.74F};

	public ForeverCopyRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ForeverCopyEntity entity) {
		return Identifier.of("chainsawman", "textures/entity/forever_flesh.png");
	}

	@Override
	public boolean shouldRender(ForeverCopyEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		return true;
	}

	@Override
	public void render(ForeverCopyEntity copy, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(copy.getWorld().getEntityById(copy.targetId()) instanceof LivingEntity living)) {
			return;
		}
		Entity h = copy.getWorld().getEntityById(copy.hubId());
		ForeverHubEntity hub = h instanceof ForeverHubEntity hh ? hh : null;
		float now = copy.getWorld().getTime() + delta;
		int slot = MathHelper.clamp(Math.abs(copy.slot()), 1, 4);
		float alpha = ALPHA[slot - 1];
		int lit = ForeverGeo.lit(light, 15, 13);
		Vec3d here = copy.getLerpedPos(delta);
		// Appearing: it comes out of the target's own place and settles in the first few ticks.
		float born = MathHelper.clamp((copy.age + delta) / 4.0F, 0.0F, 1.0F);
		Vec3d offset = living.getLerpedPos(delta).subtract(here).multiply(1.0F - born * born * (3.0F - 2.0F * born));
		alpha *= 0.25F + 0.75F * born;
		boolean snapping = copy.snapAt() >= 0;
		float k = 0.0F;
		if (snapping) {
			k = MathHelper.clamp((now - copy.snapAt()) / Forever.SNAP, 0.0F, 1.0F);
			float e = k * k;
			offset = living.getLerpedPos(delta).subtract(here).multiply(e);
			alpha = MathHelper.lerp(k, alpha, 0.92F);
			// The streak: afterimages back along the way it came, strung out.
			for (int i = 1; i <= 5; i++) {
				float kk = Math.max(0.0F, k - 0.13F * i);
				Vec3d at = living.getLerpedPos(delta).subtract(here).multiply(kk * kk);
				float[] tint = {DRAIN[0], DRAIN[1], DRAIN[2], alpha * (0.5F - 0.09F * i)};
				drawTarget(living, dispatcher, copy.getYaw(), copy.stride(), copy.strideSpeed(), false, tint, false, 1.0F, at, matrices, provider, lit, delta);
			}
		}
		float[] body = {DRAIN[0] * 1.5F, DRAIN[1] * 1.55F, DRAIN[2] * 1.45F, alpha};
		drawTarget(living, dispatcher, copy.getYaw(), copy.stride(), copy.strideSpeed(), false, body, true, 1.0F, offset, matrices, provider, lit, delta);
		float[] outline = {0.7F, 1.0F, 0.78F, alpha * 0.3F};
		drawTarget(living, dispatcher, copy.getYaw(), copy.stride(), copy.strideSpeed(), false, outline, true, 1.07F, offset, matrices, provider, lit, delta);
		if (hub != null && !snapping) {
			matrices.push();
			matrices.translate(offset.x, offset.y, offset.z);
			ForeverHubRenderer.marks(dispatcher, matrices, provider, hub.marks(), copy.getHeight(), copy.getWidth(), now - hub.startedAt(), alpha);
			matrices.pop();
		}
	}

	/**
	 * Draws {@code living} with its own renderer at the origin of {@code matrices} (moved by {@code offset}, scaled about its feet),
	 * facing {@code facing} in the stride {@code stride}/{@code speed} (or in its own pose when {@code own}), seen through {@code tint}.
	 */
	@SuppressWarnings("unchecked")
	static void drawTarget(LivingEntity living, EntityRenderDispatcher dispatcher, float facing, float stride, float speed, boolean own, float[] tint,
			boolean emissive, float scale, Vec3d offset, MatrixStack matrices, VertexConsumerProvider provider, int light, float delta) {
		EntityRenderer<LivingEntity> renderer = (EntityRenderer<LivingEntity>) (EntityRenderer<?>) dispatcher.getRenderer(living);
		LimbAnimatorAccessor limbs = (LimbAnimatorAccessor) (Object) living.limbAnimator;
		float pos = limbs.chainsawman$pos();
		float spd = limbs.chainsawman$speed();
		float prev = limbs.chainsawman$prevSpeed();
		float body = living.bodyYaw;
		float prevBody = living.prevBodyYaw;
		float head = living.headYaw;
		float prevHead = living.prevHeadYaw;
		int hurt = living.hurtTime;
		if (!own) {
			float rel = MathHelper.wrapDegrees(head - body);
			limbs.chainsawman$setPos(stride);
			limbs.chainsawman$setSpeed(speed);
			limbs.chainsawman$setPrevSpeed(speed);
			living.bodyYaw = facing;
			living.prevBodyYaw = facing;
			living.headYaw = facing + rel;
			living.prevHeadYaw = facing + rel;
		}
		living.hurtTime = 0;
		boolean was = drawing;
		drawing = true;
		matrices.push();
		matrices.translate(offset.x, offset.y, offset.z);
		if (scale != 1.0F) {
			matrices.scale(scale, scale, scale);
		}
		try {
			renderer.render(living, living.getYaw(), delta, matrices, new ForeverTint(provider, tint, renderer.getTexture(living), emissive), light);
		} finally {
			matrices.pop();
			drawing = was;
			living.hurtTime = hurt;
			limbs.chainsawman$setPos(pos);
			limbs.chainsawman$setSpeed(spd);
			limbs.chainsawman$setPrevSpeed(prev);
			living.bodyYaw = body;
			living.prevBodyYaw = prevBody;
			living.headYaw = head;
			living.prevHeadYaw = prevHead;
		}
	}
}
