package io.github.kortev.shootingstar.gap;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.registry.ModCriteria;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Ginnungagap itself, the void between universes: a flat floor in the dark, under the lattice of the other
 * universes. When the black takes a world, everyone in it is brought here, standing where they were; when the key is
 * turned again they go back. Where each of them came from is kept with the world's saved data, so nobody is left
 * here by a restart.
 */
public final class VoidWorld {
	public static final RegistryKey<World> KEY = RegistryKey.of(RegistryKeys.WORLD, ShootingStar.id("ginnungagap"));
	/** Where feet stand on the floor (see data/shootingstar/dimension/ginnungagap.json). */
	public static final int FLOOR = 64;

	private VoidWorld() {
	}

	/** Where someone was taken from: their world, place and facing, and the event that took them. */
	public record Origin(Identifier world, double x, double y, double z, float yaw, float pitch, int gap) {
	}

	public static boolean in(ServerPlayerEntity player) {
		return player.getWorld().getRegistryKey() == KEY;
	}

	/** Takes the player into the void, standing where they stood, and remembers where they were. */
	public static void takeIn(ServerPlayerEntity player, int gapId) {
		ServerWorld into = player.getServer().getWorld(KEY);
		if (into == null || in(player)) {
			return;
		}
		Origins.of(player.getServer()).put(player.getUuid(), new Origin(player.getWorld().getRegistryKey().getValue(), player.getX(),
				player.getY(), player.getZ(), player.getYaw(), player.getPitch(), gapId));
		player.teleport(into, player.getX(), FLOOR, player.getZ(), player.getYaw(), player.getPitch());
		player.fallDistance = 0.0F;
		ModCriteria.fire(player, ModCriteria.GAP_VOID);
	}

	/**
	 * Sends the player back where they came from. If where they stood has been erased, they are set down on the rim
	 * of the hole instead, on the side they were on. With no record of where they came from (from before the record
	 * was kept), they go to their own spawn.
	 */
	public static void bringBack(ServerPlayerEntity player, @Nullable BlockPos hole, int radius) {
		MinecraftServer server = player.getServer();
		Origin origin = Origins.of(server).remove(player.getUuid());
		ServerWorld world = origin == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, origin.world()));
		if (world == null) {
			ServerWorld spawn = server.getWorld(player.getSpawnPointDimension());
			spawn = spawn == null ? server.getOverworld() : spawn;
			BlockPos at = player.getSpawnPointPosition() != null ? player.getSpawnPointPosition() : spawn.getSpawnPos();
			player.teleport(spawn, at.getX() + 0.5, spawn.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()),
					at.getZ() + 0.5, player.getYaw(), player.getPitch());
			return;
		}
		double x = origin.x();
		double y = origin.y();
		double z = origin.z();
		if (hole != null && Math.hypot(x - hole.getX() - 0.5, z - hole.getZ() - 0.5) <= radius + 8) {
			double dx = x - hole.getX() - 0.5;
			double dz = z - hole.getZ() - 0.5;
			double d = Math.hypot(dx, dz);
			if (d < 1.0E-3) {
				dx = 1.0;
				d = 1.0;
			}
			x = hole.getX() + 0.5 + dx / d * (radius + 10);
			z = hole.getZ() + 0.5 + dz / d * (radius + 10);
			y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z));
		}
		player.teleport(world, x, y, z, origin.yaw(), origin.pitch());
		player.fallDistance = 0.0F;
	}

	/** Whether the player was taken by the given event. */
	public static boolean takenBy(ServerPlayerEntity player, int gapId) {
		Origin origin = Origins.of(player.getServer()).get(player.getUuid());
		return origin != null && origin.gap() == gapId;
	}

	/** Where everyone in the void came from, saved with the overworld. */
	static final class Origins extends PersistentState {
		private static final Type<Origins> TYPE = new Type<>(Origins::new, Origins::read, null);
		private final Map<UUID, Origin> origins = new HashMap<>();

		static Origins of(MinecraftServer server) {
			return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "shootingstar_void_origins");
		}

		void put(UUID id, Origin origin) {
			origins.put(id, origin);
			markDirty();
		}

		@Nullable
		Origin get(UUID id) {
			return origins.get(id);
		}

		@Nullable
		Origin remove(UUID id) {
			Origin origin = origins.remove(id);
			markDirty();
			return origin;
		}

		private static Origins read(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
			Origins state = new Origins();
			for (String key : nbt.getKeys()) {
				NbtCompound o = nbt.getCompound(key);
				Identifier world = Identifier.tryParse(o.getString("World"));
				if (world == null) {
					continue;
				}
				state.origins.put(UUID.fromString(key), new Origin(world, o.getDouble("X"), o.getDouble("Y"), o.getDouble("Z"),
						o.getFloat("Yaw"), o.getFloat("Pitch"), o.getInt("Gap")));
			}
			return state;
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
			origins.forEach((id, o) -> {
				NbtCompound c = new NbtCompound();
				c.putString("World", o.world().toString());
				c.putDouble("X", o.x());
				c.putDouble("Y", o.y());
				c.putDouble("Z", o.z());
				c.putFloat("Yaw", o.yaw());
				c.putFloat("Pitch", o.pitch());
				c.putInt("Gap", o.gap());
				nbt.put(id.toString(), c);
			});
			return nbt;
		}
	}
}
