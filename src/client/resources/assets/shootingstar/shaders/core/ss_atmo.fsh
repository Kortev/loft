#version 150

// Atmospheric rim drawn additively on a shell slightly larger than the planet.

uniform vec3 LightDir;
uniform vec3 GlowColor;
uniform float Intensity;
uniform float Falloff;

in vec3 viewPos;
in vec3 viewNormal;
in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 n = normalize(viewNormal);
    vec3 v = normalize(-viewPos);
    vec3 l = normalize(LightDir);
    float facing = clamp(dot(n, v), 0.0, 1.0);
    float rim = pow(1.0 - facing, Falloff);
    float edge = smoothstep(0.0, 0.3, facing);
    float lit = smoothstep(-0.35, 0.45, dot(n, l));
    float dusk = 1.0 - smoothstep(-0.1, 0.35, dot(n, l));
    vec3 color = mix(GlowColor, vec3(1.0, 0.45, 0.2), dusk * 0.6) * rim * edge * lit * Intensity;
    fragColor = vec4(color, 1.0);
}
