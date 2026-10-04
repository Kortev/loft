#version 150

// Light from the blast falling on the world: positions and face normals come back out of the depth
// buffer, the surface colour is estimated from the finished frame, and the reflected light is added
// to the HDR effects buffer (so it blooms and is smoked over like everything else there).

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform mat4 InvViewProj;
uniform vec2 ScreenSize;
uniform float Ambient;
uniform vec2 Fog;
uniform vec4 Light0Pos;
uniform vec4 Light0Color;
uniform vec4 Light1Pos;
uniform vec4 Light1Color;
uniform vec4 Light2Pos;
uniform vec4 Light2Color;
uniform vec4 Light3Pos;
uniform vec4 Light3Color;

in vec2 texCoord;

out vec4 fragColor;

vec3 position(vec2 uv) {
    float depth = texture(Sampler1, uv).r;
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

// pos.xyz relative to the camera, pos.w the distance at which the light has fallen to half;
// color.a wraps the light a little round surfaces turned away from it.
vec3 light(vec3 p, vec3 n, vec4 pos, vec4 color) {
    vec3 d = pos.xyz - p;
    float dist2 = max(dot(d, d), 1.0e-4);
    vec3 l = d * inversesqrt(dist2);
    float facing = clamp((dot(n, l) + color.a) / (1.0 + color.a), 0.0, 1.0);
    return color.rgb * facing / (1.0 + dist2 / (pos.w * pos.w));
}

void main() {
    vec2 px = 1.0 / ScreenSize;
    float depth = texture(Sampler1, texCoord).r;
    if (depth >= 1.0) {
        discard;
    }
    vec3 p = position(texCoord);
    // Face normal from the neighbours on the nearer side, so block edges do not smear.
    vec3 r = position(texCoord + vec2(px.x, 0.0)) - p;
    vec3 l = p - position(texCoord - vec2(px.x, 0.0));
    vec3 u = position(texCoord + vec2(0.0, px.y)) - p;
    vec3 b = p - position(texCoord - vec2(0.0, px.y));
    vec3 dx = dot(r, r) < dot(l, l) ? r : l;
    vec3 dy = dot(u, u) < dot(b, b) ? u : b;
    vec3 n = cross(dx, dy);
    float len = length(n);
    n = len > 1.0e-10 ? n / len : -normalize(p);
    if (dot(n, p) > 0.0) {
        n = -n;
    }
    vec3 e = light(p, n, Light0Pos, Light0Color) + light(p, n, Light1Pos, Light1Color)
        + light(p, n, Light2Pos, Light2Color) + light(p, n, Light3Pos, Light3Color);
    // Far ground is mostly fog by now, and the fog swallows the light too.
    e *= 1.0 - smoothstep(Fog.x, Fog.y, length(p));
    vec3 albedo = min(texture(Sampler0, texCoord).rgb / Ambient, vec3(1.0));
    fragColor = vec4(albedo * e, 0.0);
}
