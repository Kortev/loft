#version 150

// Cel-shaded puffs of fire, smoke and dust, drawn like the impact frames: hard, lumpy cartoon-cloud shapes, flat light
// and shadow bands, and fire in flat bands from a white-hot core out to a red rim. A dying puff breaks into pieces and
// shrinks away instead of going transparent. Drawn in two passes: first every puff a little fatter in ink, then every
// puff's fill over the top, so the ink line only shows round the outside of each cloud (a trail reads as one tube,
// not a string of beads). Blended premultiplied over the scene and cut softly where it meets the terrain.

uniform sampler2D Sampler1;
uniform vec2 ScreenSize;
uniform float ProjA;
uniform float ProjB;
uniform float Softness;
// 0: the ink pass (the puff fattened by the line's width, all ink); 1: the fill pass.
uniform float Pass;

// corner: -1..1 across the puff, turned with its spin; local: the same, upright on screen.
in vec2 corner;
in vec2 local;
// rgb: the smoke's colour in the light; a: how much of the puff is left (it pops in and breaks up by this).
in vec4 vertexColor;
in float seed;
in float glow;

out vec4 fragColor;

// Flat lighting from the upper right and a little in front, in screen space, like a drawn cloud.
const vec3 LIGHT = vec3(0.37, 0.79, 0.49);
const vec3 INK = vec3(0.07, 0.05, 0.065);

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    return noise(p) * 0.5 + noise(p * 2.1 + 3.7) * 0.3 + noise(p * 4.3 + 9.1) * 0.2;
}

// 0 below the edge, 1 above it, antialiased over a pixel.
float band(float x, float edge) {
    float w = fwidth(x) + 1.0e-4;
    return smoothstep(edge - w, edge + w, x);
}

void main() {
    vec2 p = corner + seed * 13.0;
    float left = clamp(vertexColor.a, 0.0, 1.0);
    // Big round billows round the outline; the puff grows in as it appears and shrinks as it goes.
    float billows = fbm(p * 1.25);
    float outline = length(corner) + (billows - 0.5) * 0.5 - mix(0.3, 0.9, sqrt(left));
    // A dying puff is eaten away from the edge inwards in a few big bites until it falls apart.
    float holes = (fbm(p * 1.4 + 5.0) + length(corner) * 0.35 - left * 1.5) * 0.6;
    float s = max(outline, holes);
    float aa = fwidth(s) + 1.0e-4;
    // The ink line, a couple of pixels wide (thinner on small, far puffs): the ink pass draws the puff this much fatter.
    float line = min(aa * 3.2, 0.1);
    float inside = 1.0 - smoothstep(-aa, aa, s - (Pass < 0.5 ? line : 0.0));
    if (inside <= 0.003) {
        discard;
    }
    // Both distances from depth values, so they agree even while the camera shakes.
    float z = texture(Sampler1, gl_FragCoord.xy / ScreenSize).r * 2.0 - 1.0;
    float scene = ProjB / (z + ProjA);
    float puff = ProjB / ((gl_FragCoord.z * 2.0 - 1.0) + ProjA);
    float soft = clamp((scene - puff) / Softness, 0.0, 1.0);

    // A ball's normal, bumped by the billows, cut into flat light, mid and shadow tones.
    vec2 q = local * 0.92;
    vec3 n = normalize(vec3(q, sqrt(max(1.0 - dot(q, q), 0.0)) + 0.15));
    float bump = fbm(p * 2.2 + 1.3);
    float lit = dot(n, LIGHT) + (bump - 0.5) * 0.6;
    vec3 base = vertexColor.rgb;
    vec3 color = mix(base * vec3(0.5, 0.47, 0.6), base * 0.9, band(lit, -0.12));
    color = mix(color, base * 1.25 + vec3(0.03, 0.025, 0.0), band(lit, 0.45));

    // Fire in flat bands, white in the middle of the hottest puffs, shrinking into the middle as the puff cools.
    float heat = glow * (1.25 - length(local) * 1.1) * (0.75 + 0.5 * bump);
    float burning = band(heat, 0.5);
    vec3 fire = mix(vec3(0.95, 0.16, 0.04), vec3(1.0, 0.45, 0.07) * 2.0, band(heat, 0.68));
    fire = mix(fire, vec3(1.0, 0.8, 0.25) * 2.8, band(heat, 1.0));
    fire = mix(fire, vec3(1.0, 0.97, 0.86) * 4.0, band(heat, 1.75));
    color = mix(color, fire, burning);
    if (Pass < 0.5) {
        // Ink: near black round smoke and dust, a dark red round fire.
        color = mix(INK, vec3(0.42, 0.06, 0.02), band(glow, 0.9));
    }

    float a = inside * soft;
    fragColor = vec4(color * a, a);
}
