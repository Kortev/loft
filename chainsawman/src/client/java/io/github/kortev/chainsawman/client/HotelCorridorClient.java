package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.HotelBlocks;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/**
 * The client side of the hotel's own blocks (package 0.1g): the clock's face is cut out round, and its hands are drawn here, pixel by pixel
 * on the face's own grid, from the time (or stopped at 8:18 with the second hand twitching forward and snapping back).
 */
public final class HotelCorridorClient {
	private HotelCorridorClient() {
	}

	public static void init() {
		BlockRenderLayerMap.INSTANCE.putBlock(HotelBlocks.CLOCK, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(HotelBlocks.FLOOR_SIGN, RenderLayer.getCutout());
		BlockEntityRendererFactories.register(HotelBlocks.CLOCK_ENTITY, ctx -> new ClockRenderer());
	}

	static final int HOUR = PixelArt.rgb(30, 22, 18);
	static final int MINUTE = PixelArt.rgb(44, 32, 26);
	static final int SECOND = PixelArt.rgb(170, 30, 26);
	static final int HUB = PixelArt.rgb(150, 112, 48);

	/** The hands, as pixels on the 16 x 16 face grid (row 0 at the bottom): {@code minutes} past twelve, {@code second} 0 to 59. */
	public static int[][] hands(int minutes, int second) {
		int[][] g = new int[16][16];
		hand(g, (minutes % 720) / 720.0 * 360.0, 2.6, HOUR);
		hand(g, (minutes % 60) / 60.0 * 360.0, 4.2, MINUTE);
		hand(g, second / 60.0 * 360.0, 4.6, SECOND);
		g[7][7] = HUB;
		g[7][8] = HUB;
		g[8][7] = HUB;
		g[8][8] = HUB;
		return g;
	}

	private static void hand(int[][] g, double degrees, double length, int colour) {
		double a = Math.toRadians(degrees);
		for (double t = 0.6; t <= length; t += 0.25) {
			int x = (int) Math.floor(8.0 + Math.sin(a) * t);
			int y = (int) Math.floor(8.0 + Math.cos(a) * t);
			if (x >= 2 && x < 14 && y >= 2 && y < 14) {
				g[y][x] = colour;
			}
		}
	}

	static final class ClockRenderer implements BlockEntityRenderer<HotelBlocks.ClockEntity> {
		@Override
		public void render(HotelBlocks.ClockEntity clock, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light, int overlay) {
			if (clock.getWorld() == null) {
				return;
			}
			BlockState state = clock.getCachedState();
			if (!(state.getBlock() instanceof HotelBlocks.Clock)) {
				return;
			}
			int minutes = HotelBlocks.reading(clock.getWorld(), state);
			long t = clock.getWorld().getTime();
			int second;
			if (state.get(HotelBlocks.Clock.STOPPED)) {
				// 8:18, and the second hand ticks one on and snaps back, over and over.
				second = 41 + ((t / 10) % 3 == 1 ? 1 : 0);
			} else {
				second = (int) ((t / 20) % 60);
			}
			Direction facing = state.get(HotelBlocks.Clock.FACING);
			matrices.push();
			// The block's frame turned so its face looks north (as the model is drawn), then that face as the +z plane seen from the front.
			matrices.translate(0.5, 0.5, 0.5);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - facing.asRotation()));
			matrices.translate(-0.5, -0.5, -0.5);
			// Facing north: the face is the plane z = 14/16 seen from -z. Flip so +z points out of the face.
			matrices.translate(1.0, 0.0, 14.0 / 16.0);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
			VertexConsumer vc = PixelArt.buffer(provider);
			PixelArt.grid(vc, matrices.peek(), hands(minutes, second), 0, 0, 0.003F, light);
			matrices.pop();
		}
	}
}
