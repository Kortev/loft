package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.client.ClientConfig;
import io.github.kortev.shootingstar.client.ClientStrikes;
import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.network.GapEndPayload;
import io.github.kortev.shootingstar.network.GapLockPayload;
import io.github.kortev.shootingstar.registry.ModSounds;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Client side of Ginnungagap events: their state and their sound cues. */
public final class ClientGaps {
	private static final Map<Integer, ClientGap> GAPS = new LinkedHashMap<>();
	private static boolean hudOverride;
	private static boolean savedHudHidden;

	private ClientGaps() {
	}

	public static Collection<ClientGap> all() {
		return GAPS.values();
	}

	/** The local player's event, while it still owns their screen. */
	@Nullable
	public static ClientGap mine() {
		for (ClientGap gap : GAPS.values()) {
			if (gap.cinematic()) {
				return gap;
			}
		}
		return null;
	}

	/** True while the feed covers the shooter's screen. */
	public static boolean feedShowing(@Nullable ClientGap gap, double t) {
		return gap != null && gap.cinematic() && !gap.feedSkipped && t >= GapTimeline.FEED && t < GapTimeline.INBOUND;
	}

	/** The shooter can neither move, look round, swing nor use anything until the camera is back in their eyes. */
	public static boolean locked() {
		ClientGap gap = mine();
		return gap != null && gap.age < GapTimeline.RETURN;
	}

	/**
	 * The camera is flying where the world's culling has never looked from: the rise into the clouds, and the shots
	 * over the target until the black comes. A tick of margin either side.
	 */
	public static boolean flying() {
		ClientGap gap = mine();
		if (gap == null) {
			return false;
		}
		int t = gap.age;
		return t >= GapTimeline.RISE - 2 && t < GapTimeline.FEED + 2 || t >= GapTimeline.INBOUND - 2 && t < GapTimeline.ERASURE;
	}

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

	public static void onLock(GapLockPayload payload, MinecraftClient client) {
		if (client.world == null || client.player == null) {
			return;
		}
		boolean mine = payload.shooter().equals(client.player.getUuid());
		PlayerEntity shooter = client.world.getPlayerByUuid(payload.shooter());
		Vec3d from = shooter != null ? shooter.getPos() : client.player.getPos();
		ClientGap gap = new ClientGap(payload.gapId(), payload.target(), payload.shooter(), mine, payload.age(), payload.radius(),
				payload.terrain(), payload.swapSpot(), payload.tree(), from);
		gap.feedSkipped = !ClientConfig.feed || payload.age() > GapTimeline.FEED;
		GAPS.put(gap.id, gap);
		if (mine && payload.age() == 0) {
			ClientStrikes.master(ModSounds.GAP_KEY, 1.0F, 1.0F);
		}
	}

	public static void onEnd(GapEndPayload payload) {
		ClientGap gap = GAPS.get(payload.gapId());
		if (gap != null) {
			gap.ended = true;
		}
	}

	/** The skip key: the feed goes, and its sounds with it. */
	public static void skipFeed() {
		ClientGap gap = mine();
		if (gap == null || gap.feedSkipped) {
			return;
		}
		gap.feedSkipped = true;
		gap.feedSounds.forEach(MinecraftClient.getInstance().getSoundManager()::stop);
		gap.feedSounds.clear();
	}

	public static void clear(MinecraftClient client) {
		GAPS.clear();
	}

	public static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null) {
			return;
		}
		hud(client);
		for (Iterator<ClientGap> it = GAPS.values().iterator(); it.hasNext(); ) {
			ClientGap gap = it.next();
			int from = gap.age;
			gap.age++;
			if (gap.cinematic()) {
				cues(gap, from, gap.age);
			}
			boolean over = gap.ended || (!gap.mine && gap.age > GapTimeline.END + 40);
			if (over) {
				it.remove();
			}
		}
	}

	/**
	 * The vanilla HUD goes while the feed is up or the camera is away from the shooter's eyes, and comes back with
	 * them. (Hiding it hides the hand too, so it stays for the key turning in first person.)
	 */
	public static void hud(MinecraftClient client) {
		ClientGap gap = mine();
		boolean away = gap != null && (GapCamera.current(1.0F) != null || feedShowing(gap, gap.age));
		if (away && !hudOverride) {
			savedHudHidden = client.options.hudHidden;
			client.options.hudHidden = true;
			hudOverride = true;
		} else if (!away && hudOverride) {
			client.options.hudHidden = savedHudHidden;
			hudOverride = false;
		}
	}

	private static boolean crossed(int from, int to, int mark) {
		return from < mark && to >= mark;
	}

	private static void cues(ClientGap gap, int from, int to) {
		if (crossed(from, to, GapTimeline.RISE)) {
			ClientStrikes.master(ModSounds.CAMERA_RISE, 1.0F, 1.0F);
		}
		if (!gap.feedSkipped) {
			if (crossed(from, to, GapTimeline.FEED)) {
				ClientStrikes.master(ModSounds.FEED_ZOOM, 1.0F, 1.0F);
				held(gap, ModSounds.FEED_AMBIENCE);
			}
			// The emitters lighting round the frame.
			if (crossed(from, to, GapTimeline.GATE + 10)) {
				held(gap, ModSounds.FEED_WAKE);
			}
			// The window tearing open, the block selected and drawn through.
			if (crossed(from, to, GapTimeline.OPEN + 8)) {
				held(gap, ModSounds.GAP_TEAR);
			}
			if (crossed(from, to, GapTimeline.CUT)) {
				held(gap, ModSounds.GAP_LOCK);
			}
			if (crossed(from, to, GapTimeline.CUT + 12)) {
				held(gap, ModSounds.FEED_LOAD);
			}
			if (crossed(from, to, GapTimeline.SEND + 3)) {
				held(gap, ModSounds.FEED_RELEASE);
			}
			if (crossed(from, to, GapTimeline.FALL + 40)) {
				held(gap, ModSounds.FEED_REENTRY);
			}
		}
		// Six seconds long, ending dead on contact: it carries on whether the feed is up or not.
		if (crossed(from, to, GapTimeline.CONTACT - 120)) {
			ClientStrikes.master(ModSounds.GAP_DRONE, 1.0F, 1.0F);
		}
		if (crossed(from, to, GapTimeline.INBOUND)) {
			ClientStrikes.master(ModSounds.STRIKE_INBOUND_NEAR, 1.0F, 1.0F);
		}
		if (crossed(from, to, GapTimeline.CONTACT)) {
			ClientStrikes.master(ModSounds.GAP_CONTACT, 1.0F, 1.0F);
			ClientStrikes.master(ModSounds.GAP_IMPACT, 1.0F, 1.0F);
		}
		if (crossed(from, to, GapTimeline.BLAST)) {
			ClientStrikes.master(ModSounds.STRIKE_RUMBLE_NEAR, 1.0F, 1.0F);
		}
		if (crossed(from, to, GapTimeline.COLLAPSE)) {
			ClientStrikes.master(ModSounds.GAP_ERASE, 1.0F, 1.0F);
		}
		if (crossed(from, to, GapTimeline.NOTHING)) {
			ClientStrikes.master(ModSounds.GAP_VOID, 1.0F, 1.0F);
		}
	}

	/** A feed sound that stops if the shooter skips the feed. */
	private static void held(ClientGap gap, SoundEvent sound) {
		SoundInstance instance = PositionedSoundInstance.master(sound, 1.0F, 1.0F);
		gap.feedSounds.add(instance);
		MinecraftClient.getInstance().getSoundManager().play(instance);
	}
}
