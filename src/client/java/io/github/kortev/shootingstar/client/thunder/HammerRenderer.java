package io.github.kortev.shootingstar.client.thunder;

import io.github.kortev.shootingstar.ShootingStar;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Draws Mjölnir wherever the game draws the item (in the hand in first and third person, in slots, on the ground, in
 * item frames, on a head, and held high by HammerRaise): the baked mesh ({@link HammerMesh}) with its baked texture,
 * lit by the world's light and shaded by its normals as any entity is, then its interlace and runes again in their
 * glow texture, added at full brightness, so they burn blue even in the dark.
 *
 * <p>The item model (models/item/mjolnir.json) has the builtin/entity parent: the game turns and scales the item for
 * the view by the model's display transforms, moves it half a block so the item's centre is at the origin, and hands
 * it here to draw a block from 0 to 1, in which the mesh lies on the diagonal as the old cuboid hammer did.
 */
public final class HammerRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	/** The hammer's colour, with its hammering, chamfers and shadows baked in. */
	public static final Identifier TEXTURE = ShootingStar.id("textures/item/mjolnir_baked.png");
	/** Black but for the light in the interlace's channels and the runes. */
	public static final Identifier GLOW = ShootingStar.id("textures/item/mjolnir_glow.png");
	private static final int WHITE = 0xFFFFFFFF;

	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		HammerMesh mesh = HammerMesh.get();
		if (mesh == null) {
			return;
		}
		MatrixStack.Entry entry = matrices.peek();
		VertexConsumer body = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		mesh.draw(entry, body, mesh.quads, WHITE, light, overlay);
		// The glow adds light, so it can only be dimmed by colour; past full it is drawn again over itself.
		float glow = HammerGlow.level();
		if (glow <= 0.0F || mesh.glowing == 0) {
			return;
		}
		int passes = (int) Math.ceil(glow);
		int shade = Math.round(255.0F * glow / passes);
		int color = 0xFF000000 | shade << 16 | shade << 8 | shade;
		for (int i = 0; i < passes; i++) {
			mesh.draw(entry, vertexConsumers.getBuffer(RenderLayer.getEyes(GLOW)), mesh.glowing, color,
					LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
		}
	}
}
