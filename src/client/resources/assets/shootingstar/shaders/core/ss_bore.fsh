#version 150

// The accelerator's coils seen from inside the barrel, drawn on a tube one coil radius across (objPos.xy round the
// unit circle, objPos.z along the barrel in coil spacings). Instead of drawing each coil and smearing many frames
// together, every ring is box-filtered analytically over the distance the coils travel while the shutter is open (plus
// the pixel's own footprint), so the barrel is exact at any speed: separate rings when slow, a smooth glowing tube when
// fast, never strobing. Each period along the barrel holds a housing's inner face (its emitter strips), the face it
// turns to the camera (its status lights), and the gap to the next coil, which shows what lies beyond. Blended with
// premultiplied alpha.

uniform float Travel;
uniform float Smear;
uniform float RoundZ;
uniform float CameraZ;
uniform float Speed;
uniform float Idle;
uniform float Light;
uniform float Twist;
uniform vec3 ArcColor;
uniform vec3 HotColor;

in vec3 viewPos;
in vec3 viewNormal;
in vec3 objPos;
in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

const float PI = 3.14159265;
// A housing's depth along the barrel and its height out from the bore, in coil spacings (tools/models.py).
const float DEPTH = 0.28;
const float HEIGHT = 0.28;
// Every STATION coils a ring of bright running lights marks a segment of the barrel, like the lamps in a road tunnel.
const float STATION = 12.0;

// Integral from 0 to x of a comb of boxes of width w centred on the integers (period 1).
float comb(float x, float w) {
    float f = fract(x + 0.5);
    return floor(x + 0.5) * w + clamp(f - 0.5 + w * 0.5, 0.0, w) - w * 0.5;
}

// The comb's mean over [x - len, x]: a box of width w smeared over len.
float smeared(float x, float w, float len) {
    return (comb(x, w) - comb(x - len, w)) / len;
}

// How hard a coil rel spacings ahead of the round (negative: behind) is firing (x), and how hot it still is (y).
vec2 wave(float rel) {
    float front = rel >= 0.0 ? exp(-rel * rel * 2.2) : exp(-rel * rel * 6.0);
    float trail = rel < 0.0 ? exp(rel / (0.8 + 3.0 * Speed)) : 0.0;
    return vec2(front, trail);
}

void main() {
    vec3 n = normalize(viewNormal);
    vec3 v = normalize(-viewPos);
    float c = clamp(abs(dot(n, v)), 0.02, 1.0);
    // Looking along the wall, the face each housing turns to the camera covers part of the gap behind it: its
    // height projected along the barrel.
    float grazing = min(HEIGHT * sqrt(1.0 - c * c) / c, 1.0 - DEPTH);
    // Which side of each housing faces the camera: the near side when looking up the barrel, the far side looking back.
    float side = objPos.z > CameraZ ? -1.0 : 1.0;

    float z = objPos.z + Travel;
    // Motion blur plus the pixel's footprint, so far rings average out instead of aliasing.
    float len = max(Smear + fwidth(objPos.z) * 1.5, 1.0e-3);
    float blur = smoothstep(0.15, 1.2, len);
    float inner = smeared(z, DEPTH, len);
    float faceShift = side * (DEPTH + grazing) * 0.5;
    float face = grazing > 0.002 ? smeared(z - faceShift, grazing, len) : 0.0;

    // The firing wave rides with the round. While the rings are still sharp each coil fires as a whole as the round
    // passes it; once they blur together it is a smooth glow travelling with the round.
    float rel = objPos.z - RoundZ;
    float ring = floor(z + 0.5);
    vec2 w = mix(wave(ring - Travel - RoundZ), wave(rel), blur);
    float angle = atan(objPos.y, objPos.x);

    // Inner face: twelve emitter strips round each coil, each coil turned a little further than the last; a fired coil
    // glows all over its face, brightest along the strips.
    float phase = 12.0 * (angle - ring * Twist) / (2.0 * PI);
    float strip = 1.0 - smoothstep(0.08, 0.2, abs(fract(phase) - 0.5));
    float strips = mix(0.35 + 0.65 * strip, 0.55, blur);
    vec3 flash = ArcColor * w.x * (2.4 + 4.0 * Speed);
    vec3 cool = mix(vec3(0.55, 0.08, 0.03), HotColor, w.y);
    vec3 cooling = cool * w.y * (0.8 + 1.4 * Speed);
    float d2 = rel * rel + 0.6;
    vec3 innerLit = vec3(0.07, 0.068, 0.072) * Light + ArcColor * (0.5 + 1.2 * Speed) * 0.35 / (1.0 + d2);
    vec3 innerColor = innerLit + (flash + cooling) * strips + vec3(1.0, 0.55, 0.22) * Idle * strip * step(0.0, rel);

    // The face turned to the camera: lit hard by the coil firing round the round when it faces it (the rings past
    // the round), and by the glow of the cooling coils on the other side; six status lights on each.
    float facesRound = step(0.0, -rel * side);
    vec3 faceLit = vec3(0.1, 0.098, 0.104) * Light
        + ArcColor * (1.2 + 3.0 * Speed) * facesRound / (1.0 + rel * rel * 0.7)
        + HotColor * 0.35 * (1.0 - facesRound) * exp(-abs(rel) * 0.8) * (0.4 + Speed);
    // The status lights sit in the middle of the face, lined up from coil to coil, so when the rings blur together
    // they draw six straight speed lines down the barrel.
    float dots = pow(max(cos(6.0 * angle), 0.0), 90.0) * smeared(z - faceShift, max(grazing * 0.3, 0.03), len);
    vec3 status = vec3(1.0, 0.62, 0.3) * dots * (0.8 + 2.0 * w.x + 1.5 * Speed);

    // Station rings: a slower beat of bright running lights that still shows the speed once the coils blur together.
    float marker = smeared((z - faceShift) / STATION, max(grazing, 0.12) / STATION, len / STATION);
    float lamps = pow(max(cos(4.0 * (angle - 0.3927)), 0.0), 60.0);
    vec3 stationLight = vec3(0.9, 0.95, 1.0) * marker * (lamps * 4.0 + 0.15);

    vec3 color = inner * innerColor + face * faceLit + status + stationLight;
    float alpha = clamp(inner + face, 0.0, 1.0);
    fragColor = vec4(color, alpha);
}
