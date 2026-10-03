#version 150

// Soft, noisy puffs blended premultiplied over the scene, faded where they meet the terrain.
// Hot puffs also emit light (fire), which can go past white in the HDR effects buffer.

uniform sampler2D Sampler1;
uniform vec2 ScreenSize;
uniform float ProjA;
uniform float ProjB;
uniform float Softness;

in vec2 corner;
in vec4 vertexColor;
in float seed;
in float glow;

out vec4 fragColor;

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

vec3 fire(float t) {
    vec3 c = mix(vec3(0.5, 0.06, 0.01), vec3(1.0, 0.38, 0.06), smoothstep(0.0, 0.5, t));
    c = mix(c, vec3(1.0, 0.8, 0.45), smoothstep(0.5, 1.0, t));
    return c;
}

void main() {
    vec2 p = corner + seed * 13.0;
    float r = length(corner);
    float n = fbm(p * 1.7);
    float shape = smoothstep(1.0, 0.25, r + (n - 0.5) * 0.7);
    if (shape <= 0.002) {
        discard;
    }
    // Both distances from depth values, so they agree even while the camera shakes.
    float z = texture(Sampler1, gl_FragCoord.xy / ScreenSize).r * 2.0 - 1.0;
    float scene = ProjB / (z + ProjA);
    float puff = ProjB / ((gl_FragCoord.z * 2.0 - 1.0) + ProjA);
    float soft = clamp((scene - puff) / Softness, 0.0, 1.0);
    float detail = fbm(p * 3.0);
    float shade = 0.7 + 0.45 * detail;
    float a = shape * vertexColor.a * soft;
    // Fire burns brightest in the dense middle of the puff.
    float heat = glow * shape * soft * (0.55 + 0.6 * n);
    vec3 emit = fire(clamp(heat * 0.5, 0.0, 1.0)) * heat;
    fragColor = vec4(vertexColor.rgb * shade * a + emit, a);
}
