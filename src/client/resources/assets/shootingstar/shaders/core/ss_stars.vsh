#version 150

// Each star is a screen-aligned quad around its direction; size and brightness follow its magnitude.
// At relativistic speed the stars crowd forward and brighten (aberration and beaming).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 ScreenSize;
uniform float Brightness;
uniform float Beta;
uniform vec3 Forward;
uniform float Time;
uniform vec3 Streak;

out vec4 vertexColor;
out vec2 corner;
out float streak;

void main() {
    vec3 d = normalize(Position);
    float boost = 1.0;
    vec3 tint = vec3(1.0);
    if (Beta > 0.0001) {
        float c = dot(d, Forward);
        float cp = (c + Beta) / (1.0 + Beta * c);
        vec3 perp = d - Forward * c;
        float pl = length(perp);
        vec3 pd = pl > 1.0e-6 ? perp / pl : vec3(0.0);
        d = Forward * cp + pd * sqrt(max(0.0, 1.0 - cp * cp));
        float gamma = 1.0 / sqrt(1.0 - Beta * Beta);
        float doppler = 1.0 / (gamma * (1.0 - Beta * cp));
        boost = clamp(doppler * doppler * doppler, 0.0, 40.0);
        tint = doppler > 1.0
            ? mix(vec3(1.0), vec3(0.55, 0.72, 1.0), clamp((doppler - 1.0) * 0.5, 0.0, 1.0))
            : mix(vec3(1.0), vec3(1.0, 0.42, 0.22), clamp((1.0 - doppler) * 1.6, 0.0, 1.0));
    }
    vec4 clip = ProjMat * ModelViewMat * vec4(d * 100.0, 1.0);
    float mag = Color.a * 255.0 / 25.0;
    float b = pow(10.0, -0.4 * mag) * Brightness * boost;
    float twinkle = 0.88 + 0.12 * sin(Time * 2.3 + Position.x * 911.0 + Position.y * 377.0);
    float size = clamp(0.9 + 2.8 * sqrt(b), 0.9, 7.0);
    vec2 px = 2.0 / ScreenSize;
    vec2 offset = UV0 * size * px;
    streak = 0.0;
    if (Streak.x > 0.0 && clip.w > 0.0) {
        // Stretch away from the vanishing point for the warp.
        vec2 ndc = clip.xy / clip.w;
        vec2 radial = ndc - Streak.yz;
        float rl = length(radial);
        vec2 dir = rl > 1.0e-4 ? radial / rl : vec2(1.0, 0.0);
        vec2 side = vec2(-dir.y, dir.x);
        float len = Streak.x * rl;
        offset = dir * (UV0.x < 0.0 ? UV0.x * size * px.x : UV0.x * (size * px.x + len))
            + side * UV0.y * size * px.y;
        streak = len / (len + size * px.x);
    }
    gl_Position = clip + vec4(offset * clip.w, 0.0, 0.0);
    vertexColor = vec4(Color.rgb * tint, min(b * 3.0, 1.6) * twinkle);
    corner = UV0;
}
