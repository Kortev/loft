#version 150

// The condensation shell racing out ahead of the fireball: a bright rim, clear in the middle.

uniform vec3 GlowColor;
uniform float Intensity;
uniform float Falloff;

in vec3 viewPos;
in vec3 viewNormal;
in vec3 objPos;
in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 n = normalize(viewNormal);
    vec3 v = normalize(-viewPos);
    float facing = abs(dot(n, v));
    float rim = pow(1.0 - facing, Falloff);
    // Fade towards the ground so the shell sits on the terrain instead of ending in a hard line.
    float ground = smoothstep(-0.02, 0.25, objPos.y);
    fragColor = vec4(GlowColor * rim * ground * Intensity, 1.0);
}
