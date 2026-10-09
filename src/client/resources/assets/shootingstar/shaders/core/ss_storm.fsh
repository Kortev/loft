#version 150

// The feed's Earth for Mjölnir: the planet's day, night and cloud maps (Sampler0-2), with the detail they lack worked
// in from NoiseTex (Sampler3) at whatever scale the camera is near enough to see, so it stays sharp from orbit all the
// way down into the storm: the clouds billow down to tens of metres and stand out in relief, the cities' glow gathers
// into towns and streets with roads of light between them. Lit by the sun through the air (yellowing and reddening
// towards the terminator, a twilight beyond it, blue haze towards the limb) and on the night side by the moon. Over it,
// every thunderstorm on Earth flickering inside its clouds, dark once its charge has been relayed on to the target (the
// relay has passed it at Front, by Drain); and over the target a storm wound into a vortex, lightning all through it.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform vec3 LightDir;
uniform vec3 SunObj;
uniform float CloudShift;
uniform float Exposure;
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
// The planet's radius in kilometres (the sphere's is 1).
const float KM = 6371.0;
// Storm cells across the map: about a degree and a half each.
const vec2 CELLS = vec2(256.0, 128.0);
// Turns each finer layer of detail against the last, so the noise texture's tiles never line up.
const mat2 TURN = mat2(0.8, 0.6, -0.6, 0.8);
// The mean of NoiseTex's red channel, so detail made from it averages out to nothing from far off.
const float MEAN = 0.464;
// The same for its alpha channel, the finest noise (32 cells to a tile, so its tiles repeat least).
const float FINE = 0.516;
// How high the cloud tops' relief stands, in kilometres for a unit of cloud: more than it is, for the light to model.
const float RELIEF = 6.0;
// The maps' sizes, for sampling them smoothly close in.
const vec2 MAP = vec2(2048.0, 1024.0);
// The mean of the towns' pattern below, which it is divided by so it averages to 1.
const float TOWNS = 0.40;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
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
            float thick = textureLod(Sampler2, (cell + 0.5) / CELLS + vec2(CloudShift, 0.0), 0.0).r;
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
            light += b * (exp(-r2 / 0.3) + 0.5 * exp(-r2 / 0.03));
        }
    }
    return light * drained;
}

// Lightning all through the vortex, a cell of it at a time; f is a frame that turns with the storm (unwound, so a
// flash lights a round patch of cloud and not an arc along an arm).
float vortexLightning(vec2 f) {
    vec2 g = f * 6.0;
    vec2 c = floor(g);
    float light = 0.0;
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            vec2 cell = c + vec2(float(i), float(j));
            float h2 = hash(cell + 8.1);
            float phase = fract(Time * (0.07 + 0.1 * h2) + hash(cell + 13.7));
            if (phase > 0.12) {
                continue;
            }
            vec2 at = cell + 0.5 + (vec2(hash(cell + 5.7), hash(cell + 9.1)) - 0.5) * 0.8;
            vec2 d = g - at;
            float r2 = dot(d, d);
            light += flicker(phase / 0.12) * (0.4 + 0.6 * hash(cell + 3.3)) * (exp(-r2 / 0.3) + 0.8 * exp(-r2 / 0.03));
        }
    }
    return light;
}

// Weights for a smooth (cubic B-spline) lookup between texels.
vec4 cubic(float x) {
    vec4 n = vec4(1.0, 2.0, 3.0, 4.0) - x;
    vec4 s = n * n * n;
    float a = s.x;
    float b = s.y - 4.0 * s.x;
    float c = s.z - 4.0 * s.y + 6.0 * s.x;
    return vec4(a, b, c, 6.0 - a - b - c) / 6.0;
}

// A map looked up smoothly between its texels (four bilinear taps), so that close in, where each texel covers many
// pixels, it does not show the squares and diamonds a bilinear lookup draws.
vec4 smoothMap(sampler2D map, vec2 uv) {
    vec2 t = uv * MAP - 0.5;
    vec2 f = fract(t);
    t -= f;
    vec4 xc = cubic(f.x);
    vec4 yc = cubic(f.y);
    vec4 c = t.xxyy + vec2(-0.5, 1.5).xyxy;
    vec4 w = vec4(xc.xz + xc.yw, yc.xz + yc.yw);
    vec4 o = (c + vec4(xc.yw, yc.yw) / w) / MAP.xxyy;
    float sx = w.x / (w.x + w.y);
    float sy = w.z / (w.z + w.w);
    return mix(mix(texture(map, o.yw), texture(map, o.xw), sx), mix(texture(map, o.yz), texture(map, o.xz), sx), sy);
}

// Point lights in cells size kilometres across, one to a cell in about half of them, each a pixel or so wide (px
// kilometres to a pixel). Divided by their mean, so a field of them carries the same light as the glow they break up.
float points(vec2 km, float size, float px) {
    vec2 g = km / size;
    vec2 c = floor(g);
    float h = hash(c + size);
    vec2 at = vec2(hash(c + 1.7 + size), hash(c + 4.3 - size)) * 0.8 + 0.1;
    vec2 d = (fract(g) - at) * size / px;
    return step(0.45, h) * (0.5 + h) * exp(-dot(d, d) * 1.2) * size * size / (px * px) / 1.75;
}

// Point lights about four pixels apart at whatever distance: the two nearest sizes of cell, blended.
float sparkle(vec2 km, float px) {
    float level = log2(px * 4.0);
    float l0 = floor(level);
    return mix(points(km, exp2(l0), px), points(km, exp2(l0 + 1.0), px), level - l0);
}

// The clouds' detail round a point (kilometres from the target), about 0; far off, the noise texture's mipmaps average
// it away. Its large part: heaped towers tens of kilometres across down to a kilometre or so.
float heaps(vec2 km) {
    return (texture(Sampler3, km / 160.0).r - MEAN) * 0.62;
}

// The fine part: puffs a kilometre across down to tens of metres.
float puffs(vec2 km) {
    return (texture(Sampler3, TURN * km / 22.0 + 0.5).a - FINE) * 0.34;
}

// All of it.
float billows(vec2 km) {
    return heaps(km) + puffs(km);
}

// Sunlight where the sun stands mu (the cosine of its angle from overhead) high, through the air: white with the sun
// high, yellowing and then reddening as it sets, gone soon after.
vec3 sunlight(float mu) {
    vec3 air = vec3(0.035, 0.09, 0.22) / (max(mu, 0.0) + 0.04);
    return exp(-air * 0.5) * smoothstep(-0.03, 0.05, mu);
}

// The sky's own light past the terminator: the upper air still sunlit overhead, orange at first and then deep blue,
// gone eighteen degrees on.
vec3 twilight(float mu) {
    float t = smoothstep(-0.3, 0.02, mu);
    vec3 tint = mix(vec3(0.04, 0.045, 0.11), vec3(0.4, 0.2, 0.09), smoothstep(-0.12, 0.03, mu));
    return tint * t * t * 0.14 * (1.0 - smoothstep(0.02, 0.3, mu));
}

void main() {
    vec3 n = normalize(viewNormal);
    vec3 v = normalize(-viewPos);
    vec3 l = normalize(LightDir);
    vec3 p = normalize(objPos);
    float mu = dot(p, SunObj);

    // Where this point is from the target: how far (radians), which way round, and in kilometres on a map of the planet
    // centred on it (stereographic, so the detail is the same shape everywhere); and how much ground a pixel covers.
    float ct = clamp(dot(p, Target), -1.0, 1.0);
    float ang = acos(ct);
    vec3 side = cross(Target, vec3(0.0, 1.0, 0.0));
    side = dot(side, side) < 1.0e-6 ? vec3(1.0, 0.0, 0.0) : normalize(side);
    vec3 other = cross(Target, side);
    float phi = atan(dot(p, other), dot(p, side));
    vec2 km = 2.0 * KM * vec2(dot(p, side), dot(p, other)) / max(1.0 + ct, 0.05);
    float px = max(length(dFdx(km)), length(dFdy(km)));
    float coslat = max(cos((texCoord.y - 0.5) * PI), 0.05);

    // The maps' twenty-kilometre texels broken into the shapes of clouds and coasts by a slow warp.
    vec2 warp = (texture(Sampler3, km / 420.0 + 0.31).gb - 0.5) * 28.0;
    vec2 uv = texCoord + vec2(warp.x / (2.0 * PI * KM * coslat), -warp.y / (PI * KM));
    vec2 cloudUv = uv + vec2(CloudShift, 0.0);

    // Clouds: the map's, with billows worked in, most at their thinning edges.
    float c0 = smoothMap(Sampler2, cloudUv).r;
    float b = billows(km);
    float edge = smoothstep(0.02, 0.3, c0) * (1.0 - 0.5 * smoothstep(0.6, 0.95, c0));
    float cloud = clamp(c0 + b * 0.9 * edge, 0.0, 1.0);
    float tops = c0 + b;

    // The storm over the target, wound into a vortex round an eye, lightning in it that never stops.
    float lightning = 0.0;
    float vortexCloud = 0.0;
    // (Its noise is looked up whether or not this point is in it, outside the test: a texture's mipmap level needs
    // the pixels round it to have looked it up too.)
    if (Vortex > 0.0) {
        float rr = ang / Vortex;
        float wind = phi - Spin + 2.3 * log(rr + 0.03);
        vec2 q = vec2(cos(wind), sin(wind)) * rr;
        float vn = texture(Sampler3, q * 0.6 + 0.2).r * 0.7 + texture(Sampler3, TURN * q * 5.0 + 0.7).r * 0.3;
        float inside = 1.0 - step(Vortex * 1.4, ang);
        // Bands winding out of it, and in the middle one overcast, its texture the billows of the towers.
        float arms = mix(0.75, 0.5 + 0.5 * cos(2.0 * wind + vn * 5.0), smoothstep(0.4, 0.9, rr));
        float vc = smoothstep(0.3, 0.62, vn * 0.8 + arms * 0.4 + (1.0 - rr) * 0.4 + b * 0.8);
        vc *= (1.0 - smoothstep(0.75, 1.15, rr + (vn - 0.5) * 0.3)) * smoothstep(0.035, 0.11, rr) * inside;
        cloud = max(cloud, vc);
        vortexCloud = vc;
        // The towers round the eye stand highest.
        float wall = exp(-pow((rr - 0.22) / 0.16, 2.0));
        tops = max(tops, vc * (0.9 + 0.7 * wall) + b);
        vec2 turning = vec2(cos(phi - Spin), sin(phi - Spin)) * rr;
        lightning = inside > 0.0 ? vortexLightning(turning) * (0.25 + 1.0 * wall) * (0.2 + vc) : 0.0;
    }

    // The tops' slope towards the sun, from their height a step that way: a low sun lights the faces turned to it and
    // leaves the far sides in shadow. Close in, the step is short enough to catch the finest billows.
    vec3 sunAcross = SunObj - p * mu;
    vec2 toSun = vec2(dot(sunAcross, side), dot(sunAcross, other));
    toSun = dot(toSun, toSun) > 1.0e-8 ? normalize(toSun) : vec2(1.0, 0.0);
    float coarse = clamp(px * 1.5, 1.5, 8.0);
    float fine = clamp(px * 1.5, 0.12, 6.0);
    float big = heaps(km);
    float small = b - big;
    float slope = ((heaps(km + toSun * coarse) - big) / coarse * RELIEF + (puffs(km + toSun * fine) - small) / fine * 0.4)
        * smoothstep(0.05, 0.4, cloud);
    // The moon, high over the night side round the target, lights the clouds there in the same way.
    vec3 moon = normalize(Target * 0.75 + other * 0.5 - side * 0.45);
    float muMoon = dot(p, moon);
    vec3 moonAcross = moon - p * muMoon;
    vec2 toMoon = normalize(vec2(dot(moonAcross, side), dot(moonAcross, other)) + 1.0e-6);
    float moonSlope = ((heaps(km + toMoon * coarse) - big) / coarse * RELIEF + (puffs(km + toMoon * fine) - small) / fine * 0.4)
        * smoothstep(0.05, 0.4, cloud);
    vec3 moonlight = vec3(0.55, 0.62, 0.85) * 0.03;

    // The ground: its colour in sunlight, the twilight and the moon; the sun's glint off water.
    vec3 albedo = pow(texture(Sampler0, uv).rgb, vec3(2.2));
    float water = 1.0 - smoothstep(0.012, 0.03, albedo.g + albedo.r * 0.5);
    vec3 surface = albedo * (sunlight(mu) * max(mu, 0.0) * 1.35 + twilight(mu) + moonlight * 0.5 * max(muMoon, 0.0));
    vec3 h = normalize(l + v);
    surface += sunlight(mu) * pow(max(dot(n, h), 0.0), 600.0) * 0.8 * water * smoothstep(0.1, 0.45, dot(n, v));

    // The clouds stand a little higher than the ground and see the sun a little longer.
    float muC = mu + 0.03;
    float lift = clamp(muC + slope * sqrt(max(1.0 - muC * muC, 0.0)), 0.0, 1.0);
    float liftMoon = clamp(muMoon + moonSlope, 0.0, 1.0);
    // The storm over the target towers over the rest and catches more of the moon.
    vec3 cloudColor = sunlight(muC) * lift * 1.15 + twilight(muC) * 1.6 + moonlight * (0.1 + liftMoon) * (1.0 + 0.8 * vortexCloud);
    vec3 color = mix(surface, cloudColor, cloud * 0.95);

    // City lights: the night map less the moonlit land it was made over (blue, dim). Close in, the glow of a city
    // gathers into towns, the towns into villages and lit blocks, the blocks into streets, with roads of light between
    // them; each kept to the same light in all, so nothing brightens or dims as it comes into view.
    vec3 lights = max(pow(smoothMap(Sampler1, uv).rgb, vec3(2.2)) - vec3(0.012, 0.011, 0.04), 0.0);
    vec3 region = max(pow(textureLod(Sampler1, uv, 4.0).rgb, vec3(2.2)) - vec3(0.012, 0.011, 0.04), 0.0);
    float wTown = 1.0 - smoothstep(0.8, 3.0, px);
    float wSpark = 1.0 - smoothstep(0.6, 2.5, px);
    float towns = pow(smoothstep(0.3, 0.75, texture(Sampler3, km / 90.0 + 0.13).g * 0.6
        + texture(Sampler3, TURN * km / 37.0 + 0.29).r * 0.4 + 0.02), 1.6) / TOWNS;
    float roads = smoothstep(0.72, 0.78, texture(Sampler3, km / 260.0 + 0.4).b);
    // In the towns the light is points; between them it runs along the roads.
    lights = lights * mix(1.0, towns, wTown) * mix(1.0, 0.25 + 0.75 * sparkle(km, px), wSpark)
        + region * roads * 1.5 * wTown * mix(1.0, 0.4 + 0.6 * sparkle(km + 7.0, px), wSpark);
    float dark = 1.0 - smoothstep(-0.1, 0.06, mu);
    // Bright in the middle of a city, sodium orange at its edges; and the haze over it glowing with its light.
    vec3 sodium = mix(vec3(1.0, 0.62, 0.3), vec3(1.0, 0.86, 0.68), smoothstep(0.05, 0.5, lights.g));
    color += (lights * sodium * 2.4 + region * vec3(1.0, 0.7, 0.4) * 0.35) * dark * (1.0 - cloud * 0.85);

    // The storms: dark once the relay has passed them and their charge has gone on, flashing harder before it comes.
    // Their lightning lights the clouds from inside, brightest in the thick of the towers.
    float outside = smoothstep(Front - 0.02, Front + 0.02, ang);
    float drained = mix(1.0, mix(1.0, 0.06, Drain), outside);
    float boosted = (1.0 - outside) * Drain;
    float flash = Storms * storms(texCoord + vec2(CloudShift, 0.0), coslat, drained, boosted) * (1.0 + 1.5 * boosted);
    flash += lightning * Storms;
    float within = (0.2 + 1.2 * smoothstep(0.3, 1.0, tops)) * (0.35 + 1.65 * smoothstep(-0.25, 0.2, heaps(km) * 1.4));
    color += vec3(0.68, 0.76, 1.0) * flash * within * (0.3 + cloud * 0.9);

    // What has gathered at the target.
    color += vec3(0.75, 0.85, 1.0) * Charge * (exp(-ang * ang / 1.6e-5) * 3.0 + exp(-ang * ang / 2.0e-4) * 0.4);

    // The air between the camera and the ground, more of it the more slantwise it is seen: blue haze in daylight
    // towards the limb, warm along the terminator, nothing at night.
    float facing = clamp(dot(n, v), 0.0, 1.0);
    float air = 1.0 - exp(-0.05 / max(facing, 0.03));
    vec3 sky = vec3(0.2, 0.38, 0.9) * sunlight(mu + 0.06) * smoothstep(-0.12, 0.3, mu) + twilight(mu) * 2.0;
    color = color * (1.0 - air * 0.55) + sky * air;

    color *= Exposure;
    fragColor = vec4(pow(max(color, 0.0), vec3(1.0 / 2.2)), 1.0);
}
