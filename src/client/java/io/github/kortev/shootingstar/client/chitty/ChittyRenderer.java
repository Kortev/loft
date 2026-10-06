package io.github.kortev.shootingstar.client.chitty;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.chitty.ChittyEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Draws Chitty from her Blender mesh and poses her parts: the wheels roll, the front ones steer and all four turn flat to
 * fly; the wings fan out from under the running boards; the propeller comes out on the grille and spins; the floats blow
 * up and the screw turns in the water; the car pitches and banks in the air and rocks when she is hit.
 *
 * <p>Blender's axes map onto the car's as (x, y, z) to (-x, z, y), so a Blender turn about its z is a turn about our y by
 * the same angle, about its y one about our z, and about its x one about our x the other way.
 */
public class ChittyRenderer extends EntityRenderer<ChittyEntity> {
	public static final Identifier TEXTURE = ShootingStar.id("textures/entity/chitty.png");
	/** How far the front wheels turn, and the steering wheel with them. */
	private static final float STEER_LOCK = 28.0F;
	private static final float WHEEL_TURNS = 110.0F;

	public ChittyRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.shadowRadius = 1.6F;
	}

	@Override
	public Identifier getTexture(ChittyEntity car) {
		return TEXTURE;
	}

	@Override
	public void render(ChittyEntity car, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
		ChittyMesh mesh = ChittyMesh.get();
		if (mesh != null) {
			matrices.push();
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
			float wobble = car.getDamageWobbleTicks() - tickDelta;
			float strength = Math.max(0.0F, car.getDamageWobbleStrength() - tickDelta);
			if (wobble > 0.0F) {
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
						MathHelper.sin(wobble) * wobble * strength / 30.0F * car.getDamageWobbleSide()));
			}
			matrices.translate(0.0, ChittyEntity.TILT_PIVOT, 0.0);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-car.getTilt(tickDelta)));
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(car.getBank(tickDelta)));
			matrices.translate(0.0, -ChittyEntity.TILT_PIVOT, 0.0);
			drawParts(car, mesh, tickDelta, matrices, buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light);
			ChittyMesh.Part glass = mesh.parts.get("glass");
			if (glass != null) {
				glass.draw(matrices.peek(), buffers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE)), light, OverlayTexture.DEFAULT_UV);
			}
			matrices.pop();
		}
		super.render(car, yaw, tickDelta, matrices, buffers, light);
	}

	private void drawParts(ChittyEntity car, ChittyMesh mesh, float tickDelta, MatrixStack matrices, VertexConsumer out, int light) {
		int overlay = OverlayTexture.DEFAULT_UV;
		float wings = car.getWingOpen(tickDelta);
		float floats = car.getFloatOpen(tickDelta);
		float flat = smooth(MathHelper.clamp(wings * 1.4F - 0.2F, 0.0F, 1.0F));
		float steer = car.getSteer(tickDelta);
		float spin = car.getWheelSpin(tickDelta);
		for (ChittyMesh.Part part : mesh.parts.values()) {
			String name = part.name;
			if (name.equals("glass")) {
				continue;
			}
			if (name.equals("body")) {
				part.draw(matrices.peek(), out, light, overlay);
				continue;
			}
			matrices.push();
			matrices.translate(part.pivot.x, part.pivot.y, part.pivot.z);
			matrices.multiply(part.rest);
			boolean visible = true;
			if (name.startsWith("wheel_")) {
				float side = name.endsWith("r") ? 1.0F : -1.0F;
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90.0F * side * flat));
				if (name.startsWith("wheel_f")) {
					matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(steer * STEER_LOCK * (1.0F - flat)));
				}
				matrices.multiply(RotationAxis.POSITIVE_X.rotation(spin));
			} else if (name.equals("steering_wheel")) {
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(steer * WHEEL_TURNS));
			} else if (name.startsWith("wing_") || name.startsWith("canard_") || name.startsWith("tailwing_")) {
				visible = wings > 0.01F;
				float side = name.contains("_r_") ? 1.0F : -1.0F;
				// They grow out from under the boards first, then fan open with a little overshoot.
				float grow = smooth(MathHelper.clamp(wings * 1.6F, 0.0F, 1.0F));
				float fan = backOut(MathHelper.clamp(wings * 1.25F - 0.25F, 0.0F, 1.0F));
				float open = MathHelper.lerp(fan, -90.0F, part.a);
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(open * side));
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-part.b * side * fan));
				float k = 0.12F + 0.88F * grow;
				matrices.scale(k, 1.0F, k);
			} else if (name.equals("propeller")) {
				visible = wings > 0.01F;
				float k = smooth(MathHelper.clamp(wings * 1.5F - 0.3F, 0.0F, 1.0F));
				matrices.scale(k, k, k);
				matrices.multiply(RotationAxis.POSITIVE_Z.rotation(car.getPropSpin(tickDelta)));
			} else if (name.equals("screw")) {
				visible = floats > 0.01F;
				matrices.scale(floats, floats, floats);
				matrices.multiply(RotationAxis.POSITIVE_Z.rotation(car.getScrewSpin(tickDelta)));
			} else if (name.startsWith("float_")) {
				visible = floats > 0.01F;
				float k = smooth(floats);
				matrices.scale(0.2F + 0.8F * k, 0.15F + 0.85F * k, 0.5F + 0.5F * k);
			}
			if (visible) {
				part.draw(matrices.peek(), out, light, overlay);
			}
			matrices.pop();
		}
	}

	private static float smooth(float x) {
		return x * x * (3.0F - 2.0F * x);
	}

	/** Eases out past 1 and back, for a sprung opening. */
	private static float backOut(float x) {
		float c = 1.6F;
		float t = x - 1.0F;
		return 1.0F + (c + 1.0F) * t * t * t + c * t * t;
	}
}
