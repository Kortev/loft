package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.FleshGrowthEntity;
import io.github.kortev.chainsawman.hotel.Hotel;
import io.github.kortev.chainsawman.hotel.HotelBuilder;
import io.github.kortev.chainsawman.hotel.HotelPlan;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The loop floor of the Eternity Devil's hotel, as this client sees it while it is looped (from the loop's fog payload, which says where the
 * hotel stands). Its corridor is built to repeat every {@link HotelPlan#PERIOD} blocks and a player who walks past the window is put back a
 * period with everything kept (the server's relative move): the blocks match, but the things in it (a survivor sitting, an eye, a dropped
 * item, a mob, a friend) are each in one place only, and would pop in and out of the fog as you are put back. So each is also drawn where
 * the corridor repeats it, a period and two either way (inside the corridor); and when the server moves one of them a period (it carries what
 * follows you, and keeps items in the window), it is moved here first by the same, so it does not slide across.
 */
public final class ClientLoop {
	private static boolean on;
	private static BlockPos origin = BlockPos.ORIGIN;
	private static int rot;

	private ClientLoop() {
	}

	/** The loop's fog for this player: on (with where the hotel stands) or off. */
	public static void set(float end, BlockPos at, int turn) {
		on = end > 0.0F;
		if (on) {
			origin = at;
			rot = turn & 3;
		}
	}

	public static boolean on() {
		return on && MinecraftClient.getInstance().world != null;
	}

	/** The corridor's things drawn again where the corridor repeats them. */
	public static void render(WorldRenderContext context) {
		VertexConsumerProvider consumers = context.consumers();
		if (!on() || context.world() == null || consumers == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
		Camera camera = context.camera();
		Vec3d cam = camera.getPos();
		float delta = context.tickCounter().getTickDelta(false);
		MatrixStack matrices = context.matrixStack() != null ? context.matrixStack() : new MatrixStack();
		double far = Hotel.FOG_END + 4.0;
		for (Entity e : context.world().getEntities()) {
			if (e == camera.getFocusedEntity() && !camera.isThirdPerson()) {
				continue;
			}
			if (e instanceof FleshGrowthEntity g && g.kind() != FleshGrowthEntity.EYE && g.kind() != FleshGrowthEntity.BREATH) {
				// (The flesh at the corners is put at every period already.)
				continue;
			}
			Vec3d at = e.getLerpedPos(delta);
			Vec3d lp = HotelBuilder.local(origin, rot, at);
			if (!Hotel.inLoopCorridor(lp)) {
				continue;
			}
			for (int k = -2; k <= 2; k++) {
				double x = lp.x + k * HotelPlan.PERIOD;
				if (k == 0 || x < HotelPlan.CORR_X0 || x >= HotelPlan.CORR_X1 + 1.0) {
					continue;
				}
				Vec3d g = HotelBuilder.world(origin, rot, x, lp.y, lp.z);
				if (g.squaredDistanceTo(cam) > far * far) {
					continue;
				}
				try {
					dispatcher.render(e, g.x - cam.x, g.y - cam.y, g.z - cam.z, e.getYaw(delta), delta, matrices, consumers, dispatcher.getLight(e, delta));
				} catch (RuntimeException ignored) {
					// (Something that will not draw away from itself is just not repeated.)
				}
			}
		}
	}

	/**
	 * The server is moving {@code e} to {@code (x, y, z)}: if that is a period or two along the corridor (the loop carrying it), it is put there
	 * here and now, its last frame's place with it, so what is drawn does not slide 12 blocks across the corridor in between.
	 */
	public static void beforeMove(Entity e, double x, double y, double z) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!on() || e == client.player) {
			return;
		}
		Vec3d from = HotelBuilder.local(origin, rot, e.getPos());
		Vec3d to = HotelBuilder.local(origin, rot, new Vec3d(x, y, z));
		if (!Hotel.inLoopCorridor(from) || !Hotel.inLoopCorridor(to)) {
			return;
		}
		double dx = to.x - from.x;
		long k = Math.round(dx / HotelPlan.PERIOD);
		if (k == 0 || Math.abs(dx - k * HotelPlan.PERIOD) > 2.5) {
			return;
		}
		Vec3d d = HotelBuilder.world(BlockPos.ORIGIN, rot, (double) (k * HotelPlan.PERIOD), 0.0, 0.0);
		e.setPosition(e.getX() + d.x, e.getY(), e.getZ() + d.z);
		e.prevX += d.x;
		e.prevZ += d.z;
		e.lastRenderX += d.x;
		e.lastRenderZ += d.z;
	}
}
