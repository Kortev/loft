#version 150

// Mjölnir's storm: a disc of cloud (UV -1..1 across it) wound into a vortex by log-spiral arms, thinning to ragged
// edges and opening to an eye, seen from underneath. Its noise is looked up in NoiseTex (Sampler0) rather than worked
// out per pixel: red and green billowing noise, blue ridged noise (the striations the rotation draws in it), alpha fine
// noise (the lumps hanging from its base). Lightning inside it lights it from within at Flash (x, y across the disc, z
// how bright), and the stroke lights its base from below. Premultiplied alpha.

uniform sampler2D Sampler0;

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

void main() {
    float r = length(uv);
    if (r > 1.0) {
        discard;
    }
    float a = atan(uv.y, uv.x);
    // The arms wind tighter towards the middle; coordinates that turn with them keep the noise on the spiral.
    float wind = a - Spin + 2.6 * log(r + 0.04);
    vec2 q = vec2(cos(wind), sin(wind)) * r;
    vec2 seed = vec2(Layer * 0.137, Layer * 0.291);
    // Warp the spiral a little by a slower noise, so the arms are torn rather than drawn with a ruler.
    vec4 warp = texture(Sampler0, q * 0.45 + seed + vec2(Time * 0.00025, 0.0));
    vec2 qw = q + (warp.gb - 0.5) * 0.24;
    vec4 big = texture(Sampler0, qw * 0.85 + seed * 2.0);
    vec4 mid = texture(Sampler0, qw * 2.4 + seed * 3.0 + 0.5);
    // Lumps too small to follow the spiral, drifting as the base churns.
    float lumps = texture(Sampler0, uv * 3.3 + seed * 5.0 + vec2(0.0, -Time * 0.0004)).a;
    float n = big.r * 0.62 + mid.r * 0.38;
    float detail = mid.g;
    float ridge = big.b;

    float arms = 0.5 + 0.5 * cos(2.0 * wind + n * 2.5);
    float body = smoothstep(0.3, 0.66, n + arms * 0.22 + (1.0 - r) * 0.32 - 0.12 + (detail - 0.5) * 0.32);
    float edge = 1.0 - smoothstep(0.58, 1.0, r + (n - 0.5) * 0.34);
    float eye = smoothstep(Eye, Eye * 3.0, r + (detail - 0.5) * Eye);
    float d = clamp(body * edge * eye * Density, 0.0, 1.0);
    if (d < 0.003) {
        discard;
    }

    // Underneath, thicker cloud is darker; the light of day comes through the thin parts, greener where it is
    // thickest (the hail in its core).
    float light = 0.2 + 0.8 * Daylight;
    float thick = smoothstep(0.15, 0.9, d);
    vec3 thin = vec3(0.4, 0.43, 0.5);
    vec3 dense = mix(vec3(0.075, 0.085, 0.11), vec3(0.07, 0.1, 0.1), smoothstep(0.55, 0.0, r) * Daylight);
    vec3 base = mix(thin, dense, thick) * light;
    // The lumps: pouches, dark in the middle and lit round their rims; the striations: bands along the spiral.
    float pouch = smoothstep(0.35, 0.75, lumps);
    base *= (0.72 + 0.55 * detail) * (0.7 + 0.45 * pouch) * (0.82 + 0.3 * ridge);
    // The eyewall catches whatever light comes down the eye.
    float wall = exp(-pow((r - Eye * 3.2) / max(Eye * 2.0, 0.01), 2.0));
    base += vec3(0.25, 0.3, 0.38) * wall * light * 0.6;

    // Lightning inside the cloud, glowing through it round where it is, picking out the cloud's own structure.
    vec2 fp = uv - Flash.xy;
    float inside = Flash.z * exp(-dot(fp, fp) * 11.0) * (0.45 + 1.1 * n * n) * (0.7 + 0.6 * ridge);
    vec3 color = base + vec3(0.52, 0.62, 1.0) * inside;
    // The stroke lights the base of the storm from below, brightest on the lumps that hang lowest.
    color += vec3(0.8, 0.86, 1.0) * Stroke * exp(-r * r * 9.0) * (0.45 + 0.7 * detail + 0.4 * pouch);
    float fog = 1.0 - smoothstep(FogEnd * 1.5, FogEnd * 3.2, viewDist);
    float alpha = d * 0.95 * fog * vertexColor.a;
    fragColor = vec4(color * alpha, alpha);
}
