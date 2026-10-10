package io.github.kortev.shootingstar.command;

import io.github.kortev.shootingstar.strike.StrikeManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** {@code /gungnir strike <pos>} and {@code /gungnir cancel}, for operators and map makers. */
public final class GungnirCommand {
	private GungnirCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				CommandManager.literal("gungnir")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.literal("strike")
								.then(CommandManager.argument("target", BlockPosArgumentType.blockPos())
										.executes(context -> strike(context.getSource(),
												BlockPosArgumentType.getLoadedBlockPos(context, "target")))))
						.then(CommandManager.literal("cancel")
								.executes(context -> cancel(context.getSource())))));
	}

	private static int strike(ServerCommandSource source, BlockPos pos) {
		StrikeManager.launch(source.getWorld(), pos, source.getPlayer());
		source.sendFeedback(() -> Text.translatable("commands.shootingstar.strike", pos.getX(), pos.getY(), pos.getZ()), true);
		return 1;
	}

	private static int cancel(ServerCommandSource source) {
		int cancelled = StrikeManager.cancelAll(source.getServer());
		source.sendFeedback(() -> Text.translatable("commands.shootingstar.cancel", cancelled), true);
		return cancelled;
	}
}
