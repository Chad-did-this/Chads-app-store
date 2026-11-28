const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const assets = path.join(__dirname, '../app/src/main/assets');

function loadGame(bridge) {
    const elements = new Map();
    function element(id) {
        if (!elements.has(id)) elements.set(id, {
            hidden: !['home'].includes(id),
            value: '', checked: false, disabled: false,
            style: {}, dataset: {}, clientWidth: 400,
            setAttribute(name, value) { this[name] = value; },
            replaceChildren(...children) { this.children = children; }
        });
        return elements.get(id);
    }
    for (const [id, value] of Object.entries({
        bpm: '75', density: '1', mode: 'timed', range: 'beginner', acc: 'natural'
    })) element(id).value = value;
    const hands = ['treble', 'bass', 'both'].map(clef => ({
        dataset: {clef}, setAttribute() {}
    }));
    const events = {};
    const document = {
        hidden: false,
        getElementById: element,
        querySelectorAll: () => hands,
        createElement: () => ({dataset: {}, style: {}}),
        addEventListener(name, callback) { events[name] = callback; }
    };
    const context = vm.createContext({
        document, AndroidMic: bridge, Float32Array, console,
        performance: {now: () => 100},
        setTimeout, clearTimeout,
        requestAnimationFrame() {},
        addEventListener(name, callback) { events[name] = callback; },
        atob: value => {
            if (!/^[A-Za-z0-9+/]*={0,2}$/.test(value)) throw new Error('Invalid base64');
            return Buffer.from(value, 'base64').toString('binary');
        }
    });
    context.window = context;
    for (const name of ['pitch.js', 'native-microphone.js', 'game.js']) {
        vm.runInContext(fs.readFileSync(path.join(assets, 'js', name), 'utf8'), context);
    }
    return {context, element, events, document, run: code => vm.runInContext(code, context)};
}

function fakeBridge() {
    return {
        state: 'stopped', packet: '', stops: 0,
        start() { this.state = 'ready'; },
        stop() { this.state = 'stopped'; this.stops++; },
        status() { return this.state; },
        frame() { return this.packet; }
    };
}

function packet(sequence = 1, rate = 48000) {
    const bytes = Buffer.alloc(8192);
    bytes.writeInt16LE(-32768, 0);
    bytes.writeInt16LE(32767, 2);
    return `${sequence}:${rate}:${bytes.toString('base64')}`;
}

test('pitch detection covers both hands at every fallback sample rate', () => {
    const app = loadGame();
    for (const sampleRate of [16000, 22050, 32000, 44100, 48000]) {
        for (const midi of [36, 48, 60, 69, 84]) {
            const frequency = 440 * 2 ** ((midi - 69) / 12);
            app.context.input = Float32Array.from({length: 4096}, (_, i) =>
                0.2 * Math.sin(2 * Math.PI * frequency * i / sampleRate));
            const result = app.run(`detectPitch(input, ${sampleRate})`);
            assert.ok(Math.abs(1200 * Math.log2(result.frequency / frequency)) < 8,
                `MIDI ${midi} at ${sampleRate} Hz`);
        }
    }
    app.context.input = new Float32Array(4096).fill(0.1);
    assert.equal(app.run('detectPitch(input, 48000).frequency'), null);
});

test('native frames decode signed PCM and ignore duplicates', async () => {
    const bridge = fakeBridge();
    const app = loadGame(bridge);
    await app.run('startMic()');
    bridge.packet = packet();
    const frame = app.run('nativeMicrophone.read(110)');
    assert.equal(frame.sampleRate, 48000);
    assert.equal(frame.samples[0], -1);
    assert.equal(frame.samples[1], 32767 / 32768);
    assert.equal(app.run('nativeMicrophone.read(120)'), null);
    bridge.packet = packet(2);
    assert.ok(app.run('nativeMicrophone.read(10000)'));
    assert.throws(() => app.run('nativeMicrophone.read(14001)'), /stopped sending audio/);
    app.run('closeMic()');
});

test('invalid native packets fail with a retry message', async () => {
    const bridge = fakeBridge();
    const app = loadGame(bridge);
    await app.run('startMic()');
    for (const invalid of ['bad', '1:0:AAAA', '1:48000:!invalid', '1:48000:AAAA']) {
        bridge.packet = invalid;
        assert.throws(() => app.run('nativeMicrophone.read(110)'), /audio data/);
    }
    app.run('closeMic()');
});

test('missing native bridge returns to Play and test keys still work', async () => {
    const app = loadGame();
    await app.run('startMic()');
    assert.equal(app.run('mic'), false);
    assert.equal(app.element('start').disabled, false);
    assert.match(app.element('homeStatus').textContent, /Android app/);
    app.element('demo').onclick();
    assert.equal(app.run('game && !mic && !paused'), true);
    assert.equal(app.element('keyDetails').hidden, false);
});

test('permission denial and interrupted capture release the microphone', async () => {
    const bridge = fakeBridge();
    bridge.start = function () { this.state = 'Permission denied'; };
    const app = loadGame(bridge);
    await app.run('startMic()');
    assert.equal(app.element('start').disabled, false);
    assert.match(app.element('homeStatus').textContent, /Permission denied/);
    bridge.start = function () { this.state = 'ready'; };
    await app.run('startMic()');
    bridge.state = 'Android silenced the microphone';
    app.run('analyze(200)');
    assert.equal(app.run('game || mic || starting'), false);
    assert.equal(app.element('home').hidden, false);
    assert.match(app.element('homeStatus').textContent, /silenced/);
    assert.equal(bridge.state, 'stopped');
});

test('cancelling startup prevents late permission results from starting a game', async () => {
    const bridge = fakeBridge();
    bridge.start = function () { this.state = 'starting'; };
    const app = loadGame(bridge);
    const pending = app.run('startMic()');
    app.run('closeMic()');
    bridge.state = 'ready';
    await pending;
    assert.equal(app.run('game || mic || starting'), false);
    assert.equal(app.element('start').disabled, false);
});

test('backgrounding cancels microphone startup and stops active recording', async () => {
    const bridge = fakeBridge();
    const app = loadGame(bridge);
    await app.run('startMic()');
    app.document.hidden = true;
    app.events.visibilitychange();
    assert.equal(bridge.state, 'stopped');
    assert.equal(app.run('game || mic || starting'), false);
    assert.equal(app.element('home').hidden, false);
});

test('microphone timing compensation is applied before marking missed notes', () => {
    const app = loadGame();
    app.run(`
        game=true; mic=true; beat=1.4; lastTime=100;
        queue=[{m:60, clef:'treble', spell:spelling(60), at:1, result:null}];
        advanceTo(136);
    `);
    assert.equal(app.run('missed'), 0);
    app.run("play(60,136,'mic')");
    assert.equal(app.run('correct'), 1);
    assert.equal(app.run('wrong + missed'), 0);
});

test('test-key timing and wait-for-note mode keep their existing scoring', () => {
    const app = loadGame();
    app.run(`
        game=true; beat=1; lastTime=100;
        queue=[{m:60, clef:'treble', spell:spelling(60), at:1, result:null}];
        play(60,100);
    `);
    assert.equal(app.run('correct'), 1);
    app.element('mode').value = 'wait';
    app.run(`
        beat=2; lastTime=100;
        queue=[{m:62, clef:'treble', spell:spelling(62), at:2, result:null}];
        play(60,100); play(62,100);
    `);
    assert.equal(app.run('wrong'), 1);
    assert.equal(app.run('correct'), 2);
    assert.equal(app.run('missed'), 0);
});
