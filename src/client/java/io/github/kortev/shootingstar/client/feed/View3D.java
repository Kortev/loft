package io.github.kortev.shootingstar.client.feed;

/**
 * A tiny software camera for the uplink feed. Scenes are modelled in their own units (planet
 * radii, mostly) and projected to GUI coordinates here, so the feed never touches the game's
 * world matrices.
 */
final class View3D {
	static final double NEAR = 1.0E-4;

	double ex, ey, ez;
	double rx, ry, rz;
	double ux, uy, uz;
	double fx, fy, fz;
	double focal;
	double cx, cy;
	double width, height;

	void viewport(double width, double height, double fovDegrees) {
		this.width = width;
		this.height = height;
		this.cx = width * 0.5;
		this.cy = height * 0.5;
		this.focal = height * 0.5 / Math.tan(Math.toRadians(fovDegrees) * 0.5);
	}

	void lookAt(double ex, double ey, double ez, double tx, double ty, double tz, double upX, double upY, double upZ) {
		this.ex = ex;
		this.ey = ey;
		this.ez = ez;
		double fx = tx - ex;
		double fy = ty - ey;
		double fz = tz - ez;
		double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
		this.fx = fx / fl;
		this.fy = fy / fl;
		this.fz = fz / fl;
		// right = forward x up
		double rx = this.fy * upZ - this.fz * upY;
		double ry = this.fz * upX - this.fx * upZ;
		double rz = this.fx * upY - this.fy * upX;
		double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
		this.rx = rx / rl;
		this.ry = ry / rl;
		this.rz = rz / rl;
		// up = right x forward
		this.ux = this.ry * this.fz - this.rz * this.fy;
		this.uy = this.rz * this.fx - this.rx * this.fz;
		this.uz = this.rx * this.fy - this.ry * this.fx;
	}

	/** Rolls the camera around its forward axis. */
	void roll(double radians) {
		double c = Math.cos(radians);
		double s = Math.sin(radians);
		double nrx = rx * c + ux * s;
		double nry = ry * c + uy * s;
		double nrz = rz * c + uz * s;
		double nux = ux * c - rx * s;
		double nuy = uy * c - ry * s;
		double nuz = uz * c - rz * s;
		rx = nrx;
		ry = nry;
		rz = nrz;
		ux = nux;
		uy = nuy;
		uz = nuz;
	}

	/** Projects a point; out = {screenX, screenY, depth}. False when behind the camera. */
	boolean project(double x, double y, double z, double[] out) {
		double dx = x - ex;
		double dy = y - ey;
		double dz = z - ez;
		double depth = dx * fx + dy * fy + dz * fz;
		if (depth < NEAR) {
			return false;
		}
		out[0] = cx + (dx * rx + dy * ry + dz * rz) / depth * focal;
		out[1] = cy - (dx * ux + dy * uy + dz * uz) / depth * focal;
		out[2] = depth;
		return true;
	}

	/** Projects a direction at infinity (stars). */
	boolean projectDir(double dx, double dy, double dz, double[] out) {
		double depth = dx * fx + dy * fy + dz * fz;
		if (depth < 1.0E-3) {
			return false;
		}
		out[0] = cx + (dx * rx + dy * ry + dz * rz) / depth * focal;
		out[1] = cy - (dx * ux + dy * uy + dz * uz) / depth * focal;
		out[2] = depth;
		return true;
	}

	double depth(double x, double y, double z) {
		return (x - ex) * fx + (y - ey) * fy + (z - ez) * fz;
	}

	/** Apparent radius in pixels of a sphere of radius r whose centre is at distance d. */
	double sphereRadius(double r, double d) {
		return focal * r / Math.sqrt(Math.max(d * d - r * r, 1.0E-9));
	}

	boolean onScreen(double sx, double sy, double margin) {
		return sx > -margin && sy > -margin && sx < width + margin && sy < height + margin;
	}
}
