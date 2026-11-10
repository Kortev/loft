package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.EternityDevilEntity;
import io.github.kortev.chainsawman.hotel.Shaft;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Eternity Devil's mass at the bottom of the shaft: the baked shaft-floor mesh ({@code eternity_shaft_*}: the mass with its ragged
 * mouth, fused arms, swaying arms and screaming skulls with working jaws, ropes) drawn over the floor of blocks, its mouth over the pit.
 * Drawn by {@link EternityDevilRenderer} for the devil in the shaft, in its own frame. The pit glows faintly red (the shader's glow, from
 * deep in the mouth) and more so as it is hurt.
 */
final class ShaftMassRenderer {
	private ShaftMassRenderer() {
	}

	/** The pit's middle in the devil's frame: across 0, up from the entity's feet (the pit's bottom), and out from its back plane. */
	private static final double PIT_UP = Shaft.PIT_DEPTH;
	private static final double PIT_OUT = Shaft.MASS_X - (Shaft.PIT_X + 0.5);
	/** The mesh's mass top is about 1.0 above its base; this sinks it so the top is level with the walking floor. */
	private static final float SINK = 0.85F;

	static void render(EternityDevilEntity devil, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(devil.getWorld() instanceof ClientWorld world)) {
			return;
		}
		float now = world.getTime() + delta;
		float pain = devil.pain();
		FleshMesh mesh = FleshMesh.get("shaft");
		Matrix4f model = new Matrix4f(matrices.peek().getPositionMatrix()).translate(0.0F, (float) PIT_UP - SINK, (float) PIT_OUT);
		FleshMesh.Look look = new FleshMesh.Look().light(light, 0.7F, 1.15F);
		look.rim = 0.5F;
		look.glowAt = new Vector3f(0.0F, -0.8F, 0.0F);
		look.glowRange = 1.3F;
		look.glowR = 0.25F + 0.2F * pain;
		look.glowG = 0.07F;
		look.glowB = 0.03F;
		// It moves faster the more it hurts.
		mesh.draw(model, now * (1.0F + 0.8F * pain), look, null);
	}
}
