package io.github.kortev.shootingstar.client.world;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import io.github.kortev.shootingstar.client.ClientStrikes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/**
 * One impact as this client sees it. The fireball, the condensation shell and the shock front are
 * functions of time; the debris thrown out of the bowl, the fire and dust clouds and the sparks are
 * simulated a tick at a time. Everything scales with the crater radius.
 */
public final class ImpactScene {
	static final double GRAVITY = 0.06;
	private static final int LIFETIME = 900;

	static final class Chunk {
		double x;
		double y;
		double z;
		double px;
		double py;
		double pz;
		double vx;
		double vy;
		double vz;
		float ax;
		float ay;
		float az;
		float angle;
		float prevAngle;
		float spin;
		float size;
		int sprite;
		float heat;
		boolean landed;
		boolean bounced;
		boolean trail;
		int landedAge;
	}

	static final class Puff {
		double x;
		double y;
		double z;
		double px;
		double py;
		double pz;
		double vx;
		double vy;
		double vz;
		float size;
		float prevSize;
		float growth;
		float r;
		float g;
		float b;
		float alpha;
		float glow;
		float glowDecay;
		float seed;
		float spin;
		float buoyancy;
		float drag;
		/** Height above the impact where a column puff stops rising and spreads into the cap; 0 for none. */
		double cap;
		int age;
		int life;
	}

	static final class Spark {
		double x;
		double y;
		double z;
		double px;
		double py;
		double pz;
		double vx;
		double vy;
		double vz;
		float size;
		int age;
		int life;
		/** A glowing ember drifting up out of the bowl rather than a spark flung out of it. */
		boolean ember;
		float seed;
	}

	/** Lightning in the ash cloud: a jagged main channel with a fork or two, flickering for a few ticks. */
	static final class Bolt {
		final List<Vec3d> channel;
		final List<List<Vec3d>> forks;
		final Vec3d middle;
		final int life;
		int age;

		Bolt(List<Vec3d> channel, List<List<Vec3d>> forks, int life) {
			this.channel = channel;
			this.forks = forks;
			this.middle = channel.get(channel.size() / 2);
			this.life = life;
		}

		/** Return strokes flicker: on, dim, on again, fading. */
		float brightness() {
			float fade = 1.0F - (float) age / life;
			return (age == 1 || age == 3 && life > 4 ? 0.35F : 1.0F) * fade;
		}
	}

	final Vec3d center;
	final int radius;
	final double scorch;
	final double bowl;
	final double waveTicks;
	/** Size factor relative to the default 64-block crater. */
	final double scale;
	final boolean terrain;
	final List<Chunk> chunks = new ArrayList<>();
	final List<Puff> puffs = new ArrayList<>();
	final List<Spark> sparks = new ArrayList<>();
	final List<Bolt> bolts = new ArrayList<>();
	final List<Sprite> sprites = new ArrayList<>();
	final List<float[]> tints = new ArrayList<>();
	private final Random random;
	private final double capBase;
	private int nextBolt = 55;
	/** Ticks since the hit; the first tick after the scene is made is tick 0. */
	int age = -1;

	public ImpactScene(MinecraftClient client, ClientWorld world, Vec3d center, int radius, boolean terrain) {
		this.center = center;
		this.radius = radius;
		this.scorch = Math.round(radius * 1.5F);
		this.bowl = Math.max(4, Math.round(radius * 0.55F));
		this.waveTicks = Math.max(18, Math.round(radius * 0.75F));
		this.scale = radius / 64.0;
		this.terrain = terrain;
		this.random = new Random(Double.doubleToLongBits(center.x * 31 + center.z));
		this.capBase = radius * 1.7;
		sampleSprites(client, world);
	}

	/** Ticks since the hit, with the partial tick. */
	public double time(float tickDelta) {
		return age + tickDelta;
	}

	public boolean done() {
		return age > LIFETIME || age > 600 && chunks.isEmpty() && puffs.isEmpty() && sparks.isEmpty() && bolts.isEmpty();
	}

	// --- blast shapes ----------------------------------------------------------------------

	/** Radius of the shock front {@code e} ticks after impact: the server's carving front, then a dust wave. */
	double front(double e) {
		if (e <= 0) {
			return 0;
		}
		if (e < waveTicks) {
			return scorch * Math.pow(e / waveTicks, 0.45);
		}
		double v0 = 0.45 * scorch / waveTicks;
		double tau = waveTicks * 1.2;
		return scorch + v0 * tau * (1.0 - Math.exp(-(e - waveTicks) / tau));
	}

	/** Speed of the front in blocks per tick. */
	double frontSpeed(double e) {
		return (front(e + 0.5) - front(e - 0.5));
	}

	/** When the shock reaches {@code distance}; past the dust wave it travels at the speed of sound. */
	public double arrival(double distance) {
		if (distance <= 0) {
			return 0;
		}
		if (distance <= scorch) {
			return waveTicks * Math.pow(distance / scorch, 1.0 / 0.45);
		}
		double v0 = 0.45 * scorch / waveTicks;
		double tau = waveTicks * 1.2;
		double reach = v0 * tau;
		double past = distance - scorch;
		if (past < reach * 0.95) {
			return waveTicks - tau * Math.log(1.0 - past / reach);
		}
		return waveTicks - tau * Math.log(0.05) + (past - reach * 0.95) / 17.0;
	}

	/** Radius of the fireball dome. */
	double domeRadius(double e) {
		return radius * 0.62 * (1.0 - Math.exp(-Math.max(0, e) / 2.6)) * (1.0 + 0.0015 * Math.max(0, e - 10));
	}

	/** Brightness of the fireball: blinding, then cooling. */
	double domeIntensity(double e) {
		if (e < 0) {
			return 0;
		}
		return e < 5 ? 1.8 : 1.8 * Math.exp(-(e - 5) / 14.0);
	}

	// --- simulation ------------------------------------------------------------------------

	public void tick(ClientWorld world) {
		age++;
		if (terrain && age < 3) {
			launchChunks(world, (int) MathHelper.clamp(radius * 10.0, 150, 1000) / 3);
		}
		if (age == 0) {
			launchSparks();
		}
		spawnColumn();
		spawnDust(world);
		spawnEmbers();
		lightning(world);

		for (Iterator<Chunk> it = chunks.iterator(); it.hasNext(); ) {
			Chunk c = it.next();
			if (stepChunk(world, c)) {
				it.remove();
			}
		}
		for (Iterator<Puff> it = puffs.iterator(); it.hasNext(); ) {
			Puff p = it.next();
			if (stepPuff(p)) {
				it.remove();
			}
		}
		for (Iterator<Spark> it = sparks.iterator(); it.hasNext(); ) {
			Spark s = it.next();
			s.px = s.x;
			s.py = s.y;
			s.pz = s.z;
			if (s.ember) {
				// Carried up by the heat, wandering on the eddies.
				s.vx = s.vx * 0.96 + (random.nextDouble() - 0.5) * 0.03;
				s.vz = s.vz * 0.96 + (random.nextDouble() - 0.5) * 0.03;
				s.vy = s.vy * 0.97 + 0.006;
			} else {
				s.vy -= GRAVITY;
				s.vx *= 0.98;
				s.vy *= 0.98;
				s.vz *= 0.98;
			}
			s.x += s.vx;
			s.y += s.vy;
			s.z += s.vz;
			if (++s.age > s.life) {
				it.remove();
			}
		}
	}

	private void launchChunks(ClientWorld world, int count) {
		double k = Math.sqrt(scale);
		for (int i = 0; i < count; i++) {
			Chunk c = new Chunk();
			double r = Math.sqrt(random.nextDouble()) * bowl;
			double a = random.nextDouble() * Math.PI * 2;
			c.x = c.px = center.x + Math.cos(a) * r;
			c.z = c.pz = center.z + Math.sin(a) * r;
			c.y = c.py = center.y + 0.5 + random.nextDouble() * 2.0;
			double out = (0.45 + 2.0 * Math.pow(random.nextDouble(), 1.5)) * k;
			double up = (0.9 + 2.6 * random.nextDouble()) * k;
			c.vx = Math.cos(a) * out;
			c.vz = Math.sin(a) * out;
			c.vy = up;
			double roll = random.nextDouble();
			c.size = (float) (roll < 0.15 ? 1.2 + random.nextDouble() * 1.4 : roll < 0.7 ? 0.5 + random.nextDouble() * 0.7
					: 0.2 + random.nextDouble() * 0.3);
			c.heat = (float) (1.0 - 0.6 * r / bowl);
			c.trail = c.size > 1.3F && random.nextDouble() < 0.8;
			float ax = (float) random.nextGaussian();
			float ay = (float) random.nextGaussian();
			float az = (float) random.nextGaussian();
			float len = (float) Math.sqrt(ax * ax + ay * ay + az * az) + 1.0E-4F;
			c.ax = ax / len;
			c.ay = ay / len;
			c.az = az / len;
			c.spin = (float) ((0.1 + random.nextDouble() * 0.4) / Math.max(0.5, c.size)) * (random.nextBoolean() ? 1 : -1);
			c.angle = c.prevAngle = (float) (random.nextDouble() * Math.PI * 2);
			c.sprite = sprites.isEmpty() ? 0 : random.nextInt(sprites.size());
			chunks.add(c);
		}
	}

	/** Moves a chunk; returns true when it is gone. */
	private boolean stepChunk(ClientWorld world, Chunk c) {
		c.px = c.x;
		c.py = c.y;
		c.pz = c.z;
		c.prevAngle = c.angle;
		if (c.landed) {
			c.heat *= 0.97F;
			return ++c.landedAge > 160;
		}
		c.vy -= GRAVITY;
		c.vx *= 0.995;
		c.vy *= 0.995;
		c.vz *= 0.995;
		c.x += c.vx;
		c.y += c.vy;
		c.z += c.vz;
		c.angle += c.spin;
		c.heat *= 0.985F;
		if (c.trail && c.heat > 0.1F) {
			// Overlapping puffs every tick, so the trail reads as one smoky arc rather than a string of beads.
			Puff p = puff(c.x - c.vx * 0.5, c.y - c.vy * 0.5, c.z - c.vz * 0.5, 0.85 * c.size, 0.2F, 0.18F, 0.17F, 0.45F,
					45 + random.nextInt(30));
			p.glow = c.heat * 1.4F;
			p.glowDecay = 0.86F;
			p.growth = 0.09F;
			p.drag = 0.9F;
			p.buoyancy = 0.006F;
		}
		int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(c.x), MathHelper.floor(c.z));
		if (c.vy < 0 && c.y - c.size * 0.5 < ground) {
			if (c.size > 0.45F && (!c.bounced || c.vy < -0.3)) {
				// A burst of dust where it hits, glowing if the rock is still hot.
				float tone = 0.36F + random.nextFloat() * 0.12F;
				Puff p = puff(c.x, ground + 0.4, c.z, 0.5 + c.size * 0.9, tone, tone * 0.86F, tone * 0.72F, 0.4F,
						35 + random.nextInt(35));
				p.vx = c.vx * 0.2;
				p.vz = c.vz * 0.2;
				p.vy = 0.06;
				p.growth = 0.04F + c.size * 0.02F;
				p.drag = 0.88F;
				p.buoyancy = 0.003F;
				p.glow = c.heat > 0.4F ? c.heat * 0.9F : 0.0F;
				p.glowDecay = 0.85F;
			}
			if (c.bounced || c.vy > -0.5) {
				c.landed = true;
				c.y = ground + c.size * 0.35;
			} else {
				c.bounced = true;
				c.y = ground + c.size * 0.5;
				c.vy = -c.vy * 0.25;
				c.vx *= 0.45;
				c.vz *= 0.45;
				c.spin *= 0.5F;
			}
		}
		return c.y < world.getBottomY() - 16 || age > LIFETIME;
	}

	private void launchSparks() {
		int count = (int) MathHelper.clamp(380 * Math.sqrt(scale), 120, 600);
		double k = Math.sqrt(scale);
		for (int i = 0; i < count; i++) {
			Spark s = new Spark();
			s.x = s.px = center.x + random.nextGaussian() * bowl * 0.25;
			s.y = s.py = center.y + 1.0 + random.nextDouble() * 3.0;
			s.z = s.pz = center.z + random.nextGaussian() * bowl * 0.25;
			double a = random.nextDouble() * Math.PI * 2;
			double tilt = Math.acos(1.0 - random.nextDouble() * 0.9);
			double speed = (1.4 + 3.6 * random.nextDouble()) * k;
			s.vx = Math.cos(a) * Math.sin(tilt) * speed;
			s.vz = Math.sin(a) * Math.sin(tilt) * speed;
			s.vy = Math.cos(tilt) * speed;
			s.size = (float) (0.18 + random.nextDouble() * 0.3);
			s.life = 14 + random.nextInt(34);
			sparks.add(s);
		}
	}

	/**
	 * Volcanic lightning: the churning ash in the column builds up charge and every few seconds it
	 * discharges, inside the cloud or now and then down to the rim. Thunder follows at the speed of
	 * sound.
	 */
	private void lightning(ClientWorld world) {
		for (Bolt bolt : bolts) {
			bolt.age++;
		}
		bolts.removeIf(b -> b.age > b.life);
		if (age < 50 || age > 560 || age < nextBolt) {
			return;
		}
		nextBolt = age + 22 + random.nextInt(age < 200 ? 40 : 90);
		double spread = radius * 0.32;
		Vec3d from = center.add(gaussian() * spread, capBase * (0.55 + 0.4 * random.nextDouble()), gaussian() * spread);
		Vec3d to;
		if (random.nextDouble() < 0.3) {
			// Down to the ground at the rim.
			double a = random.nextDouble() * Math.PI * 2;
			double r = radius * (0.7 + 0.5 * random.nextDouble());
			int x = MathHelper.floor(center.x + Math.cos(a) * r);
			int z = MathHelper.floor(center.z + Math.sin(a) * r);
			to = new Vec3d(x + 0.5, world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z), z + 0.5);
		} else {
			to = from.add(gaussian() * radius * 0.5, (random.nextDouble() - 0.6) * radius * 0.5, gaussian() * radius * 0.5);
		}
		List<Vec3d> channel = jagged(from, to, 0.22, 6);
		List<List<Vec3d>> forks = new ArrayList<>();
		for (int f = 0; f < 1 + random.nextInt(3); f++) {
			Vec3d start = channel.get(random.nextInt(channel.size() * 3 / 4));
			Vec3d dir = to.subtract(from).normalize().add(gaussian() * 0.8, gaussian() * 0.5, gaussian() * 0.8).normalize();
			forks.add(jagged(start, start.add(dir.multiply(from.distanceTo(to) * (0.15 + 0.25 * random.nextDouble()))), 0.3, 4));
		}
		Bolt bolt = new Bolt(channel, forks, 4 + random.nextInt(4));
		bolts.add(bolt);
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			double distance = client.gameRenderer.getCamera().getPos().distanceTo(bolt.middle);
			ClientStrikes.at(client, bolt.middle, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 10.0F,
					0.65F + random.nextFloat() * 0.3F, (int) (distance / 17.0));
		}
	}

	/** A lightning channel from a to b by midpoint displacement. */
	private List<Vec3d> jagged(Vec3d a, Vec3d b, double roughness, int depth) {
		List<Vec3d> points = new ArrayList<>();
		points.add(a);
		subdivide(points, a, b, roughness, depth);
		return points;
	}

	private void subdivide(List<Vec3d> points, Vec3d a, Vec3d b, double roughness, int depth) {
		if (depth == 0) {
			points.add(b);
			return;
		}
		Vec3d d = b.subtract(a);
		double len = d.length();
		Vec3d jitter = new Vec3d(gaussian(), gaussian(), gaussian());
		Vec3d axis = d.multiply(1.0 / Math.max(len, 1.0E-6));
		jitter = jitter.subtract(axis.multiply(jitter.dotProduct(axis)));
		Vec3d mid = a.add(d.multiply(0.5)).add(jitter.multiply(len * roughness * 0.5));
		subdivide(points, a, mid, roughness * 0.75, depth - 1);
		subdivide(points, mid, b, roughness * 0.75, depth - 1);
	}

	private double gaussian() {
		return random.nextGaussian();
	}

	/**
	 * The path the round tore through the sky stays behind as a trail of hot, glowing air that cools
	 * into a long smoke line and drifts apart on the wind.
	 */
	public void trail(Vec3d dir, double length) {
		double reach = Math.min(length, 620.0);
		double windX = (random.nextDouble() - 0.5) * 0.06;
		double windZ = (random.nextDouble() - 0.5) * 0.06;
		for (double d = 3.0; d < reach; d += 3.0 + d * 0.012) {
			Vec3d p = center.add(dir.multiply(d));
			double size = 1.2 + d * 0.012;
			float tone = 0.6F + random.nextFloat() * 0.08F;
			Puff puff = puff(p.x + gaussian() * size * 0.3, p.y + gaussian() * size * 0.3, p.z + gaussian() * size * 0.3, size,
					tone, tone * 0.97F, tone * 0.94F, 0.32F, 380 + random.nextInt(260));
			puff.vx = windX * (0.5 + d / reach) + gaussian() * 0.01;
			puff.vz = windZ * (0.5 + d / reach) + gaussian() * 0.01;
			puff.vy = 0.0;
			puff.drag = 0.995F;
			puff.buoyancy = 0.0005F;
			puff.growth = (float) (0.01 + size * 0.004);
			puff.glow = 2.2F * (float) (1.0 - d / reach * 0.6);
			puff.glowDecay = 0.93F;
		}
	}

	/** Embers rising off the molten bowl for as long as it glows. */
	private void spawnEmbers() {
		if (age < 8 || age > 760 || !terrain) {
			return;
		}
		double rate = (age < 80 ? 7.0 : 2.5) * Math.sqrt(scale) * (1.0 - Math.max(0, age - 500) / 260.0);
		int n = (int) rate + (random.nextDouble() < rate - (int) rate ? 1 : 0);
		for (int i = 0; i < n; i++) {
			Spark s = new Spark();
			double r = Math.sqrt(random.nextDouble()) * bowl * 0.85;
			double a = random.nextDouble() * Math.PI * 2;
			s.x = s.px = center.x + Math.cos(a) * r;
			s.z = s.pz = center.z + Math.sin(a) * r;
			s.y = s.py = center.y - bowl * 0.3 * (1.0 - r / bowl) + 1.0 + random.nextDouble() * 2.0;
			s.vx = (random.nextDouble() - 0.5) * 0.08;
			s.vz = (random.nextDouble() - 0.5) * 0.08;
			s.vy = 0.12 + random.nextDouble() * 0.25;
			s.size = (float) (0.07 + random.nextDouble() * 0.1);
			s.life = 50 + random.nextInt(80);
			s.ember = true;
			s.seed = random.nextFloat() * 6.28F;
			sparks.add(s);
		}
	}

	/** Fireball, then the rising column that spreads into a cap, then the bowl smoking for a while. */
	private void spawnColumn() {
		double k = Math.sqrt(scale);
		int n;
		if (age <= 12) {
			n = 14;
		} else if (age <= 60) {
			n = 5;
		} else if (age <= 420) {
			n = age % 2 == 0 ? 1 : 0;
		} else {
			n = 0;
		}
		for (int i = 0; i < n; i++) {
			boolean fireball = age <= 12;
			double r = (fireball ? 0.3 : 0.12) * radius * Math.sqrt(random.nextDouble());
			double a = random.nextDouble() * Math.PI * 2;
			double y = center.y + random.nextDouble() * (fireball ? 0.3 : 0.15) * radius;
			float grey = 0.13F + random.nextFloat() * 0.09F;
			int life = age > 60 ? 220 + random.nextInt(80) : 300 + random.nextInt(120);
			Puff p = puff(center.x + Math.cos(a) * r, y, center.z + Math.sin(a) * r, (fireball ? 0.13 : 0.075) * radius
					* (0.8 + random.nextDouble() * 0.4), grey, grey * 0.92F, grey * 0.85F, 0.85F, life);
			double out = fireball ? 0.25 + random.nextDouble() * 0.5 : 0.05;
			p.vx = Math.cos(a) * out * k;
			p.vz = Math.sin(a) * out * k;
			p.vy = (fireball ? 1.4 + random.nextDouble() * 1.6 : 0.9 + random.nextDouble() * 0.6) * k;
			p.buoyancy = (float) (0.085 * k);
			p.drag = 0.95F;
			p.growth = (float) (0.0028 * radius);
			p.glow = age > 60 ? 0.5F : fireball ? 3.6F : 2.2F;
			p.glowDecay = age > 60 ? 0.97F : 0.965F;
			p.cap = capBase + random.nextDouble() * radius * 0.5;
		}
	}

	/** Dust thrown up where the shock front is passing over the ground. */
	private void spawnDust(ClientWorld world) {
		if (age < 2 || age > waveTicks * 1.8) {
			return;
		}
		double front = front(age);
		double speed = frontSpeed(age);
		int n = (int) Math.max(6, 10 * Math.sqrt(scale));
		for (int i = 0; i < n; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double r = front + (random.nextDouble() - 0.5) * 4.0;
			double x = center.x + Math.cos(a) * r;
			double z = center.z + Math.sin(a) * r;
			int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
			double y = Math.max(ground, center.y - radius * 0.2) + random.nextDouble() * 3.0;
			float tone = 0.4F + random.nextFloat() * 0.12F;
			Puff p = puff(x, y, z, (0.06 + random.nextDouble() * 0.06) * radius, tone, tone * 0.86F, tone * 0.72F, 0.38F,
					120 + random.nextInt(120));
			p.vx = Math.cos(a) * speed * 0.6;
			p.vz = Math.sin(a) * speed * 0.6;
			p.vy = 0.05 + random.nextDouble() * 0.12;
			p.drag = 0.92F;
			p.buoyancy = 0.002F;
			p.growth = (float) (0.0019 * radius);
			p.glow = age < 12 ? 1.2F : 0.0F;
			p.glowDecay = 0.9F;
		}
	}

	private Puff puff(double x, double y, double z, double size, float r, float g, float b, float alpha, int life) {
		Puff p = new Puff();
		p.x = p.px = x;
		p.y = p.py = y;
		p.z = p.pz = z;
		p.size = p.prevSize = (float) size;
		p.r = r;
		p.g = g;
		p.b = b;
		p.alpha = alpha;
		p.life = life;
		p.seed = random.nextFloat() * 10.0F;
		p.spin = random.nextFloat() * 2.0F - 1.0F;
		p.drag = 0.95F;
		puffs.add(p);
		return p;
	}

	/** Moves a puff; returns true when it is gone. */
	private boolean stepPuff(Puff p) {
		p.px = p.x;
		p.py = p.y;
		p.pz = p.z;
		p.prevSize = p.size;
		if (p.cap > 0 && p.y - center.y > p.cap) {
			// Stop rising and spread out under the cap.
			double dx = p.x - center.x;
			double dz = p.z - center.z;
			double len = Math.sqrt(dx * dx + dz * dz) + 1.0E-3;
			p.vx += dx / len * 0.06 * Math.sqrt(scale);
			p.vz += dz / len * 0.06 * Math.sqrt(scale);
			p.vy *= 0.85;
			p.buoyancy = 0;
		}
		p.vx *= p.drag;
		p.vy = p.vy * p.drag + p.buoyancy;
		p.vz *= p.drag;
		p.x += p.vx;
		p.y += p.vy;
		p.z += p.vz;
		p.size += p.growth * (1.0F - (float) p.age / p.life);
		p.glow *= p.glowDecay;
		return ++p.age > p.life;
	}

	// --- block textures for the debris ------------------------------------------------------

	private void sampleSprites(MinecraftClient client, ClientWorld world) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int i = 0; i < 28; i++) {
			double r = Math.sqrt(random.nextDouble()) * radius * 0.6;
			double a = random.nextDouble() * Math.PI * 2;
			int x = MathHelper.floor(center.x + Math.cos(a) * r);
			int z = MathHelper.floor(center.z + Math.sin(a) * r);
			int top = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
			pos.set(x, top, z);
			BlockState state = world.getBlockState(pos);
			for (int k = 0; k < 8 && !state.isOpaqueFullCube(world, pos) && pos.getY() > world.getBottomY(); k++) {
				pos.move(0, -1, 0);
				state = world.getBlockState(pos);
			}
			if (state.isAir() || !state.getFluidState().isEmpty()) {
				continue;
			}
			addSprite(client, world, state, pos);
		}
		// Rock from deeper down and the molten crust's own colours.
		addSprite(client, world, Blocks.BLACKSTONE.getDefaultState(), null);
		addSprite(client, world, Blocks.BASALT.getDefaultState(), null);
		addSprite(client, world, Blocks.MAGMA_BLOCK.getDefaultState(), null);
		addSprite(client, world, Blocks.STONE.getDefaultState(), null);
	}

	private void addSprite(MinecraftClient client, ClientWorld world, BlockState state, BlockPos pos) {
		Sprite sprite = client.getBlockRenderManager().getModels().getModelParticleSprite(state);
		int color = pos == null ? -1 : client.getBlockColors().getColor(state, world, pos, 0);
		sprites.add(sprite);
		tints.add(color == -1 ? new float[] {1, 1, 1}
				: new float[] {(color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F});
	}
}
