package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.gap.GapManager;
import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.registry.ModGameRules;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * A whole Ginnungagap event on flat stone with three players in the world: the shooter, someone standing in the zone
 * and someone a long way off. The one in the zone is erased as the black reaches them; the other two are carried to
 * the rim of the hole and held there, out of harm's way, through the black and the rebuild; and then carried home.
 */
public class GapGameTests implements FabricGameTest {
	private static final int RADIUS = 20;

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_gap", tickLimit = GapTimeline.END + GapTimeline.REBUILD_END + 200)
	public void liveEvent(TestContext context) {
		ServerWorld world = context.getWorld();
		world.getGameRules().get(ModGameRules.GAP_RADIUS).set(RADIUS, world.getServer());
		world.getGameRules().get(ModGameRules.GAP_LETHAL).set(true, world.getServer());
		// Well clear of the test box: a disc of stone for the zone and a platform for each player.
		BlockPos center = context.getAbsolutePos(new BlockPos(4, 3, 80));
		for (BlockPos pos : BlockPos.iterate(center.add(-RADIUS - 16, -3, -RADIUS - 16), center.add(RADIUS + 16, 0, RADIUS + 16))) {
			world.setBlockState(pos, Blocks.STONE.getDefaultState());
		}
		BlockPos farGround = center.add(150, 0, 30);
		for (BlockPos pos : BlockPos.iterate(farGround.add(-3, -1, -3), farGround.add(3, 0, 3))) {
			world.setBlockState(pos, Blocks.STONE.getDefaultState());
		}
		Vec3d shooterHome = Vec3d.ofBottomCenter(center.add(-RADIUS - 10, 1, 0));
		Vec3d victimAt = Vec3d.ofBottomCenter(center.add(6, 1, 4));
		Vec3d farHome = Vec3d.ofBottomCenter(farGround.up());
		ServerPlayerEntity shooter = player(context, "shooter", shooterHome);
		ServerPlayerEntity victim = player(context, "victim", victimAt);
		ServerPlayerEntity far = player(context, "faraway", farHome);

		GapManager.Gap gap = GapManager.launch(world, center, shooter);
		StringBuilder problems = new StringBuilder();
		context.waitAndRun(GapTimeline.ERASURE - 2, () -> {
			if (!victim.isAlive()) {
				problems.append("the victim died before the black reached them; ");
			}
		});
		context.waitAndRun(GapTimeline.NOTHING + 30, () -> {
			if (victim.isAlive()) {
				problems.append("the victim in the zone survived the black with ").append(victim.getHealth()).append(" hp; ");
			}
			if (!shooter.isAlive() || !far.isAlive()) {
				problems.append("someone outside the zone died: shooter ").append(shooter.isAlive()).append(", far ").append(far.isAlive())
						.append("; ");
			}
			for (ServerPlayerEntity player : new ServerPlayerEntity[] {shooter, far}) {
				double out = Math.hypot(player.getX() - center.getX() - 0.5, player.getZ() - center.getZ() - 0.5);
				if (Math.abs(out - RADIUS - 10.0) > 3.0) {
					problems.append(player.getName().getString()).append(" was not gathered at the rim (").append(String.format("%.1f", out))
							.append(" out); ");
				}
				if (!GapManager.held(player)) {
					problems.append(player.getName().getString()).append(" is not held on the floor; ");
				}
			}
			if (shooter.getPos().distanceTo(far.getPos()) > 12.0) {
				problems.append("the two at the rim are ").append(String.format("%.1f", shooter.getPos().distanceTo(far.getPos())))
						.append(" apart; ");
			}
			// Held means untouchable.
			float before = far.getHealth();
			far.damage(world.getDamageSources().generic(), 5.0F);
			if (far.getHealth() < before) {
				problems.append("the far player was hurt while held; ");
			}
		});
		context.waitAndRun(GapTimeline.END + 2, () -> GapManager.release(gap, world.getServer()));
		context.waitAndRun(GapTimeline.END + 2 + GapTimeline.REBUILD_END + 40, () -> {
			if (far.getPos().distanceTo(farHome) > 2.0) {
				problems.append("the far player was not taken home: at ").append(far.getPos()).append("; ");
			}
			if (shooter.getPos().distanceTo(shooterHome) > 2.0) {
				problems.append("the shooter was not taken home: at ").append(shooter.getPos()).append("; ");
			}
			if (GapManager.held(far) || GapManager.held(shooter)) {
				problems.append("still held after going home; ");
			}
			ShootingStar.LOGGER.info("[gametest] liveEvent: {}", problems.length() == 0 ? "ok" : problems);
			context.assertTrue(problems.length() == 0, problems.toString());
			context.complete();
		});
	}

	/** A connected survival player standing at {@code at}. */
	private static ServerPlayerEntity player(TestContext context, String name, Vec3d at) {
		ServerWorld world = context.getWorld();
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(GameMode.SURVIVAL);
		player.teleport(world, at.x, at.y, at.z, 0.0F, 0.0F);
		return player;
	}
}
