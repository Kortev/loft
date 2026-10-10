package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chitty.carriage.Carriage;
import io.github.kortev.chitty.carriage.CarriageEntity;
import io.github.kortev.chitty.carriage.CarriagePartEntity;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * The Child Catcher's carriage with nobody driving her: the server moves her. She is longer than a test's box with her
 * horse ahead of her, so each test runs in a batch of its own, and takes her away at its end. Prisoners are husks,
 * which the sun cannot hurt, players and villagers.
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

	private static ZombieEntity husk(TestContext context, Vec3d at) {
		ZombieEntity husk = context.spawnEntity(EntityType.HUSK, at);
		husk.setAiDisabled(true);
		return husk;
	}

	private static void done(TestContext context, CarriageEntity carriage, ServerPlayerEntity... players) {
		// Everyone off first: a player leaving the game while aboard takes the vehicle with them.
		carriage.removeAllPassengers();
		for (ServerPlayerEntity player : players) {
			context.getWorld().getServer().getPlayerManager().remove(player);
		}
		carriage.discard();
		context.complete();
	}

	/**
	 * Set going, she goes ahead, and her horse's hitbox with her, ahead of her in her shafts. Hitting the horse hits her.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_carriage_pull", tickLimit = 60)
	public void pulls(TestContext context) {
		floor(context);
		CarriageEntity carriage = carriage(context, 4.0, 1.0);
		Vec3d start = carriage.getPos();
		context.runAtTick(2, () -> carriage.launch(0.2F));
		context.runAtTick(8, () -> {
			Vec3d went = carriage.getPos().subtract(start);
			context.assertTrue(went.z > 0.5, "she did not go ahead: she went " + went);
			CarriagePartEntity horse = null;
			for (CarriagePartEntity part : context.getWorld().getEntitiesByClass(CarriagePartEntity.class,
					carriage.getBoundingBox().expand(6.0), p -> p.getCarriage() == carriage && p.getPart() == CarriagePartEntity.HORSE)) {
				horse = part;
			}
			context.assertTrue(horse != null, "her horse has no hitbox");
			Vec3d shafts = carriage.toWorld(new Vec3d(0.0, 0.0, CarriageEntity.HORSE_AHEAD));
			context.assertTrue(Math.hypot(horse.getX() - shafts.x, horse.getZ() - shafts.z) < 0.4,
					"her horse's hitbox was left behind: " + horse.getPos().subtract(carriage.getPos()));
			ZombieEntity hitter = husk(context, new Vec3d(1.0, 1.0, 6.0));
			horse.damage(context.getWorld().getDamageSources().mobAttack(hitter), 2.0F);
			context.assertTrue(carriage.getDamageWobbleTicks() > 0, "hitting her horse did not hit her");
			done(context, carriage);
		});
	}

	/**
	 * With the door open, a husk led in and a player shoved in go in the cage. Shut, nobody inside gets out (not by
	 * sneaking, nor anything that would take them off her, nor getting on something else), nor hurts anyone outside
	 * through the bars, up on her box included, nor breaks her; opened again, a player can get out and the husk makes a
	 * run for it.
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
		child.stopRiding();
		context.assertTrue(child.getVehicle() == carriage && carriage.inCage(child), "the door shut, the player was taken out");
		BoatEntity boat = context.spawnEntity(EntityType.BOAT, new Vec3d(7.0, 1.0, 7.0));
		context.assertTrue(!child.startRiding(boat, true) && child.getVehicle() == carriage, "the door shut, the player got in a boat");
		boat.discard();
		context.assertTrue(!outside.damage(context.getWorld().getDamageSources().mobAttack(husk), 2.0F),
				"from in the cage, the husk hurt someone outside it");
		ZombieEntity driver = husk(context, new Vec3d(4.0, 1.0, 6.0));
		context.assertTrue(carriage.seat(driver, CarriageEntity.BOX), "a husk could not be sat up on the box");
		context.assertTrue(!driver.damage(context.getWorld().getDamageSources().mobAttack(husk), 2.0F),
				"from in the cage, the husk hurt someone up on her box");
		context.assertTrue(!carriage.damage(context.getWorld().getDamageSources().mobAttack(husk), 2.0F),
				"from in the cage, the husk hit her");
		driver.stopRiding();
		driver.discard();
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
	 * Climbing in at the open door, sneaking, a player stays in (they are not straight back out for holding sneak); let go
	 * of and pressed again, sneak gets them out.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_carriage_climb", tickLimit = 60)
	public void climbIn(TestContext context) {
		floor(context);
		CarriageEntity carriage = carriage(context, 4.0, 4.5);
		ServerPlayerEntity child = player(context, "carriage_climber");
		Vec3d door = carriage.toWorld(CarriageEntity.DOOR_OUT);
		child.refreshPositionAndAngles(door.x, door.y, door.z, 0.0F, 0.0F);
		carriage.setDoor(true, child);
		child.setSneaking(true);
		Vec3d back = new Vec3d(0.0, 1.3, CarriageEntity.CAGE_BACK - 0.2).rotateY(carriage.getYaw() * MathHelper.RADIANS_PER_DEGREE);
		carriage.interactAt(child, back, Hand.MAIN_HAND);
		context.assertTrue(child.getVehicle() == carriage && carriage.inCage(child), "sneaking at the open door, the player did not climb in");
		context.runAtTick(5, () -> {
			context.assertTrue(child.getVehicle() == carriage, "still holding sneak from climbing in, the player fell straight back out");
			child.setSneaking(false);
		});
		context.runAtTick(8, () -> child.setSneaking(true));
		context.runAtTick(12, () -> {
			context.assertTrue(child.getVehicle() == null, "sneak pressed again, with the door open, the player did not get out");
			done(context, carriage, child);
		});
	}

	/**
	 * Dressed as a trader's wagon, with bait set out on her counter by the driver: another player reaching for it is
	 * caught, the door slamming on them (and hidden in her cage); the driver takes the rest back. The whip throws the
	 * disguise off.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_carriage_bait", tickLimit = 60)
	public void baitTrap(TestContext context) {
		floor(context);
		CarriageEntity carriage = carriage(context, 4.0, 3.5);
		ServerPlayerEntity driver = player(context, "carriage_driver");
		ServerPlayerEntity mark = player(context, "carriage_mark");
		driver.changeGameMode(GameMode.SURVIVAL);
		mark.changeGameMode(GameMode.SURVIVAL);
		Vec3d door = carriage.toWorld(CarriageEntity.DOOR_OUT);
		mark.refreshPositionAndAngles(door.x, door.y, door.z, 0.0F, 0.0F);
		context.assertTrue(carriage.seat(driver, CarriageEntity.DRIVER), "the driver could not get up on the box");
		carriage.toggleDisguise(driver);
		context.assertTrue(carriage.isDisguised(), "the disguise did not go up");
		context.assertTrue(carriage.setBait(driver, new ItemStack(Items.EMERALD, 3)), "the bait could not be set out");
		context.assertTrue(carriage.setBait(driver, new ItemStack(Items.DIAMOND)), "a second bait could not be set out");
		context.assertTrue(carriage.getBait(0).isOf(Items.EMERALD) && carriage.getBait(0).getCount() == 1,
				"the bait is not one emerald: " + carriage.getBait(0));
		// Saved and loaded, bait keeps its places: two of the same are two pieces, not one stack.
		CarriageEntity saved = Carriage.ENTITY.create(context.getWorld());
		saved.setBait(driver, new ItemStack(Items.BREAD, 2));
		saved.setBait(driver, new ItemStack(Items.BREAD, 2));
		CarriageEntity loaded = Carriage.ENTITY.create(context.getWorld());
		loaded.readNbt(saved.writeNbt(new NbtCompound()));
		context.assertTrue(loaded.getBait(0).getCount() == 1 && loaded.getBait(1).getCount() == 1 && loaded.getBait(1).isOf(Items.BREAD),
				"saved and loaded, the bait was not as it was: " + loaded.getBait(0) + ", " + loaded.getBait(1));
		Vec3d counter = new Vec3d(0.0, 1.3, CarriageEntity.CAGE_BACK - 0.2).rotateY(carriage.getYaw() * MathHelper.RADIANS_PER_DEGREE);
		carriage.interactAt(mark, counter, Hand.MAIN_HAND);
		context.assertTrue(mark.getVehicle() == carriage && carriage.inCage(mark), "reaching for the bait, the player was not caught");
		context.assertTrue(!carriage.isDoorOpen(), "the door did not slam on them");
		context.assertTrue(CarriageEntity.hidden(mark), "in the disguised cage, the player is not hidden");
		context.assertTrue(!carriage.letsOut(mark), "the caught player could get out");
		// Down off the box (a rider cannot reach his own carriage's counter), the driver takes the diamond back.
		driver.stopRiding();
		carriage.interactAt(driver, counter, Hand.MAIN_HAND);
		context.assertTrue(driver.getVehicle() == null && driver.getInventory().count(Items.DIAMOND) == 1,
				"the driver did not take the diamond back, nor was left free");
		carriage.crackWhip(driver);
		context.assertTrue(!carriage.isDisguised(), "the whip did not throw the disguise off");
		context.assertTrue(!carriage.hasBait(), "the bait stayed on the counter with the disguise gone");
		done(context, carriage, driver, mark);
	}

	/** Bread on her counter brings a villager at her back in: caught, the door slamming on it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_carriage_lure", tickLimit = 80)
	public void villagersComeForBread(TestContext context) {
		floor(context);
		CarriageEntity carriage = carriage(context, 4.0, 3.5);
		ServerPlayerEntity driver = player(context, "carriage_baker");
		context.assertTrue(carriage.seat(driver, CarriageEntity.DRIVER), "the driver could not get up on the box");
		carriage.toggleDisguise(driver);
		carriage.setBait(driver, new ItemStack(Items.BREAD));
		VillagerEntity villager = context.spawnEntity(EntityType.VILLAGER, context.getRelative(carriage.toWorld(CarriageEntity.DOOR_OUT)));
		villager.setAiDisabled(true);
		context.runAtTick(45, () -> {
			context.assertTrue(villager.getVehicle() == carriage && carriage.inCage(villager), "the villager at the counter was not caught");
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
