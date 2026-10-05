#version 150

// The block a universe is held in, and the window of an open gate onto the void between universes.
// Mode 0 is the block's dark glass: drawn black with alpha Dark over what is behind it (blended to darken it),
// so the galaxies inside stand out against any sky. Mode 1 is its glass: hairline edges with a glow round
// them, the far ones fainter through the dark, and a sheen where a face turns away; Heat sets it burning.
// Mode 2 is the window, a quad from -1 to 1 opening as a square from the middle (Reveal) onto the void between
// universes: what lies beyond it was drawn from the same eye into Sampler0, so the window shows that picture
// where it is on screen, with a crackling rim round the opening.

in vec3 objPos;
in vec3 objNormal;
in vec3 camObj;

uniform int Mode;
uniform float Edge;
uniform float Dark;
uniform float Heat;
uniform float Reveal;
uniform float Time;
uniform vec3 EdgeColor;
uniform vec2 ScreenSize;
uniform sampler2D Sampler0;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
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

// How close a point on a cube face is to the face's edges (0 on an edge).
float edgeDistance(vec3 p) {
    vec3 a = abs(p);
    float m = max(a.x, max(a.y, a.z));
    vec3 q = a;
    if (a.x == m) {
        q.x = 0.0;
    } else if (a.y == m) {
        q.y = 0.0;
    } else {
        q.z = 0.0;
    }
    return 1.0 - max(q.x, max(q.y, q.z));
}

void main() {
    float px = length(fwidth(objPos));
    if (Mode == 0) {
        fragColor = vec4(0.0, 0.0, 0.0, Dark);
        return;
    }
    if (Mode == 2) {
        vec2 q = objPos.xy;
        float open = max(abs(q.x), abs(q.y));
        if (open > Reveal) {
            discard;
        }
        float rim = Reveal - open;
        float crackle = 0.6 + 0.4 * noise(vec3(q * 18.0, Time * 0.6));
        vec3 c = texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb;
        c += vec3(0.9, 0.85, 1.0) * exp(-rim / max(px * 3.0, 0.004)) * 3.0 * crackle;
        c += vec3(0.7, 0.55, 1.0) * exp(-rim / 0.05) * 0.8;
        fragColor = vec4(c, 1.0);
        return;
    }
    vec3 rd = normalize(objPos - camObj);
    float e = max(edgeDistance(objPos), 0.0);
    float line = 1.0 - smoothstep(px * 0.8, px * 2.0, e);
    float halo = exp(-e / max(px * 6.0, 0.02));
    float near = gl_FrontFacing ? 1.0 : 0.4;
    float facing = abs(dot(normalize(objNormal), rd));
    vec3 c = EdgeColor * (line * 2.0 + halo * 0.2) * Edge * near;
    c += vec3(0.55, 0.5, 0.8) * pow(1.0 - facing, 4.0) * 0.3 * Edge * near;
    c += vec3(1.0, 0.9, 1.0) * Heat * (0.6 + 1.4 * halo) * near;
    fragColor = vec4(c, 1.0);
}
