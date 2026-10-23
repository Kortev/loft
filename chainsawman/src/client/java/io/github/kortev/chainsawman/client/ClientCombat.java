package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Frenzy;
import io.github.kortev.chainsawman.form.Limbs;
import io.github.kortev.chainsawman.form.Merge;
import io.github.kortev.chainsawman.form.Saw;
import io.github.kortev.chainsawman.network.FormActionPayload;
import io.github.kortev.chainsawman.network.FormEventPayload;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The fight as each client knows it ({@link FormEventPayload}): who slashed and with which step of the combo, who is
 * grinding, whose chain is out and to where, who is in a frenzy, who is down. And your own keys in the form: the attack
 * key slashes (or grinds, held), the chain key fires the chain, the ult key calls up Beam; down, you can only look round.
 */
public final class ClientCombat {
	/** How long a slash plays (ticks), and how long the attack key must be held before it grinds. */
	public static final int SWING = 10;
	private static final int HOLD_TO_GRIND = 6;
	/** How long Beam's charge is felt on your screen (ticks): the dash and a moment after. */
	private static final float CHARGE_FELT = io.github.kortev.chainsawman.form.Beam.CHARGE_TICKS + 3;
	/** How long a chain is out (ticks). */
	public static final int CHAIN = 12;
	/** Long enough ago that the next slash may come (not Long.MIN_VALUE: the time since it would overflow). */
	private static final long NEVER = -1000L;

	public record Swing(int step, long at) {
	}

	/**
	 * A chain fired: what it caught (an entity's id; -1 a block, -2 nothing), where it went, and when. One that caught
	 * something is held until the server lets it go.
	 */
	public record Chain(int entity, Vec3d to, long at) {
		public boolean held() {
			return entity != -2;
		}
	}

	/** A heavy hit's pause: the swing held still until {@code until}, at {@code age} ticks in, for {@code length} ticks. */
	private record Pause(long until, float age, int length) {
	}

	/** How long a hit holds the swing still (ticks), how hard it kicks your camera (blocks) and punches the view in. */
	private static final int PAUSE = 2;
	private static final double KICK = 0.07;
	/**
	 * A saw biting into a player (IMPACT): how long the swing holds on it (ticks), and how many times as hard as a hit's
	 * the kick to your camera is (the one bitten is jolted a little less).
	 */
	private static final int IMPACT_PAUSE = 4;
	private static final double IMPACT_KICK = 2.4;
	/** How early a click may come and still slash the moment it can (ticks). */
	private static final int BUFFER = 5;
	/** How long a slash swung here waits for the server's word on it before it's taken as dropped (ticks). */
	private static final int PREDICTED = 12;
	/** The step in with a slash: only after someone this near (blocks) and no nearer; and how far ahead it looks. */
	private static final double STEP_CLOSE = 1.4;
	private static final double STEP_FAR = 5.0;
	private static final float PUNCH = 0.05F;
	/** Swinging from a block: how fast the chain reels you in (blocks a tick), and how short it gets. */

	private static final Map<UUID, Swing> SWINGS = new ConcurrentHashMap<>();
	private static final Set<UUID> GRINDING = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Chain> CHAINS = new ConcurrentHashMap<>();
	private static final Map<UUID, Pause> PAUSED = new ConcurrentHashMap<>();
	/** When your own last hit landed (for the kick), and how long your swinging chain is (or -1). */
	private static long kickAt = NEVER;
	/** How hard the last kick was, times a hit's. */
	private static double kickSize = 1.0;
	/** A click that came a moment early (when), to slash the moment it can. */
	private static long queuedSlash = NEVER;
	/** Your last slash as swung here, before the server's word on it; your combo's last step and when. */
	@Nullable
	private static Swing predicted;
	private static int lastStep = -1;
	private static long lastStepAt = NEVER;
	private static double tether = -1.0;
	/** Reeled in to a block: how close is there (blocks), and the fastest it goes (blocks a tick). */
	private static final double ARRIVE = 1.4;
	private static final double ZIP = 1.7;
	private static final Map<UUID, Long> FRENZY = new ConcurrentHashMap<>();
	/** Who is faint from blood loss (you, as far as the screen goes), since when. */
	private static long faintFrom = NEVER;
	/** How bloody each one's form is (0 to 1): more with every hit and kill, washed off in water and rain. */
	private static final Map<UUID, Float> BLOODY = new ConcurrentHashMap<>();
	/** When each arm grew back (world time), right and left, to grow it out on screen. */
	private static final Map<UUID, long[]> REGROWN = new ConcurrentHashMap<>();
	/** Who has Pochita's heart: the cord on their chest. */
	private static final Set<UUID> HEARTS = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Long> FRENZY_FROM = new ConcurrentHashMap<>();
	private static final Set<UUID> DOWN = ConcurrentHashMap.newKeySet();
	/** When each got up off the ground after being down (world time). */
	private static final Map<UUID, Long> UP = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> LOST = new ConcurrentHashMap<>();
	/** When each player's arms (right, left) last came off. */
	private static final Map<UUID, long[]> LOST_AT = new ConcurrentHashMap<>();
	/** Your own blood, as the server last said (-1 before it has). */
	private static int blood = -1;
	private static final Map<UUID, SoundInstance> GRIND_SOUNDS = new ConcurrentHashMap<>();
	private static final Map<UUID, SoundInstance> HELD_SOUNDS = new ConcurrentHashMap<>();
	private static final Map<UUID, SoundInstance> FRENZY_SOUNDS = new ConcurrentHashMap<>();
	private static final DustParticleEffect EYE = new DustParticleEffect(new Vector3f(1.0F, 0.85F, 0.3F), 0.8F);
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.5F, 0.0F, 0.03F), 1.0F);
	private static final net.minecraft.particle.BlockStateParticleEffect DRIP = new net.minecraft.particle.BlockStateParticleEffect(
			net.minecraft.particle.ParticleTypes.FALLING_DUST, net.minecraft.block.Blocks.REDSTONE_BLOCK.getDefaultState());
	private static final DustParticleEffect MIST = new DustParticleEffect(new Vector3f(0.75F, 0.04F, 0.06F), 0.7F);
	private static final Random RANDOM = Random.create();

	/** Your own attack key: how long it has been held, whether you are grinding, and when you last slashed. */
	private static int held;
	private static boolean grindSent;
	/** Whether the server has been told you are sprinting on Beam's back (the game sends no sprint for a rider of a mount it does not steer). */
	private static boolean sprintSent;
	private static long lastSlash = NEVER;

	private ClientCombat() {
	}

	static void init() {
		Downed.client = p -> DOWN.contains(p.getUuid());
		Limbs.clientRight = p -> (LOST.getOrDefault(p.getUuid(), 0) & Limbs.RIGHT) != 0;
		Limbs.clientLeft = p -> (LOST.getOrDefault(p.getUuid(), 0) & Limbs.LEFT) != 0;
		Frenzy.client = p -> FRENZY.containsKey(p.getUuid());
		Merge.client = MergeCinematic::merging;
		io.github.kortev.chainsawman.form.Form.client = ClientForms::on;
	}

	/** Something done in the form, as the server tells it (or a test's stand-in, which the server never sees). */
	public static void event(FormEventPayload event) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		if (world == null) {
			return;
		}
		UUID id = event.player();
		long now = world.getTime();
		switch (event.kind()) {
			case FormEventPayload.SWING -> {
				if (predicted != null && client.player != null && id.equals(client.player.getUuid()) && event.value() <= 2
						&& now - predicted.at() <= PREDICTED) {
					// The server's word on a slash already swinging here: only its step, if it says another.
					if (event.value() != predicted.step()) {
						SWINGS.put(id, new Swing(event.value(), predicted.at()));
					}
					lastStep = event.value();
					predicted = null;
				} else {
					SWINGS.put(id, new Swing(event.value(), now));
				}
			}
			case FormEventPayload.IMPACT -> {
				// A saw biting into someone: sparks off the teeth, the crunch.
				Vec3d at = new Vec3d(event.x(), event.y(), event.z());
				for (int i = 0; i < 10; i++) {
					world.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, (RANDOM.nextDouble() - 0.5) * 0.9, RANDOM.nextDouble() * 0.5,
							(RANDOM.nextDouble() - 0.5) * 0.9);
				}
				for (int i = 0; i < 4; i++) {
					world.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, (RANDOM.nextDouble() - 0.5) * 0.6, RANDOM.nextDouble() * 0.4,
							(RANDOM.nextDouble() - 0.5) * 0.6);
				}
				world.playSound(at.x, at.y, at.z, ChainsawSounds.CRUNCH, SoundCategory.PLAYERS, 1.0F, 0.92F + RANDOM.nextFloat() * 0.16F, false);
				ClientPlayerEntity self = client.player;
				if (self != null && id.equals(self.getUuid())) {
					// Yours: the swing held a beat in it, the camera kicked hard.
					float age = swingAge(self, 0.0F);
					if (age >= 0.0F) {
						PAUSED.put(id, new Pause(now + IMPACT_PAUSE, age, IMPACT_PAUSE));
					}
					kickAt = now;
					kickSize = IMPACT_KICK;
				} else if (self != null && event.value() == self.getId()) {
					// Bitten into yourself: jolted.
					kickAt = now;
					kickSize = IMPACT_KICK * 0.7;
				}
			}
			case FormEventPayload.BEAM_HIT -> BeamClient.hit(event.value(), now);
			case FormEventPayload.GRIND -> {
				if (event.value() != 0) {
					GRINDING.add(id);
				} else {
					GRINDING.remove(id);
				}
			}
			case FormEventPayload.CHAIN -> {
				CHAINS.put(id, new Chain(event.value(), new Vec3d(event.x(), event.y(), event.z()), now));
				if (client.player != null && id.equals(client.player.getUuid())) {
					tether = -1.0;
				}
			}
			case FormEventPayload.CHAIN_END -> CHAINS.remove(id);
			case FormEventPayload.BLOOD -> blood = event.value();
			case FormEventPayload.MERGE -> {
				if (event.value() == 1) {
					MergeCinematic.started(id, now, new Vec3d(event.x(), event.y(), event.z()));
				} else if (event.value() == 2) {
					MergeCinematic.waiting(id);
				} else {
					MergeCinematic.ended(id);
				}
			}
			case FormEventPayload.FAINT -> {
				if (client.player != null && id.equals(client.player.getUuid())) {
					faintFrom = event.value() != 0 ? now : NEVER;
				}
			}
			case FormEventPayload.TURN -> {
				ClientForms.turned(id, now);
				FRENZY.remove(id);
				GRINDING.remove(id);
			}
			case FormEventPayload.HEART -> {
				if (event.value() != 0) {
					HEARTS.add(id);
				} else {
					HEARTS.remove(id);
				}
			}
			case FormEventPayload.LIMBS -> {
				int was = LOST.getOrDefault(id, 0);
				if (event.value() == 0) {
					LOST.remove(id);
				} else {
					LOST.put(id, event.value());
				}
				int back = was & ~event.value();
				if (back != 0 && !DOWN.contains(id)) {
					// Grown back out of blood.
					long[] at = REGROWN.computeIfAbsent(id, k -> new long[] {NEVER, NEVER});
					if ((back & Limbs.RIGHT) != 0) {
						at[0] = now;
					}
					if ((back & Limbs.LEFT) != 0) {
						at[1] = now;
					}
				}
				if (!DOWN.contains(id) && world.getPlayerByUuid(id) instanceof AbstractClientPlayerEntity player) {
					// An arm lost in the fight: off it flies.
					int off = event.value() & ~was;
					long[] at = LOST_AT.computeIfAbsent(id, k -> new long[] {NEVER, NEVER});
					boolean mine = client.player != null && id.equals(client.player.getUuid());
					if ((off & Limbs.RIGHT) != 0) {
						Severed.off(player, Severed.RIGHT_ARM, now);
						at[0] = now;
						if (mine) {
							LensBlood.lost(true, now);
						}
					}
					if ((off & Limbs.LEFT) != 0) {
						Severed.off(player, Severed.LEFT_ARM, now);
						at[1] = now;
						if (mine) {
							LensBlood.lost(false, now);
						}
					}
				}
			}
			case FormEventPayload.HIT, FormEventPayload.KILL -> {
				// Drenched a little more.
				BLOODY.merge(id, event.kind() == FormEventPayload.KILL ? 0.2F : 0.05F, (a, b) -> Math.min(1.0F, a + b));
				if (event.kind() == FormEventPayload.KILL) {
					// Cut in two (the server is taking the body away; it's kept here to come apart).
					CutDown.killed(world, event.value(), now);
					net.minecraft.entity.Entity dead = world.getEntityById(event.value());
					if (dead != null && client.player != null && id.equals(client.player.getUuid())) {
						LensBlood.killed(dead.distanceTo(client.player), now);
					}
					if (client.player != null && id.equals(client.player.getUuid())) {
						Sfx.show(Sfx.KILL, 0.13F, 16, now);
					}
				} else if (client.player != null && id.equals(client.player.getUuid())) {
					// Your own hit landing: the overhead chop's, or grinding's, sound lettered.
					Swing swung = SWINGS.get(id);
					if (grinding(client.player)) {
						Sfx.grinding(now);
					} else if (swung != null && swung.step() == 2 && now - swung.at() < SWING) {
						Sfx.show(Sfx.CHOP, 0.11F, 12, now);
					}
				}
				PlayerEntity player = world.getPlayerByUuid(id);
				Pause held = PAUSED.get(id);
				if (player != null && (held == null || held.until() < now + PAUSE)) {
					float age = swingAge(player, 0.0F);
					if (age >= 0.0F) {
						PAUSED.put(id, new Pause(now + PAUSE, age, PAUSE));
					}
				}
				if (client.player != null && id.equals(client.player.getUuid())) {
					// (An impact this tick kicks harder: left as it is.)
					if (kickAt != now) {
						kickSize = 1.0;
					}
					kickAt = now;
				}
			}
			case FormEventPayload.DOWNED -> {
				if (event.value() != 0) {
					if (!DOWN.contains(id) && world.getPlayerByUuid(id) instanceof AbstractClientPlayerEntity player) {
						// Going down: whatever arms and legs are still on fly off.
						int lost = LOST.getOrDefault(id, 0);
						if ((lost & Limbs.RIGHT) == 0) {
							Severed.off(player, Severed.RIGHT_ARM, now);
						}
						if ((lost & Limbs.LEFT) == 0) {
							Severed.off(player, Severed.LEFT_ARM, now);
						}
						Severed.off(player, Severed.RIGHT_LEG, now);
						Severed.off(player, Severed.LEFT_LEG, now);
					}
					DOWN.add(id);
					UP.remove(id);
				} else if (DOWN.remove(id)) {
					UP.put(id, now);
				}
			}
			case FormEventPayload.FRENZY -> {
				if (event.value() > 0) {
					FRENZY.put(id, now + event.value());
					FRENZY_FROM.put(id, now);
					PlayerEntity player = world.getPlayerByUuid(id);
					if (player != null) {
						flare(world, player);
					}
				} else {
					FRENZY.remove(id);
				}
			}
			default -> {
			}
		}
	}

	@Nullable
	public static Swing swing(PlayerEntity player) {
		return SWINGS.get(player.getUuid());
	}

	/** How many ticks into its last slash {@code player} is, while it plays; otherwise -1. */
	public static float swingAge(PlayerEntity player, float tickDelta) {
		Swing swing = SWINGS.get(player.getUuid());
		if (swing == null) {
			return -1.0F;
		}
		long now = player.getWorld().getTime();
		Pause pause = PAUSED.get(player.getUuid());
		if (pause != null && now < pause.until()) {
			// Held still on the hit: the swing caught in what it cut.
			return pause.age();
		}
		float paused = pause != null && pause.until() > swing.at() ? pause.length() : 0.0F;
		float age = now - swing.at() + tickDelta - paused;
		return age >= 0.0F && age < SWING ? age : -1.0F;
	}

	public static boolean grinding(PlayerEntity player) {
		return GRINDING.contains(player.getUuid());
	}

	@Nullable
	public static Chain chain(PlayerEntity player) {
		Chain chain = CHAINS.get(player.getUuid());
		return chain != null && (chain.held() || player.getWorld().getTime() - chain.at() < CHAIN) ? chain : null;
	}

	/** How hard your camera is kicked now by your own last hit (blocks). */
	public static double kick(float tickDelta) {
		ClientPlayerEntity self = MinecraftClient.getInstance().player;
		if (self == null || kickAt == NEVER) {
			return 0.0;
		}
		float t = self.getWorld().getTime() - kickAt + tickDelta;
		return t < 0.0F ? 0.0 : KICK * kickSize * Math.exp(-t / 2.0);
	}

	/** How far your view is punched in by your own last hit (1 for not at all). */
	public static float fov(float tickDelta) {
		ClientPlayerEntity self = MinecraftClient.getInstance().player;
		// (On Beam, the view widens a little with his speed.)
		float speed = BeamClient.fov(tickDelta);
		if (self == null || kickAt == NEVER) {
			return speed * (1.0F + 0.2F * lunging(tickDelta));
		}
		float t = self.getWorld().getTime() - kickAt + tickDelta;
		return speed * (t < 0.0F ? 1.0F : 1.0F - PUNCH * (float) Math.exp(-t / 2.5)) * (1.0F + 0.2F * lunging(tickDelta));
	}

	/** How far into your own lunge you are, as a swell from 0 to 1 and back (0 when not lunging). */
	public static float lunging(float tickDelta) {
		ClientPlayerEntity self = MinecraftClient.getInstance().player;
		if (self != null && self.getVehicle() instanceof io.github.kortev.chainsawman.BeamEntity beam) {
			// On Beam: his charge is yours (the same rush of lines and widening of the view).
			float age = beam.sinceCharge(tickDelta);
			return age >= 0.0F && age <= CHARGE_FELT ? MathHelper.sin(age / CHARGE_FELT * MathHelper.PI) : 0.0F;
		}
		Swing swing = self != null ? SWINGS.get(self.getUuid()) : null;
		float age = self != null ? swingAge(self, tickDelta) : -1.0F;
		if (swing == null || swing.step() != 3 || age < 0.0F) {
			return 0.0F;
		}
		return MathHelper.sin(Math.min(age / SWING, 1.0F) * MathHelper.PI);
	}

	public static Map<UUID, Chain> chains() {
		return CHAINS;
	}

	/** Your own blood, 0 to 100, or -1 if the server hasn't said. */
	public static int blood() {
		return blood;
	}

	/** How far {@code player} is through getting up after being down (0 to 1), or -1. */
	public static float gettingUp(PlayerEntity player, float tickDelta) {
		Long at = UP.get(player.getUuid());
		if (at == null) {
			return -1.0F;
		}
		float k = (player.getWorld().getTime() - at + tickDelta) / Downed.RISE;
		return k >= 0.0F && k <= 1.0F ? k : -1.0F;
	}

	/** How faint from blood loss you are (0 to 1), easing in. */
	public static float faint(float tickDelta) {
		ClientPlayerEntity self = MinecraftClient.getInstance().player;
		if (self == null || faintFrom == NEVER) {
			return 0.0F;
		}
		return MathHelper.clamp((self.getWorld().getTime() - faintFrom + tickDelta) / 40.0F, 0.0F, 1.0F);
	}

	/** How bloody {@code player}'s form is (0 to 1). */
	public static float bloody(PlayerEntity player) {
		return BLOODY.getOrDefault(player.getUuid(), 0.0F);
	}

	/** How far grown back {@code player}'s {@code arm} is (0 to 1), or -1 if it isn't growing. */
	/** When {@code player}'s {@code arm} last came off (world time); long ago if never. */
	public static long lostAt(PlayerEntity player, int arm) {
		long[] at = LOST_AT.get(player.getUuid());
		return at == null ? NEVER : at[arm == Limbs.RIGHT ? 0 : 1];
	}

	public static float regrowing(PlayerEntity player, int arm, float tickDelta) {
		long[] at = REGROWN.get(player.getUuid());
		if (at == null) {
			return -1.0F;
		}
		long when = at[arm == Limbs.RIGHT ? 0 : 1];
		float k = (player.getWorld().getTime() - when + tickDelta) / 12.0F;
		return when == NEVER || k < 0.0F || k > 1.0F ? -1.0F : k;
	}

	/** Whether {@code player} has Pochita's heart, as this client has been told. */
	public static boolean heart(PlayerEntity player) {
		return HEARTS.contains(player.getUuid());
	}

	public static boolean frenzy(PlayerEntity player) {
		return FRENZY.containsKey(player.getUuid());
	}

	/** Whether {@code player}'s saws are working (slashing, grinding, cutting through something): the chains race. */
	public static boolean busy(PlayerEntity player) {
		return swingAge(player, 0.0F) >= 0.0F || grinding(player) || player.handSwinging || frenzy(player);
	}

	/** How red the edges of your screen are from the fight: pulsing in a frenzy, a hit as it kicks in, dark while down. */
	public static float vignette(float tickDelta) {
		ClientPlayerEntity self = MinecraftClient.getInstance().player;
		if (self == null) {
			return 0.0F;
		}
		if (Downed.is(self)) {
			return 0.85F;
		}
		Long from = FRENZY_FROM.get(self.getUuid());
		if (!frenzy(self) || from == null) {
			return 0.0F;
		}
		float t = self.getWorld().getTime() - from + tickDelta;
		float kick = (float) Math.exp(-t / 6.0) * 0.8F;
		return Math.max(kick, 0.42F + 0.12F * MathHelper.sin(t * 0.45F));
	}

	/**
	 * The use key in the form: right-clicking at nothing (or at a mob), the lunge, headfirst the way you look. At a block
	 * (a door, a chest) or sneaking (to give a friend blood) it does what it always does. Whether the lunge took it.
	 */
	public static boolean use(MinecraftClient client) {
		ClientPlayerEntity self = client.player;
		if (self != null && fighting(self) && self.getVehicle() instanceof io.github.kortev.chainsawman.BeamEntity) {
			// From Beam's back the use key is his charge, wherever you are looking.
			send(FormActionPayload.LUNGE, true);
			return true;
		}
		if (self == null || !fighting(self) || self.isSneaking() || client.crosshairTarget != null
				&& client.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
			return false;
		}
		send(FormActionPayload.LUNGE, true);
		return true;
	}

	/** The attack key pressed in the form: a slash. Whether the game should go on to do what it would have (mine a block). */
	public static boolean attack(MinecraftClient client) {
		ClientPlayerEntity self = client.player;
		boolean wielding = self != null && !ClientForms.on(self) && Saw.wielding(self);
		if (wielding) {
			// Pochita held is a real chainsaw, not swung: holding the key revs him and cuts (tick); on a block it's the
			// game's own mining, his tool speed doing the rest.
			return client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK;
		}
		if (self == null || !fighting(self)) {
			return self == null || !Downed.is(self);
		}
		long now = self.getWorld().getTime();
		if (self.getVehicle() instanceof io.github.kortev.chainsawman.BeamEntity) {
			// From Beam's back the attack key is his bite: told to the server, and his jaw snaps when it answers; no swing of the saws.
			if (now - lastSlash >= io.github.kortev.chainsawman.form.Beam.BITE_EVERY) {
				lastSlash = now;
				send(FormActionPayload.SLASH, true);
			}
			return client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK;
		}
		if (now - lastSlash >= Saw.SLASH_EVERY) {
			slashNow(self, now, wielding);
		} else if (now - lastSlash >= Saw.SLASH_EVERY - BUFFER) {
			// A moment early: it comes the moment it can.
			queuedSlash = now;
		}
		return client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK;
	}

	/**
	 * A slash, the moment you click: sent to the server with what's under your crosshair, and swung and heard here at
	 * once, not when the server's word comes back (on a server far off, a moment late); a step in after whoever it's at.
	 * The server still decides what it hits, and says which step of the combo it was (SWING).
	 */
	private static void slashNow(ClientPlayerEntity self, long now, boolean wielding) {
		lastSlash = now;
		queuedSlash = NEVER;
		LivingEntity aim = aim(self);
		if (ClientPlayNetworking.canSend(FormActionPayload.ID)) {
			ClientPlayNetworking.send(new FormActionPayload(FormActionPayload.SLASH, true, aim != null ? aim.getId() : -1));
		}
		// The step the server will make it (Saw.slash).
		int step = lastStep < 0 || now - lastStepAt > Saw.COMBO_GAP ? 0 : (lastStep + 1) % 3;
		boolean right = Limbs.has(self, Limbs.RIGHT);
		boolean left = Limbs.has(self, Limbs.LEFT);
		if (!right && !left) {
			step = 2;
		} else if (step == 0 && !right) {
			step = 1;
		} else if (step == 1 && !left) {
			step = 0;
		}
		if (wielding && step == 1) {
			step = 0;
		}
		lastStep = step;
		lastStepAt = now;
		predicted = new Swing(step, now);
		SWINGS.put(self.getUuid(), predicted);
		self.getWorld().playSound(self, self.getX(), self.getY() + 1.0, self.getZ(), ChainsawSounds.SLASH, SoundCategory.PLAYERS, 1.0F, 1.0F);
		if (wielding) {
			self.swingHand(Hand.MAIN_HAND);
		}
		stepIn(self, aim, step == 2);
	}

	/** What's under your crosshair to slash, in reach (a little past it: the server allows for lag) and not behind a wall. */
	@Nullable
	private static LivingEntity aim(ClientPlayerEntity self) {
		Vec3d eye = self.getEyePos();
		Vec3d look = self.getRotationVec(1.0F);
		double reach = Saw.SLASH_REACH + 0.8;
		EntityHitResult hit = ProjectileUtil.raycast(self, eye, eye.add(look.multiply(reach)), self.getBoundingBox().stretch(look.multiply(reach)).expand(1.0),
				e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && !(e instanceof ArmorStandEntity), reach * reach);
		if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
			return null;
		}
		HitResult wall = self.raycast(reach, 1.0F, false);
		if (wall.getType() == HitResult.Type.BLOCK && wall.getPos().squaredDistanceTo(eye) < hit.getPos().squaredDistanceTo(eye)) {
			return null;
		}
		return target;
	}

	/**
	 * A step in with the slash, after whoever it's at (what you aim at, or the nearest in front of you), so a player
	 * backing off or strafing stays in reach; the finisher's longer. Only on the ground, and not into them.
	 */
	private static void stepIn(ClientPlayerEntity self, @Nullable LivingEntity aim, boolean finisher) {
		LivingEntity at = aim;
		if (at == null) {
			Vec3d flat = self.getRotationVec(1.0F).multiply(1.0, 0.0, 1.0).normalize();
			double nearest = STEP_FAR;
			for (net.minecraft.entity.Entity e : self.getWorld().getOtherEntities(self, self.getBoundingBox().expand(STEP_FAR),
					e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && !(e instanceof ArmorStandEntity))) {
				Vec3d to = e.getPos().subtract(self.getPos()).multiply(1.0, 0.0, 1.0);
				double far = to.length();
				if (far < nearest && far > 0.01 && to.normalize().dotProduct(flat) > 0.75) {
					nearest = far;
					at = (LivingEntity) e;
				}
			}
		}
		if (at == null || !self.isOnGround()) {
			return;
		}
		Vec3d to = at.getPos().subtract(self.getPos()).multiply(1.0, 0.0, 1.0);
		double far = to.length();
		if (far < STEP_CLOSE || far > STEP_FAR) {
			return;
		}
		Vec3d dir = to.normalize();
		double push = Math.min(finisher ? 0.45 : 0.3, (far - STEP_CLOSE) * 0.25);
		self.addVelocity(dir.x * push, 0.0, dir.z * push);
	}

	/** Whether you are in the form, done transforming and up: free to fight. */
	public static boolean fighting(PlayerEntity player) {
		return ClientForms.on(player) && ClientForms.pullAge(player, 0.0F) < 0.0F && ClientForms.turnAge(player, 0.0F) < 0.0F
				&& !Downed.is(player);
	}

	static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		ClientPlayerEntity self = client.player;
		if (world == null || self == null) {
			return;
		}
		long now = world.getTime();
		if (queuedSlash != NEVER) {
			boolean wielding = !ClientForms.on(self) && Saw.wielding(self);
			if (now - queuedSlash > BUFFER * 2 || !fighting(self) && !wielding) {
				queuedSlash = NEVER;
			} else if (now - lastSlash >= Saw.SLASH_EVERY) {
				slashNow(self, now, wielding);
			}
		}
		while (ChainsawManClient.CHAIN.wasPressed()) {
			if (fighting(self)) {
				send(FormActionPayload.CHAIN, true);
			}
		}
		while (ChainsawManClient.KICK.wasPressed()) {
			if (fighting(self)) {
				send(FormActionPayload.KICK, true);
				kickAt = self.getWorld().getTime();
			}
		}
		while (ChainsawManClient.FRENZY.wasPressed()) {
			if (fighting(self)) {
				send(FormActionPayload.FRENZY, true);
			}
		}
		// Held, the attack key grinds, unless it is on a block (cutting through that is the game's own mining).
		boolean onBlock = client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK;
		boolean wielding = !ClientForms.on(self) && Saw.wielding(self);
		boolean armed = fighting(self) || wielding;
		held = armed && client.options.attackKey.isPressed() && client.currentScreen == null ? held + 1 : 0;
		// (Pochita held runs the moment it's held, cutting a tree too: the server revs him up before he bites.)
		boolean grind = wielding ? held > 0 : held >= HOLD_TO_GRIND && !onBlock;
		if (grind != grindSent) {
			grindSent = grind;
			send(FormActionPayload.GRIND, grind);
		}
		boolean sprint = self.getVehicle() instanceof io.github.kortev.chainsawman.BeamEntity && self.isSprinting() && fighting(self);
		if (sprint != sprintSent) {
			sprintSent = sprint;
			send(FormActionPayload.SPRINT, sprint);
		}
		if (Downed.is(self) || MergeCinematic.merging(self)) {
			holdStill(client.options);
		}
		MergeCinematic.tick(world);
		CutDown.tick(world);
		Severed.tick(world);
		swing(client, self);
		for (AbstractClientPlayerEntity player : world.getPlayers()) {
			UUID id = player.getUuid();
			loop(client, GRIND_SOUNDS, player, ChainsawSounds.GRIND, () -> grinding(player) && (ClientForms.on(player) || Saw.wielding(player)), 0.9F);
			// Pochita in your hands putters along, idling, till you cut.
			loop(client, HELD_SOUNDS, player, ChainsawSounds.IDLE, () -> !ClientForms.on(player) && Saw.wielding(player) && !grinding(player), 0.35F);
			loop(client, FRENZY_SOUNDS, player, ChainsawSounds.FRENZY_LOOP, () -> frenzy(player) && ClientForms.on(player), 0.8F);
			if (frenzy(player) && now % 3 == 0) {
				eyeGlow(world, player);
			}
			if (frenzy(player) && ClientForms.on(player) && !ownEyes(player)) {
				// Blood boiling off them: a red mist rising round the body.
				for (int i = 0; i < 2; i++) {
					double a = RANDOM.nextDouble() * Math.PI * 2.0;
					double r = 0.3 + RANDOM.nextDouble() * 0.25;
					world.addParticle(MIST, player.getX() + Math.cos(a) * r, player.getY() + 0.2 + RANDOM.nextDouble() * 1.5,
							player.getZ() + Math.sin(a) * r, 0.0, 0.04 + RANDOM.nextDouble() * 0.04, 0.0);
				}
			}
			exhaust(world, player, now);
			float up = gettingUp(player, 0.0F);
			if (up >= 0.0F && up < 0.3F && !ownEyes(player)) {
				// The arms and legs growing back: blood off the stumps.
				for (int i = 0; i < 3; i++) {
					world.addParticle(BLOOD, player.getX() + (RANDOM.nextDouble() - 0.5) * 0.9, player.getY() + 0.15 + RANDOM.nextDouble() * 0.4,
							player.getZ() + (RANDOM.nextDouble() - 0.5) * 0.9, 0.0, 0.0, 0.0);
				}
			}
			if (DOWN.contains(id) && now % 8 == 0 && !ownEyes(player)) {
				world.addParticle(BLOOD, player.getX() + (RANDOM.nextDouble() - 0.5) * 0.8, player.getY() + 0.2,
						player.getZ() + (RANDOM.nextDouble() - 0.5) * 0.8, 0.0, 0.0, 0.0);
			}
		}
		FRENZY.entrySet().removeIf(e -> now > e.getValue() + 40);
		UP.entrySet().removeIf(e -> now > e.getValue() + Downed.RISE + 20);
		for (AbstractClientPlayerEntity player : world.getPlayers()) {
			Float b = BLOODY.get(player.getUuid());
			if (b == null) {
				continue;
			}
			// Dries and flakes off slowly; water and rain wash it away.
			float left = b - (player.isTouchingWaterOrRain() ? 0.05F : 0.0006F);
			if (left <= 0.0F) {
				BLOODY.remove(player.getUuid());
				continue;
			}
			BLOODY.put(player.getUuid(), left);
			if (left > 0.35F && ClientForms.on(player) && !ownEyes(player) && RANDOM.nextFloat() < left * 0.5F) {
				// Dripping off the saws.
				Vec3d right = Vec3d.fromPolar(0.0F, player.getBodyYaw() + 90.0F).multiply(RANDOM.nextBoolean() ? 0.4 : -0.4);
				world.addParticle(DRIP, player.getX() + right.x, player.getY() + 0.5 + RANDOM.nextDouble() * 0.6, player.getZ() + right.z, 0.0, -0.05,
						0.0);
			}
		}
		CHAINS.entrySet().removeIf(e -> now - e.getValue().at() > (e.getValue().held() ? 400 : CHAIN));
		PAUSED.entrySet().removeIf(e -> now > e.getValue().until() + SWING);
		SWINGS.entrySet().removeIf(e -> now - e.getValue().at() > SWING * 3);
	}

	/**
	 * Swinging from your chain caught in a block: the chain doesn't stretch, so you swing round where it's fixed, reeled
	 * in a little every tick; jumping lets go (with a kick the way you look).
	 */
	private static void swing(MinecraftClient client, ClientPlayerEntity self) {
		// Hooked on a block: reeled in straight to it, fast, and let go there with a hop (or early, jumping).
		Chain chain = CHAINS.get(self.getUuid());
		if (chain == null || chain.entity() != -1) {
			tether = -1.0;
			return;
		}
		Vec3d to = chain.to().subtract(self.getPos().add(0.0, 1.0, 0.0));
		double far = to.length();
		boolean jumped = client.options.jumpKey.isPressed() && self.getWorld().getTime() - chain.at() > 4;
		// Stuck against something: as good as there.
		boolean stuck = tether >= 0.0 && tether - far < 0.05 && self.getWorld().getTime() - chain.at() > 6;
		if (far < ARRIVE || jumped || stuck) {
			self.setVelocity(self.getRotationVec(1.0F).multiply(0.25).add(0.0, 0.42, 0.0));
			send(FormActionPayload.RELEASE, true);
			CHAINS.remove(self.getUuid());
			tether = -1.0;
			return;
		}
		tether = far;
		self.setVelocity(to.multiply(Math.min(ZIP, 0.6 + far * 0.15) / far));
		self.fallDistance = 0.0F;
	}

	/** Down, you can look round but not move, jump, fight or use anything. */
	private static void holdStill(GameOptions o) {
		for (KeyBinding key : new KeyBinding[] {o.forwardKey, o.backKey, o.leftKey, o.rightKey, o.jumpKey, o.sneakKey, o.sprintKey, o.attackKey,
				o.useKey, o.pickItemKey, o.swapHandsKey}) {
			key.setPressed(false);
			while (key.wasPressed()) {
				// Swallowed.
			}
		}
	}

	/** The frenzy kicking in on {@code player}: the eye flaring. */
	private static void flare(ClientWorld world, PlayerEntity player) {
		if (ownEyes(player)) {
			return;
		}
		Vec3d eye = eyeAt(player);
		for (int i = 0; i < 24; i++) {
			world.addParticle(EYE, eye.x, eye.y, eye.z, (RANDOM.nextDouble() - 0.5) * 0.3, RANDOM.nextDouble() * 0.15, (RANDOM.nextDouble() - 0.5) * 0.3);
		}
	}

	/** In a frenzy the eye burns: sparks of its light trailing off it. */
	private static void eyeGlow(ClientWorld world, PlayerEntity player) {
		if (ownEyes(player)) {
			return;
		}
		Vec3d eye = eyeAt(player);
		world.addParticle(EYE, eye.x, eye.y, eye.z, 0.0, 0.03, 0.0);
	}

	/**
	 * The engine's exhaust, light grey puffs off the top of the pole behind the bar: one now and then while it idles,
	 * more while the saws work or in a frenzy, a burst as it roars into life at the end of the transformation.
	 */
	private static void exhaust(ClientWorld world, AbstractClientPlayerEntity player, long now) {
		if (ownEyes(player) || player.isInvisible()) {
			return;
		}
		float pull = ClientForms.pullAge(player, 0.0F);
		boolean roar = pull >= io.github.kortev.chainsawman.form.FormTimeline.ROAR && pull < io.github.kortev.chainsawman.form.FormTimeline.ROAR + 10;
		if (!ClientForms.on(player) && !roar) {
			return;
		}
		int every = roar ? 1 : frenzy(player) ? 2 : busy(player) ? 3 : 20;
		if (now % every != 0) {
			return;
		}
		Vec3d back = Vec3d.fromPolar(0.0F, player.getHeadYaw()).multiply(-1.0);
		Vec3d at = player.getEyePos().add(back.multiply(0.15)).add(0.0, 0.6, 0.0);
		world.addParticle(ParticleTypes.WHITE_SMOKE, at.x, at.y, at.z, back.x * 0.04 + (RANDOM.nextDouble() - 0.5) * 0.02,
				0.05 + RANDOM.nextDouble() * 0.03, back.z * 0.04 + (RANDOM.nextDouble() - 0.5) * 0.02);
	}

	/** Whether {@code player} is you, seen from your own eyes (particles on you would fill the view). */
	private static boolean ownEyes(PlayerEntity player) {
		MinecraftClient client = MinecraftClient.getInstance();
		return player == client.player && !client.gameRenderer.getCamera().isThirdPerson();
	}

	/** Where the chainsaw head's eye is, near enough: on the side of the visor, at the front. */
	private static Vec3d eyeAt(PlayerEntity player) {
		Vec3d look = Vec3d.fromPolar(0.0F, player.getHeadYaw());
		Vec3d side = Vec3d.fromPolar(0.0F, player.getHeadYaw() + 90.0F);
		return player.getEyePos().add(look.multiply(0.3)).add(side.multiply(-0.25)).add(0.0, 0.1, 0.0);
	}

	private static void loop(MinecraftClient client, Map<UUID, SoundInstance> playing, AbstractClientPlayerEntity player, SoundEvent sound,
			BooleanSupplier while_, float volume) {
		SoundInstance now = playing.get(player.getUuid());
		if (now != null && client.getSoundManager().isPlaying(now)) {
			return;
		}
		playing.remove(player.getUuid());
		if (while_.getAsBoolean()) {
			FormLoopSound loop = new FormLoopSound(player, sound, while_, volume);
			playing.put(player.getUuid(), loop);
			client.getSoundManager().play(loop);
		}
	}

	private static void send(byte action, boolean on) {
		if (ClientPlayNetworking.canSend(FormActionPayload.ID)) {
			ClientPlayNetworking.send(new FormActionPayload(action, on));
		}
	}

	static void clear() {
		SWINGS.clear();
		GRINDING.clear();
		CHAINS.clear();
		MergeCinematic.clear();
		CutDown.clear();
		LensBlood.clear();
		Sfx.clear();
		Severed.clear();
		SawTrails.clear();
		LOST.clear();
		LOST_AT.clear();
		Stumps.clear();
		HEARTS.clear();
		BLOODY.clear();
		REGROWN.clear();
		faintFrom = NEVER;
		UP.clear();
		blood = -1;
		PAUSED.clear();
		kickAt = NEVER;
		tether = -1.0;
		FRENZY.clear();
		FRENZY_FROM.clear();
		DOWN.clear();
		GRIND_SOUNDS.clear();
		HELD_SOUNDS.clear();
		FRENZY_SOUNDS.clear();
		held = 0;
		grindSent = false;
		sprintSent = false;
		lastSlash = NEVER;
		queuedSlash = NEVER;
		predicted = null;
		lastStep = -1;
		lastStepAt = NEVER;
		kickSize = 1.0;
	}

	/** A loop on someone, going round where they are while it should. */
	static final class FormLoopSound extends MovingSoundInstance {
		private final AbstractClientPlayerEntity player;
		private final BooleanSupplier while_;

		FormLoopSound(AbstractClientPlayerEntity player, SoundEvent sound, BooleanSupplier while_, float volume) {
			super(sound, SoundCategory.PLAYERS, Random.create());
			this.player = player;
			this.while_ = while_;
			this.repeat = true;
			this.repeatDelay = 0;
			this.volume = volume;
			this.attenuationType = SoundInstance.AttenuationType.LINEAR;
			follow();
		}

		@Override
		public void tick() {
			if (player.isRemoved() || !while_.getAsBoolean()) {
				setDone();
				return;
			}
			follow();
		}

		private void follow() {
			x = player.getX();
			y = player.getY() + 1.4;
			z = player.getZ();
		}
	}
}
