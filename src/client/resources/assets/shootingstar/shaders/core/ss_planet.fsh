#version 150

// Earth: Blue Marble day map, Black Marble city lights and a moving cloud layer, lit in linear space.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec3 LightDir;
uniform float CloudShift;
uniform float Exposure;
uniform float Detail;

in vec3 viewPos;
in vec3 viewNormal;
in vec2 texCoord;

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

void main() {
    vec3 n = normalize(viewNormal);
    vec3 v = normalize(-viewPos);
    vec3 l = normalize(LightDir);
    float ndl = dot(n, l);

    vec3 day = pow(texture(Sampler0, texCoord).rgb, vec3(2.2));
    vec3 night = pow(texture(Sampler1, texCoord).rgb, vec3(2.2));
    vec2 cloudUv = texCoord + vec2(CloudShift, 0.0);
    float cloud = texture(Sampler2, cloudUv).r;
    float shadow = texture(Sampler2, cloudUv + vec2(0.0025, 0.0015)).r;
    if (Detail > 0.0) {
        // Close to the surface the maps run out of resolution: add fine cloud texture and relief.
        float fine = noise(texCoord * 2400.0) * 0.5 + noise(texCoord * 5200.0) * 0.5;
        cloud = clamp(cloud + (fine - 0.5) * 0.35 * Detail * smoothstep(0.05, 0.4, cloud), 0.0, 1.0);
        day *= 1.0 + (noise(texCoord * 3600.0) - 0.5) * 0.4 * Detail;
    }

    float lit = clamp(ndl, 0.0, 1.0);
    float twilight = smoothstep(-0.12, 0.14, ndl);
    float water = 1.0 - smoothstep(0.012, 0.03, day.g + day.r * 0.5);
    vec3 h = normalize(l + v);
    float glint = pow(max(dot(n, h), 0.0), 90.0) * water * twilight;

    vec3 surface = day * (lit * 1.35 + 0.012) * (1.0 - shadow * 0.5 * lit);
    surface += vec3(1.0, 0.92, 0.8) * glint * 0.8;
    vec3 cloudColor = vec3(1.0) * (lit * 1.15 + 0.015);
    float dusk = (1.0 - smoothstep(0.0, 0.35, ndl)) * twilight;
    cloudColor = mix(cloudColor, cloudColor * vec3(1.6, 0.8, 0.45), dusk);
    vec3 color = mix(surface, cloudColor, cloud * 0.93);
    color += night * vec3(1.0, 0.78, 0.5) * 2.4 * (1.0 - twilight) * (1.0 - cloud * 0.85);

    float fres = pow(1.0 - clamp(dot(n, v), 0.0, 1.0), 3.0);
    color += vec3(0.22, 0.48, 1.0) * fres * (twilight * 0.85 + 0.03);
    color *= Exposure;
    fragColor = vec4(pow(max(color, 0.0), vec3(1.0 / 2.2)), 1.0);
}
