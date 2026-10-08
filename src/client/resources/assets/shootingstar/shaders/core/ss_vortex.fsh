#version 150

// Mjölnir's storm: a disc of cloud (UV -1..1 across it) wound into a vortex by log-spiral arms, thinning to ragged
// edges and opening to an eye, seen from underneath. Lightning inside it lights it from within at Flash (x, y across
// the disc, z how bright), and the stroke lights its base from below. Premultiplied alpha.

uniform float Time;
uniform float Spin;
uniform float Density;
uniform float Layer;
uniform float Daylight;
uniform vec3 Flash;
uniform float Stroke;
uniform float Eye;
uniform float FogEnd;

in vec2 uv;
in vec4 vertexColor;
in float viewDist;

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
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        amp *= 0.5;
    }
    return sum;
}

void main() {
    float r = length(uv);
    if (r > 1.0) {
        discard;
    }
    float a = atan(uv.y, uv.x);
    // The arms wind tighter towards the middle; coordinates that turn with them keep the noise on the spiral.
    float wind = a - Spin + 2.6 * log(r + 0.04);
    vec2 q = vec2(cos(wind), sin(wind)) * r;
    float n = fbm(q * 3.2 + vec2(Layer * 5.3, Layer * 2.1) + vec2(Time * 0.003, 0.0));
    float detail = fbm(q * 11.0 + vec2(Layer * 1.7, 3.0));
    // Lumps hanging from the base of the cloud, too small to follow the spiral.
    float lumps = fbm(uv * 26.0 + vec2(Layer * 3.1, -Time * 0.002));
    float arms = 0.5 + 0.5 * cos(2.0 * wind + n * 2.5);
    float body = smoothstep(0.24, 0.62, n * 1.0 + arms * 0.24 + (1.0 - r) * 0.34 - 0.1 + (detail - 0.5) * 0.35);
    float edge = 1.0 - smoothstep(0.6, 1.0, r + (n - 0.5) * 0.3);
    float eye = smoothstep(Eye, Eye * 3.0, r + (detail - 0.5) * Eye);
    float d = clamp(body * edge * eye * Density, 0.0, 1.0);
    if (d < 0.003) {
        discard;
    }

    // Underneath, thicker cloud is darker; the light of day comes through the thin parts.
    float light = 0.22 + 0.78 * Daylight;
    vec3 base = mix(vec3(0.36, 0.38, 0.44), vec3(0.09, 0.1, 0.13), smoothstep(0.15, 0.9, d)) * light;
    base *= (0.7 + 0.6 * detail) * (0.78 + 0.44 * lumps);
    // Lightning inside the cloud, glowing through it round where it is.
    vec2 fp = uv - Flash.xy;
    float inside = Flash.z * exp(-dot(fp, fp) * 14.0) * (0.55 + 0.9 * n);
    vec3 color = base + vec3(0.5, 0.62, 1.0) * inside;
    // The stroke lights the base of the storm from below.
    color += vec3(0.8, 0.86, 1.0) * Stroke * exp(-r * r * 9.0) * (0.5 + 0.8 * detail);
    float fog = 1.0 - smoothstep(FogEnd * 1.5, FogEnd * 3.2, viewDist);
    float alpha = d * 0.94 * fog * vertexColor.a;
    fragColor = vec4(color * alpha, alpha);
}
