package io.github.kortev.chainsawman.contract;

import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** What Echo throws into the world for everyone to see (particles; the clients add the copies' own looks, the sound is {@code EchoSounds}'s). */
final class EchoEffects {
	private EchoEffects() {
	}

	/** The burst: a flash on the owner, a streak out to each copy, a ripple ring on the ground where each lands. */
	static void burst(ServerWorld world, ServerPlayerEntity owner, List<EchoEntity> copies) {
		Vec3d heart = owner.getPos().add(0.0, 1.0, 0.0);
		world.spawnParticles(ParticleTypes.FLASH, heart.x, heart.y, heart.z, 1, 0.0, 0.0, 0.0, 0.0);
		spray(world, ParticleTypes.END_ROD, heart, 40, 0.5);
		ring(world, owner.getPos(), 0.35, 28);
		for (EchoEntity e : copies) {
			streak(world, heart, e.getPos().add(0.0, 1.0, 0.0), e.future() ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, 10);
			ring(world, e.getPos(), 0.3, 22);
		}
		EchoSounds.burst(world, owner);
	}

	/** The finale begins: every copy gathers itself (a swirl of light round it), and the owner glows. */
	static void gather(ServerWorld world, ServerPlayerEntity owner, List<EchoEntity> copies) {
		for (EchoEntity e : copies) {
			world.spawnParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 1.0, e.getZ(), 14, 0.3, 0.6, 0.3, 0.05);
		}
		Vec3d heart = owner.getPos().add(0.0, 1.0, 0.0);
		world.spawnParticles(ParticleTypes.REVERSE_PORTAL, heart.x, heart.y, heart.z, 20, 0.4, 0.7, 0.4, 0.08);
		EchoSounds.gather(world, owner);
	}

	/** The copies streak back: light pouring into the owner from each. */
	static void streakIn(ServerWorld world, ServerPlayerEntity owner, List<EchoEntity> copies) {
		Vec3d heart = owner.getPos().add(0.0, 1.0, 0.0);
		for (EchoEntity e : copies) {
			streak(world, e.getPos().add(0.0, 1.0, 0.0), heart, e.future() ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, 12);
		}
		EchoSounds.streakIn(world, owner);
	}

	/** The last blow: a flash, the ring of particles closing on the owner, and what the target took. */
	static void boom(ServerWorld world, ServerPlayerEntity owner, LivingEntity target, Vec3d heart, int count, boolean hit) {
		world.spawnParticles(ParticleTypes.FLASH, heart.x, heart.y, heart.z, 1, 0.0, 0.0, 0.0, 0.0);
		world.spawnParticles(ParticleTypes.EXPLOSION, heart.x, heart.y, heart.z, 3, 0.6, 0.6, 0.6, 0.0);
		spray(world, ParticleTypes.END_ROD, heart, 60, 0.9);
		// A ring closing in: particles on a circle, each given the way to the middle.
		int n = 36;
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2.0 * i / n;
			double r = 5.0;
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, heart.x + Math.cos(a) * r, heart.y - 0.4, heart.z + Math.sin(a) * r, 0, -Math.cos(a), 0.0, -Math.sin(a), 0.5);
		}
		if (hit && target != null) {
			Vec3d at = target.getPos().add(0.0, target.getHeight() * 0.5, 0.0);
			world.spawnParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			world.spawnParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 30, 0.5, 0.5, 0.5, 0.6);
			world.spawnParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 30, 0.5, 0.5, 0.5, 0.4);
		}
		EchoSounds.finale(world, owner);
	}

	/** A copy gone: a little puff. */
	static void puff(ServerWorld world, EchoEntity e) {
		world.spawnParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + 1.0, e.getZ(), 6, 0.25, 0.5, 0.25, 0.02);
	}

	/** A copy's blow landing. */
	static void hit(ServerWorld world, EchoEntity copy, LivingEntity target) {
		Vec3d at = target.getPos().add(0.0, target.getHeight() * 0.6, 0.0);
		world.spawnParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.3);
	}

	private static void streak(ServerWorld world, Vec3d from, Vec3d to, ParticleEffect effect, int steps) {
		for (int i = 0; i <= steps; i++) {
			double k = i / (double) steps;
			Vec3d p = from.lerp(to, k);
			world.spawnParticles(effect, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
		}
	}

	private static void spray(ServerWorld world, ParticleEffect effect, Vec3d at, int count, double speed) {
		world.spawnParticles(effect, at.x, at.y, at.z, count, 0.3, 0.5, 0.3, speed);
	}

	/** A ripple on the ground: a ring of light going out from {@code at}. */
	private static void ring(ServerWorld world, Vec3d at, double speed, int n) {
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2.0 * i / n;
			world.spawnParticles(ParticleTypes.END_ROD, at.x + Math.cos(a) * 0.4, at.y + 0.1, at.z + Math.sin(a) * 0.4, 0, Math.cos(a), 0.0, Math.sin(a), speed);
		}
	}
}
