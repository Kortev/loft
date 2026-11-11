package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.PochitaEntity;
import io.github.kortev.chainsawman.devil.Devils;
import io.github.kortev.chainsawman.devil.TomatoDevilEntity;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.shootingstar.ShootingStar;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * -Ptarget=cinematic: the Tomato Devil's horror short (plan 3.4c), filmed as eight shots on a village's edge at night: Pochita growling at the dark
 * by the one lantern; far glints between the trees; a hand feeling its way; its shape against the moon from behind a tree; eyes rising into a
 * cottage window past a sleeping villager; one eye, which snaps to you, then all of them; the mouth parting; it coming at you. Each shot is set up
 * in a lead of its own (cut out afterwards: the marks of every shot's first and last frame are written beside the capture as
 * {@code cinematic_marks.json}); the grade, the cut and the sound are made afterwards from the frames ({@code tools/tomato_creepy.py}).
 * <p>
 * Staged (nothing of the game changed): the devil's speed lowered for its creeping walk and raised for the last shot; its mind switched off where it
 * is posed (its animations still run: they are all drawn on the client from its state); its noticing set by hand; Pochita told where to look.
 */
public final class TomatoCinematic {
	/** Night, the moon well up in the east (a full moon: the world's first night). */
	static final long NIGHT = 14600L;
	private static final int LEAD = 30;

	/** The lens (vertical degrees) and the fog (start, end; blocks) of the shot being filmed: 0 and -1 for the game's own. */
	static volatile float lens;
	public static volatile float fogStart = -1.0F;
	public static volatile float fogEnd = -1.0F;

	private static int devilId = -1;
	private static int shot = -1;
	private static int shotAt;
	private static double shotClock;
	private static volatile int slapAt = -1;
	private static final List<long[]> MARKS = new ArrayList<>();

	private TomatoCinematic() {
	}

	public static boolean on() {
		return TomatoSelfTest.active() && TomatoSelfTest.target().equals("cinematic");
	}

	/** The lens while a shot is on (for ReelLensMixin), else 0. */
	public static float lens() {
		return on() ? lens : 0.0F;
	}

	// ---- The set.

	static BlockPos b(int dx, int y, int dz) {
		return new BlockPos(TomatoSelfTest.SITE.getX() + dx, TomatoSelfTest.GROUND + y, TomatoSelfTest.SITE.getZ() + dz);
	}

	static Vec3d at(double dx, double dy, double dz) {
		return TomatoSelfTest.site(dx, dy, dz);
	}

	private static void set(ServerWorld w, BlockPos p, BlockState s) {
		w.setBlockState(p, s, Block.NOTIFY_LISTENERS);
	}

	/** The trees of the wood to the east (offsets), and the lone one in the field. */
	static final int[][] WOOD = {{17, -10}, {19, -5}, {23, 1}, {18, 4}, {21, 9}, {24, -7}, {22, 14}, {16, 13}, {26, 5}, {25, -13}, {20, -15}};
	static final int[] LONE = {9, -22};
	/** The lantern on its post at the village's edge. */
	static final int[] POST = {3, 6};

	/** The village's edge: a cottage (a window east, a lantern inside, a villager asleep), the lantern post, the wood, grass to lie in. */
	static void build(ServerWorld w) {
		w.setTimeOfDay(NIGHT);
		// The cottage: x -4..0, z -3..3; cobbles at the foot, spruce walls, oak corners, a dark roof, glass in the east wall.
		for (int x = -8; x <= 0; x++) {
			for (int z = -3; z <= 3; z++) {
				set(w, b(x, 0, z), Blocks.SPRUCE_PLANKS.getDefaultState());
				boolean edge = x == -8 || x == 0 || z == -3 || z == 3;
				boolean corner = (x == -8 || x == 0) && (z == -3 || z == 3);
				for (int y = 1; y <= 4; y++) {
					BlockState s = !edge ? Blocks.AIR.getDefaultState() : corner ? Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState()
							: y == 1 ? Blocks.COBBLESTONE.getDefaultState() : Blocks.SPRUCE_PLANKS.getDefaultState();
					set(w, b(x, y, z), s);
				}
			}
		}
		for (int x = -9; x <= 1; x++) {
			for (int z = -4; z <= 4; z++) {
				set(w, b(x, 5, z), Blocks.DARK_OAK_PLANKS.getDefaultState());
				if (x > -9 && x < 1 && z > -4 && z < 4) {
					set(w, b(x, 6, z), Blocks.DARK_OAK_PLANKS.getDefaultState());
				}
			}
		}
		for (int z = -1; z <= 0; z++) {
			for (int y = 2; y <= 3; y++) {
				set(w, b(0, y, z), Blocks.GLASS.getDefaultState());
			}
		}
		// A small window at the back too, so the room isn't a box.
		set(w, b(-8, 2, -1), Blocks.GLASS.getDefaultState());
		// Inside: the bed (head to the west wall), the lantern hung from the roof, a chest, a table.
		set(w, b(-3, 1, 1), Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.WEST).with(BedBlock.PART, BedPart.HEAD));
		set(w, b(-2, 1, 1), Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.WEST).with(BedBlock.PART, BedPart.FOOT));
		set(w, b(-1, 1, 0), Blocks.BARREL.getDefaultState());
		set(w, b(-1, 2, 0), Blocks.LANTERN.getDefaultState());
		set(w, b(-6, 1, -2), Blocks.CHEST.getDefaultState());
		set(w, b(-6, 1, 2), Blocks.CRAFTING_TABLE.getDefaultState());
		// The lantern post.
		set(w, b(POST[0], 1, POST[1]), Blocks.OAK_FENCE.getDefaultState());
		set(w, b(POST[0], 2, POST[1]), Blocks.OAK_FENCE.getDefaultState());
		set(w, b(POST[0], 3, POST[1]), Blocks.LANTERN.getDefaultState());
		// The wood, and the lone tree.
		for (int[] t : WOOD) {
			tree(w, b(t[0], 1, t[1]), 7);
		}
		tree(w, b(LONE[0], 1, LONE[1]), 6);
		// Grass to lie low in (shot 2), clear of where the hands come down.
		for (int x = 6; x <= 12; x++) {
			for (int z = -5; z <= 1; z++) {
				int h = Math.floorMod(x * 73 + z * 151, 10);
				if (x >= 9 && z >= -3 && z <= -1) {
					continue;
				}
				if (h < 3) {
					set(w, b(x, 1, z), Blocks.TALL_GRASS.getDefaultState().with(TallPlantBlock.HALF, DoubleBlockHalf.LOWER));
					set(w, b(x, 2, z), Blocks.TALL_GRASS.getDefaultState().with(TallPlantBlock.HALF, DoubleBlockHalf.UPPER));
				} else if (h < 8) {
					set(w, b(x, 1, z), Blocks.SHORT_GRASS.getDefaultState());
				}
			}
		}
		// A few tufts at the wood's foot.
		for (int x = 14; x <= 27; x++) {
			for (int z = -16; z <= 16; z++) {
				if (Math.floorMod(x * 31 + z * 17, 7) == 0 && w.getBlockState(b(x, 1, z)).isAir() && !(x > 18 && x < 23 && z > -4 && z < 1)) {
					set(w, b(x, 1, z), Blocks.SHORT_GRASS.getDefaultState());
				}
			}
		}
	}

	private static void tree(ServerWorld w, BlockPos base, int trunk) {
		for (int y = 0; y < trunk; y++) {
			set(w, base.up(y), Blocks.DARK_OAK_LOG.getDefaultState());
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = trunk - 3; dy <= trunk; dy++) {
					int r = dy >= trunk - 1 ? 1 : 2;
					if (Math.abs(dx) <= r && Math.abs(dz) <= r && !(Math.abs(dx) == r && Math.abs(dz) == r && dy != trunk - 2)) {
						BlockPos p = base.add(dx, dy, dz);
						if (w.getBlockState(p).isAir()) {
							set(w, p, Blocks.DARK_OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));
						}
					}
				}
			}
		}
	}

	/** The villager asleep in the cottage's bed. */
	static void sleeper(ServerWorld w) {
		VillagerEntity v = EntityType.VILLAGER.create(w);
		BlockPos head = b(-3, 1, 1);
		v.refreshPositionAndAngles(head.getX() + 0.5, head.getY() + 0.6, head.getZ() + 0.5, 90.0F, 0.0F);
		v.setAiDisabled(true);
		v.setPersistent();
		w.spawnEntity(v);
		v.sleep(head);
	}

	// ---- The camera.

	/** A camera at {@code from} looking at {@code to}. */
	static Capture.Pose look(Vec3d from, Vec3d to) {
		Vec3d d = to.subtract(from);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
		return new Capture.Pose(from.x, from.y, from.z, yaw, pitch);
	}

	private static double ease(double u) {
		u = MathHelper.clamp(u, 0.0, 1.0);
		return u * u * (3.0 - 2.0 * u);
	}

	/** A slow move from one place to another over the shot ({@code len} ticks), looking from one point to another. */
	private static void move(int len, Vec3d from0, Vec3d from1, Vec3d to0, Vec3d to1) {
		double start = shotClock;
		Capture.camera = t -> {
			double u = ease((t - start) / len);
			return look(from0.lerp(from1, u), to0.lerp(to1, u));
		};
	}

	private static void hold(Vec3d from, Vec3d to) {
		Capture.Pose p = look(from, to);
		Capture.camera = t -> p;
	}

	// ---- The take.

	/** The shots' lengths in ticks (the lead before each not counted). */
	static final int[] LEN = {130, 200, 170, 240, 190, 150, 120, 150};

	/** One client tick of the take (from the run's start); true when it is over. */
	static boolean tick(MinecraftClient c, IntegratedServer s, int f) {
		c.options.hudHidden = true;
		c.options.setPerspective(Perspective.FIRST_PERSON);
		c.options.getCloudRenderMode().setValue(CloudRenderMode.OFF);
		c.options.getGamma().setValue(1.0);
		c.inGameHud.getChatHud().clear(false);
		if (f == 1) {
			// First a still of Pochita sitting, by day (the fix: he sat a little over the ground).
			s.execute(() -> {
				ServerWorld w = s.getOverworld();
				w.setTimeOfDay(6000L);
				s.getGameRules().get(io.github.kortev.chainsawman.form.TeamRules.CHAINSAW_HEARTS).set(8, s);
				you(s).removeAttached(Heart.MERGED);
				PochitaEntity p = pochita(w, at(-8.0, 0.0, -14.0), 0.0F);
				p.setSitting(true);
				p.setInSittingPose(true);
				p.setAiDisabled(true);
				fly(s, at(-4.6, 0.3, -14.0));
			});
			lens = 26.0F;
			hold(at(-4.6, 0.3, -14.0), at(-8.0, 0.3, -14.0));
		}
		if (f == 40) {
			TomatoSelfTest.still(c, "pochita_sit_side");
			hold(at(-5.6, 0.9, -11.6), at(-8.0, 0.3, -14.0));
		}
		if (f == 46) {
			TomatoSelfTest.still(c, "pochita_sit_threequarter");
		}
		if (f == 50) {
			s.execute(() -> {
				clear(s.getOverworld(), true);
				s.getOverworld().setTimeOfDay(NIGHT);
			});
		}
		int start = 70;
		if (f == start - 5 && TomatoSelfTest.capturing()) {
			Capture.start(c, c.runDirectory.toPath().resolve("capture"));
		}
		int at = start;
		for (int n = 0; n < LEN.length; n++) {
			int lead = at;
			int begin = lead + LEAD;
			int end = begin + LEN[n];
			if (f == lead) {
				shot = n;
				shotAt = begin;
				setup(c, s, n);
			}
			if (f == begin) {
				shotClock = Capture.active() ? Capture.time() : f;
				mark(n, true);
				first(c, s, n);
			}
			if (f > lead && f <= begin && shot == n && n == 4) {
				// (held low under the window through the lead too)
				s.execute(() -> {
					TomatoDevilEntity d = devil(s);
					if (d != null) {
						d.begin(TomatoDevilEntity.EMERGE);
					}
				});
			}
			if (f > begin && f < end && shot == n) {
				during(c, s, n, f - begin);
			}
			if (f == end) {
				mark(n, false);
				s.execute(() -> {
					TomatoDevilEntity d = devil(s);
					if (d != null) {
						ShootingStar.LOGGER.info("[cinematic] the devil at the end of the shot: offset {}", d.getPos().subtract(at(0.0, 0.0, 0.0)));
					}
				});
			}
			at = end;
		}
		if (f == at + 2) {
			writeMarks(c);
			return true;
		}
		return false;
	}

	private static void mark(int n, boolean first) {
		long frame = Capture.active() ? Capture.frames() : -1;
		MARKS.add(new long[] {n + 1, first ? 0 : 1, frame});
		ShootingStar.LOGGER.info("[cinematic] shot {} {} at frame {}", n + 1, first ? "begins" : "ends", frame);
	}

	/** A moment inside a shot (the snap, the mouth, the slap), for the cut and the sound. */
	private static void event(int n, int kind) {
		long frame = Capture.active() ? Capture.frames() : -1;
		MARKS.add(new long[] {n + 1, kind, frame});
		ShootingStar.LOGGER.info("[cinematic] shot {} event {} at frame {}", n + 1, kind, frame);
	}

	private static boolean slapMarked;

	private static void writeMarks(MinecraftClient c) {
		StringBuilder sb = new StringBuilder("[\n");
		for (int i = 0; i < MARKS.size(); i++) {
			long[] m = MARKS.get(i);
			sb.append(String.format(Locale.ROOT, "  {\"shot\": %d, \"edge\": \"%s\", \"frame\": %d}%s\n", m[0], m[1] == 0 ? "begin" : m[1] == 1 ? "end" : "event", m[2],
					i + 1 < MARKS.size() ? "," : ""));
		}
		sb.append("]\n");
		try {
			Path dir = c.runDirectory.toPath().resolve("capture");
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("cinematic_marks.json"), sb.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			ShootingStar.LOGGER.warn("[cinematic] could not write the marks", e);
		}
	}

	private static ServerPlayerEntity you(IntegratedServer s) {
		return TomatoSelfTest.you(s);
	}

	/** You (unseen: the camera is where your eyes are, so the devil's eyes can look into it), flying at {@code eye}. */
	private static void fly(IntegratedServer s, Vec3d eye) {
		ServerPlayerEntity p = you(s);
		if (p.interactionManager.getGameMode() != GameMode.CREATIVE) {
			p.changeGameMode(GameMode.CREATIVE);
		}
		p.getAbilities().flying = true;
		p.sendAbilitiesUpdate();
		p.teleport(s.getOverworld(), eye.x, eye.y - 1.62, eye.z, p.getYaw(), p.getPitch());
	}

	private static void clear(ServerWorld w, boolean pochitaToo) {
		for (TomatoDevilEntity e : w.getEntitiesByType(Devils.TOMATO_DEVIL, e -> true)) {
			e.discard();
		}
		if (pochitaToo) {
			for (PochitaEntity p : w.getEntitiesByType(ChainsawMan.POCHITA_ENTITY, p -> true)) {
				p.discard();
			}
		}
	}

	private static PochitaEntity pochita(ServerWorld w, Vec3d pos, float yaw) {
		PochitaEntity p = ChainsawMan.POCHITA_ENTITY.create(w);
		p.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0.0F);
		p.setHeadYaw(yaw);
		p.setBodyYaw(yaw);
		p.setTamed(true, true);
		p.setOwnerUuid(null);
		p.setPersistent();
		w.spawnEntity(p);
		return p;
	}

	private static TomatoDevilEntity devil(ServerWorld w, Vec3d pos, float yaw, boolean mind, double speed) {
		TomatoDevilEntity d = TomatoSelfTest.devil(w, pos, yaw);
		d.setAiDisabled(!mind);
		d.aimAt(yaw);
		d.setPersistent();
		d.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(TomatoDevilEntity.SPEED * speed);
		devilId = d.getId();
		return d;
	}

	private static TomatoDevilEntity devil(IntegratedServer s) {
		return devilId >= 0 && s.getOverworld().getEntityById(devilId) instanceof TomatoDevilEntity d ? d : null;
	}

	/** Where the pupils of eye {@code k} of a devil standing at {@code feet} facing {@code yaw} are, and the way that eye faces (its rest). */
	static Vec3d[] eye(Vec3d feet, float yaw, double[] e) {
		double a = -Math.toRadians(yaw);
		double cx = e[0] * Math.cos(a) + e[2] * Math.sin(a);
		double cz = -e[0] * Math.sin(a) + e[2] * Math.cos(a);
		double nx = e[3] * Math.cos(a) + e[5] * Math.sin(a);
		double nz = -e[3] * Math.sin(a) + e[5] * Math.cos(a);
		return new Vec3d[] {feet.add(cx, 2.45 + e[1], cz), new Vec3d(nx, e[4], nz).normalize()};
	}

	/** The big eye beside the mouth (the baked list's first): centre, normal, radius. */
	static final double[] BIG_EYE = {0.68, 0.503, 1.021, 0.612, 0.439, 0.658, 0.31};

	// Where the close-ups are posed: in the open by the lantern post, facing south (the camera south of it, the lantern to its right).
	static final Vec3d CLOSE = at(4.6, 0.0, 9.0);
	static final Vec3d POCHITA_FROM = at(1.6, 0.0, 9.0);
	static final Vec3d POCHITA_TO = at(4.2, 0.0, 6.7);
	static final float POCHITA_YAW = -131.0F;
	/** Low behind the sleeping villager's head, on the line to the window. */
	static final Vec3d SLEEPER_CAM = at(-4.6, 1.05, 2.15);
	static final Vec3d SLEEPER_LOOK = at(0.0, 1.45, -0.6);
	/** Outside the window, where it rises into it. */
	static final Vec3d WINDOW_DEVIL = at(2.35, 0.0, -1.9);
	/** Where it comes at you from in the last shot. */
	static final Vec3d LUNGE_FROM = at(15.0, 0.0, 2.0);

	private static void setup(MinecraftClient c, IntegratedServer s, int n) {
		slapAt = -1;
		s.execute(() -> {
			ServerWorld w = s.getOverworld();
			if (n != 6) {
				clear(w, n != 0);
			}
			switch (n) {
				case 0 -> {
					// Pochita by the lantern, trotting out toward the field; the devil far out in the dark, past the fog.
					clear(w, true);
					PochitaEntity p = pochita(w, POCHITA_FROM, POCHITA_YAW);
					p.setAiDisabled(true);
					fly(s, at(0.4, 0.45, 10.4));
				}
				case 1 -> {
					// Far off between the trees.
					clear(w, true);
					devil(w, at(25.5, 0.0, -2.4), 90.0F, false, 1.0);
					fly(s, at(9.0, 0.3, -2.0));
				}
				case 2 -> {
					// It creeps past, slowly, toward the village; its near hands come down by the camera.
					TomatoDevilEntity d = devil(w, at(11.5, 0.0, 4.0), 90.0F, true, 0.45);
					d.walkTo = at(1.5, 0.0, 4.0);
					fly(s, at(6.0, 0.3, 7.6));
				}
				case 3 -> {
					// Across the field beyond the lone tree, under the moon.
					TomatoDevilEntity d = devil(w, at(13.0, 0.0, -27.0), 0.0F, true, 0.36);
					d.walkTo = at(13.0, 0.0, -12.0);
					fly(s, at(8.2, 0.3, -22.5));
				}
				case 4 -> {
					// Up to the cottage's window from the field.
					// It comes up out of the ground under the window (its coming back from a seed: up from low and small), held low till the
					// shot is under way.
					TomatoDevilEntity d = devil(w, WINDOW_DEVIL, 90.0F, false, 1.0);
					d.begin(TomatoDevilEntity.EMERGE);
					fly(s, SLEEPER_CAM);
				}
				case 5 -> {
					// Posed by the lantern post: one eye, close.
					devil(w, CLOSE, 0.0F, false, 1.0);
					Vec3d[] e = eye(CLOSE, 0.0F, BIG_EYE);
					fly(s, e[0].add(e[1].multiply(2.1)));
				}
				case 7 -> {
					// It comes at you.
					fly(s, at(3.9, 0.5, 8.3));
				}
				default -> {
				}
			}
		});
		// The camera, the lens and the fog, from the lead on (so the chunks and the light are there by the first frame).
		switch (n) {
			case 0 -> {
				lens = 46.0F;
				fog(2.0F, 22.0F);
				hold(at(0.4, 0.45, 10.4), at(5.0, 1.0, 4.0));
			}
			case 1 -> {
				lens = 38.0F;
				fog(2.0F, 25.0F);
				hold(at(9.0, 0.3, -2.0), at(25.5, 2.0, -2.4));
			}
			case 2 -> {
				lens = 26.0F;
				fog(3.0F, 30.0F);
				hold(at(6.0, 0.3, 7.6), at(8.4, 0.12, 6.0));
			}
			case 3 -> {
				lens = 58.0F;
				fog(6.0F, 44.0F);
				hold(at(8.2, 0.3, -22.55), at(13.0, 2.9, -22.55));
			}
			case 4 -> {
				lens = 52.0F;
				fog(3.0F, 34.0F);
				hold(SLEEPER_CAM, SLEEPER_LOOK);
			}
			case 5 -> {
				lens = 34.0F;
				fog(4.0F, 40.0F);
				Vec3d[] e = eye(CLOSE, 0.0F, BIG_EYE);
				hold(e[0].add(e[1].multiply(2.1)), e[0]);
			}
			case 6 -> {
				lens = 40.0F;
				fog(4.0F, 40.0F);
			}
			case 7 -> {
				lens = 60.0F;
				fog(3.0F, 34.0F);
				hold(at(3.9, 0.5, 8.3), LUNGE_FROM.add(0.0, 2.9, 0.0));
			}
			default -> {
			}
		}
	}

	private static void fog(float start, float end) {
		fogStart = start;
		fogEnd = end;
	}

	/** At the shot's first frame: the moves that run the whole shot. */
	private static void first(MinecraftClient c, IntegratedServer s, int n) {
		switch (n) {
			case 0 -> move(LEN[0], at(0.4, 0.45, 10.4), at(1.0, 0.4, 9.7), at(5.0, 1.0, 4.0), at(5.2, 0.8, 4.2));
			case 1 -> move(LEN[1], at(9.0, 0.3, -2.0), at(9.6, 0.3, -2.0), at(25.5, 2.0, -2.4), at(25.5, 2.1, -2.4));
			case 3 -> move(LEN[3], at(8.2, 0.3, -22.55), at(8.55, 0.3, -23.3), at(13.0, 2.9, -22.55), at(13.0, 2.8, -23.3));
			case 4 -> move(LEN[4], SLEEPER_CAM, SLEEPER_CAM.add(0.3, 0.0, -0.1), SLEEPER_LOOK, SLEEPER_LOOK.add(0.0, 0.1, 0.0));
			case 5 -> {
				Vec3d[] e = eye(CLOSE, 0.0F, BIG_EYE);
				move(LEN[5], e[0].add(e[1].multiply(2.1)), e[0].add(e[1].multiply(1.8)), e[0], e[0]);
			}
			case 6 -> {
				// The mouth, from in front, at its height: you are where the camera is, so every eye is on it.
				Vec3d mouth = CLOSE.add(0.0, 2.43, 1.3);
				Vec3d from = mouth.add(0.0, 0.05, 2.25);
				s.execute(() -> fly(s, from));
				move(LEN[6], from, mouth.add(0.0, 0.03, 1.9), mouth, mouth);
			}
			case 7 -> {
				// following it in, and up as it rears over you
				Vec3d cam = at(3.9, 0.5, 8.3);
				Vec3d[] aim = {LUNGE_FROM.add(0.0, 2.6, 0.0)};
				Capture.camera = t -> {
					MinecraftClient mc = MinecraftClient.getInstance();
					if (mc.world != null && devilId >= 0 && mc.world.getEntityById(devilId) instanceof TomatoDevilEntity d) {
						float dt = mc.getRenderTickCounter().getTickDelta(true);
						double rear = d.state() == TomatoDevilEntity.SLAP ? MathHelper.clamp(d.stateTime(dt) / 14.0, 0.0, 1.0) : 0.0;
						Vec3d want = d.getLerpedPos(dt).add(0.0, 2.4 + 1.5 * rear, 0.0);
						aim[0] = aim[0].lerp(want, 0.12);
					}
					return look(cam, aim[0]);
				};
			}
			default -> {
			}
		}
	}

	/** Each tick of a shot ({@code t} ticks in). */
	private static void during(MinecraftClient c, IntegratedServer s, int n, int t) {
		switch (n) {
			case 0 -> {
				// He trots out toward the field, stops dead and faces the dark.
				s.execute(() -> {
					for (PochitaEntity p : s.getOverworld().getEntitiesByType(ChainsawMan.POCHITA_ENTITY, p -> true)) {
						if (t <= 44) {
							Vec3d to = POCHITA_FROM.lerp(POCHITA_TO, Math.min(1.0, t / 44.0));
							p.refreshPositionAndAngles(to.x, to.y, to.z, POCHITA_YAW, 0.0F);
							p.setBodyYaw(POCHITA_YAW);
							p.setHeadYaw(POCHITA_YAW);
						}
						if (t == 50) {
							p.alertAt = at(30.0, 1.0, 1.0);
							p.alertUntil = s.getOverworld().getTime() + 400;
						}
					}
				});
			}
			case 4 -> {
				if (t < 40) {
					s.execute(() -> {
						TomatoDevilEntity d = devil(s);
						if (d != null) {
							d.begin(TomatoDevilEntity.EMERGE);
						}
					});
				}
				if (t == 40) {
					event(n, 2);
				}
				if (t == 41) {
					// slowly: 80 frames at 0.45 (the rise, 8 to 30 ticks into it, over about 2.7 s)
					Capture.slow(0.45, 80);
				}
			}
			case 5 -> {
				if (t == 64) {
					event(n, 2);
					s.execute(() -> {
						TomatoDevilEntity d = devil(s);
						if (d != null) {
							d.notice(true);
						}
					});
				}
				if (t == 99) {
					// Wider: every eye on you.
					Vec3d from = CLOSE.add(0.3, 3.0, 6.2);
					s.execute(() -> fly(s, from));
				}
				if (t == 100) {
					lens = 44.0F;
					Vec3d from = CLOSE.add(0.3, 3.0, 6.2);
					shotClock = Capture.active() ? Capture.time() : 0;
					move(LEN[5] - 100, from, CLOSE.add(0.28, 2.95, 5.6), CLOSE.add(0.0, 2.3, 0.0), CLOSE.add(0.0, 2.35, 0.0));
				}
			}
			case 6 -> {
				if (t == 36) {
					event(n, 2);
					s.execute(() -> {
						TomatoDevilEntity d = devil(s);
						if (d != null) {
							d.aimAt(0.0F);
							d.begin(TomatoDevilEntity.SPIT);
						}
					});
				}
				if (t == 37) {
					// slowly: 90 frames at 0.3 of the game's speed (18 ticks: the mouth all the way open)
					Capture.slow(0.3, 90);
				}
			}
			case 7 -> {
				if (slapAt >= 0 && !slapMarked) {
					slapMarked = true;
					event(n, 2);
				}
				s.execute(() -> {
					TomatoDevilEntity d = devil(s);
					if (t == 1) {
						TomatoDevilEntity e = devil(s.getOverworld(), LUNGE_FROM, 112.0F, true, 1.35);
						e.walkTo = at(4.3, 0.0, 8.1);
						e.notice(true);
						return;
					}
					if (d != null && slapAt < 0 && d.getPos().distanceTo(at(3.9, 0.0, 8.3)) < 4.7) {
						slapAt = t;
						d.setAiDisabled(true);
						d.walkTo = null;
						d.getNavigation().stop();
						float yaw = (float) Math.toDegrees(Math.atan2(-(at(3.9, 0.0, 8.3).x - d.getX()), at(3.9, 0.0, 8.3).z - d.getZ()));
						d.aimAt(yaw);
						d.begin(TomatoDevilEntity.SLAP);
						ShootingStar.LOGGER.info("[cinematic] the slap begins at shot tick {}", t);
					}
				});
			}
			default -> {
			}
		}
	}
}
