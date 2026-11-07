#version 150

// Echo's time-warp: at the edges of the screen a colour fringe (red and blue pulled apart along the way out), the picture ghosted a
// little further in, a cool drained tint, all of it breathing slowly. Subtle on purpose: it is played through. Pulse is the finale's
// swell (0 to 1), added to the steady Strength (0 to 1).

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform float Strength;
uniform float Pulse;
uniform float Time;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 to = texCoord - 0.5;
    vec2 q = to * vec2(ScreenSize.x / ScreenSize.y, 1.0);
    float r = length(q);
    float edge = smoothstep(0.16, 0.78, r);
    float s = Strength * 0.8 + Pulse;
    float wob = 0.5 + 0.5 * sin(Time * 0.31 + r * 8.0);
    vec2 dir = to / max(length(to), 1.0e-4);
    float disp = (0.006 + 0.009 * wob) * edge * s;
    vec3 col;
    col.r = texture(Sampler0, texCoord + dir * disp * 1.7).r;
    col.g = texture(Sampler0, texCoord).g;
    col.b = texture(Sampler0, texCoord - dir * disp * 1.7).b;
    // The ghost: the picture again, a little smaller, laid over its own edges.
    vec3 ghost = texture(Sampler0, 0.5 + to * (1.0 - 0.04 * edge * s * (0.7 + wob))).rgb;
    col = mix(col, max(col, ghost * 0.92), 0.32 * edge * s);
    float grey = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(grey) * vec3(0.94, 1.0, 1.07), 0.24 * edge * s);
    col += vec3(0.04, 0.07, 0.11) * edge * s * 0.5;
    col = mix(col, vec3(1.0), clamp((Pulse - 1.0) * 0.5, 0.0, 0.6));
    fragColor = vec4(col, 1.0);
}
