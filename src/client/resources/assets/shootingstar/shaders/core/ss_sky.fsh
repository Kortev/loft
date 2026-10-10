#version 150

// Milky Way background looked up by view direction, with relativistic aberration at high speed.

uniform sampler2D Sampler0;
uniform mat4 InvViewProj;
uniform mat4 SkyRot;
uniform float Brightness;
uniform float Beta;
uniform vec3 Forward;

in vec2 texCoord;

out vec4 fragColor;

const float PI = 3.14159265;

void main() {
    vec4 p = InvViewProj * vec4(texCoord * 2.0 - 1.0, 1.0, 1.0);
    vec3 d = normalize(p.xyz / p.w);
    float boost = 1.0;
    if (Beta > 0.0001) {
        // Light reaching us from d left its source from c0 in the rest frame; beaming brightens ahead.
        float c = dot(d, Forward);
        float c0 = (c - Beta) / (1.0 - Beta * c);
        vec3 perp = d - Forward * c;
        float pl = length(perp);
        vec3 pd = pl > 1.0e-5 ? perp / pl : vec3(0.0);
        float gamma = 1.0 / sqrt(1.0 - Beta * Beta);
        float doppler = gamma * (1.0 + Beta * c0);
        boost = clamp(doppler * doppler, 0.05, 3.0);
        d = Forward * c0 + pd * sqrt(max(0.0, 1.0 - c0 * c0));
    }
    vec3 g = (SkyRot * vec4(d, 0.0)).xyz;
    float lon = atan(g.z, g.x);
    float lat = asin(clamp(g.y, -1.0, 1.0));
    vec2 uv = vec2(lon / (2.0 * PI) + 0.5, 0.5 - lat / PI);
    // Pick derivatives that do not jump at the longitude seam.
    vec2 uv2 = vec2(fract(uv.x + 0.5) - 0.5, uv.y);
    vec2 dx = dFdx(uv);
    vec2 dy = dFdy(uv);
    vec2 dx2 = dFdx(uv2);
    vec2 dy2 = dFdy(uv2);
    if (dot(dx2, dx2) + dot(dy2, dy2) < dot(dx, dx) + dot(dy, dy)) {
        dx = dx2;
        dy = dy2;
    }
    vec3 color = textureGrad(Sampler0, uv, dx, dy).rgb;
    color = mix(vec3(dot(color, vec3(0.299, 0.587, 0.114))), color, 0.7) * Brightness * boost;
    fragColor = vec4(color, 1.0);
}
