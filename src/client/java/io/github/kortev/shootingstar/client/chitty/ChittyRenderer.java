package io.github.kortev.shootingstar.client.chitty;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.chitty.ChittyEntity;
import net.minecraft.client.MinecraftClient;
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
import org.joml.Quaternionf;

/**
 * Draws Chitty from her Blender mesh and poses her parts: the wheels roll and the front ones steer, and on the water
 * turn sideways to lie flat on her raft; the wings swing out from under the running boards and fan open, the nose wing
 * opens out in front of her and the fan under her tail straight back with its little propeller pushing, and the
 * mast on the end of each wing stands up with its propeller turning flat on top; the raft blows up round her and the
 * screw turns in the water; the car pitches and banks in the air and rocks when she is hit.
 *
 * <p>Her texture is baked with her light in it (tools/chitty_model.py), so most of her is drawn evenly lit; only the
 * wheels, which roll, carry real normals and take the game's light, and her polished metal is shone live as you look at
 * it (ChittyShine).
 *
 * <p>Blender's axes map onto the car's as (x, y, z) to (-x, z, y), so a Blender turn about its z is a turn about our y by
 * the same angle, about its y one about our z, and about its x one about our x the other way.
 */
public class ChittyRenderer extends EntityRenderer<ChittyEntity> {
	public static final Identifier TEXTURE = ShootingStar.id("textures/entity/chitty.png");
	/** How far the front wheels turn, and the steering wheel with them. */
	private static final float STEER_LOCK = 28.0F;
	private static final float WHEEL_TURNS = 110.0F;
	private final ChittyShine shine = new ChittyShine();

	public ChittyRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.shadowRadius = 1.6F;
		MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE, new ChittyTexture(TEXTURE));
	}

	@Override
	public Identifier getTexture(ChittyEntity car) {
		return TEXTURE;
	}

	@Override
	public void render(ChittyEntity car, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
		ChittyMesh mesh = ChittyMesh.get();
		if (mesh != null) {
			shine.setUp(car, tickDelta, light);
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
				glass.draw(matrices.peek(), buffers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE)), light, OverlayTexture.DEFAULT_UV,
						shine);
			}
			matrices.pop();
		}
		super.render(car, yaw, tickDelta, matrices, buffers, light);
	}

	private void drawParts(ChittyEntity car, ChittyMesh mesh, float tickDelta, MatrixStack matrices, VertexConsumer out, int light) {
		int overlay = OverlayTexture.DEFAULT_UV;
		float wings = car.getWingOpen(tickDelta);
		float floats = car.getFloatOpen(tickDelta);
		float steer = car.getSteer(tickDelta);
		float spin = car.getWheelSpin(tickDelta);
		float flat = smooth(MathHelper.clamp(floats * 1.4F - 0.2F, 0.0F, 1.0F));
		for (ChittyMesh.Part part : mesh.parts.values()) {
			String name = part.name;
			if (name.equals("glass") || name.startsWith("mast_") || name.startsWith("rotor_") || name.equals("tailprop")) {
				continue;
			}
			if (name.equals("body")) {
				part.draw(matrices.peek(), out, light, overlay, shine);
				continue;
			}
			matrices.push();
			boolean visible = true;
			if (name.startsWith("wing_") || name.startsWith("nosefan_") || name.startsWith("tailfan_")) {
				poseFan(matrices, part, wings);
			} else {
				matrices.translate(part.pivot.x, part.pivot.y, part.pivot.z);
				matrices.multiply(part.rest);
				if (name.startsWith("wheel_")) {
					float side = name.endsWith("r") ? 1.0F : -1.0F;
					matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90.0F * side * flat));
					if (name.startsWith("wheel_f")) {
						matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(steer * STEER_LOCK * (1.0F - flat)));
					}
					matrices.multiply(RotationAxis.POSITIVE_X.rotation(spin));
				} else if (name.equals("steering_wheel")) {
					matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(steer * WHEEL_TURNS));
				} else if (name.equals("screw")) {
					visible = floats > 0.01F;
					matrices.scale(floats, floats, floats);
					matrices.multiply(RotationAxis.POSITIVE_Z.rotation(car.getScrewSpin(tickDelta)));
				} else if (name.startsWith("float")) {
					// Blown up out of a flat bundle under her into a great raft.
					visible = floats > 0.01F;
					float k = smooth(floats);
					matrices.scale(0.45F + 0.55F * k, 0.1F + 0.9F * k, 0.75F + 0.25F * k);
				}
			}
			if (visible) {
				part.draw(matrices.peek(), out, light, overlay, shine);
			}
			matrices.pop();
		}
		// The masts on the wing tips, and the propellers on the masts.
		float raise = smooth(MathHelper.clamp(wings * 2.0F - 1.0F, 0.0F, 1.0F));
		for (String side : new String[] {"r", "l"}) {
			ChittyMesh.Part wing = mesh.parts.get("wing_" + side + "_0");
			ChittyMesh.Part mast = mesh.parts.get("mast_" + side);
			ChittyMesh.Part rotor = mesh.parts.get("rotor_" + side);
			if (wing == null || mast == null) {
				continue;
			}
			matrices.push();
			poseFan(matrices, wing, wings);
			matrices.translate(mast.pivot.x, mast.pivot.y, mast.pivot.z);
			// Folded, it lies along the spar; it stands up once the wing is out.
			Quaternionf folded = new Quaternionf(mast.a, mast.b, mast.c, mast.d);
			matrices.multiply(folded.slerp(new Quaternionf(), raise));
			mast.draw(matrices.peek(), out, light, overlay, shine);
			if (rotor != null && raise > 0.02F) {
				matrices.translate(rotor.pivot.x, rotor.pivot.y, rotor.pivot.z);
				matrices.scale(raise, 1.0F, raise);
				matrices.multiply(RotationAxis.POSITIVE_Y.rotation(car.getPropSpin(tickDelta) * (side.equals("r") ? 1.0F : -1.0F)));
				rotor.draw(matrices.peek(), out, light, overlay, shine);
			}
			matrices.pop();
		}
		// The propeller on the end of the tail fan, turning about the fan's middle panel.
		ChittyMesh.Part prop = mesh.parts.get("tailprop");
		ChittyMesh.Part host = prop != null ? mesh.parts.get("tailfan_c_" + (int) prop.a) : null;
		if (host != null && raise > 0.02F) {
			matrices.push();
			poseFan(matrices, host, wings);
			matrices.translate(prop.pivot.x, prop.pivot.y, prop.pivot.z);
			matrices.scale(raise, raise, raise);
			matrices.multiply(RotationAxis.POSITIVE_X.rotation(car.getPropSpin(tickDelta) * 1.7F));
			prop.draw(matrices.peek(), out, light, overlay, shine);
			matrices.pop();
		}
	}

	/**
	 * Moves the stack to a fan panel's hinge and swings it from folded (drawn in, under the car) to open (spread out
	 * with its dihedral), with a little sprung overshoot.
	 */
	private static void poseFan(MatrixStack matrices, ChittyMesh.Part part, float wings) {
		float side = part.name.contains("_l_") ? -1.0F : 1.0F;
		float fan = backOut(MathHelper.clamp(wings * 1.25F - 0.25F, 0.0F, 1.0F));
		matrices.translate(part.pivot.x, part.pivot.y, part.pivot.z);
		matrices.multiply(part.rest);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerp(fan, part.c, part.a) * side));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-part.b * side * MathHelper.clamp(fan, 0.0F, 1.0F)));
		float k = MathHelper.lerp(MathHelper.clamp(fan, 0.0F, 1.0F), part.d, 1.0F);
		matrices.scale(k, 1.0F, k);
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
