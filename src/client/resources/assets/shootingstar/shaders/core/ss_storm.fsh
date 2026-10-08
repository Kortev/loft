#version 150

// The feed's Earth for Mjölnir: the planet's day, night and cloud maps as ss_planet draws them, and over them every
// thunderstorm on Earth flickering inside its clouds; the charge of the global circuit drawn in across the planet to
// the target, a ring of light closing on it with filaments of current running in ahead of it, the storms it has passed
// gone dark and the ones inside it flashing harder; and over the target, a storm wound into a vortex with lightning
// round its eye.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec3 LightDir;
uniform float CloudShift;
uniform float Exposure;
uniform float Detail;
uniform float Time;
uniform vec3 Target;
uniform float Storms;
uniform float Front;
uniform float Drain;
uniform float Vortex;
uniform float Spin;
uniform float Charge;

in vec3 viewPos;
in vec3 viewNormal;
in vec2 texCoord;
in vec3 objPos;

out vec4 fragColor;

const float PI = 3.14159265;
// Storm cells across the map: about a degree and a half each.
const vec2 CELLS = vec2(256.0, 128.0);

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
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        amp *= 0.5;
    }
    return sum;
}

// One flash of a storm, 0..1 through it: a stroke, a dip, a restrike, and the glow dying away.
float flicker(float k) {
    return k < 0.25 ? 1.0 : k < 0.4 ? 0.3 : k < 0.6 ? 0.85 : (1.0 - k) * 1.6;
}

// The lightning reaching this point from the storms in the nearby cells.
float storms(vec2 uv, float coslat, float drained, float boosted) {
    vec2 g = uv * CELLS;
    vec2 c = floor(g);
    float light = 0.0;
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            vec2 cell = c + vec2(float(i), float(j));
            cell.x = mod(cell.x, CELLS.x);
            float h1 = hash(cell);
            float thick = texture(Sampler2, (cell + 0.5) / CELLS + vec2(CloudShift, 0.0)).r;
            if (h1 > 0.5 || thick < 0.42) {
                continue;
            }
            float h2 = hash(cell + 17.3);
            float h3 = hash(cell + 41.1);
            float rate = 0.025 + 0.06 * h2 * (0.5 + boosted);
            float phase = fract(Time * rate + h3);
            if (phase > 0.14) {
                continue;
            }
            vec2 at = cell + 0.5 + (vec2(hash(cell + 5.7), hash(cell + 9.1)) - 0.5) * 0.7;
            vec2 d = g - at;
            d.x = d.x - CELLS.x * floor(d.x / CELLS.x + 0.5);
            d.x *= coslat;
            float r2 = dot(d, d);
            float b = flicker(phase / 0.14) * (0.6 + 0.8 * h2) * (thick - 0.3);
            light += b * (exp(-r2 / 0.45) + 0.6 * exp(-r2 / 0.04));
        }
    }
    return light * drained;
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
    if (Detail > 0.0) {
        float fine = noise(texCoord * 2400.0) * 0.5 + noise(texCoord * 5200.0) * 0.5;
        cloud = clamp(cloud + (fine - 0.5) * 0.35 * Detail * smoothstep(0.05, 0.4, cloud), 0.0, 1.0);
        day *= 1.0 + (noise(texCoord * 3600.0) - 0.5) * 0.4 * Detail;
    }

    // Where this point is from the target: how far (radians) and which way round.
    vec3 p = normalize(objPos);
    float ang = acos(clamp(dot(p, Target), -1.0, 1.0));
    vec3 side = cross(Target, vec3(0.0, 1.0, 0.0));
    side = dot(side, side) < 1.0e-6 ? vec3(1.0, 0.0, 0.0) : normalize(side);
    vec3 other = cross(Target, side);
    float phi = atan(dot(p, other), dot(p, side));

    // The vortex over the target: log-spiral arms of cloud round an eye, lit from inside by lightning that never stops.
    float eyewall = 0.0;
    float vortexCloud = 0.0;
    if (Vortex > 0.0 && ang < Vortex * 1.4) {
        float rr = ang / Vortex;
        float wind = phi - Spin + 3.0 * log(rr + 0.03);
        vec2 q = vec2(cos(wind), sin(wind)) * rr;
        float vn = fbm(q * 4.0 + vec2(3.7, 1.3));
        float arms = 0.5 + 0.5 * cos(2.0 * wind + vn * 2.5);
        float vc = smoothstep(0.32, 0.62, vn * 0.95 + arms * 0.35 + (1.0 - rr) * 0.32);
        vc *= (1.0 - smoothstep(0.75, 1.15, rr + (vn - 0.5) * 0.3)) * smoothstep(0.035, 0.11, rr);
        cloud = max(cloud, vc);
        vortexCloud = vc;
        // Lightning crawling round the eye wall, and flickering all through the arms.
        float band = exp(-pow((rr - 0.2) / 0.1, 2.0));
        eyewall = band * pow(noise(vec2(phi * 5.0 + Spin * 3.0, Time * 0.9)), 3.0) * 3.0;
        float cells = noise(q * 7.0 + vec2(floor(Time * 1.3) * 7.1, 0.0));
        eyewall += vc * smoothstep(0.62, 0.9, cells) * 1.6;
    }

    float lit = clamp(ndl, 0.0, 1.0);
    float twilight = smoothstep(-0.12, 0.14, ndl);
    float water = 1.0 - smoothstep(0.012, 0.03, day.g + day.r * 0.5);
    vec3 h = normalize(l + v);
    float glint = pow(max(dot(n, h), 0.0), 90.0) * water * twilight;

    vec3 surface = day * (lit * 1.35 + 0.012);
    surface += vec3(1.0, 0.92, 0.8) * glint * 0.8;
    // A little moonlight on the night side's clouds, so the storms have somewhere to be.
    vec3 cloudColor = vec3(1.0) * (lit * 1.15 + 0.004) + vec3(0.004, 0.005, 0.009) * (1.0 - twilight);
    // The storm over the target towers higher than any other, and catches what light there is.
    cloudColor += vec3(0.03, 0.035, 0.05) * vortexCloud * (1.0 - twilight);
    float dusk = (1.0 - smoothstep(0.0, 0.35, ndl)) * twilight;
    cloudColor = mix(cloudColor, cloudColor * vec3(1.6, 0.8, 0.45), dusk);
    vec3 color = mix(surface, cloudColor, cloud * 0.93);
    color += night * vec3(1.0, 0.78, 0.5) * 2.4 * (1.0 - twilight) * (1.0 - cloud * 0.85);

    // The storms: dark once the ring has passed them and their charge is gone, flashing harder inside it.
    float outside = smoothstep(Front - 0.02, Front + 0.02, ang);
    float drained = mix(1.0, mix(1.0, 0.06, Drain), outside);
    float boosted = (1.0 - outside) * Drain;
    float coslat = max(cos((texCoord.y - 0.5) * PI), 0.05);
    float flash = Storms * storms(texCoord + vec2(CloudShift, 0.0), coslat, drained, boosted) * (1.0 + 1.5 * boosted);
    flash += eyewall * Storms;
    color += vec3(0.68, 0.76, 1.0) * flash * (0.3 + cloud * 0.9);

    // The ring closing on the target, its edge a tangle of current.
    float ringOn = smoothstep(3.1, 2.8, Front) * smoothstep(0.0, 0.04, Front);
    if (ringOn > 0.0) {
        float width = 0.02 + 0.03 * Front;
        float tangle = fbm(vec2(phi * 18.0, ang * 30.0 - Time * 0.15));
        float ring = exp(-pow((ang - Front - (tangle - 0.5) * width * 1.6) / width, 2.0));
        color += vec3(0.5, 0.72, 1.0) * ring * (0.4 + 1.6 * tangle * tangle) * 1.6 * ringOn;
        // Filaments running in ahead of it to the target, pulsing inward.
        if (ang < Front) {
            float wiggle = (noise(vec2(ang * 22.0, phi * 2.0)) - 0.5) * 2.4 + (noise(vec2(ang * 90.0, phi * 4.0)) - 0.5) * 0.9;
            float spokes = pow(max(0.0, sin(phi * 9.0 + wiggle)), 40.0);
            float pulse = smoothstep(0.55, 1.0, fract(ang * 18.0 + Time * 0.45));
            float fade = smoothstep(0.0, 0.3, ang / max(Front, 1.0e-3));
            color += vec3(0.62, 0.8, 1.0) * spokes * (0.25 + 1.2 * pulse) * fade * ringOn * 0.9;
        }
    }

    // What has gathered at the target.
    color += vec3(0.75, 0.85, 1.0) * Charge * (exp(-ang * ang / 1.6e-5) * 6.0 + exp(-ang * ang / 2.0e-4) * 0.6);

    float fres = pow(1.0 - clamp(dot(n, v), 0.0, 1.0), 3.0);
    color += vec3(0.22, 0.48, 1.0) * fres * (twilight * 0.85 + 0.006);
    color *= Exposure;
    fragColor = vec4(pow(max(color, 0.0), vec3(1.0 / 2.2)), 1.0);
}
