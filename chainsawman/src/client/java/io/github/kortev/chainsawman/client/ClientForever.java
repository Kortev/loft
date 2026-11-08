package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.contract.ForeverCopyEntity;
import io.github.kortev.chainsawman.contract.ForeverCubeEntity;
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
	private static final List<ForeverCubeEntity> CUBES = new ArrayList<>();
	/** Locks told before the thing they hold is known here: put in place as soon as it is. */
	private static final Map<Integer, ForeverLockPayload> PENDING = new HashMap<>();
	/** The world render's projection and view (rotation) matrices and where the camera was, for the screen passes. */
	public static org.joml.Matrix4f projection = new org.joml.Matrix4f();
	public static org.joml.Matrix4f view = new org.joml.Matrix4f();
	public static net.minecraft.util.math.Vec3d camera = net.minecraft.util.math.Vec3d.ZERO;

	private ClientForever() {
	}

	public static void told(ForeverLockPayload payload) {
		if (payload.on()) {
			ForeverFlags.CLIENT.add(payload.target());
			PENDING.put(payload.target(), payload);
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.world != null && client.world.getEntityById(payload.target()) != null) {
				freeze(client.world.getEntityById(payload.target()), payload);
				PENDING.remove(payload.target());
			}
		} else {
			ForeverFlags.CLIENT.remove(payload.target());
			PENDING.remove(payload.target());
		}
	}

	/**
	 * Puts a locked thing exactly where the server froze it and holds it: position, facing and stride set once, with the "previous"
	 * values the same so there is nothing to interpolate between (an entity whose tick is stopped would otherwise be drawn sliding
	 * from its old place to its new one over and over), the swing and the velocity cleared.
	 */
	private static void freeze(Entity e, ForeverLockPayload p) {
		if (!(e instanceof net.minecraft.entity.LivingEntity living)) {
			return;
		}
		living.refreshPositionAndAngles(p.x(), p.y(), p.z(), p.yaw(), p.pitch());
		living.updateTrackedPositionAndAngles(p.x(), p.y(), p.z(), p.yaw(), p.pitch(), 0);
		living.setBodyYaw(p.bodyYaw());
		living.prevBodyYaw = p.bodyYaw();
		living.setHeadYaw(p.headYaw());
		living.prevHeadYaw = p.headYaw();
		living.prevPitch = p.pitch();
		living.prevYaw = p.yaw();
		io.github.kortev.chainsawman.client.mixin.LimbAnimatorAccessor limbs = (io.github.kortev.chainsawman.client.mixin.LimbAnimatorAccessor) (Object) living.limbAnimator;
		limbs.chainsawman$setPos(p.limbPos());
		limbs.chainsawman$setSpeed(p.limbSpeed());
		limbs.chainsawman$setPrevSpeed(p.limbSpeed());
		living.handSwinging = false;
		living.handSwingTicks = 0;
		living.handSwingProgress = 0.0F;
		living.lastHandSwingProgress = 0.0F;
		living.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
	}

	/** The cubes of frozen time in the world here that have not begun to end. */
	public static List<ForeverCubeEntity> cubes() {
		return CUBES;
	}

	/** Whether the point is inside a cube of frozen time (particles there hang still). */
	public static boolean frozenAt(double x, double y, double z) {
		for (ForeverCubeEntity c : CUBES) {
			if (c.phase() != io.github.kortev.chainsawman.contract.ForeverHubEntity.SINKING && Math.abs(x - c.getX()) <= io.github.kortev.chainsawman.contract.Forever.CUBE_HALF
					&& Math.abs(y - c.getY()) <= io.github.kortev.chainsawman.contract.Forever.CUBE_HALF && Math.abs(z - c.getZ()) <= io.github.kortev.chainsawman.contract.Forever.CUBE_HALF) {
				return true;
			}
		}
		return false;
	}

	public static void clear() {
		ForeverFlags.CLIENT.clear();
		HUBS.clear();
		COPIES.clear();
		LAYOUTS.clear();
		CUBES.clear();
		PENDING.clear();
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

	/** The lumps of flesh round {@code hub}, each as {@code [x, y, z, radius]} in the world (for keeping a camera out of them). */
	public static List<double[]> lumps(ForeverHubEntity hub) {
		List<double[]> out = new ArrayList<>();
		for (ForeverLayout.Lump l : layout(hub).lumps) {
			for (ForeverLayout.Part p : l.parts) {
				out.add(new double[] {hub.getX() + p.x, hub.getY() + p.y + p.ry * 0.5, hub.getZ() + p.z, Math.max(p.rx, p.rz) * 1.05});
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
		net.minecraft.client.world.ClientWorld world = (net.minecraft.client.world.ClientWorld) hub.getWorld();
		List<ForeverCopyEntity> cs = copies(hub.getId());
		ForeverLayout l = LAYOUTS.get(hub.getId());
		if (l != null && l.seed == hub.seed() && (cs.isEmpty() || cs.size() + 1 == l.moments)) {
			return l;
		}
		// One lump for the held thing and one for each moment of it, nearest the middle first.
		List<ForeverCopyEntity> sorted = new ArrayList<>(cs);
		sorted.sort((x, y) -> Integer.compare(Math.abs(x.slot()), Math.abs(y.slot())));
		List<net.minecraft.util.math.Vec3d> feet = new ArrayList<>();
		feet.add(net.minecraft.util.math.Vec3d.ZERO);
		for (ForeverCopyEntity c : sorted) {
			feet.add(c.getPos().subtract(hub.getPos()));
		}
		double scale = 1.0;
		if (world.getEntityById(hub.targetId()) instanceof net.minecraft.entity.LivingEntity t) {
			scale = Math.max(1.0, t.getWidth() / 0.6 * 0.8);
		}
		float width = 0.6F;
		float height = 1.8F;
		if (world.getEntityById(hub.targetId()) instanceof net.minecraft.entity.LivingEntity t2) {
			width = t2.getWidth();
			height = t2.getHeight();
		}
		l = new ForeverLayout(world, hub, feet, scale, sorted, width, height);
		LAYOUTS.put(hub.getId(), l);
		return l;
	}

	/** Whether the player here is held by a hub that has not begun to sink. */
	public static boolean mineLocked(MinecraftClient client) {
		return client.player != null && ForeverFlags.CLIENT.contains(client.player.getId());
	}

	public static void tick(MinecraftClient client) {
		HUBS.clear();
		COPIES.clear();
		CUBES.clear();
		if (client.world == null) {
			LAYOUTS.clear();
			PENDING.clear();
			return;
		}
		for (Integer id : List.copyOf(PENDING.keySet())) {
			Entity e = client.world.getEntityById(id);
			if (e != null) {
				freeze(e, PENDING.remove(id));
			}
		}
		for (Entity e : client.world.getEntities()) {
			if (e instanceof ForeverCubeEntity cube && !cube.isRemoved()) {
				CUBES.add(cube);
			} else if (e instanceof ForeverHubEntity hub && !hub.isRemoved()) {
				HUBS.put(hub.getId(), hub);
			} else if (e instanceof ForeverCopyEntity copy && !copy.isRemoved()) {
				COPIES.computeIfAbsent(copy.hubId(), k -> new ArrayList<>()).add(copy);
			}
		}
		LAYOUTS.keySet().removeIf(id -> !HUBS.containsKey(id));
	}
}
