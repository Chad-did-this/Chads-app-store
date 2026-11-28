'use strict';

const MIC_SENSITIVITY = {low: 0.004, normal: 0.001, high: 0.0002, max: 0.00006};
const down = new Float32Array(8192);
const diff = new Float32Array(4096);

function microphoneThreshold(level = 'high') {
    return MIC_SENSITIVITY[level] || MIC_SENSITIVITY.high;
}

function microphoneLevel(rms, minimumRms) {
    if (!Number.isFinite(rms) || rms <= 0) return 0;
    const relative = 20 * Math.log10(rms / minimumRms);
    return Math.max(0, Math.min(100, (relative + 8) * 100 / 48));
}

function detectPitch(input, sampleRate, minimumRms = microphoneThreshold()) {
    const stride = sampleRate >= 40000 ? 2 : 1;
    const rate = sampleRate / stride;
    const count = Math.floor(input.length / stride);
    if (count < 64 || count > down.length || !Number.isFinite(rate) || rate <= 0) return null;

    let mean = 0;
    for (let i = 0; i < count; i++) {
        let sample = 0;
        for (let j = 0; j < stride; j++) sample += input[i * stride + j];
        down[i] = sample / stride;
        mean += down[i];
    }
    mean /= count;
    let power = 0;
    for (let i = 0; i < count; i++) {
        down[i] -= mean;
        power += down[i] * down[i];
    }

    const rms = Math.sqrt(power / count);
    if (!Number.isFinite(rms)) return {rms: 0, frequency: null, confidence: 0};
    const floor = Number.isFinite(minimumRms) && minimumRms > 0 ? minimumRms : microphoneThreshold();
    if (rms < floor) return {rms, frequency: null, confidence: 0};
    for (let i = 0; i < count; i++) down[i] /= rms;

    const minLag = Math.max(2, Math.floor(rate / 2200));
    const maxLag = Math.min(diff.length - 2, Math.ceil(rate / 45), Math.floor(count / 2) - 2);
    const window = count - maxLag;
    if (maxLag <= minLag + 2) return {rms, frequency: null, confidence: 0};

    let sum = 0;
    diff[0] = 1;
    for (let lag = 1; lag <= maxLag + 1; lag++) {
        let difference = 0;
        for (let i = 0; i < window; i++) {
            const delta = down[i] - down[i + lag];
            difference += delta * delta;
        }
        sum += difference;
        diff[lag] = sum ? difference * lag / sum : 1;
    }

    let best = -1;
    for (let lag = minLag; lag <= maxLag; lag++) {
        if (diff[lag] < 0.16) {
            while (lag < maxLag && diff[lag + 1] < diff[lag]) lag++;
            best = lag;
            break;
        }
    }
    if (best < 0) {
        let value = 1;
        for (let lag = minLag; lag <= maxLag; lag++) {
            if (diff[lag] < value) {
                value = diff[lag];
                best = lag;
            }
        }
        if (value > 0.25) return {rms, frequency: null, confidence: 0};
    }

    const a = diff[best - 1];
    const b = diff[best];
    const c = diff[best + 1];
    const denominator = a - 2 * b + c;
    const refined = best + (Math.abs(denominator) > 1e-9
        ? Math.max(-0.5, Math.min(0.5, 0.5 * (a - c) / denominator)) : 0);
    return {rms, frequency: rate / refined, confidence: 1 - b};
}
