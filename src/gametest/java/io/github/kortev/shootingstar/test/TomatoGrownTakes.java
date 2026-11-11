package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.devil.Devils;
import io.github.kortev.chainsawman.devil.TomatoDevilEntity;
import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * The grown Tomato Devil's takes (devil/Growth), on the Tomato's site:
 * <ul>
 * <li>-Ptarget=growths: one still of it at each growth, 0 to 5, standing in the same place and pose, every eye on you, the camera the same
 * for all six ({@code tomato_growth_<n>.png}, for growth_steps.jpg); then the same six from the side, and its face close at 0 and at 5.</li>
 * <li>-Ptarget=growreel: the reel take: it stands 6 blocks off, growth 0, and every two seconds is set one step higher by NBT (as
 * {@code /data merge entity} does), shuddering each time, up to 5; about 13 s. With -Pcapture=true, the film.</li>
 * </ul>
 * (-Ptarget=grown, the played fight against a growth 5, is {@link TomatoFight}'s.)
 */
final class TomatoGrownTakes {
	private TomatoGrownTakes() {
	}

	/** The afternoon, for the stills (lit evenly); the reel at dusk. */
	static final long AFTERNOON = 9000L;
	private static int devilId = -1;

	private static TomatoDevilEntity put(IntegratedServer s, int growth, float yaw) {
		ServerWorld w = s.getOverworld();
		for (TomatoDevilEntity e : w.getEntitiesByType(Devils.TOMATO_DEVIL, e -> true)) {
			e.discard();
		}
		TomatoDevilEntity d = TomatoSelfTest.devil(w, TomatoSelfTest.site(0.0, 0.0, 0.0), yaw);
		d.setAiDisabled(true);
		d.growth().set(growth, true);
		d.notice(true);
		devilId = d.getId();
		return d;
	}

	/** -Ptarget=growths. */
	static boolean stills(MinecraftClient c, IntegratedServer s, int f) {
		c.options.hudHidden = true;
		c.options.setPerspective(Perspective.FIRST_PERSON);
		c.options.getFov().setValue(50);
		Vec3d p = TomatoSelfTest.site(0.0, 0.0, 0.0);
		if (f == 1) {
			s.execute(() -> s.getOverworld().setTimeOfDay(AFTERNOON));
		}
		// three-quarter front from 9 blocks at a man's eye height, looking a little up: the same for all six
		double cx = 5.2;
		double cz = -7.4;
		float yaw = (float) Math.toDegrees(Math.atan2(-(0.0 - cx), 0.0 - cz));
		for (int g = 0; g <= 5; g++) {
			int at = 20 + g * 50;
			int growth = g;
			if (f == at) {
				s.execute(() -> {
					put(s, growth, 180.0F + yaw);
					s.getOverworld().setTimeOfDay(AFTERNOON);
					TomatoSelfTest.you(s).teleport(s.getOverworld(), p.x + cx, p.y, p.z + cz, yaw, -4.0F);
				});
			}
			if (f >= at && f <= at + 50) {
				Capture.camera = t -> new Capture.Pose(p.x + cx, p.y + 1.62, p.z + cz, yaw, -6.0F);
			}
			if (f == at + 45) {
				TomatoSelfTest.still(c, "growth_" + g);
			}
		}
		// the side (it faces the camera's left), and the face close, at 0 and at 5
		int at = 20 + 6 * 50;
		int[][] more = {{0, 0}, {5, 0}, {0, 1}, {5, 1}};
		for (int[] m : more) {
			int growth = m[0];
			boolean face = m[1] == 1;
			if (f == at) {
				s.execute(() -> {
					put(s, growth, face ? 180.0F : 90.0F);
					TomatoSelfTest.you(s).teleport(s.getOverworld(), p.x, p.y, p.z - (face ? 4.5 : 9.0), 0.0F, 0.0F);
				});
			}
			if (f >= at && f <= at + 50) {
				Capture.camera = face ? t -> new Capture.Pose(p.x, p.y + 2.9, p.z - 4.6, 0.0F, 4.0F) : t -> new Capture.Pose(p.x, p.y + 1.8, p.z - 9.5, 0.0F, -6.0F);
			}
			if (f == at + 45) {
				TomatoSelfTest.still(c, (face ? "growth_face_" : "growth_side_") + growth);
			}
			at += 50;
		}
		return f > at + 5;
	}

	/** -Ptarget=growreel. */
	static boolean reel(MinecraftClient c, IntegratedServer s, int f) {
		c.options.hudHidden = true;
		c.options.setPerspective(Perspective.FIRST_PERSON);
		c.options.getFov().setValue(70);
		c.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
		Vec3d p = TomatoSelfTest.site(0.0, 0.0, 0.0);
		// 6 blocks off, a man's eyes, looking up at it a little
		Capture.camera = t -> new Capture.Pose(p.x, p.y + 1.62, p.z - 6.0, 0.0F, -12.0F);
		if (f == 1) {
			s.execute(() -> {
				put(s, 0, 180.0F);
				TomatoSelfTest.you(s).teleport(s.getOverworld(), p.x, p.y, p.z - 6.0, 0.0F, -12.0F);
			});
		}
		if (f == 60) {
			if (TomatoSelfTest.capturing()) {
				Capture.start(c, c.runDirectory.toPath().resolve("capture"));
			}
			TomatoSelfTest.still(c, "growreel_0");
		}
		// every two seconds a step, set by NBT as /data merge entity does it
		for (int g = 1; g <= 5; g++) {
			int at = 60 + 30 + (g - 1) * 40;
			int growth = g;
			if (f == at) {
				s.execute(() -> {
					if (s.getOverworld().getEntityById(devilId) instanceof TomatoDevilEntity d) {
						NbtCompound n = new NbtCompound();
						d.writeNbt(n);
						n.putInt("Growth", growth);
						d.readNbt(n);
						ShootingStar.LOGGER.info("[selftest] growreel: growth {} (shudder at {})", d.growthLevel(), d.grewAt());
					}
				});
			}
			if (f == at + 34) {
				TomatoSelfTest.still(c, "growreel_" + g);
			}
		}
		return f > 60 + 30 + 4 * 40 + 70;
	}
}
