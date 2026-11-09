package io.github.kortev.chainsawman.form;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/**
 * What the world remembers about teams and hearts, kept with the overworld: who holds a heart and when they were last seen, when a
 * heart was last stolen from or by each, whose heart went back to the world while they were away (they find out when they join), and
 * whose buddy ended it while they were away.
 */
public final class TeamState extends PersistentState {
	private static final Type<TeamState> TYPE = new Type<>(TeamState::new, TeamState::fromNbt, null);

	/** Hybrids (by who) and the world time they were last in the game. */
	public final Map<UUID, Long> seen = new HashMap<>();
	/** Who got their heart by stealing it, and when (world time): it cannot change hands again for a day. */
	public final Map<UUID, Long> stolen = new HashMap<>();
	/** Whose heart went back to the world while they were away. */
	public final Set<UUID> returned = new HashSet<>();
	/** Whose buddy ended it while they were away. */
	public final Set<UUID> ended = new HashSet<>();

	public static TeamState of(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "chainsawman_teams");
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		nbt.put("Seen", times(seen));
		nbt.put("Stolen", times(stolen));
		nbt.put("Returned", ids(returned));
		nbt.put("Ended", ids(ended));
		return nbt;
	}

	private static NbtList times(Map<UUID, Long> map) {
		NbtList list = new NbtList();
		for (Map.Entry<UUID, Long> e : map.entrySet()) {
			NbtCompound c = new NbtCompound();
			c.putUuid("Id", e.getKey());
			c.putLong("At", e.getValue());
			list.add(c);
		}
		return list;
	}

	private static NbtList ids(Set<UUID> set) {
		NbtList list = new NbtList();
		for (UUID id : set) {
			NbtCompound c = new NbtCompound();
			c.putUuid("Id", id);
			list.add(c);
		}
		return list;
	}

	private static TeamState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		TeamState state = new TeamState();
		for (NbtElement e : nbt.getList("Seen", NbtElement.COMPOUND_TYPE)) {
			NbtCompound c = (NbtCompound) e;
			state.seen.put(c.getUuid("Id"), c.getLong("At"));
		}
		for (NbtElement e : nbt.getList("Stolen", NbtElement.COMPOUND_TYPE)) {
			NbtCompound c = (NbtCompound) e;
			state.stolen.put(c.getUuid("Id"), c.getLong("At"));
		}
		for (NbtElement e : nbt.getList("Returned", NbtElement.COMPOUND_TYPE)) {
			state.returned.add(((NbtCompound) e).getUuid("Id"));
		}
		for (NbtElement e : nbt.getList("Ended", NbtElement.COMPOUND_TYPE)) {
			state.ended.add(((NbtCompound) e).getUuid("Id"));
		}
		return state;
	}
}
