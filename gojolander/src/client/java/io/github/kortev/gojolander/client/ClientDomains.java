package io.github.kortev.gojolander.client;

import io.github.kortev.gojolander.GojoSounds;
import io.github.kortev.gojolander.domain.DomainTimeline;
import io.github.kortev.gojolander.network.DomainCaughtPayload;
import io.github.kortev.gojolander.network.DomainEndPayload;
import io.github.kortev.gojolander.network.DomainLockPayload;
import io.github.kortev.shootingstar.client.ClientConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The client side of Domains: what it knows of each, whose screen and body they have, and their sounds, played from the
 * timeline as it passes. Inside the sphere (or casting it) the sounds are heard as they are, all round; outside it,
 * placed where it is.
 */
public final class ClientDomains {
	private static final Map<Integer, ClientDomain> DOMAINS = new LinkedHashMap<>();
	/** How far off the Domain's sounds carry to someone outside it, beyond its own size. */
	private static final double HEARD = 64.0;
	/** When he speaks: "Domain Expansion" as his hand comes up into the sign, "Unlimited Void" as the void opens. */
	private static final int SAYS_EXPANSION = DomainTimeline.HAND - 2;
	private static final int SAYS_VOID = DomainTimeline.VOID + 2;
	/** How long after it lets go (ticks) one it held in the air may still land and go down onto their knees. */
	private static final int LAND = 40;
	/** How long after its end a Domain is kept, for the last of the shattering to be drawn and the broken to get up. */
	private static final int KEEP = LAND + DomainTimeline.RISEN - DomainTimeline.SHATTER + 2;
	/** How near (blocks) one of the caught has to be for their throb or their knees to be heard, and how many are. */
	private static final double NEAR = 24.0;
	private static final int VOICES = 4;
	/** The broken (network ids), and the world tick their daze is over: the light leaving them, a mote at a time. */
	private static final Map<Integer, Long> DAZED = new HashMap<>();
	/** The HUD (and the hand) put away for the caster's shot, and whether it was away already. */
	private static boolean hudOverride;
	private static boolean savedHudHidden;

	private ClientDomains() {
	}

	public static Collection<ClientDomain> all() {
		return DOMAINS.values();
	}

	/** The Domain this client's player is casting, until the camera is back in their eyes and a moment after. */
	@Nullable
	public static ClientDomain mine() {
		for (ClientDomain domain : DOMAINS.values()) {
			if (domain.mine && domain.age < domain.end()) {
				return domain;
			}
		}
		return null;
	}

	/** Whether the camera's shots of the caster run: his own Domain, not yet closed, and shots wanted. */
	public static boolean shooting() {
		ClientDomain domain = mine();
		return domain != null && ClientConfig.cameraShots && domain.age < DomainTimeline.CLOSE;
	}

	/**
	 * Whether this player's screen is the Domain's alone, with no HUD over it: the caster's shots, and for everyone in it,
	 * the black and the void opening out of it.
	 */
	public static boolean cinematic(MinecraftClient client) {
		if (shooting()) {
			return true;
		}
		for (ClientDomain domain : DOMAINS.values()) {
			if (domain.age >= DomainTimeline.CLOSE && domain.age < DomainTimeline.VOID + DomainTimeline.OPEN && domain.age < domain.collapse()
					&& (domain.mine || domain.holdsMe || client.gameRenderer != null && domain.contains(client.gameRenderer.getCamera().getPos()))) {
				return true;
			}
		}
		return false;
	}

	/** The Domain holding this client's player still now, or null. */
	@Nullable
	public static ClientDomain holding() {
		for (ClientDomain domain : DOMAINS.values()) {
			if (domain.holdsMe && domain.standing(domain.age)) {
				return domain;
			}
		}
		return null;
	}

	/** The Domain holding {@code entity} still now (any entity this client was told it caught), or null. */
	@Nullable
	public static ClientDomain holdingEntity(Entity entity) {
		for (ClientDomain domain : DOMAINS.values()) {
			if (domain.standing(domain.age) && domain.caught.contains(entity.getId())) {
				return domain;
			}
		}
		return null;
	}

	/** The Domain that let {@code entity} go onto its knees, from the moment it let go until they are up again, or null. */
	@Nullable
	public static ClientDomain kneeling(Entity entity) {
		for (ClientDomain domain : DOMAINS.values()) {
			if (domain.knelt.containsKey(entity.getId()) && domain.age >= domain.collapse() && domain.age < domain.risen(entity.getId())) {
				return domain;
			}
		}
		return null;
	}

	/**
	 * Whether this client's player is being let go of broken, from just before the void lets go until they are up again:
	 * their eyes go down into the middle of their own body then.
	 */
	public static boolean breakingMe() {
		for (ClientDomain domain : DOMAINS.values()) {
			int after = domain.age - domain.collapse();
			if (domain.holdsMe && !domain.shattered && after >= -5 && after < LAND + DomainTimeline.RISEN) {
				return true;
			}
		}
		return false;
	}

	/** For {@link io.github.kortev.gojolander.domain.DomainHold}: whether this entity is this client's player, held. */
	public static boolean heldHere(Entity entity) {
		return entity == MinecraftClient.getInstance().player && holding() != null;
	}

	/**
	 * Whether this client's player can neither move nor look round: held, on their knees as it let them go (until they
	 * come to), or casting with the camera taken.
	 */
	public static boolean locked() {
		if (holding() != null || shooting()) {
			return true;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		ClientDomain knelt = client.player != null ? kneeling(client.player) : null;
		return knelt != null && knelt.age < knelt.comeTo(client.player.getId());
	}

	/** The Domain {@code player} is casting, while the sign is up (from the key until a moment after it closes). */
	@Nullable
	public static ClientDomain signing(PlayerEntity player) {
		for (ClientDomain domain : DOMAINS.values()) {
			if (player.getUuid().equals(domain.caster) && domain.age < DomainTimeline.CLOSE + 12) {
				return domain;
			}
		}
		return null;
	}

	/** The Domain {@code player} cast, while his blindfold is up off his eyes: from the sign until the barrier is gone. */
	@Nullable
	public static ClientDomain unveiled(PlayerEntity player) {
		for (ClientDomain domain : DOMAINS.values()) {
			if (player.getUuid().equals(domain.caster) && domain.age < domain.end()) {
				return domain;
			}
		}
		return null;
	}

	/** Swallows every key press (before anything reads them this tick) while this player is locked. */
	public static void holdInput(MinecraftClient client) {
		if (!locked()) {
			return;
		}
		GameOptions o = client.options;
		for (KeyBinding key : new KeyBinding[] {o.forwardKey, o.backKey, o.leftKey, o.rightKey, o.jumpKey, o.sneakKey, o.sprintKey,
				o.attackKey, o.useKey, o.pickItemKey, o.dropKey, o.swapHandsKey}) {
			key.setPressed(false);
			while (key.wasPressed()) {
				// Swallowed.
			}
		}
	}

	public static void onLock(DomainLockPayload payload, MinecraftClient client) {
		if (client.world == null || client.player == null) {
			return;
		}
		UUID caster = payload.caster().equals(DomainLockPayload.NOBODY) ? null : payload.caster();
		boolean mine = client.player.getUuid().equals(caster);
		PlayerEntity casting = caster != null ? client.world.getPlayerByUuid(caster) : null;
		float yaw = casting != null ? casting.getYaw() : client.player.getYaw();
		ClientDomain domain = new ClientDomain(payload.domainId(), caster, mine, payload.center(), payload.radius(), payload.hold(), yaw,
				payload.age());
		DOMAINS.put(domain.id, domain);
		if (payload.age() == 0) {
			cues(client, domain, -1, 0);
		}
	}

	public static void onCaught(DomainCaughtPayload payload, MinecraftClient client) {
		ClientDomain domain = DOMAINS.get(payload.domainId());
		if (domain == null || client.player == null) {
			return;
		}
		domain.caught.addAll(payload.caught());
		domain.holdsMe = domain.caught.contains(client.player.getId());
		if (domain.holdsMe) {
			client.player.setVelocity(Vec3d.ZERO);
			// Told only once the void is already open (the news can come a tick late): the flood pours in at once.
			if (domain.age >= DomainTimeline.VOID) {
				startFlood(client, domain);
			}
		}
	}

	public static void onEnd(DomainEndPayload payload, MinecraftClient client) {
		ClientDomain domain = DOMAINS.get(payload.domainId());
		if (domain == null) {
			return;
		}
		if (payload.cancelled()) {
			stopSounds(client, domain);
			DOMAINS.remove(domain.id);
			return;
		}
		int from = domain.age;
		domain.shattered = true;
		domain.age = Math.max(domain.age, payload.age());
		cues(client, domain, from, domain.age);
	}

	public static void tick(MinecraftClient client) {
		hud(client);
		motes(client);
		if (DOMAINS.isEmpty()) {
			return;
		}
		Iterator<ClientDomain> it = DOMAINS.values().iterator();
		while (it.hasNext()) {
			ClientDomain domain = it.next();
			int from = domain.age;
			domain.age++;
			cues(client, domain, from, domain.age);
			landings(client, domain);
			if (domain.age > domain.end() + KEEP) {
				stopSounds(client, domain);
				it.remove();
			}
		}
	}

	/** Into another world, or out of the game: none of these are this player's any more. */
	public static void clear(MinecraftClient client) {
		for (ClientDomain domain : DOMAINS.values()) {
			stopSounds(client, domain);
		}
		DOMAINS.clear();
		DAZED.clear();
		hud(client);
	}

	/**
	 * The broken, all through their daze: now and then a faint mote of light drifting up off their head and away (the
	 * effects' own swirls are round them too), fewer as it wears off. Not their own, seen from their own eyes.
	 */
	private static void motes(MinecraftClient client) {
		if (DAZED.isEmpty() || client.world == null) {
			return;
		}
		long now = client.world.getTime();
		Iterator<Map.Entry<Integer, Long>> it = DAZED.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Long> entry = it.next();
			Entity entity = client.world.getEntityById(entry.getKey());
			long left = entry.getValue() - now;
			if (left <= 0L || !(entity instanceof LivingEntity living) || !living.isAlive()
					|| entity == client.player && !client.player.hasStatusEffect(StatusEffects.SLOWNESS)) {
				it.remove();
				continue;
			}
			boolean ownEyes = entity == client.player && !client.gameRenderer.getCamera().isThirdPerson();
			int every = left < 60L ? 20 : 12;
			if (ownEyes || (now + entity.getId()) % every != 0L) {
				continue;
			}
			// One at a time, rising off the top of the head and away, so each is seen on its own.
			Random random = entity.getRandom();
			double w = entity.getWidth() * 0.5;
			client.world.addParticle(ParticleTypes.END_ROD, entity.getX() + (random.nextDouble() - 0.5) * w,
					entity.getBoundingBox().maxY + 0.05, entity.getZ() + (random.nextDouble() - 0.5) * w,
					(random.nextDouble() - 0.5) * 0.02, 0.045 + random.nextDouble() * 0.02, (random.nextDouble() - 0.5) * 0.02);
		}
	}

	/**
	 * The caster's shots put the HUD away, and the hand with it (the camera is out in front of him, not in his eyes), and
	 * so do the black and the void opening for everyone in it; the Domain's name is still drawn over them. Put back as
	 * it was after.
	 */
	private static void hud(MinecraftClient client) {
		boolean takeOver = cinematic(client);
		if (takeOver && !hudOverride) {
			savedHudHidden = client.options.hudHidden;
			client.options.hudHidden = true;
			hudOverride = true;
		} else if (!takeOver && hudOverride) {
			client.options.hudHidden = savedHudHidden;
			hudOverride = false;
		}
	}

	/** Whether this client hears {@code domain} from inside: casting it, caught in it, or with their camera within it. */
	private static boolean hearsInside(MinecraftClient client, ClientDomain domain) {
		if (domain.mine || domain.holdsMe) {
			return true;
		}
		return client.gameRenderer != null && domain.contains(client.gameRenderer.getCamera().getPos());
	}

	/** The sounds of every mark the timeline passes going from {@code from} to {@code to}. */
	private static void cues(MinecraftClient client, ClientDomain domain, int from, int to) {
		if (client.player == null) {
			return;
		}
		double far = domain.radius + HEARD;
		boolean inRange = client.player.getPos().squaredDistanceTo(domain.center) < far * far;
		if (passed(from, to, DomainTimeline.SIGN)) {
			if (domain.mine) {
				master(client, GojoSounds.SIGN, 1.0F);
			} else if (inRange) {
				at(client, domain.center, GojoSounds.SIGN_WORLD, 1.4F);
			}
		}
		if (passed(from, to, DomainTimeline.RISE - 4)) {
			domain.heardInside = hearsInside(client, domain);
			if (domain.heardInside) {
				master(client, GojoSounds.CLOSE, 1.0F);
			} else if (inRange) {
				at(client, domain.center, GojoSounds.CLOSE_WORLD, 3.0F);
			}
		}
		// His voice: heard by him as it is, by anyone else in reach from where he stands; "Unlimited Void", once it has
		// closed, by everyone in it all round, as the void's own sounds are.
		if (passed(from, to, SAYS_EXPANSION)) {
			says(client, domain, GojoSounds.VOICE_EXPANSION, inRange);
		}
		if (passed(from, to, SAYS_VOID) && to < domain.collapse()) {
			if (hearsInside(client, domain)) {
				master(client, GojoSounds.VOICE_VOID, 1.0F);
			} else {
				says(client, domain, GojoSounds.VOICE_VOID, inRange);
			}
		}
		// Between the close and the void, silence. Then the void opens with a bloom of sound, and its hum goes on under it.
		if (passed(from, to, DomainTimeline.VOID) && to < domain.collapse() && hearsInside(client, domain)) {
			master(client, GojoSounds.REVEAL, 1.0F);
			domain.voidSound = new LoopSound(GojoSounds.VOID, 0.9F);
			client.getSoundManager().play(domain.voidSound);
			if (domain.holdsMe) {
				startFlood(client, domain);
			}
		}
		// Each step down: the flood surging through whoever it holds (not caught up all at once by a shattering).
		int steps = DomainTimeline.steps(domain.hold);
		for (int k = 1; k < steps && !domain.shattered; k++) {
			if (passed(from, to, DomainTimeline.step(domain.hold, k))) {
				if (domain.holdsMe) {
					master(client, GojoSounds.PULSE, 1.0F);
				} else if (hearsInside(client, domain)) {
					nearest(client, domain.caught).forEach(e -> at(client, e.getPos(), GojoSounds.PULSE_WORLD, 1.0F));
				}
			}
		}
		// Its end: let go of, it fades away; cut short, it shatters.
		if (passed(from, to, domain.collapse())) {
			stopSounds(client, domain);
			if (hearsInside(client, domain)) {
				master(client, domain.shattered ? GojoSounds.COLLAPSE : GojoSounds.FADE, 1.0F);
			} else if (inRange) {
				at(client, domain.center, domain.shattered ? GojoSounds.COLLAPSE_WORLD : GojoSounds.FADE_WORLD, 3.0F);
			}
			if (!domain.shattered) {
				broken(client, domain);
			}
		}
	}

	/**
	 * Let go of at its end: the caught come out of it broken. Whoever is on the ground then drops to their knees as the
	 * ringing in their head snaps off (heard by them inside, and by whoever is by as their knees hit the ground), and
	 * all of them are dazed.
	 */
	private static void broken(MinecraftClient client, ClientDomain domain) {
		if (client.world == null) {
			return;
		}
		long until = client.world.getTime() + DomainTimeline.DAZE;
		for (int id : domain.caught) {
			if (client.world.getEntityById(id) instanceof LivingEntity entity && entity.isAlive()) {
				DAZED.put(id, until);
				if (grounded(client, entity)) {
					domain.knelt.put(id, domain.collapse());
				} else {
					domain.falling.add(id);
				}
			}
		}
		if (domain.holdsMe) {
			master(client, GojoSounds.BREAK, 1.0F);
		}
		nearest(client, domain.knelt.keySet()).forEach(e -> at(client, e.getPos(), GojoSounds.BREAK_WORLD, 1.2F));
	}

	/**
	 * Whoever it let go of in the air goes down onto their knees as they land (their knees giving out a few ticks after,
	 * as they would have where they stood), unless they are still falling a while after.
	 */
	private static void landings(MinecraftClient client, ClientDomain domain) {
		if (domain.falling.isEmpty() || client.world == null) {
			return;
		}
		if (domain.age > domain.collapse() + LAND) {
			domain.falling.clear();
			return;
		}
		List<Integer> landed = new ArrayList<>();
		for (int id : domain.falling) {
			if (client.world.getEntityById(id) instanceof LivingEntity entity && entity.isAlive()) {
				if (grounded(client, entity)) {
					landed.add(id);
					domain.knelt.put(id, domain.age - DomainTimeline.SNAP + 4);
				}
			} else {
				landed.add(id);
			}
		}
		landed.forEach(domain.falling::remove);
		nearest(client, landed.stream().filter(domain.knelt::containsKey).toList())
				.forEach(e -> at(client, e.getPos(), GojoSounds.BREAK_WORLD, 1.2F));
	}

	/** Whether {@code entity} stands on something. */
	private static boolean grounded(MinecraftClient client, LivingEntity entity) {
		return !client.world.isSpaceEmpty(entity, entity.getBoundingBox().offset(0.0, -0.1, 0.0));
	}

	/** Of {@code ids}, the few that are near this player (not this player), nearest first. */
	private static List<Entity> nearest(MinecraftClient client, Collection<Integer> ids) {
		List<Entity> near = new ArrayList<>();
		if (client.world == null || client.player == null) {
			return near;
		}
		for (int id : ids) {
			Entity entity = client.world.getEntityById(id);
			if (entity != null && entity != client.player && entity.squaredDistanceTo(client.player) < NEAR * NEAR) {
				near.add(entity);
			}
		}
		near.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(client.player)));
		return near.subList(0, Math.min(VOICES, near.size()));
	}

	/** His voice, {@code line}: in his own ears as it is; for anyone else in reach, from where he stands (or its middle). */
	private static void says(MinecraftClient client, ClientDomain domain, SoundEvent line, boolean inRange) {
		if (domain.mine) {
			master(client, line, 1.0F);
			return;
		}
		if (!inRange || client.world == null) {
			return;
		}
		PlayerEntity caster = domain.caster != null ? client.world.getPlayerByUuid(domain.caster) : null;
		at(client, caster != null ? caster.getEyePos().subtract(0.0, 1.0, 0.0) : domain.center, line, 2.0F);
	}

	/** What the caught hear, from the void opening until it shatters (once). */
	private static void startFlood(MinecraftClient client, ClientDomain domain) {
		if (domain.floodSound == null && domain.age < domain.collapse()) {
			domain.floodSound = new LoopSound(GojoSounds.FLOOD, 0.8F);
			client.getSoundManager().play(domain.floodSound);
		}
	}

	/** Whether going from tick {@code from} to {@code to} passes {@code mark}. */
	private static boolean passed(int from, int to, int mark) {
		return from < mark && to >= mark;
	}

	private static void stopSounds(MinecraftClient client, ClientDomain domain) {
		for (SoundInstance sound : new SoundInstance[] {domain.voidSound, domain.floodSound}) {
			if (sound != null) {
				client.getSoundManager().stop(sound);
			}
		}
		domain.voidSound = null;
		domain.floodSound = null;
	}

	private static void master(MinecraftClient client, SoundEvent sound, float volume) {
		client.getSoundManager().play(PositionedSoundInstance.master(sound, 1.0F, volume));
	}

	private static void at(MinecraftClient client, Vec3d pos, SoundEvent sound, float volume) {
		client.getSoundManager().play(new PositionedSoundInstance(sound, SoundCategory.PLAYERS, volume, 1.0F, Random.create(), pos.x,
				pos.y + 1.0, pos.z));
	}
}
