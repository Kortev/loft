#version 150

// See ss_galaxy.vsh. Added to what is behind; Heat whitens a universe that is burning up.

in vec3 light;
in vec2 corner;
flat in int kind;
in float shape;
in float phase;

uniform float Heat;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(0.3183099, 0.3678794) + vec2(0.1, 0.7));
    p *= 17.0;
    return fract(p.x * p.y * (p.x + p.y));
}

float noise(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    float r = length(corner);
    float strength = max(max(light.r, light.g), light.b);
    vec3 c;
    if (kind == 0) {
        c = light * exp(-r * r * 4.5) * 1.1;
    } else if (kind == 3) {
        c = light * exp(-r * r * 5.0) * (1.0 - smoothstep(0.55, 1.0, r)) * 0.08;
    } else if (kind == 2) {
        // An elliptical: steep in the middle, a long faint halo, a little squashed.
        vec2 q = vec2(corner.x, corner.y * (1.0 + shape * 0.8));
        float s = length(q);
        c = light * (exp(-sqrt(s) * 5.5) * 3.0 + exp(-s * s * 6.0) * 0.25) * (1.0 - smoothstep(0.8, 1.0, s));
    } else {
        // A spiral face on: two arms winding out of a warm bulge, knots of new stars along them, dust between.
        if (r > 1.25) {
            discard;
        }
        float a = atan(corner.y, corner.x);
        float swirl = a - log(r + 0.03) * (1.7 + 1.5 * shape) - phase;
        float arms = pow(0.5 + 0.5 * cos(2.0 * swirl), 3.0);
        float fine = 0.65 + 0.35 * noise(vec2(r * 22.0, swirl * 4.0));
        float disc = exp(-r * 3.0) * (1.0 - smoothstep(0.7, 1.15, r));
        float knots = smoothstep(0.72, 0.95, noise(vec2(r * 18.0, swirl * 6.0 + phase))) * arms;
        float lanes = 1.0 - 0.6 * smoothstep(0.55, 0.8, noise(vec2(r * 30.0, swirl * 5.0) + 4.0)) * (1.0 - arms);
        c = light * disc * (0.18 + 1.3 * arms * fine) * lanes * 1.6;
        c += vec3(1.0, 0.45, 0.8) * knots * disc * 1.8 * strength;
        c += vec3(1.0, 0.86, 0.62) * (exp(-r * r * 40.0) * 2.2 + exp(-r * r * 6.0) * 0.25) * strength;
    }
    c = mix(c, vec3(max(max(c.r, c.g), c.b)) * vec3(1.0, 0.92, 1.0), clamp(Heat, 0.0, 1.0) * 0.7);
    fragColor = vec4(c, 1.0);
}
