package io.github.kortev.shootingstar.gap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * What a Ginnungagap event has to remember through the server going down, saved with the world: where everyone it
 * gathered came from (so they are sent home whenever they are next seen), the event itself while it runs (so a hole cut
 * short by a crash is finished when the server comes back), and whose cracked key is to be put right.
 */
final class GapState extends PersistentState {
	private static final String ID = "shootingstar_ginnungagap";
	private static final Type<GapState> TYPE = new Type<>(GapState::new, GapState::read, null);

	/** Where someone gathered for an event came from, and which way they faced. */
	record Home(RegistryKey<World> world, Vec3d pos, float yaw, float pitch) {
	}

	/** The event running: enough to finish its hole after a crash. */
	record Event(RegistryKey<World> dimension, BlockPos target, int radius, boolean terrain, UUID shooter, boolean taken) {
	}

	final Map<UUID, Home> homes = new HashMap<>();
	/** Players whose cracked key is to be put right when next seen: true to shatter it (the world was taken), false to mend it. */
	final Map<UUID, Boolean> keys = new HashMap<>();
	@Nullable
	Event event;

	static GapState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		NbtList list = new NbtList();
		homes.forEach((id, home) -> {
			NbtCompound entry = new NbtCompound();
			entry.putUuid("Player", id);
			entry.putString("World", home.world().getValue().toString());
			entry.putDouble("X", home.pos().x);
			entry.putDouble("Y", home.pos().y);
			entry.putDouble("Z", home.pos().z);
			entry.putFloat("Yaw", home.yaw());
			entry.putFloat("Pitch", home.pitch());
			list.add(entry);
		});
		nbt.put("Homes", list);
		NbtList keyList = new NbtList();
		keys.forEach((id, shatter) -> {
			NbtCompound entry = new NbtCompound();
			entry.putUuid("Player", id);
			entry.putBoolean("Shatter", shatter);
			keyList.add(entry);
		});
		nbt.put("Keys", keyList);
		if (event != null) {
			NbtCompound entry = new NbtCompound();
			entry.putString("World", event.dimension().getValue().toString());
			entry.putLong("Target", event.target().asLong());
			entry.putInt("Radius", event.radius());
			entry.putBoolean("Terrain", event.terrain());
			entry.putUuid("Shooter", event.shooter());
			entry.putBoolean("Taken", event.taken());
			nbt.put("Event", entry);
		}
		return nbt;
	}

	private static GapState read(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		GapState state = new GapState();
		for (NbtElement element : nbt.getList("Homes", NbtElement.COMPOUND_TYPE)) {
			NbtCompound entry = (NbtCompound) element;
			Identifier world = Identifier.tryParse(entry.getString("World"));
			if (world != null && entry.containsUuid("Player")) {
				state.homes.put(entry.getUuid("Player"), new Home(RegistryKey.of(RegistryKeys.WORLD, world),
						new Vec3d(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z")), entry.getFloat("Yaw"), entry.getFloat("Pitch")));
			}
		}
		for (NbtElement element : nbt.getList("Keys", NbtElement.COMPOUND_TYPE)) {
			NbtCompound entry = (NbtCompound) element;
			if (entry.containsUuid("Player")) {
				state.keys.put(entry.getUuid("Player"), entry.getBoolean("Shatter"));
			}
		}
		if (nbt.contains("Event", NbtElement.COMPOUND_TYPE)) {
			NbtCompound entry = nbt.getCompound("Event");
			Identifier world = Identifier.tryParse(entry.getString("World"));
			if (world != null && entry.containsUuid("Shooter")) {
				state.event = new Event(RegistryKey.of(RegistryKeys.WORLD, world), BlockPos.fromLong(entry.getLong("Target")), entry.getInt("Radius"),
						entry.getBoolean("Terrain"), entry.getUuid("Shooter"), entry.getBoolean("Taken"));
			}
		}
		return state;
	}
}
