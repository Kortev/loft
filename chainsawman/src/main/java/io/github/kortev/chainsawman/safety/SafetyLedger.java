package io.github.kortev.chainsawman.safety;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/**
 * What the world keeps for Public Safety, with the overworld: the bells whose villages have had their notice board put up (or had no room
 * for one), by dimension. A village is done once, whatever happens to its board afterwards.
 */
public final class SafetyLedger extends PersistentState {
	private static final Type<SafetyLedger> TYPE = new Type<>(SafetyLedger::new, SafetyLedger::fromNbt, null);

	/** Dimension id to the bells done (as packed block positions). */
	public final Map<String, Set<Long>> bells = new HashMap<>();

	public static SafetyLedger of(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "chainsawman_safety");
	}

	/** Whether a bell within {@code radius} blocks of {@code bell} (in {@code dimension}) is done: the same village. */
	public boolean doneNear(String dimension, BlockPos bell, int radius) {
		Set<Long> set = bells.get(dimension);
		if (set == null) {
			return false;
		}
		long r2 = (long) radius * radius;
		for (long l : set) {
			if (BlockPos.fromLong(l).getSquaredDistance(bell) <= r2) {
				return true;
			}
		}
		return false;
	}

	public void done(String dimension, BlockPos bell) {
		bells.computeIfAbsent(dimension, k -> new HashSet<>()).add(bell.asLong());
		markDirty();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtList list = new NbtList();
		for (Map.Entry<String, Set<Long>> e : bells.entrySet()) {
			NbtCompound c = new NbtCompound();
			c.putString("Dimension", e.getKey());
			c.put("Bells", new NbtLongArray(e.getValue().stream().mapToLong(Long::longValue).toArray()));
			list.add(c);
		}
		nbt.put("Boards", list);
		return nbt;
	}

	public static SafetyLedger fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		SafetyLedger ledger = new SafetyLedger();
		for (NbtElement e : nbt.getList("Boards", NbtElement.COMPOUND_TYPE)) {
			NbtCompound c = (NbtCompound) e;
			Set<Long> set = new HashSet<>();
			for (long l : c.getLongArray("Bells")) {
				set.add(l);
			}
			ledger.bells.put(c.getString("Dimension"), set);
		}
		return ledger;
	}
}
