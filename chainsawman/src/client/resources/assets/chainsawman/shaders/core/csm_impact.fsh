#version 150

// Chainsaw Man's roar, as the anime cuts it: impact frames, the picture flattened to three inks (black, a hard red and
// white), and on the next frame flipped to its negative; with speed lines bursting out from the middle. And the edges
// of the screen washed with red, from the burst and the roar, fading.

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
// 0: the picture as it is; 1: the impact frame; 2: its negative.
uniform float Style;
// How red the screen's edges are (0 to 1).
uniform float Vignette;

in vec2 texCoord;

out vec4 fragColor;

const vec3 INK = vec3(0.02, 0.0, 0.01);
const vec3 RED = vec3(0.86, 0.03, 0.06);
const vec3 PAPER = vec3(0.98, 0.96, 0.94);

float hash(float x) {
    return fract(sin(x * 127.1) * 43758.5453);
}

void main() {
    vec3 colour = texture(Sampler0, texCoord).rgb;
    vec2 to = (texCoord - 0.5) * vec2(ScreenSize.x / ScreenSize.y, 1.0);
    float r = length(to);
    if (Style > 0.5) {
        float lum = dot(colour, vec3(0.299, 0.587, 0.114));
        vec3 ink = lum < 0.2 ? INK : lum < 0.5 ? RED : PAPER;
        if (Style > 1.5) {
            ink = lum < 0.2 ? PAPER : lum < 0.5 ? INK : RED;
        }
        // Speed lines: thin wedges out from the middle, clear of it.
        float a = atan(to.y, to.x);
        float line = step(0.93, hash(floor(a * 60.0))) * smoothstep(0.18, 0.5, r);
        colour = mix(ink, Style > 1.5 ? INK : PAPER, line);
    }
    float edge = smoothstep(0.35, 0.95, r);
    colour = mix(colour, vec3(0.45, 0.0, 0.02), clamp(Vignette * edge, 0.0, 0.85));
    fragColor = vec4(colour, 1.0);
}
