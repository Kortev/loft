package io.github.kortev.gojolander.client;

import io.github.kortev.gojolander.GojoSounds;
import io.github.kortev.gojolander.Gojolander;
import io.github.kortev.gojolander.network.PurpleEndPayload;
import io.github.kortev.gojolander.network.PurpleLaunchPayload;
import io.github.kortev.gojolander.purple.PurpleTimeline;
import io.github.kortev.shootingstar.client.ClientConfig;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

/**
 * Every Hollow Purple this client knows of: their state, their sounds on their cues, the dust and sparks they throw
 * (the world's own change, the erased tunnel, comes from the server; the light is {@link PurpleRender}), and for the
 * caster the shots ({@link PurpleCamera}), which take his hands off the keys.
 */
public final class ClientPurples {
	private static final Map<Integer, ClientPurple> PURPLES = new LinkedHashMap<>();
	private static final Random RANDOM = Random.create();
	/** How far off its sounds and dust are bothered with. */
	private static final double HEARD = 200.0;
	/** How long the merge's sound runs before its crack: it is played that long before the orbs meet. */
	private static final int MERGE_LEAD = 14;
	private static final DustParticleEffect VIOLET = new DustParticleEffect(new Vector3f(0.55F, 0.16F, 1.0F), 2.2F);
	private static final DustParticleEffect LILAC = new DustParticleEffect(new Vector3f(0.82F, 0.62F, 1.0F), 1.3F);
	private static final Identifier VOICE = Gojolander.id("purple.voice");

	private ClientPurples() {
	}

	public static Collection<ClientPurple> all() {
		return PURPLES.values();
	}

	/** The Purple this client's player is casting, until its sphere is gone. */
	@Nullable
	public static ClientPurple mine() {
		for (ClientPurple purple : PURPLES.values()) {
			if (purple.mine && !purple.cancelled && purple.age < purple.end()) {
				return purple;
			}
		}
		return null;
	}

	/** Whether the camera's shots of the caster run: his own Purple, and shots wanted. */
	public static boolean shooting() {
		ClientPurple purple = mine();
		return purple != null && ClientConfig.cameraShots && purple.age < PurpleTimeline.RELEASE + PurpleTimeline.CINEMA;
	}

	/** The Purple {@code caster} is casting, from the key until its sphere is gone. */
	@Nullable
	public static ClientPurple casting(UUID caster) {
		for (ClientPurple purple : PURPLES.values()) {
			if (caster.equals(purple.caster) && !purple.cancelled && purple.age < PurpleTimeline.RELEASE + 16) {
				return purple;
			}
		}
		return null;
	}

	public static void onLaunch(PurpleLaunchPayload payload, MinecraftClient client) {
		if (client.world == null || client.player == null) {
			return;
		}
		UUID caster = payload.caster().equals(PurpleLaunchPayload.NOBODY) ? null : payload.caster();
		boolean mine = client.player.getUuid().equals(caster);
		ClientPurple purple = new ClientPurple(payload.id(), caster, mine, payload.eye(), payload.dir(), payload.radius(), payload.range(),
				payload.age());
		PURPLES.put(purple.id, purple);
		if (payload.age() == 0) {
			cues(client, purple, -1, 0);
		}
	}

	public static void onEnd(PurpleEndPayload payload, MinecraftClient client) {
		ClientPurple purple = PURPLES.get(payload.id());
		if (purple != null) {
			purple.cancelled = true;
		}
	}

	public static void tick(MinecraftClient client) {
		if (PURPLES.isEmpty() || client.world == null) {
			return;
		}
		Iterator<ClientPurple> it = PURPLES.values().iterator();
		while (it.hasNext()) {
			ClientPurple purple = it.next();
			int from = purple.age;
			purple.age++;
			if (!purple.cancelled) {
				cues(client, purple, from, purple.age);
				particles(client, purple);
			}
			if (purple.age > purple.end() + PurpleTimeline.KEEP) {
				it.remove();
			}
		}
	}

	public static void clear() {
		PURPLES.clear();
	}

	// =============================================================================================
	// Sounds
	// =============================================================================================

	private static boolean passed(int from, int to, int mark) {
		return from < mark && to >= mark;
	}

	private static void cues(MinecraftClient client, ClientPurple p, int from, int to) {
		ClientWorld world = client.world;
		if (world == null || client.player == null) {
			return;
		}
		PurplePose.Anchor at = PurplePose.anchor(client, p, 1.0F);
		Vec3d here = at.feet().add(0.0, 1.2, 0.0);
		boolean near = client.gameRenderer.getCamera().getPos().distanceTo(p.eye) < HEARD;
		if (!p.mine && !near) {
			return;
		}
		if (passed(from, to, PurpleTimeline.BLUE)) {
			play(client, p, GojoSounds.PURPLE_BLUE, GojoSounds.PURPLE_BLUE_WORLD, here, 3.0F);
		}
		if (passed(from, to, PurpleTimeline.RED)) {
			play(client, p, GojoSounds.PURPLE_RED, GojoSounds.PURPLE_RED_WORLD, here, 3.0F);
		}
		if (passed(from, to, PurpleTimeline.MERGE - MERGE_LEAD)) {
			play(client, p, GojoSounds.PURPLE_MERGE, GojoSounds.PURPLE_MERGE_WORLD, here, 5.0F);
		}
		if (passed(from, to, PurpleTimeline.MERGE - 2) && hasVoice(client)) {
			// His voice, if there is one in the slot (kortev supplies it).
			if (p.mine) {
				client.getSoundManager().play(PositionedSoundInstance.master(GojoSounds.PURPLE_VOICE, 1.0F, 1.0F));
			} else {
				client.getSoundManager().play(new PositionedSoundInstance(GojoSounds.PURPLE_VOICE, SoundCategory.PLAYERS, 2.0F, 1.0F,
						Random.create(), here.x, here.y, here.z));
			}
		}
		if (passed(from, to, PurpleTimeline.RELEASE)) {
			play(client, p, GojoSounds.PURPLE_RELEASE, GojoSounds.PURPLE_RELEASE_WORLD, p.form, 8.0F);
			// The roar of it carving: heard from where he is, then (for those who watch) from along its path.
			if (p.mine) {
				client.getSoundManager().play(PositionedSoundInstance.master(GojoSounds.PURPLE_ROAR, 1.0F, 1.0F));
			} else {
				Vec3d mid = p.form.add(p.dir.multiply(Math.min(p.range * 0.5, 110.0)));
				client.getSoundManager().play(new PositionedSoundInstance(GojoSounds.PURPLE_ROAR_WORLD, SoundCategory.PLAYERS, 12.0F, 1.0F,
						Random.create(), mid.x, mid.y, mid.z));
			}
		}
		if (passed(from, to, p.arrives())) {
			Vec3d end = p.form.add(p.dir.multiply(p.range));
			if (p.mine) {
				client.getSoundManager().play(PositionedSoundInstance.master(GojoSounds.PURPLE_FADE, 1.0F, 0.9F));
			} else {
				client.getSoundManager().play(new PositionedSoundInstance(GojoSounds.PURPLE_FADE_WORLD, SoundCategory.PLAYERS, 10.0F, 1.0F,
						Random.create(), end.x, end.y, end.z));
			}
		}
	}

	/** Whether there is a clip in the voice slot (sounds.json's purple.voice has none until kortev supplies one). */
	private static boolean hasVoice(MinecraftClient client) {
		WeightedSoundSet set = client.getSoundManager().get(VOICE);
		return set != null && set.getSound(Random.create()) != SoundManager.MISSING_SOUND;
	}

	/** Heard as it is by the caster; by everyone else from {@code where}, placed (louder, to carry across a field). */
	private static void play(MinecraftClient client, ClientPurple p, SoundEvent own, SoundEvent world, Vec3d where, float carry) {
		if (p.mine) {
			client.getSoundManager().play(PositionedSoundInstance.master(own, 1.0F, 1.0F));
		} else {
			client.getSoundManager().play(new PositionedSoundInstance(world, SoundCategory.PLAYERS, carry, 1.0F, Random.create(), where.x,
					where.y, where.z));
		}
	}

	// =============================================================================================
	// Dust and sparks
	// =============================================================================================

	private static void particles(MinecraftClient client, ClientPurple p) {
		ClientWorld world = client.world;
		if (world == null || client.gameRenderer.getCamera().getPos().distanceTo(p.eye) > HEARD + 100.0) {
			return;
		}
		PurplePose.Anchor at = PurplePose.anchor(client, p, 1.0F);
		double t = p.age;
		if (t < PurpleTimeline.MERGE) {
			orbs(world, p, at, t);
		} else if (t < PurpleTimeline.RELEASE) {
			forming(world, p, at, t);
		} else if (t < p.arrives() + 2) {
			flying(world, p, t);
		}
	}

	/** Blue pulling dust, loose blocks and sparks in; Red throwing sparks and heat out. */
	private static void orbs(ClientWorld world, ClientPurple p, PurplePose.Anchor at, double t) {
		double blue = PurpleTimeline.grow(t, PurpleTimeline.BLUE, 14.0);
		double red = PurpleTimeline.grow(t, PurpleTimeline.RED, 12.0);
		if (blue > 0.05) {
			Vec3d c = PurplePose.hand(p, true, t, at);
			int n = (int) (2 + 5 * blue);
			for (int i = 0; i < n; i++) {
				Vec3d from = c.add(unit().multiply(2.0 + RANDOM.nextDouble() * 2.6));
				Vec3d v = c.subtract(from).multiply(0.14 + RANDOM.nextDouble() * 0.08);
				world.addParticle(RANDOM.nextInt(2) == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y, from.z, v.x,
						v.y, v.z);
			}
			// Loose blocks off the ground, drawn in.
			BlockPos under = BlockPos.ofFloored(at.feet()).down();
			BlockState ground = world.getBlockState(under);
			if (!ground.isAir() && RANDOM.nextInt(2) == 0) {
				Vec3d from = at.feet().add((RANDOM.nextDouble() - 0.5) * 3.0, 0.1, (RANDOM.nextDouble() - 0.5) * 3.0);
				Vec3d v = c.subtract(from).multiply(0.07);
				world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), from.x, from.y, from.z, v.x, v.y + 0.12, v.z);
			}
		}
		if (red > 0.05) {
			Vec3d c = PurplePose.hand(p, false, t, at);
			int n = (int) (2 + 5 * red);
			for (int i = 0; i < n; i++) {
				Vec3d out = unit();
				Vec3d from = c.add(out.multiply(0.3));
				Vec3d v = out.multiply(0.18 + RANDOM.nextDouble() * 0.2);
				world.addParticle(RANDOM.nextInt(6) == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SMALL_FLAME, from.x, from.y, from.z, v.x, v.y, v.z);
			}
			if (RANDOM.nextInt(2) == 0) {
				world.addParticle(ParticleTypes.CRIMSON_SPORE, c.x, c.y, c.z, (RANDOM.nextDouble() - 0.5) * 0.3, (RANDOM.nextDouble() - 0.5) * 0.3,
						(RANDOM.nextDouble() - 0.5) * 0.3);
			}
		}
	}

	/** The collision and the sphere swelling: a burst of violet from where they met, then dragged in and held. */
	private static void forming(ClientWorld world, ClientPurple p, PurplePose.Anchor at, double t) {
		Vec3d merge = PurplePose.meet(p, at);
		double since = t - PurpleTimeline.MERGE;
		if (since < 4.0) {
			for (int i = 0; i < 70; i++) {
				Vec3d out = unit();
				Vec3d v = out.multiply(0.5 + RANDOM.nextDouble() * 1.0);
				world.addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.DRAGON_BREATH, merge.x, merge.y, merge.z, v.x, v.y, v.z);
			}
		}
		Vec3d c = p.sphereAt(t, merge);
		double r = p.sphereRadius(t);
		for (int i = 0; i < 14; i++) {
			Vec3d out = unit();
			Vec3d from = c.add(out.multiply(r * (1.1 + RANDOM.nextDouble() * 0.6)));
			Vec3d v = out.multiply(-0.12);
			world.addParticle(i % 2 == 0 ? VIOLET : LILAC, from.x, from.y, from.z, v.x, v.y, v.z);
		}
	}

	/** The sphere eating its way through: violet dust off the new wall, the debris thrown off it, sparks round the sphere. */
	private static void flying(ClientWorld world, ClientPurple p, double t) {
		double front = p.front(t);
		double from = Math.max(0.0, Math.min(front, p.dusted));
		double to = front;
		p.dusted = to;
		if (to <= from) {
			return;
		}
		double r = p.radius;
		Vec3d dir = p.dir;
		Vec3d up = Math.abs(dir.y) < 0.95 ? new Vec3d(0.0, 1.0, 0.0) : new Vec3d(1.0, 0.0, 0.0);
		Vec3d a = dir.crossProduct(up).normalize();
		Vec3d b = dir.crossProduct(a).normalize();
		int count = (int) Math.min(120.0, 24.0 + (to - from) * 14.0);
		for (int i = 0; i < count; i++) {
			double along = from + (to - from) * RANDOM.nextDouble() - r * RANDOM.nextDouble() * 1.2;
			double ang = RANDOM.nextDouble() * Math.PI * 2.0;
			Vec3d out = a.multiply(Math.cos(ang)).add(b.multiply(Math.sin(ang)));
			Vec3d pos = p.form.add(dir.multiply(Math.max(0.0, along))).add(out.multiply(r + 0.3));
			Vec3d v = out.multiply(0.12 + RANDOM.nextDouble() * 0.2).add(dir.multiply(0.1));
			world.addParticle(RANDOM.nextInt(3) == 0 ? LILAC : VIOLET, pos.x, pos.y, pos.z, v.x, v.y, v.z);
			if (i % 4 == 0) {
				// What the wall was: thrown off it in pieces.
				BlockPos outside = BlockPos.ofFloored(p.form.add(dir.multiply(Math.max(0.0, along))).add(out.multiply(r + 1.4)));
				BlockState state = world.getBlockState(outside);
				if (!state.isAir() && state.getFluidState().isEmpty()) {
					Vec3d w = out.multiply(0.25 + RANDOM.nextDouble() * 0.35).add(dir.multiply(0.3));
					world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.x, pos.y, pos.z, w.x, w.y + 0.1, w.z);
				}
			}
		}
		// Sparks round the sphere itself.
		Vec3d c = p.form.add(dir.multiply(front));
		for (int i = 0; i < 10; i++) {
			Vec3d out = unit();
			Vec3d pos = c.add(out.multiply(r * (0.9 + RANDOM.nextDouble() * 0.5)));
			Vec3d v = dir.multiply(-0.4 - RANDOM.nextDouble() * 0.4).add(out.multiply(0.1));
			world.addParticle(i % 2 == 0 ? ParticleTypes.DRAGON_BREATH : ParticleTypes.END_ROD, pos.x, pos.y, pos.z, v.x, v.y, v.z);
		}
	}

	private static Vec3d unit() {
		double x;
		double y;
		double z;
		double l;
		do {
			x = RANDOM.nextDouble() * 2.0 - 1.0;
			y = RANDOM.nextDouble() * 2.0 - 1.0;
			z = RANDOM.nextDouble() * 2.0 - 1.0;
			l = x * x + y * y + z * z;
		} while (l > 1.0 || l < 1.0E-4);
		double s = 1.0 / Math.sqrt(l);
		return new Vec3d(x * s, y * s, z * s);
	}

	/** The player, for the caster's own shot. */
	@Nullable
	static ClientPlayerEntity player() {
		return MinecraftClient.getInstance().player;
	}

	static double ease(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}
}
