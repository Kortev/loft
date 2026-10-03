#version 150

// Feed finish: bloom, optional zoom blur and chromatic fringe, vignette, scanlines, grain and flashes.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform float BloomStrength;
uniform float WideStrength;
uniform float Vignette;
uniform float Grain;
uniform float Time;
uniform float Aberration;
uniform float Flash;
uniform vec3 FlashColor;
uniform float Scanlines;
uniform float Fade;
uniform vec2 ScreenSize;
uniform float ZoomBlur;
uniform float Saturation;

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 scene(vec2 uv) {
    if (Aberration > 0.0) {
        vec2 off = (uv - 0.5) * Aberration;
        return vec3(texture(Sampler0, uv - off).r, texture(Sampler0, uv).g, texture(Sampler0, uv + off).b);
    }
    return texture(Sampler0, uv).rgb;
}

void main() {
    vec2 uv = texCoord;
    vec3 color;
    if (ZoomBlur > 0.0) {
        color = vec3(0.0);
        for (int i = 0; i < 12; i++) {
            float k = 1.0 - ZoomBlur * float(i) / 11.0;
            color += scene((uv - 0.5) * k + 0.5);
        }
        color /= 12.0;
    } else {
        color = scene(uv);
    }
    color += texture(Sampler1, uv).rgb * BloomStrength + texture(Sampler2, uv).rgb * WideStrength;
    float grey = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(grey), color, Saturation);
    vec2 d = uv - 0.5;
    color *= 1.0 - Vignette * dot(d, d) * 2.4;
    color *= 1.0 - Scanlines * 0.1 * step(0.5, fract(gl_FragCoord.y / 3.0));
    color += (hash(floor(uv * ScreenSize) + floor(Time * 24.0) * 17.0) - 0.5) * Grain;
    color = mix(color, FlashColor, clamp(Flash, 0.0, 1.0));
    fragColor = vec4(color * Fade, 1.0);
}
