package io.github.kortev.chainsawman;

import io.github.kortev.chainsawman.form.Heart;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;

/**
 * There's one Pochita in a world, and he's waiting somewhere: fainted in the hollow of a fallen log, {@link #NEAR} to
 * {@link #FAR} blocks from the world's spawn, placed once when the world first starts with this mod. You have to go and
 * find him: within {@link #HEAR} blocks kortev hears him whimpering. If he's ever really lost (the void, the kill
 * command) he turns up there again a while later; once he's someone's heart, never again.
 */
public final class PochitaPlace extends PersistentState {
	private static final int NEAR = 250;
	private static final int FAR = 450;
	private static final double HEAR = 40.0;
	/** How long after he's lost he turns up again (ticks). */
	private static final int BACK_AFTER = 2400;
	private static final Type<PochitaPlace> TYPE = new Type<>(PochitaPlace::new, PochitaPlace::fromNbt, null);

	private boolean placed;
	private BlockPos at = BlockPos.ORIGIN;
	/** When he was lost (world time), or -1. */
	private long lost = -1;
	private boolean merged;

	private static PochitaPlace of(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "chainsawman_pochita");
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			PochitaPlace place = of(server);
			if (!place.placed) {
				place.choose(server.getOverworld());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 40 != 0) {
				return;
			}
			PochitaPlace place = of(server);
			ServerWorld world = server.getOverworld();
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (Heart.merged(player)) {
					if (!place.merged) {
						place.merged = true;
						place.markDirty();
					}
					continue;
				}
				if (player.getGameProfile().getName().equalsIgnoreCase(ChainsawMan.OWNER) && player.getWorld() == world
						&& !world.getEntitiesByClass(PochitaEntity.class, player.getBoundingBox().expand(HEAR), p -> !p.isTamed()).isEmpty()) {
					player.sendMessage(Text.translatable("message.chainsawman.pochita_near").formatted(Formatting.GRAY, Formatting.ITALIC), true);
				}
			}
			if (place.lost >= 0 && !place.merged && world.getTime() - place.lost > BACK_AFTER) {
				place.lost = -1;
				place.spawn(world);
				place.markDirty();
			}
		});
	}

	/** He's really gone: he'll be back at his place before long. */
	public static void lost(MinecraftServer server) {
		PochitaPlace place = of(server);
		place.lost = server.getOverworld().getTime();
		place.markDirty();
	}

	/** Somewhere dry and open some way from spawn, the log laid there, and him in it. */
	private void choose(ServerWorld world) {
		Random random = Random.create(world.getSeed() ^ 0x5EED_C4A1L);
		BlockPos spawn = world.getSpawnPos();
		for (int tries = 0; tries < 12; tries++) {
			float angle = random.nextFloat() * MathHelper.TAU;
			int distance = NEAR + random.nextInt(FAR - NEAR);
			int x = spawn.getX() + (int) (MathHelper.cos(angle) * distance);
			int z = spawn.getZ() + (int) (MathHelper.sin(angle) * distance);
			world.getChunk(x >> 4, z >> 4);
			int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos ground = new BlockPos(x, y - 1, z);
			if (y <= world.getBottomY() + 1 || !world.getFluidState(ground).isEmpty() || world.getFluidState(ground.up()).isIn(FluidTags.WATER)) {
				continue;
			}
			at = ground.up();
			log(world, at, random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z);
			spawn(world);
			placed = true;
			markDirty();
			ChainsawMan.LOGGER.info("Pochita is waiting at {}", at);
			return;
		}
		// Nowhere good: by spawn, then.
		at = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn);
		spawn(world);
		placed = true;
		markDirty();
	}

	/** A hollow fallen log lying along {@code axis}, open at both ends, ferns about it. */
	private static void log(ServerWorld world, BlockPos at, Direction.Axis axis) {
		BlockState wood = Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, axis);
		Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		Direction across = along.rotateYClockwise();
		for (int i = -2; i <= 2; i++) {
			BlockPos p = at.offset(along, i);
			world.setBlockState(p, Blocks.AIR.getDefaultState());
			world.setBlockState(p.up(), Blocks.AIR.getDefaultState());
			world.setBlockState(p.offset(across), wood);
			world.setBlockState(p.offset(across.getOpposite()), wood);
			world.setBlockState(p.up().offset(across), wood);
			world.setBlockState(p.up().offset(across.getOpposite()), wood);
			world.setBlockState(p.up(2), wood);
		}
		for (BlockPos p : new BlockPos[] {at.offset(along, 3), at.offset(along, -3).offset(across, 2), at.offset(across, -2).offset(along, 1)}) {
			if (world.getBlockState(p).isAir() && world.getBlockState(p.down()).isOpaqueFullCube(world, p.down())) {
				world.setBlockState(p, Blocks.FERN.getDefaultState());
			}
		}
	}

	/** Him, fainted, in the log. */
	private void spawn(ServerWorld world) {
		world.getChunk(at.getX() >> 4, at.getZ() >> 4);
		PochitaEntity pochita = ChainsawMan.POCHITA_ENTITY.create(world);
		if (pochita == null) {
			return;
		}
		pochita.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.random.nextFloat() * 360.0F, 0.0F);
		pochita.initialize(world, world.getLocalDifficulty(at), SpawnReason.STRUCTURE, null);
		pochita.setPersistent();
		world.spawnEntity(pochita);
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		nbt.putBoolean("Placed", placed);
		nbt.putLong("At", at.asLong());
		nbt.putLong("Lost", lost);
		nbt.putBoolean("Merged", merged);
		return nbt;
	}

	private static PochitaPlace fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		PochitaPlace place = new PochitaPlace();
		place.placed = nbt.getBoolean("Placed");
		place.at = BlockPos.fromLong(nbt.getLong("At"));
		place.lost = nbt.contains("Lost") ? nbt.getLong("Lost") : -1;
		place.merged = nbt.getBoolean("Merged");
		return place;
	}
}
