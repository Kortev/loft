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
 * turn sideways to lie flat on her raft; the wings unfold out from under the running boards like pleated fans, their
 * pleats flattening as they open, the nose wing draws out in front of her and opens, the tail wing behind, and the
 * mast on the end of each wing stands up with its propeller turning flat on top while the pusher propeller unfolds on
 * the stern; the raft blows up round her and the screw turns in the water; the gear lever and handbrake move with the
 * driving, the starting handle swings as she is started, and the hamper is there or not; the car pitches and banks in
 * the air and rocks when she is hit.
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
	/** A folded side wing: how much of its spread it keeps, and how far it hangs down (WING in tools/chitty_model.py). */
	private static final float PLEAT_CLOSED = 0.07F;
	private static final float PLEAT_DROP = 0.15F;
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
		// The raft: the wheels turn flat first, then it blows up round her.
		float flat = smooth(MathHelper.clamp(floats * 2.2F - 0.1F, 0.0F, 1.0F));
		float inflate = backOut(MathHelper.clamp((floats - 0.3F) / 0.7F, 0.0F, 1.0F));
		for (ChittyMesh.Part part : mesh.parts.values()) {
			String name = part.name;
			if (name.equals("glass") || name.startsWith("mast_") || name.startsWith("rotor_") || name.equals("tailprop")) {
				continue;
			}
			if (name.equals("body")) {
				part.draw(matrices.peek(), out, light, overlay, shine);
				continue;
			}
			if (name.equals("hamper") && !car.hasHamper()) {
				continue;
			}
			matrices.push();
			boolean visible = true;
			if (name.startsWith("wing_")) {
				poseWing(matrices, part, wings);
			} else if (name.startsWith("nosefan_") || name.startsWith("tailfan_")) {
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
					visible = inflate > 0.01F;
					matrices.scale(inflate, inflate, inflate);
					matrices.multiply(RotationAxis.POSITIVE_Z.rotation(car.getScrewSpin(tickDelta)));
				} else if (name.startsWith("float")) {
					// Blown up out of a flat bundle under her into a great raft, swelling a little past full and settling.
					visible = floats > 0.01F;
					float k = Math.max(0.0F, inflate);
					matrices.scale(0.45F + 0.55F * k, 0.1F + 0.9F * k, 0.75F + 0.25F * k);
				} else if (name.equals("lever_gear")) {
					// Forward with the throttle, back for reverse.
					matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(18.0F * car.getGearLever(tickDelta)));
				} else if (name.equals("lever_brake")) {
					// Pulled back while she stands.
					matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-26.0F * car.getBrakeLever(tickDelta)));
				} else if (name.equals("crank")) {
					matrices.multiply(RotationAxis.POSITIVE_Z.rotation(car.getCrankSpin(tickDelta)));
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
			poseWing(matrices, wing, wings);
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
		// The pusher propeller on its shaft out of the top of the stern, unfolding as the masts come up and turning
		// about her length.
		ChittyMesh.Part prop = mesh.parts.get("tailprop");
		if (prop != null && raise > 0.02F) {
			matrices.push();
			matrices.translate(prop.pivot.x, prop.pivot.y, prop.pivot.z);
			matrices.multiply(prop.rest);
			matrices.scale(raise, raise, raise);
			matrices.multiply(RotationAxis.POSITIVE_Z.rotation(car.getPropSpin(tickDelta) * 1.7F));
			prop.draw(matrices.peek(), out, light, overlay, shine);
			matrices.pop();
		}
	}

	/**
	 * Moves the stack to a side wing panel's hinge and poses it. Each wing is one pleated cloth, a hand fan on its side:
	 * its back edge lies along her side under the running board the whole time, and folded it is closed up onto it,
	 * the pleats standing on edge, dropped to hang under the board. Opening, it draws out to its full length, its front
	 * edge swings out and every pleat flattens as it goes, alternately up and down so that neighbouring panels meet
	 * along their edges, until it lies flat, tipped up a little about her length. (tools/chitty_model.py's pose() does
	 * the same.)
	 */
	private static void poseWing(MatrixStack matrices, ChittyMesh.Part part, float wings) {
		float side = part.name.contains("_l_") ? -1.0F : 1.0F;
		int index = part.name.charAt(part.name.length() - 1) - '0';
		float open = backOut(MathHelper.clamp(wings / 0.9F, 0.0F, 1.0F));
		float out = smooth(MathHelper.clamp(wings / 0.6F, 0.0F, 1.0F));
		// How much of its open width each pleat shows from above, and so how far it stands up off the flat.
		float across = MathHelper.clamp(MathHelper.lerp(open, PLEAT_CLOSED, 1.0F), 0.0F, 1.0F);
		float pleat = (float) Math.acos(across);
		float yaw = MathHelper.lerp(open, part.c, part.a);
		matrices.translate(part.pivot.x, part.pivot.y - PLEAT_DROP * MathHelper.sin(pleat), part.pivot.z);
		matrices.multiply(part.rest);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-part.b * side * MathHelper.clamp(open, 0.0F, 1.0F)));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw * side));
		matrices.multiply(RotationAxis.POSITIVE_X.rotation(index % 2 == 0 ? pleat : -pleat));
		float k = MathHelper.lerp(out, part.d, 1.0F);
		matrices.scale(k, 1.0F, k);
	}

	/**
	 * Moves the stack to a nose or tail fan section's hinge and poses it, in two stages as the fan comes out: first the
	 * folded fan, still closed, draws out to its full length; then it opens, section after section, each to its place
	 * with a little sprung overshoot. Going away it does the same backwards.
	 */
	private static void poseFan(MatrixStack matrices, ChittyMesh.Part part, float wings) {
		float side = part.name.contains("_l_") ? -1.0F : 1.0F;
		int index = part.name.charAt(part.name.length() - 1) - '0';
		float out = smooth(MathHelper.clamp(wings / 0.45F, 0.0F, 1.0F));
		float spread = backOut(MathHelper.clamp((wings - 0.4F - 0.03F * index) / 0.42F, 0.0F, 1.0F));
		float yaw = MathHelper.lerp(spread, part.c, part.a);
		matrices.translate(part.pivot.x, part.pivot.y, part.pivot.z);
		matrices.multiply(part.rest);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw * side));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-part.b * side * MathHelper.clamp(spread, 0.0F, 1.0F)));
		float k = MathHelper.lerp(out, part.d, 1.0F);
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
