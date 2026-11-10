package io.github.kortev.chainsawman.hotel;

import io.github.kortev.chainsawman.ChainsawMan;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.StructureWorldAccess;

/**
 * The missing squad: our own hunters (not the show's), who went into the hotel and did not come out. Two or three of them are alive in the
 * loop when it closes ({@link SquadMemberEntity}: Yamada, Mori and Sato, one of them mad with fear), and their notes lie on lecterns on the bedside tables of the rooms below and one is in Yamada's pocket, one short line each (what happened, and what the devil is: hints, not answers). The ones who were fed and still stand when the loop
 * breaks walk to the exit and leave, and pay each player who fought the devil ({@link #reward}).
 */
public final class Squad {
	public static final String[] NAMES = {"Yamada", "Mori", "Sato"};
	/** Where they sit: local x along the loop floor's corridor, and which wall (true: the north wall, z 4). */
	private static final double[] SIT_X = {17.7, 19.6, 22.3};
	private static final boolean[] SIT_NORTH = {true, false, true};
	/** What each survivor is worth to each player inside when the loop breaks (for now experience and gold; yen and rank come with Public Safety). */
	public static final int REWARD_XP = 150;
	public static final int REWARD_GOLD = 4;

	/** One note: the floor (0 for the lobby floor to 6), and its words. It lies on the bedside table of one room on that floor. */
	public record Note(int floor, int x, String text) {
	}

	/** The notes of floors 1 to 7, one line each, from the squad's first day to their last: what happened, and a hint or two. */
	public static final List<Note> NOTES = List.of(
			new Note(0, 10, "Day 1. The doors locked behind us at dusk. Yamada says it is only the wind."),
			new Note(1, 10, "Day 2. Room 202 twice. We counted. Mori counts everything now."),
			new Note(2, 10, "Day 3. The painting follows us. Sato will not look at it."),
			new Note(3, 10, "Day 4. The carpet is stained to one door. We took the other way."),
			new Note(4, 10, "Day 5. It watches from the walls. The eyes shut when you look at them."),
			new Note(5, 10, "Day 6. The door breathes. It bleeds when you cut it. Do not cut unless you mean it."),
			new Note(6, 10, "Day 7. No more bread. The phone will not stop ringing."));
	/** The last note, from the loop floor: Yamada carries it, and drops it when he is fed. */
	public static final Note LAST = new Note(7, 0, "Day 9. The corridor goes on and we do not. The heartbeat is louder by room 805.");
	/** The back room (side 1, the second from the stairs) and where its bed's table is: x 10, z 12. */
	public static final int TABLE_Z = 12;

	private Squad() {
	}

	/** The lecterns with the notes, put down with the hotel (inside {@code clip} only, if given). */
	static void placeNotes(StructureWorldAccess world, BlockPos origin, int rot, BlockBox clip) {
		BlockRotation turn = HotelBuilder.turn(rot);
		for (Note n : NOTES) {
			BlockPos at = notePos(origin, rot, n);
			if (clip != null && !clip.contains(at)) {
				continue;
			}
			Direction facing = turn.rotate(Direction.NORTH);
			world.setBlockState(at, Blocks.LECTERN.getDefaultState().with(LecternBlock.FACING, facing).with(LecternBlock.HAS_BOOK, true), Block.NOTIFY_LISTENERS);
			if (world.getBlockEntity(at) instanceof LecternBlockEntity lectern) {
				lectern.setBook(book(n));
			}
		}
	}

	/** Where a note lies: on the bedside table (a slab) in a room, so it is never in the corridor. */
	public static BlockPos notePos(BlockPos origin, int rot, Note n) {
		return HotelBuilder.world(origin, rot, n.x(), HotelPlan.floorY(n.floor()) + 2, TABLE_Z);
	}

	public static ItemStack book(Note n) {
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(RawFilteredPair.of("The squad's note"), "Squad 4", 0,
				List.of(RawFilteredPair.of(Text.literal(n.text()))), true));
		return stack;
	}

	/** The loop has closed: the survivors, starving against the walls. */
	static void start(ServerWorld world, HomeBlockEntity h) {
		Hotel.Run r = h.run;
		end(world, h);
		int mad = world.random.nextInt(NAMES.length);
		for (int i = 0; i < NAMES.length; i++) {
			SquadMemberEntity m = new SquadMemberEntity(ChainsawMan.SQUAD_MEMBER, world);
			boolean north = SIT_NORTH[i];
			Vec3d at = HotelBuilder.world(h.origin, h.rot, SIT_X[i], 29.0, north ? 5.45 : 7.55);
			m.refreshPositionAndAngles(at.x, at.y, at.z, HotelBuilder.alongYaw(h.rot) + (north ? 90.0F : -90.0F), 0.0F);
			m.setCustomName(Text.literal(NAMES[i]));
			m.setCustomNameVisible(true);
			m.setVariant(i);
			m.setMad(i == mad);
			m.setHealth(i == mad ? m.getMaxHealth() : 9.0F);
			m.setState(SquadMemberEntity.HUNGRY);
			if (i == 0) {
				m.note = book(LAST);
			}
			world.spawnEntity(m);
			r.squad.add(m.getUuid());
		}
	}

	/** The survivors still in the hotel. */
	public static List<SquadMemberEntity> members(ServerWorld world, HomeBlockEntity h) {
		List<SquadMemberEntity> out = new ArrayList<>();
		for (UUID id : h.run.squad) {
			if (world.getEntity(id) instanceof SquadMemberEntity m && !m.isRemoved()) {
				out.add(m);
			}
		}
		return out;
	}

	/** The hotel opens without a rescue (or after one): whoever is left of them goes (the leavers walk out first). */
	static void end(ServerWorld world, HomeBlockEntity h) {
		for (SquadMemberEntity m : members(world, h)) {
			if (m.state() != SquadMemberEntity.LEAVING) {
				m.discard();
			}
		}
		h.run.squad.clear();
	}

	/** The loop has broken: each rescued survivor walks to the exit and leaves, and pays everyone who fought the devil. */
	static void leave(ServerWorld world, HomeBlockEntity h) {
		int saved = 0;
		Vec3d exit = HotelBuilder.world(h.origin, h.rot, HotelPlan.SHAFT_X0 + 2.5, 29.0, 2.0);
		for (SquadMemberEntity m : members(world, h)) {
			if (m.rescued) {
				saved++;
				m.setMad(false);
				m.setState(SquadMemberEntity.LEAVING);
				m.setTarget(null);
				m.setHealth(m.getMaxHealth());
				m.exit = exit;
			} else {
				m.discard();
			}
		}
		h.run.squad.clear();
		h.run.saved = saved;
		if (saved > 0) {
			// Everyone who fought the devil, alive or not, here or not (paid when they can take it), and nobody else.
			for (UUID id : h.run.fought) {
				reward(world.getServer(), id, saved);
			}
		}
	}

	/**
	 * What {@code survivors} rescued hunters are worth to one player who fought when the loop breaks, paid when they are in the game, alive
	 * and up ({@link HotelLedger}). HOOK: Public Safety's yen and rank are paid here once they exist; for now experience and gold.
	 */
	public static void reward(net.minecraft.server.MinecraftServer server, UUID player, int survivors) {
		HotelLedger.owe(server, player, List.of(new ItemStack(Items.GOLD_INGOT, REWARD_GOLD * survivors)), REWARD_XP * survivors,
				"message.chainsawman.squad.thanks", survivors);
	}
}
