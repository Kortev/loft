#version 150

// Turbulent plasma for the re-entry sheath and the impact fireball. Drawn additively.

uniform float Time;
uniform float Intensity;
uniform float Heat;
uniform vec3 Flow;
uniform float Scale;
// 1 for the impact fireball, drawn cel-shaded like the impact frames; 0 for the feed's re-entry sheath.
uniform float Toon;

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

float fbm(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += noise(p) * amp;
        p = p * 2.03 + vec3(1.7, 9.2, 4.1);
        amp *= 0.5;
    }
    return sum;
}

vec3 ramp(float t) {
    vec3 c = mix(vec3(0.35, 0.03, 0.01), vec3(1.0, 0.32, 0.05), smoothstep(0.0, 0.45, t));
    c = mix(c, vec3(1.0, 0.75, 0.3), smoothstep(0.4, 0.75, t));
    return mix(c, vec3(1.0, 0.97, 0.9), smoothstep(0.7, 1.0, t));
}

void main() {
    vec3 n = normalize(viewNormal);
    vec3 v = normalize(-viewPos);
    float facing = abs(dot(n, v));
    vec3 p = objPos * Scale - Flow * Time;
    float f = fbm(p * 2.2);
    float g = fbm(p * 5.0 + f * 2.0);
    float heat = clamp(Heat * (0.45 + 0.75 * f + 0.25 * g) * (0.4 + 0.6 * facing), 0.0, 1.0);
    if (Toon > 0.5) {
        // Flat bands of fire with a hard, ragged edge and a dark red line round it; as it cools it is eaten away
        // instead of fading. Opaque, so it hides what is behind it like a drawn fireball.
        float edge = f + 0.25 * facing;
        float cut = mix(0.72, 0.3, clamp(Intensity, 0.0, 1.0));
        float ew = fwidth(edge) + 1.0e-4;
        float body = smoothstep(cut - ew, cut + ew, edge);
        if (body < 0.01) {
            discard;
        }
        float t = heat * (1.0 + 0.35 * (edge - cut));
        float tw = fwidth(t) + 1.0e-4;
        vec3 c = mix(vec3(0.9, 0.16, 0.04), vec3(1.0, 0.45, 0.07) * 1.6, smoothstep(0.3 - tw, 0.3 + tw, t));
        c = mix(c, vec3(1.0, 0.8, 0.25) * 2.2, smoothstep(0.52 - tw, 0.52 + tw, t));
        c = mix(c, vec3(1.0, 0.97, 0.86) * 3.2, smoothstep(0.74 - tw, 0.74 + tw, t));
        float rim = 1.0 - smoothstep(0.16, 0.2, facing);
        float line = smoothstep(cut + ew * 2.5, cut + ew * 1.5, edge);
        c = mix(c, vec3(0.42, 0.06, 0.02), max(rim, line));
        fragColor = vec4(c * body, body);
        return;
    }
    float alpha = Intensity * smoothstep(0.15, 0.6, f + 0.2 * facing) * (0.35 + 0.65 * facing);
    fragColor = vec4(ramp(heat) * alpha, 1.0);
}
