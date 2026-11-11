package io.github.kortev.chainsawman.devil;

import io.github.kortev.chainsawman.ChainsawMan;
import java.util.function.IntConsumer;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * A devil's growth, 0 to 5: a devil left alive gets worse. In the show a devil is as strong as its name is feared; here time counts only
 * as fear spreading, so a devil that stays near people is talked about and feared more each night. Saved with the devil (NBT
 * {@code Growth}, which is also the way to set it for a test or a film: {@code /summon chainsawman:tomato_devil ~ ~ ~ {Growth:5}}) and
 * told to its owner, which sends it to clients.
 *
 * <p>A step comes when a night ends and the devil lived through it near people ({@link #NEAR} blocks of a player or a villager at some point
 * that night; one a night), when a player died to it (once a night), and when it came back from its seeds. Never more than
 * {@link #MOST_A_NIGHT} in one night, never above {@link #MAX}, never while it isn't loaded (it only counts while it ticks), never lost.
 *
 * <p>Each step: health +{@link #HEALTH_STEP} of its base (healed by what it gained), damage +{@link #DAMAGE_STEP}, size +{@link #SIZE_STEP}
 * (model and hitbox), its voice {@link #VOICE_STEP} lower; one more carcass at steps 2 and 4; and from step 1 it never despawns. Its tells
 * and timings don't change. One shared class so that any devil can grow: the Tomato Devil is the first.
 */
public final class Growth {
	public static final int MAX = 5;
	/** How near a player or a villager must come in a night for that night to count (blocks). */
	public static final double NEAR = 64.0;
	/** The most steps one night can bring, whatever happens in it. */
	public static final int MOST_A_NIGHT = 2;
	public static final double HEALTH_STEP = 0.10;
	public static final float DAMAGE_STEP = 0.06F;
	public static final double SIZE_STEP = 0.04;
	/** How much lower its sounds are pitched a step (a fraction of the pitch). */
	public static final float VOICE_STEP = 0.05F;
	/** A step gained with a player this near shows: a shudder and a low swell of sound (blocks). */
	public static final double FELT = 32.0;
	/** How long the shudder lasts (ticks). */
	public static final int SHUDDER = 36;
	/** The steps that bring one more carcass each. */
	public static final int[] MORE_CARCASS_AT = {2, 4};

	static final Identifier HEALTH = ChainsawMan.id("growth_health");
	static final Identifier SIZE = ChainsawMan.id("growth_size");
	static final Identifier DAMAGE = ChainsawMan.id("growth_damage");

	private final MobEntity owner;
	private final IntConsumer told;
	private int level;
	/** The night the counts below are for ({@link #nightOf}), and what happened in it. */
	private long night = Long.MIN_VALUE;
	private int steps;
	private boolean near;
	private boolean killed;
	private boolean ended;

	/** {@code told} hears the new level each time it changes (the owner sends it to clients; the shudder). */
	public Growth(MobEntity owner, IntConsumer told) {
		this.owner = owner;
		this.told = told;
	}

	public int level() {
		return level;
	}

	/** The steps gained in the night now counting. */
	public int stepsTonight() {
		return steps;
	}

	// ---- The night (dusk 13000 to 23000 by the clock), and which night a time belongs to (each night with the day after it).

	public static boolean dark(long timeOfDay) {
		long t = Math.floorMod(timeOfDay, 24000L);
		return t >= 13000L && t <= 23000L;
	}

	public static long nightOf(long timeOfDay) {
		return Math.floorDiv(timeOfDay + 11000L, 24000L);
	}

	private void turnTo(long timeOfDay) {
		long n = nightOf(timeOfDay);
		if (n != night) {
			night = n;
			steps = 0;
			near = false;
			killed = false;
			ended = false;
		}
	}

	// ---- The rule.

	/** One tick of its life: anyone near in the dark is remembered; when the dark ends after a night with people near, a step. */
	public boolean tick(long timeOfDay, boolean peopleNear) {
		turnTo(timeOfDay);
		if (dark(timeOfDay)) {
			near |= peopleNear;
			return false;
		}
		if (near && !ended) {
			ended = true;
			return step();
		}
		return false;
	}

	/** The owner's tick on the server: is anyone near (looked at once a second, and only in the dark)? */
	public boolean tick() {
		World world = owner.getWorld();
		long time = world.getTimeOfDay();
		boolean look = dark(time) && !near && owner.age % 20 == 0;
		return tick(time, look && peopleNear(owner));
	}

	/** A player or a villager within {@link #NEAR} blocks. */
	public static boolean peopleNear(MobEntity e) {
		World w = e.getWorld();
		if (w.getClosestPlayer(e.getX(), e.getY(), e.getZ(), NEAR, p -> !p.isSpectator() && p.isAlive()) != null) {
			return true;
		}
		return !w.getEntitiesByClass(VillagerEntity.class, e.getBoundingBox().expand(NEAR), v -> v.isAlive() && v.squaredDistanceTo(e) <= NEAR * NEAR).isEmpty();
	}

	/** A player died to it: a step, once a night. */
	public boolean killedPlayer(long timeOfDay) {
		turnTo(timeOfDay);
		if (killed) {
			return false;
		}
		killed = true;
		return step();
	}

	/** It came back out of its seeds: a step. */
	public boolean cameBack(long timeOfDay) {
		turnTo(timeOfDay);
		return step();
	}

	private boolean step() {
		if (level >= MAX || steps >= MOST_A_NIGHT) {
			return false;
		}
		steps++;
		set(level + 1, true);
		return true;
	}

	/** Sets the level (a load, a test, a film), healing by the health gained if {@code heal}; it never despawns once grown. */
	public void set(int to, boolean heal) {
		to = Math.max(0, Math.min(MAX, to));
		float before = owner.getMaxHealth();
		level = to;
		modify(EntityAttributes.GENERIC_MAX_HEALTH, HEALTH, HEALTH_STEP * to);
		modify(EntityAttributes.GENERIC_SCALE, SIZE, SIZE_STEP * to);
		modify(EntityAttributes.GENERIC_ATTACK_DAMAGE, DAMAGE, DAMAGE_STEP * to);
		float gained = owner.getMaxHealth() - before;
		if (heal && gained > 0.0F) {
			owner.setHealth(Math.min(owner.getMaxHealth(), owner.getHealth() + gained));
		} else if (owner.getHealth() > owner.getMaxHealth()) {
			owner.setHealth(owner.getMaxHealth());
		}
		if (to >= 1) {
			owner.setPersistent();
		}
		owner.calculateDimensions();
		told.accept(to);
	}

	private void modify(RegistryEntry<EntityAttribute> attribute, Identifier id, double amount) {
		EntityAttributeInstance a = owner.getAttributeInstance(attribute);
		if (a == null) {
			return;
		}
		if (amount == 0.0) {
			a.removeModifier(id);
		} else {
			a.overwritePersistentModifier(new EntityAttributeModifier(id, amount, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	// ---- What a step is worth.

	/** What its blows are multiplied by. */
	public float damage() {
		return damage(level);
	}

	public static float damage(int level) {
		return 1.0F + DAMAGE_STEP * level;
	}

	/** What its sounds' pitch is multiplied by. */
	public float voice() {
		return voice(level);
	}

	public static float voice(int level) {
		return 1.0F - VOICE_STEP * level;
	}

	/** How many carcasses it leaves: one, and one more at each of {@link #MORE_CARCASS_AT}. */
	public static int carcasses(int level) {
		int n = 1;
		for (int at : MORE_CARCASS_AT) {
			if (level >= at) {
				n++;
			}
		}
		return n;
	}

	// ---- Saving.

	public void write(NbtCompound nbt) {
		nbt.putInt("Growth", level);
		NbtCompound n = new NbtCompound();
		n.putLong("Night", night);
		n.putInt("Steps", steps);
		n.putBoolean("Near", near);
		n.putBoolean("Killed", killed);
		n.putBoolean("Ended", ended);
		nbt.put("GrowthNight", n);
	}

	/** Reads it back; true if the level rose (set by a command on a devil already about: it shows). */
	public boolean read(NbtCompound nbt) {
		if (nbt.contains("GrowthNight")) {
			NbtCompound n = nbt.getCompound("GrowthNight");
			night = n.getLong("Night");
			steps = n.getInt("Steps");
			near = n.getBoolean("Near");
			killed = n.getBoolean("Killed");
			ended = n.getBoolean("Ended");
		}
		if (!nbt.contains("Growth")) {
			return false;
		}
		int was = level;
		set(nbt.getInt("Growth"), true);
		return level > was;
	}

	/** The whole of it as NBT (carried by the seeds to the devil that comes back). */
	public NbtCompound save() {
		NbtCompound n = new NbtCompound();
		write(n);
		return n;
	}
}
