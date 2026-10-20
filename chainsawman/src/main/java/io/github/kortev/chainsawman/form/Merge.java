package io.github.kortev.chainsawman.form;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.PochitaEntity;
import io.github.kortev.chainsawman.PochitaItem;
import io.github.kortev.chainsawman.network.FormEventPayload;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The first time kortev dies with Pochita (carried, or tamed and within {@link #NEAR} blocks), he becomes their heart:
 * not a death but the cinematic of it ({@link MergeTimeline}), held still and untouchable through it. At its end the
 * heart is theirs for good ({@link Heart#MERGED}), and they stand there, the cord hanging out of their chest, until they
 * pull it themselves (as Denji does), the transformation's own cinematic following; or, left there {@link #WAIT} ticks,
 * it pulls itself.
 */
public final class Merge {
	/** How near a tamed Pochita must be to come to you (blocks). */
	public static final double NEAR = 32.0;
	/** How long you stand waiting to pull the cord before it pulls itself (ticks). */
	public static final int WAIT = 60 * 20;

	private static final Map<UUID, Integer> MERGING = new ConcurrentHashMap<>();
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.5F, 0.0F, 0.03F), 1.4F);

	/** Who is merging as far as each client has been told; the server's own on the server. */
	public static Predicate<PlayerEntity> client = p -> false;

	private Merge() {
	}

	public static boolean is(PlayerEntity player) {
		return player.getWorld().isClient ? client.test(player) : MERGING.containsKey(player.getUuid());
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (MERGING.remove(handler.player.getUuid()) != null) {
				// Gone mid-way: the heart is theirs all the same.
				handler.player.setAttached(Heart.MERGED, true);
			}
		});
	}

	/** Whether {@code player}, dying now, becomes Chainsaw Man: kortev, not yet merged, Pochita carried or near. */
	public static boolean comes(ServerPlayerEntity player) {
		return !Heart.merged(player) && player.getGameProfile().getName().equalsIgnoreCase(ChainsawMan.OWNER)
				&& (PochitaItem.carried(player) || near(player) != null);
	}

	/** {@code player}'s own tamed Pochita near them, if there is one. */
	@Nullable
	private static PochitaEntity near(ServerPlayerEntity player) {
		List<PochitaEntity> near = player.getServerWorld().getEntitiesByClass(PochitaEntity.class, player.getBoundingBox().expand(NEAR),
				p -> p.isTamed() && p.isOwner(player) && p.isAlive());
		return near.isEmpty() ? null : near.get(0);
	}

	/** Death, but not: Pochita comes to {@code player}'s body and the cinematic plays. */
	public static void start(ServerPlayerEntity player) {
		if (MERGING.containsKey(player.getUuid())) {
			return;
		}
		Vec3d body = player.getPos();
		Vec3d from;
		PochitaEntity pochita = PochitaItem.carried(player) ? null : near(player);
		if (pochita != null) {
			// He comes from where he was, or from FROM blocks off along the way if further.
			Vec3d to = pochita.getPos().subtract(body);
			from = to.length() > MergeTimeline.FROM ? body.add(to.normalize().multiply(MergeTimeline.FROM)) : pochita.getPos();
			pochita.discard();
		} else {
			// Out of your pack, from beside you.
			for (int i = 0; i < player.getInventory().size(); i++) {
				if (player.getInventory().getStack(i).isOf(ChainsawMan.POCHITA)) {
					player.getInventory().removeStack(i, 1);
					break;
				}
			}
			from = body.add(Vec3d.fromPolar(0.0F, player.getBodyYaw() + 90.0F).multiply(2.0));
		}
		// However he came, there's only the one Pochita, and now he's going into you.
		for (PochitaEntity other : player.getServerWorld().getEntitiesByClass(PochitaEntity.class, player.getBoundingBox().expand(NEAR * 2),
				p -> p.isTamed() && p.isOwner(player))) {
			other.discard();
		}
		player.setHealth(1.0F);
		player.extinguish();
		player.clearStatusEffects();
		Form.end(player);
		Downed.hold(player, true);
		MERGING.put(player.getUuid(), 0);
		Form.play(player, ChainsawSounds.MERGE, 1.6F);
		player.getServerWorld().spawnParticles(BLOOD, body.x, body.y + 0.2, body.z, 40, 0.6, 0.05, 0.6, 0.02);
		Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.MERGE, 1, from.x, from.y, from.z));
	}

	static void tick(MinecraftServer server) {
		for (Map.Entry<UUID, Integer> e : Map.copyOf(MERGING).entrySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
			if (player == null) {
				MERGING.remove(e.getKey());
				continue;
			}
			int age = e.getValue() + 1;
			player.setHealth(Math.max(player.getHealth(), 1.0F));
			player.fallDistance = 0.0F;
			if (age == MergeTimeline.SINK) {
				player.getServerWorld().spawnParticles(BLOOD, player.getX(), player.getY() + 0.4, player.getZ(), 50, 0.3, 0.2, 0.3, 0.25);
			}
			if (age == MergeTimeline.END) {
				finish(player);
			}
			if (age >= MergeTimeline.END + WAIT) {
				Form.pullCord(player);
			} else {
				MERGING.put(e.getKey(), age);
			}
		}
	}

	/** The heart theirs for good, whole and full of blood; waiting now for them to pull the cord. */
	private static void finish(ServerPlayerEntity player) {
		player.setAttached(Heart.MERGED, true);
		Heart.told(player);
		player.setHealth(player.getMaxHealth());
		Blood.set(player, Blood.MAX);
		Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.MERGE, 2));
	}

	/** Whether {@code player} is on their feet at the end of it, waiting to pull the cord. */
	public static boolean waiting(ServerPlayerEntity player) {
		Integer age = MERGING.get(player.getUuid());
		return age != null && age >= MergeTimeline.END;
	}

	/** The cord pulled at the end of it: the merge over, the transformation's own cinematic from here. */
	static void pulled(ServerPlayerEntity player) {
		MERGING.remove(player.getUuid());
		Downed.hold(player, false);
		Form.broadcast(player.server, new FormEventPayload(player.getUuid(), FormEventPayload.MERGE, 0));
		player.sendMessage(Text.translatable("message.chainsawman.merged").formatted(Formatting.GOLD), false);
	}

	static void clear() {
		MERGING.clear();
	}
}
