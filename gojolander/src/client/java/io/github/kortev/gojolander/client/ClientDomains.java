package io.github.kortev.gojolander.client;

import io.github.kortev.gojolander.GojoSounds;
import io.github.kortev.gojolander.domain.DomainTimeline;
import io.github.kortev.gojolander.network.DomainCaughtPayload;
import io.github.kortev.gojolander.network.DomainEndPayload;
import io.github.kortev.gojolander.network.DomainLockPayload;
import io.github.kortev.shootingstar.client.ClientConfig;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
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
	/** How long after its end a Domain is kept, for the last of the shattering to be drawn. */
	private static final int KEEP = 10;
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

	/** For {@link io.github.kortev.gojolander.domain.DomainHold}: whether this entity is this client's player, held. */
	public static boolean heldHere(Entity entity) {
		return entity == MinecraftClient.getInstance().player && holding() != null;
	}

	/** Whether this client's player can neither move nor look round: held, or casting with the camera taken. */
	public static boolean locked() {
		return holding() != null || shooting();
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
		domain.age = Math.max(domain.age, payload.age());
		cues(client, domain, from, domain.age);
	}

	public static void tick(MinecraftClient client) {
		hud(client);
		if (DOMAINS.isEmpty()) {
			return;
		}
		Iterator<ClientDomain> it = DOMAINS.values().iterator();
		while (it.hasNext()) {
			ClientDomain domain = it.next();
			int from = domain.age;
			domain.age++;
			cues(client, domain, from, domain.age);
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
		hud(client);
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
		// Between the close and the void, silence. Then the void opens with a bloom of sound, and its hum goes on under it.
		if (passed(from, to, DomainTimeline.VOID) && to < domain.collapse() && hearsInside(client, domain)) {
			master(client, GojoSounds.REVEAL, 1.0F);
			domain.voidSound = new LoopSound(GojoSounds.VOID, 0.9F);
			client.getSoundManager().play(domain.voidSound);
			if (domain.holdsMe) {
				startFlood(client, domain);
			}
		}
		if (passed(from, to, domain.collapse())) {
			stopSounds(client, domain);
			if (hearsInside(client, domain)) {
				master(client, GojoSounds.COLLAPSE, 1.0F);
			} else if (inRange) {
				at(client, domain.center, GojoSounds.COLLAPSE_WORLD, 3.0F);
			}
		}
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
