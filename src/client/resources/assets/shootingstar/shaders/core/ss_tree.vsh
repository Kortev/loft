#version 150

// Yggdrasil (TreeRender): tens of thousands of points of light, a quad each, drawn the way the galaxies are. Each
// knows when it grows (UV2.y, of 30000) and how far it is along the tree from the foot of its axis (Normal.x), so the
// tree grows from the roots up as Grow goes from 0 to 1, light runs up its filaments all the time, and a wave of it
// (Wave, in the same units as along) can be sent out from the foot through the whole of it.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 ScreenSize;
uniform float Grow;
uniform float Time;
uniform float Wave;
uniform float Bright;
// How far the long root (kind 4, laid out one unit long) runs, in the tree's units: out to the shooter.
uniform float Reach;

out vec3 light;
out vec2 corner;
flat out int kind;

void main() {
    float size = float(UV2.x) * 1.0e-4;
    float grow = float(UV2.y) / 30000.0;
    float along = (Normal.x * 0.5 + 0.5) * 2.5;
    kind = int(Normal.y * 4.0 + 0.5);
    float seed = (Normal.z * 0.5 + 0.5) * 255.0;
    vec3 at = Position;
    if (kind == 4) {
        at.z *= Reach;
        along *= Reach;
    }
    vec4 centre = ModelViewMat * vec4(at, 1.0);
    float depth = max(-centre.z, 1.0e-6);
    float perUnit = ProjMat[1][1] * ScreenSize.y * 0.5 / depth;
    float rp = size * length(ModelViewMat[0].xyz) * perUnit;

    float appear = smoothstep(grow - 0.015, grow, Grow);
    // Each point flares as it comes, so the growing tips of the tree burn.
    float tip = exp(-max(Grow - grow, 0.0) * 60.0) * appear * 3.0;
    float pulse = 0.75 + 0.6 * pow(0.5 + 0.5 * sin(along * 30.0 - Time * 0.25 + seed * 0.05), 6.0);
    float wave = Wave < 0.0 ? 0.0 : 5.0 * exp(-pow((along - Wave) / 0.05, 2.0));
    float twinkle = kind == 1 ? 0.6 + 0.4 * sin(Time * 0.2 + seed) : 1.0;
    light = Color.rgb * Color.a * 4.0 * Bright * appear * (pulse + tip + wave) * twinkle;
    corner = UV0;
    float q;
    if (kind == 3) {
        // The haze: soft and wide, never less than a couple of pixels, and gone if the camera comes up close to it.
        q = max(rp, 2.0);
        light = Color.rgb * Color.a * 4.0 * Bright * appear * (0.25 + wave * 0.1) * min(1.0, rp / q) * (1.0 - smoothstep(60.0, 300.0, rp));
    } else if (kind == 2) {
        q = max(rp, 2.0) * 3.0;
    } else {
        // Never less than a pixel across; the same light spread over it, so it dims as it shrinks.
        q = max(rp, 1.0);
        light *= rp * rp / (q * q);
        q *= 1.2;
    }
    gl_Position = ProjMat * (centre + vec4(UV0 * q / perUnit, 0.0, 0.0));
}
