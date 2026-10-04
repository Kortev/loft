package io.github.kortev.shootingstar.command;

import io.github.kortev.shootingstar.gap.GapManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** {@code /ginnungagap open <pos>} and {@code /ginnungagap release}, for operators and map makers. */
public final class GapCommand {
	private GapCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				CommandManager.literal("ginnungagap")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.literal("open")
								.then(CommandManager.argument("target", BlockPosArgumentType.blockPos())
										.executes(context -> open(context.getSource(),
												BlockPosArgumentType.getLoadedBlockPos(context, "target")))))
						.then(CommandManager.literal("release")
								.executes(context -> release(context.getSource())))));
	}

	private static int open(ServerCommandSource source, BlockPos pos) {
		GapManager.launch(source.getWorld(), pos, source.getPlayer());
		source.sendFeedback(() -> Text.translatable("commands.shootingstar.gap.open", pos.getX(), pos.getY(), pos.getZ()), true);
		return 1;
	}

	private static int release(ServerCommandSource source) {
		int released = GapManager.cancelAll(source.getServer());
		source.sendFeedback(() -> Text.translatable("commands.shootingstar.gap.release", released), true);
		return released;
	}
}
