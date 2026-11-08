package io.github.kortev.chainsawman.hotel;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.contract.Contract;
import io.github.kortev.chainsawman.contract.Contracts;
import io.github.kortev.chainsawman.network.HotelPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * What a hotel does, run by its home's block entity every tick ({@link HomeBlockEntity}).
 *
 * <p>By day it is dead: dark windows, the doors shut and chained. At night, while a player is within {@link #NEAR} blocks and its devil is
 * not away, it wakes: the lobby and some windows lit, the doors open, a hum in the lobby. Each floor above is stranger (the plan has the
 * wrong things; a door breathes on floor 6, a phone rings on floor 7). On floor 8 the corridor is a loop: past {@link HotelPlan#WINDOW_HI}
 * a player is put back {@link HotelPlan#PERIOD} blocks, their position, speed and look kept (the corridor repeats, hidden in fog beyond), and
 * so is a player going down the stairs. Every lap adds to the signs: flesh at the corners, the lamps flickering, the crowd whispering.
 */
public final class Hotel {
	/** How near (blocks, flat) a player must be for a hotel to wake. */
	public static final double NEAR = 96.0;
	/** How far the loop's fog lets you see (blocks). */
	public static final float FOG_END = 11.0F;
	public static final int LAPS_FOR_DEAL = 3;
	public static final int FLOOR_TICKS_FOR_DEAL = 1200;

	private Hotel() {
	}

	/** What is going on in one hotel right now (not saved). */
	public static final class Run {
		/** The night state is on: lit, open, the loop running. */
		public boolean night;
		public boolean started;
		/** How many times each player has been put back, and when each first came onto the loop floor. */
		public final Map<UUID, Integer> laps = new HashMap<>();
		public final Map<UUID, Long> since = new HashMap<>();
		/** Players who took the deal: the loop lets them go. */
		public final Set<UUID> freed = new HashSet<>();
		/** Players who have reached the loop floor (and so are looped if they go down the stairs). */
		public final Set<UUID> top = new HashSet<>();
		/** Players the fog has been switched on for. */
		public final Set<UUID> fogged = new HashSet<>();
		public int flesh;
		/** The flesh at the corners (entities made for the level reached), to take away again. */
		public final List<UUID> growths = new ArrayList<>();
		public int flickerPhase = -1;
		public long flickerUntil;
		public long humAt = -100000;
		public long breathAt = -100000;
		public boolean breathOpen;
		public long phoneAt = -100000;
		public long whisperAt = -100000;
		/** The fight is on: the loop is suspended. */
		public boolean fight;
		public long fightAt;
		public long bleedFrom;
		public float bleedShown;
		public long emptySince;
		public UUID devil;
		/** An offer is out or was made (so not made again) while this hotel's devil waits. */
		public Deal deal;
		public boolean dealt;
		/** Ticks this hotel gives to answer, if not the default (the tests give a short time). */
		public int answerTicks = -1;
		/** The tick the devil died, while the lights die after it; -1 when not. */
		public long dyingAt = -1;

		public boolean fighting() {
			return fight;
		}
	}

	private record In(ServerPlayerEntity player, Vec3d local) {
	}

	/** The in-game day number of the world's time. */
	public static long day(ServerWorld world) {
		return world.getTimeOfDay() / 24000L;
	}

	/** Dusk to dawn, by the clock: hotels wake in it. */
	public static boolean isNightTime(ServerWorld world) {
		long t = world.getTimeOfDay() % 24000L;
		return t >= 12500L && t < 23500L;
	}

	public static void tick(ServerWorld world, HomeBlockEntity h) {
		Run r = h.run;
		long now = world.getTime();
		if (h.away && !r.fighting() && day(world) >= h.awayUntilDay) {
			h.away = false;
			h.markDirty();
		}
		Vec3d centre = HotelBuilder.world(h.origin, h.rot, HotelPlan.SX / 2.0, 0.0, HotelPlan.SZ / 2.0);
		boolean near = false;
		List<In> inside = new ArrayList<>();
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (!p.isAlive() || p.isSpectator()) {
				continue;
			}
			double dx = p.getX() - centre.x;
			double dz = p.getZ() - centre.z;
			if (dx * dx + dz * dz < NEAR * NEAR) {
				near = true;
			}
			Vec3d lp = HotelBuilder.local(h.origin, h.rot, p.getPos());
			if (lp.x > -1.5 && lp.x < HotelPlan.SX + 1.5 && lp.z > -1.5 && lp.z < HotelPlan.SZ + 1.5 && lp.y > -1.0 && lp.y < HotelPlan.SY) {
				inside.add(new In(p, lp));
			}
		}
		if (!r.started) {
			// Loaded afresh: if the world was left in a fight the walls are still bled (the devil and the flesh are not kept): put them right.
			HotelBuilder.bleed(world, h.origin, h.rot, 0.0F);
		}
		if (now % 10 == 0 || !r.started) {
			r.started = true;
			updateNight(world, h, near, inside);
		}
		if (!r.night) {
			return;
		}
		floors(world, h, inside, now);
		loop(world, h, inside, now);
		if (r.dyingAt >= 0) {
			dying(world, h, now);
		}
		if (r.deal != null) {
			dealTick(world, h, inside, now);
		}
		if (r.fight) {
			fight(world, h, inside, now);
		}
	}

	/** Wakes the hotel at night with a player near, puts it to sleep by day (but not while someone is still on the loop floor, or fighting). */
	private static void updateNight(ServerWorld world, HomeBlockEntity h, boolean near, List<In> inside) {
		Run r = h.run;
		boolean onTop = false;
		boolean freedInside = false;
		for (In in : inside) {
			onTop |= in.local().y >= 28.5;
			freedInside |= r.freed.contains(in.player().getUuid());
		}
		boolean want = isNightTime(world) && !h.away && near;
		if (r.night && (r.fighting() || r.dyingAt >= 0 || r.deal != null || onTop && !h.away || h.away && freedInside)) {
			want = true;
		}
		if (want != r.night) {
			r.night = want;
			HotelBuilder.setNight(world, h.origin, h.rot, want);
			if (!want) {
				reset(world, h);
			}
		}
	}

	/** Back to waiting: no laps, no flesh, the fog off. */
	public static void reset(ServerWorld world, HomeBlockEntity h) {
		Run r = h.run;
		r.laps.clear();
		r.since.clear();
		r.top.clear();
		r.freed.clear();
		if (r.flesh != 0) {
			r.flesh = 0;
			growths(world, h, 0);
		}
		if (r.flickerPhase >= 0) {
			r.flickerPhase = -1;
		}
		for (UUID id : new ArrayList<>(r.fogged)) {
			fog(world, id, 0.0F);
		}
		r.fogged.clear();
	}

	// ---- The floors.

	/** The wrong things that are heard: the lobby's hum, the breathing door of floor 6, the phone ringing on floor 7. */
	private static void floors(ServerWorld world, HomeBlockEntity h, List<In> inside, long now) {
		Run r = h.run;
		HotelPlan plan = HotelPlan.get();
		boolean lobby = false;
		boolean six = false;
		boolean seven = false;
		for (In in : inside) {
			int floor = (int) Math.floor((in.local().y - 1.0) / HotelPlan.PITCH);
			if (floor == 0 && in.local().x > 17.0) {
				lobby = true;
			} else if (floor == 5) {
				six = true;
			} else if (floor == 6) {
				seven = true;
			}
		}
		if (lobby && now - r.humAt >= 120) {
			r.humAt = now;
			play(world, h, 28.0, 2.0, 4.0, ChainsawSounds.HOTEL_HUM, 0.8F, 1.0F);
		}
		if (six && now - r.breathAt >= 70 && plan.breathDoor != null) {
			r.breathAt = now;
			r.breathOpen = !r.breathOpen;
			for (int k = 0; k < 2; k++) {
				BlockPos at = HotelBuilder.world(h.origin, h.rot, plan.breathDoor.x(), plan.breathDoor.y() + k, plan.breathDoor.z());
				BlockState s = world.getBlockState(at);
				if (s.getBlock() instanceof DoorBlock) {
					world.setBlockState(at, s.with(DoorBlock.OPEN, r.breathOpen), Block.NOTIFY_LISTENERS);
				}
			}
			play(world, h, plan.breathDoor.x() + 0.5, plan.breathDoor.y() + 1.0, plan.breathDoor.z() + 0.5, ChainsawSounds.HOTEL_BREATH, 1.0F, r.breathOpen ? 1.0F : 0.85F);
		}
		if (seven && now - r.phoneAt >= 90 && plan.phone != null) {
			r.phoneAt = now;
			play(world, h, plan.phone.x() + 0.5, plan.phone.y() + 0.5, plan.phone.z() + 0.5, ChainsawSounds.HOTEL_PHONE, 1.2F, 1.0F);
		}
	}

	// ---- The loop.

	private static void loop(ServerWorld world, HomeBlockEntity h, List<In> inside, long now) {
		Run r = h.run;
		Set<UUID> looped = new HashSet<>();
		Set<UUID> here = new HashSet<>();
		int maxLaps = 0;
		for (In in : inside) {
			ServerPlayerEntity p = in.player();
			UUID id = p.getUuid();
			Vec3d lp = in.local();
			if (lp.y >= 28.8) {
				r.top.add(id);
			} else if (lp.y < 24.0) {
				r.top.remove(id);
				r.since.remove(id);
			}
			boolean free = r.freed.contains(id) || r.fighting() || h.away;
			boolean corridor = lp.y >= 28.5 && lp.y < 32.0 && lp.x >= 1.0 && lp.x <= HotelPlan.SX - 1.0 && lp.z >= HotelPlan.CORR_Z0 && lp.z <= HotelPlan.CORR_Z1 + 1.0;
			boolean shaft = lp.x >= HotelPlan.SHAFT_X0 - 1.0 && lp.x <= HotelPlan.SHAFT_X1 + 1.0 && lp.z >= 0.0 && lp.z <= 4.2;
			if (!r.top.contains(id) || free) {
				continue;
			}
			here.add(id);
			r.since.putIfAbsent(id, now);
			if (corridor) {
				looped.add(id);
				if (lp.x >= HotelPlan.WINDOW_HI) {
					shift(h, p, -HotelPlan.PERIOD);
					lap(r, id);
				} else if (lp.x < HotelPlan.WINDOW_LO) {
					shift(h, p, HotelPlan.PERIOD);
					lap(r, id);
				}
			} else if (shaft && lp.y < 28.0 && lp.y > 24.0) {
				// Down the stairs: back at the start of the corridor.
				Vec3d to = HotelBuilder.world(h.origin, h.rot, HotelPlan.WINDOW_LO + 1.0, 29.0, 6.5);
				p.networkHandler.requestTeleport(to.x, to.y, to.z, HotelBuilder.alongYaw(h.rot), 0.0F);
				p.setVelocity(Vec3d.ZERO);
				lap(r, id);
				looped.add(id);
			}
			maxLaps = Math.max(maxLaps, r.laps.getOrDefault(id, 0));
		}
		maybeDeal(world, h, here, now);
		// The signs build up with the laps.
		int level = Math.min(3, maxLaps);
		if (level != r.flesh && !r.fighting()) {
			r.flesh = level;
			growths(world, h, level);
		}
		if (!here.isEmpty() && !r.fighting()) {
			flicker(world, h, maxLaps, now);
			int gap = Math.max(40, 160 - 40 * maxLaps);
			if (now - r.whisperAt >= gap) {
				r.whisperAt = now;
				play(world, h, HotelPlan.WINDOW_LO + 6.0, 30.5, 6.5, ChainsawSounds.HOTEL_WHISPER, 0.3F + 0.25F * maxLaps, 0.9F + 0.05F * maxLaps);
			}
		}
		// The fog that hides the corridor's ends, for those who are looped.
		for (UUID id : looped) {
			if (r.fogged.add(id)) {
				fog(world, id, FOG_END);
			}
		}
		for (UUID id : new ArrayList<>(r.fogged)) {
			if (!looped.contains(id)) {
				r.fogged.remove(id);
				fog(world, id, 0.0F);
			}
		}
	}

	private record G(int kind, double x, double y, double z, boolean north) {
	}

	/** Where the flesh grows at each level: level 1 the corners every period, 2 more of them, 3 bulges out of the walls and hangs from the ceiling. */
	private static List<G> growthPlaces(int level) {
		List<G> out = new ArrayList<>();
		for (int x = HotelPlan.CORR_X0; x <= HotelPlan.CORR_X1; x++) {
			int m = x % HotelPlan.PERIOD;
			if (level >= 1 && (m == 3 || m == 9)) {
				out.add(new G(FleshGrowthEntity.FLOOR, x + 0.5, 29.0, 5.0, true));
				out.add(new G(FleshGrowthEntity.FLOOR, x + 0.5, 29.0, 8.0, false));
			}
			if (level >= 2 && (m == 2 || m == 4 || m == 8 || m == 10)) {
				out.add(new G(FleshGrowthEntity.FLOOR, x + 0.5, 29.0, m % 4 == 0 ? 8.0 : 5.0, m % 4 != 0));
			}
			if (level >= 3 && (m == 6 || m == 0)) {
				out.add(new G(FleshGrowthEntity.WALL, x + 0.5, 30.0, 5.0, true));
				out.add(new G(FleshGrowthEntity.WALL, x + 0.5, 30.5, 8.0, false));
			}
		}
		return out;
	}

	/** The flesh at {@code level} (0 none): the old taken away and the new made (the same places every period, so the loop stays seamless). */
	static void growths(ServerWorld world, HomeBlockEntity h, int level) {
		Run r = h.run;
		for (UUID id : r.growths) {
			net.minecraft.entity.Entity e = world.getEntity(id);
			if (e != null) {
				e.discard();
			}
		}
		r.growths.clear();
		int n = 0;
		for (G g : growthPlaces(level)) {
			FleshGrowthEntity e = new FleshGrowthEntity(ChainsawMan.FLESH_GROWTH, world);
			Vec3d at = HotelBuilder.world(h.origin, h.rot, g.x(), g.y(), g.z());
			float yaw = HotelBuilder.alongYaw(h.rot) + (g.north() ? 90.0F : -90.0F);
			e.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0F);
			e.set(g.kind(), (n++ * 7919 + g.kind() * 31) ^ (int) (g.x() * 13));
			world.spawnEntity(e);
			r.growths.add(e.getUuid());
		}
	}

	private static void lap(Run r, UUID id) {
		r.laps.merge(id, 1, Integer::sum);
	}

	/** The lamps of one phase of the loop going out for a moment, now and then, more as the laps go on. */
	private static void flicker(ServerWorld world, HomeBlockEntity h, int laps, long now) {
		Run r = h.run;
		if (r.flickerPhase >= 0 && now >= r.flickerUntil) {
			lamps(world, h, r.flickerPhase, true);
			r.flickerPhase = -1;
		} else if (r.flickerPhase < 0 && laps > 0 && world.random.nextInt(Math.max(6, 70 - 20 * laps)) == 0) {
			r.flickerPhase = new int[] {1, 5, 9}[world.random.nextInt(3)];
			r.flickerUntil = now + 2 + world.random.nextInt(4);
			lamps(world, h, r.flickerPhase, false);
		}
	}

	private static void lamps(ServerWorld world, HomeBlockEntity h, int phase, boolean lit) {
		for (HotelPlan.Lamp l : HotelPlan.get().lamps) {
			if (l.phase() == phase) {
				HotelBuilder.lamp(world, HotelBuilder.world(h.origin, h.rot, l.x(), l.y(), l.z()), lit);
			}
		}
	}

	/**
	 * Puts {@code p} {@code dx} blocks along the corridor (local x) with nothing else changed: the client is told to move by this much from
	 * where it is when the packet arrives, so its speed and its look carry across, and lag does not matter.
	 */
	public static void shift(HomeBlockEntity h, ServerPlayerEntity p, double dx) {
		Vec3d d = HotelBuilder.world(BlockPos.ORIGIN, h.rot, dx, 0.0, 0.0);
		// (The call takes the place to end up at; with every axis marked relative it tells the client the difference.)
		p.networkHandler.requestTeleport(p.getX() + d.x, p.getY(), p.getZ() + d.z, p.getYaw(), p.getPitch(), Set.of(PositionFlag.X, PositionFlag.Y, PositionFlag.Z, PositionFlag.Y_ROT, PositionFlag.X_ROT));
	}

	static void fog(ServerWorld world, UUID id, float end) {
		ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(id);
		if (p != null && ServerPlayNetworking.canSend(p, HotelPayloads.Fog.ID)) {
			ServerPlayNetworking.send(p, new HotelPayloads.Fog(end));
		}
	}

	/** A sound at a local point of the hotel, for everyone near. */
	static void play(ServerWorld world, HomeBlockEntity h, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
		Vec3d at = HotelBuilder.world(h.origin, h.rot, x, y, z);
		world.playSound(null, at.x, at.y, at.z, sound, SoundCategory.AMBIENT, volume, pitch);
	}

	// ---- The deal.

	/** One offer of the devil's contract to the players in the loop. */
	public static final class Deal {
		public final Set<UUID> players = new HashSet<>();
		public final Set<UUID> answered = new HashSet<>();
		public final Set<UUID> accepted = new HashSet<>();
		public final Map<UUID, Long> deadline = new HashMap<>();
		public long started;
		public boolean open;
		public int said;
	}

	/** Ticks a player has to answer the offer (20 seconds), and when its words and its screen come (ticks after it starts). */
	public static int answerTicks = 400;
	public static final int[] SPEECH_AT = {0, 26, 54};
	public static final int OPENS_AT = 84;
	private static final String[] SPEECH = {"message.chainsawman.devil.deal.1", "message.chainsawman.devil.deal.2", "message.chainsawman.devil.deal.3"};

	/** The Eternity Devil speaks to those in the loop once someone has walked three laps or been on the floor for a minute. */
	private static void maybeDeal(ServerWorld world, HomeBlockEntity h, Set<UUID> here, long now) {
		Run r = h.run;
		if (r.deal != null || r.dealt || r.fighting() || h.away || here.isEmpty()) {
			return;
		}
		boolean ready = false;
		for (UUID id : here) {
			ready |= r.laps.getOrDefault(id, 0) >= LAPS_FOR_DEAL || now - r.since.getOrDefault(id, now) >= FLOOR_TICKS_FOR_DEAL;
		}
		if (!ready) {
			return;
		}
		r.dealt = true;
		if (world.getGameRules().getBoolean(HotelRules.ONE_CONTRACT_PER_DEVIL) && holderStillHolds(world, h)) {
			// Someone already holds this devil's contract: it is not offered again. It does not let anyone out.
			speak(world, here, "message.chainsawman.devil.held", 90, 0);
			startFight(world, h, now + 60);
			return;
		}
		Deal d = new Deal();
		d.started = now;
		d.players.addAll(here);
		r.deal = d;
	}

	/** Whether the player who holds this hotel's contract still holds it (one who is away is taken to). */
	public static boolean holderStillHolds(ServerWorld world, HomeBlockEntity h) {
		if (h.holder == null) {
			return false;
		}
		ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(h.holder);
		if (p == null) {
			return true;
		}
		if (Contracts.has(p, Contract.ETERNITY)) {
			return true;
		}
		h.holder = null;
		h.markDirty();
		return false;
	}

	private static void dealTick(ServerWorld world, HomeBlockEntity h, List<In> inside, long now) {
		Run r = h.run;
		Deal d = r.deal;
		long t = now - d.started;
		// Anyone who has left the loop floor (or the game) is out of it.
		Set<UUID> there = new HashSet<>();
		for (In in : inside) {
			if (r.top.contains(in.player().getUuid()) && in.local().y >= 28.0) {
				there.add(in.player().getUuid());
			}
		}
		d.players.retainAll(there);
		if (d.players.isEmpty()) {
			r.deal = null;
			r.dealt = false;
			return;
		}
		while (d.said < SPEECH.length && t >= SPEECH_AT[d.said]) {
			speak(world, d.players, SPEECH[d.said], d.said == SPEECH.length - 1 ? 90 : 60, d.said == SPEECH.length - 1 ? 2 : 0);
			voice(world, d.players);
			d.said++;
		}
		if (!d.open && t >= OPENS_AT) {
			d.open = true;
			for (UUID id : d.players) {
				int give = r.answerTicks > 0 ? r.answerTicks : answerTicks;
				d.deadline.put(id, now + give);
				ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(id);
				if (p != null && ServerPlayNetworking.canSend(p, HotelPayloads.Offer.ID)) {
					ServerPlayNetworking.send(p, new HotelPayloads.Offer(give));
				}
			}
		}
		if (!d.open) {
			return;
		}
		boolean done = true;
		for (UUID id : d.players) {
			if (d.answered.contains(id)) {
				continue;
			}
			if (now >= d.deadline.getOrDefault(id, now)) {
				d.answered.add(id);
				ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(id);
				if (p != null) {
					close(p);
					speak(world, Set.of(id), "message.chainsawman.devil.silence", 60, 0);
				}
			} else {
				done = false;
			}
		}
		if (done) {
			resolve(world, h, now);
		}
	}

	/** Everyone has answered or run out of time: those who took it are out, and whoever is left in the loop meets the devil. */
	private static void resolve(ServerWorld world, HomeBlockEntity h, long now) {
		Run r = h.run;
		Deal d = r.deal;
		r.deal = null;
		boolean anyAccepted = !d.accepted.isEmpty();
		if (anyAccepted) {
			// The devil goes with its contract: it is away for a few days.
			h.away = true;
			h.awayUntilDay = day(world) + world.getGameRules().getInt(HotelRules.DEVIL_RESPAWN_DAYS);
			h.markDirty();
		}
		Set<UUID> left = new HashSet<>();
		for (UUID id : d.players) {
			if (!d.accepted.contains(id)) {
				left.add(id);
			}
		}
		if (!left.isEmpty()) {
			startFight(world, h, now);
		}
	}

	/** Called when a player answers the contract screen. */
	public static void answer(ServerPlayerEntity player, boolean accept) {
		ServerWorld world = player.getServerWorld();
		UUID id = player.getUuid();
		for (HomeBlockEntity h : HomeBlockEntity.LOADED) {
			if (h.getWorld() != world) {
				continue;
			}
			Deal d = h.run.deal;
			if (d == null || !d.open || !d.players.contains(id) || d.answered.contains(id)) {
				continue;
			}
			d.answered.add(id);
			close(player);
			if (!accept) {
				speak(world, Set.of(id), "message.chainsawman.devil.refused", 70, 0);
				return;
			}
			if (Contracts.grant(player, Contract.ETERNITY) == Contracts.Result.HYBRID) {
				speak(world, Set.of(id), "message.chainsawman.devil.hybrid", 90, 0);
				return;
			}
			d.accepted.add(id);
			h.run.freed.add(id);
			h.holder = id;
			h.markDirty();
			world.playSound(null, player.getX(), player.getY(), player.getZ(), ChainsawSounds.HOTEL_SEAL, SoundCategory.HOSTILE, 1.5F, 1.0F);
			speak(world, Set.of(id), "message.chainsawman.devil.deal.done", 90, 0);
			h.run.fogged.remove(id);
			fog(world, id, 0.0F);
			return;
		}
	}

	private static void close(ServerPlayerEntity p) {
		if (ServerPlayNetworking.canSend(p, HotelPayloads.Offer.ID)) {
			ServerPlayNetworking.send(p, new HotelPayloads.Offer(0));
		}
	}

	/** Words on the screen of {@code to}, in the devil's crowd voice. */
	static void speak(ServerWorld world, java.util.Collection<UUID> to, String key, int ticks, int style) {
		for (UUID id : to) {
			ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(id);
			if (p != null && ServerPlayNetworking.canSend(p, HotelPayloads.Speech.ID)) {
				ServerPlayNetworking.send(p, new HotelPayloads.Speech(key, ticks, style));
			}
		}
	}

	private static void voice(ServerWorld world, Set<UUID> to) {
		for (UUID id : to) {
			ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(id);
			if (p != null) {
				world.playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), ChainsawSounds.HOTEL_VOICE, SoundCategory.HOSTILE, 2.0F, 0.9F);
			}
		}
	}

	// ---- The fight.

	/** The walls bleed and the devil grows out of them at the corridor's end ({@code at}: the tick it starts). */
	public static void startFight(ServerWorld world, HomeBlockEntity h, long at) {
		Run r = h.run;
		if (r.fight) {
			return;
		}
		r.fight = true;
		r.fightAt = at;
		r.bleedFrom = at;
		r.bleedShown = 0.0F;
		r.emptySince = at;
		for (UUID id : new ArrayList<>(r.fogged)) {
			fog(world, id, 0.0F);
		}
		r.fogged.clear();
		play(world, h, 30.0, 30.0, 6.5, ChainsawSounds.HOTEL_BLEED, 2.5F, 1.0F);
		EternityDevilEntity devil = EternityDevilEntity.spawn(world, h);
		r.devil = devil.getUuid();
		// Anyone standing where it grows is put in front of it.
		Vec3d front = HotelBuilder.world(h.origin, h.rot, 31.0, 29.0, 6.5);
		for (ServerPlayerEntity p : world.getPlayers()) {
			Vec3d lp = HotelBuilder.local(h.origin, h.rot, p.getPos());
			if (lp.x > EternityDevilEntity.LOCAL_X - EternityDevilEntity.WIDTH / 2.0 - 0.3 && lp.x < HotelPlan.SX && lp.y > 28.0 && lp.y < 33.0 && lp.z > 4.0 && lp.z < 9.0) {
				p.networkHandler.requestTeleport(front.x, front.y, front.z, p.getYaw(), p.getPitch());
			}
		}
	}

	private static void fight(ServerWorld world, HomeBlockEntity h, List<In> inside, long now) {
		Run r = h.run;
		float amount = Math.min(1.0F, Math.max(0.0F, (now - r.bleedFrom) / 70.0F));
		if (Math.abs(amount - r.bleedShown) >= 0.04F || amount >= 1.0F && r.bleedShown < 1.0F) {
			r.bleedShown = amount;
			HotelBuilder.bleed(world, h.origin, h.rot, amount);
		}
		net.minecraft.entity.Entity e = r.devil == null ? null : world.getEntity(r.devil);
		if (!(e instanceof EternityDevilEntity devil) || devil.isRemoved()) {
			// (Not found in the first moments: its chunk may still be coming.)
			if (now - r.fightAt > 60) {
				endFight(world, h, null);
			}
			return;
		}
		if (devil.isDead()) {
			return;
		}
		boolean anyone = false;
		for (In in : inside) {
			anyone |= in.local().y >= 28.5 && !in.player().isSpectator();
		}
		if (anyone) {
			r.emptySince = now;
		} else if (now - r.emptySince > 400) {
			// Nobody is left to fight: it sinks back into the walls.
			endFight(world, h, devil);
		}
	}

	/** The fight is over without a kill: the devil goes back into the walls, the flesh and blood with it. */
	private static void endFight(ServerWorld world, HomeBlockEntity h, EternityDevilEntity devil) {
		Run r = h.run;
		if (devil != null) {
			devil.discard();
		}
		r.fight = false;
		r.devil = null;
		r.bleedShown = 0.0F;
		r.deal = null;
		r.dealt = false;
		r.laps.clear();
		r.since.clear();
		HotelBuilder.bleed(world, h.origin, h.rot, 0.0F);
		if (r.flesh != 0) {
			r.flesh = 0;
			growths(world, h, 0);
		}
	}

	/** The devil is dead: the loop breaks (the flesh and blood on the walls gone, the lights dying), it drops its loot and is away for a few days. */
	public static void killed(ServerWorld world, EternityDevilEntity devil) {
		if (!(world.getBlockEntity(devil.homePos) instanceof HomeBlockEntity h)) {
			return;
		}
		Run r = h.run;
		long now = world.getTime();
		r.fight = false;
		r.devil = null;
		r.deal = null;
		r.laps.clear();
		r.since.clear();
		r.dyingAt = now;
		h.away = true;
		h.awayUntilDay = day(world) + world.getGameRules().getInt(HotelRules.DEVIL_RESPAWN_DAYS);
		h.markDirty();
		HotelBuilder.bleed(world, h.origin, h.rot, 0.0F);
		r.bleedShown = 0.0F;
		if (r.flesh != 0) {
			r.flesh = 0;
			growths(world, h, 0);
		}
		for (UUID id : new ArrayList<>(r.fogged)) {
			fog(world, id, 0.0F);
		}
		r.fogged.clear();
		play(world, h, 24.0, 30.0, 6.5, ChainsawSounds.HOTEL_LOOP_BREAK, 3.0F, 1.0F);
		// The flesh creeping on the floor goes with it.
		for (FleshPatchEntity patch : world.getEntitiesByType(ChainsawMan.FLESH_PATCH, e -> e.squaredDistanceTo(devil) < 40.0 * 40.0)) {
			patch.discard();
		}
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (p.squaredDistanceTo(devil) < 40.0 * 40.0) {
				speak(world, Set.of(p.getUuid()), "message.chainsawman.devil.last", 80, 1);
			}
		}
	}

	/** Its loot, on the floor where it stood, when it has collapsed. */
	public static void dropLoot(ServerWorld world, EternityDevilEntity devil) {
		Vec3d at = devil.getPos().add(HotelBuilder.world(BlockPos.ORIGIN, devil.rot, -2.8, 0.2, 0.0));
		drop(world, at, new ItemStack(ChainsawMan.ETERNITY_FLESH, FLESH_DROP));
		drop(world, at, new ItemStack(ChainsawMan.ETERNITY_BLOOD, BLOOD_DROP));
	}

	/** What the devil drops: three lumps of its flesh and two bottles of its blood. */
	public static final int FLESH_DROP = 3;
	public static final int BLOOD_DROP = 2;

	private static void drop(ServerWorld world, Vec3d at, ItemStack stack) {
		ItemEntity item = new ItemEntity(world, at.x, at.y, at.z, stack);
		item.setVelocity(world.random.nextGaussian() * 0.05, 0.25, world.random.nextGaussian() * 0.05);
		// It glows, to be found in the dark of the corridor (the lights are dying).
		item.setGlowing(true);
		world.spawnEntity(item);
	}

	/** The lights dying after the kill, east to west along the loop floor and then everywhere, and then the hotel going back to sleep. */
	private static void dying(ServerWorld world, HomeBlockEntity h, long now) {
		Run r = h.run;
		long t = now - r.dyingAt;
		HotelPlan plan = HotelPlan.get();
		if (t < DYING_TICKS) {
			// The loop floor's lamps go out from the east end back toward the stairs, flickering; then the rest with the hotel.
			double x = HotelPlan.CORR_X1 + 1.0 - (t / (DYING_TICKS * 0.75)) * 36.0;
			for (HotelPlan.Lamp l : plan.lamps) {
				if (l.floor() == HotelPlan.FLOORS - 1) {
					boolean lit = l.x() < x && (t % 8 < 5 || t > DYING_TICKS * 0.6);
					HotelBuilder.lamp(world, HotelBuilder.world(h.origin, h.rot, l.x(), l.y(), l.z()), lit);
				}
			}
		} else {
			r.dyingAt = -1;
			r.night = false;
			HotelBuilder.setNight(world, h.origin, h.rot, false);
			reset(world, h);
		}
	}

	/** How long the lights take to die after the kill (ticks). */
	public static final int DYING_TICKS = 160;

	/** Puts a hotel down at {@code origin} turned {@code rot}: its home, or null if the home could not be found. */
	public static HomeBlockEntity place(ServerWorld world, BlockPos origin, int rot) {
		HotelBuilder.place(world, origin, rot, null);
		BlockPos at = HotelBuilder.world(origin, rot, HotelPlan.HOME_X, HotelPlan.HOME_Y, HotelPlan.HOME_Z);
		return world.getBlockEntity(at) instanceof HomeBlockEntity home ? home : null;
	}

	/** {@code /chainsaw eternity}: a hotel whose front faces {@code player}, its plaza starting three blocks ahead. */
	public static HomeBlockEntity placeInFront(ServerPlayerEntity player) {
		Direction facing = player.getHorizontalFacing();
		// The front's outward direction (local -z) is north, east, south, west for the turns 0 to 3: it must point at the player.
		int rot = switch (facing) {
			case NORTH -> 2;
			case EAST -> 3;
			case SOUTH -> 0;
			default -> 1;
		};
		BlockPos ahead = player.getBlockPos().offset(facing, 3);
		Vec3d off = HotelBuilder.world(BlockPos.ORIGIN, rot, HotelPlan.SX / 2.0, 0.0, -HotelBuilder.PLAZA);
		BlockPos origin = new BlockPos(ahead.getX() - (int) Math.round(off.x), player.getBlockPos().getY() - 1, ahead.getZ() - (int) Math.round(off.z));
		return place(player.getServerWorld(), origin, rot);
	}
}
