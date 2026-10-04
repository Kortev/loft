package io.github.kortev.shootingstar.client.camera;

import io.github.kortev.shootingstar.client.ClientStrike;
import io.github.kortev.shootingstar.client.ClientStrikes;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Air between the camera and the ground during the rise. A hundred and fifty blocks up, the loaded world would
 * end in a hard edge against the sky; instead the far ground fades into a pale haze well before that edge, as it
 * does seen from a plane, and at the top the camera climbs into a cloud deck where everything turns to white
 * mist just as the feed cuts in.
 */
public final class AerialHaze {
	/** Height of the cloud deck's base above the target, and how far the mist takes to close in. */
	private static final double DECK = 100.0;
	private static final double DECK_DEPTH = 40.0;

	public record State(float start, float end, float mist, float r, float g, float b) {
	}

	private AerialHaze() {
	}

	@Nullable
	public static State get(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		ClientStrike strike = ClientStrikes.cinematic();
		if (player == null || client.world == null || strike == null) {
			return null;
		}
		double t = strike.time(tickDelta);
		if (t >= StrikeTimeline.ORBIT || !ClientStrikes.shotActive(strike, t)) {
			return null;
		}
		Vec3d cam = client.gameRenderer.getCamera().getPos();
		Vec3d eye = player.getCameraPosVec(tickDelta);
		float view = client.gameRenderer.getViewDistance();
		double offset = Math.sqrt((cam.x - eye.x) * (cam.x - eye.x) + (cam.z - eye.z) * (cam.z - eye.z));
		double altitude = cam.y - strike.center.y;
		// The farthest ground still loaded, seen from the camera, less a margin so the last chunks are already gone.
		float end = (float) MathHelper.clamp(view - offset - 28.0, 40.0, view);
		float aerial = (float) MathHelper.clamp((altitude - 10.0) / 80.0, 0.0, 1.0);
		float start = MathHelper.lerp(aerial, end - MathHelper.clamp(end / 10.0F, 4.0F, 64.0F), end * 0.08F);
		float mist = (float) MathHelper.clamp((altitude - DECK) / DECK_DEPTH, 0.0, 1.0);
		mist = mist * mist * (3.0F - 2.0F * mist);
		end = MathHelper.lerp(mist, end, 3.0F);
		start = MathHelper.lerp(mist, start, -2.0F);
		float angle = client.world.getSkyAngle(tickDelta) * MathHelper.TAU;
		float day = MathHelper.clamp(MathHelper.cos(angle) * 2.0F + 0.5F, 0.12F, 1.0F);
		return new State(start, end, mist, 0.88F * day, 0.9F * day, 0.94F * day);
	}
}
