package io.github.kortev.chainsawman.hotel;

import io.github.kortev.chainsawman.ChainsawMan;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/**
 * One hotel's devil: where the hotel stands and how it is turned, whether its devil is away (killed, or gone off with a deal) and till
 * which in-game day, and who holds its contract. What is happening right now (the loop's laps, the deal, the fight) is in {@link Hotel.Run}
 * and is not saved: a hotel loaded again starts at its waiting loop.
 */
public class HomeBlockEntity extends BlockEntity {
	/** Every home that is loaded, for the tests and the commands to find. */
	public static final java.util.Set<HomeBlockEntity> LOADED = ConcurrentHashMap.newKeySet();

	public BlockPos origin = BlockPos.ORIGIN;
	public int rot;
	/** The devil is away until this in-game day (the day number is {@code timeOfDay / 24000}), if {@link #away}. */
	public boolean away;
	public long awayUntilDay;
	public UUID holder;
	public final Hotel.Run run = new Hotel.Run();

	public HomeBlockEntity(BlockPos pos, BlockState state) {
		super(ChainsawMan.HOME_ENTITY, pos, state);
	}

	public void set(BlockPos origin, int rot) {
		this.origin = origin;
		this.rot = rot & 3;
		markDirty();
	}

	@Override
	public void markRemoved() {
		LOADED.remove(this);
		super.markRemoved();
	}

	@Override
	public void cancelRemoval() {
		super.cancelRemoval();
		LOADED.add(this);
	}

	@Override
	public void setWorld(net.minecraft.world.World world) {
		super.setWorld(world);
		if (!world.isClient) {
			LOADED.add(this);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.writeNbt(nbt, lookup);
		nbt.putInt("OriginX", origin.getX());
		nbt.putInt("OriginY", origin.getY());
		nbt.putInt("OriginZ", origin.getZ());
		nbt.putInt("Rot", rot);
		nbt.putBoolean("Away", away);
		nbt.putLong("AwayUntil", awayUntilDay);
		if (holder != null) {
			nbt.putUuid("Holder", holder);
		}
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.readNbt(nbt, lookup);
		origin = new BlockPos(nbt.getInt("OriginX"), nbt.getInt("OriginY"), nbt.getInt("OriginZ"));
		rot = nbt.getInt("Rot") & 3;
		away = nbt.getBoolean("Away");
		awayUntilDay = nbt.getLong("AwayUntil");
		holder = nbt.containsUuid("Holder") ? nbt.getUuid("Holder") : null;
	}
}
