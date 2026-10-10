#version 150

// The condensation shell racing out ahead of the fireball: a bright rim, clear in the middle.

uniform vec3 GlowColor;
uniform float Intensity;
uniform float Falloff;
// 1: a crisp drawn ring round the shell's edge, like the impact frames, instead of a soft glow.
uniform float Toon;

in vec3 viewPos;
in vec3 viewNormal;
in vec3 objPos;
in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 n = normalize(viewNormal);
    vec3 v = normalize(-viewPos);
    float facing = min(abs(dot(n, v)), 1.0);
    float rim = pow(1.0 - facing, Falloff);
    if (Toon > 0.5) {
        float x = 1.0 - facing;
        float w = fwidth(x) + 1.0e-4;
        rim = (smoothstep(0.86 - w, 0.86 + w, x) - smoothstep(0.97 - w, 0.97 + w, x)) * 1.6 + rim * 0.15;
    }
    // Fade towards the ground so the shell sits on the terrain instead of ending in a hard line.
    float ground = smoothstep(-0.02, 0.25, objPos.y);
    fragColor = vec4(GlowColor * rim * ground * Intensity, 1.0);
}
