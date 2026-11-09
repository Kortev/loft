package io.github.kortev.chainsawman.form;

import com.mojang.serialization.Codec;
import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.network.FormEventPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

/**
 * The heart levels up. Feeding on devils' blood makes Pochita's heart stronger, in 3 levels, and the form shows it:
 * <ul>
 * <li><b>Heart XP</b> comes from killing devil-type things ({@link #DEVILS}, an entity type tag so every future devil counts: {@link #KILL_XP};
 * its flesh parts, {@link #PARTS}: {@link #PART_XP}) and from drinking Eternity's blood ({@link #BLOOD_XP}).</li>
 * <li><b>Level 2</b> ({@link #LEVEL_2} XP): damage {@link #DAMAGE_PER_LEVEL} more, a deeper engine, more teeth on the saws.</li>
 * <li><b>Level 3</b> ({@link #LEVEL_3} XP): damage that much more again, the chain pulls twice as fast ({@link #chainSpeed}), Beam bites
 * {@link #BEAM_BITE} times harder, and blood sprays further ({@link #spray}).</li>
 * </ul>
 * Each level-up is a moment: a roar, a burst of blood and a line on screen. The XP belongs to the heart: it goes with it when it is stolen
 * and is gone when it is given up.
 */
public final class HeartLevel {
	public static final int LEVEL_2 = 30;
	public static final int LEVEL_3 = 80;
	/** XP for killing something of the devils tag, of the parts tag, and for drinking a bottle of Eternity blood. */
	public static final int KILL_XP = 40;
	public static final int PART_XP = 6;
	public static final int BLOOD_XP = 10;
	/** The damage each level adds to a slash, a finisher and a lunge. */
	public static final float DAMAGE_PER_LEVEL = 2.0F;
	/** At level 3: how much harder Beam bites (a multiple of his damage), and how many times faster the chain pulls. */
	public static final float BEAM_BITE = 1.5F;
	public static final double CHAIN_PULL = 2.0;

	public static final TagKey<EntityType<?>> DEVILS = TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(ChainsawMan.MOD_ID, "devils"));
	public static final TagKey<EntityType<?>> PARTS = TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(ChainsawMan.MOD_ID, "devil_parts"));

	public static final AttachmentType<Integer> XP = AttachmentRegistry.<Integer>builder().persistent(Codec.INT).copyOnDeath()
			.buildAndRegister(ChainsawMan.id("heart_xp"));

	private HeartLevel() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getAttacker() instanceof ServerPlayerEntity killer && killer != entity && Heart.has(killer)) {
				int xp = xpFor(entity);
				if (xp > 0) {
					add(killer, xp);
				}
			}
		});
	}

	/** What killing {@code entity} is worth to a heart. */
	public static int xpFor(Entity entity) {
		if (entity.getType().isIn(DEVILS)) {
			return KILL_XP;
		}
		return entity.getType().isIn(PARTS) ? PART_XP : 0;
	}

	public static int xp(PlayerEntity player) {
		return player.getAttachedOrElse(XP, 0);
	}

	/** 1 to 3 for a hybrid, 0 for anyone else. */
	public static int level(PlayerEntity player) {
		return Heart.has(player) ? levelOf(xp(player)) : 0;
	}

	public static int levelOf(int xp) {
		return xp >= LEVEL_3 ? 3 : xp >= LEVEL_2 ? 2 : 1;
	}

	/** Damage a hybrid's heart adds to a slash, finisher or lunge: 0, 2 or 4. */
	public static float damage(PlayerEntity player) {
		return Math.max(0, level(player) - 1) * DAMAGE_PER_LEVEL;
	}

	/** How much faster the chain pulls (1, or {@link #CHAIN_PULL} at level 3). */
	public static double chainSpeed(PlayerEntity player) {
		return level(player) >= 3 ? CHAIN_PULL : 1.0;
	}

	/** How much harder Beam bites for his rider (1, or {@link #BEAM_BITE} at level 3). */
	public static float beamBite(PlayerEntity player) {
		return level(player) >= 3 ? BEAM_BITE : 1.0F;
	}

	/** How far blood sprays (a multiple: 1, 1.6 at level 3). */
	public static float spray(PlayerEntity player) {
		return level(player) >= 3 ? 1.6F : 1.0F;
	}

	/** Heart XP outright (a stolen heart's, a command's, the tests'); told to everyone, with no moment. */
	public static void set(ServerPlayerEntity player, int xp) {
		if (xp <= 0) {
			player.removeAttached(XP);
		} else {
			player.setAttached(XP, xp);
		}
		told(player);
	}

	/** {@code xp} more for {@code player}'s heart: a level-up is a moment. */
	public static void add(ServerPlayerEntity player, int xp) {
		int before = levelOf(xp(player));
		player.setAttached(XP, xp(player) + xp);
		int now = levelOf(xp(player));
		if (now > before) {
			levelUp(player, now);
		} else {
			tellOwner(player);
		}
	}

	/** Everyone told the heart's level (the saws' teeth, the engine) and the owner the XP. */
	public static void told(ServerPlayerEntity player) {
		Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.HEART_LEVEL, level(player)));
		tellOwner(player);
	}

	private static void tellOwner(ServerPlayerEntity player) {
		if (Heart.has(player) && ServerPlayNetworking.canSend(player, FormEventPayload.ID)) {
			ServerPlayNetworking.send(player, new FormEventPayload(player.getUuid(), FormEventPayload.HEART_XP, xp(player)));
		}
	}

	/** The moment: a roar, a burst of blood, and a line on every screen that can see it (the owner's most of all). */
	private static void levelUp(ServerPlayerEntity player, int level) {
		told(player);
		var world = player.getServerWorld();
		world.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), ChainsawSounds.HEART_LEVEL, SoundCategory.PLAYERS, 2.4F, level >= 3 ? 0.85F : 1.0F);
		world.spawnParticles(new DustParticleEffect(new Vector3f(0.7F, 0.02F, 0.05F), 1.8F), player.getX(), player.getY() + 1.2, player.getZ(), 90 + 40 * level, 0.9, 0.9, 0.9, 0.25);
		world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT, player.getX(), player.getY() + 1.2, player.getZ(), 40, 0.8, 0.8, 0.8, 0.7);
		Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.HEART_LEVEL_UP, level, player.getX(), player.getY(), player.getZ()));
	}

	public static void clear() {
	}
}
