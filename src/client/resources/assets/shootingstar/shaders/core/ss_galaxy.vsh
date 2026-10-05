#version 150

// The galaxies of a universe (Mesh.universe), a quad each. A galaxy too small to make out is a point of light,
// dimming as it shrinks below a pixel so that a hundred thousand of them add up to the web they lie on rather
// than to a white-out; once it is a few pixels across a spiral is drawn as a disc turned the way it faces and an
// elliptical as a round glow. So one universe holds together from a single galaxy filling the picture to the
// whole web of them a speck across. With Glow 1 the quads are the web's own faint light, which only shows from
// far off, where it stands for the galaxies too faint to see one by one.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 ScreenSize;
uniform float Brightness;
uniform vec3 Tint;
uniform float Glow;

out vec3 light;
out vec2 corner;
flat out int kind;
out float shape;
out float phase;

void main() {
    float radius = float(UV2.x) * 1.0e-5;
    vec4 centre = ModelViewMat * vec4(Position, 1.0);
    float depth = max(-centre.z, 1.0e-6);
    // Pixels per unit of view space at the galaxy's distance, and its radius on screen in pixels.
    float perUnit = ProjMat[1][1] * ScreenSize.y * 0.5 / depth;
    float rp = radius * length(ModelViewMat[0].xyz) * perUnit;
    light = Color.rgb * Tint * Color.a * Brightness;
    shape = float(UV2.y & 255) / 255.0;
    phase = fract(sin(dot(Position, vec3(12.9898, 78.233, 37.719))) * 43758.5453) * 6.2831853;
    corner = UV0;
    // Anything grown bigger than the picture is gone, so a galaxy the camera passes close by does not flood it.
    light *= 1.0 - smoothstep(1.2, 3.0, rp / ScreenSize.y);
    vec4 pos;
    if (Glow > 0.5) {
        kind = 3;
        float q = max(rp, 2.0);
        light *= (1.0 - smoothstep(40.0, 200.0, rp)) * min(1.0, rp / q);
        pos = centre + vec4(UV0 * q / perUnit, 0.0, 0.0);
    } else if (rp < 3.0) {
        kind = 0;
        // Never less than a pixel across; the same light spread over it, so it dims as it shrinks.
        float q = max(rp, 1.0);
        light *= rp * rp / (q * q);
        pos = centre + vec4(UV0 * q * 1.8 / perUnit, 0.0, 0.0);
    } else if ((UV2.y >> 8) == 1) {
        kind = 2;
        pos = centre + vec4(UV0 * rp * 1.6 / perUnit, 0.0, 0.0);
    } else {
        kind = 1;
        vec3 pole = normalize(Normal);
        vec3 e1 = normalize(cross(pole, abs(pole.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
        vec3 e2 = cross(pole, e1);
        corner = UV0 * 1.25;
        pos = ModelViewMat * vec4(Position + (e1 * UV0.x + e2 * UV0.y) * radius * 1.25, 1.0);
    }
    gl_Position = ProjMat * pos;
}
