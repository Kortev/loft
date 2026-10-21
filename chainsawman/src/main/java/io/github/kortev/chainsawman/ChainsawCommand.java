package io.github.kortev.chainsawman;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.kortev.chainsawman.form.Blood;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Heart;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * {@code /chainsaw}, for operators, to try everything out: {@code scene} builds a test ground round you (a flat floor,
 * a row of mobs standing still in front of you to cut down, a wild Pochita fainted in a log to your right) and sets you
 * up to go through it all from the taming to the merge; {@code mobs} puts the row of mobs back; {@code pochita} a
 * fainted Pochita in a log beside you; {@code heart} gives you Pochita's heart now (no merge), {@code reset} takes it
 * away again (to see the merge again); {@code blood} fills your blood; {@code down} and {@code up} put you down and back
 * up.
 */
public final class ChainsawCommand {
	/** How far the test ground goes out from you, and how high it's cleared (blocks). */
	private static final int RADIUS = 16;
	private static final int CLEAR = 8;
	/** What the mobs in the row are tagged, so the next scene takes them away first. */
	private static final String TAG = "chainsawman_scene";
	private static final EntityType<?>[] ROW = {EntityType.HUSK, EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.CREEPER, EntityType.SPIDER,
			EntityType.COW, EntityType.PIG, EntityType.SHEEP};

	private ChainsawCommand() {
	}

	static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				CommandManager.literal("chainsaw")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.literal("scene").executes(context -> scene(context)))
						.then(CommandManager.literal("mobs").executes(context -> {
							mobs(context.getSource().getPlayerOrThrow());
							return done(context, "mobs");
						}))
						.then(CommandManager.literal("pochita").executes(context -> {
							ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
							pochita(player, side(player).multiply(4.0).add(player.getPos()));
							return done(context, "pochita");
						}))
						.then(CommandManager.literal("heart").executes(context -> {
							ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
							Heart.give(player);
							Blood.set(player, Blood.MAX);
							return done(context, "heart");
						}))
						.then(CommandManager.literal("reset").executes(context -> {
							reset(context.getSource().getPlayerOrThrow());
							return done(context, "reset");
						}))
						.then(CommandManager.literal("blood").executes(context -> {
							Blood.set(context.getSource().getPlayerOrThrow(), Blood.MAX);
							return done(context, "blood");
						}))
						.then(CommandManager.literal("down").executes(context -> {
							ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
							Form.end(player);
							Downed.down(player);
							return done(context, "down");
						}))
						.then(CommandManager.literal("up").executes(context -> {
							Downed.up(context.getSource().getPlayerOrThrow());
							return done(context, "up");
						}))));
	}

	private static int done(CommandContext<ServerCommandSource> context, String what) {
		context.getSource().sendFeedback(() -> Text.translatable("commands.chainsaw." + what), false);
		return 1;
	}

	/** The test ground round {@code player}, and them set up to go through it all. */
	private static int scene(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		ServerWorld world = player.getServerWorld();
		BlockPos feet = player.getBlockPos();
		// A flat floor, and clear air over it.
		for (int x = -RADIUS; x <= RADIUS; x++) {
			for (int z = -RADIUS; z <= RADIUS; z++) {
				world.setBlockState(feet.add(x, -1, z), Blocks.SMOOTH_STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
				for (int y = 0; y < CLEAR; y++) {
					world.setBlockState(feet.add(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
		world.setTimeOfDay(6000);
		world.setWeather(12000, 0, false, false);
		reset(player);
		// Survival: the merge needs you able to die.
		player.changeGameMode(GameMode.SURVIVAL);
		mobs(player);
		pochita(player, side(player).multiply(9.0).add(player.getPos()));
		player.sendMessage(Text.translatable("commands.chainsaw.scene").formatted(Formatting.GOLD), false);
		for (int i = 1; i <= 7; i++) {
			player.sendMessage(Text.translatable("commands.chainsaw.scene." + i, Text.keybind("key.chainsawman.pull_cord"),
					Text.keybind("key.chainsawman.chain"), Text.keybind("key.chainsawman.frenzy")).formatted(Formatting.GRAY), false);
		}
		return 1;
	}

	/** Out of the form, up, whole, full of blood, and Pochita's heart not theirs: as before it all. */
	private static void reset(ServerPlayerEntity player) {
		Form.end(player);
		Downed.up(player);
		Heart.take(player);
		player.setHealth(player.getMaxHealth());
		Blood.set(player, Blood.MAX);
	}

	/** A row of mobs standing still, 6 blocks in front of {@code player}, facing them, to cut down. */
	private static void mobs(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		for (Entity old : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(40.0), e -> e.getCommandTags().contains(TAG))) {
			old.discard();
		}
		Vec3d front = front(player);
		Vec3d side = side(player);
		float facing = player.getYaw() + 180.0F;
		for (int i = 0; i < ROW.length; i++) {
			Vec3d at = player.getPos().add(front.multiply(6.0)).add(side.multiply(-7.0 + i * 2.0));
			Entity entity = ROW[i].create(world);
			if (!(entity instanceof MobEntity mob)) {
				continue;
			}
			mob.refreshPositionAndAngles(at.x, player.getY(), at.z, facing, 0.0F);
			mob.initialize(world, world.getLocalDifficulty(mob.getBlockPos()), SpawnReason.COMMAND, null);
			mob.setAiDisabled(true);
			mob.setPersistent();
			mob.setHeadYaw(facing);
			mob.setBodyYaw(facing);
			mob.addCommandTag(TAG);
			world.spawnEntity(mob);
		}
	}

	/** A wild Pochita fainted under a fallen tree at {@code at}. */
	private static void pochita(ServerPlayerEntity player, Vec3d at) {
		PochitaPlace.waiting(player.getServerWorld(), BlockPos.ofFloored(at.x, player.getY(), at.z),
				// Lying across your way, an open end toward you.
				Math.abs(front(player).x) > 0.5 ? Direction.Axis.Z : Direction.Axis.X);
	}

	/** The way {@code player} faces, to the nearest of the four, flat. */
	private static Vec3d front(ServerPlayerEntity player) {
		Direction facing = player.getHorizontalFacing();
		return new Vec3d(facing.getOffsetX(), 0.0, facing.getOffsetZ());
	}

	/** To {@code player}'s right. */
	private static Vec3d side(ServerPlayerEntity player) {
		Direction right = player.getHorizontalFacing().rotateYClockwise();
		return new Vec3d(right.getOffsetX(), 0.0, right.getOffsetZ());
	}
}
