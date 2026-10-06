package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.gap.GapManager;
import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.item.GenesisKeyItem;
import io.github.kortev.shootingstar.registry.ModDamageTypes;
import io.github.kortev.shootingstar.registry.ModGameRules;
import io.github.kortev.shootingstar.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

/**
 * Whole Ginnungagap events on flat stone with players all over the server: the shooter; one standing where the burst
 * comes up (swallowed by it), one further out in the zone (erased by the black); one a long way off; one in the Nether;
 * and one whose home is over a column of nothing by the time it is over. Everyone left is carried to the rim, held
 * there on one floor, out of harm's way, and carried home again to solid ground.
 */
public class GapGameTests implements FabricGameTest {
	private static final int RADIUS = 20;
	/** How each player of these tests died. */
	private static final Map<UUID, RegistryKey<DamageType>> DEATHS = new HashMap<>();

	static {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity) {
				source.getTypeRegistryEntry().getKey().ifPresent(key -> DEATHS.put(entity.getUuid(), key));
			}
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_gap", tickLimit = GapTimeline.END + GapTimeline.REBUILD_END + 200)
	public void liveEvent(TestContext context) {
		ServerWorld world = context.getWorld();
		ServerWorld nether = world.getServer().getWorld(World.NETHER);
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
		// A home that will be over nothing: a column emptied to the bottom of the world, with one block left to stand on.
		BlockPos voidGround = center.add(70, 0, -50);
		for (BlockPos pos : BlockPos.iterate(voidGround.add(-4, world.getBottomY() - voidGround.getY(), -4), voidGround.add(4, 6, 4))) {
			world.setBlockState(pos, Blocks.AIR.getDefaultState());
		}
		world.setBlockState(voidGround, Blocks.STONE.getDefaultState());
		BlockPos netherGround = new BlockPos(center.getX() / 8, 100, center.getZ() / 8);
		for (BlockPos pos : BlockPos.iterate(netherGround.add(-2, 0, -2), netherGround.add(2, 3, 2))) {
			nether.setBlockState(pos, pos.getY() == netherGround.getY() ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
		}
		BlockPos spawnBefore = world.getSpawnPos();
		float spawnAngle = world.getSpawnAngle();
		world.setSpawnPos(center.add(3, 1, 3), 0.0F);

		Vec3d shooterHome = Vec3d.ofBottomCenter(center.add(-RADIUS - 10, 1, 0));
		Vec3d farHome = Vec3d.ofBottomCenter(farGround.up());
		Vec3d voidHome = Vec3d.ofBottomCenter(voidGround.up());
		Vec3d netherHome = Vec3d.ofBottomCenter(netherGround.up());
		ServerPlayerEntity shooter = player(context, world, "shooter", shooterHome);
		ServerPlayerEntity swallowed = player(context, world, "swallowed", Vec3d.ofBottomCenter(center.add(5, 1, 0)));
		ServerPlayerEntity erased = player(context, world, "erased", Vec3d.ofBottomCenter(center.add(17, 1, 0)));
		ServerPlayerEntity far = player(context, world, "faraway", farHome);
		ServerPlayerEntity voided = player(context, world, "voided", voidHome);
		ServerPlayerEntity netherite = player(context, nether, "netherite", netherHome);
		// The shooter's key, cracked by its first turn.
		ItemStack key = new ItemStack(ModItems.GENESIS_KEY);
		NbtCompound cracked = new NbtCompound();
		cracked.putBoolean("Cracked", true);
		key.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(cracked));
		shooter.getInventory().setStack(0, key);
		far.getInventory().setStack(0, new ItemStack(ModItems.GENESIS_KEY));

		GapManager.Gap gap = GapManager.launch(world, center, shooter);
		StringBuilder problems = new StringBuilder();
		context.waitAndRun(GapTimeline.INBOUND, () -> {
			// One at a time: a second key does not turn.
			far.setStackInHand(Hand.MAIN_HAND, far.getInventory().getStack(0));
			ActionResult second = far.getStackInHand(Hand.MAIN_HAND).use(world, far, Hand.MAIN_HAND).getResult();
			if (second.isAccepted() || GapManager.active().size() != 1) {
				problems.append("a second key turned while one was running; ");
			}
		});
		context.waitAndRun(GapTimeline.CONTACT - 2, () -> {
			if (!swallowed.isAlive() || !erased.isAlive()) {
				problems.append("someone died before the block came down; ");
			}
		});
		context.waitAndRun(GapTimeline.ERASURE - 2, () -> {
			if (swallowed.isAlive()) {
				problems.append("the player where the burst came up survived it; ");
			} else if (DEATHS.get(swallowed.getUuid()) != ModDamageTypes.SWALLOWED) {
				problems.append("the player in the burst died of ").append(DEATHS.get(swallowed.getUuid())).append("; ");
			}
			if (!erased.isAlive()) {
				problems.append("the player outside the burst died before the black came: ").append(DEATHS.get(erased.getUuid())).append("; ");
			}
		});
		context.waitAndRun(GapTimeline.NOTHING + 30, () -> {
			if (erased.isAlive()) {
				problems.append("the player in the zone survived the black; ");
			} else if (DEATHS.get(erased.getUuid()) != ModDamageTypes.ERASED) {
				problems.append("the player in the zone died of ").append(DEATHS.get(erased.getUuid())).append("; ");
			}
			double floor = Double.NaN;
			for (ServerPlayerEntity player : new ServerPlayerEntity[] {shooter, far, voided, netherite}) {
				String name = player.getName().getString();
				if (!player.isAlive()) {
					problems.append(name).append(" died; ");
					continue;
				}
				if (player.getWorld() != world) {
					problems.append(name).append(" was not brought into the event's world; ");
					continue;
				}
				double out = Math.hypot(player.getX() - center.getX() - 0.5, player.getZ() - center.getZ() - 0.5);
				if (out < RADIUS + 8 || out > RADIUS + 20) {
					problems.append(name).append(" was not gathered at the rim (").append(String.format("%.1f", out)).append(" out); ");
				}
				if (!GapManager.held(player)) {
					problems.append(name).append(" is not held on the floor; ");
				}
				if (Double.isNaN(floor)) {
					floor = player.getY();
				} else if (Math.abs(player.getY() - floor) > 0.01) {
					problems.append(name).append(" stands at ").append(String.format("%.2f", player.getY())).append(", not on everyone's floor at ")
							.append(String.format("%.2f", floor)).append("; ");
				}
			}
			// Level with the tree's roots, a block over the old ground at the point of contact.
			if (Math.abs(floor - (center.getY() + 2.0)) > 0.01) {
				problems.append("everyone's floor is at ").append(floor).append(", not level with the roots at ").append(center.getY() + 2.0)
						.append("; ");
			}
			if (shooter.getPos().distanceTo(far.getPos()) > 12.0) {
				problems.append("the shooter and the far player are ").append(String.format("%.1f", shooter.getPos().distanceTo(far.getPos())))
						.append(" apart; ");
			}
			// Held means untouchable.
			float before = far.getHealth();
			far.damage(world.getDamageSources().generic(), 5.0F);
			if (far.getHealth() < before) {
				problems.append("the far player was hurt while held; ");
			}
			// The world's spawn was in the hole: moved out to its rim.
			double spawnOut = Math.hypot(world.getSpawnPos().getX() - center.getX(), world.getSpawnPos().getZ() - center.getZ());
			if (spawnOut <= RADIUS + 8) {
				problems.append("the world's spawn is still in the hole, ").append(String.format("%.1f", spawnOut)).append(" out; ");
			}
			// The ground under the player whose home it was goes, an old hole by the time they go back.
			world.setBlockState(voidGround, Blocks.AIR.getDefaultState());
		});
		context.waitAndRun(GapTimeline.END + 2, () -> GapManager.release(gap, world.getServer()));
		context.waitAndRun(GapTimeline.END + 2 + GapTimeline.REBUILD_END + 40, () -> {
			if (far.getPos().distanceTo(farHome) > 2.0) {
				problems.append("the far player was not taken home: at ").append(far.getPos()).append("; ");
			}
			if (shooter.getPos().distanceTo(shooterHome) > 2.0) {
				problems.append("the shooter was not taken home: at ").append(shooter.getPos()).append("; ");
			}
			if (netherite.getWorld() != nether || netherite.getPos().distanceTo(netherHome) > 2.0) {
				problems.append("the player from the Nether was not taken home: in ").append(netherite.getWorld().getRegistryKey().getValue())
						.append(" at ").append(netherite.getPos()).append("; ");
			}
			BlockPos under = voided.getBlockPos().down();
			if (!voided.isAlive() || world.getBlockState(under).getCollisionShape(world, under).isEmpty()
					|| voided.getPos().distanceTo(voidHome) > 16.0) {
				problems.append("the player whose home went was not set down on ground near it: at ").append(voided.getPos())
						.append(" over ").append(world.getBlockState(under)).append("; ");
			}
			for (ServerPlayerEntity player : new ServerPlayerEntity[] {shooter, far, voided, netherite}) {
				if (GapManager.held(player)) {
					problems.append(player.getName().getString()).append(" is still held after going home; ");
				}
			}
			// Released without it: the cracked key has shattered.
			for (int i = 0; i < shooter.getInventory().size(); i++) {
				if (shooter.getInventory().getStack(i).isOf(ModItems.GENESIS_KEY)) {
					problems.append("the shooter's cracked key is still whole; ");
				}
			}
			world.setSpawnPos(spawnBefore, spawnAngle);
			ShootingStar.LOGGER.info("[gametest] liveEvent: {}", problems.length() == 0 ? "ok" : problems);
			context.assertTrue(problems.length() == 0, problems.toString());
			context.complete();
		});
	}

	/** The server going down in the middle of an event: the hole finished, its floor gone, everyone home, the key shattered. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "d_gapstop", tickLimit = GapTimeline.NOTHING + 200)
	public void stopMidEvent(TestContext context) {
		ServerWorld world = context.getWorld();
		world.getGameRules().get(ModGameRules.GAP_RADIUS).set(RADIUS, world.getServer());
		BlockPos center = context.getAbsolutePos(new BlockPos(4, 3, -120));
		for (BlockPos pos : BlockPos.iterate(center.add(-RADIUS - 16, -3, -RADIUS - 16), center.add(RADIUS + 16, 0, RADIUS + 16))) {
			world.setBlockState(pos, Blocks.STONE.getDefaultState());
		}
		Vec3d shooterHome = Vec3d.ofBottomCenter(center.add(-RADIUS - 6, 1, 4));
		Vec3d watcherHome = Vec3d.ofBottomCenter(center.add(RADIUS + 9, 1, -3));
		ServerPlayerEntity shooter = player(context, world, "stopshooter", shooterHome);
		ServerPlayerEntity watcher = player(context, world, "stopwatcher", watcherHome);
		ItemStack key = new ItemStack(ModItems.GENESIS_KEY);
		NbtCompound cracked = new NbtCompound();
		cracked.putBoolean("Cracked", true);
		key.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(cracked));
		shooter.getInventory().setStack(0, key);
		GapManager.launch(world, center, shooter);
		StringBuilder problems = new StringBuilder();
		context.waitAndRun(GapTimeline.NOTHING + 20, () -> {
			if (!GapManager.held(watcher)) {
				problems.append("the watcher was not gathered; ");
			}
			GapManager.endNow(world.getServer());
			if (GapManager.held(watcher) || GapManager.held(shooter) || GapManager.running()) {
				problems.append("someone is still held, or the event still running; ");
			}
			if (watcher.getPos().distanceTo(watcherHome) > 2.0) {
				problems.append("the watcher was not sent home: at ").append(watcher.getPos()).append("; ");
			}
			// The hole finished all the way down, and no floor of barriers left over it.
			for (int y = world.getBottomY(); y <= center.getY() + 2; y++) {
				BlockPos at = new BlockPos(center.getX() + 3, y, center.getZ() - 2);
				if (!world.getBlockState(at).isAir()) {
					problems.append("the hole is not finished: ").append(world.getBlockState(at)).append(" at y=").append(y).append("; ");
					break;
				}
			}
			for (int i = 0; i < shooter.getInventory().size(); i++) {
				if (shooter.getInventory().getStack(i).isOf(ModItems.GENESIS_KEY) && GenesisKeyItem.cracked(shooter.getInventory().getStack(i))) {
					problems.append("the shooter's cracked key is still there; ");
				}
			}
			ShootingStar.LOGGER.info("[gametest] stopMidEvent: {}", problems.length() == 0 ? "ok" : problems);
			context.assertTrue(problems.length() == 0, problems.toString());
			context.complete();
		});
	}

	/** A connected survival player standing at {@code at} in {@code world}. */
	private static ServerPlayerEntity player(TestContext context, ServerWorld world, String name, Vec3d at) {
		ServerWorld home = context.getWorld();
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayerEntity player = new ServerPlayerEntity(home.getServer(), home, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		home.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(GameMode.SURVIVAL);
		player.teleport(world, at.x, at.y, at.z, 0.0F, 0.0F);
		return player;
	}
}
