package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.contract.ForeverCopyEntity;
import io.github.kortev.chainsawman.contract.ForeverFlags;
import io.github.kortev.chainsawman.contract.ForeverHubEntity;
import io.github.kortev.chainsawman.network.ForeverLockPayload;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

/**
 * Forever as this client knows it: which living things are locked (told by the server; the freezing mixins read it), and, scanned
 * each tick, the Eternity Devil's holds in the world ({@link ForeverHubEntity}) with the copies that belong to each, and the devil's
 * layout for each ({@link ForeverLayout}).
 */
public final class ClientForever {
	private static final Map<Integer, ForeverHubEntity> HUBS = new HashMap<>();
	private static final Map<Integer, List<ForeverCopyEntity>> COPIES = new HashMap<>();
	private static final Map<Integer, ForeverLayout> LAYOUTS = new HashMap<>();

	private ClientForever() {
	}

	public static void told(ForeverLockPayload payload) {
		if (payload.on()) {
			ForeverFlags.CLIENT.add(payload.target());
		} else {
			ForeverFlags.CLIENT.remove(payload.target());
		}
	}

	public static void clear() {
		ForeverFlags.CLIENT.clear();
		HUBS.clear();
		COPIES.clear();
		LAYOUTS.clear();
	}

	public static Collection<ForeverHubEntity> hubs() {
		return HUBS.values();
	}

	/** The copies that belong to {@code hub}. */
	public static List<ForeverCopyEntity> copiesOf(ForeverHubEntity hub) {
		return copies(hub.getId());
	}

	/** Where the mouths of the devil's flesh round {@code hub} are in the world, each as {@code [where, which way it faces]} (for filming). */
	public static List<net.minecraft.util.math.Vec3d[]> mouths(ForeverHubEntity hub) {
		List<net.minecraft.util.math.Vec3d[]> out = new ArrayList<>();
		ForeverLayout l = layout(hub);
		for (ForeverLayout.Mouth m : l.mouths) {
			out.add(new net.minecraft.util.math.Vec3d[] {hub.getPos().add(m.pos), m.normal});
		}
		return out;
	}

	/** For each lump that has an eye: {@code [mouth, which way it faces, eye]} in the world (for filming a mouth and an eye together). */
	public static List<net.minecraft.util.math.Vec3d[]> mouthsWithEyes(ForeverHubEntity hub) {
		List<net.minecraft.util.math.Vec3d[]> out = new ArrayList<>();
		ForeverLayout l = layout(hub);
		for (ForeverLayout.Mouth m : l.mouths) {
			for (ForeverLayout.Eye e : l.eyes) {
				if (e.lump == m.lump) {
					out.add(new net.minecraft.util.math.Vec3d[] {hub.getPos().add(m.pos), m.normal, hub.getPos().add(e.pos)});
					break;
				}
			}
		}
		return out;
	}

	/** How high the target hangs over the ground under it (0 when it stands on it), for filming. */
	public static double hover(ForeverHubEntity hub) {
		return Math.max(0.0, -layout(hub).centreGround);
	}

	/** Where the eyes are in the world (for filming). */
	public static List<net.minecraft.util.math.Vec3d> eyes(ForeverHubEntity hub) {
		List<net.minecraft.util.math.Vec3d> out = new ArrayList<>();
		for (ForeverLayout.Eye e : layout(hub).eyes) {
			out.add(hub.getPos().add(e.pos));
		}
		return out;
	}

	static ForeverHubEntity hub(int id) {
		return HUBS.get(id);
	}

	static List<ForeverCopyEntity> copies(int hubId) {
		return COPIES.getOrDefault(hubId, List.of());
	}

	static ForeverLayout layout(ForeverHubEntity hub) {
		ForeverLayout l = LAYOUTS.get(hub.getId());
		if (l == null || l.seed != hub.seed()) {
			l = new ForeverLayout((net.minecraft.client.world.ClientWorld) hub.getWorld(), hub);
			LAYOUTS.put(hub.getId(), l);
		}
		return l;
	}

	/** Whether the player here is held by a hub that has not begun to sink. */
	public static boolean mineLocked(MinecraftClient client) {
		return client.player != null && ForeverFlags.CLIENT.contains(client.player.getId());
	}

	public static void tick(MinecraftClient client) {
		HUBS.clear();
		COPIES.clear();
		if (client.world == null) {
			LAYOUTS.clear();
			return;
		}
		for (Entity e : client.world.getEntities()) {
			if (e instanceof ForeverHubEntity hub && !hub.isRemoved()) {
				HUBS.put(hub.getId(), hub);
			} else if (e instanceof ForeverCopyEntity copy && !copy.isRemoved()) {
				COPIES.computeIfAbsent(copy.hubId(), k -> new ArrayList<>()).add(copy);
			}
		}
		LAYOUTS.keySet().removeIf(id -> !HUBS.containsKey(id));
	}
}
