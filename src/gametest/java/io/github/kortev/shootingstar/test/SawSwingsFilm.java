package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.form.Blood;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The saw swings' film (-Pselftest=form -Ptarget=swings, with -Pcapture=true): Chainsaw Man cutting through the row of mobs on the test
 * ground ({@code /chainsaw scene}), played through the real keys ({@link Fighter}): from your own eyes with the HUD on, then the same fight
 * again (the row put back) from outside, a three-quarter front view following you. 1080x1920, normal field of view. The video lands in
 * build/selftest/capture.
 *
 * <pre>./gradlew runSelftest -Pselftest=form -Ptarget=swings -Pcapture=true -Pwidth=1080 -Pheight=1920 "-Pjvmargs=-Xmx2600m -XX:TieredStopAtLevel=1"</pre>
 */
public final class SawSwingsFilm {
	private static final Fighter YOU = new Fighter();
	/** The longest a take may run (ticks), and how long it holds on the row cut down. */
	private static final int TAKE = 260;
	private static final int HOLD = 16;
	/** How far north of a mob you stand to cut it (blocks): in reach, not in its face. */
	private static final double STAND = 2.5;
	/** The row carried on west past the ground's own (offsets east of where it was built, along the row's line), and what stands there. */
	private static final int[] MORE = {-9, -11, -13, -15};
	private static final net.minecraft.entity.EntityType<?>[] MORE_TYPES = {net.minecraft.entity.EntityType.ZOMBIE, net.minecraft.entity.EntityType.HUSK,
			net.minecraft.entity.EntityType.VINDICATOR, net.minecraft.entity.EntityType.ZOMBIE};
	/** Which take (0 setting up the first, 1 first person, 2 setting up the second, 3 outside), since when, and when its row was down. */
	private static int take;
	private static int since;
	private static int downAt = -1;
	private static boolean started;
	/** Where the ground is built from (the first take's place). */
	private static Vec3d spawn;

	private SawSwingsFilm() {
	}

	public static boolean active() {
		return FormSelfTest.active() && "swings".equals(System.getProperty("shootingstar.selftest.target"));
	}

	/** One tick of the film; true when it is over. */
	static boolean tick(MinecraftClient c, IntegratedServer s, int f) {
		c.getToastManager().clear();
		c.inGameHud.getChatHud().clear(false);
		int t = f - since;
		switch (take) {
			case 0, 2 -> {
				if (t == 1) {
					c.setCameraEntity(c.player);
					c.options.hudHidden = false;
					c.options.getFov().setValue(70);
					c.options.setPerspective(take == 0 ? Perspective.FIRST_PERSON : Perspective.THIRD_PERSON_BACK);
					YOU.release(c);
					c.player.setYaw(0.0F);
					c.player.setPitch(10.0F);
					s.execute(() -> ground(s));
				}
				if (take == 2) {
					// Out in front of you and off to your right, a little above, looking back at you: a three-quarter front view.
					Capture.camera = x -> outside(c);
				}
				if (t == 31) {
					if (!started && "true".equals(System.getProperty("shootingstar.selftest.capture"))) {
						started = true;
						Capture.start(c, c.runDirectory.toPath().resolve("capture"));
					}
					take++;
					since = f;
					downAt = -1;
				}
			}
			case 1, 3 -> {
				boolean standing = fight(c, t);
				if (!standing && downAt < 0) {
					downAt = t;
					YOU.stop();
					YOU.apply(c);
				}
				if (downAt >= 0 && t - downAt > HOLD || t > TAKE) {
					ShootingStar.LOGGER.info("[selftest] swings: take {} over at {} ticks, {} slashes", take, t, YOU.swings);
					YOU.release(c);
					if (take == 3) {
						Capture.camera = null;
						return true;
					}
					take++;
					since = f;
				}
			}
			default -> {
				return true;
			}
		}
		return false;
	}

	/** Where the outside camera is: out ahead and to the right of you (facing the row's way, south), above head height, looking back at your chest. */
	private static Capture.Pose outside(MinecraftClient c) {
		float delta = c.getRenderTickCounter().getTickDelta(true);
		Vec3d p = c.player.getLerpedPos(delta);
		// Off to your left as you face the row (south; your left is toward +x, the way you came along it), so the mobs still standing are
		// beyond you, not between.
		// (Looking between you and the mob you are cutting, so both are in the picture.)
		double a = Math.toRadians(62.0);
		Vec3d cam = p.add(Math.sin(a) * 4.5, 1.8, Math.cos(a) * 4.5);
		Vec3d look = p.add(0.0, 1.0, 1.2).subtract(cam);
		float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
		return new Capture.Pose(cam.x, cam.y, cam.z, yaw, pitch);
	}

	/**
	 * One tick of the fight: to the nearest mob still standing, stepping to a spot just north of it (the row's near side) while facing it, and
	 * cutting it down; false once none is left.
	 */
	private static boolean fight(MinecraftClient c, int t) {
		List<MobEntity> row = c.world.getEntitiesByClass(MobEntity.class, c.player.getBoundingBox().expand(24.0),
				e -> e.isAlive() && e.getHealth() > 0.0F && !(e instanceof io.github.kortev.chainsawman.PochitaEntity));
		if (row.isEmpty()) {
			return false;
		}
		Vec3d me = c.player.getPos();
		MobEntity next = row.stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(me))).get();
		YOU.turn = 14.0F;
		YOU.aim(c, next.getPos().add(0.0, next.getHeight() * 0.55, 0.0));
		Vec3d spot = next.getPos().add(0.0, 0.0, -STAND);
		YOU.strafeTo(c, spot, 0.35, false);
		double far = Math.hypot(next.getX() - me.x, next.getZ() - me.z);
		float off = Math.abs(MathHelper.wrapDegrees(YOU.yaw - c.player.getYaw()));
		if (t > 6 && far < 3.4 && off < 25.0F) {
			YOU.swing(c, 1.0F);
		}
		YOU.apply(c);
		return true;
	}

	/** On the server: the test ground (a fresh row of mobs), you in the form, whole and full of blood, facing the row (south). */
	private static void ground(IntegratedServer s) {
		ServerPlayerEntity p = s.getPlayerManager().getPlayerList().get(0);
		for (Entity e : p.getServerWorld().getOtherEntities(p, p.getBoundingBox().expand(40.0), e -> e instanceof ArmorStandEntity)) {
			e.discard();
		}
		if (spawn == null) {
			spawn = p.getPos();
		}
		p.networkHandler.requestTeleport(spawn.x, spawn.y, spawn.z, 0.0F, 10.0F);
		s.getCommandManager().executeWithPrefix(p.getCommandSource().withLevel(4), "chainsaw scene");
		// (The wild Pochita the ground sets out is for the merge: not in this.)
		for (Entity e : p.getServerWorld().getOtherEntities(p, p.getBoundingBox().expand(40.0), e -> e instanceof io.github.kortev.chainsawman.PochitaEntity)) {
			e.discard();
		}
		// (Nor the fallen tree it lies under: clear air over the floor.)
		net.minecraft.util.math.BlockPos base = net.minecraft.util.math.BlockPos.ofFloored(spawn);
		for (net.minecraft.util.math.BlockPos b : net.minecraft.util.math.BlockPos.iterate(base.add(-16, 0, -16), base.add(16, 5, 16))) {
			if (!p.getServerWorld().getBlockState(b).isAir()) {
				p.getServerWorld().setBlockState(b, net.minecraft.block.Blocks.AIR.getDefaultState());
			}
		}
		// The row carried on west, so a take lasts: more of the same, standing still, facing you, tagged as the ground's own (the next
		// ground takes them away).
		for (int i = 0; i < MORE.length; i++) {
			if (MORE_TYPES[i].create(p.getServerWorld()) instanceof MobEntity mob) {
				mob.refreshPositionAndAngles(spawn.x + MORE[i], spawn.y, spawn.z + 6.0, 180.0F, 0.0F);
				mob.initialize(p.getServerWorld(), p.getServerWorld().getLocalDifficulty(mob.getBlockPos()), net.minecraft.entity.SpawnReason.COMMAND, null);
				mob.setAiDisabled(true);
				mob.setPersistent();
				mob.setHeadYaw(180.0F);
				mob.setBodyYaw(180.0F);
				mob.addCommandTag("chainsawman_scene");
				p.getServerWorld().spawnEntity(mob);
			}
		}
		// Tougher than they are, so each takes a flurry, not a cut (the film is of the swings).
		for (MobEntity m : p.getServerWorld().getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(30.0), e -> e.getCommandTags().contains("chainsawman_scene"))) {
			m.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(45.0);
			m.setHealth(45.0F);
		}
		// At the east end of the row (6 blocks south of where the ground was built, from 7 east to 7 west), to cut along it westward.
		p.networkHandler.requestTeleport(spawn.x + 9.0, spawn.y, spawn.z + 4.3, 20.0F, 10.0F);
		ShootingStar.LOGGER.info("[selftest] swings: the ground at {}, {} mobs in the row", spawn,
				p.getServerWorld().getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(24.0), e -> !(e instanceof io.github.kortev.chainsawman.PochitaEntity)).size());
		Heart.give(p);
		Blood.set(p, Blood.MAX);
		Form.start(p);
		io.github.kortev.chainsawman.form.Limbs.whole(p);
		// Lasting the whole take (the form drains).
		p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.HEALTH_BOOST, 20 * 120, 4, false, false));
		p.setHealth(p.getMaxHealth());
	}
}
