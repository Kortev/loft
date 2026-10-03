#version 150

// Hard-surface and rock shading for the feed's meshes. Vertex colour carries baked occlusion (r),
// a glow mask (g) and a material id (b); see tools/models.py.

uniform vec3 LightDir;
uniform vec3 LightColor;
uniform vec3 AmbientColor;
uniform vec3 RimColor;
uniform vec3 GlowColor;
uniform float GlowStrength;
uniform float Heat;
uniform float Fade;

in vec3 viewPos;
in vec3 viewNormal;
in vec3 objPos;
in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(hash(i + vec3(0.0, 1.0, 0.0)), hash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(hash(i + vec3(0.0, 0.0, 1.0)), hash(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(hash(i + vec3(0.0, 1.0, 1.0)), hash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

void main() {
    vec3 n = normalize(viewNormal);
    if (!gl_FrontFacing) {
        n = -n;
    }
    vec3 v = normalize(-viewPos);
    vec3 l = normalize(LightDir);
    float ao = vertexColor.r;
    float glowMask = vertexColor.g;
    int mat = int(vertexColor.b * 255.0 + 0.5);

    vec3 albedo = vec3(0.045);
    float spec = 0.5;
    float shine = 48.0;
    vec3 emit = vec3(0.0);
    if (mat == 0) {
        // Hull plating with faint seams every 0.8 units along the round.
        float seam = 1.0 - smoothstep(0.0, 0.035, abs(fract(objPos.z * 1.25) - 0.5) - 0.44);
        albedo = vec3(0.04, 0.04, 0.046) * (1.0 - 0.45 * seam);
        spec = 0.65;
        shine = 64.0;
    } else if (mat == 1) {
        albedo = vec3(0.05, 0.046, 0.042);
    } else if (mat == 2) {
        albedo = vec3(0.07, 0.066, 0.062);
        spec = 0.85;
        shine = 80.0;
        float tip = smoothstep(3.4, 5.0, objPos.z);
        emit += mix(vec3(1.0, 0.32, 0.06), vec3(1.0, 0.92, 0.82), tip) * Heat * (0.15 + tip * 1.8);
    } else if (mat == 3) {
        albedo = vec3(0.06, 0.06, 0.066);
        spec = 0.4;
        shine = 32.0;
    } else if (mat == 4) {
        albedo = vec3(0.1, 0.05, 0.025);
        spec = 0.25;
    } else if (mat == 5) {
        float grain = noise(objPos * 3.1) * 0.6 + noise(objPos * 11.0) * 0.4;
        albedo = mix(vec3(0.12, 0.105, 0.09), vec3(0.3, 0.27, 0.23), grain);
        spec = 0.05;
        shine = 6.0;
    } else if (mat == 6) {
        albedo = vec3(0.6, 0.42, 0.12);
        spec = 1.0;
        shine = 24.0;
    } else if (mat == 7) {
        vec2 cell = fract(objPos.xz * 7.0);
        float grid = step(0.9, max(cell.x, cell.y));
        albedo = mix(vec3(0.015, 0.03, 0.08), vec3(0.2), grid);
        spec = 1.0;
        shine = 120.0;
    } else if (mat == 8) {
        albedo = vec3(0.62);
        spec = 0.3;
        shine = 16.0;
    }
    emit += GlowColor * GlowStrength * glowMask;

    float ndl = max(dot(n, l), 0.0);
    vec3 h = normalize(l + v);
    float s = pow(max(dot(n, h), 0.0), shine) * spec * step(0.0001, ndl);
    float rim = pow(1.0 - max(dot(n, v), 0.0), 4.0);
    vec3 color = albedo * (AmbientColor * ao + LightColor * ndl) + LightColor * s * ao + RimColor * rim * ao + emit;
    fragColor = vec4(color * Fade, 1.0);
}
