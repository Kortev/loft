package io.github.kortev.shootingstar.client.gfx;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** A perspective camera for the feed's own 3D scenes. */
public final class Cam {
	public final Vector3f pos = new Vector3f();
	public final Matrix4f view = new Matrix4f();
	public final Matrix4f viewRot = new Matrix4f();
	public final Matrix4f proj = new Matrix4f();
	public float width;
	public float height;

	public Cam look(Vector3f eye, Vector3f at, Vector3f up) {
		pos.set(eye);
		view.setLookAt(eye, at, up);
		viewRot.set(view).setTranslation(0, 0, 0);
		return this;
	}

	/** Camera at {@code eye} looking along {@code forward}, rolled by {@code roll} radians. */
	public Cam lookDir(Vector3f eye, Vector3f forward, Vector3f up, float roll) {
		Vector3f at = new Vector3f(eye).add(forward);
		look(eye, at, up);
		if (roll != 0.0F) {
			Matrix4f r = new Matrix4f().rotateZ(roll);
			view.set(new Matrix4f(r).mul(view));
			viewRot.set(view).setTranslation(0, 0, 0);
		}
		return this;
	}

	public Cam perspective(float fovDegrees, float width, float height, float near, float far) {
		this.width = width;
		this.height = height;
		proj.setPerspective((float) Math.toRadians(fovDegrees), width / height, near, far);
		return this;
	}

	public Matrix4f modelView(Matrix4f model) {
		return new Matrix4f(view).mul(model);
	}

	/** World direction turned into view space, for lighting uniforms. */
	public Vector3f viewDir(Vector3f world) {
		return viewRot.transformDirection(new Vector3f(world)).normalize();
	}

	/** Screen position in GUI pixels (origin top-left) and view depth, or null when behind the camera. */
	@Nullable
	public Vector3f screen(Vector3f world, float guiWidth, float guiHeight) {
		Vector4f clip = new Vector4f(world, 1.0F);
		view.transform(clip);
		float depth = -clip.z;
		proj.transform(clip);
		if (clip.w <= 1.0E-4F) {
			return null;
		}
		float x = (clip.x / clip.w * 0.5F + 0.5F) * guiWidth;
		float y = (0.5F - clip.y / clip.w * 0.5F) * guiHeight;
		return new Vector3f(x, y, depth);
	}

	public Vector3f forward() {
		return new Vector3f(-view.m02(), -view.m12(), -view.m22()).normalize();
	}

	public Vector3f right() {
		return new Vector3f(view.m00(), view.m10(), view.m20()).normalize();
	}

	public Vector3f up() {
		return new Vector3f(view.m01(), view.m11(), view.m21()).normalize();
	}
}
