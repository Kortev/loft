#version 150

// Jupiter: the Cassini cylindrical map with slow differential band drift and limb darkening.

uniform sampler2D Sampler0;
uniform vec3 LightDir;
uniform float Time;
uniform float Exposure;
uniform vec3 SunObj;
uniform float RingRadius;
uniform float RingWidth;
uniform vec3 MoonPos;
uniform float MoonRadius;
uniform float Detail;

in vec3 viewPos;
in vec3 viewNormal;
in vec2 texCoord;
in vec3 objPos;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    return noise(p) * 0.5 + noise(p * 2.07 + 3.1) * 0.28 + noise(p * 4.3 + 7.7) * 0.14 + noise(p * 8.9 + 1.3) * 0.08;
}

void main() {
    vec3 n = normalize(viewNormal);
    vec3 v = normalize(-viewPos);
    vec3 l = normalize(LightDir);
    float lat = (0.5 - texCoord.y) * 3.14159265;
    float drift = Time * (0.00045 + 0.00035 * sin(lat * 11.0));
    vec2 uv = vec2(texCoord.x + drift, texCoord.y);
    float streaks = 0.5;
    if (Detail > 0.0) {
        // Close over the cloud tops the map runs out of resolution: stir in eddies, stretched along the bands.
        vec2 q = texCoord * vec2(1400.0, 700.0);
        vec2 warp = vec2(fbm(q * vec2(0.18, 0.6)), fbm(q * vec2(0.18, 0.6) + 5.2)) - 0.5;
        uv += warp * vec2(0.004, 0.0015) * Detail;
        streaks = fbm(q * vec2(0.08, 1.1) + warp * 3.0);
    }
    vec3 albedo = pow(texture(Sampler0, uv).rgb, vec3(2.2));
    albedo *= 1.0 + (streaks - 0.5) * 0.45 * Detail;
    // The map's polar caps are flat grey; fade them into a hazy blue-grey.
    float pole = smoothstep(0.8, 0.97, abs(texCoord.y - 0.5) * 2.0);
    albedo = mix(albedo, vec3(0.2, 0.21, 0.24), pole);

    float ndl = dot(n, l);
    float lit = clamp(ndl, 0.0, 1.0);
    float limb = pow(max(dot(n, v), 0.0), 0.3);
    // Shadows cast across the cloud tops (in the planet's own frame, radius 1): the accelerator ring's thin line
    // and a moon's round black spot.
    vec3 sun = normalize(SunObj + vec3(0.0, 1.0e-5, 0.0));
    float shade = 1.0;
    if (RingRadius > 0.0 && abs(sun.y) > 1.0e-3) {
        float t = -objPos.y / sun.y;
        if (t > 0.0) {
            vec3 hit = objPos + sun * t;
            float off = abs(length(hit.xz) - RingRadius);
            shade *= 1.0 - 0.85 * (1.0 - smoothstep(RingWidth * 0.5, RingWidth, off));
        }
    }
    if (MoonRadius > 0.0) {
        float t = dot(MoonPos - objPos, sun);
        if (t > 0.0) {
            float d = length(objPos + sun * t - MoonPos);
            shade *= mix(0.04, 1.0, smoothstep(MoonRadius * 0.82, MoonRadius * 1.08, d));
        }
    }
    vec3 color = albedo * (lit * 1.45 * shade + 0.004) * limb;
    color *= smoothstep(-0.06, 0.18, ndl) * 0.92 + 0.08 * lit;
    float fres = pow(1.0 - clamp(dot(n, v), 0.0, 1.0), 2.5);
    color += vec3(0.9, 0.72, 0.5) * fres * 0.1 * smoothstep(-0.2, 0.4, ndl);
    color *= Exposure;
    fragColor = vec4(pow(max(color, 0.0), vec3(1.0 / 2.2)), 1.0);
}
