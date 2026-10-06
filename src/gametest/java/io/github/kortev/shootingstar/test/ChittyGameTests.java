package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.chitty.Chitty;
import io.github.kortev.shootingstar.chitty.ChittyEntity;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Chitty with nobody driving her: the server moves her, as it does a boat. Riders are husks, which the sun cannot hurt. */
public class ChittyGameTests implements FabricGameTest {
	/** Every hurt dealt to a test's riders, so a failure can say what it was and when. */
	private static final List<String> HURTS = new ArrayList<>();
	private static final List<java.util.UUID> WATCHED = new ArrayList<>();

	static {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (WATCHED.contains(entity.getUuid())) {
				HURTS.add(source.getName() + " " + amount + " at tick " + entity.getWorld().getTime() + " (y " + String.format("%.2f", entity.getY())
						+ ", riding " + (entity.getVehicle() != null) + ", fall " + entity.fallDistance + ")");
			}
			return true;
		});
	}

	private static void floor(TestContext context, int y) {
		ServerWorld world = context.getWorld();
		for (BlockPos pos : BlockPos.iterate(context.getAbsolutePos(new BlockPos(0, y, 0)), context.getAbsolutePos(new BlockPos(7, y, 7)))) {
			world.setBlockState(pos, Blocks.STONE.getDefaultState());
		}
	}

	private static ChittyEntity car(TestContext context, double x, double y, double z) {
		ChittyEntity car = context.spawnEntity(Chitty.ENTITY, new Vec3d(x, y, z));
		car.setYaw(0.0F);
		car.prevYaw = 0.0F;
		return car;
	}

	/** Dropped into a pool she floats, the bottom of her box about a third of a block under. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_chitty", tickLimit = 140)
	public void floats(TestContext context) {
		ServerWorld world = context.getWorld();
		floor(context, 0);
		for (BlockPos pos : BlockPos.iterate(context.getAbsolutePos(new BlockPos(0, 1, 0)), context.getAbsolutePos(new BlockPos(7, 4, 7)))) {
			boolean wall = pos.getX() == context.getAbsolutePos(BlockPos.ORIGIN).getX()
					|| pos.getZ() == context.getAbsolutePos(BlockPos.ORIGIN).getZ()
					|| pos.getX() == context.getAbsolutePos(new BlockPos(7, 0, 0)).getX()
					|| pos.getZ() == context.getAbsolutePos(new BlockPos(0, 0, 7)).getZ();
			world.setBlockState(pos, wall ? Blocks.STONE.getDefaultState() : Blocks.WATER.getDefaultState());
		}
		ChittyEntity car = car(context, 4.0, 6.5, 4.0);
		context.runAtTick(120, () -> {
			double depth = car.getFluidHeight(FluidTags.WATER);
			context.assertTrue(!car.isRemoved(), "the car sank or broke");
			context.assertTrue(depth > 0.1 && depth < 0.6, "she floats " + depth + " deep, not about 0.3");
			context.assertTrue(Math.abs(car.getVelocity().y) < 0.05, "still bobbing at " + car.getVelocity().y);
			context.assertTrue(!car.isOnGround(), "she sank to the bottom");
			context.complete();
		});
	}

	/** Flying with nobody at the wheel she glides down, lands, folds her wings, and her passenger is unhurt. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_chitty", tickLimit = 200)
	public void landsSafely(TestContext context) {
		floor(context, 0);
		ChittyEntity car = car(context, 4.0, 7.0, 4.0);
		car.launch(0.0F, true);
		ZombieEntity rider = context.spawnEntity(EntityType.HUSK, new Vec3d(4.0, 7.0, 4.0));
		rider.setAiDisabled(true);
		context.assertTrue(rider.startRiding(car), "the zombie could not get in");
		float health = rider.getHealth();
		context.runAtTick(160, () -> {
			context.assertTrue(car.isOnGround(), "she never landed: y " + car.getY());
			context.assertTrue(!car.isFlying(), "her wings are still out");
			context.assertTrue(rider.getHealth() >= health, "the passenger was hurt landing (" + rider.getHealth() + " of " + health + ") by "
					+ (rider.getRecentDamageSource() != null ? rider.getRecentDamageSource().getName() : "nothing recorded"));
			context.complete();
		});
	}

	/** Dropped without her wings, she lands hard but nobody aboard takes fall damage. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_chitty", tickLimit = 120)
	public void noFallDamage(TestContext context) {
		floor(context, 0);
		ChittyEntity car = car(context, 4.0, 7.5, 4.0);
		ZombieEntity rider = context.spawnEntity(EntityType.HUSK, new Vec3d(4.0, 7.5, 4.0));
		rider.setAiDisabled(true);
		rider.startRiding(car);
		WATCHED.add(rider.getUuid());
		float health = rider.getHealth();
		context.runAtTick(80, () -> {
			context.assertTrue(car.isOnGround(), "she never came down");
			List<String> hurts = new ArrayList<>(HURTS);
			context.assertTrue(rider.getHealth() >= health, "the passenger was hurt (" + rider.getHealth() + " of " + health + "): "
					+ hurts + ", riding " + rider.getVehicle());
			context.assertTrue(!car.isRemoved(), "the fall broke her");
			context.complete();
		});
	}

	/** Rolling at a wall, she stops with her bonnet short of it instead of driving her nose into it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_chitty", tickLimit = 120)
	public void stopsAtWall(TestContext context) {
		ServerWorld world = context.getWorld();
		floor(context, 0);
		for (BlockPos pos : BlockPos.iterate(context.getAbsolutePos(new BlockPos(0, 1, 7)), context.getAbsolutePos(new BlockPos(7, 3, 7)))) {
			world.setBlockState(pos, Blocks.STONE.getDefaultState());
		}
		ChittyEntity car = car(context, 4.0, 1.0, 1.5);
		car.launch(0.5F, false);
		double wall = context.getAbsolutePos(new BlockPos(0, 0, 7)).getZ();
		Vec3d start = car.getPos();
		context.runAtTick(100, () -> {
			// Facing south (+z): the front of her bonnet is 1.5 ahead of her middle.
			double front = car.getZ() + 1.5;
			context.assertTrue(car.getZ() > start.z + 1.0, "she never moved: from " + start + " to " + car.getPos());
			context.assertTrue(front <= wall + 0.3, "her nose went into the wall: front at " + front + ", wall at " + wall);
			context.complete();
		});
	}

	/** Four seats, the first (the driver's) on her right; a fifth passenger is turned away. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_chitty", tickLimit = 40)
	public void seatsFour(TestContext context) {
		floor(context, 0);
		ChittyEntity car = car(context, 4.0, 1.0, 4.0);
		List<ZombieEntity> riders = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			ZombieEntity zombie = context.spawnEntity(EntityType.HUSK, new Vec3d(1.0 + i, 1.0, 1.0));
			zombie.setAiDisabled(true);
			riders.add(zombie);
		}
		for (ZombieEntity zombie : riders) {
			zombie.startRiding(car);
		}
		context.runAtTick(5, () -> {
			context.assertTrue(car.getPassengerList().size() == 4, "seats " + car.getPassengerList().size());
			context.assertTrue(!riders.get(4).hasVehicle(), "a fifth got in");
			// At yaw 0 she faces +z, so her right is -x.
			ZombieEntity first = (ZombieEntity) car.getPassengerList().get(0);
			context.assertTrue(first.getX() < car.getX() - 0.2, "the first seat is not on her right: " + (first.getX() - car.getX()));
			ZombieEntity back = (ZombieEntity) car.getPassengerList().get(2);
			context.assertTrue(back.getZ() < car.getZ() - 0.5, "the back seat is not behind: " + (back.getZ() - car.getZ()));
			context.complete();
		});
	}

	/** Hit hard she comes apart and drops herself as an item. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_chitty", tickLimit = 40)
	public void breaksIntoItem(TestContext context) {
		floor(context, 0);
		ChittyEntity car = car(context, 4.0, 1.0, 4.0);
		ServerWorld world = context.getWorld();
		car.damage(world.getDamageSources().generic(), 4.0F);
		context.assertTrue(!car.isRemoved(), "one light knock broke her");
		car.damage(world.getDamageSources().generic(), 20.0F);
		context.assertTrue(car.isRemoved(), "a heavy blow did not break her");
		context.runAtTick(5, () -> {
			context.expectItemAt(Chitty.ITEM, new BlockPos(4, 1, 4), 3.0);
			context.complete();
		});
	}
}
