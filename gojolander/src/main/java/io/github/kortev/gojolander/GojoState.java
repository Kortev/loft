package io.github.kortev.gojolander;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

/**
 * What this mod keeps with the world: who its owner is, once an operator has said ({@link Owner}), and when each
 * caster's Domain is next ready, in the overworld's game time, so that leaving and coming back does not refresh it.
 */
public final class GojoState extends PersistentState {
	private static final String ID = "gojolander";
	private static final Type<GojoState> TYPE = new Type<>(GojoState::new, GojoState::read, null);

	@Nullable
	private String owner;
	private final Map<UUID, Long> readyAt = new HashMap<>();

	public static GojoState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
	}

	public String owner() {
		return owner != null ? owner : Owner.PLACEHOLDER;
	}

	void setOwner(String name) {
		owner = name;
		markDirty();
	}

	/** The overworld time at which {@code caster} may cast again (0 if they may now). */
	public long readyAt(UUID caster) {
		return readyAt.getOrDefault(caster, 0L);
	}

	public void setReadyAt(UUID caster, long time) {
		if (time <= 0L) {
			readyAt.remove(caster);
		} else {
			readyAt.put(caster, time);
		}
		markDirty();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		if (owner != null) {
			nbt.putString("Owner", owner);
		}
		NbtList list = new NbtList();
		readyAt.forEach((id, time) -> {
			NbtCompound entry = new NbtCompound();
			entry.putUuid("Caster", id);
			entry.putLong("ReadyAt", time);
			list.add(entry);
		});
		nbt.put("Ready", list);
		return nbt;
	}

	private static GojoState read(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		GojoState state = new GojoState();
		if (nbt.contains("Owner", NbtElement.STRING_TYPE)) {
			state.owner = nbt.getString("Owner");
		}
		NbtList list = nbt.getList("Ready", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			state.readyAt.put(entry.getUuid("Caster"), entry.getLong("ReadyAt"));
		}
		return state;
	}
}
