package io.github.kortev.shootingstar.test;

import com.mojang.authlib.GameProfile;
import io.github.kortev.chitty.airship.Airship;
import io.github.kortev.chitty.airship.AirshipEntity;
import io.github.kortev.chitty.airship.AirshipHookEntity;
import io.github.kortev.chitty.airship.AirshipPartEntity;
import io.github.kortev.shootingstar.OwnerOnly;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The airship with nobody piloting her: the server moves her. She is far bigger than a test's box, so each test runs
 * in a batch of its own (her envelope's hitboxes would reach into a neighbour's), and takes her away at its end.
 * Riders are husks, which the sun cannot hurt.
 */
public class AirshipGameTests implements FabricGameTest {
	private static void floor(TestContext context, int y) {
		ServerWorld world = context.getWorld();
		for (BlockPos pos : BlockPos.iterate(context.getAbsolutePos(new BlockPos(0, y, 0)), context.getAbsolutePos(new BlockPos(7, y, 7)))) {
			world.setBlockState(pos, Blocks.STONE.getDefaultState());
		}
	}

	private static AirshipEntity ship(TestContext context, double x, double y, double z) {
		AirshipEntity ship = context.spawnEntity(Airship.ENTITY, new Vec3d(x, y, z));
		ship.setYaw(0.0F);
		ship.prevYaw = 0.0F;
		return ship;
	}

	private static ZombieEntity husk(TestContext context, double x, double y, double z) {
		ZombieEntity husk = context.spawnEntity(EntityType.HUSK, new Vec3d(x, y, z));
		husk.setAiDisabled(true);
		return husk;
	}

	private static void done(TestContext context, AirshipEntity ship) {
		ship.removeAllPassengers();
		ship.discard();
		context.complete();
	}

	/** Left to herself with nobody aboard, she hovers where she is: neither falls nor drifts up. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_airship_hover", tickLimit = 120)
	public void hovers(TestContext context) {
		floor(context, 0);
		AirshipEntity ship = ship(context, 4.0, 4.0, 4.0);
		double y = ship.getY();
		context.runAtTick(80, () -> {
			context.assertTrue(Math.abs(ship.getY() - y) < 0.2, "she did not hold her height: from " + y + " to " + ship.getY());
			done(context, ship);
		});
	}

	/**
	 * Set going with nobody at the wheel, she flies on ahead, slowing as she goes. (Her gondola is kept under the test
	 * area's barrier roof: in it, she cannot move.)
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_airship_fly", tickLimit = 40)
	public void fliesAhead(TestContext context) {
		floor(context, 0);
		AirshipEntity ship = ship(context, 4.0, 5.0, 1.0);
		Vec3d start = ship.getPos();
		context.runAtTick(2, () -> ship.launch(0.3F));
		context.runAtTick(12, () -> {
			Vec3d went = ship.getPos().subtract(start);
			context.assertTrue(went.z > 1.5, "she did not fly ahead: she went " + went + ", at " + ship.getSpeed() + " a tick");
			done(context, ship);
		});
	}

	/** Eight can stand in her gondola, and no more. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_airship_eight", tickLimit = 60)
	public void standsEight(TestContext context) {
		floor(context, 0);
		AirshipEntity ship = ship(context, 4.0, 1.0, 4.0);
		for (int i = 0; i < AirshipEntity.PLACES.length; i++) {
			context.assertTrue(husk(context, 4.0, 1.0, 4.0).startRiding(ship), "rider " + (i + 1) + " could not board");
		}
		context.assertTrue(!husk(context, 4.0, 1.0, 4.0).startRiding(ship), "a ninth rider got aboard");
		context.runAtTick(5, () -> {
			// Each where nobody else is, standing on the floor of the gondola.
			List<Vec3d> stands = new ArrayList<>();
			for (var passenger : ship.getPassengerList()) {
				Vec3d stand = ship.standOf(passenger);
				context.assertTrue(stand != null, "a rider has nowhere to stand");
				for (Vec3d other : stands) {
					context.assertTrue(stand.distanceTo(other) > 0.3, "two riders stand in one place: " + stand);
				}
				stands.add(stand);
				context.assertTrue(Math.abs(passenger.getY() - (ship.getY() + 0.42)) < 0.05, "a rider is not standing on the floor: "
						+ (passenger.getY() - ship.getY()));
			}
			done(context, ship);
		});
	}

	/**
	 * Aboard, a player walks about the gondola but not out of it; walking up to the wheel takes it and sneaking lets it
	 * go. Sneaking again high in the air lets the rope ladder down, and once it is down they get off onto it.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_airship_walk", tickLimit = 80)
	public void walksToTheWheel(TestContext context) {
		floor(context, 0);
		// Under the test area's barrier roof (y 8), which her ladder would otherwise come down onto.
		AirshipEntity ship = ship(context, 4.0, 6.0, 4.0);
		ServerPlayerEntity walker = player(context, "airship_walker");
		context.assertTrue(ship.board(walker, 3), "the player could not board");
		context.assertTrue(ship.getControllingPassenger() == null, "the player had the wheel without walking to it");
		Vec3d start = ship.standOf(walker);
		ship.walk(walker, start.x - 0.1, start.z + 0.1);
		context.assertTrue(ship.standOf(walker).distanceTo(start) > 0.05, "the player could not walk about the gondola");
		ship.walk(walker, 3.0, start.z);
		context.assertTrue(Math.abs(ship.standOf(walker).x) <= AirshipEntity.FLOOR_HALF_WIDTH + 1.0E-6,
				"the player walked out through her side: " + ship.standOf(walker));
		for (int i = 0; i < 20 && ship.getControllingPassenger() == null; i++) {
			Vec3d at = ship.standOf(walker);
			Vec3d way = AirshipEntity.HELM_SPOT.subtract(at);
			Vec3d step = way.length() > 0.3 ? way.normalize().multiply(0.3) : way;
			ship.walk(walker, at.x + step.x, at.z + step.z);
		}
		context.assertTrue(ship.getControllingPassenger() == walker, "walking up to the wheel did not take it: " + ship.standOf(walker));
		context.assertTrue(!ship.letsGo(walker), "sneaking at the wheel took the pilot off her");
		context.assertTrue(ship.getControllingPassenger() == null, "sneaking at the wheel did not let go of it");
		context.runAtTick(3, () -> {
			context.assertTrue(!ship.letsGo(walker), "the player got off in mid-air with no ladder down");
			context.assertTrue(ship.isLadderDown(), "sneaking in mid-air did not let the ladder down");
		});
		context.runAtTick(40, () -> {
			context.assertTrue(ship.letsGo(walker), "with the ladder down the player could not get off onto it (ladder "
					+ ship.getLadder() + ", down " + ship.isLadderDown() + ", ship removed " + ship.isRemoved() + " at y "
					+ (ship.getY() - context.getAbsolute(Vec3d.ZERO).y) + ", player aboard " + (walker.getVehicle() == ship)
					+ ", at the wheel " + (ship.getControllingPassenger() == walker) + ", sneaking " + walker.isSneaking() + ")");
			// Off first: a player leaving the game while aboard takes the vehicle with them.
			walker.stopRiding();
			context.getWorld().getServer().getPlayerManager().remove(walker);
			done(context, ship);
		});
	}

	/** She lifts six: with six aboard she holds her height. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_airship_six", tickLimit = 140)
	public void liftsSix(TestContext context) {
		floor(context, 0);
		AirshipEntity ship = ship(context, 4.0, 5.0, 4.0);
		for (int i = 0; i < AirshipEntity.LIFT; i++) {
			husk(context, 4.0, 5.0, 4.0).startRiding(ship);
		}
		double y = ship.getY();
		context.runAtTick(100, () -> {
			context.assertTrue(!ship.isOverloaded(), "six overload her");
			context.assertTrue(Math.abs(ship.getY() - y) < 0.2, "with six aboard she did not hold her height: from " + y + " to " + ship.getY());
			done(context, ship);
		});
	}

	/** With seven aboard she is overloaded and sinks slowly, as in the film. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_airship_seven", tickLimit = 140)
	public void sinksOverloaded(TestContext context) {
		floor(context, 0);
		AirshipEntity ship = ship(context, 4.0, 5.0, 4.0);
		for (int i = 0; i < AirshipEntity.LIFT + 1; i++) {
			husk(context, 4.0, 5.0, 4.0).startRiding(ship);
		}
		double y = ship.getY();
		context.runAtTick(100, () -> {
			context.assertTrue(ship.isOverloaded(), "seven do not overload her");
			context.assertTrue(ship.getY() < y - 1.0, "overloaded, she did not sink: from " + y + " to " + ship.getY());
			context.assertTrue(ship.getY() > y - 4.5, "overloaded, she dropped like a stone: from " + y + " to " + ship.getY());
			done(context, ship);
		});
	}

	/** Let down onto a mob, her grapple seizes it and winds it up off the ground, and sets it down again when asked. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_airship_grapple", tickLimit = 260)
	public void grappleSeizes(TestContext context) {
		floor(context, 0);
		AirshipEntity ship = ship(context, 4.0, 9.0, 4.0);
		ZombieEntity husk = husk(context, 4.0, 1.0, 4.25);
		ship.lowerGrappleTo(1.0);
		boolean[] caught = {false};
		context.runAtEveryTick(() -> caught[0] |= husk.getVehicle() instanceof AirshipHookEntity);
		context.runAtTick(80, () -> {
			context.assertTrue(caught[0], "the grapple never caught the mob (grapple " + ship.getHookState() + " at " + ship.getHookDrop() + ")");
			context.assertTrue(husk.getVehicle() instanceof AirshipHookEntity, "the mob got off the grapple");
			context.assertTrue(husk.getY() > context.getAbsolute(new Vec3d(0, 1.5, 0)).y, "the mob was not lifted: y " + husk.getY());
			context.assertTrue(ship.getHookState() == AirshipEntity.Hook.HOLDING, "she is not holding it: " + ship.getHookState());
			ship.workGrapple();
		});
		context.runAtTick(220, () -> {
			context.assertTrue(husk.getVehicle() == null, "the grapple never let the mob go");
			context.assertTrue(husk.getY() < context.getAbsolute(new Vec3d(0, 1.5, 0)).y, "the mob was not set down: y " + husk.getY());
			context.assertTrue(ship.getHookState() == AirshipEntity.Hook.RAISING || ship.getHookState() == AirshipEntity.Hook.UP,
					"the grapple did not wind back up: " + ship.getHookState());
			husk.discard();
			done(context, ship);
		});
	}

	/**
	 * Someone on the ground takes hold of her grapple as it hangs there and hooks it onto a mob within reach: it takes
	 * hold, and she winds the mob up off the ground. (She and her grapple stay under the test area's barrier roof, y 8.)
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_airship_hold", tickLimit = 160)
	public void grappleHeldHooks(TestContext context) {
		floor(context, 0);
		AirshipEntity ship = ship(context, 4.0, 9.0, 4.0);
		ServerPlayerEntity holder = player(context, "grapple_holder");
		Vec3d stand = context.getAbsolute(new Vec3d(4.0, 1.0, 5.6));
		holder.requestTeleport(stand.x, stand.y, stand.z);
		ZombieEntity husk = husk(context, 6.0, 1.0, 5.6);
		ship.lowerGrappleTo(2.0);
		context.runAtTick(40, () -> {
			context.assertTrue(ship.getHookState() == AirshipEntity.Hook.DOWN, "the grapple did not come down to the ground: "
					+ ship.getHookState() + " at " + ship.getHookOffset());
			context.assertTrue(ship.takeHoldOfGrapple(holder), "the player could not take hold of the grapple");
			context.assertTrue(ship.getHookState() == AirshipEntity.Hook.HELD, "the grapple is not in the player's hands");
		});
		context.runAtTick(45, () -> context.assertTrue(ship.hookOnto(holder, husk), "the player could not hook the grapple onto the mob"));
		context.runAtTick(130, () -> {
			context.assertTrue(husk.getVehicle() instanceof AirshipHookEntity, "the hooked mob is not on the grapple");
			context.assertTrue(husk.getY() > context.getAbsolute(new Vec3d(0.0, 2.5, 0.0)).y, "the hooked mob was not wound up: y "
					+ (husk.getY() - context.getAbsolute(Vec3d.ZERO).y));
			husk.discard();
			context.getWorld().getServer().getPlayerManager().remove(holder);
			done(context, ship);
		});
	}

	/**
	 * Her grapple is a weight on a rope: hanging under her as she sets off, it swings back and trails behind her. (Under
	 * the test area's barrier roof, as in fliesAhead.)
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_airship_swing", tickLimit = 60)
	public void grappleSwings(TestContext context) {
		floor(context, 0);
		AirshipEntity ship = ship(context, 4.0, 6.0, 1.0);
		ship.hangGrappleAt(3.0);
		Vec3d start = ship.getPos();
		context.runAtTick(2, () -> ship.launch(0.3F));
		context.runAtTick(12, () -> {
			Vec3d at = ship.getHookOffset();
			context.assertTrue(at.z < -0.3, "the grapple did not trail behind her as she set off: " + at + " (she went "
					+ ship.getPos().subtract(start) + ", at " + ship.getSpeed() + " a tick)");
			context.assertTrue(at.length() <= 3.0 + 1.0E-3, "the grapple hangs further out than its rope: " + at.length());
			done(context, ship);
		});
	}

	/** A bomb dropped from her falls, strikes the ground and breaks it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_airship_bomb", tickLimit = 120)
	public void bombBreaksGround(TestContext context) {
		floor(context, 0);
		AirshipEntity ship = ship(context, 4.0, 9.0, 4.0);
		ship.loadBombs(2);
		context.runAtTick(5, () -> {
			ship.dropBomb(null);
			ship.dropBomb(null);
			context.assertTrue(ship.getBombs() == 1, "the second bomb fell before the first had cleared: " + ship.getBombs() + " left");
		});
		context.runAtTick(90, () -> {
			int holes = 0;
			for (BlockPos pos : BlockPos.iterate(context.getAbsolutePos(new BlockPos(1, 0, 1)), context.getAbsolutePos(new BlockPos(7, 0, 7)))) {
				if (context.getWorld().getBlockState(pos).isAir()) {
					holes++;
				}
			}
			context.assertTrue(holes > 0, "the bomb did not break the ground");
			done(context, ship);
		});
	}

	/** Her rope ladder lets down to the ground, and a mob at the foot of it is on it, as on a ladder, and climbs it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_airship_ladder", tickLimit = 100)
	public void ladderClimbs(TestContext context) {
		floor(context, 0);
		AirshipEntity ship = ship(context, 4.0, 6.0, 4.0);
		ship.toggleLadder();
		ZombieEntity husk = husk(context, 4.0 + AirshipEntity.LADDER_LINE.x, 1.0, 4.0 + AirshipEntity.LADDER_LINE.z);
		context.runAtTick(60, () -> {
			context.assertTrue(ship.getLadder() > 4.0, "the ladder did not come down to the ground: " + ship.getLadder());
			context.assertTrue(husk.isClimbing(), "a mob at the foot of the ladder is not on it");
			// Walking into it (facing her) climbs it, as a ladder against a wall, and goes no further into it.
			husk.setYaw(90.0F);
			husk.setBodyYaw(90.0F);
			husk.setMovementSpeed(0.25F);
			double x = husk.getX();
			Vec3d climb = husk.applyMovementInput(new Vec3d(0.0, 0.0, 1.0), 0.6F);
			context.assertTrue(climb.y > 0.15, "walking into the ladder did not climb it: " + climb);
			context.assertTrue(husk.getX() > x - 1.0E-3, "the mob walked on into the ladder: " + (husk.getX() - x));
			husk.discard();
			done(context, ship);
		});
	}

	/** She keeps hitboxes along her gondola, her envelope and her tail, and hitting one hits her. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_airship_boxes", tickLimit = 40)
	public void hitboxes(TestContext context) {
		floor(context, 0);
		AirshipEntity ship = ship(context, 4.0, 1.0, 4.0);
		context.runAtTick(5, () -> {
			List<AirshipPartEntity> parts = context.getWorld().getEntitiesByType(Airship.PART, ship.getBoundingBox().expand(30.0),
					p -> p.getShip() == ship);
			context.assertTrue(parts.size() == 9, "she has " + parts.size() + " hitboxes, not 9");
			AirshipPartEntity top = parts.stream().filter(p -> p.getY() > ship.getY() + 3.0).findFirst().orElse(null);
			context.assertTrue(top != null, "none of her hitboxes is up at her envelope");
			top.damage(context.getWorld().getDamageSources().generic(), 2.0F);
			context.assertTrue(ship.getDamageWobbleTicks() > 0, "hitting her envelope did not hit her");
			done(context, ship);
		});
	}

	/** Hit hard enough, she comes down and drops herself as an item. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_airship_break", tickLimit = 40)
	public void breaksIntoItem(TestContext context) {
		floor(context, 0);
		AirshipEntity ship = ship(context, 4.0, 1.0, 4.0);
		ServerWorld world = context.getWorld();
		ship.damage(world.getDamageSources().generic(), 4.0F);
		context.assertTrue(!ship.isRemoved(), "one light knock broke her");
		ship.damage(world.getDamageSources().generic(), 30.0F);
		context.assertTrue(ship.isRemoved(), "a heavy blow did not break her");
		context.runAtTick(5, () -> {
			context.expectItemAt(Airship.ITEM, new BlockPos(4, 1, 4), 3.0);
			context.complete();
		});
	}

	/** Only kortev can make her (and her bombs); anyone can still make ordinary things. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "c_airship_craft")
	public void onlyTheOwnerCrafts(TestContext context) {
		ServerPlayerEntity owner = player(context, OwnerOnly.OWNER);
		ServerPlayerEntity other = player(context, "someone_else");
		try {
			context.assertTrue(crafted(context, owner, airship()).isOf(Airship.ITEM), "kortev could not craft the airship");
			context.assertTrue(crafted(context, other, airship()).isEmpty(), "someone else crafted the airship");
			context.assertTrue(crafted(context, owner, bombs()).isOf(Airship.BOMB), "kortev could not craft bombs");
			context.assertTrue(crafted(context, owner, bombs()).getCount() == 2, "the recipe does not make two bombs");
			context.assertTrue(crafted(context, other, bombs()).isEmpty(), "someone else crafted bombs");
		} finally {
			context.getWorld().getServer().getPlayerManager().remove(owner);
			context.getWorld().getServer().getPlayerManager().remove(other);
		}
		context.complete();
	}

	private static ItemStack[] airship() {
		return new ItemStack[] {new ItemStack(Items.PURPLE_WOOL), new ItemStack(Items.WHITE_WOOL), new ItemStack(Items.PURPLE_WOOL),
				new ItemStack(Items.PHANTOM_MEMBRANE), new ItemStack(Items.CHAIN), new ItemStack(Items.PHANTOM_MEMBRANE),
				new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.OAK_BOAT), new ItemStack(Items.GOLD_INGOT)};
	}

	private static ItemStack[] bombs() {
		return new ItemStack[] {new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GUNPOWDER), ItemStack.EMPTY,
				new ItemStack(Items.GUNPOWDER), new ItemStack(Items.STRING), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
	}

	private static ItemStack crafted(TestContext context, ServerPlayerEntity player, ItemStack[] grid) {
		CraftingScreenHandler table = new CraftingScreenHandler(1, player.getInventory(),
				ScreenHandlerContext.create(context.getWorld(), context.getAbsolutePos(BlockPos.ORIGIN)));
		for (int i = 0; i < grid.length; i++) {
			table.getSlot(1 + i).setStack(grid[i]);
		}
		return table.getSlot(0).getStack();
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
