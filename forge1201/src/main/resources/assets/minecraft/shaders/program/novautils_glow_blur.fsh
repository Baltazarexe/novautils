#version 150

// NovaUtils soft glow: a wide gaussian instead of the 2-pixel blur.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 BlurDir;

out vec4 fragColor;

void main() {
    vec2 stepDir = oneTexel * BlurDir;
    const float R = 11.0;
    const float SIGMA = 4.2;
    vec4 sum = vec4(0.0);
    float weights = 0.0;
    for (float a = -R; a <= R; a += 1.0) {
        float w = exp(-(a * a) / (2.0 * SIGMA * SIGMA));
        vec4 s = texture(DiffuseSampler, texCoord + stepDir * a);
        sum += vec4(s.rgb * s.a, s.a) * w;
        weights += w;
    }
    vec4 avg = sum / weights;
    vec3 color = avg.a > 0.0001 ? avg.rgb / avg.a : vec3(0.0);
    // soft curve: full strength at the edge, a long fade outwards
    fragColor = vec4(color, 1.0 - exp(-avg.a * 2.6));
}
