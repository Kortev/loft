package io.github.kortev.chainsawman.form;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.network.FormEventPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * The saws as weapons, in the form. A slash (the attack key) sweeps the saws through whatever is in front, in a combo
 * of three: the right arm across, the left back, then both down from overhead, harder. Holding the attack key grinds:
 * the saws held out revving, chewing into whatever is in front every few ticks. Every hit throws blood and sparks, and
 * drinks: a hit heals a little, a kill a lot ({@link #HIT_HEAL}, {@link #KILL_HEAL}). And the form costs blood: while it
 * is on, health drains away ({@link #DRAIN_EVERY}), and only fighting keeps you up.
 */
public final class Saw {
	/** A slash: how far it reaches (blocks), how wide (the cosine of half its angle), its damage, the finisher's. */
	public static final double SLASH_REACH = 3.5;
	private static final double SLASH_WIDTH = 0.45;
	private static final double FINISHER_WIDTH = 0.7;
	public static final float SLASH_DAMAGE = 10.0F;
	public static final float FINISHER_DAMAGE = 14.0F;
	/** Ticks between slashes at the fastest, and how long the combo waits for the next before starting over. */
	public static final int SLASH_EVERY = 12;
	private static final int COMBO_GAP = 24;
	/** Grinding: how far and wide it reaches, how often it bites (ticks) and for how much. */
	private static final double GRIND_REACH = 2.8;
	private static final double GRIND_WIDTH = 0.6;
	public static final int GRIND_EVERY = 4;
	public static final float GRIND_DAMAGE = 2.5F;
	/** Healing: a slash that hits, every third bite of grinding that hits, a kill (health points; 2 to a heart). */
	public static final float HIT_HEAL = 1.0F;
	private static final int GRIND_HEAL_BITES = 3;
	public static final float KILL_HEAL = 6.0F;
	/**
	 * Pochita held as a chainsaw (before he's your heart): the same moves, a taste of the form, at this much of the
	 * damage, with one arm (the right's sweep twice, then the chop), and no blood drunk.
	 */
	public static final float WIELDED = 0.5F;
	/** The form's cost: a point of health (half a heart) every so many ticks. */
	public static final int DRAIN_EVERY = 60;
	/**
	 * The lunge (attacking while sprinting): headfirst, the head's saw first, this fast (blocks a tick), for this long
	 * (ticks), cutting what it runs through for this much; then this long before the next.
	 */
	private static final double LUNGE_SPEED = 1.5;
	public static final int LUNGE_TICKS = 8;
	public static final float LUNGE_DAMAGE = 12.0F;
	public static final int LUNGE_EVERY = 24;

	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.62F, 0.02F, 0.04F), 1.0F);
	private static final DustParticleEffect DARK_BLOOD = new DustParticleEffect(new Vector3f(0.35F, 0.0F, 0.02F), 1.3F);
	private static final BlockStateParticleEffect GORE = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());

	/** Each fighter's last slash: when, and its step in the combo. */
	private record Slash(long at, int step) {
	}

	private static final Map<UUID, Slash> SLASHED = new ConcurrentHashMap<>();
	/** Who is grinding, and how many bites that hit since the last heal. */
	private static final Map<UUID, Integer> GRINDING = new ConcurrentHashMap<>();
	/** Who the form drains, and since when (server ticks). */
	private static final Map<UUID, Long> DRAINING = new ConcurrentHashMap<>();
	/** Who is lunging: since when, and what the lunge has cut already (each thing once). */
	private record Lunge(long at, Set<UUID> cut) {
	}

	private static final Map<UUID, Lunge> LUNGING = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LUNGED = new ConcurrentHashMap<>();
	/** What a kill throws about: torn flesh and bone. */
	private static final ItemStackParticleEffect FLESH = new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.ROTTEN_FLESH));
	private static final ItemStackParticleEffect BONE = new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.BONE));

	private Saw() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getAttacker() instanceof ServerPlayerEntity killer && killer != entity && Form.fighting(killer)) {
				heal(killer, KILL_HEAL);
				Blood.add(killer, Blood.KILL);
				Limbs.regrow(killer);
				tearApart(killer, entity);
			}
		});
	}

	/** A slash of the saws, if one may come yet: the next step of the combo, through all in front. */
	public static boolean slash(ServerPlayerEntity player) {
		boolean held = !Form.fighting(player) && wielding(player);
		if (!Form.fighting(player) && !held) {
			return false;
		}
		long now = player.getServerWorld().getTime();
		Slash last = SLASHED.get(player.getUuid());
		if (last != null && now - last.at() < SLASH_EVERY) {
			return false;
		}
		int step = last == null || now - last.at() > COMBO_GAP ? 0 : (last.step() + 1) % 3;
		// An arm gone: its sweep comes from the other; with none, the head's saw nods down.
		boolean right = Limbs.has(player, Limbs.RIGHT);
		boolean left = Limbs.has(player, Limbs.LEFT);
		if (!right && !left) {
			step = 2;
		} else if (step == 0 && !right) {
			step = 1;
		} else if (step == 1 && !left) {
			step = 0;
		}
		if (held && step == 1) {
			step = 0;
		}
		SLASHED.put(player.getUuid(), new Slash(now, step));
		boolean finisher = step == 2;
		float damage = (finisher ? FINISHER_DAMAGE : SLASH_DAMAGE) * (held ? WIELDED : Frenzy.damage(player) * Limbs.damage(player));
		List<LivingEntity> hit = inFront(player, SLASH_REACH, finisher ? FINISHER_WIDTH : SLASH_WIDTH);
		for (LivingEntity target : hit) {
			target.timeUntilRegen = 0;
			target.damage(player.getDamageSources().playerAttack(player), damage);
			Vec3d push = target.getPos().subtract(player.getPos()).multiply(1.0, 0.0, 1.0).normalize();
			target.takeKnockback(finisher ? 0.9 : 0.45, -push.x, -push.z);
			bleed(player.getServerWorld(), target, finisher ? 30 : 18);
		}
		Form.play(player, ChainsawSounds.SLASH, 1.0F);
		Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.SWING, step));
		if (held) {
			if (!hit.isEmpty()) {
				Form.play(player, ChainsawSounds.HIT, 0.8F);
				Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.HIT, hit.size()));
			}
		} else {
			landed(player, hit.size());
		}
		return true;
	}

	/** Whether {@code player} holds Pochita in their hand, as a chainsaw. */
	public static boolean wielding(PlayerEntity player) {
		return player.getMainHandStack().isOf(ChainsawMan.POCHITA);
	}

	/** A hit or more landing: the sound of it, the blood drunk, and everyone told (the pause and kick of a heavy hit). */
	private static void landed(ServerPlayerEntity player, int hit) {
		if (hit > 0) {
			Form.play(player, ChainsawSounds.HIT, 1.0F);
			heal(player, HIT_HEAL * Frenzy.heal(player));
			Blood.add(player, Blood.HIT);
			Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.HIT, hit));
		}
	}

	/** The lunge: off headfirst the way you look, cutting through what you run into. */
	public static boolean lunge(ServerPlayerEntity player) {
		if (!Form.fighting(player)) {
			return false;
		}
		long now = player.getServerWorld().getTime();
		Long last = LUNGED.get(player.getUuid());
		if (last != null && now - last < LUNGE_EVERY || !Blood.spend(player, Blood.LUNGE)) {
			return false;
		}
		LUNGED.put(player.getUuid(), now);
		LUNGING.put(player.getUuid(), new Lunge(now, ConcurrentHashMap.newKeySet()));
		Vec3d look = player.getRotationVec(1.0F).multiply(1.0, 0.25, 1.0).normalize();
		player.setVelocity(look.multiply(LUNGE_SPEED).add(0.0, 0.15, 0.0));
		player.velocityModified = true;
		Form.play(player, ChainsawSounds.SLASH, 1.2F);
		Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.SWING, 3));
		return true;
	}

	/** A kill by the saws: torn apart, in a burst of blood, flesh and bone, the body gone with it. */
	private static void tearApart(ServerPlayerEntity killer, LivingEntity dead) {
		ServerWorld world = killer.getServerWorld();
		Vec3d at = dead.getBoundingBox().getCenter();
		double w = dead.getWidth() * 0.5;
		double h = dead.getHeight() * 0.4;
		world.spawnParticles(GORE, at.x, at.y, at.z, 40, w, h, w, 0.35);
		world.spawnParticles(BLOOD, at.x, at.y, at.z, 50, w, h, w, 0.3);
		world.spawnParticles(DARK_BLOOD, at.x, at.y, at.z, 25, w, h, w, 0.2);
		world.spawnParticles(FLESH, at.x, at.y, at.z, 14, w, h, w, 0.25);
		world.spawnParticles(BONE, at.x, at.y, at.z, 6, w, h, w, 0.25);
		Form.broadcast(killer.server, new FormEventPayload(killer.getUuid(), FormEventPayload.KILL, dead.getId()));
		if (!(dead instanceof PlayerEntity)) {
			// Nothing left to fall over.
			dead.discard();
		}
	}

	/** The saws held grinding, or let go. */
	public static void grind(ServerPlayerEntity player, boolean on) {
		boolean was = GRINDING.containsKey(player.getUuid());
		if (on && (Form.fighting(player) || wielding(player) && !Form.on(player))) {
			GRINDING.putIfAbsent(player.getUuid(), 0);
		} else {
			GRINDING.remove(player.getUuid());
		}
		if (was != GRINDING.containsKey(player.getUuid())) {
			Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.GRIND, was ? 0 : 1));
		}
	}

	public static boolean grinding(PlayerEntity player) {
		return GRINDING.containsKey(player.getUuid());
	}

	/** Heals {@code player} by {@code amount}: the blood they drink. */
	public static void heal(ServerPlayerEntity player, float amount) {
		player.heal(amount);
	}

	static void tick(MinecraftServer server) {
		long now = server.getOverworld().getTime();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			UUID id = player.getUuid();
			if (!Form.fighting(player)) {
				DRAINING.remove(id);
				if (GRINDING.containsKey(id) && (Form.on(player) || !wielding(player))) {
					grind(player, false);
				} else if (GRINDING.containsKey(id) && now % GRIND_EVERY == 0) {
					// Pochita held, grinding: half the bite, no blood drunk.
					for (LivingEntity target : inFront(player, GRIND_REACH, GRIND_WIDTH)) {
						target.timeUntilRegen = 0;
						target.damage(player.getDamageSources().playerAttack(player), GRIND_DAMAGE * WIELDED);
						bleed(player.getServerWorld(), target, 5);
					}
				}
				continue;
			}
			// The cost: a point of health every DRAIN_EVERY ticks from when the form came on; out of blood, down.
			long since = DRAINING.computeIfAbsent(id, k -> now);
			if (now > since && (now - since) % DRAIN_EVERY == 0) {
				if (player.getHealth() <= 1.0F) {
					Downed.down(player);
					continue;
				}
				player.setHealth(player.getHealth() - 1.0F);
			}
			Lunge lunge = LUNGING.get(id);
			if (lunge != null) {
				if (now - lunge.at() > LUNGE_TICKS) {
					LUNGING.remove(id);
				} else {
					int cut = 0;
					for (LivingEntity target : inFront(player, 2.2, 0.35)) {
						if (lunge.cut().add(target.getUuid())) {
							target.timeUntilRegen = 0;
							target.damage(player.getDamageSources().playerAttack(player), LUNGE_DAMAGE * Frenzy.damage(player));
							Vec3d push = player.getRotationVec(1.0F);
							target.takeKnockback(1.2, -push.x, -push.z);
							bleed(player.getServerWorld(), target, 30);
							cut++;
						}
					}
					landed(player, cut);
				}
			}
			Integer bites = GRINDING.get(id);
			if (bites != null && now % GRIND_EVERY == 0) {
				List<LivingEntity> hit = inFront(player, GRIND_REACH, GRIND_WIDTH);
				for (LivingEntity target : hit) {
					target.timeUntilRegen = 0;
					target.damage(player.getDamageSources().playerAttack(player), GRIND_DAMAGE * Frenzy.damage(player) * Limbs.damage(player));
					target.setVelocity(target.getVelocity().multiply(0.4, 1.0, 0.4));
					target.velocityModified = true;
					bleed(player.getServerWorld(), target, 8);
				}
				if (!hit.isEmpty()) {
					Blood.add(player, Blood.BITE);
					bites++;
					if (bites >= GRIND_HEAL_BITES) {
						heal(player, HIT_HEAL * Frenzy.heal(player));
						bites = 0;
					}
					GRINDING.put(id, bites);
				}
			}
		}
	}

	/**
	 * The living things in front of {@code player}, within {@code reach} of their eyes and inside the cone whose half
	 * angle's cosine is {@code width}, that they can see: not themselves, nobody downed, no spectator, no armour stand
	 * (there is no blood in one to drink).
	 */
	public static List<LivingEntity> inFront(ServerPlayerEntity player, double reach, double width) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0F);
		List<LivingEntity> out = new ArrayList<>();
		Box box = player.getBoundingBox().expand(reach + 1.0);
		for (Entity e : player.getWorld().getOtherEntities(player, box,
				e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && !(e instanceof ArmorStandEntity))) {
			LivingEntity living = (LivingEntity) e;
			if (living instanceof ServerPlayerEntity other && Downed.is(other)) {
				continue;
			}
			// To the nearest point of it, so a big thing in front is hit wherever its middle is.
			Box b = living.getBoundingBox();
			Vec3d near = new Vec3d(MathHelper.clamp(eye.x, b.minX, b.maxX), MathHelper.clamp(eye.y, b.minY, b.maxY),
					MathHelper.clamp(eye.z, b.minZ, b.maxZ));
			Vec3d to = near.subtract(eye);
			double distance = to.length();
			if (distance > reach) {
				continue;
			}
			Vec3d middle = b.getCenter().subtract(eye);
			boolean ahead = distance < 0.6 || middle.normalize().dotProduct(look) >= width || to.normalize().dotProduct(look) >= width;
			if (ahead && player.canSee(living)) {
				out.add(living);
			}
		}
		return out;
	}

	/** Blood thrown off {@code target}, and sparks where the saw bites. */
	static void bleed(ServerWorld world, LivingEntity target, int count) {
		Vec3d at = target.getBoundingBox().getCenter();
		double w = target.getWidth() * 0.4;
		double h = target.getHeight() * 0.3;
		world.spawnParticles(BLOOD, at.x, at.y, at.z, count * 2 / 3, w, h, w, 0.2);
		world.spawnParticles(DARK_BLOOD, at.x, at.y, at.z, count / 3, w, h, w, 0.15);
		world.spawnParticles(GORE, at.x, at.y, at.z, count / 4, w, h, w, 0.25);
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, count / 3, w, h, w, 0.4);
	}

	/** Particles round {@code player} for everyone but them: in their own eyes they would fill the view. */
	static void around(ServerPlayerEntity player, net.minecraft.particle.ParticleEffect effect, double up, int count, double spread, double speed) {
		for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
			if (other != player) {
				player.getServerWorld().spawnParticles(other, effect, false, player.getX(), player.getY() + up, player.getZ(), count, spread, spread * 0.5,
						spread, speed);
			}
		}
	}

	/** Who is grinding now (for the tests). */
	public static Set<UUID> grinders() {
		return Set.copyOf(GRINDING.keySet());
	}

	static void forget(UUID id) {
		LUNGING.remove(id);
		LUNGED.remove(id);
		SLASHED.remove(id);
		GRINDING.remove(id);
		DRAINING.remove(id);
	}

	static void clear() {
		LUNGING.clear();
		LUNGED.clear();
		SLASHED.clear();
		GRINDING.clear();
		DRAINING.clear();
	}
}
