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

	private Hotel() {
	}

	/** What is going on in one hotel right now (not saved). */
	public static final class Run {
		/** The night state is on: lit, open, the loop running. */
		public boolean night;
		public boolean started;
		/** How many times each player has been put back. */
		public final Map<UUID, Integer> laps = new HashMap<>();
		/** The hotel is sealed (from the first player reaching the loop until the devil is killed or spared, or nobody is left inside), and who is inside it. */
		public boolean sealed;
		/** The devil is begging: the contract is on offer to everyone inside. */
		public Beg beg;
		/** Ticks this hotel gives to answer, if not the default (the tests give a short time). */
		public int begTicks = -1;
		/** Finding the devil in the walls, while the hotel is sealed and the fight has not begun. */
		public Hunt hunt;
		/** The squad's members (ids), and how many were rescued and walked out when the loop broke. */
		public final List<UUID> squad = new ArrayList<>();
		public int saved;
		/** Where along the corridor the devil stands in the fight (local x). */
		public double fightX = EternityDevilEntity.LOCAL_X;
		public long sealedAt;
		public final Set<UUID> inside = new HashSet<>();
		public long sealMsgAt = -100000;
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
	/** The devil goes away for {@code devilRespawnDays} days of game time (24000 ticks each: a clock that is set back or stopped does not hold it). */
	static void sendAway(ServerWorld world, HomeBlockEntity h) {
		int days = world.getGameRules().getInt(HotelRules.DEVIL_RESPAWN_DAYS);
		h.away = true;
		h.awayUntilDay = day(world) + days;
		h.awayUntilTick = world.getTime() + days * 24000L;
		h.markDirty();
	}

	/** Whether every chunk of the hotel is loaded (it is not woken, or changed, at the edge of the range with half of it unloaded). */
	static boolean loaded(ServerWorld world, HomeBlockEntity h) {
		net.minecraft.util.math.BlockBox box = HotelBuilder.box(h.origin, h.rot);
		for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
			for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) {
				if (!world.isChunkLoaded(net.minecraft.util.math.ChunkPos.toLong(cx, cz))) {
					return false;
				}
			}
		}
		return true;
	}

	public static boolean isNightTime(ServerWorld world) {
		long t = world.getTimeOfDay() % 24000L;
		return t >= 12500L && t < 23500L;
	}

	public static void tick(ServerWorld world, HomeBlockEntity h) {
		Run r = h.run;
		long now = world.getTime();
		if (!loaded(world, h)) {
			return;
		}
		if (h.away && !r.fighting() && (h.awayUntilTick > 0 ? now >= h.awayUntilTick : day(world) >= h.awayUntilDay)) {
			h.away = false;
			h.awayUntilTick = 0;
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
		sealing(world, h, inside, now);
		if (r.hunt != null) {
			List<ServerPlayerEntity> here = new ArrayList<>();
			for (In in : inside) {
				if (r.inside.contains(in.player().getUuid())) {
					here.add(in.player());
				}
			}
			Hunt.tick(world, h, here, now);
		}
		loop(world, h, inside, now);
		if (r.dyingAt >= 0) {
			dying(world, h, now);
		}
		if (r.beg != null) {
			begTick(world, h, now);
		}
		if (r.fight) {
			fight(world, h, inside, now);
		}
	}

	/** Wakes the hotel at night with a player near, puts it to sleep by day (but not while someone is still on the loop floor, or fighting). */
	private static void updateNight(ServerWorld world, HomeBlockEntity h, boolean near, List<In> inside) {
		Run r = h.run;
		boolean onTop = false;
		for (In in : inside) {
			onTop |= in.local().y >= 28.5;
		}
		boolean want = isNightTime(world) && !h.away && near;
		if (r.night && (r.fighting() || r.sealed || r.dyingAt >= 0 || onTop && !h.away)) {
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
		r.top.clear();
		if (r.sealed) {
			open(world, h);
		}
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

	// ---- The seal.

	/** Where an outsider who gets into a sealed hotel is put: out on the plaza in front of the doors (local). */
	private static final double[] OUT = {27.5, 1.0, -3.5};

	/**
	 * The first player to reach the loop floor closes the loop on everyone on that floor at that moment: they are inside, and the hotel is
	 * sealed until it is over. Anyone else who is in the hotel is put out, the entrance stays shut, and nobody gets in. Someone inside who
	 * dies, or leaves, is no longer inside; when nobody is, it opens.
	 */
	private static void sealing(ServerWorld world, HomeBlockEntity h, List<In> inside, long now) {
		Run r = h.run;
		if (!r.sealed) {
			if (h.away || r.dyingAt >= 0 || r.fighting()) {
				return;
			}
			boolean reached = false;
			for (In in : inside) {
				reached |= in.local().y >= 28.8 && !in.player().isSpectator();
			}
			if (!reached) {
				return;
			}
			r.sealed = true;
			r.sealedAt = now;
			r.inside.clear();
			// Everyone already in the building is inside: those on the loop floor are looped at once, those still climbing join the loop when they arrive.
			for (In in : inside) {
				r.inside.add(in.player().getUuid());
			}
			HotelBuilder.shutEntrance(world, h.origin, h.rot);
			play(world, h, 27.0, 2.0, 0.0, ChainsawSounds.HOTEL_SEAL, 2.0F, 0.7F);
			speak(world, r.inside, "message.chainsawman.hotel.sealed", 70, 0);
			Hunt.start(world, h, now);
			Squad.start(world, h);
			return;
		}
		// Those who are no longer in the hotel (dead, gone, out) are no longer inside.
		Set<UUID> here = new HashSet<>();
		for (In in : inside) {
			here.add(in.player().getUuid());
		}
		r.inside.retainAll(here);
		if (r.inside.isEmpty() && now - r.sealedAt > 20) {
			open(world, h);
			if (r.fight || r.hunt != null || r.devil != null) {
				endFight(world, h, r.devil == null ? null : world.getEntity(r.devil) instanceof EternityDevilEntity d ? d : null);
			}
			return;
		}
		if (now % 10 == 0) {
			HotelBuilder.shutEntrance(world, h.origin, h.rot);
		}
		for (In in : inside) {
			ServerPlayerEntity p = in.player();
			if (!r.inside.contains(p.getUuid()) && !p.isCreative()) {
				Vec3d to = HotelBuilder.world(h.origin, h.rot, OUT[0], OUT[1], OUT[2]);
				p.stopRiding();
				p.teleport(world, to.x, to.y, to.z, p.getYaw(), 0.0F);
				p.setVelocity(Vec3d.ZERO);
				p.sendMessage(net.minecraft.text.Text.translatable("message.chainsawman.hotel.shut"), true);
			}
		}
		// Players at the doors are told they do not open.
		if (now - r.sealMsgAt >= 40) {
			r.sealMsgAt = now;
			Vec3d door = HotelBuilder.world(h.origin, h.rot, 27.5, 1.0, -0.5);
			for (ServerPlayerEntity p : world.getPlayers()) {
				if (!r.inside.contains(p.getUuid()) && p.squaredDistanceTo(door) < 36.0) {
					p.sendMessage(net.minecraft.text.Text.translatable("message.chainsawman.hotel.shut"), true);
				}
			}
		}
	}

	/** Whether {@code pos} is part of a hotel that is sealed now (the home that holds it, or null). */
	public static HomeBlockEntity sealedAt(ServerWorld world, BlockPos pos) {
		for (HomeBlockEntity h : HomeBlockEntity.LOADED) {
			if (h.getWorld() == world && h.run.sealed && HotelBuilder.box(h.origin, h.rot).contains(pos)) {
				return h;
			}
		}
		return null;
	}

	/** The hotel opens: not sealed any more, the entrance as the night has it. */
	static void open(ServerWorld world, HomeBlockEntity h) {
		Run r = h.run;
		if (!r.sealed) {
			return;
		}
		r.sealed = false;
		r.inside.clear();
		Hunt.end(world, h);
		Squad.end(world, h);
		if (r.night) {
			HotelBuilder.setNight(world, h.origin, h.rot, true);
		}
	}

	/** Left-clicking a block of a sealed hotel: it does not break, and a wall of the loop floor is cut (see {@link Hunt#cut}). */
	public static void init() {
		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
				HomeBlockEntity h = sealedAt(sw, pos);
				if (h != null) {
					Hunt.cut(sw, h, pos, sp);
					return net.minecraft.util.ActionResult.FAIL;
				}
			}
			return net.minecraft.util.ActionResult.PASS;
		});
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
				r.laps.remove(id);
			}
			boolean free = !r.inside.contains(id) || r.fighting() || h.away;
			boolean corridor = lp.y >= 28.5 && lp.y < 32.0 && lp.x >= 1.0 && lp.x <= HotelPlan.SX - 1.0 && lp.z >= HotelPlan.CORR_Z0 && lp.z <= HotelPlan.CORR_Z1 + 1.0;
			boolean shaft = lp.x >= HotelPlan.SHAFT_X0 - 1.0 && lp.x <= HotelPlan.SHAFT_X1 + 1.0 && lp.z >= 0.0 && lp.z <= 4.2;
			if (!r.top.contains(id) || free) {
				continue;
			}
			here.add(id);
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
				p.stopRiding();
				p.networkHandler.requestTeleport(to.x, to.y, to.z, HotelBuilder.alongYaw(h.rot), 0.0F);
				p.setVelocity(Vec3d.ZERO);
				lap(r, id);
				looped.add(id);
			}
			maxLaps = Math.max(maxLaps, r.laps.getOrDefault(id, 0));
		}
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
		// (A rider is put off: the mount would stay behind.)
		p.stopRiding();
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

	// ---- Words and contracts.

	// ---- The beg.

	/** How long (ticks) the contract is on offer, and how long the devil begs when there is nobody to answer (only hybrids inside). */
	public static final int BEG_TICKS = 200;
	public static final int BEG_QUIET = 60;

	/** The devil's offer: its contract for its life, to everyone inside, and who has answered what. */
	public static final class Beg {
		public final Set<UUID> players = new HashSet<>();
		public final Set<UUID> hybrids = new HashSet<>();
		public final Set<UUID> answered = new HashSet<>();
		public final Set<UUID> accepted = new HashSet<>();
		/** Those who accepted with every slot full, and have yet to say which contract they give up. */
		public final Set<UUID> dropping = new HashSet<>();
		public long started;
		public int ticks;
	}

	/** The devil is at the line: it begs, in its crowd voice, and every player inside gets the contract screen (a hybrid's with Accept greyed out). */
	static void beg(ServerWorld world, EternityDevilEntity devil) {
		if (!(world.getBlockEntity(devil.homePos) instanceof HomeBlockEntity h)) {
			return;
		}
		Run r = h.run;
		Beg b = new Beg();
		b.started = world.getTime();
		b.ticks = r.begTicks > 0 ? r.begTicks : BEG_TICKS;
		r.beg = b;
		for (UUID id : r.inside) {
			ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(id);
			if (p == null || !p.isAlive()) {
				continue;
			}
			b.players.add(id);
			boolean hybrid = io.github.kortev.chainsawman.form.Heart.has(p);
			if (hybrid) {
				b.hybrids.add(id);
			}
			offer(p, b.ticks, hybrid);
		}
		speak(world, b.players, "message.chainsawman.devil.beg", 90, 1);
		voice(world, b.players);
	}

	private static void begTick(ServerWorld world, HomeBlockEntity h, long now) {
		Run r = h.run;
		Beg b = r.beg;
		for (UUID id : new ArrayList<>(b.players)) {
			if (!r.inside.contains(id)) {
				// Gone (dead, left): out of it.
				b.players.remove(id);
				b.hybrids.remove(id);
				b.dropping.remove(id);
				ServerPlayerEntity left = world.getServer().getPlayerManager().getPlayer(id);
				if (left != null) {
					offer(left, 0, false);
				}
			}
		}
		long t = now - b.started;
		boolean timeout = t >= b.ticks;
		boolean all = true;
		for (UUID id : b.players) {
			if (!b.hybrids.contains(id) && !b.answered.contains(id) || b.dropping.contains(id)) {
				all = false;
			}
		}
		boolean needed = b.players.size() > b.hybrids.size();
		if (timeout || all && (needed || t >= BEG_QUIET)) {
			resolve(world, h);
		}
	}

	/**
	 * Everyone has answered, or run out of time. If anyone took the deal they each hold the contract and the devil tears off part of itself and
	 * sinks into the walls (its loot for everyone, gone for {@code devilRespawnDays}); if nobody did it fights no more and can be killed.
	 */
	private static void resolve(ServerWorld world, HomeBlockEntity h) {
		Run r = h.run;
		Beg b = r.beg;
		r.beg = null;
		for (UUID id : b.players) {
			ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(id);
			if (p != null) {
				offer(p, 0, false);
			}
		}
		EternityDevilEntity devil = r.devil != null && world.getEntity(r.devil) instanceof EternityDevilEntity d ? d : null;
		if (devil == null || devil.isDead()) {
			return;
		}
		if (!b.accepted.isEmpty()) {
			speak(world, r.inside, "message.chainsawman.devil.sink", 70, 0);
			devil.spare();
		} else {
			speak(world, r.inside, "message.chainsawman.devil.broken", 70, 1);
			devil.giveUp();
		}
	}

	/** The player's answer to the contract screen. */
	public static void answer(ServerPlayerEntity player, boolean accept) {
		ServerWorld world = player.getServerWorld();
		UUID id = player.getUuid();
		for (HomeBlockEntity h : HomeBlockEntity.LOADED) {
			Beg b = h.run.beg;
			if (h.getWorld() != world || b == null || !b.players.contains(id) || b.answered.contains(id)) {
				continue;
			}
			offer(player, 0, false);
			if (b.hybrids.contains(id)) {
				// (A hybrid can only refuse: the screen has no Accept to press.)
				b.answered.add(id);
				if (accept) {
					speak(world, Set.of(id), "message.chainsawman.devil.hybrid", 90, 0);
				}
				return;
			}
			b.answered.add(id);
			if (!accept) {
				speak(world, Set.of(id), "message.chainsawman.devil.refused", 40, 0);
				return;
			}
			if (world.getGameRules().getBoolean(HotelRules.ONE_CONTRACT_PER_DEVIL) && h.holder != null && !h.holder.equals(id) && holderStillHolds(world, h)) {
				// Another player holds it already (they answered first): it is not given twice.
				speak(world, Set.of(id), "message.chainsawman.devil.held", 90, 0);
				return;
			}
			Contracts.Result result = Contracts.grant(player, Contract.ETERNITY);
			if (result == Contracts.Result.HYBRID) {
				speak(world, Set.of(id), "message.chainsawman.devil.hybrid", 90, 0);
				return;
			}
			if (result == Contracts.Result.FULL) {
				// Every slot is taken: which one do they give up for it?
				b.dropping.add(id);
				if (ServerPlayNetworking.canSend(player, HotelPayloads.Drop.ID)) {
					ServerPlayNetworking.send(player, new HotelPayloads.Drop((int) Math.max(20, b.started + b.ticks - world.getTime()), Contracts.ids(player)));
				}
				return;
			}
			sealed(world, h, b, player);
			return;
		}
	}

	/** The contract is theirs: the hotel knows who holds it. */
	private static void sealed(ServerWorld world, HomeBlockEntity h, Beg b, ServerPlayerEntity player) {
		b.accepted.add(player.getUuid());
		h.holder = player.getUuid();
		h.markDirty();
		world.playSound(null, player.getX(), player.getY(), player.getZ(), ChainsawSounds.HOTEL_SEAL, SoundCategory.HOSTILE, 1.5F, 1.0F);
	}

	/** The player's answer to "which contract do you give up?": that slot (-1: none, and the new one is not taken). */
	public static void drop(ServerPlayerEntity player, int slot) {
		ServerWorld world = player.getServerWorld();
		for (HomeBlockEntity h : HomeBlockEntity.LOADED) {
			Beg b = h.run.beg;
			if (h.getWorld() != world || b == null || !b.dropping.remove(player.getUuid())) {
				continue;
			}
			if (slot >= 0 && slot < Contracts.count(player) && Contracts.replace(player, slot, Contract.ETERNITY.id) == Contracts.Result.GRANTED) {
				sealed(world, h, b, player);
			}
			return;
		}
	}

	/** The contract screen on (ticks to answer) or off (0) for one player. */
	private static void offer(ServerPlayerEntity p, int ticks, boolean hybrid) {
		if (ServerPlayNetworking.canSend(p, HotelPayloads.Offer.ID)) {
			ServerPlayNetworking.send(p, new HotelPayloads.Offer(ticks, hybrid));
		}
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
		startFight(world, h, at, EternityDevilEntity.LOCAL_X, EternityDevilEntity.MAX_HEALTH);
	}

	/** The devil tears out of the wall with the middle of it at local {@code x} along the corridor and {@code health} (what the cuts left it). */
	public static void startFight(ServerWorld world, HomeBlockEntity h, long at, double x, float health) {
		Run r = h.run;
		if (r.fight) {
			return;
		}
		r.fight = true;
		r.fightAt = at;
		r.fightX = x;
		r.bleedFrom = at;
		r.bleedShown = 0.0F;
		r.emptySince = at;
		for (UUID id : new ArrayList<>(r.fogged)) {
			fog(world, id, 0.0F);
		}
		r.fogged.clear();
		play(world, h, x, 30.0, 6.5, ChainsawSounds.HOTEL_BLEED, 2.5F, 1.0F);
		EternityDevilEntity devil = EternityDevilEntity.spawn(world, h, x, health);
		r.devil = devil.getUuid();
		// Anyone standing where it grows, or behind it, is put in front of it.
		double frontX = x - EternityDevilEntity.WIDTH / 2.0 - 1.1;
		Vec3d front = HotelBuilder.world(h.origin, h.rot, frontX, 29.0, 6.5);
		for (ServerPlayerEntity p : world.getPlayers()) {
			Vec3d lp = HotelBuilder.local(h.origin, h.rot, p.getPos());
			if (lp.x > x - EternityDevilEntity.WIDTH / 2.0 - 0.3 && lp.x < HotelPlan.SX && lp.y > 28.0 && lp.y < 33.0 && lp.z > 4.0 && lp.z < 9.0) {
				p.stopRiding();
				p.networkHandler.requestTeleport(front.x, front.y, front.z, p.getYaw(), p.getPitch());
			}
		}
	}

	private static void fight(ServerWorld world, HomeBlockEntity h, List<In> inside, long now) {
		Run r = h.run;
		float amount = Math.min(1.0F, Math.max(0.0F, (now - r.bleedFrom) / 70.0F));
		if (Math.abs(amount - r.bleedShown) >= 0.04F || amount >= 1.0F && r.bleedShown < 1.0F) {
			r.bleedShown = amount;
			HotelBuilder.bleed(world, h.origin, h.rot, amount, (int) Math.max(1.0, Math.min(24.0, r.fightX - 8.0)), (int) Math.min(HotelPlan.CORR_X1, r.fightX + 6.0));
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
		r.beg = null;
		r.bleedShown = 0.0F;
		r.laps.clear();
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
		if (r.beg != null) {
			for (UUID id : r.beg.players) {
				ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(id);
				if (p != null) {
					offer(p, 0, false);
				}
			}
			r.beg = null;
		}
		r.laps.clear();
		r.dyingAt = now;
		sendAway(world, h);
		Squad.leave(world, h);
		open(world, h);
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
				speak(world, Set.of(p.getUuid()), "message.chainsawman.devil.last", 30, 1);
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
	public static final int DYING_TICKS = 110;

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
