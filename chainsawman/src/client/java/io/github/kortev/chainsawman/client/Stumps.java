package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.form.Limbs;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * An arm lost in the fight, as Denji's are: what's left is a stub of the upper arm ({@link #STUB} of its length), its
 * end torn raw round the white of the bone, bleeding: gushing as it comes off, then spurting with each beat of the
 * heart and dripping all the while. Drawn on the body for everyone ({@link FormFeature}), and in your own view where
 * that hand would be ({@link FirstPersonSaws}); grown back out of it when you drink enough blood.
 */
public final class Stumps {
	/** How far your own stub is tipped back toward your eyes, in your view (radians). */
	private static final float TIP = 1.1F;
	/** How much of the arm is left, of its length. */
	public static final float STUB = 0.42F;
	/** The torn flesh (its left half) and the bone (its right). */
	private static final Identifier TEXTURE = ChainsawMan.id("textures/entity/form_stump.png");
	/** How long it gushes once off (ticks), and how often the heart pushes out a spurt after (ticks). */
	private static final int GUSH = 30;
	private static final int BEAT = 16;
	private static final DustParticleEffect MIST = new DustParticleEffect(new Vector3f(0.6F, 0.02F, 0.04F), 1.1F);
	private static final BlockStateParticleEffect SPURT = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.getDefaultState());
	private static final BlockStateParticleEffect DRIP = new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.RED_CONCRETE.getDefaultState());
	private static final Random RANDOM = Random.create();
	/** The tick each player's stumps last bled on (right, left): they're drawn every frame, they bleed every tick. */
	private static final Map<UUID, long[]> BLED = new ConcurrentHashMap<>();

	private Stumps() {
	}

	/** The arm (posed) cut down to its stub: its sleeve goes with it. */
	public static void stub(ModelPart arm) {
		arm.xScale = 1.0F;
		arm.yScale = STUB;
		arm.zScale = 1.0F;
	}

	/** Where across the arm its middle is (pixels), and half its width: a slim arm is a pixel narrower, toward the body. */
	private static float middle(boolean right, boolean slim) {
		return (right ? -1.0F : 1.0F) * (slim ? 0.5F : 1.0F);
	}

	/** The end of the stub of {@code arm}, as posed, on the body; and its blood. */
	public static void end(MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, AbstractClientPlayerEntity player,
			ModelPart arm, boolean right) {
		boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
		float sx = arm.xScale;
		float sy = arm.yScale;
		float sz = arm.zScale;
		arm.xScale = 1.0F;
		arm.yScale = 1.0F;
		arm.zScale = 1.0F;
		matrices.push();
		arm.rotate(matrices);
		arm.xScale = sx;
		arm.yScale = sy;
		arm.zScale = sz;
		torn(matrices.peek(), consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, overlay, right, slim);
		Vector3f tip = matrices.peek().getPositionMatrix().transformPosition(middle(right, slim) / 16.0F, (10.0F * STUB + 0.6F) / 16.0F, 0.0F,
				new Vector3f());
		Vector3f along = matrices.peek().getPositionMatrix().transformDirection(0.0F, 1.0F, 0.0F, new Vector3f()).normalize();
		matrices.pop();
		Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
		bleed(player, right, new Vec3d(tip.x + cam.x, tip.y + cam.y, tip.z + cam.z), new Vec3d(along.x, along.y, along.z));
	}

	/**
	 * Your own stub, in your view where the hand would be: the arm held as the game holds an empty hand up into view, and
	 * slid up along itself so its torn end is where the hand was; blood dripping off it down the screen.
	 */
	public static void firstPerson(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player, Arm arm,
			float tickDelta) {
		if (!(MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerEntityRenderer renderer)) {
			return;
		}
		PlayerEntityModel<AbstractClientPlayerEntity> model = renderer.getModel();
		boolean right = arm == Arm.RIGHT;
		boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
		ModelPart part = right ? model.rightArm : model.leftArm;
		ModelPart sleeve = right ? model.rightSleeve : model.leftSleeve;
		ModelTransform partWas = part.getTransform();
		ModelTransform sleeveWas = sleeve.getTransform();
		boolean partShown = part.visible;
		float[] partScale = {part.xScale, part.yScale, part.zScale};
		float[] sleeveScale = {sleeve.xScale, sleeve.yScale, sleeve.zScale};
		float f = right ? 1.0F : -1.0F;
		matrices.push();
		// HeldItemRenderer.renderArmHoldingItem's, at rest.
		matrices.translate(f * 0.64000005F, -0.6F, -0.71999997F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * 45.0F));
		matrices.translate(f * -1.0F, 3.6F, 3.5F);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f * 120.0F));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(200.0F));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * -135.0F));
		matrices.translate(f * 5.6F, 0.0F, 0.0F);
		part.resetTransform();
		// Slid up along itself: the end where the hand was.
		part.pivotY += 10.0F * (1.0F - STUB);
		// Tipped back toward you: the torn end and the bone in sight.
		part.pitch = -TIP;
		part.visible = true;
		stub(part);
		sleeve.copyTransform(part);
		Identifier skin = player.getSkinTextures().texture();
		part.render(matrices, consumers.getBuffer(RenderLayer.getEntitySolid(skin)), light, OverlayTexture.DEFAULT_UV);
		if (sleeve.visible) {
			sleeve.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(skin)), light, OverlayTexture.DEFAULT_UV);
		}
		part.yScale = 1.0F;
		matrices.push();
		part.rotate(matrices);
		torn(matrices.peek(), consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, OverlayTexture.DEFAULT_UV, right, slim);
		Vector3f tip = matrices.peek().getPositionMatrix().transformPosition(middle(right, slim) / 16.0F, (10.0F * STUB + 0.9F) / 16.0F, 0.0F,
				new Vector3f());
		matrices.pop();
		matrices.pop();
		part.setTransform(partWas);
		sleeve.setTransform(sleeveWas);
		part.visible = partShown;
		part.xScale = partScale[0];
		part.yScale = partScale[1];
		part.zScale = partScale[2];
		sleeve.xScale = sleeveScale[0];
		sleeve.yScale = sleeveScale[1];
		sleeve.zScale = sleeveScale[2];
		long since = player.getWorld().getTime() - ClientCombat.lostAt(player, right ? Limbs.RIGHT : Limbs.LEFT);
		drops(consumers, tip, player.age + tickDelta, since >= 0 && since < GUSH, light);
	}

	/** Drops running off the end at {@code tip} (already in the view's own space) and falling down the screen. */
	private static void drops(VertexConsumerProvider consumers, Vector3f tip, float time, boolean gushing, int light) {
		VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
		int count = gushing ? 6 : 3;
		float period = gushing ? 6.0F : 14.0F;
		for (int i = 0; i < count; i++) {
			float t = (time / period + i / (float) count) % 1.0F;
			float fall = t * t * 0.45F;
			float x = tip.x + ((i * 7) % 5 - 2) * 0.004F;
			float y = tip.y - fall;
			// In front of the stub, which is nearer than its end.
			float z = tip.z * 0.8F;
			float s = 0.009F * (1.0F - 0.4F * t) * (gushing ? 1.3F : 1.0F);
			int alpha = (int) (255 * (1.0F - t * t));
			int colour = alpha << 24 | 0xFFFFFF;
			// A dark red pixel of the flesh, stretched a little as it falls.
			float u = 1.5F / 16.0F;
			float v = 2.5F / 8.0F;
			out.vertex(x - s, y - s * 1.6F, z).color(colour).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0.0F, 0.0F, 1.0F);
			out.vertex(x + s, y - s * 1.6F, z).color(colour).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0.0F, 0.0F, 1.0F);
			out.vertex(x + s, y + s, z).color(colour).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0.0F, 0.0F, 1.0F);
			out.vertex(x - s, y + s, z).color(colour).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0.0F, 0.0F, 1.0F);
		}
	}

	/** Bleeding from {@code at}, the end, its arm running along {@code along}: once a tick, however often it's drawn. */
	private static void bleed(AbstractClientPlayerEntity player, boolean right, Vec3d at, Vec3d along) {
		World world = player.getWorld();
		long now = world.getTime();
		long[] last = BLED.computeIfAbsent(player.getUuid(), k -> new long[] {-1L, -1L});
		int i = right ? 0 : 1;
		if (last[i] == now) {
			return;
		}
		last[i] = now;
		long since = now - ClientCombat.lostAt(player, right ? Limbs.RIGHT : Limbs.LEFT);
		boolean gushing = since >= 0 && since < GUSH;
		boolean beat = (now + (right ? 0 : BEAT / 2)) % BEAT == 0;
		if (gushing || beat) {
			for (int k = 0; k < (gushing ? 2 : 5); k++) {
				world.addParticle(SPURT, at.x, at.y, at.z, along.x * 3.0 + (RANDOM.nextDouble() - 0.5), along.y * 3.0 + 0.6, along.z * 3.0
						+ (RANDOM.nextDouble() - 0.5));
			}
			world.addParticle(MIST, at.x, at.y, at.z, along.x, along.y, along.z);
		}
		if (gushing || now % 4 == 0) {
			world.addParticle(DRIP, at.x + (RANDOM.nextDouble() - 0.5) * 0.08, at.y, at.z + (RANDOM.nextDouble() - 0.5) * 0.08, 0.0, 0.0, 0.0);
		}
	}

	/**
	 * The torn end of a stub {@code 10 * STUB} pixels down the arm (in the arm's own frame, unscaled): a slab of raw
	 * flesh across it, ragged tatters hanging off it, and the bone standing out of the middle.
	 */
	private static void torn(MatrixStack.Entry e, VertexConsumer out, int light, int overlay, boolean right, boolean slim) {
		float c = middle(right, slim);
		float w = slim ? 1.5F : 2.0F;
		float end = 10.0F * STUB;
		box(e, out, light, overlay, false, c - w + 0.15F, end - 0.3F, -1.85F, c + w - 0.15F, end + 0.35F, 1.85F);
		box(e, out, light, overlay, false, c - w + 0.3F, end + 0.35F, -1.6F, c - w + 1.3F, end + 0.95F, -0.6F);
		box(e, out, light, overlay, false, c + w - 1.2F, end + 0.35F, 0.4F, c + w - 0.3F, end + 0.75F, 1.5F);
		box(e, out, light, overlay, false, c - 0.3F, end + 0.35F, 1.0F, c + 0.6F, end + 0.6F, 1.7F);
		box(e, out, light, overlay, false, c + 0.3F, end + 0.35F, -1.5F, c + 1.1F, end + 0.55F, -0.8F);
		box(e, out, light, overlay, true, c - 0.6F, end - 0.2F, -0.6F, c + 0.6F, end + 1.25F, 0.6F);
	}

	/** A box from one corner to the other (pixels), every face the flesh's half of the texture or the bone's. */
	private static void box(MatrixStack.Entry e, VertexConsumer out, int light, int overlay, boolean bone, float x0, float y0, float z0, float x1,
			float y1, float z1) {
		float u0 = bone ? 0.5F : 0.0F;
		float u1 = bone ? 1.0F : 0.5F;
		x0 /= 16.0F;
		y0 /= 16.0F;
		z0 /= 16.0F;
		x1 /= 16.0F;
		y1 /= 16.0F;
		z1 /= 16.0F;
		face(e, out, light, overlay, u0, u1, 0.0F, -1.0F, 0.0F, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		face(e, out, light, overlay, u0, u1, 0.0F, 1.0F, 0.0F, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
		face(e, out, light, overlay, u0, u1, 0.0F, 0.0F, -1.0F, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		face(e, out, light, overlay, u0, u1, 0.0F, 0.0F, 1.0F, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
		face(e, out, light, overlay, u0, u1, -1.0F, 0.0F, 0.0F, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
		face(e, out, light, overlay, u0, u1, 1.0F, 0.0F, 0.0F, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	private static void face(MatrixStack.Entry e, VertexConsumer out, int light, int overlay, float u0, float u1, float nx, float ny, float nz,
			float... p) {
		float[][] uv = {{u0, 0.0F}, {u0, 1.0F}, {u1, 1.0F}, {u1, 0.0F}};
		for (int i = 0; i < 4; i++) {
			out.vertex(e, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).color(0xFFFFFFFF).texture(uv[i][0], uv[i][1]).overlay(overlay).light(light)
					.normal(e, nx, ny, nz);
		}
	}

	static void clear() {
		BLED.clear();
	}
}
