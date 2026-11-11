package io.github.kortev.chainsawman.client.devil;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.github.kortev.chainsawman.client.FleshMesh;
import io.github.kortev.chainsawman.devil.Growth;
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
	static final double WRIST_UP = 0.12;
	static final float EYE_R = 0.2F;
	/** How big the hands are drawn (round 1's 1.2, then 1.3 times that), and the arms' thickness across. */
	static final float HAND_SCALE = 1.56F;
	static final float ARM_THICK = 1.34F;
	/** The lids: turned back out of sight open (radians), and the lower one's way. */
	static final float LID_OPEN = -1.15F;
	static final Identifier STRAND = Identifier.of("chainsawman", "textures/entity/tomato_strand.png");

	record Eye(Vector3f c, Vector3f n, float r) {
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
				p.forward = -0.25F * rear * (1.0F - down) + 0.8F * down * (1.0F - settle);
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
						Vector3f landed = new Vector3f(sx, 0.0F, 3.5F + (i == 0 || i == 7 ? 0.5F : 0.0F));
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
				// (No bounce: it drops and lies there, heavy.)
				p.height = LOW - 0.05F;
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
			case TomatoDevilEntity.HOP -> {
				// Down low on all eight (the hands stay planted, the elbows bend out), a tremble; up; down hard.
				float squat = ramp(t, 0.0F, 12.0F);
				float u = MathHelper.clamp((t - TomatoDevilEntity.HOP_TELL) / TomatoDevilEntity.HOP_AIR, 0.0F, 1.0F);
				float rise = ramp(t, TomatoDevilEntity.HOP_LAND, TomatoDevilEntity.HOP_END);
				if (t < TomatoDevilEntity.HOP_TELL) {
					p.height = STAND - 1.05F * squat;
					p.shake = 0.025F * squat;
					p.mouth = 0.06F + 0.12F * squat;
				} else if (t < TomatoDevilEntity.HOP_LAND) {
					float base = MathHelper.lerp(u, STAND - 1.05F, STAND - 0.55F);
					// fast up out of the squat, a hang at the top, fast down
					p.height = base + 2.6F * MathHelper.sin(MathHelper.PI * u);
					p.mouth = 0.35F;
				} else {
					p.height = MathHelper.lerp(rise, STAND - 0.55F, STAND);
				}
				float air = ramp(t, TomatoDevilEntity.HOP_TELL, TomatoDevilEntity.HOP_TELL + 2.0F) * (1.0F - ramp(t, TomatoDevilEntity.HOP_LAND - 3.0F, TomatoDevilEntity.HOP_LAND));
				if (air > 0.0F) {
					for (int i = 0; i < TomatoBody.ARMS; i++) {
						Vector3f o = out(i);
						// hanging under it in the air, fingers down and curled
						Vector3f at = new Vector3f(o).mul(1.45F).add(0.0F, -1.95F, 0.0F);
						p.arm(i, air, at, new Vector3f(o.x, 0.6F, o.z), new Vector3f(o.x, -1.0F, o.z).normalize(), new Vector3f(o), 0.9F);
					}
				}
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
			boolean onBack = state == TomatoDevilEntity.ON_BACK || state == TomatoDevilEntity.GET_UP;
			for (int i = 0; i < TomatoBody.ARMS; i++) {
				Vector3f o = out(i);
				if (onBack) {
					// On its back: the arms fold in tight over its belly like a dead spider's, elbows up, hands clenched.
					// (Its frame is upside down: -Y is up. The elbows stick out and up, crooked; the hands come back in over the belly.)
					Vector3f at = new Vector3f(o).mul(0.5F).add(0, -1.35F, 0);
					p.arm(i, ease(d * 2.2F), at, new Vector3f(o.x, 0.2F, o.z), new Vector3f(-o.x, 0.6F, -o.z).normalize(), new Vector3f(o.x, -1.0F, o.z).normalize(),
							0.8F + 0.9F * d);
				} else {
					// Upright: it sinks and the arms curl in under it.
					Vector3f at = new Vector3f(o).mul(0.55F).add(0, -1.25F, 0);
					p.arm(i, ease(d * 1.6F), at, new Vector3f(o.x, -0.2F, o.z), new Vector3f(-o.x, -0.5F, -o.z).normalize(), new Vector3f(o.x, -0.3F, o.z).normalize(),
							0.4F + 1.2F * d);
				}
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
		// Grown: everything drawn bigger about its feet (the hands still planted where they stand in the world, see drawArm).
		int g = e.growthLevel();
		float gs = e.getScale();
		matrices.push();
		matrices.scale(gs, gs, gs);
		this.shadowRadius = 1.3F * gs;
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
		// Just grown a step in front of someone: it shudders, swells and settles, every eye wide.
		float sinceGrew = (float) (world.getTime() - e.grewAt()) + delta;
		boolean shudder = sinceGrew >= 0.0F && sinceGrew < Growth.SHUDDER && !e.isDead();
		if (shudder) {
			float u = sinceGrew / Growth.SHUDDER;
			p.shake += 0.075F * (1.0F - u);
			p.scale *= 1.0F + 0.09F * MathHelper.sin(MathHelper.PI * Math.min(1.0F, u * 1.5F));
			p.mouth = Math.max(p.mouth, 0.3F * MathHelper.sin(MathHelper.PI * u));
		}
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
		FleshMesh.Look skin = TomatoGrowthLook.skin(look, g, now);
		FleshMesh.Look arms = TomatoGrowthLook.arms(look, g);
		FleshMesh body = FleshMesh.file("tomato_body");
		FleshMesh parts = FleshMesh.file("tomato_parts");
		body.draw(bodyView, now, skin, null);
		// The mouth breathes: parted a little, opening and shutting slowly (one breath in 64 ticks, the breathing sound's length).
		float breath = 0.5F + 0.5F * MathHelper.sin(now * MathHelper.TAU / 64.0F + e.getId());
		// (Grown, it hangs open wider, drooling, each step.)
		float mouth = p.mouth <= 0.0601F ? 0.03F + 0.045F * g + 0.11F * breath * breath : p.mouth;
		lips(body, bodyView, base, skin, mouth, provider, light, now);
		if (!e.isDead() && world.random.nextFloat() < 0.004F) {
			world.playSound(pos.x, pos.y + STAND * gs, pos.z, io.github.kortev.chainsawman.devil.Devils.Sounds.CLICK, net.minecraft.sound.SoundCategory.HOSTILE, 0.5F,
					(0.8F + world.random.nextFloat() * 0.3F) * Growth.voice(g), false);
		}
		drawEyes(e, b, world, parts, base, bodyM, bodyView, look, skin, p, now, pos, delta, g, gs, shudder);
		drawWounds(b, matrices, provider, bodyM, light);
		TomatoGrowthLook.seams(g, now, matrices, provider, bodyM, light);
		if (!e.isDead() && !e.isInvisible()) {
			TomatoGrowthLook.drip(e, world, g, bodyM, pos, gs);
		}

		// ---- The arms.
		for (int i = 0; i < TomatoBody.ARMS; i++) {
			drawArm(e, b, world, parts, base, bodyM, arms, p, i, now, pos, gs);
		}
		matrices.pop();
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

	/** The mouth's shape (as the tool bakes it): its middle's height, half its height, half the gap between the lips' edges, the body's radii. */
	private static final float MOUTH_Y = -0.02F;
	private static final float MOUTH_HY = 0.80F;
	private static final float SLIT = 0.21F;

	private static void lips(FleshMesh body, Matrix4f bodyView, Matrix4f base, FleshMesh.Look look, float open, VertexConsumerProvider provider, int light,
			float now) {
		float a = 1.25F * MathHelper.clamp(open, 0.0F, 1.0F);
		Matrix4f[] ms = new Matrix4f[2];
		int k = 0;
		for (String name : new String[] {"lip_l", "lip_r"}) {
			FleshMesh.Part part = body.byName.get(name);
			if (part == null) {
				return;
			}
			float s = name.equals("lip_l") ? -1.0F : 1.0F;
			Matrix4f m = new Matrix4f(bodyView).translate(part.pivot).rotateY(s * a).translate(-part.pivot.x, -part.pivot.y, -part.pivot.z);
			body.drawPart(part, m, look, base);
			ms[k++] = m;
		}
		if (open > 0.5F) {
			return;
		}
		// Strands of spit and juice between the lips, stretching as they part, swaying as it breathes.
		VertexConsumer vc = provider.getBuffer(RenderLayer.getEntityTranslucent(STRAND));
		Matrix4f inv = new Matrix4f(base).invert();
		float[] ys = {-0.42F, -0.1F, 0.18F, 0.46F};
		for (int i = 0; i < ys.length; i++) {
			float y = MOUTH_Y + ys[i] * MOUTH_HY / 0.8F * 0.8F;
			float tt = (y - MOUTH_Y) / MOUTH_HY;
			float sw = SLIT * (float) Math.sqrt(Math.max(0.0F, 1.0F - tt * tt)) + 0.01F;
			float z = 1.3F * (float) Math.sqrt(Math.max(0.0F, 1.0F - (y / 1.02F) * (y / 1.02F) - 0.02F)) - 0.06F;
			// in the view (from the lips' own frames), back into the entity's frame for the buffer
			Vector3f l = inv.transformPosition(ms[0].transformPosition(new Vector3f(-sw - 0.03F, y, z)));
			Vector3f r = inv.transformPosition(ms[1].transformPosition(new Vector3f(sw + 0.03F, y + 0.02F * (i % 2), z)));
			float span = l.distance(r);
			float sag = 0.05F + 0.25F * span * span + 0.02F * MathHelper.sin(now * 0.21F + i * 1.7F);
			float wid = 0.016F / (1.0F + 3.0F * span);
			int segs = 6;
			Vector3f prev = null;
			for (int q = 0; q <= segs; q++) {
				float u = q / (float) segs;
				Vector3f pt = new Vector3f(l).lerp(r, u).add(0.0F, -sag * 4.0F * u * (1.0F - u), 0.0F);
				if (prev != null) {
					strand(vc, new Matrix4f(base), prev, pt, wid, light);
				}
				prev = pt;
			}
		}
	}

	/** One piece of a strand: a thin ribbon facing up and toward the camera well enough (two crossed quads). */
	private static void strand(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, float w, int light) {
		for (int pass = 0; pass < 2; pass++) {
			Vector3f off = pass == 0 ? new Vector3f(0, w, 0) : new Vector3f(new Vector3f(b).sub(a)).cross(0, 1, 0).normalize(w);
			quadS(vc, m, new Vector3f(a).sub(off), 0, 0, light);
			quadS(vc, m, new Vector3f(b).sub(off), 1, 0, light);
			quadS(vc, m, new Vector3f(b).add(off), 1, 1, light);
			quadS(vc, m, new Vector3f(a).add(off), 0, 1, light);
		}
	}

	private static void quadS(VertexConsumer vc, Matrix4f m, Vector3f p, float u, float v, int light) {
		vc.vertex(m, p.x, p.y, p.z).color(255, 255, 255, 200).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
	}

	private static void drawEyes(TomatoDevilEntity e, TomatoBody b, ClientWorld world, FleshMesh parts, Matrix4f base, Matrix4f bodyM, Matrix4f bodyView,
			FleshMesh.Look look, FleshMesh.Look skin, Pose p, float now, Vec3d pos, float delta, int grown, float gs, boolean shudder) {
		FleshMesh.Part eye = parts.byName.get("eye");
		if (eye == null) {
			return;
		}
		FleshMesh.Part lid = parts.byName.get("lid");
		FleshMesh.Part pupilPart = parts.byName.get("pupil");
		// What they look at: the nearest player's eyes, in the body's own frame.
		PlayerEntity near = world.getClosestPlayer(e, 32.0);
		Vector3f target = null;
		if (near != null) {
			Vec3d ep = near.getLerpedPos(delta).add(0.0, near.getStandingEyeHeight(), 0.0).subtract(pos).multiply(1.0 / gs);
			target = new Matrix4f(bodyM).invert().transformPosition(new Vector3f((float) ep.x, (float) ep.y, (float) ep.z));
		}
		float health = e.getHealth() / e.getMaxHealth();
		FleshMesh.Look burst = new FleshMesh.Look();
		burst.r = look.r * 0.45F;
		burst.g = look.g * 0.06F;
		burst.b = look.b * 0.05F;
		burst.rim = 0.2F;
		// Eyeshine: the eyeballs keep a pale light of their own in the dark, so at night the cluster of eyes shows before the body.
		FleshMesh.Look shine = new FleshMesh.Look();
		shine.r = Math.max(look.r, 0.62F);
		shine.g = Math.max(look.g, 0.6F);
		shine.b = Math.max(look.b, 0.56F);
		shine.rim = look.rim;
		shine.hurt = look.hurt;
		// Grown: the whites go bloodshot, and each step a red rim swells round every eye.
		TomatoGrowthLook.bloodshot(shine, grown);
		FleshMesh.Look rimLook = TomatoGrowthLook.rim(look, grown, now);
		FleshMesh.Look iris = TomatoGrowthLook.iris(look, grown);
		List<Eye> list = eyes();
		int n = list.size();
		if (b.gaze.length != n) {
			b.gaze = new float[n][];
			b.lookTo = new float[n][];
			b.nextLook = new float[n];
			for (int k = 0; k < n; k++) {
				Eye ey = list.get(k);
				b.gaze[k] = new float[] {ey.n.x, ey.n.y, ey.n.z};
				b.lookTo[k] = new float[] {ey.n.x, ey.n.y, ey.n.z};
				b.nextLook[k] = now + Math.floorMod(k * 53 + e.getId(), 40);
			}
		}
		float dtE = Float.isNaN(b.lastEyes) ? 0.0F : MathHelper.clamp(now - b.lastEyes, 0.0F, 4.0F);
		b.lastEyes = now;
		long noticed = e.noticedAt();
		boolean aware = noticed >= 0 && near != null && !p.dazed;
		float sinceNotice = noticed >= 0 ? (float) (world.getTime() - noticed) + delta : 1000.0F;
		// The pupils: wide while it wanders, shrinking to points the moment it has you.
		float wantPupil = shudder ? 0.35F : aware ? (sinceNotice < 30.0F ? 0.42F : 0.55F) : 1.0F;
		b.pupil += (wantPupil - b.pupil) * Math.min(1.0F, dtE * (aware ? 0.6F : 0.08F));
		for (int k = 0; k < n; k++) {
			Eye ey = list.get(k);
			boolean popped = health < 0.4F && k % 3 == 0 || health < 0.22F && k % 3 == 1;
			Vector3f dir;
			if (p.dazed) {
				dir = new Vector3f(MathHelper.sin(now * 0.5F + k), MathHelper.cos(now * 0.37F + k * 2.0F), 0.4F).normalize();
				dir = new Vector3f(ey.n).add(dir.mul(0.9F)).normalize();
			} else if (aware && target != null) {
				// Every eye on you, at once.
				dir = new Vector3f(target).sub(ey.c).normalize();
				float k2 = Math.min(1.0F, dtE * 0.85F);
				float[] g = b.gaze[k];
				g[0] += (dir.x - g[0]) * k2;
				g[1] += (dir.y - g[1]) * k2;
				g[2] += (dir.z - g[2]) * k2;
				dir = new Vector3f(g[0], g[1], g[2]).normalize();
			} else {
				// Its own: each eye wanders where it likes, out of step with the rest, darting from one place to the next with wet twitches.
				if (now >= b.nextLook[k]) {
					long h = (long) (now * 7.0F) * 31L + k * 977L + e.getId() * 131L;
					float ax = ((Math.floorMod(h, 1000)) / 1000.0F - 0.5F) * 1.6F;
					float ay = ((Math.floorMod(h / 1000, 1000)) / 1000.0F - 0.5F) * 1.2F;
					Vector3f up = new Vector3f(0, 1, 0).sub(new Vector3f(ey.n).mul(ey.n.y));
					if (up.lengthSquared() < 1.0E-4F) {
						up.set(0, 0, 1);
					}
					up.normalize();
					Vector3f sideV = new Vector3f(ey.n).cross(up).normalize();
					Vector3f t2 = new Vector3f(ey.n).add(sideV.mul(ax)).add(up.mul(ay)).normalize();
					b.lookTo[k] = new float[] {t2.x, t2.y, t2.z};
					b.nextLook[k] = now + 12.0F + Math.floorMod(h / 7, 50);
				}
				float[] g = b.gaze[k];
				float[] to = b.lookTo[k];
				float k2 = Math.min(1.0F, dtE * 0.55F);
				g[0] += (to[0] - g[0]) * k2;
				g[1] += (to[1] - g[1]) * k2;
				g[2] += (to[2] - g[2]) * k2;
				dir = new Vector3f(g[0], g[1], g[2]);
				// a tremor now and then
				float tw = MathHelper.sin(now * 2.7F + k * 5.1F) * MathHelper.sin(now * 0.31F + k);
				if (tw > 0.85F) {
					dir.add(0.05F * MathHelper.sin(now * 9.0F + k), 0.04F * MathHelper.cos(now * 11.0F + k), 0.0F);
				}
				dir.normalize();
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
			if (aware && sinceNotice < 26.0F || shudder) {
				// Noticing (or growing): wide open, no blinking.
				bl = 1.0F;
			}
			// Young, some of its eyes are still shut (a twitch under the lid now and then); each step opens more of them.
			boolean shut = !shudder && !popped && !TomatoGrowthLook.open(k, grown);
			if (shut) {
				bl = 0.05F * Math.max(0.0F, MathHelper.sin(now * 0.9F + k * 3.7F) * MathHelper.sin(now * 0.13F + k));
			}
			float s = ey.r / EYE_R * TomatoGrowthLook.eyeSize(grown);
			Matrix4f m = new Matrix4f(bodyView).translate(ey.c)
					.mul(new Matrix4f(xc.x, xc.y, xc.z, 0, yc.x, yc.y, yc.z, 0, zc.x, zc.y, zc.z, 0, 0, 0, 0, 1)).scale(s, s, s * (popped ? 0.6F : 1.0F));
			if (popped) {
				Matrix4f pm = new Matrix4f(m).scale(1.0F, 0.35F, 1.0F);
				parts.drawPart(eye, pm, burst, base);
				continue;
			}
			parts.drawPart(eye, m, shine, base);
			if (pupilPart != null && !shut) {
				float ps = b.pupil * (0.9F + 0.06F * MathHelper.sin(now * 0.2F + k));
				parts.drawPart(pupilPart, new Matrix4f(m).scale(ps, ps, 1.0F), iris, base);
			}
			if (lid != null) {
				// The lids, in the socket's frame (not the gaze's): turned back out of sight, they close over the ball to blink.
				Vector3f nz = new Vector3f(ey.n);
				Vector3f nx = new Vector3f(0, 1, 0).cross(nz);
				if (nx.lengthSquared() < 1.0E-4F) {
					nx.set(1, 0, 0);
				}
				nx.normalize();
				Vector3f ny = new Vector3f(nz).cross(nx).normalize();
				Matrix4f sock = new Matrix4f(bodyView).translate(ey.c).mul(new Matrix4f(nx.x, nx.y, nx.z, 0, ny.x, ny.y, ny.z, 0, nz.x, nz.y, nz.z, 0, 0, 0, 0, 1)).scale(s);
				if (rimLook != null && !shut) {
					// The rim: a raw red ring of swollen flesh round the ball (its two lids shut, bigger, sunk back behind it: the ball's own
					// paint glows, so a copy of it can't be tinted; the lids are flesh).
					float rs = TomatoGrowthLook.rimSize(grown);
					Matrix4f rm = new Matrix4f(sock).translate(0.0F, 0.0F, -0.45F * EYE_R).scale(rs, rs, 0.8F);
					parts.drawPart(lid, rm, rimLook, base);
					parts.drawPart(lid, new Matrix4f(rm).rotateZ(MathHelper.PI), rimLook, base);
				}
				float closed = 1.0F - bl;
				float a = LID_OPEN * (1.0F - closed);
				parts.drawPart(lid, new Matrix4f(sock).rotateX(a), skin, base);
				parts.drawPart(lid, new Matrix4f(sock).rotateZ(MathHelper.PI).rotateX(a), skin, base);
			}
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
			Pose p, int i, float now, Vec3d pos, float gs) {
		Vector3f o = out(i);
		Vector3f shoulder = bodyM.transformPosition(new Vector3f(o.x * SHOULDER_R, SHOULDER_Y, o.z * SHOULDER_R));
		// The walk's hand (the world, relative to the entity's place).
		double[] h = b.walkHand(i, now);
		// (In the scaled frame: a grown devil's hand is drawn exactly where it is planted in the world.)
		Vector3f wrist = new Vector3f((float) ((h[0] - pos.x) / gs), (float) ((h[1] - pos.y) / gs + WRIST_UP), (float) ((h[2] - pos.z) / gs));
		double handYaw = h[3];
		// A standing hand feeling about: up off the ground a little and across.
		float[] feel = b.feel(i, now, e.getId(), e.state() == TomatoDevilEntity.WALK && !e.isDead() && b.vel.length() < 0.02);
		if (feel[0] != 0.0F || feel[1] != 0.0F) {
			wrist.add((float) Math.cos(handYaw) * feel[1], feel[0], (float) -Math.sin(handYaw) * feel[1]);
		}
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
				posed.y = (float) ((TomatoBody.ground(world, pos.add(posed.x * gs, 0, posed.z * gs), pos.y) - pos.y) / gs + WRIST_UP) + lw.y;
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
		f.drawn = new Vec3d(pos.x + wrist.x * gs, pos.y + (wrist.y - WRIST_UP) * gs, pos.z + wrist.z * gs);
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
		Matrix4f hand = new Matrix4f(base).translate(wrist).mul(new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, 0, 0, 0, 1)).rotateX(pitch).scale(HAND_SCALE);
		FleshMesh.Part hp = parts.byName.get(right ? "hand_r" : "hand_l");
		if (hp == null) {
			return;
		}
		parts.drawPart(hp, hand, look, base);
		// Each finger on its own: they land and press in one after another; a standing hand taps and flexes; posed, they curl as the pose says.
		boolean still = e.state() == TomatoDevilEntity.WALK && !e.isDead() && b.vel.length() < 0.02;
		float side = right ? 1.0F : -1.0F;
		for (int j = 0; j < 4; j++) {
			FleshMesh.Part fp = parts.byName.get((right ? "finger_r" : "finger_l") + j);
			if (fp == null) {
				continue;
			}
			float[] fc = b.finger(i, j, now, e.getId(), still);
			float c = MathHelper.lerp(w, fc[0], curl + 0.08F * j);
			float spread = MathHelper.lerp(w, fc[1], 0.5F);
			float splay = side * spread * (1.5F - j) * 0.13F;
			Matrix4f fm = new Matrix4f(hand).translate(fp.pivot).rotateY(splay).rotateX(c).translate(-fp.pivot.x, -fp.pivot.y, -fp.pivot.z);
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
		Matrix4f m = new Matrix4f(base).translate(a).mul(new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, 0, 0, 0, 1)).scale(ARM_THICK, 1.0F, ARM_THICK);
		parts.drawPart(part, m, look, base);
	}
}
