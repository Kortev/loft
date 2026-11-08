#version 150

// Forever's drain: while the Eternity Devil holds a moment, the world near it loses its colour, toward a grey-sepia that is strong
// but still plays. Strength (0 to 1) is how drained; Mine is 1 for the one who is held, whose edges also go dark and brown; Pulse is
// the white flash of the release.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform mat4 InvProj;
uniform mat4 InvView;
uniform vec3 CubeCentre;
uniform float CubeHalf;
uniform float CubeOn;
uniform float CamInside;
uniform vec2 ScreenSize;
uniform float Strength;
uniform float Mine;
uniform float Pulse;
uniform float Time;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 to = texCoord - 0.5;
    float r = length(to * vec2(ScreenSize.x / ScreenSize.y, 1.0));
    vec3 col = texture(Sampler0, texCoord).rgb;
    float grey = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 sepia = vec3(grey) * vec3(1.1, 0.96, 0.76);
    vec3 drained = mix(vec3(grey), sepia, 0.8);
    drained = (drained - 0.5) * 0.9 + 0.5 + vec3(0.025, 0.012, -0.02);
    // The devil's flesh and blood (pink and red: red well over green, blue near green) keep their colour: only the world drains.
    float pink = smoothstep(1.3, 1.75, col.r / (col.g + 0.02)) * smoothstep(0.62, 0.9, col.b / (col.g + 0.02)) * smoothstep(0.25, 0.5, col.r);
    float keep = clamp(pink * 0.88, 0.0, 0.88);
    // Inside a cube of frozen time (the point a pixel shows is inside it) the world is grey; outside it stays in colour.
    float inCube = 0.0;
    if (CubeOn > 0.0) {
        float depth = texture(Sampler1, texCoord).r;
        if (depth >= 0.99999) {
            inCube = CamInside;
        } else {
            vec4 v = InvProj * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
            v /= v.w;
            vec3 w = (InvView * vec4(v.xyz, 0.0)).xyz;
            vec3 d = abs(w - CubeCentre);
            inCube = step(max(d.x, max(d.y, d.z)), CubeHalf);
        }
    }
    float amount = max(Strength, inCube * 0.95 * CubeOn);
    col = mix(col, drained, clamp(amount * 0.92, 0.0, 1.0) * (1.0 - keep));
    float edge = smoothstep(0.22, 0.85, r);
    float breath = 0.9 + 0.1 * sin(Time * 0.21);
    col = mix(col, col * vec3(0.5, 0.33, 0.3), clamp(edge * Mine * 0.9 * breath, 0.0, 1.0));
    col = mix(col, vec3(1.0), clamp(Pulse, 0.0, 1.0));
    fragColor = vec4(col, 1.0);
}
