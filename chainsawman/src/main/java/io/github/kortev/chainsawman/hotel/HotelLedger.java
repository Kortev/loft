package io.github.kortev.chainsawman.hotel;

import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Fallen;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.world.PersistentState;

/**
 * What the world keeps for the hotels, with the overworld: what each player is owed (a boss's loot, the squad's pay) until they are in the
 * game, alive and not down to take it, and who holds each devil's contract (one per world, whichever hotel gave it).
 */
public final class HotelLedger extends PersistentState {
	private static final Type<HotelLedger> TYPE = new Type<>(HotelLedger::new, HotelLedger::fromNbt, null);

	/** What one player is owed: items, experience, and the lines that say what for (a translation key and its number, or -1 for none). */
	public static final class Owed {
		public final List<ItemStack> items = new ArrayList<>();
		public int xp;
		public final List<String> keys = new ArrayList<>();
		public final List<Integer> args = new ArrayList<>();
	}

	public final Map<UUID, Owed> owed = new HashMap<>();
	/** Who holds each devil's contract (by the contract's id). */
	public final Map<String, UUID> holders = new HashMap<>();

	public static HotelLedger of(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "chainsawman_hotels");
	}

	/** {@code id} is owed {@code items} and {@code xp}, with a line ({@code key}, and {@code arg} if not -1); paid now if they can take it. */
	public static void owe(MinecraftServer server, UUID id, List<ItemStack> items, int xp, String key, int arg) {
		HotelLedger ledger = of(server);
		Owed o = ledger.owed.computeIfAbsent(id, k -> new Owed());
		for (ItemStack stack : items) {
			o.items.add(stack.copy());
		}
		o.xp += xp;
		o.keys.add(key);
		o.args.add(arg);
		ledger.markDirty();
		ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
		if (p != null) {
			ledger.pay(p);
		}
	}

	/** Every tick: each player owed something who is in the game, alive and not down is paid. */
	public static void tick(MinecraftServer server) {
		HotelLedger ledger = of(server);
		if (ledger.owed.isEmpty()) {
			return;
		}
		for (UUID id : new ArrayList<>(ledger.owed.keySet())) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
			if (p != null) {
				ledger.pay(p);
			}
		}
	}

	/** Whether {@code p} can be paid now: in the game, alive, not down. */
	public static boolean payable(ServerPlayerEntity p) {
		return !p.isRemoved() && p.isAlive() && !p.isSpectator() && !Fallen.is(p) && !Downed.is(p);
	}

	private void pay(ServerPlayerEntity p) {
		Owed o = owed.get(p.getUuid());
		if (o == null || !payable(p)) {
			return;
		}
		owed.remove(p.getUuid());
		markDirty();
		if (o.xp > 0) {
			p.addExperience(o.xp);
		}
		for (ItemStack stack : o.items) {
			p.getInventory().offerOrDrop(stack.copy());
		}
		MutableText line = Text.empty();
		for (int i = 0; i < o.keys.size(); i++) {
			if (i > 0) {
				line.append(" ");
			}
			int arg = o.args.get(i);
			line.append(arg < 0 ? Text.translatable(o.keys.get(i)) : Text.translatable(o.keys.get(i), arg));
		}
		p.sendMessage(line, true);
	}

	/** Who holds the contract {@code contract}, or null. */
	public static UUID holder(MinecraftServer server, String contract) {
		return of(server).holders.get(contract);
	}

	public static void setHolder(MinecraftServer server, String contract, UUID id) {
		HotelLedger ledger = of(server);
		if (id == null) {
			ledger.holders.remove(contract);
		} else {
			ledger.holders.put(contract, id);
		}
		ledger.markDirty();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtList list = new NbtList();
		for (Map.Entry<UUID, Owed> e : owed.entrySet()) {
			Owed o = e.getValue();
			NbtCompound c = new NbtCompound();
			c.putUuid("Id", e.getKey());
			c.putInt("Xp", o.xp);
			NbtList items = new NbtList();
			for (ItemStack stack : o.items) {
				if (!stack.isEmpty()) {
					items.add(stack.encode(registries));
				}
			}
			c.put("Items", items);
			NbtList lines = new NbtList();
			for (int i = 0; i < o.keys.size(); i++) {
				NbtCompound l = new NbtCompound();
				l.putString("Key", o.keys.get(i));
				l.putInt("Arg", o.args.get(i));
				lines.add(l);
			}
			c.put("Lines", lines);
			list.add(c);
		}
		nbt.put("Owed", list);
		NbtList held = new NbtList();
		for (Map.Entry<String, UUID> e : holders.entrySet()) {
			NbtCompound c = new NbtCompound();
			c.putString("Contract", e.getKey());
			c.putUuid("Holder", e.getValue());
			held.add(c);
		}
		nbt.put("Holders", held);
		return nbt;
	}

	public static HotelLedger fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		HotelLedger ledger = new HotelLedger();
		for (NbtElement e : nbt.getList("Owed", NbtElement.COMPOUND_TYPE)) {
			NbtCompound c = (NbtCompound) e;
			Owed o = new Owed();
			o.xp = c.getInt("Xp");
			for (NbtElement i : c.getList("Items", NbtElement.COMPOUND_TYPE)) {
				ItemStack.fromNbt(registries, i).ifPresent(o.items::add);
			}
			for (NbtElement l : c.getList("Lines", NbtElement.COMPOUND_TYPE)) {
				o.keys.add(((NbtCompound) l).getString("Key"));
				o.args.add(((NbtCompound) l).getInt("Arg"));
			}
			ledger.owed.put(c.getUuid("Id"), o);
		}
		for (NbtElement e : nbt.getList("Holders", NbtElement.COMPOUND_TYPE)) {
			NbtCompound c = (NbtCompound) e;
			ledger.holders.put(c.getString("Contract"), c.getUuid("Holder"));
		}
		return ledger;
	}
}
