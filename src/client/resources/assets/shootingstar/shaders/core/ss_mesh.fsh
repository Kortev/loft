#version 150

// Hard-surface and rock shading for the feed's meshes. Vertex colour carries baked occlusion (r), a glow mask (g)
// and a material id (b); see tools/models.py. Light: the sun (GGX specular with Schlick Fresnel), a fill light
// for what a nearby planet bounces back, the ambient sky, a rim, and a cheap reflection of the surroundings.

uniform vec3 LightDir;
uniform vec3 LightColor;
uniform vec3 AmbientColor;
uniform vec3 RimColor;
uniform vec3 GlowColor;
uniform float GlowStrength;
uniform float Heat;
uniform float Fade;
uniform vec3 FillDir;
uniform vec3 FillColor;
uniform vec3 PointPos;
uniform vec3 PointColor;
// Bifröst's emitters: how far round the frame they have lit (0..1 from the middle of the bottom beam both ways to the
// middle of the top one; above 1 all of them) and the phase of the light running round them once they are lit.
uniform float Sweep;
uniform float Phase;

in vec3 viewPos;
in vec3 viewNormal;
in vec3 objPos;
in vec3 objNormal;
in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

const float PI = 3.14159265;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(hash(i + vec3(0.0, 1.0, 0.0)), hash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(hash(i + vec3(0.0, 0.0, 1.0)), hash(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(hash(i + vec3(0.0, 1.0, 1.0)), hash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float segment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

// Runes cut round the spear's socket: every cell round the band holds a stave and a few branches.
float runes(vec3 p) {
    float around = atan(p.y, p.x) / (2.0 * PI) + 0.5;
    float cells = 18.0;
    float id = floor(around * cells);
    vec2 uv = vec2(fract(around * cells), (p.z - 2.13) / 0.29);
    uv = (uv - 0.5) * vec2(1.3, 1.25) + 0.5;
    float h1 = hash(vec3(id, 1.7, 3.1));
    float h2 = hash(vec3(id, 5.3, 0.7));
    float h3 = hash(vec3(id, 9.1, 4.4));
    float d = segment(uv, vec2(0.5, 0.08), vec2(0.5, 0.92));
    if (h1 > 0.25) {
        d = min(d, segment(uv, vec2(0.5, mix(0.25, 0.65, h2)), vec2(h1 > 0.62 ? 0.86 : 0.14, mix(0.5, 0.9, h3))));
    }
    if (h2 > 0.4) {
        d = min(d, segment(uv, vec2(0.5, mix(0.35, 0.75, h3)), vec2(h2 > 0.7 ? 0.14 : 0.86, mix(0.1, 0.45, h1))));
    }
    if (h3 > 0.72) {
        d = min(d, segment(uv, vec2(0.5, 0.5), vec2(h3 > 0.86 ? 0.86 : 0.14, 0.5)));
    }
    return 1.0 - smoothstep(0.04, 0.08, d);
}

float ggx(float ndh, float a) {
    float a2 = a * a;
    float d = ndh * ndh * (a2 - 1.0) + 1.0;
    return a2 / (PI * d * d);
}

vec3 light(vec3 n, vec3 v, vec3 l, vec3 color, vec3 base, float metal, float rough) {
    float ndl = min(dot(n, l), 1.0);
    if (ndl <= 0.0) {
        return vec3(0.0);
    }
    vec3 h = normalize(l + v + vec3(0.0, 0.0, 1.0e-5));
    float ndv = clamp(dot(n, v), 0.001, 1.0);
    float ndh = clamp(dot(n, h), 0.0, 1.0);
    float vdh = clamp(dot(v, h), 0.0, 1.0);
    vec3 f0 = mix(vec3(0.04), base, metal);
    vec3 fresnel = f0 + (1.0 - f0) * pow(1.0 - vdh, 5.0);
    float a = max(rough * rough, 0.002);
    float k = (rough + 1.0) * (rough + 1.0) / 8.0;
    float g = ndl / (ndl * (1.0 - k) + k) * ndv / (ndv * (1.0 - k) + k);
    vec3 spec = ggx(ndh, a) * fresnel * g / max(4.0 * ndl * ndv, 0.001);
    vec3 diffuse = base * (1.0 - metal) * (1.0 - fresnel);
    return (diffuse + spec * 0.8) * color * ndl;
}

void main() {
    vec3 n = normalize(viewNormal);
    if (!gl_FrontFacing) {
        n = -n;
    }
    vec3 v = normalize(-viewPos);
    float ao = vertexColor.r;
    float glowMask = vertexColor.g;
    int mat = int(vertexColor.b * 255.0 + 0.5);

    vec3 base = vec3(0.05);
    float metal = 0.0;
    float rough = 0.5;
    vec3 emit = GlowColor * GlowStrength * glowMask;
    if (mat == 0) {
        // Tungsten shaft: milled lengthwise, so the highlight breaks into fine streaks; a seam every 0.8.
        float seam = 1.0 - smoothstep(0.0, 0.035, abs(fract(objPos.z * 1.25) - 0.5) - 0.44);
        float mill = noise(vec3(atan(objPos.y, objPos.x) * 40.0, objPos.z * 0.6, 0.0));
        base = vec3(0.3, 0.31, 0.33) * (1.0 - 0.5 * seam);
        metal = 0.45;
        rough = 0.34 + 0.16 * mill;
    } else if (mat == 1) {
        base = vec3(0.42, 0.41, 0.4);
        metal = 0.6;
        rough = 0.4;
    } else if (mat == 2) {
        // The blade: polished carbide that glows from the point back as it heats.
        base = vec3(0.5, 0.5, 0.53);
        metal = 0.75;
        rough = 0.24 + 0.08 * noise(objPos * 9.0);
        float tip = smoothstep(3.0, 5.0, objPos.z);
        emit += mix(vec3(1.0, 0.32, 0.06), vec3(1.0, 0.92, 0.82), tip) * Heat * (0.2 + tip * 2.2);
    } else if (mat == 3) {
        // Structural frames and housings: dark anodised metal, rough enough to show its shape in the sun.
        base = vec3(0.17, 0.17, 0.18);
        metal = 0.55;
        rough = 0.42;
    } else if (mat == 4) {
        base = vec3(0.1, 0.05, 0.025);
        rough = 0.6;
    } else if (mat == 5) {
        float grain = noise(objPos * 3.1) * 0.6 + noise(objPos * 11.0) * 0.4;
        base = mix(vec3(0.12, 0.105, 0.09), vec3(0.3, 0.27, 0.23), grain);
        rough = 0.92;
    } else if (mat == 6) {
        // Gold insulation foil, crinkled into facets that catch the light from almost any angle.
        float crinkle = noise(objPos * 23.0) * 0.6 + noise(objPos * 61.0) * 0.4;
        n = normalize(n + (vec3(noise(objPos * 67.0), noise(objPos * 73.0 + 3.0), noise(objPos * 59.0 + 7.0)) - 0.5) * 0.8);
        // Real MLI scatters the sun off countless tiny facets, so it reads gold from almost anywhere: part diffuse.
        base = vec3(0.9, 0.66, 0.24) * (0.7 + 0.45 * crinkle);
        metal = 0.5;
        rough = 0.5;
    } else if (mat == 7) {
        vec2 cell = fract(objPos.xz * 7.0);
        float grid = step(0.9, max(cell.x, cell.y));
        base = mix(vec3(0.02, 0.04, 0.1), vec3(0.4), grid);
        metal = 0.3;
        rough = mix(0.12, 0.5, grid);
    } else if (mat == 8) {
        base = vec3(0.62);
        rough = 0.6;
    } else if (mat == 9) {
        // Ground cutting edges and fin leading edges: bright steel.
        base = vec3(0.7, 0.7, 0.72);
        metal = 1.0;
        rough = 0.14;
        emit *= 0.45;
        emit += vec3(1.0, 0.5, 0.15) * Heat * 1.2 * smoothstep(2.6, 5.0, objPos.z);
    } else if (mat == 10) {
        float r = runes(objPos);
        base = mix(vec3(0.2, 0.2, 0.21), vec3(0.03), r);
        metal = mix(0.9, 0.2, r);
        rough = mix(0.25, 0.8, r);
        emit = GlowColor * (GlowStrength * 1.6 + 0.15) * r;
    } else if (mat == 11) {
        // Copper armature bands on the sabot.
        base = vec3(0.86, 0.48, 0.28);
        metal = 1.0;
        rough = 0.3;
    } else if (mat == 12) {
        // Brushed aluminium sabot with panel lines across it.
        float brush = noise(vec3(atan(objPos.y, objPos.x) * 60.0, objPos.z * 0.8, 1.0));
        float line = 1.0 - smoothstep(0.0, 0.02, abs(fract(objPos.z * 1.43 + 0.3) - 0.5) - 0.475);
        base = vec3(0.56, 0.57, 0.59) * (1.0 - 0.55 * line);
        metal = 0.35;
        rough = 0.46 + 0.14 * brush;
    } else if (mat == 13) {
        base = vec3(0.03);
        rough = 0.75;
    } else if (mat == 14) {
        // Radiator fins: pale, dull, and red-hot after the coils fire.
        base = vec3(0.32, 0.31, 0.3);
        metal = 0.2;
        rough = 0.55;
        emit = vec3(1.0, 0.25, 0.05) * Heat * glowMask;
    } else if (mat == 15) {
        // Io: sulphur-yellow plains, white frost, red-brown patches and black volcanic spots.
        vec3 q = normalize(objPos) * 3.0;
        float plains = noise(q) * 0.55 + noise(q * 2.7) * 0.3 + noise(q * 7.1) * 0.15;
        base = mix(vec3(0.62, 0.52, 0.2), vec3(0.85, 0.8, 0.55), plains);
        base = mix(base, vec3(0.45, 0.22, 0.1), smoothstep(0.62, 0.75, noise(q * 1.6 + 4.0)) * 0.7);
        base = mix(base, vec3(0.04, 0.03, 0.02), smoothstep(0.78, 0.86, noise(q * 4.3 + 9.0)));
        rough = 0.9;
    } else if (mat == 17) {
        // Bifröst's hull: pale ceramic plates in staggered rows, each its own shade, a few dark service panels, dark
        // seams that fade out where the plates get too small on screen to show them.
        vec3 an = abs(objNormal);
        vec2 f = an.x > an.y && an.x > an.z ? objPos.zy : an.y > an.z ? objPos.xz : objPos.xy;
        vec2 g = f * vec2(1.1, 2.2);
        g.x += floor(g.y) * 0.5;
        vec2 cell = floor(g);
        float h = hash(vec3(cell, 3.7));
        vec2 e = 0.5 - abs(fract(g) - 0.5);
        vec2 w = fwidth(g);
        float seam = 1.0 - smoothstep(0.0, 1.0, min(e.x / max(w.x * 1.4, 1.0e-4), e.y / max(w.y * 1.4, 1.0e-4)) - 0.4);
        seam *= 1.0 - smoothstep(0.12, 0.35, max(w.x, w.y));
        base = vec3(0.6, 0.61, 0.64) * (0.82 + 0.3 * h);
        base = mix(base, vec3(0.2, 0.21, 0.23), step(0.93, h));
        base *= 1.0 - 0.6 * seam;
        metal = 0.2;
        rough = 0.4 + 0.25 * hash(vec3(cell, 8.1));
    } else if (mat == 16) {
        // The Moon: pale highland regolith, dark basalt maria, and fresh craters ringed with bright ejecta.
        vec3 q = normalize(objPos);
        float maria = noise(q * 2.1 + 1.3) * 0.6 + noise(q * 4.6 + 7.0) * 0.4;
        float grain = noise(q * 17.0) * 0.5 + noise(q * 43.0) * 0.5;
        base = mix(vec3(0.42, 0.41, 0.39), vec3(0.14, 0.14, 0.15), smoothstep(0.52, 0.62, maria));
        base *= 0.82 + 0.32 * grain;
        float craters = noise(q * 29.0 + 3.0);
        base += vec3(0.16) * smoothstep(0.8, 0.88, craters) - vec3(0.06) * smoothstep(0.88, 0.95, craters);
        rough = 0.95;
    }

    if (texCoord.y > 0.5) {
        // One of Bifröst's emitters: dark until the sweep reaches it, brightest just as it lights.
        float dist = abs(fract(texCoord.x - 0.125 + 0.5) - 0.5) * 2.0;
        float ahead = Sweep - dist;
        float lit = step(0.0, ahead);
        float head = exp(-max(ahead, 0.0) * 18.0);
        float chase = 0.78 + 0.22 * sin(dist * 48.0 - Phase);
        emit *= lit * (chase + 2.2 * head);
    }
    vec3 color = light(n, v, normalize(LightDir), LightColor, base, metal, rough);
    color += light(n, v, normalize(FillDir + vec3(0.0, 0.0, 1.0e-4)), FillColor, base, metal, rough);
    // A nearby light, such as a coil firing as the spear goes through it.
    vec3 toPoint = PointPos - viewPos;
    float d2 = max(dot(toPoint, toPoint), 1.0e-4);
    color += light(n, v, toPoint * inversesqrt(d2), PointColor / (1.0 + d2), base, metal, max(rough, 0.2));
    // What the surface reflects of its surroundings: the sky, plus the fill source where it faces it.
    vec3 r = reflect(-v, n);
    float ndv = clamp(dot(n, v), 0.0, 1.0);
    vec3 f0 = mix(vec3(0.04), base, metal);
    vec3 envFresnel = f0 + (max(vec3(1.0 - rough), f0) - f0) * pow(1.0 - ndv, 5.0);
    // The sky is not black to a metal: scattered sunlight and the planet's glow make a broad, soft environment.
    float towardSun = dot(r, normalize(LightDir)) * 0.5 + 0.5;
    vec3 env = AmbientColor * 2.5 + LightColor * 0.25 * towardSun * towardSun
        + FillColor * pow(max(dot(r, normalize(FillDir + vec3(0.0, 0.0, 1.0e-4))), 0.0), 2.0) * 0.6;
    color += env * envFresnel * ao * (1.0 - 0.45 * rough);
    color += base * (1.0 - metal) * AmbientColor * ao;
    color += RimColor * pow(1.0 - ndv, 4.0) * ao;
    color += emit;
    fragColor = vec4(color * Fade, 1.0);
}
