package io.github.kortev.chitty.client;

import io.github.kortev.chitty.airship.AirshipEntity;
import io.github.kortev.shootingstar.ShootingStar;
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
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * Draws the airship from her Blender mesh (tools/airship_model.py --game) and poses her parts: the two propellers and
 * their pulleys turn on their shafts, the rudder and the wheel with the pilot's steering and the elevator as she climbs
 * or sinks; the grapple hangs on its rope wherever it swings (or in the hand of whoever holds it), its rope drawn
 * straight to it and the grapple turned along it, the drum it winds onto growing thinner as the rope goes out; the rope
 * ladder hangs a rung at a time as far as it is let down; and the rack holds as many bombs as are left in it. She banks
 * a little in turns and rocks when she is hit.
 *
 * <p>Her texture is baked with her light in it, so she is drawn evenly lit; only the wheel, which turns, takes the
 * game's light. Blender's axes map onto hers as (x, y, z) to (-x, z, y): a Blender turn about its z is a turn about our
 * y by the same angle, about its y one about our z, and about its x one about our x the other way.
 */
public class AirshipRenderer extends EntityRenderer<AirshipEntity> {
	public static final Identifier TEXTURE = ShootingStar.id("textures/entity/airship.png");
	/** How far the rudder and the wheel turn, and the elevator tips (pose in tools/airship_model.py). */
	private static final float RUDDER = 25.0F;
	private static final float WHEEL = 120.0F;
	private static final float ELEVATOR = 20.0F;
	/** The point she pitches and rolls about: the middle of her, between gondola and envelope. */
	private static final float PIVOT_Y = 4.0F;
	private static final float PIVOT_Z = -2.0F;

	public AirshipRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.shadowRadius = 2.0F;
		MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE, new ChittyTexture(TEXTURE));
	}

	@Override
	public Identifier getTexture(AirshipEntity ship) {
		return TEXTURE;
	}

	@Override
	public void render(AirshipEntity ship, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
		ChittyMesh mesh = ChittyMesh.get("airship");
		if (mesh != null) {
			matrices.push();
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
			float wobble = ship.getDamageWobbleTicks() - tickDelta;
			float strength = Math.max(0.0F, ship.getDamageWobbleStrength() - tickDelta);
			matrices.translate(0.0F, PIVOT_Y, PIVOT_Z);
			if (wobble > 0.0F) {
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
						MathHelper.sin(wobble) * wobble * strength / 60.0F * ship.getDamageWobbleSide()));
			}
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-ship.getTilt(tickDelta)));
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(ship.getBank(tickDelta)));
			matrices.translate(0.0F, -PIVOT_Y, -PIVOT_Z);
			drawParts(ship, mesh, yaw, tickDelta, matrices, buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light);
			matrices.pop();
		}
		super.render(ship, yaw, tickDelta, matrices, buffers, light);
	}

	private static void drawParts(AirshipEntity ship, ChittyMesh mesh, float yaw, float tickDelta, MatrixStack matrices, VertexConsumer out,
			int light) {
		int overlay = OverlayTexture.DEFAULT_UV;
		float spin = ship.getPropSpin(tickDelta);
		float steer = ship.getSteer(tickDelta);
		float drop = ship.getShownDrop(tickDelta);
		// The grapple's ring from where its rope comes out, in her own frame, and the turn from hanging straight down to
		// lying along its rope.
		Vec3d hookAt = ship.getShownHook(tickDelta).rotateY(yaw * MathHelper.RADIANS_PER_DEGREE);
		float rope = (float) hookAt.length();
		Quaternionf along = rope > 0.05F
				? new Quaternionf().rotationTo(0.0F, -1.0F, 0.0F, (float) hookAt.x / rope, (float) hookAt.y / rope, (float) hookAt.z / rope)
				: new Quaternionf();
		float ladder = ship.getShownLadder(tickDelta);
		int bombs = ship.getBombs();
		for (ChittyMesh.Part part : mesh.parts.values()) {
			String name = part.name;
			if (name.equals("body")) {
				part.draw(matrices.peek(), out, light, overlay, null);
				continue;
			}
			if (name.startsWith("bomb_") && name.charAt(name.length() - 1) - '0' >= bombs) {
				continue;
			}
			if (name.equals("rope") && rope < 0.05F) {
				continue;
			}
			if (name.equals("ladder")) {
				// One rung's length of it, stacked down from the top as far as it is let down.
				int rungs = Math.round(Math.min(ladder, (float) AirshipEntity.LINE_MAX) / AirshipEntity.LADDER_PITCH);
				for (int i = 0; i < rungs; i++) {
					matrices.push();
					matrices.translate(part.pivot.x, part.pivot.y - i * AirshipEntity.LADDER_PITCH, part.pivot.z);
					matrices.multiply(part.rest);
					part.draw(matrices.peek(), out, light, overlay, null);
					matrices.pop();
				}
				continue;
			}
			matrices.push();
			matrices.translate(part.pivot.x, part.pivot.y, part.pivot.z);
			matrices.multiply(part.rest);
			switch (name) {
				case "prop_r", "prop_r_pulley" -> matrices.multiply(RotationAxis.POSITIVE_Z.rotation(spin));
				case "prop_l", "prop_l_pulley" -> matrices.multiply(RotationAxis.POSITIVE_Z.rotation(-spin));
				case "rudder" -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-RUDDER * steer));
				case "elevator" -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-ELEVATOR * ship.getClimb(tickDelta)));
				case "helm" -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(WHEEL * steer));
				case "coil" -> {
					// The grapple's rope winds off the drum as the grapple goes down.
					float k = 1.0F - 0.4F * Math.min(drop, (float) AirshipEntity.LINE_MAX) / (float) AirshipEntity.LINE_MAX;
					matrices.scale(k, k, 1.0F);
				}
				case "hook" -> {
					matrices.translate(hookAt.x, hookAt.y, hookAt.z);
					matrices.multiply(along);
				}
				case "rope" -> {
					matrices.multiply(along);
					matrices.scale(1.0F, rope, 1.0F);
				}
				default -> {
				}
			}
			part.draw(matrices.peek(), out, light, overlay, null);
			matrices.pop();
		}
	}
}
