package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.client.mixin.AnimalModelInvoker;
import io.github.kortev.chainsawman.client.mixin.ModelPartAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.render.entity.model.CompositeEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

/**
 * A mob the saws kill is cut in two: its body (gone from the world on the server) is kept here a moment and drawn as
 * two halves coming apart: the top sliced off sideways, tipping and falling to the ground, the legs folding the other
 * way a beat later, blood pouring up out of the cut; then a puff, and gone. The halves are the model's own parts, split
 * where they hang from (above the middle, the top half; below it, the legs), so it works for most of the game's mobs;
 * one in a single piece just falls.
 */
public final class CutDown {
	/** How long the halves lie there (ticks). */
	private static final int LASTS = 50;
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.62F, 0.02F, 0.04F), 1.2F);
	private static final DustParticleEffect DARK_BLOOD = new DustParticleEffect(new Vector3f(0.35F, 0.0F, 0.02F), 1.4F);
	private static final BlockStateParticleEffect GORE = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
	private static final Random RANDOM = Random.create();

	/** A body cut down: when, and which way its top half goes (1 or -1, across it). */
	private record Cut(LivingEntity body, long at, float side) {
	}

	private static final List<Cut> CUTS = new CopyOnWriteArrayList<>();
	/** The cut being drawn now, and how far in (ticks); null when it's anything else. */
	private static Cut drawing;
	private static float age;

	private CutDown() {
	}

	/** {@code killer}'s saws killed the mob {@code id}: keep its body, to come apart. */
	public static void killed(ClientWorld world, int id, long now) {
		Entity entity = world.getEntityById(id);
		// (Not a real player, who comes back; a film's stand-in, only here, can be cut down.)
		if (!(entity instanceof LivingEntity body) || entity instanceof PlayerEntity && entity.getId() >= 0) {
			return;
		}
		// Not red, not toppling as the game's own dead do: it comes apart instead.
		body.deathTime = 0;
		body.hurtTime = 0;
		CUTS.add(new Cut(body, now, RANDOM.nextBoolean() ? 1.0F : -1.0F));
	}

	/** Whether a cut body is being drawn now (its renderer's model draw comes to {@link #halves}). */
	public static boolean drawing() {
		return drawing != null;
	}

	/** Draws the bodies, as the entities are drawn. */
	public static void render(WorldRenderContext context) {
		ClientWorld world = context.world();
		VertexConsumerProvider consumers = context.consumers();
		if (CUTS.isEmpty() || world == null || consumers == null) {
			return;
		}
		float tickDelta = context.tickCounter().getTickDelta(false);
		Vec3d cam = context.camera().getPos();
		MatrixStack matrices = context.matrixStack() != null ? context.matrixStack() : new MatrixStack();
		for (Cut cut : CUTS) {
			float t = world.getTime() - cut.at() + tickDelta;
			if (t < 0.0F || t > LASTS) {
				continue;
			}
			LivingEntity body = cut.body();
			int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(body.getPos().add(0.0, 0.5, 0.0)));
			drawing = cut;
			age = t;
			try {
				MinecraftClient.getInstance().getEntityRenderDispatcher().render(body, body.getX() - cam.x, body.getY() - cam.y, body.getZ() - cam.z,
						body.getYaw(), 1.0F, matrices, consumers, light);
			} catch (RuntimeException e) {
				// A mob whose renderer won't draw it out of the world: it just goes.
				CUTS.remove(cut);
			} finally {
				drawing = null;
			}
		}
	}

	/** Every tick: blood pouring out of the cuts; the old bodies gone in a puff. */
	static void tick(ClientWorld world) {
		long now = world.getTime();
		for (Cut cut : CUTS) {
			long t = now - cut.at();
			LivingEntity body = cut.body();
			Vec3d at = body.getPos().add(0.0, body.getHeight() * 0.55, 0.0);
			if (t < 14) {
				for (int i = 0; i < 2; i++) {
					world.addParticle(i % 3 == 0 ? DARK_BLOOD : BLOOD, at.x, at.y, at.z, (RANDOM.nextDouble() - 0.5) * 0.25,
							0.3 + RANDOM.nextDouble() * 0.4, (RANDOM.nextDouble() - 0.5) * 0.25);
				}
				if (t % 5 == 0) {
					world.addParticle(GORE, at.x, at.y, at.z, (RANDOM.nextDouble() - 0.5) * 0.3, 0.25, (RANDOM.nextDouble() - 0.5) * 0.3);
				}
			}
			if (t > LASTS) {
				for (int i = 0; i < 12; i++) {
					world.addParticle(ParticleTypes.POOF, body.getX() + (RANDOM.nextDouble() - 0.5) * body.getWidth() * 2.0, body.getY() + 0.1,
							body.getZ() + (RANDOM.nextDouble() - 0.5) * body.getWidth() * 2.0, 0.0, 0.03, 0.0);
				}
				CUTS.remove(cut);
			}
		}
	}

	static void clear() {
		CUTS.clear();
	}

	/**
	 * Draws the body being drawn as its two halves ({@code draw} draws the model as the stack and its parts' visibility
	 * stand), in the model's own space: y down, the feet at 1.5, across it x.
	 */
	public static void halves(EntityModel<?> model, MatrixStack matrices, Runnable draw) {
		Cut cut = drawing;
		float t = age;
		List<ModelPart> parts = parts(model);
		float top = Float.MAX_VALUE;
		float bottom = -Float.MAX_VALUE;
		for (ModelPart part : parts) {
			top = Math.min(top, part.pivotY);
			bottom = Math.max(bottom, part.pivotY);
		}
		float side = cut.side();
		float feet = 1.501F;
		// The legs (or the one piece) folding over, a beat after the cut.
		float fold = easeIn((t - 6.0F) / 10.0F);
		matrices.push();
		matrices.translate(0.0F, feet, 0.0F);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-side * 80.0F * fold));
		matrices.translate(0.0F, -feet, 0.0F);
		if (parts.size() < 2 || bottom - top < 2.0F) {
			draw.run();
			matrices.pop();
			return;
		}
		float middle = (top + bottom) * 0.5F;
		boolean[] was = new boolean[parts.size()];
		for (int i = 0; i < parts.size(); i++) {
			was[i] = parts.get(i).visible;
			parts.get(i).visible = was[i] && parts.get(i).pivotY >= middle;
		}
		draw.run();
		matrices.pop();
		// The top: sliced off sideways, then tipping over and falling to the ground.
		float cutAt = bottom / 16.0F;
		float slide = ease(t / 3.0F) * 0.22F;
		float tip = easeIn((t - 2.0F) / 9.0F);
		matrices.push();
		matrices.translate(side * slide, (feet - cutAt) * 0.85F * tip, 0.0F);
		matrices.translate(0.0F, cutAt, 0.0F);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 85.0F * tip));
		matrices.translate(0.0F, -cutAt, 0.0F);
		for (int i = 0; i < parts.size(); i++) {
			parts.get(i).visible = was[i] && parts.get(i).pivotY < middle;
		}
		draw.run();
		matrices.pop();
		for (int i = 0; i < parts.size(); i++) {
			parts.get(i).visible = was[i];
		}
	}

	/** A model's own parts, as far as they can be found: a mob's head and body parts, or the pieces under its root. */
	private static List<ModelPart> parts(EntityModel<?> model) {
		List<ModelPart> parts = new ArrayList<>();
		if (model instanceof AnimalModel<?> animal) {
			((AnimalModelInvoker) animal).chainsawman$headParts().forEach(parts::add);
			((AnimalModelInvoker) animal).chainsawman$bodyParts().forEach(parts::add);
		} else if (model instanceof SinglePartEntityModel<?> single) {
			ModelPart root = single.getPart();
			Collection<ModelPart> under = ((ModelPartAccessor) (Object) root).chainsawman$children().values();
			// Down through any one-part wrappers to where it branches.
			while (under.size() == 1) {
				under = ((ModelPartAccessor) (Object) under.iterator().next()).chainsawman$children().values();
			}
			parts.addAll(under);
		} else if (model instanceof CompositeEntityModel<?> composite) {
			composite.getParts().forEach(parts::add);
		}
		return parts;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	private static float easeIn(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x;
	}
}
