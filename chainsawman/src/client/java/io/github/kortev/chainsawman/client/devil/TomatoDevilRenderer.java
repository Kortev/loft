package io.github.kortev.chainsawman.client.devil;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.github.kortev.chainsawman.client.FleshMesh;
import io.github.kortev.chainsawman.devil.TomatoDevilEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Tomato Devil ({@link TomatoDevilEntity}) from its two baked meshes ({@code tools/tomato_devil_3d.py}): the body with its eyes and its
 * two lips, and eight arms each drawn as an upper arm, a forearm, a hand and its fingers, put where a two-bone solve says (the shoulder on
 * the body's underside, the wrist where the hand is, the elbow bent out and up). The hands walk ({@link TomatoBody}); in a move they are
 * posed: reared up and slapping down, tucked round the body to roll, waving in the air on its back, curled in as it dies.
 */
public class TomatoDevilRenderer extends EntityRenderer<TomatoDevilEntity> {
	static final Identifier GASH = Identifier.of("chainsawman", "textures/entity/tomato_gash.png");
	/** The arm's two bones (the meshes' lengths) and where the shoulders are on the body (its own frame). */
	static final float UPPER = 1.30F;
	static final float FORE = 1.20F;
	static final float REACH = (UPPER + FORE) * 0.995F;
	static final float SHOULDER_R = 0.72F;
	static final float SHOULDER_Y = -0.86F;
	/** The body's middle above the ground, standing. */
	static final float STAND = 2.45F;
	/** Lying on the ground (tucked, rolling, on its back). */
	static final float LOW = 1.12F;
	/** The wrist above the ground when the hand is flat (the palm's thickness under it). */
	static final double WRIST_UP = 0.09;
	static final float EYE_R = 0.2F;

	private record Eye(Vector3f c, Vector3f n, float r) {
	}

	private static List<Eye> eyes;

	public TomatoDevilRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.shadowRadius = 1.3F;
		this.shadowOpacity = 0.6F;
	}

	@Override
	public Identifier getTexture(TomatoDevilEntity entity) {
		return GASH;
	}

	@Override
	public boolean shouldRender(TomatoDevilEntity entity, Frustum frustum, double x, double y, double z) {
		return entity.squaredDistanceTo(x, y, z) < 96.0 * 96.0;
	}

	static List<Eye> eyes() {
		if (eyes == null) {
			List<Eye> out = new ArrayList<>();
			JsonArray a = FleshMesh.file("tomato_body").info.getAsJsonArray("eyes");
			for (JsonElement el : a) {
				JsonArray e = el.getAsJsonArray();
				out.add(new Eye(new Vector3f(e.get(0).getAsFloat(), e.get(1).getAsFloat(), e.get(2).getAsFloat()),
						new Vector3f(e.get(3).getAsFloat(), e.get(4).getAsFloat(), e.get(5).getAsFloat()), e.get(6).getAsFloat()));
			}
			eyes = out;
		}
		return eyes;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	private static float ramp(float t, float a, float b) {
		return ease((t - a) / (b - a));
	}

	/** How the body and the arms are held at one moment. */
	static final class Pose {
		/** The body's middle above the ground, how far forward of the feet's middle, its nose-down tilt (radians), its swell. */
		float height = STAND;
		float forward;
		float pitch;
		float roll;
		float scale = 1.0F;
		float shake;
		/** The mouth: 0 shut, 1 wide. */
		float mouth = 0.06F;
		/** Eyes rolling about (dazed). */
		boolean dazed;
		/** Per arm: how much it is posed (0: it walks), the wrist (the body's frame), where the elbow points (the body's frame), the hand. */
		final float[] w = new float[TomatoBody.ARMS];
		final Vector3f[] wrist = new Vector3f[TomatoBody.ARMS];
		final Vector3f[] pole = new Vector3f[TomatoBody.ARMS];
		/** The hand's fingers' way (the body's frame), which way is the back of the hand (the body's frame), and the curl. */
		final Vector3f[] fingers = new Vector3f[TomatoBody.ARMS];
		final Vector3f[] back = new Vector3f[TomatoBody.ARMS];
		final float[] curl = new float[TomatoBody.ARMS];
		/** True where the posed wrist is given in the world (planted on the ground, relative to the entity) rather than on the body. */
		final boolean[] inWorld = new boolean[TomatoBody.ARMS];

		void arm(int i, float weight, Vector3f wristAt, Vector3f poleTo, Vector3f fingersTo, Vector3f backTo, float c) {
			w[i] = weight;
			wrist[i] = wristAt;
			pole[i] = poleTo;
			fingers[i] = fingersTo;
			back[i] = backTo;
			curl[i] = c;
		}
	}

	private static Vector3f out(int i) {
		float a = TomatoBody.AZ[i] * MathHelper.RADIANS_PER_DEGREE;
		return new Vector3f(MathHelper.sin(a), 0.0F, MathHelper.cos(a));
	}

	private static boolean front(int i) {
		return i == 0 || i == 1 || i == 6 || i == 7;
	}

	/** The pose for the entity's state at {@code t} ticks into it (and its dying). */
	private static Pose pose(TomatoDevilEntity e, int state, float t, float now) {
		Pose p = new Pose();
		Vector3f up = new Vector3f(0, 1, 0);
		switch (state) {
			case TomatoDevilEntity.SLAP -> {
				float rear = ramp(t, 0.0F, TomatoDevilEntity.SLAP_TELL - 2.0F);
				float down = ramp(t, TomatoDevilEntity.SLAP_TELL, TomatoDevilEntity.SLAP_HIT);
				float settle = ramp(t, TomatoDevilEntity.SLAP_HIT + 4.0F, TomatoDevilEntity.SLAP_END);
				// Up on its back four, the front tipped up; then thrown forward onto its slapping hands.
				p.pitch = -0.45F * rear * (1.0F - down) + 0.22F * down * (1.0F - settle);
				p.height = STAND + 0.35F * rear * (1.0F - down) - 0.25F * down * (1.0F - settle);
				p.forward = -0.25F * rear * (1.0F - down) + 0.55F * down * (1.0F - settle);
				p.mouth = 0.06F + 0.3F * rear * (1.0F - down);
				for (int i = 0; i < TomatoBody.ARMS; i++) {
					if (!front(i)) {
						continue;
					}
					Vector3f o = out(i);
					float tremble = 0.06F * MathHelper.sin(now * 1.7F + i * 2.1F) * rear * (1.0F - down);
					// raised: high over the body and spread, palms forward, fingers spread up
					Vector3f high = new Vector3f(o.x * 1.25F, 1.55F + (i == 0 || i == 7 ? 0.25F : 0.0F) + tremble, o.z * 0.55F + 0.35F);
					Vector3f poleHigh = new Vector3f(o.x * 1.0F, -0.6F, o.z * 0.4F - 0.8F);
					if (t < TomatoDevilEntity.SLAP_TELL) {
						Vector3f low = new Vector3f(o.x * 1.6F, -2.2F, o.z * 1.6F);
						Vector3f at = new Vector3f(low).lerp(high, rear);
						p.arm(i, ease(t / 5.0F), at, poleHigh, new Vector3f(o.x * 0.3F, 1.0F, 0.6F).normalize(), new Vector3f(0, 0, -1), 0.05F + 0.2F * rear);
					} else {
						p.arm(i, 1.0F - settle, null, poleHigh, null, null, 0.1F);
						p.inWorld[i] = true;
						float side = i == 0 || i == 7 ? 0.45F : 1.25F;
						float sx = MathHelper.sign(o.x) * side;
						Vector3f landed = new Vector3f(sx, 0.0F, 2.75F + (i == 0 || i == 7 ? 0.3F : 0.0F));
						// (the raised hand as it stood over the ground, then thrown down onto it in front)
						Vector3f highW = new Vector3f(o.x * 1.25F, 4.15F + (i == 0 || i == 7 ? 0.25F : 0.0F), o.z * 0.55F + 0.1F);
						Vector3f at = new Vector3f(highW).lerp(landed, down);
						p.wrist[i] = at;
						p.fingers[i] = new Vector3f(sx * 0.3F, -1.0F + down, 1.0F).normalize();
						p.back[i] = new Vector3f(0, 1, 0);
					}
				}
			}
			case TomatoDevilEntity.ROLL_TELL -> {
				float tuck = ramp(t, 0.0F, 10.0F);
				float rock = ramp(t, 10.0F, TomatoDevilEntity.ROLL_TELL_TICKS);
				p.height = MathHelper.lerp(tuck, STAND, LOW + 0.05F) + 0.12F * rock;
				p.pitch = -0.55F * rock + 0.04F * MathHelper.sin(now * 2.5F) * rock;
				p.forward = -0.15F * rock;
				tucked(p, tuck);
			}
			case TomatoDevilEntity.ROLL -> {
				p.height = LOW;
				tucked(p, 1.0F);
			}
			case TomatoDevilEntity.ON_BACK -> {
				float bounce = t < 10.0F ? 0.35F * Math.abs(MathHelper.sin(t / 10.0F * MathHelper.PI * 1.5F)) * (1.0F - t / 10.0F) : 0.0F;
				p.height = LOW - 0.05F + bounce;
				p.dazed = true;
				p.mouth = 0.25F + 0.15F * MathHelper.sin(now * 0.7F);
				for (int i = 0; i < TomatoBody.ARMS; i++) {
					Vector3f o = out(i);
					// in the air (the body's underside is up now), waving
					float wv = MathHelper.sin(now * 0.55F + i * 1.3F);
					float wv2 = MathHelper.cos(now * 0.43F + i * 0.9F);
					Vector3f side = new Vector3f(-o.z, 0, o.x);
					Vector3f at = new Vector3f(o).mul(1.55F + 0.25F * wv2).add(0.0F, -2.0F, 0.0F).add(side.mul(0.45F * wv));
					p.arm(i, ease(t / 6.0F), at, new Vector3f(o.x, 0.4F, o.z), new Vector3f(o.x, -0.6F, o.z).normalize(), new Vector3f(-o.x, 0, -o.z),
							0.4F + 0.4F * MathHelper.sin(now * 0.9F + i));
				}
			}
			case TomatoDevilEntity.GET_UP -> {
				float k = ramp(t, 0.0F, TomatoDevilEntity.GET_UP_TICKS);
				p.height = MathHelper.lerp(ramp(t, 4.0F, TomatoDevilEntity.GET_UP_TICKS), LOW, STAND);
				for (int i = 0; i < TomatoBody.ARMS; i++) {
					Vector3f o = out(i);
					Vector3f at = new Vector3f(o).mul(1.55F).add(0.0F, -2.0F, 0.0F);
					p.arm(i, 1.0F - ramp(t, 5.0F, TomatoDevilEntity.GET_UP_TICKS), at, new Vector3f(o.x, 0.4F, o.z),
							new Vector3f(o.x, -0.6F, o.z).normalize(), new Vector3f(-o.x, 0, -o.z), 0.3F * (1.0F - k));
				}
			}
			case TomatoDevilEntity.UNFURL -> {
				float k = ramp(t, 0.0F, TomatoDevilEntity.UNFURL_TICKS);
				p.height = MathHelper.lerp(k, LOW + 0.05F, STAND);
				tucked(p, 1.0F - ramp(t, 0.0F, 6.0F));
			}
			case TomatoDevilEntity.SPIT -> {
				float open = ramp(t, 0.0F, TomatoDevilEntity.SPIT_TELL);
				float fire = ramp(t, TomatoDevilEntity.SPIT_TELL, TomatoDevilEntity.SPIT_TELL + 2.0F);
				float close = ramp(t, TomatoDevilEntity.SPIT_TELL + 4.0F, TomatoDevilEntity.SPIT_END);
				p.mouth = Math.max(0.06F, open * (1.0F - close));
				p.pitch = -0.22F * open * (1.0F - fire) + 0.18F * fire * (1.0F - close);
				p.scale = 1.0F + 0.06F * open * (1.0F - fire);
				p.height = STAND - 0.15F * open * (1.0F - close);
				p.shake = 0.02F * open * (1.0F - fire);
			}
			case TomatoDevilEntity.EMERGE -> {
				float rise = ramp(t, 8.0F, TomatoDevilEntity.EMERGE_TICKS);
				p.height = MathHelper.lerp(rise, 0.35F, STAND);
				p.scale = MathHelper.lerp(ramp(t, 0.0F, TomatoDevilEntity.EMERGE_TICKS), 0.6F, 1.0F);
				p.mouth = 0.4F * (1.0F - rise);
				for (int i = 0; i < TomatoBody.ARMS; i++) {
					Vector3f o = out(i);
					// out of the top first, flung over and down onto the ground round it
					float reach = ramp(t, i * 0.6F, 8.0F + i * 0.6F);
					Vector3f a = new Vector3f(o).mul(0.6F).add(0, 1.8F, 0);
					Vector3f b = new Vector3f(o).mul(2.1F).add(0, -p.height / p.scale, 0);
					Vector3f at = new Vector3f(a).lerp(b, reach);
					at.y += 0.9F * MathHelper.sin(reach * MathHelper.PI);
					p.arm(i, 1.0F - rise, at, new Vector3f(o.x, 1.0F, o.z), new Vector3f(o), new Vector3f(0, 1, 0), 0.6F * (1.0F - reach));
				}
			}
			default -> {
			}
		}
		if (e.isDead()) {
			// Dying: it shakes, swells, sinks and its arms curl in under it.
			float d = MathHelper.clamp((e.deathTime + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true)) / TomatoDevilEntity.BURST_AT, 0.0F, 1.0F);
			p.height = MathHelper.lerp(ease(d * 1.4F), p.height, LOW + 0.1F);
			p.scale = 1.0F + 0.2F * d * d;
			p.shake = 0.05F * d;
			p.mouth = 0.5F * d;
			p.dazed = true;
			for (int i = 0; i < TomatoBody.ARMS; i++) {
				Vector3f o = out(i);
				Vector3f at = new Vector3f(o).mul(0.55F).add(0, -1.25F, 0);
				p.arm(i, ease(d * 1.6F), at, new Vector3f(o.x, -0.2F, o.z), new Vector3f(-o.x, -0.5F, -o.z).normalize(), new Vector3f(o.x, -0.3F, o.z).normalize(),
						0.4F + 1.2F * d);
				p.inWorld[i] = false;
			}
		}
		return p;
	}

	/** The arms folded up round the body, hands gripping its sides. */
	private static void tucked(Pose p, float k) {
		for (int i = 0; i < TomatoBody.ARMS; i++) {
			Vector3f o = out(i);
			Vector3f at = new Vector3f(o).mul(1.28F).add(0.0F, 0.05F, 0.0F);
			Vector3f side = new Vector3f(-o.z, 0, o.x);
			p.arm(i, k, at, new Vector3f(o.x, -1.0F, o.z), new Vector3f(0, 1, 0).add(side.mul(0.3F)).normalize(), new Vector3f(o), 0.7F);
		}
	}

	@Override
	public void render(TomatoDevilEntity e, float yawIn, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(e.getWorld() instanceof ClientWorld world) || e.isDead() && e.deathTime >= TomatoDevilEntity.BURST_AT) {
			return;
		}
		if (!(e.clientState instanceof TomatoBody)) {
			e.clientState = new TomatoBody();
		}
		TomatoBody b = (TomatoBody) e.clientState;
		float now = world.getTime() + delta;
		Vec3d pos = e.getLerpedPos(delta);
		int state = e.state();
		float t = e.stateTime(delta);
		boolean walking = state == TomatoDevilEntity.WALK && !e.isDead();
		// The way it faces: its body turning toward where it goes, or the move's aim, smoothly.
		float want = walking ? MathHelper.lerpAngleDegrees(delta, e.prevBodyYaw, e.bodyYaw) : e.aim();
		if (!b.ready || Float.isNaN(b.lastNow) || now - b.lastNow > 10.0F || now < b.lastNow) {
			b.reset(world, pos, want);
		}
		float dt = Float.isNaN(b.lastNow) ? 0.0F : Math.max(0.0F, now - b.lastNow);
		b.yaw += MathHelper.clamp(MathHelper.wrapDegrees(want - b.yaw), -14.0F * dt, 14.0F * dt);
		if (state != b.lastState) {
			if (state == TomatoDevilEntity.WALK && b.lastState >= 0) {
				b.plantWhereDrawn(world, pos);
			}
			if (state == TomatoDevilEntity.ROLL) {
				b.rolled = 0.0;
			}
			b.lastState = state;
		}
		b.step(world, e, pos, b.yaw, now, walking);
		Pose p = pose(e, state, t, now);
		wounds(e, b, world, pos, now);

		// ---- The body's frame (relative to the entity's place), as the meshes see it.
		float yawR = -b.yaw * MathHelper.RADIANS_PER_DEGREE;
		float walkLean = (float) Math.min(0.08, b.vel.length() * 0.45);
		float sag = walking ? 0.1F * b.sag : 0.0F;
		float breathe = 0.025F * MathHelper.sin(now * 0.12F);
		float roll = 0.0F;
		if (state == TomatoDevilEntity.ROLL) {
			roll = (float) (b.rolled / 1.15);
		} else if (state == TomatoDevilEntity.ON_BACK) {
			roll = MathHelper.PI * ramp(t, 0.0F, 6.0F) + (1.0F - ramp(t, 0.0F, 6.0F)) * 0.0F;
		} else if (state == TomatoDevilEntity.GET_UP) {
			roll = MathHelper.PI + MathHelper.PI * ramp(t, 0.0F, TomatoDevilEntity.GET_UP_TICKS - 2.0F);
		}
		float sx = p.shake * MathHelper.sin(now * 9.1F);
		float sz = p.shake * MathHelper.cos(now * 7.3F);
		Matrix4f bodyM = new Matrix4f().translate(sx, p.height - sag + breathe, sz).rotateY(yawR).translate(0, 0, p.forward)
				.rotateX(p.pitch + walkLean + roll).scale(p.scale);
		Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
		Matrix4f bodyView = new Matrix4f(base).mul(bodyM);

		FleshMesh.Look look = look(world, light, e.hurtTime > 0 ? 0.55F * e.hurtTime / 10.0F : 0.0F);
		FleshMesh body = FleshMesh.file("tomato_body");
		FleshMesh parts = FleshMesh.file("tomato_parts");
		body.draw(bodyView, now, look, null);
		lips(body, bodyView, base, look, p.mouth + 0.04F * MathHelper.sin(now * 0.31F) * (p.mouth < 0.2F ? 1 : 0));
		drawEyes(e, b, world, parts, base, bodyM, bodyView, look, p, now, pos, delta);
		drawWounds(b, matrices, provider, bodyM, light);

		// ---- The arms.
		for (int i = 0; i < TomatoBody.ARMS; i++) {
			drawArm(e, b, world, parts, base, bodyM, look, p, i, now, pos);
		}
	}

	private static FleshMesh.Look look(ClientWorld world, int light, float hurt) {
		int block = LightmapTextureManager.getBlockLightCoordinates(light);
		int sky = LightmapTextureManager.getSkyLightCoordinates(light);
		float day = 1.0F - world.getAmbientDarkness() / 15.0F;
		float lit = Math.max(block, sky * day) / 15.0F;
		FleshMesh.Look l = new FleshMesh.Look();
		float k = 0.9F * (0.36F + 0.64F * lit);
		l.r = k;
		l.g = k;
		l.b = k;
		l.rim = 0.35F;
		l.hurt = hurt;
		return l;
	}

	private static void lips(FleshMesh body, Matrix4f bodyView, Matrix4f base, FleshMesh.Look look, float open) {
		float a = 1.25F * MathHelper.clamp(open, 0.0F, 1.0F);
		for (String name : new String[] {"lip_l", "lip_r"}) {
			FleshMesh.Part part = body.byName.get(name);
			if (part == null) {
				continue;
			}
			float s = name.equals("lip_l") ? -1.0F : 1.0F;
			Matrix4f m = new Matrix4f(bodyView).translate(part.pivot).rotateY(s * a).translate(-part.pivot.x, -part.pivot.y, -part.pivot.z);
			body.drawPart(part, m, look, base);
		}
	}

	private static void drawEyes(TomatoDevilEntity e, TomatoBody b, ClientWorld world, FleshMesh parts, Matrix4f base, Matrix4f bodyM, Matrix4f bodyView,
			FleshMesh.Look look, Pose p, float now, Vec3d pos, float delta) {
		FleshMesh.Part eye = parts.byName.get("eye");
		if (eye == null) {
			return;
		}
		// What they look at: the nearest player's eyes, in the body's own frame.
		PlayerEntity near = world.getClosestPlayer(e, 32.0);
		Vector3f target = null;
		if (near != null) {
			Vec3d ep = near.getLerpedPos(delta).add(0.0, near.getStandingEyeHeight(), 0.0).subtract(pos);
			target = new Matrix4f(bodyM).invert().transformPosition(new Vector3f((float) ep.x, (float) ep.y, (float) ep.z));
		}
		float health = e.getHealth() / e.getMaxHealth();
		FleshMesh.Look burst = new FleshMesh.Look();
		burst.r = look.r * 0.45F;
		burst.g = look.g * 0.06F;
		burst.b = look.b * 0.05F;
		burst.rim = 0.2F;
		List<Eye> list = eyes();
		for (int k = 0; k < list.size(); k++) {
			Eye ey = list.get(k);
			boolean popped = health < 0.4F && k % 3 == 0 || health < 0.22F && k % 3 == 1;
			Vector3f dir;
			if (p.dazed) {
				dir = new Vector3f(MathHelper.sin(now * 0.5F + k), MathHelper.cos(now * 0.37F + k * 2.0F), 0.4F).normalize();
				dir = new Vector3f(ey.n).add(dir.mul(0.9F)).normalize();
			} else if (target != null) {
				dir = new Vector3f(target).sub(ey.c).normalize();
			} else {
				dir = new Vector3f(ey.n).add(0.25F * MathHelper.sin(now * 0.05F + k), 0.15F * MathHelper.cos(now * 0.04F + k), 0.0F).normalize();
			}
			// No further round than its socket lets it.
			float cos = dir.dot(ey.n);
			if (cos < 0.5F) {
				Vector3f perp = new Vector3f(dir).sub(new Vector3f(ey.n).mul(cos));
				if (perp.lengthSquared() < 1.0E-6F) {
					perp = new Vector3f(0, 1, 0);
				}
				perp.normalize();
				dir = new Vector3f(ey.n).mul(0.5F).add(perp.mul(0.866F)).normalize();
			}
			Vector3f zc = dir;
			Vector3f xc = new Vector3f(0, 1, 0).cross(zc);
			if (xc.lengthSquared() < 1.0E-4F) {
				xc = new Vector3f(1, 0, 0);
			}
			xc.normalize();
			Vector3f yc = new Vector3f(zc).cross(xc);
			float bl = popped ? 0.35F : b.blink(k, now, e.getId());
			float s = ey.r / EYE_R;
			Matrix4f m = new Matrix4f(bodyView).translate(ey.c)
					.mul(new Matrix4f(xc.x, xc.y, xc.z, 0, yc.x, yc.y, yc.z, 0, zc.x, zc.y, zc.z, 0, 0, 0, 0, 1)).scale(s, s * bl, s * (popped ? 0.6F : 1.0F));
			parts.drawPart(eye, m, popped ? burst : look, base);
		}
	}

	/** Cuts, made where the nearest player stood when it was hurt, kept for good. */
	private static void wounds(TomatoDevilEntity e, TomatoBody b, ClientWorld world, Vec3d pos, float now) {
		if (e.hurtTime > b.lastHurt && e.hurtTime >= 8 && b.wounds.size() < 16) {
			PlayerEntity near = world.getClosestPlayer(e, 16.0);
			float yawR = b.yaw * MathHelper.RADIANS_PER_DEGREE;
			Vector3f d;
			if (near != null) {
				Vec3d rel = near.getPos().subtract(pos);
				// into the body's frame (turned by its yaw only: the cut is made standing)
				float x = (float) (rel.x * MathHelper.cos(yawR) + rel.z * MathHelper.sin(yawR));
				float z = (float) (-rel.x * MathHelper.sin(yawR) + rel.z * MathHelper.cos(yawR));
				d = new Vector3f(x, 0.0F, z).normalize();
			} else {
				d = new Vector3f(0, 0, 1);
			}
			long seed = (long) (now * 1000) ^ e.getId() * 31L;
			float jx = ((seed % 97) / 97.0F - 0.5F) * 0.9F;
			float jy = (((seed / 97) % 89) / 89.0F - 0.5F) * 1.1F;
			Vector3f side = new Vector3f(-d.z, 0, d.x);
			Vector3f dir = new Vector3f(d).add(side.mul(jx)).add(0, jy, 0).normalize();
			float k = (float) Math.sqrt((dir.x / 1.3F) * (dir.x / 1.3F) + (dir.y / 1.06F) * (dir.y / 1.06F) + (dir.z / 1.3F) * (dir.z / 1.3F));
			Vector3f at = new Vector3f(dir).div(k);
			Vector3f n = new Vector3f(at.x / 1.69F, at.y / 1.12F, at.z / 1.69F).normalize();
			b.wounds.add(new TomatoBody.Wound(at.x, at.y, at.z, n.x, n.y, n.z, (seed % 360) * MathHelper.RADIANS_PER_DEGREE * 0.5F - 0.8F,
					0.5F + ((seed / 7) % 5) * 0.08F, now));
		}
		b.lastHurt = e.hurtTime;
	}

	private static void drawWounds(TomatoBody b, MatrixStack matrices, VertexConsumerProvider provider, Matrix4f bodyM, int light) {
		if (b.wounds.isEmpty()) {
			return;
		}
		VertexConsumer vc = provider.getBuffer(RenderLayer.getEntityCutoutNoCull(GASH));
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f m = new Matrix4f(entry.getPositionMatrix()).mul(bodyM);
		Matrix3f nm = new Matrix3f(entry.getNormalMatrix()).mul(new Matrix3f(bodyM));
		for (TomatoBody.Wound w : b.wounds) {
			Vector3f n = new Vector3f(w.nx(), w.ny(), w.nz());
			Vector3f t = new Vector3f(0, 1, 0).cross(n);
			if (t.lengthSquared() < 1.0E-4F) {
				t = new Vector3f(1, 0, 0);
			}
			t.normalize();
			Vector3f bt = new Vector3f(n).cross(t);
			float c = MathHelper.cos(w.angle());
			float s = MathHelper.sin(w.angle());
			Vector3f along = new Vector3f(t).mul(c).add(new Vector3f(bt).mul(s)).mul(w.length() * 0.5F);
			Vector3f across = new Vector3f(n).cross(along).normalize().mul(0.14F);
			Vector3f o = new Vector3f(w.x(), w.y(), w.z()).add(new Vector3f(n).mul(0.03F));
			Vector3f nn = nm.transform(new Vector3f(n)).normalize();
			quadV(vc, m, nn, new Vector3f(o).sub(along).sub(across), 0, 1, light);
			quadV(vc, m, nn, new Vector3f(o).add(along).sub(across), 1, 1, light);
			quadV(vc, m, nn, new Vector3f(o).add(along).add(across), 1, 0, light);
			quadV(vc, m, nn, new Vector3f(o).sub(along).add(across), 0, 0, light);
		}
	}

	private static void quadV(VertexConsumer vc, Matrix4f m, Vector3f n, Vector3f p, float u, float v, int light) {
		vc.vertex(m, p.x, p.y, p.z).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n.x, n.y, n.z);
	}

	/** Arm {@code i}: shoulder, elbow, wrist, hand. */
	private static void drawArm(TomatoDevilEntity e, TomatoBody b, ClientWorld world, FleshMesh parts, Matrix4f base, Matrix4f bodyM, FleshMesh.Look look,
			Pose p, int i, float now, Vec3d pos) {
		Vector3f o = out(i);
		Vector3f shoulder = bodyM.transformPosition(new Vector3f(o.x * SHOULDER_R, SHOULDER_Y, o.z * SHOULDER_R));
		// The walk's hand (the world, relative to the entity's place).
		double[] h = b.walkHand(i, now);
		Vector3f wrist = new Vector3f((float) (h[0] - pos.x), (float) (h[1] - pos.y + WRIST_UP), (float) (h[2] - pos.z));
		double handYaw = h[3];
		Vector3f fingers = new Vector3f((float) Math.sin(handYaw), 0.0F, (float) Math.cos(handYaw));
		Vector3f backV = new Vector3f(0, 1, 0);
		float pitch = (float) h[4];
		float curl = (float) h[5];
		// The pole: out from the body and up, so the elbow stands out like a crouched knee.
		Vector3f bodyMid = bodyM.transformPosition(new Vector3f(0, 0, 0));
		Vector3f outW = new Vector3f(shoulder).sub(bodyMid);
		outW.y = 0.0F;
		if (outW.lengthSquared() < 1.0E-4F) {
			outW.set(o);
		}
		outW.normalize();
		Vector3f pole = new Vector3f(outW).add(0.0F, 1.1F, 0.0F);
		float w = p.w[i];
		if (w > 0.0F && p.wrist[i] != null) {
			Vector3f posed;
			if (p.inWorld[i]) {
				// a landing on the ground in front: the body's yaw only, from the entity's place
				float yawR = -b.yaw * MathHelper.RADIANS_PER_DEGREE;
				Vector3f lw = new Vector3f(p.wrist[i]);
				posed = new Matrix4f().rotateY(yawR).transformPosition(new Vector3f(lw.x, 0.0F, lw.z));
				posed.y = (float) (TomatoBody.ground(world, pos.add(posed.x, 0, posed.z), pos.y) - pos.y + WRIST_UP) + lw.y;
			} else {
				posed = bodyM.transformPosition(new Vector3f(p.wrist[i]));
			}
			wrist.lerp(posed, w);
			Vector3f pp = bodyM.transformDirection(new Vector3f(p.pole[i])).normalize();
			pole.lerp(pp, w);
			if (p.fingers[i] != null) {
				Vector3f fw = p.inWorld[i] ? new Matrix4f().rotateY(-b.yaw * MathHelper.RADIANS_PER_DEGREE).transformDirection(new Vector3f(p.fingers[i]))
						: bodyM.transformDirection(new Vector3f(p.fingers[i]));
				fingers.lerp(fw.normalize(), w).normalize();
				Vector3f bw = p.inWorld[i] ? new Vector3f(p.back[i]) : bodyM.transformDirection(new Vector3f(p.back[i]));
				backV.lerp(bw.normalize(), w).normalize();
			}
			pitch *= 1.0F - w;
			curl = MathHelper.lerp(w, curl, p.curl[i]);
		}
		// Keep the drawn hand for the walk to take back.
		TomatoBody.Foot f = b.feet[i];
		f.drawn = new Vec3d(pos.x + wrist.x, pos.y + wrist.y - WRIST_UP, pos.z + wrist.z);
		f.drawnYaw = Math.atan2(fingers.x, fingers.z);

		// Two bones.
		Vector3f d = new Vector3f(wrist).sub(shoulder);
		float len = d.length();
		if (len > REACH) {
			// Out of reach: the hand is pulled in along the line (it slides, rarely).
			d.mul(REACH / len);
			wrist.set(shoulder).add(d);
			len = REACH;
		}
		len = Math.max(len, 0.3F);
		Vector3f u = new Vector3f(d).normalize();
		float cosA = (UPPER * UPPER + len * len - FORE * FORE) / (2.0F * UPPER * len);
		float a = (float) Math.acos(MathHelper.clamp(cosA, -1.0F, 1.0F));
		Vector3f pr = new Vector3f(pole).sub(new Vector3f(u).mul(pole.dot(u)));
		if (pr.lengthSquared() < 1.0E-5F) {
			pr = new Vector3f(outW);
		}
		pr.normalize();
		Vector3f elbow = new Vector3f(shoulder).add(new Vector3f(u).mul(MathHelper.cos(a) * UPPER)).add(new Vector3f(pr).mul(MathHelper.sin(a) * UPPER));
		FleshMesh.Part upper = parts.byName.get("upper");
		FleshMesh.Part fore = parts.byName.get("fore");
		segment(parts, upper, base, shoulder, elbow, pr, look);
		segment(parts, fore, base, elbow, wrist, pr, look);
		// The hand: fingers along {@code fingers}, the back of the hand up along {@code backV}, then dropped at the wrist by the pitch.
		Vector3f z = new Vector3f(fingers).sub(new Vector3f(backV).mul(fingers.dot(backV)));
		if (z.lengthSquared() < 1.0E-5F) {
			z = new Vector3f(outW);
		}
		z.normalize();
		Vector3f y = new Vector3f(backV).normalize();
		Vector3f x = new Vector3f(y).cross(z).normalize();
		y = new Vector3f(z).cross(x).normalize();
		Vector3f fwdBody = bodyM.transformDirection(new Vector3f(0, 0, 1)).normalize();
		boolean right = x.dot(fwdBody) >= 0.0F;
		Matrix4f hand = new Matrix4f(base).translate(wrist).mul(new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, 0, 0, 0, 1)).rotateX(pitch).scale(1.2F);
		FleshMesh.Part hp = parts.byName.get(right ? "hand_r" : "hand_l");
		FleshMesh.Part fp = parts.byName.get(right ? "fingers_r" : "fingers_l");
		if (hp != null && fp != null) {
			parts.drawPart(hp, hand, look, base);
			Matrix4f fm = new Matrix4f(hand).translate(fp.pivot).rotateX(curl).translate(-fp.pivot.x, -fp.pivot.y, -fp.pivot.z);
			parts.drawPart(fp, fm, look, base);
		}
	}

	/** One bone's piece from {@code a} to {@code b}: its +Y along the bone, its -Z toward the bend's outside ({@code out}). */
	private static void segment(FleshMesh parts, FleshMesh.Part part, Matrix4f base, Vector3f a, Vector3f b, Vector3f out, FleshMesh.Look look) {
		if (part == null) {
			return;
		}
		Vector3f y = new Vector3f(b).sub(a);
		if (y.lengthSquared() < 1.0E-6F) {
			return;
		}
		y.normalize();
		Vector3f z = new Vector3f(out).negate();
		z.sub(new Vector3f(y).mul(z.dot(y)));
		if (z.lengthSquared() < 1.0E-6F) {
			z = new Vector3f(1, 0, 0).cross(y);
		}
		z.normalize();
		Vector3f x = new Vector3f(y).cross(z).normalize();
		Matrix4f m = new Matrix4f(base).translate(a).mul(new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, 0, 0, 0, 1)).scale(1.25F, 1.0F, 1.25F);
		parts.drawPart(part, m, look, base);
	}
}
