#version 150

// The wall cloud's curtain: the rotating column of cloud hanging under Mjölnir's storm round the eye, drawn on an open
// cylinder (UV x once round it, y from its foot, 0, to where it joins the base of the storm, 1). Its noise comes from
// NoiseTex (Sampler0). Bands of cloud wind up it in a corkscrew as it turns; it is ragged at its foot and melts into
// the storm at its top; lightning inside the storm lights it from above, the stroke from inside. Premultiplied alpha.

uniform sampler2D Sampler0;

uniform float Time;
uniform float Spin;
uniform float Density;
uniform float Daylight;
uniform float Flash;
uniform float Stroke;
// Not FogEnd: the game sets a uniform of that name to its own fog every draw, whatever was set before.
uniform float FadeEnd;

in vec2 uv;
in vec4 vertexColor;
in vec3 viewPos;

out vec4 fragColor;

void main() {
    float viewDist = length(viewPos);
    float around = uv.x;
    float h = uv.y;
    // Bands round the column, leaning as they go: it is turning, and its air rising.
    vec4 big = texture(Sampler0, vec2(around * 3.0 + Spin * 0.8 + h * 0.7, h * 1.6 - Time * 0.0006));
    vec4 fine = texture(Sampler0, vec2(around * 9.0 + Spin * 1.2, h * 5.0) + 0.41);
    float bands = texture(Sampler0, vec2(around * 1.0 + Spin * 0.5, h * 7.0 + around * 1.2)).b;
    float n = big.r * 0.6 + fine.r * 0.4;
    // Ragged at the foot, where it hangs into clear air; melting into the storm's base at the top.
    float foot = smoothstep(0.0, 0.18 + (fine.g - 0.5) * 0.25, h);
    float head = 1.0 - smoothstep(0.8, 1.0, h);
    float body = smoothstep(0.22, 0.55, n + 0.12 + bands * 0.18);
    float d = clamp(body * foot * head * Density, 0.0, 1.0);
    if (d < 0.003) {
        discard;
    }
    float light = 0.18 + 0.8 * Daylight;
    // Darker low down, where it is thickest and furthest from the light; the bands catch what light there is.
    vec3 base = mix(vec3(0.06, 0.07, 0.09), vec3(0.3, 0.33, 0.4), 0.25 + 0.55 * h) * light;
    base *= (0.6 + 0.6 * bands) * (0.55 + 0.9 * n) * (0.8 + 0.4 * fine.g);
    vec3 color = base + vec3(0.5, 0.6, 1.0) * Flash * (0.2 + 0.8 * h) * n * n * 1.6;
    color += vec3(0.75, 0.82, 1.0) * Stroke * (0.4 + 0.6 * (1.0 - h)) * (0.5 + 0.7 * fine.r);
    float fog = 1.0 - smoothstep(FadeEnd * 1.5, FadeEnd * 3.2, viewDist);
    float alpha = d * 0.96 * fog * vertexColor.a;
    fragColor = vec4(color * alpha, alpha);
}
