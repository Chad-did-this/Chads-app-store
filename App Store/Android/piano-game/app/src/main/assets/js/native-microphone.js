'use strict';

class NativeMicrophone {
    constructor() {
        this.samples = new Float32Array(4096);
        this.sequence = -1;
        this.lastFrameTime = 0;
    }

    bridge() {
        const bridge = window.AndroidMic;
        if (!bridge || ['start', 'stop', 'status', 'frame'].some(name => typeof bridge[name] !== 'function')) {
            throw new Error('Microphone input needs the Android app. Use the on-screen test keys here.');
        }
        return bridge;
    }

    async start(isCurrent) {
        const bridge = this.bridge();
        this.sequence = -1;
        bridge.start();
        const deadline = performance.now() + 60000;

        while (isCurrent()) {
            const state = bridge.status();
            if (state === 'ready') {
                this.lastFrameTime = performance.now();
                return true;
            }
            if (state !== 'starting') throw new Error(this.message(state));
            if (performance.now() >= deadline) {
                throw new Error('Microphone permission or startup timed out. Tap Play to retry.');
            }
            await new Promise(resolve => setTimeout(resolve, 100));
        }
        return false;
    }

    read(time) {
        const bridge = this.bridge();
        const state = bridge.status();
        if (state !== 'ready') throw new Error(this.message(state));

        const packet = bridge.frame();
        if (!packet) return this.waitForFrame(time);

        const first = packet.indexOf(':');
        const second = packet.indexOf(':', first + 1);
        const sequence = Number(packet.slice(0, first));
        const sampleRate = Number(packet.slice(first + 1, second));
        if (first < 1 || second <= first + 1 || !Number.isSafeInteger(sequence)
            || sequence < 1 || ![16000, 22050, 32000, 44100, 48000].includes(sampleRate)) {
            throw new Error('Microphone returned invalid audio data. Tap Play to retry.');
        }
        if (sequence === this.sequence) return this.waitForFrame(time);

        let bytes;
        try {
            bytes = atob(packet.slice(second + 1));
        } catch {
            throw new Error('Microphone returned invalid audio data. Tap Play to retry.');
        }
        if (bytes.length !== this.samples.length * 2) {
            throw new Error('Microphone returned incomplete audio data. Tap Play to retry.');
        }

        for (let i = 0; i < this.samples.length; i++) {
            const word = bytes.charCodeAt(2 * i) | (bytes.charCodeAt(2 * i + 1) << 8);
            this.samples[i] = (word >= 32768 ? word - 65536 : word) / 32768;
        }
        this.sequence = sequence;
        this.lastFrameTime = time;
        return { samples: this.samples, sampleRate };
    }

    waitForFrame(time) {
        if (time - this.lastFrameTime > 3000) {
            throw new Error('Microphone stopped sending audio. Tap Play to retry.');
        }
        return null;
    }

    stop() {
        const bridge = window.AndroidMic;
        if (bridge && typeof bridge.stop === 'function') bridge.stop();
        this.sequence = -1;
        this.lastFrameTime = 0;
    }

    message(state) {
        if (state === 'stopped') return 'Recording stopped. Tap Play to restart.';
        return state || 'Microphone unavailable. Tap Play to retry.';
    }
}
