package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.client.ClientConfig;
import io.github.kortev.shootingstar.client.ClientStrikes;
import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.gap.VoidFloor;
import io.github.kortev.shootingstar.network.GapEndPayload;
import io.github.kortev.shootingstar.network.GapFloorPayload;
import io.github.kortev.shootingstar.network.GapSettlePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
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
	/** When the song in the black comes in: a second after the camera is back in the shooter's eyes. */
	private static final int SONG = GapTimeline.RETURN + 20;
	/** How near someone else's must be to see its tree, and how long it is kept, in the black, waiting to be released (ticks). */
	private static final double SPECTATE_RANGE = 600.0;
	private static final int KEEP = 20 * 60 * 60;
	private static boolean hudOverride;
	private static boolean savedHudHidden;
	private static boolean musicHeld;

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

	/** True while this player's world is gone: everything black, until the key is turned again. */
	public static boolean voidPhase() {
		for (ClientGap gap : GAPS.values()) {
			if (!gap.ended && gap.spectateAt < 0 && gap.age >= GapTimeline.NOTHING && gap.rebuildAt < 0) {
				return true;
			}
		}
		return false;
	}

	/** The event whose rebuild this player is watching (theirs, or the one whose void they were taken into), or null. */
	@Nullable
	public static ClientGap rebuilding() {
		for (ClientGap gap : GAPS.values()) {
			if (gap.rebuildAt >= 0 && !gap.ended) {
				return gap;
			}
		}
		return null;
	}

	/** The key in the shooter's hand still looks whole: until the clunk at the end of its first turn. */
	public static boolean keyStillWhole() {
		ClientGap gap = mine();
		return gap != null && gap.age < 37;
	}

	/** True while {@code player} is turning the Genesis Key, from it coming up to the camera leaving them (and a little after). */
	public static boolean turningKey(PlayerEntity player) {
		for (ClientGap gap : GAPS.values()) {
			if (gap.shooter.equals(player.getUuid()) && gap.age < GapTimeline.RISE + 20) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The shooter can neither move, look round, swing nor use anything until the camera is back in their eyes. Not if
	 * they skipped: then it is all seen from their own eyes, free to move, as anyone else sees it.
	 */
	public static boolean locked() {
		ClientGap gap = mine();
		return gap != null && !gap.feedSkipped && gap.age < GapTimeline.RETURN;
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

	/** No music of Minecraft's own from the key turning until the song in the black has played out. */
	public static boolean holdMusic() {
		return mine() != null || VoidMusic.playing();
	}

	/** The shooter's world shots run without clouds, from the block coming down to the end. */
	public static boolean cloudless() {
		ClientGap gap = mine();
		// Back with the rebuild, so they come in under the sky as it returns rather than all at once at the end.
		return gap != null && !gap.feedSkipped && gap.age >= GapTimeline.INBOUND - 2 && gap.rebuildAt < 0;
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
				payload.terrain(), from);
		gap.feedSkipped = !ClientConfig.feed || payload.age() > GapTimeline.FEED;
		GAPS.put(gap.id, gap);
		if (mine && payload.age() == 0) {
			ClientStrikes.master(ModSounds.GAP_KEY, 1.0F, 1.0F);
		}
	}

	public static void onEnd(GapEndPayload payload) {
		ClientGap gap = GAPS.get(payload.gapId());
		if (gap == null) {
			return;
		}
		// The shooter's own, and anyone's who was taken into the void with them: not at once, but the rebuild. Anyone else
		// near enough sees the tree grow; for the rest it simply ends.
		MinecraftClient client = MinecraftClient.getInstance();
		if ((gap.mine || gap.age >= GapTimeline.NOTHING) && !gap.ended && gap.rebuildAt < 0) {
			gap.rebuildAt = gap.age;
			gap.rebuildSound = new RebuildSound();
			client.getSoundManager().play(gap.rebuildSound);
		} else if (!gap.mine && gap.spectateAt < 0 && client.player != null
				&& client.player.getPos().distanceTo(gap.contact) < SPECTATE_RANGE) {
			// Anyone near enough sees the tree grow out of the hole too, its root reaching out to them.
			gap.spectateAt = gap.age;
			gap.rebuildFrom = client.player.getPos();
		} else {
			gap.ended = true;
		}
	}

	/** The skip key: the feed goes, and its sounds with it; during the rebuild, the rest of it runs six times as fast. */
	public static void skipFeed() {
		ClientGap rebuilding = rebuilding();
		ClientGap gap = rebuilding != null ? rebuilding : mine();
		if (gap != null && gap.rebuildAt >= 0) {
			if (!gap.rebuildHurried) {
				gap.rebuildHurried = true;
				if (gap.rebuildSound != null) {
					gap.rebuildSound.fade();
				}
			}
			return;
		}
		if (gap == null || gap.feedSkipped) {
			return;
		}
		gap.feedSkipped = true;
		gap.skippedAt = gap.age;
		gap.feedSounds.forEach(MinecraftClient.getInstance().getSoundManager()::stop);
		gap.feedSounds.clear();
	}

	/**
	 * Into another world (carried to the rim from another, or home to one): its events are not this one's. Those of the
	 * world arrived in are sent on arrival; the floor of nothing goes with the player (it is set before they are moved).
	 */
	public static void leftWorld() {
		GAPS.clear();
		sawRebuild = false;
		VoidFx.leftWorld();
	}

	public static void clear(MinecraftClient client) {
		GAPS.clear();
		VoidMusic.stop();
		floor = Double.NaN;
		hushed = false;
		sawRebuild = false;
		VoidFx.clear();
	}

	/**
	 * The floor of nothing this player walks on while their world is gone and coming back ({@link VoidFloor}), NaN
	 * while they are on the world's own ground.
	 */
	private static double floor = Double.NaN;
	/** The world is back where this player stands on their floor, so it can push them out of itself again. */
	private static boolean landed;
	/** The world's own sounds have been hushed for it; this player has seen a rebuild start (and so, end). */
	private static boolean hushed;
	private static boolean sawRebuild;
	/** What the world makes that nobody in the void hears: its creatures, its weather, its blocks. */
	private static final SoundCategory[] HUSHED = {SoundCategory.HOSTILE, SoundCategory.NEUTRAL, SoundCategory.AMBIENT, SoundCategory.WEATHER,
			SoundCategory.BLOCKS, SoundCategory.RECORDS};

	/** True while this player walks through the unseen world on their floor: not yet back on its ground. */
	public static boolean passingThrough() {
		return floating() && !landed;
	}

	/** True while this player is held on the floor of nothing, from the black until they are carried home. */
	public static boolean floating() {
		return !Double.isNaN(floor);
	}

	/** The local player's floor, for {@link VoidFloor}. */
	public static double floorFor(net.minecraft.entity.Entity entity) {
		return entity == MinecraftClient.getInstance().player ? floor : Double.NaN;
	}

	/** Whether a sound of {@code category} is one the void keeps from this player. */
	public static boolean hushes(SoundCategory category) {
		if (!floating()) {
			return false;
		}
		for (SoundCategory hushed : HUSHED) {
			if (hushed == category) {
				return true;
			}
		}
		return false;
	}

	/**
	 * As the world comes back where this player stands, their floor settles onto it. Out of any hill they walked into in
	 * the black they rise while it is still dark round them, before the edge of the world being put back gets to them
	 * (faster the deeper they are, and the faster the rebuild is going), so they are standing on top of it when it
	 * appears rather than looking out from inside it. Onto ground just under them they step down once it is there. Out
	 * over a valley or the hole the floor stays where it is, holding them up, until they are carried home.
	 */
	private static void settle(MinecraftClient client, ClientWorld world) {
		landed = false;
		ClientGap back = rebuilding();
		if (!floating() || back == null || client.player == null) {
			return;
		}
		double front = GapRender.rebuildFront(back, back.rebuild(1.0F));
		Vec3d feet = client.player.getPos();
		double out = Math.hypot(feet.x - back.contact.x, feet.z - back.contact.z);
		boolean coming = front < 0.0 ? back.rebuildClock >= GapTimeline.REBUILD_SWEEP : out <= front + 24.0 * back.rebuildRate();
		if (!coming) {
			return;
		}
		landed = front < 0.0 || out <= front - 6.0;
		// The highest ground under any part of them.
		int top = world.getBottomY();
		for (double dx : new double[] {-0.3, 0.3}) {
			for (double dz : new double[] {-0.3, 0.3}) {
				top = Math.max(top, world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(feet.x + dx), MathHelper.floor(feet.z + dz)));
			}
		}
		if (top <= world.getBottomY()) {
			return;
		}
		if (top > floor) {
			// Never more than the floor carries them in a tick (VoidFloor: from more than a block and a half under it, they
			// are left where they are).
			floor += Math.min(top - floor, Math.min(1.4, Math.max(0.6, (top - floor) * 0.25)));
		} else if (landed && floor - top < 6.0) {
			floor -= Math.min(floor - top, 0.3);
		}
	}

	public static void onFloor(GapFloorPayload payload, MinecraftClient client) {
		floor = payload.floor();
		if (client.player != null && floating()) {
			client.player.fallDistance = 0.0F;
			client.player.setVelocity(client.player.getVelocity().multiply(1.0, 0.0, 1.0));
		}
	}

	public static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null) {
			return;
		}
		hud(client);
		if (floating() && !hushed) {
			// Whatever the world was making stops as the black takes it; nothing more of it is heard until it is back.
			for (SoundCategory category : HUSHED) {
				client.getSoundManager().stopSounds(null, category);
			}
		}
		hushed = floating();
		settle(client, world);
		// Once this player's rebuild is over (sooner, if they hurried it), they can be sent home.
		if (rebuilding() != null) {
			sawRebuild = true;
		} else if (sawRebuild) {
			sawRebuild = false;
			if (floating() && ClientPlayNetworking.canSend(GapSettlePayload.ID)) {
				ClientPlayNetworking.send(new GapSettlePayload());
			}
		}
		boolean hold = holdMusic();
		if (hold && !musicHeld) {
			// Whatever was playing stops as the key turns; the tracker itself is held until the hold lifts.
			client.getMusicTracker().stop();
		}
		musicHeld = hold;
		for (Iterator<ClientGap> it = GAPS.values().iterator(); it.hasNext(); ) {
			ClientGap gap = it.next();
			int from = gap.age;
			gap.age++;
			// Everyone in the world hears it happen; the feed's own sounds are the shooter's.
			if (!gap.ended && gap.spectateAt < 0) {
				cues(gap, from, gap.age);
			}
			if (gap.rebuildAt >= 0 && client.player != null) {
				gap.rebuildClock += gap.rebuildRate();
				double r = gap.rebuildClock;
				// The tree knows where to send its root once the shooter has been set down out of the hole.
				if (r >= 5 && gap.rebuildFrom == null) {
					gap.rebuildFrom = client.player.getPos();
				}
				if (r >= GapTimeline.REBUILD_END) {
					gap.ended = true;
				}
			}
			// Someone else's is kept (doing nothing) until it is released, for its tree; but not for ever.
			boolean over = gap.ended || (!gap.mine && (gap.spectateAt >= 0 ? gap.age - gap.spectateAt > TreeRender.SPECTATED
					: gap.age > GapTimeline.END + KEEP));
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
		boolean away = gap != null && (GapCamera.away(1.0F) || feedShowing(gap, gap.age));
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
		if (gap.mine && !gap.feedSkipped && crossed(from, to, GapTimeline.RISE)) {
			ClientStrikes.master(ModSounds.CAMERA_RISE, 1.0F, 1.0F);
		}
		if (gap.mine && !gap.feedSkipped) {
			if (crossed(from, to, GapTimeline.FEED)) {
				ClientStrikes.master(ModSounds.FEED_ZOOM, 1.0F, 1.0F);
				// The bed under the whole of the feed up to the dive.
				held(gap, ModSounds.GAP_AMBIENCE);
			}
			// The emitters lighting round the frame, and the title landing.
			if (crossed(from, to, GapTimeline.GATE + 10)) {
				held(gap, ModSounds.GAP_WAKE);
			}
			// The window tearing open and the camera diving through it.
			if (crossed(from, to, GapTimeline.OPEN + 8)) {
				held(gap, ModSounds.GAP_TEAR);
			}
			// Inside that universe and back out of it, until the block in the middle is selected.
			if (crossed(from, to, GapTimeline.MAP)) {
				held(gap, ModSounds.GAP_MAP);
			}
			if (crossed(from, to, GapTimeline.PICK)) {
				held(gap, ModSounds.GAP_LOCK);
			}
			// Back outside the gate: the block drawn through, the window shutting.
			if (crossed(from, to, GapTimeline.CUT)) {
				held(gap, ModSounds.GAP_EXTRACT);
			}
			if (crossed(from, to, GapTimeline.SEND + 3)) {
				held(gap, ModSounds.GAP_SEND);
			}
			if (crossed(from, to, GapTimeline.FALL)) {
				held(gap, ModSounds.GAP_FALL);
			}
		}
		// Five and three quarter seconds long, from the bridge firing to dead on contact: it carries on whether the
		// feed is up or not.
		if (crossed(from, to, GapTimeline.SEND + 3)) {
			ClientStrikes.master(ModSounds.GAP_DRONE, 1.0F, 1.0F);
		}
		if (crossed(from, to, GapTimeline.INBOUND)) {
			ClientStrikes.master(ModSounds.GAP_INBOUND, 1.0F, 1.0F);
		}
		if (crossed(from, to, GapTimeline.CONTACT)) {
			ClientStrikes.master(ModSounds.GAP_CONTACT, 1.0F, 1.0F);
			ClientStrikes.master(ModSounds.GAP_IMPACT, 1.0F, 1.0F);
		}
		if (crossed(from, to, GapTimeline.BLAST)) {
			ClientStrikes.master(ModSounds.GAP_BLAST, 1.0F, 1.0F);
		}
		if (crossed(from, to, GapTimeline.ERASURE)) {
			ClientStrikes.master(ModSounds.GAP_ERASE, 1.0F, 1.0F);
		}
		if (crossed(from, to, GapTimeline.NOTHING)) {
			ClientStrikes.master(ModSounds.GAP_VOID, 1.0F, 1.0F);
		}
		if (crossed(from, to, SONG)) {
			VoidMusic.start();
		}
	}

	/** A feed sound that stops if the shooter skips the feed. */
	private static void held(ClientGap gap, SoundEvent sound) {
		SoundInstance instance = PositionedSoundInstance.master(sound, 1.0F, 1.0F);
		gap.feedSounds.add(instance);
		MinecraftClient.getInstance().getSoundManager().play(instance);
	}
}
