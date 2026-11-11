package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.ShaftArmEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One of the Eternity Devil's arms ({@link ShaftArmEntity}): out of the room's wall through a collar of flesh, a chain of the room model's
 * limb pieces ending in its big hand, every pixel 1/16 block (nothing scaled), in the room's one light. Its tell is in the floor's veins
 * ({@link ArenaRenderer}); here it is the arm itself:
 * <ul>
 * <li>the slam: the arm rears up out of the wall, the hand high, then comes down along the lane; stuck after a miss it lies there, bleeding and
 * flushed (tear it off); torn off it drops and sinks; one that hit pulls straight back into the wall;</li>
 * <li>the grab: the hand rises open at the wall, then the whole arm sweeps low across the floor; closed on whoever it caught; stuck lying
 * open after a miss;</li>
 * <li>rising up the shaft (the ledges): the shaft mesh's own grab arm.</li>
 * </ul>
 */
public class ShaftArmRenderer extends EntityRenderer<ShaftArmEntity> {
	/** The shaft mesh's grab parts' rest geometry (rising): the root's base and the height of its top. */
	private static final float ROOT_BASE = 0.7F;
	private static final float ROOT_TOP = 2.9F;
	/** A limb piece's length: pieces are laid end to end at this length (never stretched), the last one running into the hand. */
	static final float SEG = 0.5F;
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.55F, 0.02F, 0.03F), 1.0F);

	public ShaftArmRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ShaftArmEntity entity) {
		return Identifier.of("chainsawman", "textures/entity/eternity_devil.png");
	}

	@Override
	public boolean shouldRender(ShaftArmEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		return true;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	@Override
	public void render(ShaftArmEntity arm, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(arm.getWorld() instanceof ClientWorld world)) {
			return;
		}
		int mode = arm.mode();
		if (mode == ShaftArmEntity.SLAM) {
			slam(arm, world, delta, matrices);
		} else if (mode == ShaftArmEntity.GRAB) {
			grab(arm, world, delta, matrices);
		} else {
			rise(arm, world, delta, matrices, light);
		}
	}

	/** Limb pieces laid from {@code a} to {@code b} (the entity's frame), and the big hand at {@code b} pointing on ({@code palm}: which way its palm faces). */
	static void chain(Matrix4f base, Vec3d a, Vec3d b, String piece, boolean hand, Vec3d palm, float closed, FleshMesh.Look look) {
		FleshMesh mesh = FleshMesh.get("room");
		FleshMesh.Part seg = mesh.byName.get(piece);
		Vec3d d = b.subtract(a);
		double len = d.length();
		if (seg == null || len < 0.05) {
			return;
		}
		Vec3d u = d.multiply(1.0 / len);
		Quaternionf q = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) u.x, (float) u.y, (float) u.z);
		int n = Math.max(1, (int) Math.ceil(len / SEG - 0.3));
		for (int i = 0; i < n; i++) {
			Vec3d p = a.add(u.multiply(Math.min(i * SEG, Math.max(0.0, len - SEG * 0.7))));
			mesh.drawPart(seg, new Matrix4f(base).translate((float) p.x, (float) p.y, (float) p.z).rotate(q), look, base);
		}
		FleshMesh.Part h = mesh.byName.get("limb_hand");
		if (hand && h != null) {
			// The hand's palm (its +x) turned toward {@code palm}, its fingers on along the limb.
			Vec3d side = palm.subtract(u.multiply(palm.dotProduct(u)));
			Matrix4f hm = new Matrix4f(base).translate((float) b.x, (float) b.y, (float) b.z).rotate(q);
			if (side.lengthSquared() > 1.0e-4) {
				Vector3f px = q.transform(new Vector3f(1.0F, 0.0F, 0.0F));
				Vector3f pz = q.transform(new Vector3f(0.0F, 0.0F, 1.0F));
				Vec3d s = side.normalize();
				hm.rotateY((float) Math.atan2(-(s.x * pz.x + s.y * pz.y + s.z * pz.z), s.x * px.x + s.y * px.y + s.z * px.z));
			}
			if (closed > 0.0F) {
				hm.rotateZ(closed);
			}
			mesh.drawPart(h, hm, look, base);
		}
	}

	/** The collar of flesh round where a limb comes out of the room at {@code at} (the entity's frame), out along {@code out}. */
	static void collar(Matrix4f base, Vec3d at, Vec3d out, String name, FleshMesh.Look look) {
		FleshMesh mesh = FleshMesh.get("room");
		FleshMesh.Part c = mesh.byName.get(name);
		if (c == null) {
			return;
		}
		Quaternionf q = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) out.x, (float) out.y, (float) out.z);
		mesh.drawPart(c, new Matrix4f(base).translate((float) at.x, (float) at.y, (float) at.z).rotate(q), look, base);
	}

	/** The limb's look: the room's light, flushed red and pulsing while it is stuck (tear it off now), redder as it is torn. */
	private static FleshMesh.Look limbLook(ShaftArmEntity arm, ClientWorld world, float now) {
		FleshMesh.Look look = ArenaRenderer.lookNear(world, arm, now);
		if (arm.outcome() == ShaftArmEntity.CUT) {
			look.hurt = 0.6F;
		} else if (arm.stuck()) {
			look.hurt = 0.1F + 0.08F * MathHelper.sin(now * 0.5F);
		}
		return look;
	}

	private void slam(ShaftArmEntity arm, ClientWorld world, float delta, MatrixStack matrices) {
		float t = arm.age(delta);
		float now = world.getTime() + delta;
		int out = arm.outcome();
		float since = out == ShaftArmEntity.PENDING ? -1.0F : (float) (world.getTime() - arm.outAt()) + delta;
		Vec3d d = arm.dir();
		Vec3d up = new Vec3d(0.0, 1.0, 0.0);
		double len = arm.len();
		Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
		FleshMesh.Look look = limbLook(arm, world, now);
		Vec3d root = new Vec3d(0.0, 0.5, 0.0).add(d.multiply(-0.3));
		collar(base, new Vec3d(0.0, 0.5, 0.0), d, "collar", look);
		if (out == ShaftArmEntity.PENDING && t < ShaftArmEntity.SLAM_TELL) {
			// Rearing up out of the wall: the elbow out, the hand climbing high over the lane.
			float k = ease(t / 14.0F);
			Vec3d elbow = d.multiply(0.6 + 0.6 * k).add(0.0, 0.5 + 1.2 * k, 0.0);
			Vec3d hand = d.multiply(0.9 + 0.8 * k).add(0.0, 0.6 + 2.8 * k + 0.1 * Math.sin(now * 0.8), 0.0);
			chain(base, root, elbow, "limb_segment", false, up, 0.0F, look);
			chain(base, elbow, hand, "limb_segment", true, d, 0.0F, look);
			return;
		}
		double reach = len;
		double lift = 0.0;
		double sink = 0.0;
		if (out == ShaftArmEntity.PENDING || since >= 0.0F && since < 1.0F && out != ShaftArmEntity.CUT) {
			// Down along the lane.
			float s = t - ShaftArmEntity.SLAM_TELL;
			lift = 3.4 * (1.0 - ease(s / ShaftArmEntity.SLAM_FALL));
		} else if (out == ShaftArmEntity.CUT) {
			sink = 1.4 * ease(since / ShaftArmEntity.BACK);
		} else if (out == ShaftArmEntity.HIT) {
			reach = len * (1.0 - ease(since / ShaftArmEntity.BACK));
		} else if (since > ShaftArmEntity.LIE) {
			reach = len * (1.0 - ease((since - ShaftArmEntity.LIE) / ShaftArmEntity.BACK));
		}
		if (reach < 0.3) {
			return;
		}
		int n = 4;
		Vec3d prev = root;
		for (int i = 1; i <= n; i++) {
			double f = (double) i / n;
			Vec3d p = d.multiply((reach - 1.4) * f).add(0.0, 0.32 - sink + lift * Math.sin(Math.PI * 0.5 * f), 0.0);
			if (out == ShaftArmEntity.CUT) {
				// Torn off: the piece out beyond the root drops; the root goes back to a stump in the wall.
				p = p.add(0.0, -0.3 * f, 0.0);
			}
			chain(base, prev, p, "limb_segment", i == n, up.multiply(-1.0), 0.0F, look);
			prev = p;
		}
		if (arm.stuck() && world.random.nextInt(5) == 0) {
			Vec3d at = arm.getLerpedPos(delta).add(d.multiply(world.random.nextDouble() * (reach - 1.0))).add(0.0, 0.6, 0.0);
			world.addParticle(BLOOD, at.x, at.y, at.z, 0.0, -0.05, 0.0);
		}
	}

	private void grab(ShaftArmEntity arm, ClientWorld world, float delta, MatrixStack matrices) {
		float t = arm.age(delta);
		float now = world.getTime() + delta;
		int out = arm.outcome();
		float since = out == ShaftArmEntity.PENDING ? -1.0F : (float) (world.getTime() - arm.outAt()) + delta;
		Vec3d in = arm.dir();
		Vec3d pos = arm.getLerpedPos(delta);
		Vec3d hand = arm.handAt(t).subtract(arm.getPos()).add(arm.getPos().subtract(pos));
		boolean gone = out == ShaftArmEntity.CUT && since > ShaftArmEntity.BACK || out == ShaftArmEntity.MISSED && since > ShaftArmEntity.LIE + ShaftArmEntity.BACK
				|| out == ShaftArmEntity.HIT && !arm.holding() && since > 8 + ShaftArmEntity.BACK;
		if (gone) {
			return;
		}
		double sink = 0.0;
		double pull = 1.0;
		if (out == ShaftArmEntity.CUT) {
			sink = 1.4 * ease(since / ShaftArmEntity.BACK);
		} else if (out == ShaftArmEntity.MISSED && since > ShaftArmEntity.LIE) {
			pull = 1.0 - ease((since - ShaftArmEntity.LIE) / ShaftArmEntity.BACK);
		} else if (out == ShaftArmEntity.HIT && !arm.holding() && since > 0.0F) {
			pull = 1.0 - ease(since / (8.0F + ShaftArmEntity.BACK));
		}
		Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
		FleshMesh.Look look = limbLook(arm, world, now);
		Vec3d root = new Vec3d(0.0, 0.55, 0.0);
		collar(base, root, in, "collar", look);
		Vec3d tip = root.add(hand.subtract(root).multiply(pull)).add(0.0, -sink, 0.0);
		Vec3d wrist = tip.subtract(tip.subtract(root).normalize().multiply(1.2));
		Vec3d palm = arm.holding() ? new Vec3d(0, 1, 0) : t < ShaftArmEntity.GRAB_TELL && out == ShaftArmEntity.PENDING ? in : new Vec3d(0, -1, 0);
		Vec3d elbow = root.add(wrist.subtract(root).multiply(0.5)).add(0.0, 0.35, 0.0);
		chain(base, root.add(in.multiply(-0.3)), elbow, "limb_segment", false, palm, 0.0F, look);
		chain(base, elbow, wrist, "limb_segment", true, palm, arm.holding() ? 0.9F : 0.0F, look);
		if (arm.stuck() && world.random.nextInt(5) == 0) {
			Vec3d at = pos.add(wrist.multiply(world.random.nextDouble()));
			world.addParticle(BLOOD, at.x, at.y + 0.2, at.z, 0.0, -0.05, 0.0);
		}
	}

	private void rise(ShaftArmEntity arm, ClientWorld world, float delta, MatrixStack matrices, int light) {
		float now = world.getTime() + delta;
		FleshMesh mesh = FleshMesh.get("shaft");
		int k = Math.floorMod(arm.getId(), 4);
		FleshMesh.Part root = mesh.byName.get("grab_" + k + "_root");
		FleshMesh.Part hand = mesh.byName.get("grab_" + k + "_hand");
		FleshMesh.Part seg = mesh.byName.get("grab_segment");
		if (root == null || hand == null || seg == null) {
			return;
		}
		var top = hand.json.getAsJsonArray("root_top");
		Vector3f rt = new Vector3f(top.get(0).getAsFloat(), 0.0F, top.get(2).getAsFloat());
		float height = Math.max(0.3F, arm.tip() - (float) arm.getY());
		float rootH = ROOT_TOP - ROOT_BASE;
		float baseY = height >= rootH ? 0.0F : height - rootH;
		float reach = Math.max(0.0F, height - rootH);
		Matrix4f model = new Matrix4f(matrices.peek().getPositionMatrix()).translate(-rt.x, baseY - ROOT_BASE, -rt.z);
		FleshMesh.Look look = ArenaRenderer.lookNear(world, arm, now);
		Matrix4f m = new Matrix4f(model);
		FleshMesh.pose(root.anim, root.pivot, now, m);
		mesh.drawPart(root, m, look, model);
		// (Pieces end to end at their own length, the last one into the hand: never stretched.)
		int n = (int) Math.ceil(reach / SEG);
		for (int i = 0; i < n; i++) {
			float y = Math.min(i * SEG, Math.max(0.0F, reach - SEG));
			mesh.drawPart(seg, new Matrix4f(model).translate(rt.x, ROOT_TOP + y, rt.z), look, model);
		}
		Matrix4f h = new Matrix4f(model).translate(0.0F, reach, 0.0F);
		if (arm.holding()) {
			h.translate(rt.x, ROOT_TOP, rt.z).rotateX(0.35F).translate(-rt.x, -ROOT_TOP, -rt.z);
		}
		mesh.drawPart(hand, h, look, model);
	}
}
