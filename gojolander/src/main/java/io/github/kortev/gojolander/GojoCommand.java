package io.github.kortev.gojolander;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.kortev.gojolander.domain.DomainManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * {@code /gojolander}, for operators: {@code owner [<name>]} shows or sets whose this mod is; {@code domain <pos>} casts
 * a Domain there with nobody casting it (everyone inside is caught); {@code collapse} shatters every Domain now; and
 * {@code ready <player>} makes their Domain ready again.
 */
public final class GojoCommand {
	private GojoCommand() {
	}

	static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				CommandManager.literal("gojolander")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.literal("owner")
								.executes(context -> {
									context.getSource().sendFeedback(() -> Text.translatable("commands.gojolander.owner", Owner.get()), false);
									return 1;
								})
								.then(CommandManager.argument("name", StringArgumentType.word())
										.executes(context -> owner(context.getSource(), StringArgumentType.getString(context, "name")))))
						.then(CommandManager.literal("domain")
								.then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
										.executes(context -> domain(context.getSource(), Vec3ArgumentType.getVec3(context, "pos")))))
						.then(CommandManager.literal("collapse")
								.executes(context -> {
									int count = DomainManager.collapseAll();
									context.getSource().sendFeedback(() -> Text.translatable("commands.gojolander.collapse", count), true);
									return count;
								}))
						.then(CommandManager.literal("ready")
								.then(CommandManager.argument("player", EntityArgumentType.player())
										.executes(context -> ready(context.getSource(), EntityArgumentType.getPlayer(context, "player")))))));
	}

	private static int owner(ServerCommandSource source, String name) {
		Owner.set(source.getServer(), name);
		source.sendFeedback(() -> Text.translatable("commands.gojolander.owner.set", Owner.get()), true);
		return 1;
	}

	private static int domain(ServerCommandSource source, Vec3d pos) {
		DomainManager.launch(source.getWorld(), pos, null);
		source.sendFeedback(() -> Text.translatable("commands.gojolander.domain", (int) pos.x, (int) pos.y, (int) pos.z), true);
		return 1;
	}

	private static int ready(ServerCommandSource source, ServerPlayerEntity player) {
		DomainManager.ready(player);
		source.sendFeedback(() -> Text.translatable("commands.gojolander.ready", player.getDisplayName()), true);
		return 1;
	}
}
