#version 150

// The ground under Mjölnir's storm in the feed's leader shot, seen from a few kilometres up at night: farmland, forest
// and rivers worked out from NoiseTex (Sampler0) at every scale from kilometres down to tens of metres, so it is sharp
// however near the camera comes; towns and their lights; lit only by the stepped leader overhead (Tip, a point light,
// TipLight how bright) and the storm's flashes (Flash), and lost in rain haze with distance. UV is the ground plane in
// the shot's units (a hundred metres). Premultiplied alpha.

uniform sampler2D Sampler0;
uniform float Time;
uniform vec3 Tip;
uniform float TipLight;
uniform float Flash;
uniform float Haze;

in vec2 uv;
in vec4 vertexColor;
in float viewDist;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    // Land: fields and forest in patches a few kilometres across, broken up finer and finer.
    vec4 big = texture(Sampler0, uv * 0.0021 + 0.13);
    vec4 mid = texture(Sampler0, uv * 0.017 + 0.51);
    vec4 fine = texture(Sampler0, uv * 0.13 + 0.77);
    float forest = smoothstep(0.45, 0.6, big.r * 0.7 + mid.g * 0.3);
    vec3 fields = mix(vec3(0.16, 0.15, 0.1), vec3(0.2, 0.22, 0.12), mid.r);
    // Field boundaries: a grid of hedges, turned a little in each patch.
    float ang = big.g * 3.0;
    vec2 f = mat2(cos(ang), -sin(ang), sin(ang), cos(ang)) * uv * 0.35;
    float hedge = smoothstep(0.92, 0.98, max(abs(fract(f.x) - 0.5), abs(fract(f.y * 0.7) - 0.5)) * 2.0);
    fields = mix(fields, vec3(0.06, 0.08, 0.05), hedge * (1.0 - forest));
    vec3 land = mix(fields, vec3(0.05, 0.08, 0.05) * (0.7 + 0.6 * fine.r), forest);
    land *= 0.75 + 0.5 * fine.a;
    // Rivers: the creases of the ridged noise, catching what light there is.
    float river = smoothstep(0.93, 0.985, texture(Sampler0, uv * 0.0035 + 0.31).b);
    land = mix(land, vec3(0.05, 0.07, 0.1), river);

    // Light: the leader's tip overhead and the storm's flashes, falling on the ground; water shines.
    vec3 toTip = Tip - vec3(uv.x, 0.0, uv.y);
    float d2 = dot(toTip, toTip);
    float tip = TipLight * 2.5 * (Tip.y / sqrt(d2 + 1.0)) / (1.0 + d2 * 0.00015);
    // A little light all over from the glow of the storm's base and the towns, so the country reads in the dark.
    float light = 0.45 + tip + Flash * 1.4;
    vec3 color = land * light * vec3(0.8, 0.85, 1.15) + vec3(0.6, 0.7, 1.0) * river * (tip + Flash) * 0.35;

    // Towns: clusters of lights, warm, that the storm cannot put out.
    float town = smoothstep(0.72, 0.82, texture(Sampler0, uv * 0.0012 + 0.67).g);
    vec2 g = uv * 1.6;
    vec2 cell = floor(g);
    vec2 at = vec2(hash(cell + 3.1), hash(cell + 7.7));
    float lamp = step(0.55, hash(cell)) * smoothstep(0.22, 0.0, length(fract(g) - at));
    color += vec3(1.0, 0.72, 0.4) * lamp * (0.03 + town) * 3.0;

    // Rain haze: the farther, the more it is lost in the dark of the storm.
    float haze = 1.0 - exp(-viewDist * Haze);
    color = mix(color, vec3(0.045, 0.05, 0.07) + vec3(0.3, 0.35, 0.5) * Flash * 0.4, haze);
    fragColor = vec4(color, 1.0) * vertexColor.a;
}
