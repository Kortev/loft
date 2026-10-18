package io.github.kortev.shootingstar.client.feed;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;

/** The text and HUD marks a shot puts over its picture, drawn by {@link Feed} after the 3D pass. */
final class Overlay {
	static final class Label {
		final float x;
		final float y;
		final String line1;
		final int color1;
		final String line2;
		final int color2;
		float alpha = 1.0F;
		/** Draw corner brackets around the anchor point. */
		boolean marker;
		float markerX;
		float markerY;

		Label(float x, float y, String line1, int color1, String line2, int color2) {
			this.x = x;
			this.y = y;
			this.line1 = line1;
			this.color1 = color1;
			this.line2 = line2;
			this.color2 = color2;
		}
	}

	interface Bars {
		void draw(BufferBuilder b, Matrix4f m, float w, float h);
	}

	String header;
	int headerColor = Feed.RED;
	/** The colour of the title and the banner. */
	int accent = Feed.RED;
	/** How much of the header has typed in, 0..1. */
	float headerReveal = 1.0F;
	String title;
	String subtitle;
	float titleAlpha;
	/** A big centred word on its own (RELEASE). */
	String banner;
	float bannerAlpha;
	String footer;
	String footerSmall;
	Bars bars;
	final List<Label> labels = new ArrayList<>();

	// Post-processing requested by the shot. The scene is HDR: light past 1.0 rolls off and blooms.
	/** Cross-fade of the finished picture to {@link #flashColor} (whiteouts and cuts). */
	float flash;
	int flashColor = 0xFFFFFF;
	/** Scales the light before tone mapping; above 1 reads as a burst of light. */
	float exposure = 1.0F;
	float zoomBlur;
	/**
	 * Motion blur: how much of the time between frames the shutter stays open (0 = off), for motion
	 * too fast for the frame rate to follow.
	 */
	float shutter;
	float aberration;
	float bloom = 0.85F;
	float wideBloom = 0.65F;
	/** Anamorphic streaks off the brightest points. */
	float streak = 0.45F;
	/** Light above this level blooms. */
	float threshold = 0.9F;
	float vignette = 0.55F;
	/** How strongly the video lines show over the picture (1 for the full look of a feed). */
	float scanlines = 1.0F;
	float saturation = 1.0F;
	float fade = 1.0F;
}
