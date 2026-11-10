package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.ShaftHeadEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A head lunging out of the Eternity Devil's mass ({@link ShaftHeadEntity}): the lone severed head of the stages mesh ({@code eternity_head_*},
 * a gaunt head with its jaw baked wide open and ragged neck cords) rising out of the floor turned toward the nearest player, jaw hanging open
 * as it comes up and snapping shut at the bite, then sinking back.
 */
public class ShaftHeadRenderer extends EntityRenderer<ShaftHeadEntity> {
	/** The head mesh's jaw is baked open this many degrees; a positive angle closes it. */
	static final float JAW_OPEN = 52.0F;

	public ShaftHeadRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ShaftHeadEntity entity) {
		return Identifier.of("chainsawman", "textures/entity/eternity_devil.png");
	}

	@Override
	public boolean shouldRender(ShaftHeadEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		return true;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	@Override
	public void render(ShaftHeadEntity head, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(head.getWorld() instanceof ClientWorld world)) {
			return;
		}
		float t = world.getTime() + delta - head.start();
		float rise = ease(t / ShaftHeadEntity.RISE);
		float sink = ease((t - (ShaftHeadEntity.BITE_AT + 6)) / (ShaftHeadEntity.LIFE - ShaftHeadEntity.BITE_AT - 6));
		float h = rise * (1.0F - sink) * (head.struck() ? 0.25F : 1.0F);
		if (h <= 0.03F) {
			return;
		}
		// Turned toward the nearest player.
		PlayerEntity near = world.getClosestPlayer(head, 16.0);
		float theta = 0.0F;
		if (near != null) {
			theta = (float) Math.atan2(-(near.getZ() - head.getZ()), near.getX() - head.getX());
		}
		float gape = t < ShaftHeadEntity.BITE_AT - 3 ? 0.9F * ease(t / (ShaftHeadEntity.BITE_AT - 3.0F))
				: t < ShaftHeadEntity.BITE_AT ? 0.9F : 0.9F * (1.0F - ease((t - ShaftHeadEntity.BITE_AT) / 3.0F));
		float closed = JAW_OPEN * (1.0F - gape / 0.9F);
		Matrix4f model = new Matrix4f(matrices.peek().getPositionMatrix()).translate(0.0F, -2.1F * (1.0F - h), 0.0F).rotateY(theta).scale(0.95F);
		FleshMesh.Look look = new FleshMesh.Look().light(light, 0.7F, 1.15F);
		look.rim = 0.45F;
		look.hurt = head.struck() ? 0.5F : 0.0F;
		look.glowAt = new Vector3f(0.0F, -1.5F, 0.0F);
		look.glowRange = 3.0F;
		look.glowR = 0.12F;
		look.glowG = 0.05F;
		look.glowB = 0.02F;
		FleshMesh.get("head").draw(model, world.getTime() + delta, look, jawHook(closed));
	}

	/** A hook that sets the head mesh's jaw to {@code closed} degrees (0 wide open, 52 shut). */
	static FleshMesh.Hook jawHook(float closed) {
		return (p, m, base) -> {
			if (p.kind.equals("jaw")) {
				m.set(base).translate(p.pivot).rotate((float) Math.toRadians(closed), p.anim.axis).translate(-p.pivot.x, -p.pivot.y, -p.pivot.z);
			}
			return true;
		};
	}
}
