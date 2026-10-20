package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Frenzy;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The fight as each client knows it ({@link FormEventPayload}): who slashed and with which step of the combo, who is
 * grinding, whose chain is out and to where, who is in a frenzy, who is down. And your own keys in the form: the attack
 * key slashes (or grinds, held), the chain key fires the chain, the frenzy key calls it up; down, you can only look round.
 */
public final class ClientCombat {
	/** How long a slash plays (ticks), and how long the attack key must be held before it grinds. */
	public static final int SWING = 10;
	private static final int HOLD_TO_GRIND = 6;
	/** How long a chain is out (ticks). */
	public static final int CHAIN = 12;

	public record Swing(int step, long at) {
	}

	/** A chain fired: what it caught (an entity's id; -1 a block, -2 nothing), where it went, and when. */
	public record Chain(int entity, Vec3d to, long at) {
	}

	private static final Map<UUID, Swing> SWINGS = new ConcurrentHashMap<>();
	private static final Set<UUID> GRINDING = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Chain> CHAINS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> FRENZY = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> FRENZY_FROM = new ConcurrentHashMap<>();
	private static final Set<UUID> DOWN = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, SoundInstance> GRIND_SOUNDS = new ConcurrentHashMap<>();
	private static final Map<UUID, SoundInstance> FRENZY_SOUNDS = new ConcurrentHashMap<>();
	private static final DustParticleEffect EYE = new DustParticleEffect(new Vector3f(1.0F, 0.85F, 0.3F), 0.8F);
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.5F, 0.0F, 0.03F), 1.0F);
	private static final Random RANDOM = Random.create();

	/** Long enough ago that the next slash may come (not Long.MIN_VALUE: the time since it would overflow). */
	private static final long NEVER = -1000L;
	/** Your own attack key: how long it has been held, whether you are grinding, and when you last slashed. */
	private static int held;
	private static boolean grindSent;
	private static long lastSlash = NEVER;

	private ClientCombat() {
	}

	static void init() {
		Downed.client = p -> DOWN.contains(p.getUuid());
		Frenzy.client = p -> FRENZY.containsKey(p.getUuid());
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
			case FormEventPayload.SWING -> SWINGS.put(id, new Swing(event.value(), now));
			case FormEventPayload.GRIND -> {
				if (event.value() != 0) {
					GRINDING.add(id);
				} else {
					GRINDING.remove(id);
				}
			}
			case FormEventPayload.CHAIN -> CHAINS.put(id, new Chain(event.value(), new Vec3d(event.x(), event.y(), event.z()), now));
			case FormEventPayload.DOWNED -> {
				if (event.value() != 0) {
					DOWN.add(id);
				} else {
					DOWN.remove(id);
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
		float age = player.getWorld().getTime() - swing.at() + tickDelta;
		return age >= 0.0F && age < SWING ? age : -1.0F;
	}

	public static boolean grinding(PlayerEntity player) {
		return GRINDING.contains(player.getUuid());
	}

	@Nullable
	public static Chain chain(PlayerEntity player) {
		Chain chain = CHAINS.get(player.getUuid());
		return chain != null && player.getWorld().getTime() - chain.at() < CHAIN ? chain : null;
	}

	public static Map<UUID, Chain> chains() {
		return CHAINS;
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

	/** The attack key pressed in the form: a slash. Whether the game should go on to do what it would have (mine a block). */
	public static boolean attack(MinecraftClient client) {
		ClientPlayerEntity self = client.player;
		if (self == null || !fighting(self)) {
			return self == null || !Downed.is(self);
		}
		long now = self.getWorld().getTime();
		if (now - lastSlash >= Saw.SLASH_EVERY) {
			lastSlash = now;
			send(FormActionPayload.SLASH, true);
		}
		return client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK;
	}

	/** Whether you are in the form, done transforming and up: free to fight. */
	public static boolean fighting(PlayerEntity player) {
		return ClientForms.on(player) && ClientForms.pullAge(player, 0.0F) < 0.0F && !Downed.is(player);
	}

	static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		ClientPlayerEntity self = client.player;
		if (world == null || self == null) {
			return;
		}
		long now = world.getTime();
		while (ChainsawManClient.CHAIN.wasPressed()) {
			if (fighting(self)) {
				send(FormActionPayload.CHAIN, true);
			}
		}
		while (ChainsawManClient.FRENZY.wasPressed()) {
			if (fighting(self)) {
				send(FormActionPayload.FRENZY, true);
			}
		}
		// Held, the attack key grinds, unless it is on a block (cutting through that is the game's own mining).
		boolean onBlock = client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK;
		held = fighting(self) && client.options.attackKey.isPressed() && client.currentScreen == null ? held + 1 : 0;
		boolean grind = held >= HOLD_TO_GRIND && !onBlock;
		if (grind != grindSent) {
			grindSent = grind;
			send(FormActionPayload.GRIND, grind);
		}
		if (Downed.is(self)) {
			holdStill(client.options);
		}
		for (AbstractClientPlayerEntity player : world.getPlayers()) {
			UUID id = player.getUuid();
			loop(client, GRIND_SOUNDS, player, ChainsawSounds.GRIND, () -> grinding(player) && ClientForms.on(player), 0.9F);
			loop(client, FRENZY_SOUNDS, player, ChainsawSounds.FRENZY_LOOP, () -> frenzy(player) && ClientForms.on(player), 0.8F);
			if (frenzy(player) && now % 3 == 0) {
				eyeGlow(world, player);
			}
			if (DOWN.contains(id) && now % 8 == 0 && !ownEyes(player)) {
				world.addParticle(BLOOD, player.getX() + (RANDOM.nextDouble() - 0.5) * 0.8, player.getY() + 0.2,
						player.getZ() + (RANDOM.nextDouble() - 0.5) * 0.8, 0.0, 0.0, 0.0);
			}
		}
		FRENZY.entrySet().removeIf(e -> now > e.getValue() + 40);
		CHAINS.entrySet().removeIf(e -> now - e.getValue().at() > CHAIN);
		SWINGS.entrySet().removeIf(e -> now - e.getValue().at() > SWING * 3);
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
		FRENZY.clear();
		FRENZY_FROM.clear();
		DOWN.clear();
		GRIND_SOUNDS.clear();
		FRENZY_SOUNDS.clear();
		held = 0;
		grindSent = false;
		lastSlash = NEVER;
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
