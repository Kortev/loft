package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * How each player moved in their own take of the film, tick by tick, to be played back by the server's copy of them in
 * the other two: so all three move as a person does in every take (their own hands on the mouse and keys made it), and
 * do the same thing at the same moment in all of them. A take writes its own player's to build/film/moves/WHO.tsv; the
 * next take of anyone else reads it.
 */
final class FilmMoves {
	/** One tick of a player: the scene's tick, the event's age and the ticks since the world came back (-1: not yet). */
	record Move(int s, int age, int r, String dim, double x, double y, double z, float yaw, float pitch, boolean sneak,
			boolean sprint, boolean swing, boolean alive, int slot, String item) {
		Vec3d pos() {
			return new Vec3d(x, y, z);
		}
	}

	private final List<Move> moves = new ArrayList<>();

	private static Path file(FilmSelfTest.Role role) {
		return FabricLoader.getInstance().getGameDir().resolve("../film/moves").normalize()
				.resolve(role.name().toLowerCase(Locale.ROOT) + ".tsv");
	}

	void add(Move move) {
		moves.add(move);
	}

	/** The move at scene tick {@code s}, or null if there is none. */
	@Nullable
	Move at(int s) {
		return s >= 0 && s < moves.size() && moves.get(s).s() == s ? moves.get(s) : find(s);
	}

	@Nullable
	private Move find(int s) {
		for (Move m : moves) {
			if (m.s() == s) {
				return m;
			}
		}
		return null;
	}

	/** The first move at or after the event's age {@code age} (before the world came back). */
	@Nullable
	Move atAge(int age) {
		for (Move m : moves) {
			if (m.r() < 0 && m.age() >= age) {
				return m;
			}
		}
		return null;
	}

	void save(FilmSelfTest.Role role) {
		StringBuilder out = new StringBuilder();
		for (Move m : moves) {
			out.append(String.format(Locale.ROOT, "%d\t%d\t%d\t%s\t%.4f\t%.4f\t%.4f\t%.3f\t%.3f\t%d\t%d\t%d\t%d\t%d\t%s%n", m.s(), m.age(),
					m.r(), m.dim(), m.x(), m.y(), m.z(), m.yaw(), m.pitch(), m.sneak() ? 1 : 0, m.sprint() ? 1 : 0, m.swing() ? 1 : 0,
					m.alive() ? 1 : 0, m.slot(), m.item()));
		}
		try {
			Path path = file(role);
			Files.createDirectories(path.getParent());
			Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
			ShootingStar.LOGGER.info("[film] {} moves of {} written to {}", moves.size(), role.name, path);
		} catch (IOException e) {
			ShootingStar.LOGGER.error("[film] could not write {}'s moves", role.name, e);
		}
	}

	/** {@code role}'s moves from their own last take, or null if they have none yet. */
	@Nullable
	static FilmMoves load(FilmSelfTest.Role role) {
		Path path = file(role);
		if (!Files.exists(path)) {
			ShootingStar.LOGGER.info("[film] no moves of {} yet: they stand in for themselves", role.name);
			return null;
		}
		FilmMoves track = new FilmMoves();
		try {
			for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
				String[] f = line.split("\t");
				if (f.length < 15) {
					continue;
				}
				track.add(new Move(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), f[3], Double.parseDouble(f[4]),
						Double.parseDouble(f[5]), Double.parseDouble(f[6]), Float.parseFloat(f[7]), Float.parseFloat(f[8]), f[9].equals("1"),
						f[10].equals("1"), f[11].equals("1"), f[12].equals("1"), Integer.parseInt(f[13]), f[14]));
			}
		} catch (IOException | NumberFormatException e) {
			ShootingStar.LOGGER.error("[film] could not read {}'s moves", role.name, e);
			return null;
		}
		ShootingStar.LOGGER.info("[film] {} moves of {} to play back", track.moves.size(), role.name);
		return track;
	}
}
