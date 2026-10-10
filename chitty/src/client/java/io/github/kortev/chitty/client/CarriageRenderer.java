package io.github.kortev.chitty.client;

import io.github.kortev.chitty.carriage.CarriageEntity;
import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the Child Catcher's carriage from her Blender mesh (tools/carriage_model.py --game) and poses her parts: her
 * wheels turn as she goes, the fore-carriage (its axle, springs and shafts, and the front wheels) turns under the box as
 * she turns, and the cage door swings on its hinges. Her horse is drawn as the game draws its own, in her harness
 * (CarriageHorse), in her shafts; its traces run back from its collar to her splinter bar and the reins from its bit to
 * the driver's hands. Her disguise is drawn while she wears it, the cloth on the door and its counter swinging with the
 * door, and the bait standing on the counter; thrown off, its pieces fall away from her where she was, tip over onto
 * the ground and lie there a while.
 *
 * <p>She is faceted, as the game's things are: her texture holds her colours alone, drawn pixelated, and the game lights
 * each of her flat faces by which way it faces. Blender's axes map onto hers as (x, y, z) to (-x, z, y).
 */
public class CarriageRenderer extends EntityRenderer<CarriageEntity> {
	public static final Identifier TEXTURE = ShootingStar.id("textures/entity/carriage.png");
	/** How far the door swings open (degrees). */
	private static final float DOOR_SWING = 110.0F;
	/** The fore-carriage's turntable, about which it turns. */
	private static final Vec3d TURNTABLE = new Vec3d(0.0, 1.0, CarriageEntity.FRONT_AXLE);
	/** Where the horse's collar, the bit, the terrets and its traces' middle are, in its model's head and body frames (pixels). */
	private static final double[] TRACE_FROM = {3.0, 2.2, 0.5};
	private static final double[] TRACE_MID = {5.4, -3.0, -10.0};
	private static final double[] BIT = {2.4, -7.4, -6.0};
	private static final double[] TERRET = {2.9, -9.2, -9.4};
	/** The ends of her splinter bar, either side, which the traces pull on. */
	private static final double TREE = 0.29;
	/** The disguise thrown off: how fast its pieces fly out and up, how hard they fall, and when they go. */
	private static final float FLING = 0.06F;
	private static final float TOSS = 0.12F;
	private static final float FALL = 0.03F;
	private static final float LIE = 100.0F;
	private static final float GONE = 140.0F;

	private final CarriageHorse horse;
	private final ItemRenderer items;

	public CarriageRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.shadowRadius = 1.4F;
		this.horse = new CarriageHorse(context);
		this.items = context.getItemRenderer();
		MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE, new ChittyTexture(TEXTURE, true));
	}

	@Override
	public Identifier getTexture(CarriageEntity carriage) {
		return TEXTURE;
	}

	@Override
	public void render(CarriageEntity carriage, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
		ChittyMesh mesh = ChittyMesh.get("carriage");
		int overlay = OverlayTexture.DEFAULT_UV;
		if (mesh != null) {
			VertexConsumer out = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
			drawThrown(carriage, mesh, tickDelta, matrices, out, light);
		}
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
		horse.render(carriage, tickDelta, matrices, buffers, light, overlay);
		if (mesh != null) {
			VertexConsumer out = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
			drawHarness(carriage, mesh, tickDelta, matrices, out, light);
			float wobble = carriage.getDamageWobbleTicks() - tickDelta;
			if (wobble > 0.0F) {
				float strength = Math.max(0.0F, carriage.getDamageWobbleStrength() - tickDelta);
				matrices.translate(0.0F, (float) CarriageEntity.DECK_TOP, 0.0F);
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
						MathHelper.sin(wobble) * wobble * strength / 80.0F * carriage.getDamageWobbleSide()));
				matrices.translate(0.0F, (float) -CarriageEntity.DECK_TOP, 0.0F);
			}
			drawParts(carriage, mesh, tickDelta, matrices, out, light);
			if (carriage.isDisguised()) {
				drawBait(carriage, mesh, tickDelta, matrices, buffers, light);
			}
		}
		matrices.pop();
		super.render(carriage, yaw, tickDelta, matrices, buffers, light);
	}

	private static boolean disguisePart(String name) {
		return name.startsWith("disguise");
	}

	/** Turns the stack about the door's hinge as far as the door is open (for the door, and the disguise's cloth on it). */
	private static void swingDoor(ChittyMesh mesh, float door, MatrixStack matrices) {
		ChittyMesh.Part hinge = mesh.parts.get("door");
		if (hinge == null || door <= 0.0F) {
			return;
		}
		matrices.translate(hinge.pivot.x, hinge.pivot.y, hinge.pivot.z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(DOOR_SWING * door));
		matrices.translate(-hinge.pivot.x, -hinge.pivot.y, -hinge.pivot.z);
	}

	private static void drawParts(CarriageEntity carriage, ChittyMesh mesh, float tickDelta, MatrixStack matrices, VertexConsumer out,
			int light) {
		int overlay = OverlayTexture.DEFAULT_UV;
		float steer = carriage.getSteer(tickDelta) * CarriageEntity.STEER_MAX;
		float rear = carriage.getRearSpin(tickDelta);
		float front = carriage.getFrontSpin(tickDelta);
		float door = carriage.getDoorOpen(tickDelta);
		boolean disguised = carriage.isDisguised();
		for (ChittyMesh.Part part : mesh.parts.values()) {
			String name = part.name;
			if (name.equals("trace") || name.equals("rein") || disguisePart(name) && !disguised) {
				continue;
			}
			if (name.equals("body")) {
				part.draw(matrices.peek(), out, light, overlay, null);
				continue;
			}
			matrices.push();
			if (name.equals("fore_carriage") || name.startsWith("wheel_f")) {
				// The fore-carriage turns on its turntable, the front wheels with it.
				matrices.translate(TURNTABLE.x, TURNTABLE.y, TURNTABLE.z);
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(steer));
				matrices.translate(-TURNTABLE.x, -TURNTABLE.y, -TURNTABLE.z);
			}
			if (name.equals("door") || name.equals("disguise_b")) {
				swingDoor(mesh, door, matrices);
			}
			matrices.translate(part.pivot.x, part.pivot.y, part.pivot.z);
			matrices.multiply(part.rest);
			if (name.startsWith("wheel_r")) {
				matrices.multiply(RotationAxis.POSITIVE_X.rotation(rear));
			} else if (name.startsWith("wheel_f")) {
				matrices.multiply(RotationAxis.POSITIVE_X.rotation(front));
			}
			part.draw(matrices.peek(), out, light, overlay, null);
			matrices.pop();
		}
	}

	/** The bait on the door's counter, standing as dropped items stand, facing out; swinging with the door. */
	private void drawBait(CarriageEntity carriage, ChittyMesh mesh, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers,
			int light) {
		float door = carriage.getDoorOpen(tickDelta);
		for (int slot = 0; slot < 3; slot++) {
			ItemStack stack = carriage.getBait(slot);
			if (stack.isEmpty()) {
				continue;
			}
			matrices.push();
			swingDoor(mesh, door, matrices);
			Vec3d at = CarriageEntity.BAIT_AT.add((slot - 1) * CarriageEntity.BAIT_APART, 0.0, 0.0);
			matrices.translate(at.x, at.y + 0.02, at.z);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
			matrices.scale(1.2F, 1.2F, 1.2F);
			items.renderItem(stack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV, matrices, buffers, carriage.getWorld(),
					carriage.getId() * 3 + slot);
			matrices.pop();
		}
	}

	/**
	 * The disguise, thrown off: each piece flies out from where it was on her (the cloths off her sides and back, the
	 * stock off her roof), tips over outward as it falls, and lies on the ground where she was, sinking into it at last.
	 * Drawn where she was, not where she is now.
	 */
	private static void drawThrown(CarriageEntity carriage, ChittyMesh mesh, float tickDelta, MatrixStack matrices, VertexConsumer out,
			int light) {
		float t = carriage.getThrownAge(tickDelta);
		if (t >= GONE) {
			return;
		}
		Vec3d now = new Vec3d(MathHelper.lerp(tickDelta, carriage.lastRenderX, carriage.getX()),
				MathHelper.lerp(tickDelta, carriage.lastRenderY, carriage.getY()), MathHelper.lerp(tickDelta, carriage.lastRenderZ, carriage.getZ()));
		Vec3d at = carriage.getThrownAt().subtract(now);
		int overlay = OverlayTexture.DEFAULT_UV;
		matrices.push();
		matrices.translate(at.x, at.y, at.z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-carriage.getThrownYaw()));
		for (ChittyMesh.Part part : mesh.parts.values()) {
			if (!disguisePart(part.name)) {
				continue;
			}
			float px = part.pivot.x;
			float py = part.pivot.y;
			float pz = part.pivot.z;
			// Outward: off her side, off her back, or (on the roof) away from her middle.
			float dx;
			float dz;
			if (Math.abs(px) > 0.5F) {
				dx = Math.signum(px);
				dz = 0.0F;
			} else if (pz < CarriageEntity.CAGE_BACK + 0.3) {
				dx = 0.0F;
				dz = -1.0F;
			} else {
				int h = part.name.hashCode();
				float a = (h & 1023) / 1023.0F * (float) (Math.PI * 2.0);
				dx = MathHelper.cos(a);
				dz = MathHelper.sin(a);
			}
			// It flies until it is down, then lies there.
			float land = (TOSS + MathHelper.sqrt(TOSS * TOSS + 2.0F * FALL * Math.max(0.0F, py - 0.05F))) / FALL;
			float f = Math.min(t, land);
			float y = Math.max(0.05F, py + TOSS * f - 0.5F * FALL * f * f);
			if (t > LIE) {
				y -= (t - LIE) * 0.004F;
			}
			float tip = MathHelper.clamp(t / land, 0.0F, 1.0F) * (float) (Math.PI / 2.0);
			matrices.push();
			matrices.translate(px + dx * FLING * f, y, pz + dz * FLING * f);
			// Tipped outward about the level line across the way it flies (up × out).
			matrices.multiply(new Quaternionf().rotationAxis(tip, dz, 0.0F, -dx));
			matrices.multiply(part.rest);
			part.draw(matrices.peek(), out, light, overlay, null);
			matrices.pop();
		}
		matrices.pop();
	}

	/**
	 * The horse's traces, from its collar back along its sides to the ends of her splinter bar, and the reins from its
	 * bit through the terrets on its back to the driver's hands, sagging a little; in her frame, where the horse is drawn.
	 */
	private static void drawHarness(CarriageEntity carriage, ChittyMesh mesh, float tickDelta, MatrixStack matrices, VertexConsumer out,
			int light) {
		ChittyMesh.Part trace = mesh.parts.get("trace");
		ChittyMesh.Part rein = mesh.parts.get("rein");
		if (trace == null || rein == null) {
			return;
		}
		float steerFraction = carriage.getSteer(tickDelta);
		Vec3d horseAt = carriage.horseLocal(steerFraction, carriage.getHorseLift(tickDelta));
		float bodyYaw = -steerFraction * CarriageEntity.STEER_MAX;
		float steer = -bodyYaw * MathHelper.RADIANS_PER_DEGREE;
		int overlay = OverlayTexture.DEFAULT_UV;
		for (int s = -1; s <= 1; s += 2) {
			Vec3d collar = horsePoint(horseAt, bodyYaw, true, s * TRACE_FROM[0], TRACE_FROM[1], TRACE_FROM[2]);
			Vec3d side = horsePoint(horseAt, bodyYaw, false, s * TRACE_MID[0], TRACE_MID[1], TRACE_MID[2]);
			Vec3d tree = new Vec3d(s * TREE, CarriageEntity.SPLINTER.y, CarriageEntity.SPLINTER.z + 0.07).subtract(TURNTABLE)
					.rotateY(steer).add(TURNTABLE);
			strap(trace, collar, side, matrices, out, light, overlay);
			strap(trace, side, tree, matrices, out, light, overlay);
			Vec3d bit = horsePoint(horseAt, bodyYaw, true, s * BIT[0], BIT[1], BIT[2]);
			Vec3d terret = horsePoint(horseAt, bodyYaw, false, s * TERRET[0], TERRET[1], TERRET[2]);
			Vec3d hand = CarriageEntity.HANDS.add(s * 0.03, 0.0, 0.0);
			Vec3d slack = terret.add(hand).multiply(0.5).add(0.0, -0.12, 0.0);
			strap(rein, bit, terret, matrices, out, light, overlay);
			strap(rein, terret, slack, matrices, out, light, overlay);
			strap(rein, slack, hand, matrices, out, light, overlay);
		}
	}

	/**
	 * A point on the horse, in pixels in its model's head frame (its neck's pivot, carried at thirty degrees) or its body
	 * frame, where it is drawn (as LivingEntityRenderer draws it: turned to its yaw, flipped, scaled, and lifted 1.501).
	 */
	private static Vec3d horsePoint(Vec3d horseAt, float bodyYaw, boolean head, double px, double py, double pz) {
		double pitch = head ? Math.PI / 6.0 : 0.0;
		double c = Math.cos(pitch);
		double s = Math.sin(pitch);
		double y = py * c - pz * s;
		double z = py * s + pz * c;
		double mx = px / 16.0;
		double my = (y + (head ? 4.0 : 11.0)) / 16.0;
		double mz = (z + (head ? -12.0 : 5.0)) / 16.0;
		float k = CarriageHorse.SCALE;
		Vec3d v = new Vec3d(-k * mx, k * (1.501 - my), k * mz);
		return horseAt.add(v.rotateY((180.0F - bodyYaw) * MathHelper.RADIANS_PER_DEGREE));
	}

	/** A strap from one point to another: the one-block strap part, turned along it and stretched to its length. */
	private static void strap(ChittyMesh.Part part, Vec3d from, Vec3d to, MatrixStack matrices, VertexConsumer out, int light, int overlay) {
		Vec3d line = to.subtract(from);
		float length = (float) line.length();
		if (length < 1.0E-3F) {
			return;
		}
		matrices.push();
		matrices.translate(from.x, from.y, from.z);
		matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0.0F, -1.0F, 0.0F),
				new Vector3f((float) line.x / length, (float) line.y / length, (float) line.z / length)));
		matrices.scale(1.0F, length, 1.0F);
		part.draw(matrices.peek(), out, light, overlay, null);
		matrices.pop();
	}
}
