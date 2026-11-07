package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chainsawman.contract.Contract;
import io.github.kortev.chainsawman.contract.Contracts;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Heart;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * The Eternity contract and its power, Echo: the contract skeleton (granted, cleared, one at a time, never for a hybrid) and
 * Echo itself (the crowd of copies round a target on uneven ground, their mirroring, the damage cap, the finale, the cooldown).
 */
public class EchoGameTests implements FabricGameTest {
	/** A contract is granted and cleared, and told to the player's saved state. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_echo_contract", tickLimit = 20)
	public void contractGrantAndClear(TestContext context) {
		ServerPlayerEntity player = player(context, "holder");
		try {
			context.assertTrue(Contracts.get(player) == Contract.NONE, "a new player held a contract");
			context.assertTrue(Contracts.grant(player, Contract.ETERNITY) == Contracts.Result.GRANTED, "the grant was refused");
			context.assertTrue(Contracts.has(player, Contract.ETERNITY), "the contract was not held after the grant");
			Contracts.clear(player);
			context.assertTrue(Contracts.get(player) == Contract.NONE, "the contract was still held after clearing");
		} finally {
			leave(context, player);
		}
		context.complete();
	}

	/** One contract at a time: granting again (or another) leaves exactly one, and clearing leaves none. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_echo_one", tickLimit = 20)
	public void oneContractAtATime(TestContext context) {
		ServerPlayerEntity player = player(context, "monogamist");
		try {
			Contracts.grant(player, Contract.ETERNITY);
			Contracts.grant(player, Contract.ETERNITY);
			context.assertTrue(Contracts.get(player) == Contract.ETERNITY, "the contract was not held");
			Contracts.grant(player, Contract.NONE);
			context.assertTrue(Contracts.get(player) == Contract.NONE, "granting none did not end the contract");
		} finally {
			leave(context, player);
		}
		context.complete();
	}

	/** A hybrid (Pochita's heart) cannot hold a contract, and taking the heart gives the contract up. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_echo_hybrid", tickLimit = 20)
	public void hybridsCannotHoldContracts(TestContext context) {
		ServerPlayerEntity hybrid = player(context, "hybrid");
		ServerPlayerEntity hunter = player(context, "hunter");
		try {
			Heart.give(hybrid);
			context.assertTrue(Contracts.grant(hybrid, Contract.ETERNITY) == Contracts.Result.HYBRID, "a hybrid was granted a contract");
			context.assertTrue(Contracts.get(hybrid) == Contract.NONE, "a hybrid holds a contract");
			Contracts.grant(hunter, Contract.ETERNITY);
			Heart.give(hunter);
			context.assertTrue(Contracts.get(hunter) == Contract.NONE, "merging with Pochita did not end the contract");
		} finally {
			Heart.take(hybrid);
			Heart.take(hunter);
			leave(context, hybrid);
			leave(context, hunter);
		}
		context.complete();
	}

	// Helpers.

	static ServerPlayerEntity player(TestContext context, String name) {
		ServerWorld world = context.getWorld();
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(GameMode.SURVIVAL);
		Vec3d at = context.getAbsolute(new Vec3d(0.5, 40.0, 0.5));
		player.teleport(world, at.x, at.y, at.z, 0.0F, 0.0F);
		player.onTeleportationDone();
		player.setNoGravity(true);
		return player;
	}

	static void leave(TestContext context, ServerPlayerEntity player) {
		Form.end(player);
		context.getWorld().getServer().getPlayerManager().remove(player);
	}
}
