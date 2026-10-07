package io.github.kortev.shootingstar.client.chitty;

import io.github.kortev.shootingstar.chitty.ChittyEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/**
 * Chitty's polished metal, shone as you look at it rather than painted: each corner of the bonnet, the radiator and the
 * brass reflects what lies the other side of its normal from your eye, the sky overhead, the paler sky at the horizon
 * and the ground under her (its colour, from the blocks she is on), with the sun's glint where it lines up, the moon's
 * at night and a warm glow from torches about. Moving round her, the reflections move over her.
 *
 * <p>Her texture holds only how shut in the metal is (its slots and corners), and the colour worked out here is drawn
 * on it; the light of the world multiplies it as it does everything else.
 */
final class ChittyShine {
	/**
	 * Per metal (tools/chitty_model.py SHINE): how much it reflects of red, green and blue face on, how rough it is, and
	 * how much of the colour of what it reflects it loses (a coloured metal keeps its own: brass reflecting the blue sky
	 * is still golden, not green).
	 */
	private static final float[][] METALS = {
			null,
			{0.93F, 0.94F, 0.96F, 0.06F, 0.3F}, // aluminium, polished
			{1.00F, 0.80F, 0.45F, 0.16F, 0.75F}, // brass
			{0.96F, 0.96F, 0.98F, 0.03F, 0.15F}, // chrome
			{0.98F, 0.62F, 0.46F, 0.18F, 0.65F}, // copper
			{0.78F, 0.79F, 0.81F, 0.45F, 0.5F}, // aluminium, dull
	};

	private float skyR, skyG, skyB;
	private float horizonR, horizonG, horizonB;
	private float groundR, groundG, groundB;
	private float sunX, sunY;
	private float sun, moon;
	private float glow;

	/** Takes in the sky, the ground under her and the light about her, once a frame. */
	void setUp(ChittyEntity car, float tickDelta, int light) {
		ClientWorld world = (ClientWorld) car.getWorld();
		MinecraftClient client = MinecraftClient.getInstance();
		Vec3d eye = client.gameRenderer.getCamera().getPos();
		float open = LightmapTextureManager.getSkyLightCoordinates(light) / 15.0F;
		float torches = LightmapTextureManager.getBlockLightCoordinates(light) / 15.0F;
		Vec3d sky = world.getSkyColor(eye, tickDelta);
		float angle = world.getSkyAngleRadians(tickDelta);
		float day = MathHelper.clamp(MathHelper.cos(angle) * 2.0F + 0.5F, 0.0F, 1.0F);
		float clear = 1.0F - 0.75F * world.getRainGradient(tickDelta);
		skyR = (float) sky.x * open;
		skyG = (float) sky.y * open;
		skyB = (float) sky.z * open;
		// The horizon: paler and brighter than overhead by day, and a little warm.
		float haze = (0.55F * day * clear + 0.05F) * open;
		horizonR = skyR * 0.45F + haze * 1.02F;
		horizonG = skyG * 0.45F + haze;
		horizonB = skyB * 0.45F + haze * 0.98F;
		int ground = groundColor(world, car);
		float lit = (0.6F * day * clear + 0.08F) * open + 0.25F * torches;
		groundR = (ground >> 16 & 255) / 255.0F * lit;
		groundG = (ground >> 8 & 255) / 255.0F * lit;
		groundB = (ground & 255) / 255.0F * lit;
		// The sun rises in the east (+x) and turns overhead about the z axis; the moon is opposite.
		sunX = -MathHelper.sin(angle);
		sunY = MathHelper.cos(angle);
		sun = MathHelper.clamp(sunY * 4.0F + 0.3F, 0.0F, 1.0F) * clear * open;
		moon = MathHelper.clamp(-sunY * 4.0F + 0.3F, 0.0F, 1.0F) * clear * open * 0.25F;
		glow = torches * 0.4F;
	}

	/** The colour of what she stands on or flies over: the first block below her, or the top of the world there. */
	private static int groundColor(ClientWorld world, ChittyEntity car) {
		BlockPos at = car.getBlockPos();
		for (int dy = 0; dy <= 6; dy++) {
			BlockPos pos = at.down(dy);
			BlockState state = world.getBlockState(pos);
			if (!state.isAir()) {
				MapColor color = state.getMapColor(world, pos);
				if (color != MapColor.CLEAR) {
					return color.color;
				}
			}
		}
		BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, at).down();
		MapColor color = world.getBlockState(top).getMapColor(world, top);
		return color == MapColor.CLEAR ? 0x6A7A4A : color.color;
	}

	/**
	 * The colour of a corner of metal, as ARGB: (px, py, pz) is where it is from the eye and (nx, ny, nz) its normal,
	 * both in the world's axes.
	 */
	int shade(int metal, float px, float py, float pz, float nx, float ny, float nz) {
		float[] m = METALS[Math.min(metal, METALS.length - 1)];
		float rough = m[3];
		float il = MathHelper.sqrt(px * px + py * py + pz * pz);
		if (il < 1.0E-4F) {
			il = 1.0F;
		}
		float ix = px / il;
		float iy = py / il;
		float iz = pz / il;
		float nl = MathHelper.sqrt(nx * nx + ny * ny + nz * nz);
		nx /= nl;
		ny /= nl;
		nz /= nl;
		float dot = ix * nx + iy * ny + iz * nz;
		if (dot > 0.0F) {
			// Seen from behind (the inside of a part, or a normal smoothed round an edge): as if face on.
			nx = -nx;
			ny = -ny;
			nz = -nz;
			dot = -dot;
		}
		float rx = ix - 2.0F * dot * nx;
		float ry = iy - 2.0F * dot * ny;
		float rz = iz - 2.0F * dot * nz;

		// What the reflected ray sees: sky above, blending into the horizon, and the ground below, darker straight
		// under her where her own shadow falls.
		float er;
		float eg;
		float eb;
		if (ry >= 0.0F) {
			float t = smooth(MathHelper.clamp(ry / 0.55F, 0.0F, 1.0F));
			er = MathHelper.lerp(t, horizonR, skyR);
			eg = MathHelper.lerp(t, horizonG, skyG);
			eb = MathHelper.lerp(t, horizonB, skyB);
		} else {
			float t = smooth(MathHelper.clamp(-ry / 0.18F, 0.0F, 1.0F));
			float shadow = 1.0F - 0.55F * smooth(MathHelper.clamp((-ry - 0.6F) / 0.4F, 0.0F, 1.0F));
			er = MathHelper.lerp(t, horizonR * 0.7F, groundR * shadow);
			eg = MathHelper.lerp(t, horizonG * 0.7F, groundG * shadow);
			eb = MathHelper.lerp(t, horizonB * 0.7F, groundB * shadow);
		}
		// Rougher metal blurs it all towards the average.
		float blur = rough * 1.4F;
		er = MathHelper.lerp(blur, er, (skyR + horizonR + groundR) / 3.0F);
		eg = MathHelper.lerp(blur, eg, (skyG + horizonG + groundG) / 3.0F);
		eb = MathHelper.lerp(blur, eb, (skyB + horizonB + groundB) / 3.0F);
		float grey = 0.3F * er + 0.55F * eg + 0.15F * eb;
		er = MathHelper.lerp(m[4], er, grey);
		eg = MathHelper.lerp(m[4], eg, grey);
		eb = MathHelper.lerp(m[4], eb, grey);
		er += glow;
		eg += glow * 0.8F;
		eb += glow * 0.55F;

		// Brighter at a glancing angle (Schlick's Fresnel).
		float grazing = 1.0F + dot;
		float g5 = grazing * grazing * grazing * grazing * grazing;
		float fr = m[0] + (1.0F - m[0]) * g5;
		float fg = m[1] + (1.0F - m[1]) * g5;
		float fb = m[2] + (1.0F - m[2]) * g5;

		// The sun's glint: a hot spot with a soft glow round it, broader on rougher metal; the moon's, fainter.
		float sharp = 48.0F / (1.0F + rough * 12.0F);
		float toSun = Math.max(0.0F, rx * sunX + ry * sunY);
		float glint = sun * (0.85F * (float) Math.pow(toSun, sharp) + 0.22F * (float) Math.pow(toSun, 6.0F));
		float toMoon = Math.max(0.0F, -(rx * sunX + ry * sunY));
		glint += moon * (float) Math.pow(toMoon, sharp);

		float r = fr * er + glint * fr;
		float gg = fg * eg + glint * fg;
		float b = fb * eb + glint * (0.6F + 0.4F * fb);
		return 0xFF000000 | channel(r) << 16 | channel(gg) << 8 | channel(b);
	}

	private static int channel(float v) {
		return (int) (MathHelper.clamp(v, 0.0F, 1.0F) * 255.0F + 0.5F);
	}

	private static float smooth(float x) {
		return x * x * (3.0F - 2.0F * x);
	}
}
