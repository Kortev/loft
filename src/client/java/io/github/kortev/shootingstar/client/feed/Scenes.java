package io.github.kortev.shootingstar.client.feed;

import io.github.kortev.shootingstar.client.render.Gfx;
import io.github.kortev.shootingstar.client.render.Textures;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/** The seven shots of the uplink feed. Each draws its picture and returns the text to put over it. */
final class Scenes {
	static final class Label {
		final float x, y;
		final String line1;
		final int color1;
		final String line2;
		final int color2;
		float scale = 1.0F;

		Label(float x, float y, String line1, int color1, String line2, int color2) {
			this.x = x;
			this.y = y;
			this.line1 = line1;
			this.color1 = color1;
			this.line2 = line2;
			this.color2 = color2;
		}
	}

	interface Bars {
		void draw(BufferBuilder b, Matrix4f m, float w, float h);
	}

	static final class Overlay {
		String header;
		int headerColor = Feed.RED;
		String title;
		String subtitle;
		float titleAlpha;
		String footer;
		String footerSmall;
		Bars bars;
		final List<Label> labels = new ArrayList<>();
	}

	private static final int WARP = 700;
	private static final int ROCKS = 150;
	private static final int CLOUDS = 22;
	private static final int SPEED_LINES = 140;

	private final View3D view = new View3D();
	private final Painter painter = new Painter();
	private final Starfield stars = new Starfield(0x5EED5L);
	private final double[] out = new double[3];
	private final double[] out2 = new double[3];
	private final double[] tmp = new double[3];
	private final float[] warp = new float[WARP * 3];
	private final float[] rocks = new float[ROCKS * 6];
	private final float[] clouds = new float[CLOUDS * 5];
	private final float[] speedLines = new float[SPEED_LINES * 3];

	Scenes() {
		Random random = new Random(0xC0FFEEL);
		for (int i = 0; i < WARP; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = 1.5 + Math.pow(random.nextDouble(), 0.6) * 40.0;
			warp[i * 3] = (float) (Math.cos(angle) * radius);
			warp[i * 3 + 1] = (float) (Math.sin(angle) * radius);
			warp[i * 3 + 2] = (float) (random.nextDouble() * 300.0);
		}
		for (int i = 0; i < ROCKS; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = 1.6 + Math.pow(random.nextDouble(), 0.7) * 14.0;
			rocks[i * 6] = (float) (Math.cos(angle) * radius);
			rocks[i * 6 + 1] = (float) (Math.sin(angle) * radius);
			rocks[i * 6 + 2] = (float) (random.nextDouble() * 160.0);
			rocks[i * 6 + 3] = (float) (0.12 + Math.pow(random.nextDouble(), 2.5) * 0.9);
			rocks[i * 6 + 4] = random.nextFloat() * 1000.0F;
			rocks[i * 6 + 5] = random.nextFloat();
		}
		for (int i = 0; i < CLOUDS; i++) {
			clouds[i * 5] = random.nextFloat();
			clouds[i * 5 + 1] = random.nextFloat();
			clouds[i * 5 + 2] = 0.35F + random.nextFloat() * 0.6F;
			clouds[i * 5 + 3] = 0.6F + random.nextFloat() * 0.9F;
			clouds[i * 5 + 4] = random.nextFloat() * 6.28F;
		}
		for (int i = 0; i < SPEED_LINES; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = 1.7 + random.nextDouble() * 4.5;
			speedLines[i * 3] = (float) (Math.cos(angle) * radius);
			speedLines[i * 3 + 1] = (float) (Math.sin(angle) * radius);
			speedLines[i * 3 + 2] = (float) (random.nextDouble() * 70.0);
		}
	}

	// --- 1. Earth from orbit -----------------------------------------------------------------

	Overlay orbit(Matrix4f m, float w, float h, double local, double duration) {
		Overlay overlay = new Overlay();
		double p = local / duration;
		view.viewport(w, h, 34);
		Shapes.Planet earth = new Shapes.Planet(0, 0, 0, 1).tilt(-0.30, 0.08);
		earth.spin = local * 0.0035;
		double distance = MathHelper.lerp(1.0 - Math.pow(1.0 - p, 2.2), 1.5, 3.7);
		double[] facing = new double[3];
		earth.dir(Math.toRadians(30), Math.toRadians(-98 + 180), facing);
		view.lookAt(facing[0] * distance, facing[1] * distance, facing[2] * distance, 0, 0, 0, earth.nx, earth.ny, earth.nz);
		double[] sun = viewSun(-0.72, 0.32, -0.62);

		stars.draw(m, view, 1.0F);
		Shapes.planet(m, view, earth, Textures.EARTH, sun, 0.035F, 48, 96, 0xFFFFFFFF);
		Shapes.Planet cloudLayer = new Shapes.Planet(0, 0, 0, 1.012).tilt(-0.30, 0.08);
		cloudLayer.spin = local * 0.0055 + 0.4;
		Gfx.alpha();
		Shapes.planet(m, view, cloudLayer, Textures.EARTH_CLOUDS, sun, 0.02F, 40, 80, 0xFFFFFFFF);
		Shapes.atmosphere(m, view, earth, sun, 0x6FB6FF, 0.8F, 0.07F);
		sunGlint(m, earth, sun, 0.55F);

		// Target and planet markers.
		double[] target = new double[3];
		earth.dir(Math.toRadians(36), Math.toRadians(-106 + 180), target);
		BufferBuilder b = Gfx.quads();
		float pulse = 0.65F + 0.35F * MathHelper.sin((float) local * 0.9F);
		if (view.project(target[0], target[1], target[2], out)) {
			float x = (float) out[0], y = (float) out[1];
			Gfx.brackets(b, m, x, y, 5, 3, 1, Gfx.fade(Feed.RED, pulse));
			Gfx.diamond(b, m, x, y, 1.6F, Feed.RED);
			overlay.labels.add(new Label(x + 8, y - 4, "TARGET", Feed.RED, "KINETIC LOCK", Feed.GREY));
		}
		if (view.project(0, 0, 0, out)) {
			float x = (float) out[0] + w * 0.13F, y = (float) out[1] - h * 0.17F;
			Gfx.brackets(b, m, x, y, 4, 2.5F, 1, Feed.CYAN);
			overlay.labels.add(new Label(x + 7, y - 4, "EARTH", Feed.CYAN, "6,371 KM", Feed.GREY));
		}
		Gfx.draw(b);
		return overlay;
	}

	// --- 2. Relay jump to Jupiter --------------------------------------------------------------

	Overlay relay(Matrix4f m, float w, float h, double local, double duration) {
		Overlay overlay = new Overlay();
		overlay.header = "[ RELAY · JUPITER 5.2 AU ]";
		overlay.headerColor = Feed.ORANGE;
		double p = local / duration;
		view.viewport(w, h, 70);
		view.lookAt(0, 0, 0, -0.32, 0.06, 1, 0, 1, 0);
		double speed = 1.0 - Math.pow(2 * p - 1, 4);
		double travel = 900.0 * (p - (Math.pow(2 * p - 1, 5) + 1) / 10.0);

		stars.draw(m, view, (float) (1.0 - speed * 0.7));
		BufferBuilder b = Gfx.quads();
		double streak = 2.0 + speed * 60.0;
		for (int i = 0; i < WARP; i++) {
			double x = warp[i * 3], y = warp[i * 3 + 1];
			double z = ((warp[i * 3 + 2] - travel) % 300.0 + 300.0) % 300.0 + 0.6;
			if (!view.project(x, y, z, out) || !view.project(x, y, z + streak, out2)) {
				continue;
			}
			float fade = (float) MathHelper.clamp(1.0 - z / 300.0, 0.0, 1.0);
			float width = (float) MathHelper.clamp(18.0 / z, 0.4, 2.2);
			int head = Gfx.fade(0xFFE8F0FF, 0.25F + 0.75F * fade);
			int tail = Gfx.fade(0xFF8FA8FF, 0.0F);
			Gfx.line(b, m, (float) out2[0], (float) out2[1], (float) out[0], (float) out[1], width, tail, head);
		}
		Gfx.additive();
		Gfx.draw(b);

		if (view.projectDir(0, 0, 1, out)) {
			float x = (float) out[0], y = (float) out[1];
			BufferBuilder glow = Gfx.texQuads();
			float size = (float) (h * (0.12 + 0.25 * speed));
			Gfx.sprite(glow, m, x, y, size, size, 0, Gfx.fade(0xFFB8C8FF, 0.35F));
			Gfx.drawTex(glow, Textures.GLOW);
			Gfx.alpha();
			BufferBuilder marks = Gfx.quads();
			Gfx.brackets(marks, m, x, y, 4, 2.5F, 1, Feed.RED);
			Gfx.draw(marks);
			overlay.labels.add(new Label(x + 7, y - 4, "JUPITER", Feed.RED, "5.2 AU · SS-03", Feed.GREY));
		}
		Gfx.alpha();
		return overlay;
	}

	// --- 3. The accelerator wakes ------------------------------------------------------------

	Overlay wake(Matrix4f m, float w, float h, double local, double duration) {
		Overlay overlay = new Overlay();
		overlay.header = "[ ACCELERATOR WAKING ]";
		overlay.headerColor = Feed.ORANGE;
		double p = local / duration;
		view.viewport(w, h, 30);
		Shapes.Planet jupiter = new Shapes.Planet(0, 0, 0, 1).tilt(0.0, 0.05);
		jupiter.spin = 1.3 + local * 0.006;
		double distance = MathHelper.lerp(Feed.ease(p), 6.2, 5.6);
		double elevation = 0.12;
		double azimuth = -0.4 + p * 0.14;
		view.lookAt(distance * Math.cos(elevation) * Math.sin(azimuth), distance * Math.sin(elevation),
				distance * Math.cos(elevation) * Math.cos(azimuth), 0, 0, 0, 0, 1, 0);
		double[] sun = viewSun(-0.5, 0.2, -0.84);

		stars.draw(m, view, 1.0F);
		Shapes.planet(m, view, jupiter, Textures.JUPITER, sun, 0.03F, 48, 96, 0xFFFFFFFF);
		Shapes.atmosphere(m, view, jupiter, sun, 0xFFD2A0, 0.35F, 0.035F);
		aurora(m, jupiter, (float) (0.55 + 0.25 * Math.sin(local * 0.35)));

		// The ring: dim track all the way round, powered arc sweeping from the breech.
		double breech = leftmostRingAngle(jupiter, 1.22);
		double coilFraction = Math.pow(p, 1.6);
		Shapes.orbit(m, view, jupiter, 1.22, 0, Math.PI * 2, 256, 0.8F, 0x55FF7A1E);
		Gfx.additive();
		Shapes.orbit(m, view, jupiter, 1.22, breech, breech + Math.PI * 2 * coilFraction, 256, 4.0F, 0x40FF7A1E);
		Gfx.alpha();
		Shapes.orbit(m, view, jupiter, 1.22, breech, breech + Math.PI * 2 * coilFraction, 256, 1.4F, 0xFFFF8A2E);

		BufferBuilder b = Gfx.quads();
		jupiter.equator(1.22, breech, tmp);
		if (view.project(tmp[0], tmp[1], tmp[2], out)) {
			float x = (float) out[0], y = (float) out[1];
			Gfx.brackets(b, m, x, y, 4, 2.5F, 1, Feed.RED);
			overlay.labels.add(new Label(x + 7, y - 4, "BREECH", Feed.RED, "SS-03", Feed.GREY));
		}
		if (view.project(0, 0, 0, out)) {
			float x = (float) out[0] + w * 0.06F, y = (float) out[1] + h * 0.09F;
			Gfx.brackets(b, m, x, y, 4, 2.5F, 1, Feed.CYAN);
			overlay.labels.add(new Label(x + 7, y - 4, "JUPITER", Feed.CYAN, "71,492 KM · 318 M⊕", Feed.GREY));
		}
		Gfx.draw(b);

		long coils = 18_759L + (long) ((670_000L - 18_759L) * Math.pow(p, 1.4));
		overlay.footer = "COILS " + Feed.commas(coils) + " / 670,000";
		overlay.title = "THE SHOOTING STAR";
		overlay.subtitle = "SS-03 GUNGNIR · MASS DRIVER · JUPITER · RING 1.22 RJ · 536,000 KM ROUND";
		float in = (float) MathHelper.clamp((p - 0.06) / 0.1, 0.0, 1.0);
		float outFade = (float) MathHelper.clamp((0.78 - p) / 0.14, 0.0, 1.0);
		float flicker = p < 0.2 && ((int) (local * 3.0)) % 3 == 0 ? 0.4F : 1.0F;
		overlay.titleAlpha = in * outFade * flicker;
		return overlay;
	}

	// --- 4. Loading the breech ---------------------------------------------------------------

	Overlay loading(Matrix4f m, float w, float h, double local, double duration) {
		Overlay overlay = new Overlay();
		overlay.header = "[ LOADING ]";
		overlay.headerColor = Feed.WHITE;
		double p = local / duration;
		view.viewport(w, h, 52);
		view.lookAt(2.6 - p * 0.4, 1.2, -6.5 + p * 1.2, 0, 0.1, 7, 0, 1, 0);

		stars.draw(m, view, 1.0F);
		Shapes.Planet jupiter = new Shapes.Planet(0, -58, 48, 52).tilt(0.0, -Math.PI / 2);
		jupiter.spin = 0.6;
		Shapes.planet(m, view, jupiter, Textures.JUPITER, viewSun(-0.3, 0.6, -0.7), 0.05F, 72, 144, 0xFFFFFFFF);

		Shapes.Pose track = new Shapes.Pose();
		for (int k = 0; k < 12; k++) {
			double z = k * 4.0;
			int lit = p > 0.15 + k * 0.05 ? 12 : k == 0 ? (int) (p * 80) : 0;
			Shapes.coil(painter, view, track, z, 1.0, 1.32, 0.35, Math.min(12, lit));
		}
		double slide = 1.0 - Math.pow(1.0 - MathHelper.clamp(p / 0.65, 0.0, 1.0), 3.0);
		Shapes.Pose round = new Shapes.Pose().at(0, 0, MathHelper.lerp(slide, -7.0, -1.2)).facing(0, 0, 1, 0, 1, 0).scale(3.2);
		Shapes.projectile(painter, view, round, (float) p);
		painter.flush(m);
		return overlay;
	}

	// --- 5. Seven laps of the ring -----------------------------------------------------------

	Overlay laps(Matrix4f m, float w, float h, double age) {
		Overlay overlay = new Overlay();
		double progress = StrikeTimeline.lapProgress(age);
		double velocity = StrikeTimeline.lapVelocity(progress);
		double covered = StrikeTimeline.lapDistance(progress);
		int lap = StrikeTimeline.lapNumber(progress);
		overlay.header = "[ LAP " + lap + " / " + StrikeTimeline.LAP_COUNT + " ]";
		overlay.footer = String.format(Locale.ROOT, "VELOCITY %.4f c", velocity);
		overlay.bars = (b, mm, ww, hh) -> lapBars(b, mm, ww, hh, covered, lap, age);

		double spacing = 5.0;
		double travel = covered * StrikeTimeline.LAP_COUNT * 60.0 * spacing;
		view.viewport(w, h, 50 + 30 * velocity);
		double shake = velocity * velocity * 0.05;
		double jx = Math.sin(age * 3.1) * shake;
		double jy = Math.cos(age * 2.3) * shake;
		view.lookAt(0.42 + jx, 0.48 + jy, travel - 3.4, 0, 0.02, travel + 9.0, 0, 1, 0);
		view.roll(Math.sin(age * 0.05) * 0.04);

		stars.draw(m, view, 1.0F);
		Shapes.Planet jupiter = new Shapes.Planet(0, -44, travel + 26, 40).tilt(0.0, -Math.PI / 2);
		jupiter.spin = -travel * 0.004;
		Shapes.planet(m, view, jupiter, Textures.JUPITER, viewSun(-0.4, 0.55, -0.73), 0.05F, 72, 144, 0xFFFFFFFF);

		Shapes.Pose track = new Shapes.Pose();
		int first = (int) Math.floor((travel - 4.0) / spacing);
		for (int k = first; k < first + 13; k++) {
			Shapes.coil(painter, view, track, k * spacing, 1.0, 1.3, 0.35, 12);
		}
		Shapes.Pose round = new Shapes.Pose().at(0, 0, travel + 1.4).facing(0, 0, 1, 0, 1, 0).scale(3.0);
		Shapes.projectile(painter, view, round, (float) velocity);
		painter.flush(m);

		// Speed lines once the round is really moving.
		if (velocity > 0.2) {
			BufferBuilder b = Gfx.quads();
			double length = 0.5 + velocity * 16.0;
			float alpha = (float) MathHelper.clamp((velocity - 0.2) * 1.4, 0.0, 1.0);
			for (int i = 0; i < SPEED_LINES; i++) {
				double x = speedLines[i * 3], y = speedLines[i * 3 + 1];
				double z = ((speedLines[i * 3 + 2] - travel * 1.3) % 70.0 + 70.0) % 70.0 + travel - 3.0;
				if (!view.project(x, y, z, out) || !view.project(x, y, z + length, out2)) {
					continue;
				}
				int color = i % 4 == 0 ? 0xFFFFB070 : 0xFFF4F4FF;
				Gfx.line(b, m, (float) out[0], (float) out[1], (float) out2[0], (float) out2[1], 1.0F,
						Gfx.fade(color, alpha * 0.8F), Gfx.fade(color, 0.0F));
			}
			Gfx.additive();
			Gfx.draw(b);
			Gfx.alpha();
		}
		return overlay;
	}

	private static void lapBars(BufferBuilder b, Matrix4f m, float w, float h, double covered, int lap, double age) {
		float width = Math.min(160, w * 0.4F);
		float x0 = (w - width) / 2;
		float y = h - 24;
		Gfx.rect(b, m, x0, y, x0 + width, y + 1, 0x55FFFFFF);
		Gfx.rect(b, m, x0, y, x0 + width * (float) covered, y + 1, Feed.RED);
		float dash = width / StrikeTimeline.LAP_COUNT;
		for (int i = 0; i < StrikeTimeline.LAP_COUNT; i++) {
			int color = i < lap - 1 ? Feed.RED : i == lap - 1 ? ((int) (age * 0.5) % 2 == 0 ? Feed.RED : 0xFF7A2A20)
					: 0x55FFFFFF;
			float dx = x0 + i * dash + 1.5F;
			Gfx.rect(b, m, dx, y + 5, dx + dash - 3, y + 7, color);
		}
	}

	// --- 6. Through the main belt ------------------------------------------------------------

	Overlay debris(Matrix4f m, float w, float h, double local, double duration) {
		Overlay overlay = new Overlay();
		overlay.header = "[ DEBRIS FIELD · MAIN BELT ]";
		double p = local / duration;
		long range = 728_960_798L - (long) (p * 512_000_000L);
		overlay.footer = "RANGE " + Feed.commas(range) + " KM";
		overlay.footerSmall = String.format(Locale.ROOT, "VELOCITY %.4f c", 0.9612 + 0.0112 * p);
		view.viewport(w, h, 64);
		view.lookAt(0, 0, 0, 0, 0, 1, 0, 1, 0);
		view.roll(local * 0.012);
		double travel = local * 9.0;

		// Blue haze tunnel.
		BufferBuilder bg = Gfx.quads();
		float cx = w / 2, cy = h / 2;
		float reach = (float) Math.hypot(w, h) * 0.6F;
		Gfx.ring(bg, m, cx, cy, 0, reach * 0.35F, 0xFF0A1220, 0xFF1A2840, 48);
		Gfx.ring(bg, m, cx, cy, reach * 0.35F, reach, 0xFF1A2840, 0xFF6C7E9C, 48);
		Gfx.draw(bg);
		stars.draw(m, view, 0.5F);

		BufferBuilder haze = Gfx.texQuads();
		for (int i = 0; i < 40; i++) {
			double angle = i * 2.399963;
			double z = ((i * 7.3 - travel * 0.6) % 40.0 + 40.0) % 40.0 + 1.0;
			double radius = 7.0 + (i % 5);
			if (!view.project(Math.cos(angle) * radius, Math.sin(angle) * radius, z, out)) {
				continue;
			}
			float size = (float) (view.focal * 4.5 / z);
			float alpha = (float) (0.22 * MathHelper.clamp(1.0 - z / 40.0, 0.0, 1.0));
			Gfx.sprite(haze, m, (float) out[0], (float) out[1], size, size, (float) angle, Gfx.fade(0xFFC8D6EE, alpha));
		}
		Gfx.drawTex(haze, Textures.CLOUD);

		BufferBuilder streaks = Gfx.quads();
		for (int i = 0; i < WARP; i += 2) {
			double x = warp[i * 3] * 0.6, y = warp[i * 3 + 1] * 0.6;
			double z = ((warp[i * 3 + 2] * 0.5 - travel * 2.2) % 150.0 + 150.0) % 150.0 + 0.6;
			if (!view.project(x, y, z, out) || !view.project(x, y, z + 14, out2)) {
				continue;
			}
			float fade = (float) MathHelper.clamp(1.0 - z / 150.0, 0.0, 1.0);
			Gfx.line(streaks, m, (float) out2[0], (float) out2[1], (float) out[0], (float) out[1], 0.8F,
					Gfx.fade(0xFFFFFFFF, 0.0F), Gfx.fade(0xFFF0F4FF, 0.7F * fade));
		}
		Gfx.additive();
		Gfx.draw(streaks);
		Gfx.alpha();

		// Rocks rushing past, sorted with the round.
		float[] xs = new float[3];
		float[] ys = new float[3];
		int[] cs = new int[3];
		for (int i = 0; i < ROCKS; i++) {
			double x = rocks[i * 6], y = rocks[i * 6 + 1];
			double z = ((rocks[i * 6 + 2] - travel) % 160.0 + 160.0) % 160.0 + 0.8;
			if (!view.project(x, y, z, out)) {
				continue;
			}
			double size = rocks[i * 6 + 3];
			float radius = (float) (view.focal * size / z);
			if (radius < 0.5F || !view.onScreen(out[0], out[1], radius)) {
				continue;
			}
			float spin = rocks[i * 6 + 4] + (float) local * 0.05F * (i % 2 == 0 ? 1 : -1);
			float tone = 0.55F + rocks[i * 6 + 5] * 0.3F;
			int base = Gfx.argb(1.0F, 0.42F * tone, 0.39F * tone, 0.36F * tone);
			int lit = Gfx.argb(1.0F, 0.78F * tone, 0.74F * tone, 0.7F * tone);
			int shadow = Gfx.argb(1.0F, 0.12F * tone, 0.11F * tone, 0.11F * tone);
			int sides = 9;
			for (int s = 0; s < sides; s++) {
				double a0 = Math.PI * 2 * s / sides + spin;
				double a1 = Math.PI * 2 * (s + 1) / sides + spin;
				float r0 = radius * rockRadius(i, s);
				float r1 = radius * rockRadius(i, (s + 1) % sides);
				xs[0] = (float) out[0];
				ys[0] = (float) out[1];
				xs[1] = (float) (out[0] + Math.cos(a0) * r0);
				ys[1] = (float) (out[1] + Math.sin(a0) * r0);
				xs[2] = (float) (out[0] + Math.cos(a1) * r1);
				ys[2] = (float) (out[1] + Math.sin(a1) * r1);
				cs[0] = base;
				cs[1] = rockShade(a0, lit, shadow);
				cs[2] = rockShade(a1, lit, shadow);
				painter.projected(z, xs, ys, cs, 3);
			}
		}
		Shapes.Pose round = new Shapes.Pose().at(0, -0.32, 3.4).facing(0, 0, 1, 0, 1, 0).roll(0.2 + local * 0.03).scale(2.2);
		Shapes.projectile(painter, view, round, 1.0F);
		painter.flush(m);

		// The round's hot tail.
		round.apply(0, 0, -0.5, tmp, 0);
		if (view.project(tmp[0], tmp[1], tmp[2], out)) {
			BufferBuilder glow = Gfx.texQuads();
			float size = (float) (view.focal * 0.25 / out[2]);
			Gfx.sprite(glow, m, (float) out[0], (float) out[1], size, size, 0, 0xCCFFE6C0);
			Gfx.sprite(glow, m, (float) out[0], (float) out[1], size * 2.6F, size * 2.6F, 0, 0x55FF8A3A);
			Gfx.additive();
			Gfx.drawTex(glow, Textures.GLOW);
			Gfx.alpha();
		}

		if (view.projectDir(0, 0, 1, out)) {
			float x = (float) out[0] + 18, y = (float) out[1] - 22;
			BufferBuilder marks = Gfx.quads();
			Gfx.brackets(marks, m, x, y, 4, 2.5F, 1, Feed.CYAN);
			Gfx.draw(marks);
			overlay.labels.add(new Label(x + 7, y - 4, "EARTH", Feed.CYAN, "TARGET", Feed.GREY));
		}
		return overlay;
	}

	private static float rockRadius(int rock, int vertex) {
		long h = rock * 7919L + vertex * 104729L;
		h = (h ^ (h >>> 7)) * 0x9E3779B97F4A7C15L;
		return 0.7F + ((h >>> 40) & 0xFF) / 255.0F * 0.3F;
	}

	private static int rockShade(double angle, int lit, int shadow) {
		float k = (float) (0.5 + 0.5 * Math.cos(angle - Math.toRadians(-135)));
		return Gfx.lerpColor(k, shadow, lit);
	}

	// --- 7. Terminal: re-entry over Sol-3 ----------------------------------------------------

	Overlay terminal(Matrix4f m, float w, float h, double local, double duration) {
		Overlay overlay = new Overlay();
		overlay.header = "[ TERMINAL · SOL-3 ]";
		double p = local / duration;
		long range = (long) (1240 * Math.pow(1.0 - p, 1.6));
		overlay.footer = "RANGE " + Feed.commas(range) + " KM";
		overlay.footerSmall = "VELOCITY 0.9724 c";
		float shakeX = (float) (Math.sin(local * 4.7) * 2.0);
		float shakeY = (float) (Math.cos(local * 3.9) * 1.5);

		BufferBuilder sky = Gfx.quads();
		Gfx.rectV(sky, m, 0, 0, w, h * 0.55F, 0xFF2E3A5C, 0xFF8C7E8E);
		Gfx.rectV(sky, m, 0, h * 0.55F, w, h, 0xFF8C7E8E, 0xFFE8BC98);
		Gfx.draw(sky);

		// Cloud decks streaming past as the round dives down and to the left.
		BufferBuilder cloudBuf = Gfx.texQuads();
		for (int i = 0; i < CLOUDS; i++) {
			float speed = clouds[i * 5 + 3];
			float fx = ((clouds[i * 5] - (float) local * 0.045F * speed) % 1.4F + 1.4F) % 1.4F - 0.2F;
			float fy = (clouds[i * 5 + 1] - (float) local * 0.025F * speed) % 1.4F;
			if (fy < -0.2F) {
				fy += 1.4F;
			}
			float size = clouds[i * 5 + 2] * h;
			Gfx.sprite(cloudBuf, m, fx * w + shakeX, fy * h + shakeY, size * 1.5F, size, clouds[i * 5 + 4],
					Gfx.fade(0xFFF4E8E2, 0.55F + 0.3F * (i % 3) / 2.0F));
		}
		Gfx.drawTex(cloudBuf, Textures.CLOUD);

		view.viewport(w, h, 45);
		view.lookAt(0.0, 0.0, -4.2, 0, 0, 0, 0, 1, 0);
		Shapes.Pose round = new Shapes.Pose().at(0.25, 0.1, 0).facing(-0.86, -0.42, 0.28, 0, 1, 0).roll(local * 0.05).scale(2.6);
		round.apply(0, 0, 0.5, tmp, 0);
		boolean noseVisible = view.project(tmp[0], tmp[1], tmp[2], out);
		float nx = (float) out[0] + shakeX;
		float ny = (float) out[1] + shakeY;
		round.apply(0, 0, -0.5, tmp, 0);
		view.project(tmp[0], tmp[1], tmp[2], out2);

		// Shock cone trailing from the nose.
		if (noseVisible) {
			BufferBuilder shock = Gfx.quads();
			float dx = (float) (out2[0] - (nx - shakeX));
			float dy = (float) (out2[1] - (ny - shakeY));
			for (int i = -6; i <= 6; i++) {
				double spread = i * 0.09;
				float c = (float) Math.cos(spread), s = (float) Math.sin(spread);
				float ex = nx + (dx * c - dy * s) * 1.8F;
				float ey = ny + (dx * s + dy * c) * 1.8F;
				Gfx.line(shock, m, nx, ny, ex, ey, 1.2F, 0x99FFF2E0, 0x00FFFFFF);
			}
			Gfx.additive();
			Gfx.draw(shock);
			Gfx.alpha();
		}

		Shapes.projectile(painter, view, round, 1.0F);
		painter.flush(m);

		if (noseVisible) {
			float pulse = 0.85F + 0.15F * MathHelper.sin((float) local * 2.3F);
			BufferBuilder plasma = Gfx.texQuads();
			Gfx.sprite(plasma, m, nx, ny, h * 0.55F * pulse, h * 0.55F * pulse, 0, 0x88FF9A50);
			Gfx.sprite(plasma, m, nx, ny, h * 0.28F, h * 0.28F, 0, 0xDDFFE8C8);
			Gfx.sprite(plasma, m, nx, ny, h * 0.12F, h * 0.12F, 0, 0xFFFFFFFF);
			Gfx.additive();
			Gfx.drawTex(plasma, Textures.GLOW);
			Gfx.alpha();
		}

		// White-out into the world view.
		float white = (float) MathHelper.clamp((p - 0.72) / 0.28, 0.0, 1.0);
		if (white > 0) {
			BufferBuilder b = Gfx.quads();
			Gfx.rect(b, m, 0, 0, w, h, Gfx.fade(0xFFFFFFFF, white * white));
			Gfx.draw(b);
		}
		return overlay;
	}

	// --- helpers -----------------------------------------------------------------------------

	/** A sun direction expressed in the current camera's basis (right, up, forward). */
	private double[] viewSun(double right, double up, double forward) {
		double x = right * view.rx + up * view.ux + forward * view.fx;
		double y = right * view.ry + up * view.uy + forward * view.fy;
		double z = right * view.rz + up * view.uz + forward * view.fz;
		double l = Math.sqrt(x * x + y * y + z * z);
		return new double[] {x / l, y / l, z / l};
	}

	/** Specular glint where the sun reflects off the planet towards the camera. */
	private void sunGlint(Matrix4f m, Shapes.Planet planet, double[] sun, float strength) {
		double vx = view.ex - planet.x, vy = view.ey - planet.y, vz = view.ez - planet.z;
		double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
		double hx = sun[0] + vx / vl, hy = sun[1] + vy / vl, hz = sun[2] + vz / vl;
		double hl = Math.sqrt(hx * hx + hy * hy + hz * hz);
		hx /= hl;
		hy /= hl;
		hz /= hl;
		double px = planet.x + hx * planet.r, py = planet.y + hy * planet.r, pz = planet.z + hz * planet.r;
		if (!view.project(px, py, pz, out)) {
			return;
		}
		double r = view.sphereRadius(planet.r, planet.distanceTo(view));
		BufferBuilder glow = Gfx.texQuads();
		float size = (float) (r * 0.16);
		Gfx.sprite(glow, m, (float) out[0], (float) out[1], size, size, 0, Gfx.fade(0xFFFFF4DC, strength));
		Gfx.additive();
		Gfx.drawTex(glow, Textures.GLOW);
		Gfx.alpha();
	}

	private void aurora(Matrix4f m, Shapes.Planet planet, float strength) {
		BufferBuilder b = Gfx.quads();
		double[] d = new double[3];
		boolean havePrev = false;
		float px = 0, py = 0;
		for (int k = 0; k <= 96; k++) {
			planet.dir(Math.toRadians(73), Math.PI * 2 * k / 96, d);
			double x = planet.x + d[0] * planet.r * 1.015;
			double y = planet.y + d[1] * planet.r * 1.015;
			double z = planet.z + d[2] * planet.r * 1.015;
			boolean visible = !planet.occludes(view, x, y, z) && view.project(x, y, z, out);
			if (visible && havePrev) {
				Gfx.line(b, m, px, py, (float) out[0], (float) out[1], 3.0F, Gfx.fade(0xFF9CFFE0, strength * 0.5F));
			}
			if (visible) {
				px = (float) out[0];
				py = (float) out[1];
			}
			havePrev = visible;
		}
		Gfx.additive();
		Gfx.draw(b);
		Gfx.alpha();
	}

	/** Angle on the ring that appears furthest to the left on screen. */
	private double leftmostRingAngle(Shapes.Planet planet, double radius) {
		double best = 0;
		double bestX = Double.MAX_VALUE;
		for (int k = 0; k < 72; k++) {
			double angle = Math.PI * 2 * k / 72;
			planet.equator(radius, angle, tmp);
			if (view.project(tmp[0], tmp[1], tmp[2], out) && out[0] < bestX && !planet.occludes(view, tmp[0], tmp[1], tmp[2])) {
				bestX = out[0];
				best = angle;
			}
		}
		return best;
	}
}
