#version 150

// Whatever is in the void with the camera, drawn into a picture of its own and laid back over the void here: in the
// dark of it, lit only from the great eye, its edges on the side facing the eye burning blue-white.

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
// Where the eye is on the screen, and whether it is in front of the camera at all (1) or behind it (0).
uniform vec2 EyeUv;
uniform float EyeAhead;
uniform float Strength;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    vec4 e = texture(Sampler0, uv);
    if (e.a < 0.01) {
        discard;
    }
    vec2 px = 1.0 / ScreenSize;
    // Towards the eye on the screen; with it behind the camera, light from over the shoulder (down the screen).
    vec2 toward = EyeAhead > 0.5 ? normalize((EyeUv - uv) * ScreenSize + vec2(1.0e-4)) : vec2(0.0, -1.0);
    float rim = 0.0;
    for (int i = 1; i <= 5; i++) {
        float a = texture(Sampler0, uv + toward * px * float(i) * 1.6).a;
        rim = max(rim, (1.0 - a) * (1.0 - float(i - 1) * 0.15));
    }
    vec3 dark = e.rgb * vec3(0.28, 0.34, 0.5);
    vec3 lit = mix(e.rgb, dark, Strength) + vec3(0.55, 0.78, 1.0) * rim * 1.1 * Strength;
    fragColor = vec4(lit, e.a);
}
