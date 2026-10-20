package io.github.kortev.chainsawman.form;

import io.github.kortev.chainsawman.ChainsawSounds;
import io.github.kortev.chainsawman.network.FormEventPayload;
import io.github.kortev.chainsawman.network.FormPayload;
import io.github.kortev.chainsawman.network.FormPullPayload;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

/**
 * Who is Chainsaw Man right now. Pulling the cord with Pochita carried yanks it out of the chest, the engine catches,
 * and at the burst ({@link FormTimeline#BURST}) the saws tear out of head and arms: the form is on. (Every client plays
 * the transformation out from the pull: {@code FormCinematic}.)
 * Pulling it again lets the engine die and turns them back; so does losing Pochita or dying, and leaving. Everyone is
 * told, so everyone sees and hears it.
 */
public final class Form {
	private static final Set<UUID> ON = ConcurrentHashMap.newKeySet();
	/** Who has pulled the cord and is on the way into the form, and how many ticks ago. */
	private static final Map<UUID, Integer> PULLING = new ConcurrentHashMap<>();
	/** How tough the form makes you: armour and toughness while it's on. */
	public static final double ARMOR = 10.0;
	public static final double TOUGHNESS = 6.0;
	private static final net.minecraft.util.Identifier TOUGH = io.github.kortev.chainsawman.ChainsawMan.id("form_tough");
	/** Who is turning back, and how many ticks in. */
	private static final Map<UUID, Integer> TURNING = new ConcurrentHashMap<>();

	private Form() {
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			for (UUID id : ON) {
				if (ServerPlayNetworking.canSend(handler.player, FormPayload.ID)) {
					ServerPlayNetworking.send(handler.player, new FormPayload(id, true));
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			PULLING.remove(handler.player.getUuid());
			TURNING.remove(handler.player.getUuid());
			set(server, handler.player.getUuid(), false);
			Saw.forget(handler.player.getUuid());
			Grapple.forget(handler.player.getUuid());
			Frenzy.forget(handler.player.getUuid());
			Limbs.forget(handler.player.getUuid());
			Blood.forget(handler.player.getUuid());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ON.clear();
			PULLING.clear();
			TURNING.clear();
			Saw.clear();
			Downed.clear();
			Grapple.clear();
			Frenzy.clear();
			Limbs.clear();
			Blood.clear();
			Merge.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Form::tick);
		// What the form does once it is on: the saws, the blood, never dying, the chain, the frenzy.
		Saw.init();
		Downed.init();
		Limbs.init();
		Merge.init();
		Heart.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Blood.tick(server);
			Merge.tick(server);
			Saw.tick(server);
			Downed.tick(server);
			Grapple.tick(server);
			Frenzy.tick(server);
		});
	}

	/** Whether {@code player} is in the form. */
	/** Who is in the form as far as each client has been told. */
	public static java.util.function.Predicate<net.minecraft.entity.player.PlayerEntity> client = p -> false;

	/** Whether {@code player} is in the form: on either side. */
	public static boolean in(net.minecraft.entity.player.PlayerEntity player) {
		return player.getWorld().isClient ? client.test(player) : ON.contains(player.getUuid());
	}

	public static boolean on(ServerPlayerEntity player) {
		return ON.contains(player.getUuid());
	}

	/** Whether {@code player} is in the form and done transforming: free to fight. */
	/** Whether {@code player} has pulled the cord and is transforming (the form not on yet). */
	public static boolean pulling(ServerPlayerEntity player) {
		return PULLING.containsKey(player.getUuid());
	}

	public static boolean fighting(ServerPlayerEntity player) {
		return on(player) && !PULLING.containsKey(player.getUuid()) && !TURNING.containsKey(player.getUuid()) && !Downed.is(player);
	}

	/** Whether {@code player} is turning back (the form still on until the splash). */
	public static boolean turning(ServerPlayerEntity player) {
		return TURNING.containsKey(player.getUuid());
	}

	/** Straight into the form, no transformation (for the tests). */
	public static void start(ServerPlayerEntity player) {
		PULLING.remove(player.getUuid());
		set(player.server, player.getUuid(), true);
	}

	/** Out of the form now, the engine dying: downed, or for a test. */
	public static void end(ServerPlayerEntity player) {
		PULLING.remove(player.getUuid());
		TURNING.remove(player.getUuid());
		if (on(player)) {
			play(player, ChainsawSounds.STOP, 1.0F);
			set(player.server, player.getUuid(), false);
		}
	}

	/**
	 * The cord pulled: into the form if they have Pochita's heart ({@link Heart}) and the blood for it ({@link
	 * Blood#TRANSFORM}), or back out of it.
	 */
	public static void pullCord(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		if (PULLING.containsKey(id) || TURNING.containsKey(id) || Merge.is(player) && !Merge.waiting(player)) {
			return;
		}
		// The first pull, at the end of Pochita becoming your heart, is yours to make, and free.
		boolean first = Merge.waiting(player);
		if (first) {
			Merge.pulled(player);
		}
		if (on(player)) {
			// Turning back: the engine dies, the saws go in, and at the splash the form is off.
			TURNING.put(id, 0);
			Frenzy.end(player);
			play(player, ChainsawSounds.STOP, 1.0F);
			broadcast(player.server, new FormEventPayload(id, FormEventPayload.TURN, 1));
		} else if (Heart.has(player) && player.isAlive() && !Downed.is(player) && (first || Blood.spend(player, Blood.TRANSFORM))) {
			PULLING.put(id, 0);
			play(player, ChainsawSounds.CORD, 1.0F);
			play(player, ChainsawSounds.START, 1.4F);
			broadcast(player.server, new FormPullPayload(id));
		}
	}

	private static void tick(MinecraftServer server) {
		for (Map.Entry<UUID, Integer> pull : Map.copyOf(PULLING).entrySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(pull.getKey());
			int age = pull.getValue() + 1;
			if (player == null || !player.isAlive()) {
				PULLING.remove(pull.getKey());
			} else if (age >= FormTimeline.BURST) {
				// The form is on from the burst: the clients show the saws growing out and the rest of it.
				PULLING.remove(pull.getKey());
				set(server, pull.getKey(), true);
			} else {
				PULLING.put(pull.getKey(), age);
			}
		}
		for (Map.Entry<UUID, Integer> turn : Map.copyOf(TURNING).entrySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(turn.getKey());
			int age = turn.getValue() + 1;
			if (player == null || !player.isAlive()) {
				TURNING.remove(turn.getKey());
			} else if (age >= FormTimeline.BACK_SPLASH) {
				TURNING.remove(turn.getKey());
				play(player, ChainsawSounds.HIT, 0.7F);
				set(server, turn.getKey(), false);
				Blood.turnedBack(player);
			} else {
				TURNING.put(turn.getKey(), age);
			}
		}
		// Out of the form whoever has lost Pochita or died.
		if (ON.isEmpty() || server.getTicks() % 10 != 0) {
			return;
		}
		for (UUID id : Set.copyOf(ON)) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player == null || !player.isAlive() || !Heart.has(player)) {
				if (player != null) {
					play(player, ChainsawSounds.STOP, 1.0F);
				}
				set(server, id, false);
			}
		}
	}

	public static void play(ServerPlayerEntity player, SoundEvent sound, float volume) {
		player.getServerWorld().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), sound, SoundCategory.PLAYERS, volume, 1.0F);
	}

	private static void set(MinecraftServer server, UUID id, boolean on) {
		if (on ? !ON.add(id) : !ON.remove(id)) {
			return;
		}
		ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
		if (player != null && !on) {
			// Out of the form, your arms are your own again.
			Limbs.whole(player);
		}
		if (player != null) {
			tough(player, on);
		}
		broadcast(server, new FormPayload(id, on));
	}

	/**
	 * In the form you're tough, as Chainsaw Man shrugs off what would kill a man: {@link #ARMOR} armour and {@link
	 * #TOUGHNESS} toughness on you (about a third off a mob's hit), gone when you change back.
	 */
	private static void tough(ServerPlayerEntity player, boolean on) {
		for (net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute : java.util.List.of(
				net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR, net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR_TOUGHNESS)) {
			net.minecraft.entity.attribute.EntityAttributeInstance instance = player.getAttributeInstance(attribute);
			if (instance == null) {
				continue;
			}
			instance.removeModifier(TOUGH);
			if (on) {
				double amount = attribute == net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR ? ARMOR : TOUGHNESS;
				instance.addTemporaryModifier(new net.minecraft.entity.attribute.EntityAttributeModifier(TOUGH, amount,
						net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADD_VALUE));
			}
		}
	}

	/** Tells everyone. */
	public static void broadcast(MinecraftServer server, CustomPayload payload) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (ServerPlayNetworking.canSend(p, payload.getId())) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}
}
