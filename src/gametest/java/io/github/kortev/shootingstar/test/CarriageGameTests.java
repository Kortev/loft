package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chitty.carriage.Carriage;
import io.github.kortev.chitty.carriage.CarriageEntity;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The Child Catcher's carriage with nobody driving her: the server moves her. She is longer than a test's box with her
 * horse ahead of her, so each test runs in a batch of its own, and takes her away at its end. Prisoners are husks,
 * which the sun cannot hurt.
 */
public class CarriageGameTests implements FabricGameTest {
	private static void floor(TestContext context) {
		ServerWorld world = context.getWorld();
		for (BlockPos pos : BlockPos.iterate(context.getAbsolutePos(new BlockPos(0, 0, 0)), context.getAbsolutePos(new BlockPos(7, 0, 7)))) {
			world.setBlockState(pos, Blocks.STONE.getDefaultState());
		}
	}

	/** A carriage facing along +z. */
	private static CarriageEntity carriage(TestContext context, double x, double z) {
		CarriageEntity carriage = context.spawnEntity(Carriage.ENTITY, new Vec3d(x, 1.0, z));
		carriage.setYaw(0.0F);
		carriage.prevYaw = 0.0F;
		return carriage;
	}

	private static HorseEntity horse(TestContext context, double x, double z) {
		HorseEntity horse = context.spawnEntity(EntityType.HORSE, new Vec3d(x, 1.0, z));
		horse.setBaby(false);
		return horse;
	}

	private static ZombieEntity husk(TestContext context, Vec3d at) {
		ZombieEntity husk = context.spawnEntity(EntityType.HUSK, at);
		husk.setAiDisabled(true);
		return husk;
	}

	private static void done(TestContext context, CarriageEntity carriage, ServerPlayerEntity... players) {
		for (ServerPlayerEntity player : players) {
			// Off first: a player leaving the game while aboard takes the vehicle with them.
			player.stopRiding();
			context.getWorld().getServer().getPlayerManager().remove(player);
		}
		carriage.removeAllPassengers();
		carriage.discard();
		context.complete();
	}

	/**
	 * A horse led up to her on a lead and her used is hitched into her shafts: off the lead (which goes back to the
	 * player), standing where its shafts are. Unhitched, it goes back onto the player's lead. A llama, or a foal, is not
	 * hitched.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_carriage_hitch", tickLimit = 60)
	public void hitchesALedHorse(TestContext context) {
		floor(context);
		CarriageEntity carriage = carriage(context, 4.0, 1.5);
		HorseEntity horse = horse(context, 6.0, 5.0);
		ServerPlayerEntity player = player(context, "carriage_hitcher");
		Vec3d stand = context.getAbsolute(new Vec3d(6.0, 1.0, 3.0));
		player.refreshPositionAndAngles(stand.x, stand.y, stand.z, 0.0F, 0.0F);
		horse.attachLeash(player, true);
		carriage.interactAt(player, new Vec3d(0.0, 1.5, 1.6), Hand.MAIN_HAND);
		context.assertTrue(CarriageEntity.hitchedTo(horse) == carriage, "the led horse was not hitched");
		context.assertTrue(!horse.isLeashed(), "the hitched horse is still on its lead");
		context.assertTrue(player.getInventory().count(Items.LEAD) == 1, "the lead did not go back to the player");
		LlamaEntity llama = context.spawnEntity(EntityType.LLAMA, new Vec3d(2.0, 1.0, 6.0));
		HorseEntity foal = horse(context, 2.0, 4.0);
		foal.setBaby(true);
		context.assertTrue(!CarriageEntity.canPull(llama), "a llama could be put in her shafts");
		context.assertTrue(!CarriageEntity.canPull(foal), "a foal could be put in her shafts");
		context.runAtTick(5, () -> {
			Vec3d shafts = carriage.toWorld(new Vec3d(0.0, 0.0, CarriageEntity.HORSE_AHEAD));
			context.assertTrue(Math.hypot(horse.getX() - shafts.x, horse.getZ() - shafts.z) < 0.3,
					"the horse is not in her shafts: " + horse.getPos().subtract(carriage.getPos()));
			carriage.unhitch(player);
			context.assertTrue(CarriageEntity.hitchedTo(horse) == null, "unhitched, the horse is still hitched");
			context.assertTrue(horse.getLeashHolder() == player, "unhitched, the horse did not go back onto the player's lead");
			done(context, carriage, player);
		});
	}

	/** Set going, she goes ahead with her horse in her shafts, its legs going. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_carriage_pull", tickLimit = 60)
	public void pulls(TestContext context) {
		floor(context);
		CarriageEntity carriage = carriage(context, 4.0, 1.0);
		HorseEntity horse = horse(context, 4.0, 4.0);
		context.assertTrue(carriage.hitch(horse, null), "the horse could not be hitched");
		Vec3d start = carriage.getPos();
		context.runAtTick(2, () -> carriage.launch(0.2F));
		context.runAtTick(8, () -> {
			Vec3d went = carriage.getPos().subtract(start);
			context.assertTrue(went.z > 0.5, "she did not go ahead: she went " + went);
			Vec3d shafts = carriage.toWorld(new Vec3d(0.0, 0.0, CarriageEntity.HORSE_AHEAD));
			context.assertTrue(Math.hypot(horse.getX() - shafts.x, horse.getZ() - shafts.z) < 0.4,
					"her horse was left behind: " + horse.getPos().subtract(carriage.getPos()));
			context.assertTrue(horse.limbAnimator.getSpeed() > 0.1F, "her horse's legs did not go: " + horse.limbAnimator.getSpeed());
			done(context, carriage);
		});
	}

	/**
	 * With the door open, a husk led in and a player shoved in go in the cage. Shut, nobody inside gets out, nor hurts
	 * anyone outside through the bars; opened again, a player can get out and the husk makes a run for it.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_carriage_cage", tickLimit = 120)
	public void cageKeepsThemIn(TestContext context) {
		floor(context);
		CarriageEntity carriage = carriage(context, 4.0, 4.5);
		ServerPlayerEntity catcher = player(context, "carriage_catcher");
		ServerPlayerEntity child = player(context, "carriage_child");
		Vec3d door = carriage.toWorld(CarriageEntity.DOOR_OUT);
		child.refreshPositionAndAngles(door.x, door.y, door.z, 0.0F, 0.0F);
		ZombieEntity husk = husk(context, new Vec3d(2.0, 1.0, 2.0));
		ZombieEntity outside = husk(context, new Vec3d(6.5, 1.0, 1.5));
		context.assertTrue(!carriage.shove(catcher, child), "shoved in with the door shut");
		carriage.setDoor(true, catcher);
		context.assertTrue(carriage.isDoorOpen(), "the door did not open");
		context.assertTrue(carriage.shove(catcher, child), "standing at the open door, the player was not shoved in");
		context.assertTrue(carriage.putInCage(husk), "the husk could not be put in");
		context.assertTrue(carriage.inCage(child) && carriage.inCage(husk), "they are not in the cage");
		carriage.setDoor(false, catcher);
		context.assertTrue(!carriage.letsOut(child), "the door shut, the player could get out");
		context.assertTrue(!outside.damage(context.getWorld().getDamageSources().mobAttack(husk), 2.0F),
				"from in the cage, the husk hurt someone outside it");
		carriage.setDoor(true, catcher);
		context.assertTrue(carriage.letsOut(child), "the door open, the player could not get out");
		context.runAtTick(80, () -> {
			context.assertTrue(!husk.hasVehicle(), "the door open, the husk did not get out");
			context.assertTrue(husk.getPos().distanceTo(door) < 1.5, "the husk did not get out by the door: "
					+ husk.getPos().subtract(carriage.getPos()));
			done(context, carriage, catcher, child);
		});
	}

	/**
	 * Dressed as a sweet cart with her door open, she draws a child at her door in; the driver's whip throws the
	 * disguise off.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_carriage_disguise", tickLimit = 80)
	public void sweetCart(TestContext context) {
		floor(context);
		CarriageEntity carriage = carriage(context, 4.0, 3.5);
		HorseEntity horse = horse(context, 4.0, 6.0);
		carriage.hitch(horse, null);
		ServerPlayerEntity driver = player(context, "carriage_driver");
		context.assertTrue(carriage.seat(driver, CarriageEntity.DRIVER), "the driver could not get up on the box");
		context.assertTrue(carriage.getControllingPassenger() == driver, "the driver does not have the reins");
		carriage.toggleDisguise(driver);
		context.assertTrue(carriage.isDisguised(), "the disguise did not go up");
		carriage.setDoor(true, driver);
		VillagerEntity child = context.spawnEntity(EntityType.VILLAGER, context.getRelative(carriage.toWorld(CarriageEntity.DOOR_OUT)));
		child.setBaby(true);
		child.setAiDisabled(true);
		context.runAtTick(45, () -> {
			context.assertTrue(child.getVehicle() == carriage && carriage.inCage(child), "the child at the door did not climb in");
			carriage.crackWhip(driver);
			context.assertTrue(!carriage.isDisguised(), "the whip did not throw the disguise off");
			done(context, carriage, driver);
		});
	}

	private static ServerPlayerEntity player(TestContext context, String name) {
		ServerWorld world = context.getWorld();
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		return player;
	}
}
