package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.chitty.Chitty;
import io.github.kortev.shootingstar.chitty.ChittyEntity;
import io.github.kortev.shootingstar.chitty.ChittyHamperEntity;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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

	/** A stone pool four blocks deep. */
	private static void pool(TestContext context) {
		ServerWorld world = context.getWorld();
		floor(context, 0);
		for (BlockPos pos : BlockPos.iterate(context.getAbsolutePos(new BlockPos(0, 1, 0)), context.getAbsolutePos(new BlockPos(7, 4, 7)))) {
			boolean wall = pos.getX() == context.getAbsolutePos(BlockPos.ORIGIN).getX()
					|| pos.getZ() == context.getAbsolutePos(BlockPos.ORIGIN).getZ()
					|| pos.getX() == context.getAbsolutePos(new BlockPos(7, 0, 0)).getX()
					|| pos.getZ() == context.getAbsolutePos(new BlockPos(0, 0, 7)).getZ();
			world.setBlockState(pos, wall ? Blocks.STONE.getDefaultState() : Blocks.WATER.getDefaultState());
		}
	}

	private static ChittyEntity car(TestContext context, double x, double y, double z) {
		ChittyEntity car = context.spawnEntity(Chitty.ENTITY, new Vec3d(x, y, z));
		car.setYaw(0.0F);
		car.prevYaw = 0.0F;
		return car;
	}

	/**
	 * Dropped into a pool with nobody aboard she settles into the water, and after a little while blows up her raft by
	 * herself and floats on it, the bottom of her box about a third of a block under.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_chitty", tickLimit = 160)
	public void floats(TestContext context) {
		pool(context);
		ChittyEntity car = car(context, 4.0, 6.5, 4.0);
		context.runAtTick(40, () -> {
			context.assertTrue(car.getFluidHeight(FluidTags.WATER) > 0.05, "she never reached the water");
			context.assertTrue(!car.isFloating(), "her raft came up at once");
		});
		context.runAtTick(140, () -> {
			double depth = car.getFluidHeight(FluidTags.WATER);
			context.assertTrue(car.isFloating(), "she never blew up her raft");
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
		// A seated zombie's head is over three blocks above the car: start low enough to keep it inside the test's box.
		ChittyEntity car = car(context, 4.0, 5.0, 4.0);
		car.launch(0.0F, true);
		ZombieEntity rider = context.spawnEntity(EntityType.HUSK, new Vec3d(4.0, 5.0, 4.0));
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

	/**
	 * Settling into deep water with someone aboard, the water closes over them and washes them out of the seat; left to
	 * herself, she blows up her raft and comes up.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_chitty", tickLimit = 200)
	public void washesOut(TestContext context) {
		pool(context);
		ChittyEntity car = car(context, 4.0, 6.5, 4.0);
		ZombieEntity rider = context.spawnEntity(EntityType.HUSK, new Vec3d(4.0, 6.5, 4.0));
		rider.setAiDisabled(true);
		context.assertTrue(rider.startRiding(car), "the zombie could not get in");
		boolean[] washed = {false};
		context.runAtEveryTick(() -> washed[0] |= !car.isFloating() && rider.getVehicle() != car);
		context.runAtTick(180, () -> {
			context.assertTrue(washed[0], "the water never washed the rider out (riding " + (rider.getVehicle() == car) + ")");
			context.assertTrue(car.isFloating(), "she never blew up her raft");
			double depth = car.getFluidHeight(FluidTags.WATER);
			context.assertTrue(depth > 0.1 && depth < 0.6, "she floats " + depth + " deep, not about 0.3");
			context.complete();
		});
	}

	/** Falling with someone aboard, she opens her wings by herself before she hits, then lands and folds them. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_chitty", tickLimit = 160)
	public void catchesHerself(TestContext context) {
		floor(context, 0);
		ChittyEntity car = car(context, 4.0, 5.0, 4.0);
		ZombieEntity rider = context.spawnEntity(EntityType.HUSK, new Vec3d(4.0, 5.0, 4.0));
		rider.setAiDisabled(true);
		context.assertTrue(rider.startRiding(car), "the zombie could not get in");
		// As if she had already fallen off a cliff: the box is too small to drop her far enough.
		car.fallDistance = 12.0F;
		boolean[] caught = {false};
		context.runAtEveryTick(() -> caught[0] |= car.isFlying());
		context.runAtTick(30, () -> context.assertTrue(caught[0], "her wings never opened: y " + car.getY()));
		context.runAtTick(140, () -> {
			context.assertTrue(car.isOnGround(), "she never landed: y " + car.getY());
			context.assertTrue(!car.isFlying(), "her wings are still out");
			context.complete();
		});
	}

	/** Her wings opened by hand stay out standing on the ground, and go away when asked; the raft never comes up on land. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_chitty", tickLimit = 120)
	public void heldOpen(TestContext context) {
		floor(context, 0);
		ChittyEntity car = car(context, 4.0, 1.0, 4.0);
		context.runAtTick(5, car::toggleWings);
		context.runAtTick(70, () -> {
			context.assertTrue(car.isOnGround(), "she is not standing on the ground");
			context.assertTrue(car.isFlying(), "her wings folded by themselves");
			context.assertTrue(!car.isFloating(), "her raft came up on dry land");
			car.toggleWings();
		});
		context.runAtTick(80, () -> {
			context.assertTrue(!car.isFlying(), "they did not go away when asked");
			context.complete();
		});
	}

	/** Afloat on her raft, running at the bank, she climbs out onto the land and lets the raft down. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_chitty", tickLimit = 160)
	public void climbsOutOfWater(TestContext context) {
		ServerWorld world = context.getWorld();
		floor(context, 0);
		// Water two deep across the near half, a bank level with the water's top across the far half.
		for (BlockPos pos : BlockPos.iterate(context.getAbsolutePos(new BlockPos(0, 1, 0)), context.getAbsolutePos(new BlockPos(7, 2, 7)))) {
			int z = pos.getZ() - context.getAbsolutePos(BlockPos.ORIGIN).getZ();
			int x = pos.getX() - context.getAbsolutePos(BlockPos.ORIGIN).getX();
			boolean wall = x == 0 || x == 7 || z == 0;
			world.setBlockState(pos, wall || z >= 4 ? Blocks.STONE.getDefaultState() : Blocks.WATER.getDefaultState());
		}
		ChittyEntity car = car(context, 4.0, 2.7, 1.6);
		car.blowUpRaft();
		context.runAtTick(10, () -> car.launch(0.32F, false));
		context.runAtTick(70, () -> {
			context.assertTrue(car.getFluidHeight(FluidTags.WATER) < 0.05, "she is still in the water at " + car.getPos());
			context.assertTrue(car.getY() >= context.getAbsolutePos(new BlockPos(0, 3, 0)).getY() - 0.05, "she never got up the bank: y "
					+ car.getY());
		});
		context.runAtTick(130, () -> {
			context.assertTrue(!car.isFloating(), "her raft stayed up on land");
			context.complete();
		});
	}

	/** The ejector fires the back seat's passengers up out of the car; the front seats stay put. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_chitty", tickLimit = 60)
	public void ejects(TestContext context) {
		floor(context, 0);
		ChittyEntity car = car(context, 4.0, 1.0, 4.0);
		List<ZombieEntity> riders = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			ZombieEntity zombie = context.spawnEntity(EntityType.HUSK, new Vec3d(1.0 + i, 1.0, 1.0));
			// The back seat's riders keep their AI: a mob without it never moves, so it could not be thrown.
			zombie.setAiDisabled(i < 2);
			context.assertTrue(car.seat(zombie, i), "could not seat a passenger in seat " + i);
			riders.add(zombie);
		}
		double start = car.getY();
		context.runAtTick(5, car::ejectBackSeat);
		context.runAtTick(12, () -> {
			context.assertTrue(riders.get(0).getVehicle() == car && riders.get(1).getVehicle() == car, "a front seat went too");
			for (int i = 2; i < 4; i++) {
				ZombieEntity back = riders.get(i);
				context.assertTrue(back.getVehicle() == null, "seat " + i + " was not ejected");
				context.assertTrue(back.getY() > start + 2.0, "seat " + i + " did not go up: y " + (back.getY() - start));
			}
			context.complete();
		});
	}

	/** A passenger can be put in any free seat: the back seat is behind, the passenger's beside the driver's. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_chitty", tickLimit = 40)
	public void chooseSeat(TestContext context) {
		floor(context, 0);
		ChittyEntity car = car(context, 4.0, 1.0, 4.0);
		ZombieEntity back = context.spawnEntity(EntityType.HUSK, new Vec3d(2.0, 1.0, 1.0));
		ZombieEntity beside = context.spawnEntity(EntityType.HUSK, new Vec3d(3.0, 1.0, 1.0));
		back.setAiDisabled(true);
		beside.setAiDisabled(true);
		context.assertTrue(car.seat(back, 3), "could not take the back seat");
		context.assertTrue(car.seat(beside, 1), "could not take the seat beside the driver");
		context.assertTrue(!car.seat(beside, 3), "two in one seat");
		context.runAtTick(5, () -> {
			context.assertTrue(car.seatOf(back) == 3 && car.seatOf(beside) == 1, "seats " + car.seatOf(back) + ", " + car.seatOf(beside));
			context.assertTrue(back.getZ() < car.getZ() - 0.5, "the back seat is not behind");
			context.assertTrue(beside.getX() > car.getX() + 0.2, "the passenger's seat is not on her left");
			context.assertTrue(car.getControllingPassenger() == null, "a passenger is driving");
			context.complete();
		});
	}

	/**
	 * The hamper has its own hitbox out on her stern (hers is too short to reach it); it holds things; taken off, it
	 * spills them out behind her and its hitbox goes; it goes back on.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_chitty", tickLimit = 40)
	public void hamper(TestContext context) {
		floor(context, 0);
		ChittyEntity car = car(context, 4.0, 1.0, 4.0);
		context.assertTrue(car.hasHamper(), "she came without her hamper");
		car.getHamper().setStack(0, new ItemStack(Items.BREAD, 5));
		context.runAtTick(3, () -> {
			List<ChittyHamperEntity> boxes = hamperBoxes(context, car);
			context.assertTrue(boxes.size() == 1, boxes.size() + " hamper hitboxes");
			double behind = car.getZ() - boxes.get(0).getZ();
			context.assertTrue(behind > 2.5 && behind < 3.5, "the hamper hitbox is not on her stern: " + behind + " behind");
			car.toggleHamper();
			context.assertTrue(!car.hasHamper(), "the hamper did not come off");
			context.assertTrue(car.getHamper().isEmpty(), "the hamper kept its bread");
		});
		context.runAtTick(8, () -> {
			context.assertTrue(hamperBoxes(context, car).isEmpty(), "the hamper hitbox stayed without the hamper");
			context.expectItemAt(Items.BREAD, new BlockPos(4, 1, 1), 2.5);
			car.toggleHamper();
			context.assertTrue(car.hasHamper(), "the hamper did not go back on");
			context.complete();
		});
	}

	private static List<ChittyHamperEntity> hamperBoxes(TestContext context, ChittyEntity car) {
		return context.getWorld().getEntitiesByType(Chitty.HAMPER, car.getBoundingBox().expand(5.0), box -> box.getCar() == car);
	}

	/** Dropped without her wings, she lands hard (four blocks: enough to hurt) but nobody aboard takes fall damage. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_chitty", tickLimit = 120)
	public void noFallDamage(TestContext context) {
		floor(context, 0);
		ChittyEntity car = car(context, 4.0, 5.0, 4.0);
		ZombieEntity rider = context.spawnEntity(EntityType.HUSK, new Vec3d(4.0, 5.0, 4.0));
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

	/** Four seats, mobs filling the back first and the driver's (on her right) last; a fifth passenger is turned away. */
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
			// Mobs fill the back seat first and the driver's seat last.
			context.assertTrue(car.seatOf(riders.get(0)) == 2 && car.seatOf(riders.get(1)) == 3, "the back seat did not fill first: "
					+ car.seatOf(riders.get(0)) + ", " + car.seatOf(riders.get(1)));
			context.assertTrue(car.seatOf(riders.get(3)) == 0, "the driver's seat was not last: " + car.seatOf(riders.get(3)));
			// At yaw 0 she faces +z, so her right is -x.
			ZombieEntity back = riders.get(0);
			context.assertTrue(back.getZ() < car.getZ() - 0.5, "the back seat is not behind: " + (back.getZ() - car.getZ()));
			ZombieEntity driver = riders.get(3);
			context.assertTrue(driver.getX() < car.getX() - 0.2, "the driver's seat is not on her right: " + (driver.getX() - car.getX()));
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
