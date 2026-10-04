#version 150

// The mirror universe: the world's own blocks, upside down and recoloured, with a ragged torn-off edge.
// ClipMode 1 keeps what has come through the tear (below ClipY), 2 what is still behind it.

uniform float ClipY;
uniform int ClipMode;
uniform float Radius;
uniform float Glow;
uniform float Fade;

in vec3 localPos;
in float worldY;
in vec4 vertexColor;
in vec2 faceUV;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    if (ClipMode == 1 && worldY > ClipY) {
        discard;
    }
    if (ClipMode == 2 && worldY < ClipY) {
        discard;
    }
    // Torn off in whole blocks along a ragged, squarish outline: chunks of it missing, never round.
    vec2 cell = floor(localPos.xz) + 0.5;
    float metric = max(abs(cell.x), abs(cell.y)) + 9.0 * hash(floor(cell / 7.0)) + 3.0 * hash(floor(cell / 2.0) + 17.0);
    if (metric > Radius * 0.92) {
        discard;
    }
    vec3 c = vertexColor.rgb;
    vec2 f = min(faceUV, 1.0 - faceUV);
    float line = 1.0 - smoothstep(0.0, 0.07, min(f.x, f.y));
    c *= 1.0 - 0.3 * line;
    c += Glow * vec3(0.08, 0.30, 0.34) * (0.5 + 0.5 * line);
    fragColor = vec4(c, Fade);
}
