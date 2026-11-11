package io.github.kortev.chainsawman.client.devil;

import io.github.kortev.chainsawman.client.FleshMesh;
import io.github.kortev.chainsawman.devil.LeechDevilEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Leech Devil ({@link LeechDevilEntity}) from its two baked meshes ({@code tools/leech_devil_3d.py}): the body (breathing), the head on
 * the neck's turn (its face finds you late, by sound) with its jaw and its six locks of hair swaying; four legs, each a thigh and a shin by a
 * two-bone solve ending in a sucker foot that steps ({@link LeechBody}); two tentacle arms drawn as a chain of segments along a curve, each
 * ending in a sucker; the tongue as a chain of segments to its point. Every sucker opens and shuts out of time with the others. In a move
 * the pose is the move's: rearing with the mouth gaping and the tongue lancing out; an arm coiled back, whipping out, stuck and drinking
 * (blood pulsing up it), torn loose and flailing, hanging limp without its sucker; low and pawing, charging, skidding; sagging and folding as
 * it dies, the suckers slowing to a stop.
 */
public class LeechDevilRenderer extends EntityRenderer<LeechDevilEntity> {
	static final Identifier TEXTURE = Identifier.of("chainsawman", "textures/item/leech_sucker.png");
	/** The measures of tools/leech_devil_3d.py. */
	static final float THIGH = 1.20F;
	static final float SHIN = 1.15F;
	static final float TSEG = 0.40F;
	static final float TONGUE_SEG = 0.50F;
	static final float ANKLE_UP = 0.17F;
	static final Vector3f[] HIPS = {new Vector3f(0.72F, 1.20F, 1.00F), new Vector3f(-0.72F, 1.20F, 1.00F), new Vector3f(0.82F, 1.38F, -1.50F),
			new Vector3f(-0.82F, 1.38F, -1.50F)};
	static final Vector3f[] SHOULDERS = {new Vector3f(0.66F, 1.50F, 1.62F), new Vector3f(-0.66F, 1.50F, 1.62F)};
	static final Vector3f NECK = new Vector3f(0.0F, 1.70F, 1.40F);
	static final Vector3f JAW_PIVOT = new Vector3f(0.0F, 1.42F, 1.48F);
	static final Vector3f CROWN = new Vector3f(0.0F, 2.36F, 1.90F);
	/** Where the tongue comes from (the head's frame): its root, behind the teeth. */
	static final Vector3f TONGUE_ROOT = new Vector3f(0.0F, 1.34F, 2.45F);
	/** How many segments make an arm. */
	static final int ARM_SEGS = 12;
	static final String[] HAIR = {"hair_bangs", "hair_fl", "hair_fr", "hair_l", "hair_r", "hair_back"};

	public LeechDevilRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.shadowRadius = 1.7F;
		this.shadowOpacity = 0.65F;
	}

	@Override
	public Identifier getTexture(LeechDevilEntity entity) {
		return TEXTURE;
	}

	@Override
	public boolean shouldRender(LeechDevilEntity entity, Frustum frustum, double x, double y, double z) {
		return entity.squaredDistanceTo(x, y, z) < 96.0 * 96.0;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	private static float ramp(float t, float a, float b) {
		return ease((t - a) / (b - a));
	}

	private static Vector3f look(float yaw, float pitch) {
		float y = yaw * MathHelper.RADIANS_PER_DEGREE;
		float p = pitch * MathHelper.RADIANS_PER_DEGREE;
		return new Vector3f(-MathHelper.sin(y) * MathHelper.cos(p), MathHelper.sin(p), MathHelper.cos(y) * MathHelper.cos(p));
	}

	/** The body's pose at one moment. */
	private static final class Pose {
		float dy;
		float pitch;
		float roll;
		float jaw = 0.05F;
		float shake;
	}

	private static Pose pose(LeechDevilEntity e, int state, float t, float now, float dying) {
		Pose p = new Pose();
		switch (state) {
			case LeechDevilEntity.TONGUE -> {
				float rear = ramp(t, 0.0F, LeechDevilEntity.TONGUE_TELL - 2.0F);
				float out = ramp(t, LeechDevilEntity.TONGUE_TELL, LeechDevilEntity.TONGUE_TELL + 2.0F);
				float back = ramp(t, LeechDevilEntity.TONGUE_HIT + LeechDevilEntity.TONGUE_HOLD, LeechDevilEntity.TONGUE_END);
				p.pitch = -0.26F * rear * (1.0F - out) + 0.06F * out * (1.0F - back);
				p.dy = 0.3F * rear * (1.0F - out);
				p.jaw = 0.06F + 0.62F * rear * (1.0F - back);
				p.shake = 0.018F * rear * (1.0F - out);
			}
			case LeechDevilEntity.LATCH -> {
				float coil = ramp(t, 0.0F, LeechDevilEntity.LATCH_TELL);
				float done = ramp(t, LeechDevilEntity.LATCH_TELL + LeechDevilEntity.LATCH_OUT, LeechDevilEntity.LATCH_END);
				p.jaw = 0.06F + 0.18F * coil * (1.0F - done);
				p.pitch = -0.06F * coil * (1.0F - done);
				p.shake = 0.008F * coil * (1.0F - ramp(t, LeechDevilEntity.LATCH_TELL, LeechDevilEntity.LATCH_TELL + 2.0F));
			}
			case LeechDevilEntity.LATCHED -> {
				float pulse = (t % LeechDevilEntity.LATCH_PULSE) / LeechDevilEntity.LATCH_PULSE;
				p.jaw = 0.18F + 0.1F * MathHelper.sin(pulse * MathHelper.TAU);
				p.dy = 0.03F * MathHelper.sin(pulse * MathHelper.TAU);
			}
			case LeechDevilEntity.RECOIL -> {
				float k = 1.0F - ramp(t, 8.0F, LeechDevilEntity.RECOIL_TICKS);
				p.jaw = (0.72F + 0.06F * MathHelper.sin(now * 3.1F)) * k;
				p.pitch = -0.3F * ramp(t, 0.0F, 4.0F) * k;
				p.dy = 0.22F * k;
				p.shake = 0.035F * k;
			}
			case LeechDevilEntity.TRAMPLE_TELL -> {
				float low = ramp(t, 0.0F, 8.0F);
				p.dy = -0.45F * low;
				p.pitch = 0.13F * low;
				p.jaw = 0.12F + 0.05F * MathHelper.sin(now * 0.8F);
				p.shake = 0.012F * low;
			}
			case LeechDevilEntity.TRAMPLE -> {
				p.dy = -0.35F + 0.14F * Math.abs(MathHelper.sin(now * 1.1F));
				p.pitch = 0.1F + 0.04F * MathHelper.sin(now * 2.2F);
				p.jaw = 0.3F;
			}
			case LeechDevilEntity.SKID -> {
				float k = 1.0F - ramp(t, 8.0F, LeechDevilEntity.SKID_TICKS);
				p.dy = -0.35F * k;
				p.pitch = -0.12F * ramp(t, 0.0F, 4.0F) * k;
				p.jaw = 0.25F * k + 0.05F;
			}
			default -> {
			}
		}
		if (dying > 0.0F) {
			float sag = ease(dying * 1.3F);
			p.dy = MathHelper.lerp(sag, p.dy, -0.78F);
			p.pitch = MathHelper.lerp(sag, p.pitch, 0.1F);
			p.roll = 0.12F * sag;
			p.jaw = MathHelper.lerp(sag, p.jaw, 0.42F);
			p.shake = 0.02F * (1.0F - sag);
		}
		return p;
	}

	@Override
	public void render(LeechDevilEntity e, float yawIn, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(e.getWorld() instanceof ClientWorld world)) {
			return;
		}
		if (!(e.clientState instanceof LeechBody)) {
			e.clientState = new LeechBody();
		}
		LeechBody b = (LeechBody) e.clientState;
		float now = world.getTime() + delta;
		Vec3d pos = e.getLerpedPos(delta);
		int state = e.state();
		float t = e.stateTime(delta);
		boolean dead = e.isDead();
		float dying = dead ? MathHelper.clamp((e.deathTime + delta) / LeechDevilEntity.DEATH_TICKS, 0.0F, 1.0F) : 0.0F;
		boolean walking = state == LeechDevilEntity.WALK && !dead;
		float want = walking ? MathHelper.lerpAngleDegrees(delta, e.prevBodyYaw, e.bodyYaw) : e.aim();
		if (!b.ready || Float.isNaN(b.lastNow) || now - b.lastNow > 10.0F || now < b.lastNow) {
			b.reset(world, pos, want);
		}
		float dt = Float.isNaN(b.lastNow) ? 0.0F : Math.max(0.0F, now - b.lastNow);
		float turn = state == LeechDevilEntity.TRAMPLE ? 30.0F : 12.0F;
		b.yaw += MathHelper.clamp(MathHelper.wrapDegrees(want - b.yaw), -turn * dt, turn * dt);
		if (state != b.lastState) {
			if (state == LeechDevilEntity.WALK && b.lastState >= 0) {
				b.plantWhereDrawn(world, pos);
			}
			if (state == LeechDevilEntity.RECOIL && b.lastState == LeechDevilEntity.LATCHED && b.lastStuckAt != null) {
				// The sucker tears off and drops.
				b.dropFrom = b.lastStuckAt;
				b.dropAt = now;
				b.dropGround = LeechBody.ground(world, b.lastStuckAt, b.lastStuckAt.y - 1.0);
			}
			b.lastState = state;
		}
		b.step(world, e, pos, b.yaw, now, walking);
		Pose p = pose(e, state, t, now, dying);

		// ---- The body's frame (relative to the entity's place).
		float yawR = -b.yaw * MathHelper.RADIANS_PER_DEGREE;
		float sx = p.shake * MathHelper.sin(now * 9.1F);
		float sz = p.shake * MathHelper.cos(now * 7.3F);
		Matrix4f bodyM = new Matrix4f().translate(sx, p.dy, sz).rotateY(yawR).translate(0.0F, 1.5F, -0.3F).rotateX(p.pitch).rotateZ(p.roll).translate(0.0F, -1.5F, 0.3F);
		// It breathes: the body swells and sinks, its width and its height out of step, as a leech ripples.
		float br = MathHelper.sin(now * 0.085F + e.getId());
		float br2 = MathHelper.sin(now * 0.085F + e.getId() - 1.2F);
		float bk = dead ? 1.0F - dying : 1.0F;
		Matrix4f breathM = new Matrix4f(bodyM).translate(0.0F, 1.7F, -0.4F).scale(1.0F + 0.025F * br * bk, 1.0F + 0.035F * br2 * bk, 1.0F + 0.012F * br * bk)
				.translate(0.0F, -1.7F, 0.4F);
		Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
		FleshMesh.Look look = look(world, light, e.hurtTime > 6 ? 0.12F * (e.hurtTime - 6) / 4.0F : 0.0F);
		FleshMesh body = FleshMesh.file("leech_body");
		FleshMesh parts = FleshMesh.file("leech_parts");
		body.draw(new Matrix4f(base).mul(breathM), now, look, null);

		// ---- The head: it finds you late, as if by sound; the jaw, the hair.
		PlayerEntity near = world.getClosestPlayer(e, 32.0);
		if (now - b.heardAt >= 7.0F || now < b.heardAt) {
			b.heardAt = now;
			b.headWant = 0.0F;
			b.headPitchWant = 0.0F;
			if (near != null && !dead && (state == LeechDevilEntity.WALK || state == LeechDevilEntity.LATCHED || state == LeechDevilEntity.SKID)) {
				Vec3d rel = near.getEyePos().subtract(pos.add(0.0, 1.7, 0.0));
				float yawTo = (float) Math.toDegrees(Math.atan2(-rel.x, rel.z));
				b.headWant = MathHelper.clamp(MathHelper.wrapDegrees(yawTo - b.yaw), -42.0F, 42.0F);
				b.headPitchWant = MathHelper.clamp((float) Math.toDegrees(Math.atan2(rel.y, Math.hypot(rel.x, rel.z))), -20.0F, 25.0F);
			}
		}
		b.headYaw += MathHelper.clamp(b.headWant - b.headYaw, -3.0F * dt, 3.0F * dt);
		b.headPitch += MathHelper.clamp(b.headPitchWant - b.headPitch, -2.0F * dt, 2.0F * dt);
		float headDroop = dying > 0.0F ? 0.35F * ease(dying * 1.4F) : 0.0F;
		Matrix4f headM = new Matrix4f(bodyM).translate(NECK).rotateY(b.headYaw * MathHelper.RADIANS_PER_DEGREE)
				.rotateX(-b.headPitch * MathHelper.RADIANS_PER_DEGREE + headDroop).translate(-NECK.x, -NECK.y, -NECK.z);
		draw(body, "head", new Matrix4f(base).mul(headM), look, base);
		Matrix4f jawM = new Matrix4f(headM).translate(JAW_PIVOT).rotateX(p.jaw + 0.03F * Math.max(0.0F, MathHelper.sin(now * 0.07F))).translate(-JAW_PIVOT.x,
				-JAW_PIVOT.y, -JAW_PIVOT.z);
		draw(body, "jaw", new Matrix4f(base).mul(jawM), look, base);
		double speed = b.vel.length();
		float trail = (float) Math.min(0.35, speed * 0.9) + (state == LeechDevilEntity.TRAMPLE ? 0.15F : 0.0F);
		for (int k = 0; k < HAIR.length; k++) {
			float ax = 0.035F * MathHelper.sin(now * 0.06F + k * 1.7F) + trail;
			float az = 0.025F * MathHelper.cos(now * 0.047F + k * 2.3F);
			if (state == LeechDevilEntity.RECOIL) {
				ax += 0.05F * MathHelper.sin(now * 2.0F + k);
			}
			Matrix4f hm = new Matrix4f(headM).translate(CROWN).rotateX(ax).rotateZ(az).translate(-CROWN.x, -CROWN.y, -CROWN.z);
			draw(body, HAIR[k], new Matrix4f(base).mul(hm), look, base);
		}

		// ---- The tongue.
		drawTongue(e, b, parts, base, headM, look, state, t, now, dying, walking);

		// ---- The suckers' clock: each its own, slowing to a stop as it dies.
		float clock = now;
		if (dead) {
			float since = (e.deathTime + delta);
			float T = LeechDevilEntity.DEATH_TICKS;
			clock = (now - since) + since * (1.0F - 0.5F * Math.min(since, T) / T);
			if (since > T) {
				clock = (now - since) + T * 0.5F;
			}
		}

		// ---- The legs.
		for (int i = 0; i < LeechBody.LEGS; i++) {
			drawLeg(e, b, world, parts, base, bodyM, look, i, now, clock, pos, state, t, dying);
		}
		// ---- The arms.
		for (int a = 0; a < 2; a++) {
			drawArm(e, b, world, parts, base, bodyM, look, a, now, clock, pos, state, t, dying, delta);
		}
		// ---- A sucker torn off, on the ground.
		drawDropped(b, world, parts, base, look, now, pos);
	}

	private static void draw(FleshMesh mesh, String name, Matrix4f m, FleshMesh.Look look, Matrix4f base) {
		FleshMesh.Part part = mesh.byName.get(name);
		if (part != null) {
			mesh.drawPart(part, m, look, base);
		}
	}

	private static FleshMesh.Look look(ClientWorld world, int light, float hurt) {
		int block = LightmapTextureManager.getBlockLightCoordinates(light);
		int sky = LightmapTextureManager.getSkyLightCoordinates(light);
		float day = 1.0F - world.getAmbientDarkness() / 15.0F;
		float lit = Math.max(block, sky * day) / 15.0F;
		FleshMesh.Look l = new FleshMesh.Look();
		float k = 0.95F * (0.38F + 0.62F * lit);
		l.r = k;
		l.g = k;
		l.b = k;
		l.rim = 0.18F;
		l.hurt = hurt;
		return l;
	}

	/** How open sucker {@code k} is at {@code clock}: they pulse out of time with each other. */
	private static float open(int k, float clock) {
		float w = 0.07F + 0.023F * ((k * 5) % 7);
		return 0.5F + 0.5F * MathHelper.sin(clock * w + k * 2.1F);
	}

	private static void drawTongue(LeechDevilEntity e, LeechBody b, FleshMesh parts, Matrix4f base, Matrix4f headM, FleshMesh.Look look, int state, float t, float now,
			float dying, boolean walking) {
		Vector3f root = headM.transformPosition(new Vector3f(TONGUE_ROOT));
		Vector3f fwd = headM.transformDirection(new Vector3f(0, 0, 1)).normalize();
		Vector3f dir;
		float len;
		float wig;
		float droop = 0.0F;
		if (state == LeechDevilEntity.TONGUE) {
			dir = look(e.aim(), e.aimPitch());
			float reach = Math.max(1.0F, e.reach()) + 0.3F;
			if (t < LeechDevilEntity.TONGUE_TELL) {
				// coiled back in the gaping mouth: its tip curled up behind the teeth, twitching
				float rear = ramp(t, 0.0F, LeechDevilEntity.TONGUE_TELL - 2.0F);
				len = 0.25F + 0.25F * rear;
				dir = new Vector3f(fwd).add(0.0F, 0.9F * rear, 0.0F).normalize();
				wig = 0.06F;
			} else if (t < LeechDevilEntity.TONGUE_HIT) {
				len = reach * MathHelper.clamp((t - LeechDevilEntity.TONGUE_TELL + 0.5F) / LeechDevilEntity.TONGUE_OUT, 0.0F, 1.0F);
				wig = 0.04F;
			} else if (t < LeechDevilEntity.TONGUE_HIT + LeechDevilEntity.TONGUE_HOLD) {
				len = reach;
				wig = 0.12F;
			} else {
				len = reach * (1.0F - ramp(t, LeechDevilEntity.TONGUE_HIT + LeechDevilEntity.TONGUE_HOLD, LeechDevilEntity.TONGUE_END));
				wig = 0.15F;
				droop = 0.3F;
			}
		} else if (dying > 0.0F) {
			len = 0.9F * ease(dying * 1.5F);
			dir = new Vector3f(fwd).add(0.0F, -0.8F, 0.0F).normalize();
			wig = 0.03F * (1.0F - dying);
			droop = 1.0F;
		} else if (walking) {
			if (now > b.tasteAt + 40.0F || now < b.tasteAt - 400.0F) {
				b.tasteAt = now + 70.0F + (float) Math.floorMod((long) (now * 13.0F) + e.getId(), 110L);
			}
			float u = (now - b.tasteAt) / 34.0F;
			if (u < 0.0F || u > 1.0F) {
				return;
			}
			// it tastes the air: the tongue slides out, flicking, and back
			len = 1.3F * MathHelper.sin(MathHelper.PI * u);
			dir = new Vector3f(fwd).add(0.0F, -0.15F, 0.0F).normalize();
			wig = 0.12F;
			droop = 0.4F;
		} else {
			return;
		}
		if (len < 0.08F) {
			return;
		}
		Vector3f side = new Vector3f(dir).cross(0, 1, 0);
		if (side.lengthSquared() < 1.0E-4F) {
			side.set(1, 0, 0);
		}
		side.normalize();
		int n = Math.max(2, (int) Math.ceil(len / TONGUE_SEG));
		Vector3f[] pts = new Vector3f[n + 1];
		for (int k = 0; k <= n; k++) {
			float f = k / (float) n;
			Vector3f q = new Vector3f(root).add(new Vector3f(dir).mul(len * f));
			q.add(new Vector3f(side).mul(wig * MathHelper.sin(k * 1.4F - now * 0.9F) * f));
			q.y -= droop * 0.3F * f * f * Math.min(len, 2.0F);
			pts[k] = q;
		}
		for (int k = 0; k < n; k++) {
			float th = 1.9F - 0.5F * k / n;
			seg(parts, k == n - 1 ? "tongue_tip" : "tongue", base, pts[k], pts[k + 1], new Vector3f(0, 1, 0), th, k == n - 1 ? 0.45F : TONGUE_SEG, look);
		}
	}

	/** One piece from {@code a} to {@code b}: its +Y along the line (stretched to fit {@code length} of the piece), {@code thick} across. */
	private static void seg(FleshMesh parts, String name, Matrix4f base, Vector3f a, Vector3f b, Vector3f upHint, float thick, float length, FleshMesh.Look look) {
		FleshMesh.Part part = parts.byName.get(name);
		if (part == null) {
			return;
		}
		Vector3f y = new Vector3f(b).sub(a);
		float len = y.length();
		if (len < 1.0E-4F) {
			return;
		}
		y.div(len);
		Vector3f z = new Vector3f(upHint).sub(new Vector3f(y).mul(upHint.dot(y)));
		if (z.lengthSquared() < 1.0E-5F) {
			z = new Vector3f(1, 0, 0).cross(y);
		}
		z.normalize();
		Vector3f x = new Vector3f(y).cross(z).normalize();
		Matrix4f m = new Matrix4f(base).translate(a).mul(new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, 0, 0, 0, 1)).scale(thick, len / length, thick);
		parts.drawPart(part, m, look, base);
	}

	/** A sucker at {@code at} opening along {@code dir}, {@code open} wide (0 puckered shut, 1 open), {@code size} big. */
	private static void sucker(FleshMesh parts, Matrix4f base, Vector3f at, Vector3f dir, float open, float size, FleshMesh.Look look) {
		FleshMesh.Part part = parts.byName.get("sucker");
		if (part == null || size <= 0.01F) {
			return;
		}
		Vector3f y = new Vector3f(dir).normalize();
		Vector3f z = new Vector3f(0, 1, 0).sub(new Vector3f(y).mul(y.y));
		if (z.lengthSquared() < 1.0E-4F) {
			z.set(0, 0, 1);
			z.sub(new Vector3f(y).mul(y.z));
		}
		z.normalize();
		Vector3f x = new Vector3f(y).cross(z).normalize();
		float w = (0.55F + 0.6F * MathHelper.clamp(open, 0.0F, 1.3F)) * size;
		Matrix4f m = new Matrix4f(base).translate(at).mul(new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, 0, 0, 0, 1))
				.scale(w, size * (0.85F + 0.25F * open), w);
		parts.drawPart(part, m, look, base);
	}

	/** The two-bone solve: the joint between {@code a} and {@code c} (bones {@code l1}, {@code l2}), bent toward {@code pole}. */
	private static Vector3f joint(Vector3f a, Vector3f c, Vector3f pole, float l1, float l2) {
		Vector3f d = new Vector3f(c).sub(a);
		float len = MathHelper.clamp(d.length(), 0.3F, (l1 + l2) * 0.995F);
		Vector3f u = d.normalize();
		float cosA = (l1 * l1 + len * len - l2 * l2) / (2.0F * l1 * len);
		float ang = (float) Math.acos(MathHelper.clamp(cosA, -1.0F, 1.0F));
		Vector3f pr = new Vector3f(pole).sub(new Vector3f(u).mul(pole.dot(u)));
		if (pr.lengthSquared() < 1.0E-5F) {
			pr = new Vector3f(0, 1, 0);
		}
		pr.normalize();
		return new Vector3f(a).add(new Vector3f(u).mul(MathHelper.cos(ang) * l1)).add(pr.mul(MathHelper.sin(ang) * l1));
	}

	private static void drawLeg(LeechDevilEntity e, LeechBody b, ClientWorld world, FleshMesh parts, Matrix4f base, Matrix4f bodyM, FleshMesh.Look look, int i, float now,
			float clock, Vec3d pos, int state, float t, float dying) {
		Vector3f hip = bodyM.transformPosition(new Vector3f(HIPS[i]));
		double[] f = b.foot(i, now);
		Vector3f ankle = new Vector3f((float) (f[0] - pos.x), (float) (f[1] - pos.y) + ANKLE_UP, (float) (f[2] - pos.z));
		float swing = (float) f[3];
		Vector3f fwd = bodyM.transformDirection(new Vector3f(0, 0, 1)).normalize();
		if (state == LeechDevilEntity.TRAMPLE_TELL && i < 2) {
			// pawing the ground with a front sucker: up, forward, stamped down (at the server's paw ticks: 4 the left, 10 the right, 16 the left)
			int[] paws = i == 0 ? new int[] {4, 16} : new int[] {10};
			for (int pt : paws) {
				float u = (t - (pt - 4)) / 5.0F;
				if (u > 0.0F && u < 1.0F) {
					float lift = MathHelper.sin(u * MathHelper.PI);
					ankle.add(new Vector3f(fwd).mul(0.35F * lift)).add(0.0F, 0.75F * lift, 0.0F);
					swing = u;
				}
			}
		}
		if (dying > 0.0F) {
			// folding: the feet come in under it as it sinks
			Vector3f under = new Vector3f(hip.x * 0.8F, ANKLE_UP + 0.02F, hip.z * 0.92F);
			under.y = (float) (LeechBody.ground(world, pos.add(under.x, 0, under.z), pos.y) - pos.y) + ANKLE_UP;
			ankle.lerp(under, ease(dying * 1.2F) * 0.55F);
		}
		b.feet[i].drawn = new Vec3d(pos.x + ankle.x, pos.y + ankle.y - ANKLE_UP, pos.z + ankle.z);
		Vector3f outW = bodyM.transformDirection(new Vector3f(Math.signum(HIPS[i].x), 0, 0));
		outW.y = 0.0F;
		outW.normalize();
		Vector3f pole = new Vector3f(outW).add(0.0F, 1.1F, 0.0F);
		Vector3f knee = joint(hip, ankle, pole, THIGH, SHIN);
		seg(parts, "thigh", base, hip, knee, new Vector3f(outW).negate(), 1.0F, THIGH, look);
		seg(parts, "shin", base, knee, ankle, new Vector3f(outW).negate(), 1.0F, SHIN, look);
		// the foot: shut on the ground while it stands, opening as it lifts
		float o = swing < 0.0F ? 0.25F + 0.2F * open(i, clock) : 0.5F + 0.5F * MathHelper.sin(swing * MathHelper.PI);
		if (dying > 0.0F) {
			o = open(i, clock);
		}
		sucker(parts, base, ankle, new Vector3f(0, -1, 0), o, 1.0F, look);
	}

	private static Vector3f bezier(Vector3f p0, Vector3f p1, Vector3f p2, float u) {
		float a = (1 - u) * (1 - u);
		float bb = 2 * (1 - u) * u;
		float c = u * u;
		return new Vector3f(p0).mul(a).add(new Vector3f(p1).mul(bb)).add(new Vector3f(p2).mul(c));
	}

	private static void drawArm(LeechDevilEntity e, LeechBody b, ClientWorld world, FleshMesh parts, Matrix4f base, Matrix4f bodyM, FleshMesh.Look look, int a, float now,
			float clock, Vec3d pos, int state, float t, float dying, float delta) {
		float side = a == 0 ? 1.0F : -1.0F;
		Vector3f sh = bodyM.transformPosition(new Vector3f(SHOULDERS[a]));
		// At rest: hanging forward and down, the tips curling near the ground, swaying.
		float sw = MathHelper.sin(now * 0.05F + a * 2.0F);
		// (arched up and out from the shoulder, the tips hanging forward at chest height, curling and swaying)
		Vector3f tip = bodyM.transformPosition(new Vector3f(side * (1.05F + 0.25F * sw), 0.55F + 0.25F * MathHelper.sin(now * 0.07F + a), 3.2F + 0.25F * MathHelper.sin(now * 0.04F + a)));
		Vector3f ctrl = bodyM.transformPosition(new Vector3f(side * (1.45F + 0.15F * sw), 1.85F, 2.6F));
		Vector3f tipDir = null;
		float open = open(4 + a, clock);
		float suckerSize = 1.0F;
		float bulge = -1.0F;
		boolean active = a == e.arm();
		Vector3f aimDir = look(e.aim(), e.aimPitch());
		if (active && state == LeechDevilEntity.LATCH) {
			float tellEnd = LeechDevilEntity.LATCH_TELL;
			float outEnd = tellEnd + LeechDevilEntity.LATCH_OUT;
			Vector3f full = new Vector3f(sh).add(new Vector3f(aimDir).mul(Math.max(1.0F, e.reach())));
			if (t < tellEnd) {
				// coiled back over its shoulder, the sucker opening wide, trembling
				float coil = ramp(t, 0.0F, tellEnd - 3.0F);
				float tr = 0.05F * MathHelper.sin(now * 2.7F) * coil;
				Vector3f tc = bodyM.transformPosition(new Vector3f(side * 1.2F, 2.75F + tr, 0.75F + tr));
				Vector3f cc = bodyM.transformPosition(new Vector3f(side * 1.35F, 2.4F, 2.2F));
				tip.lerp(tc, coil);
				ctrl.lerp(cc, coil);
				open = MathHelper.lerp(coil, open, 1.25F);
				tipDir = new Vector3f(tip).sub(ctrl).normalize();
			} else if (t < outEnd) {
				float k = MathHelper.clamp((t - tellEnd) / LeechDevilEntity.LATCH_OUT, 0.0F, 1.0F);
				tip = new Vector3f(sh).lerp(full, 0.15F + 0.85F * k);
				ctrl = new Vector3f(sh).lerp(tip, 0.5F).add(0.0F, 0.9F * (1.0F - k), 0.0F);
				open = 1.25F;
				tipDir = new Vector3f(aimDir);
			} else {
				float u = ramp(t, outEnd, LeechDevilEntity.LATCH_END);
				Vector3f mid = new Vector3f(sh).lerp(full, 0.5F).add(0.0F, -0.4F * u, 0.0F);
				tip = new Vector3f(full).lerp(tip, u);
				ctrl = new Vector3f(mid).lerp(ctrl, u);
				open = MathHelper.lerp(u, 1.0F, open);
			}
		} else if (active && state == LeechDevilEntity.LATCHED) {
			Entity stuck = world.getEntityById(e.stuckTo());
			if (stuck != null) {
				Vec3d sp = stuck.getLerpedPos(delta).add(0.0, stuck.getHeight() * 0.55, 0.0);
				b.lastStuckAt = sp;
				Vec3d toward = pos.add(sh.x, sh.y, sh.z).subtract(sp).normalize().multiply(0.15);
				tip = new Vector3f((float) (sp.x + toward.x - pos.x), (float) (sp.y + toward.y - pos.y), (float) (sp.z + toward.z - pos.z));
				ctrl = new Vector3f(sh).lerp(tip, 0.5F).add(0.0F, -0.35F, 0.0F);
				tipDir = new Vector3f(tip).sub(ctrl).normalize();
				open = 0.85F + 0.15F * MathHelper.sin(t * MathHelper.TAU / LeechDevilEntity.LATCH_PULSE);
				// the blood pulsing up the arm: a swelling running from the sucker to the body each pulse
				bulge = 1.0F - (t % LeechDevilEntity.LATCH_PULSE) / LeechDevilEntity.LATCH_PULSE;
			}
		} else if (active && state == LeechDevilEntity.RECOIL) {
			// torn loose: the arm thrashes up, the sucker gone
			float k = 1.0F - ramp(t, 6.0F, LeechDevilEntity.RECOIL_TICKS);
			Vector3f fl = bodyM.transformPosition(new Vector3f(side * (1.5F + 0.3F * MathHelper.sin(now * 1.7F)), 2.6F + 0.5F * MathHelper.sin(now * 1.3F),
					1.8F + 0.6F * MathHelper.sin(now * 0.9F)));
			Vector3f limp = bodyM.transformPosition(new Vector3f(side * 0.75F, 0.05F, 2.4F));
			tip = new Vector3f(limp).lerp(fl, k);
			ctrl = bodyM.transformPosition(new Vector3f(side * 1.2F, 1.8F * k + 0.9F * (1 - k), 2.2F));
			suckerSize = 0.0F;
		}
		if (active && e.armStunned() && state != LeechDevilEntity.RECOIL && state != LeechDevilEntity.LATCH && state != LeechDevilEntity.LATCHED) {
			// hanging limp, its sucker growing back only at the end
			tip = bodyM.transformPosition(new Vector3f(side * 0.75F, 0.05F, 2.4F + 0.05F * MathHelper.sin(now * 0.2F)));
			ctrl = bodyM.transformPosition(new Vector3f(side * 1.0F, 0.85F, 2.2F));
			float left = e.armStunUntil() - (world.getTime() + delta);
			suckerSize = 1.0F - MathHelper.clamp(left / 20.0F, 0.0F, 1.0F);
			open = 0.2F;
		}
		if (dying > 0.0F) {
			float k = ease(dying * 1.3F);
			tip.lerp(bodyM.transformPosition(new Vector3f(side * 0.9F, -0.55F, 3.0F)), k);
			ctrl.lerp(bodyM.transformPosition(new Vector3f(side * 1.1F, 0.5F, 2.4F)), k);
		}
		// The chain along the curve; thinner as it stretches, as a leech does.
		Vector3f[] pts = new Vector3f[ARM_SEGS + 1];
		float arc = 0.0F;
		for (int k = 0; k <= ARM_SEGS; k++) {
			pts[k] = bezier(sh, ctrl, tip, k / (float) ARM_SEGS);
			if (k > 0) {
				arc += pts[k].distance(pts[k - 1]);
			}
		}
		float stretch = MathHelper.clamp((float) Math.sqrt(3.4F / Math.max(arc, 0.5F)), 0.55F, 1.1F);
		FleshMesh.Look blood = new FleshMesh.Look();
		blood.r = Math.min(1.0F, look.r * 1.9F);
		blood.g = look.g * 0.35F;
		blood.b = look.b * 0.35F;
		blood.rim = look.rim;
		blood.hurt = look.hurt;
		for (int k = 0; k < ARM_SEGS; k++) {
			float u = (k + 0.5F) / ARM_SEGS;
			float th = (1.1F - 0.38F * u) * stretch;
			boolean swell = bulge >= 0.0F && Math.abs(u - bulge) < 0.12F;
			if (swell) {
				th *= 1.4F;
			}
			seg(parts, "tseg", base, pts[k], pts[k + 1], new Vector3f(0, 1, 0), th, TSEG, swell ? blood : look);
		}
		Vector3f dir = tipDir != null ? tipDir : new Vector3f(pts[ARM_SEGS]).sub(pts[ARM_SEGS - 1]).normalize();
		sucker(parts, base, pts[ARM_SEGS], dir, open, 0.95F * suckerSize, look);
	}

	private static void drawDropped(LeechBody b, ClientWorld world, FleshMesh parts, Matrix4f base, FleshMesh.Look look, float now, Vec3d pos) {
		if (Float.isNaN(b.dropAt) || b.dropFrom == null) {
			return;
		}
		float age = now - b.dropAt;
		if (age > 110.0F || age < 0.0F) {
			b.dropAt = Float.NaN;
			return;
		}
		// it falls, lands mouth up, gapes and shuts a few times, slower, and sinks away
		double fall = 0.5 * 0.06 * age * age;
		double y = Math.max(b.dropGround + 0.05, b.dropFrom.y - fall);
		float sink = MathHelper.clamp((age - 80.0F) / 30.0F, 0.0F, 1.0F);
		Vector3f at = new Vector3f((float) (b.dropFrom.x - pos.x), (float) (y - pos.y) - 0.15F * sink, (float) (b.dropFrom.z - pos.z));
		float o = 0.5F + 0.5F * MathHelper.sin(age * 0.25F * (1.0F - age / 120.0F));
		Vector3f dir = new Vector3f(0.3F, 1.0F, 0.2F).normalize();
		sucker(parts, base, at, dir, o, 0.95F * (1.0F - 0.5F * sink), look);
	}
}
