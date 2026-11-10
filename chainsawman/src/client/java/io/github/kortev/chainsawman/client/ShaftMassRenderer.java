package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.EternityDevilEntity;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Eternity Devil's mouth at the foot of the shaft: the baked shaft-floor mesh ({@code eternity_shaft_*}: the mass with its ragged mouth,
 * fused arms, swaying arms and screaming skulls with working jaws, ropes) drawn over the floor of blocks round the mouth, its mouth over the
 * hole everyone falls through. Drawn by {@link ArenaRenderer}. The mouth glows from deep inside (the arena's light below), more as it hurts.
 */
final class ShaftMassRenderer {
	private ShaftMassRenderer() {
	}

	/** The mesh's mass top is about 1.0 above its base; this sinks it so the top is level with the floor round the mouth. */
	private static final float SINK = 0.85F;
	/** The mass is drawn this much wider across so it runs wall to wall round the mouth and its arms and heads overlap the walls. */
	private static final float WIDEN = 1.25F;

	/** {@code atMouth}: the frame at the middle of the mouth, level with the floor round it. */
	static void render(EternityDevilEntity devil, float delta, Matrix4f atMouth, int light) {
		if (!(devil.getWorld() instanceof ClientWorld world)) {
			return;
		}
		float now = world.getTime() + delta;
		float pain = devil.pain();
		FleshMesh mesh = FleshMesh.get("shaft");
		Matrix4f model = new Matrix4f(atMouth).translate(0.0F, -SINK, 0.0F).scale(WIDEN, 1.0F, WIDEN);
		FleshMesh.Look look = new FleshMesh.Look().light(light, 0.75F, 1.2F);
		look.rim = 0.5F;
		look.glowAt = new Vector3f(0.0F, -0.8F, 0.0F);
		look.glowRange = 1.8F;
		look.glowR = 0.9F + 0.3F * pain;
		look.glowG = 0.32F;
		look.glowB = 0.1F;
		// It moves faster the more it hurts; while the core is offered its heads and arms draw back.
		boolean offered = devil.pain() > 0.999F && ClientHotel.sinceBeg(delta) >= 0.0F;
		FleshMesh.Hook hook = offered ? (p, m, base) -> !(p.kind.equals("head") || p.kind.equals("jaw") || p.kind.equals("arm") || p.kind.equals("rope")) : null;
		mesh.draw(model, now * (1.0F + 0.8F * pain), look, hook);
	}
}
