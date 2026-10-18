#version 150

// What the Domain does to those it catches: everything there is, all at once and forever. The picture is torn apart
// by it: light rushing in from every side, the image doubled and split into its colours, flickering grids of
// meaning that never resolve, flashes of every colour, and over and over the white.

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform float Time;
// How hard it is pouring in now (0 to 1), and a number that changes every frame, so no two frames are alike.
uniform float Strength;
uniform float Seed;

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec3 scene(vec2 uv) {
    return texture(Sampler0, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

void main() {
    vec2 uv = texCoord;
    vec2 centre = vec2(0.5);
    vec2 to = uv - centre;
    float r = length(to * vec2(ScreenSize.x / ScreenSize.y, 1.0));
    float frame = floor(Seed);
    float s = Strength;

    // Light rushing in: the picture smeared towards the middle, split into its colours, and doubled.
    vec3 c = vec3(0.0);
    float split = 0.012 * s;
    for (int i = 0; i < 8; i++) {
        float k = float(i) / 8.0 * 0.12 * s;
        vec2 q = centre + to * (1.0 - k);
        c.r += scene(q + to * split).r;
        c.g += scene(q).g;
        c.b += scene(q - to * split).b;
    }
    c /= 8.0;
    vec2 ghost = vec2(sin(Time * 7.0), cos(Time * 5.3)) * 0.02 * s;
    c = mix(c, scene(uv + ghost), 0.35 * s);

    // Streaks of it, straight out from the middle, racing.
    float ang = atan(to.y, to.x);
    float lane = floor(ang * 90.0);
    float run = fract(r * (1.5 + 2.0 * hash(vec2(lane, 1.0))) - Time * (1.2 + 2.5 * hash(vec2(lane, 2.0))));
    float streak = step(0.72, hash(vec2(lane, frame * 0.0 + 3.0))) * smoothstep(0.0, 0.1, run) * smoothstep(0.35, 0.1, run);
    c += vec3(0.75, 0.9, 1.0) * streak * smoothstep(0.05, 0.5, r) * 1.4 * s;

    // Meaning in grids, flickering every frame: rows of marks too fast to read, in patches.
    vec2 cellSize = vec2(10.0, 14.0) / ScreenSize;
    vec2 cell = floor(uv / cellSize);
    vec2 area = floor(uv * vec2(6.0, 5.0));
    float areaOn = step(0.6, hash(area + frame * 0.37));
    vec2 inCell = fract(uv / cellSize);
    float glyph = step(0.5, hash(cell + frame)) * step(0.15, inCell.x) * step(inCell.x, 0.85)
            * step(0.2, inCell.y) * step(inCell.y, 0.8) * step(0.35, hash(floor(inCell * 3.0) + cell + frame * 1.7));
    vec3 ink = mix(vec3(0.6, 0.85, 1.0), vec3(1.0), hash(cell + 9.0));
    c = mix(c, ink, glyph * areaOn * 0.55 * s);

    // Flashes: now and then the whole picture one colour, then white.
    float flash = step(0.86, hash(vec2(frame, 7.0)));
    vec3 tint = hash(vec2(frame, 8.0)) > 0.5 ? vec3(0.3, 0.55, 1.0) : hash(vec2(frame, 9.0)) > 0.5 ? vec3(1.0, 0.35, 0.8) : vec3(1.0);
    c = mix(c, tint, flash * 0.5 * s);
    float white = 0.5 + 0.5 * sin(Time * 2.3);
    c = mix(c, vec3(1.0), white * white * 0.35 * s);

    // The edges burn out.
    c += vec3(0.6, 0.8, 1.0) * smoothstep(0.35, 0.95, r) * 0.6 * s;
    fragColor = vec4(c, 1.0);
}
