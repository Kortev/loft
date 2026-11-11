package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.EternityDevilEntity;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Eternity Devil's mouth at the foot of the shaft: the baked shaft-floor mesh ({@code eternity_shaft_*}: the mass with its ragged mouth,
 * fused arms, swaying arms and screaming skulls with working jaws, ropes), at the room's pixel size (1/16 block, scale 1), filling the mouth's
 * hollow wall to wall; its mouth open down the throat into the lit arena. Drawn by {@link ArenaRenderer}, in the room's one light.
 */
final class ShaftMassRenderer {
	private ShaftMassRenderer() {
	}

	/** The mesh's mass top is about 1.0 above its base; this sinks it so the top is level with the floor round the mouth. */
	private static final float SINK = 0.85F;

	/** {@code atMouth}: the frame at the middle of the mouth, level with the floor round it; {@code k}: the room's light now. */
	static void render(EternityDevilEntity devil, float delta, Matrix4f atMouth, float k) {
		if (!(devil.getWorld() instanceof ClientWorld world)) {
			return;
		}
		float now = world.getTime() + delta;
		float pain = devil.pain();
		FleshMesh mesh = FleshMesh.get("shaft");
		Matrix4f model = new Matrix4f(atMouth).translate(0.0F, -SINK, 0.0F);
		FleshMesh.Look look = ArenaRenderer.look(k);
		look.glowAt = new Vector3f(0.0F, -0.8F, 0.0F);
		look.glowRange = 1.8F;
		look.glowR = 0.7F + 0.3F * pain;
		look.glowG = 0.22F;
		look.glowB = 0.06F;
		// It moves faster the more it hurts; while the core is offered its heads and arms draw back.
		boolean offered = devil.pain() > 0.999F && ClientHotel.sinceBeg(delta) >= 0.0F;
		FleshMesh.Hook hook = offered ? (p, m, base) -> !(p.kind.equals("head") || p.kind.equals("jaw") || p.kind.equals("arm") || p.kind.equals("rope")) : null;
		mesh.draw(model, now * (1.0F + 0.8F * pain), look, hook);
	}
}
