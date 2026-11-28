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
        bpm: '75', density: '1', mode: 'timed', range: 'beginner', acc: 'natural', middleCLabel: '3', micSensitivity: 'high', micSensitivityQuick: 'high', gameHands: 'treble', gameBpm: '75'
    })) element(id).value = value;
    element('hintToggle').checked = true;
    function node() {
        return {dataset: {}, style: {}, children: [],
            setAttribute(name, value) { this[name] = value; },
            replaceChildren(...children) { this.children = children; }};
    }
    const hands = ['treble', 'bass', 'both'].map(clef => Object.assign(node(), {dataset: {clef}}));
    const studyHands = ['treble', 'bass', 'both'].map(studyHand =>
        Object.assign(node(), {dataset: {studyHand}}));
    const studyRanges = [node()];
    const studyAccidentals = [node()];
    const selectors = {
        '[data-clef]': hands, '[data-study-hand]': studyHands,
        '[data-study-range]': studyRanges, '[data-study-acc]': studyAccidentals
    };
    const events = {};
    const document = {
        hidden: false,
        getElementById: element,
        querySelectorAll: selector => selectors[selector] || [],
        createElement: node,
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
    for (const name of ['pitch.js', 'native-microphone.js', 'game.js', 'note-study.js']) {
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

test('Flashcards opens ordered right and left hand rows with names already visible', () => {
    const app = loadGame();
    app.element('openFlashcards').onclick();
    assert.equal(app.element('flashcardScreen').hidden, false);
    const hands = app.element('handCards').children;
    assert.deepEqual(hands.map(hand => hand.dataset.hand), ['treble', 'bass']);
    assert.equal(hands[0].children[0].children[0].textContent, 'Right hand');
    assert.equal(hands[1].children[0].children[0].textContent, 'Left hand');
    assert.deepEqual(hands[0].children[1].children.map(button => Number(button.dataset.midi)), [60, 62, 64, 65, 67]);
    assert.deepEqual(hands[1].children[1].children.map(button => Number(button.dataset.midi)), [48, 50, 52, 53, 55]);
    for (const [index, letter] of ['C', 'D', 'E', 'F', 'G'].entries()) {
        assert.match(hands[0].children[1].children[index].innerHTML, new RegExp(`<strong>${letter}</strong>`));
        assert.match(hands[1].children[1].children[index].innerHTML, new RegExp(`<strong>${letter}</strong>`));
    }
    assert.match(hands[0].children[1].children[0].innerHTML, /C3/);
    assert.match(hands[1].children[1].children[0].innerHTML, /C2/);
    assert.equal(app.run('game || mic || starting'), false);
    app.run('tick(600000)');
    assert.equal(app.element('handCards').children, hands);
    for (const hand of hands) {
        assert.equal([...hand.children[3].innerHTML.matchAll(/data-active="true"/g)].length, 5);
    }
});

test('starting C choices update only that hand and match the actual practice notes', () => {
    const app = loadGame();
    app.element('openFlashcards').onclick();
    const rightSelect = app.element('handCards').children[0].children[0].children[1].children[1];
    assert.equal(rightSelect.value, '60');
    rightSelect.value = '72';
    rightSelect.onchange();
    const hands = app.element('handCards').children;
    assert.match(hands[0].children[1].children[0].innerHTML, /C4/);
    assert.match(hands[1].children[1].children[0].innerHTML, /C2/);
    assert.equal(app.run("nameOf(poolFor('treble','beginner','natural')[0])"), 'C4');
    assert.equal(app.run("nameOf(poolFor('bass','beginner','natural')[0])"), 'C2');
    app.element('flashOverviewBack').onclick();
    app.element('demo').onclick();
    assert.equal(app.run('queue.every(note=>[72,74,76,77,79].includes(note.m))'), true);
});

test('staff reference names all five lines and four spaces in both clefs correctly', () => {
    const app = loadGame();
    const lines = {treble: [64, 67, 71, 74, 77], bass: [43, 47, 50, 53, 57]};
    const spaces = {treble: [65, 69, 72, 76], bass: [45, 48, 52, 55]};
    for (const clef of ['treble', 'bass']) {
        lines[clef].forEach((m, i) => {
            assert.equal(app.run(`studyNoteInfo({m:${m},clef:'${clef}',spell:spelling(${m})}).position`), i * 2);
        });
        spaces[clef].forEach((m, i) => {
            assert.equal(app.run(`studyNoteInfo({m:${m},clef:'${clef}',spell:spelling(${m})}).position`), i * 2 + 1);
        });
    }
    assert.match(app.run("studyNoteInfo({m:60,clef:'treble',spell:spelling(60)}).location"), /ledger line below/);
    assert.match(app.run("studyNoteInfo({m:60,clef:'bass',spell:spelling(60)}).location"), /ledger line above/);
    assert.equal(app.run("studyNoteInfo({m:60,clef:'treble',spell:spelling(60)}).spoken"), 'Middle C · octave 3');
});

test('every supported piano note highlights exactly its own key', () => {
    const app = loadGame();
    for (let midi = 36; midi <= 84; midi++) {
        const svg = app.run(`studyKeyboard(${midi})`);
        assert.equal([...svg.matchAll(/data-active="true"/g)].length, 1);
        assert.match(svg, new RegExp(`data-midi="${midi}" data-active="true"`));
    }
    const sharp = app.run("studyNoteInfo({m:61,clef:'treble',spell:spelling(61)})");
    const flat = app.run("studyNoteInfo({m:61,clef:'treble',spell:spelling(61,true)})");
    assert.equal(sharp.label, 'C♯3');
    assert.equal(flat.label, 'D♭3');
    assert.notEqual(sharp.position, flat.position);
    assert.equal(sharp.keyHint, flat.keyHint);
    assert.match(sharp.keyHint, /black key between C and D/);
});

test('opening the note guide freezes practice and returns without changing notes or scores', () => {
    const app = loadGame();
    app.element('demo').onclick();
    app.run('beat=4.5; correct=3; wrong=2; missed=1; lastTime=100;');
    const before = app.run('JSON.stringify(queue)');
    app.element('gameNoteGuide').onclick();
    assert.equal(app.element('noteGuideScreen').hidden, false);
    assert.equal(app.run('paused'), true);
    app.run('advanceTo(600000)');
    assert.equal(app.run('beat'), 4.5);
    assert.equal(app.run('correct'), 3);
    assert.equal(app.run('wrong'), 2);
    assert.equal(app.run('missed'), 1);
    assert.equal(app.run('JSON.stringify(queue)'), before);
    app.element('guideBack').onclick();
    assert.equal(app.element('gameScreen').hidden, false);
    assert.equal(app.run('paused'), false);
    assert.equal(app.run('lastTime'), null);
    app.element('pause').onclick();
    app.element('pauseNoteGuide').onclick();
    app.element('guideBack').onclick();
    assert.equal(app.element('pauseScreen').hidden, false);
    assert.equal(app.run('paused'), true);
});

test('guide filters include both clefs, sharps and flats without changing game settings', () => {
    const app = loadGame();
    app.element('openNoteGuide').onclick();
    const studyHands = app.document.querySelectorAll('[data-study-hand]');
    studyHands.find(button => button.dataset.studyHand === 'bass').onclick();
    const bass = app.element('guideNotes').children;
    assert.equal(bass.length, 1);
    assert.match(bass[0].children[0].textContent, /Bass/);
    const bassCards = bass[0].children[1].children;
    assert.equal(bassCards.length, 5);
    assert.match(bassCards[0].innerHTML, /<strong>C2<\/strong>/);
    assert.match(bassCards.at(-1).innerHTML, /<strong>G2<\/strong>/);
    studyHands.find(button => button.dataset.studyHand === 'both').onclick();
    assert.equal(app.element('guideNotes').children.length, 2);
    const accidentalToggle = app.document.querySelectorAll('[data-study-acc]')[0];
    accidentalToggle.checked = true;
    accidentalToggle.onchange();
    const trebleCards = app.element('guideNotes').children[0].children[1].children;
    const labels = trebleCards.map(card => card.innerHTML).join('');
    assert.match(labels, /<strong>C♯3<\/strong>/);
    assert.match(labels, /<strong>D♭3<\/strong>/);
    assert.equal(app.run('hand'), 'treble');
    assert.equal(app.element('acc').value, 'natural');
});

test('tapping a hand note opens its labeled detail and Back returns to both hand rows', () => {
    const app = loadGame();
    app.element('openFlashcards').onclick();
    app.element('handCards').children[0].children[1].children[0].onclick();
    assert.equal(app.element('noteCardScreen').hidden, false);
    assert.equal(app.element('flashName').textContent, 'C3');
    assert.equal(app.element('noteCardTitle').textContent, 'Right hand · C3');
    assert.equal(app.element('flashAnswer').hidden, false);
    assert.match(app.element('flashKeyboard').innerHTML, /data-midi="60" data-active="true"/);
    app.element('flashBack').onclick();
    assert.equal(app.element('flashcardScreen').hidden, false);
    assert.equal(app.element('handCards').children.length, 2);
    app.element('handCards').children[1].children[1].children[4].onclick();
    assert.equal(app.element('noteCardTitle').textContent, 'Left hand · G2');
    app.element('flashBack').onclick();
    app.element('flashOverviewBack').onclick();
    assert.equal(app.element('home').hidden, false);
    for (const id of ['flashcardScreen', 'noteCardScreen', 'noteGuideScreen', 'gameScreen', 'pauseScreen']) {
        assert.equal(app.element(id).hidden, true);
    }
});

test('low starting notes stay inside their staff image and away from their labels', () => {
    const app = loadGame();
    for (const clef of ['treble', 'bass']) {
        for (const m of [36, 48, 60, 72]) {
            const svg = app.run(`studyStaff({m:${m},clef:'${clef}',spell:spelling(${m})})`);
            const height = Number(svg.match(/viewBox="0 0 320 ([0-9.]+)"/)[1]);
            const y = Number(svg.match(/<ellipse[^>]+cy="([0-9.]+)"/)[1]);
            assert.ok(y >= 0 && y + 10 < height);
        }
    }
    app.element('demo').onclick();
    const svg = app.element('staff').innerHTML;
    const lowestHead = Math.max(...[...svg.matchAll(/<ellipse[^>]+cy="([0-9.]+)"/g)].map(match => Number(match[1])));
    const label = Number(svg.match(/<text[^>]+y="([0-9.]+)"/)[1]);
    assert.ok(label >= lowestHead + 28);
});

test('visible game name toggle labels each scrolling note and can hide them again', () => {
    const app = loadGame();
    app.element('demo').onclick();
    assert.equal(app.run('hinting'), true);
    assert.match(app.element('staff').innerHTML, /<text[^>]+>[A-G]3<\/text>/);
    app.element('gameNoteNames').onclick();
    assert.equal(app.run('hinting'), false);
    assert.equal(app.element('hintToggle').checked, false);
    assert.doesNotMatch(app.element('staff').innerHTML, /<text/);
    app.element('gameNoteNames').onclick();
    assert.equal(app.run('hinting'), true);
    assert.equal(app.element('gameNoteNames')['aria-pressed'], 'true');
});

test('flashcards cancel a pending microphone request and ignore its late answer', async () => {
    const bridge = fakeBridge();
    bridge.start = function () { this.state = 'starting'; };
    const app = loadGame(bridge);
    const request = app.run('startMic()');
    app.element('openFlashcards').onclick();
    bridge.state = 'ready';
    await request;
    assert.equal(app.element('flashcardScreen').hidden, false);
    assert.equal(app.run('game || mic || starting'), false);
});

function keyboardKey(svg, midi) {
    const start = svg.indexOf(`<g class="study-piano-key" data-key-midi="${midi}"`);
    assert.ok(start >= 0, `Key ${midi} is present`);
    const next = svg.indexOf('<g class="study-piano-key"', start + 1);
    return svg.slice(start, next < 0 ? svg.length : next);
}

test('middle C is C3 and its single ledger line crosses the notehead on the C key', () => {
    const app = loadGame();
    assert.equal(app.run('nameOf(60)'), 'C3');
    assert.equal(app.run('spelling(60).label'), 'C3');
    assert.equal(app.run("studyNoteInfo({m:60,clef:'treble',spell:spelling(60)}).position"), -2);
    const key = keyboardKey(app.run('studyKeyboard(60)'), 60);
    assert.match(key, /data-key-note="60" data-clef="treble"/);
    assert.equal([...key.matchAll(/data-ledger="below"/g)].length, 1);
    const ledgerY = Number(key.match(/<line data-ledger="below"[^>]+y1="([0-9.]+)"/)[1]);
    const headY = Number(key.match(/<ellipse[^>]+cy="([0-9.]+)"/)[1]);
    assert.equal(ledgerY, headY);
    assert.match(key, />C3<\/text>/);
});

test('every white and black piano key contains the matching written note in its own clef', () => {
    const app = loadGame();
    for (const clef of ['treble', 'bass']) {
        const svg = app.run(`studyKeyboard(60, nameOf(60), [60], '${clef}')`);
        for (let midi = 60; midi <= 72; midi++) {
            const key = keyboardKey(svg, midi);
            assert.match(key, new RegExp(`data-key-note="${midi}" data-clef="${clef}"`));
            assert.match(key, new RegExp(`<ellipse data-midi="${midi}"`));
        }
    }
});

test('changing the middle C label changes names without moving notes or changing practice pitch', () => {
    const app = loadGame();
    app.element('demo').onclick();
    const pitches = app.run('JSON.stringify(queue.map(note=>note.m))');
    const position = app.run('spelling(60).step');
    app.element('middleCLabel').value = '4';
    app.element('middleCLabel').onchange();
    assert.equal(app.run('nameOf(60)'), 'C4');
    assert.equal(app.run('spelling(60).label'), 'C4');
    assert.equal(app.run('spelling(60).step'), position);
    assert.equal(app.run('JSON.stringify(queue.map(note=>note.m))'), pitches);
    assert.equal(app.run("nameOf(poolFor('treble','beginner','natural')[0])"), 'C4');
    assert.equal(app.run('queue.every(note=>note.spell.label.endsWith("4"))'), true);
});

test('piano key tap and keyboard activation enlarge the exact note without adding microphone access', () => {
    const app = loadGame();
    app.element('openFlashcards').onclick();
    const rightKeyboard = app.element('handCards').children[0].children[3];
    const target = {closest: () => ({dataset: {keyMidi: '60'}})};
    rightKeyboard.onclick({type: 'click', target});
    assert.equal(app.element('noteCardTitle').textContent, 'Right hand · C3');
    assert.equal(app.element('flashWords').textContent, 'Middle C · octave 3');
    assert.equal(app.run('mic || starting'), false);
    app.element('flashBack').onclick();
    const leftKeyboard = app.element('handCards').children[1].children[3];
    leftKeyboard.onkeydown({type: 'keydown', key: 'Enter', target: {closest: () => ({dataset: {keyMidi: '48'}})}});
    assert.equal(app.element('noteCardTitle').textContent, 'Left hand · C2');
    assert.match(app.element('flashKeyboard').innerHTML, /data-key-note="48" data-clef="bass"/);
});

test('microphone middle C frequency identifies the same C3 used on the staff and keyboard', () => {
    const app = loadGame();
    app.context.input = Float32Array.from({length: 4096}, (_, i) =>
        0.2 * Math.sin(2 * Math.PI * 261.625565 * i / 48000));
    const detected = app.run('Math.round(69+12*Math.log2(detectPitch(input,48000).frequency/440))');
    assert.equal(detected, 60);
    assert.equal(app.run(`nameOf(${detected})`), 'C3');
    assert.equal(app.run("poolFor('treble','beginner','natural')[0]"), detected);
});

function quietPianoPacket(sequence, amplitude = 0.001, midi = 60, rate = 48000) {
    const bytes = Buffer.alloc(8192);
    const frequency = 440 * 2 ** ((midi - 69) / 12);
    for (let i = 0; i < 4096; i++) {
        const sample = amplitude * Math.sin(2 * Math.PI * frequency * i / rate);
        bytes.writeInt16LE(Math.round(sample * 32767), 2 * i);
    }
    return `${sequence}:${rate}:${bytes.toString('base64')}`;
}

function noiseGenerator(seed) {
    let state = seed >>> 0;
    return () => {
        state = (Math.imul(state, 1664525) + 1013904223) >>> 0;
        return state / 2147483648 - 1;
    };
}

test('high sensitivity recognizes quiet piano tones rejected by the previous loudness cutoff', () => {
    const app = loadGame();
    for (const rate of [16000, 22050, 32000, 44100, 48000]) {
        for (const midi of [48, 60, 67, 72]) {
            const frequency = 440 * 2 ** ((midi - 69) / 12);
            const noise = noiseGenerator(midi + rate);
            app.context.input = Float32Array.from({length: 4096}, (_, i) => {
                const phase = 2 * Math.PI * frequency * i / rate;
                return 0.02 + 0.00065 * Math.sin(phase) + 0.0004 * Math.sin(2 * phase)
                    + 0.0002 * Math.sin(3 * phase) + 0.0001 * noise();
            });
            const result = app.run(`detectPitch(input,${rate},microphoneThreshold('high'))`);
            assert.ok(result.rms < 0.004, 'Signal is quieter than the old cutoff');
            assert.ok(result.frequency, `Quiet MIDI ${midi} at ${rate} Hz is recognized`);
            assert.ok(Math.abs(1200 * Math.log2(result.frequency / frequency)) < 12);
        }
    }
});

test('very high sensitivity supports weaker notes while retaining a silence floor', () => {
    const app = loadGame();
    app.context.input = Float32Array.from({length: 4096}, (_, i) =>
        0.00015 * Math.sin(2 * Math.PI * 261.625565 * i / 48000));
    assert.equal(app.run("detectPitch(input,48000,microphoneThreshold('high')).frequency"), null);
    const result = app.run("detectPitch(input,48000,microphoneThreshold('max'))");
    assert.ok(Math.abs(result.frequency - 261.625565) < 1);
    app.context.input = new Float32Array(4096);
    assert.equal(app.run("detectPitch(input,48000,microphoneThreshold('max')).frequency"), null);
    app.context.input.fill(0.2);
    assert.equal(app.run("detectPitch(input,48000,microphoneThreshold('max')).frequency"), null);
});

test('sensitivity does not treat random background hiss as a piano note', () => {
    const app = loadGame();
    for (const rate of [16000, 44100, 48000]) {
        for (const seed of [1, 17, 137, 997]) {
            const noise = noiseGenerator(seed);
            app.context.input = Float32Array.from({length: 4096}, () => noise() * 0.001);
            assert.equal(app.run(`detectPitch(input,${rate},microphoneThreshold('max')).frequency`), null);
        }
    }
});

test('quiet signed PCM from the native bridge registers a correct piano note', async () => {
    const bridge = fakeBridge();
    const app = loadGame(bridge);
    await app.run('startMic()');
    app.element('mode').value = 'wait';
    app.run("queue=[{m:60,clef:'treble',spell:spelling(60),at:1,result:null}];beat=1;lastTime=100;");
    bridge.packet = quietPianoPacket(1);
    app.run('analyze(200)');
    bridge.packet = quietPianoPacket(2);
    app.run('analyze(240)');
    assert.equal(app.run('correct'), 1);
    assert.equal(app.run('wrong + missed'), 0);
    assert.equal(app.element('status').textContent, 'Heard C3');
    assert.ok(Number.parseFloat(app.element('level').style.width) > 10);
    app.run('closeMic()');
});

test('a quiet repeated note can register a fresh attack without reaching the former loud attack floor', async () => {
    const bridge = fakeBridge();
    const app = loadGame(bridge);
    await app.run('startMic()');
    app.element('mode').value = 'wait';
    app.run("queue=[{m:60,clef:'treble',spell:spelling(60),at:1,result:null}];beat=1;lastTime=100;");
    bridge.packet = quietPianoPacket(1);
    app.run('analyze(200)');
    bridge.packet = quietPianoPacket(2);
    app.run('analyze(240)');
    assert.equal(app.run('correct'), 1);
    app.run("queue=[{m:60,clef:'treble',spell:spelling(60),at:1,result:null}];beat=1;");
    bridge.packet = quietPianoPacket(3, 0.00035);
    app.run('analyze(300)');
    assert.equal(app.run('correct'), 1);
    bridge.packet = quietPianoPacket(4, 0.001);
    app.run('analyze(420)');
    assert.equal(app.run('correct'), 2);
    app.run('closeMic()');
});

test('live sensitivity changes stay in sync and preserve the recording session and score', async () => {
    const bridge = fakeBridge();
    const app = loadGame(bridge);
    await app.run('startMic()');
    const stops = bridge.stops;
    app.run('correct=7;');
    app.element('micSensitivityQuick').value = 'max';
    app.element('micSensitivityQuick').onchange();
    assert.equal(app.element('micSensitivity').value, 'max');
    assert.equal(app.run('minimumMicRms()'), 0.00006);
    assert.equal(app.run('correct'), 7);
    assert.equal(app.run('mic'), true);
    assert.equal(bridge.stops, stops);
    app.element('micSensitivity').value = 'normal';
    app.element('micSensitivity').onchange();
    assert.equal(app.element('micSensitivityQuick').value, 'normal');
    app.run('closeMic()');
});

test('changing sensitivity allows the same weak native audio to be recognized', async () => {
    const bridge = fakeBridge();
    const app = loadGame(bridge);
    await app.run('startMic()');
    app.element('mode').value = 'wait';
    app.run("queue=[{m:60,clef:'treble',spell:spelling(60),at:1,result:null}];beat=1;lastTime=100;");
    app.run("setMicrophoneSensitivity('low')");
    bridge.packet = quietPianoPacket(1);
    app.run('analyze(200)');
    bridge.packet = quietPianoPacket(2);
    app.run('analyze(240)');
    assert.equal(app.run('correct'), 0);
    app.run("setMicrophoneSensitivity('high')");
    bridge.packet = quietPianoPacket(3);
    app.run('analyze(280)');
    bridge.packet = quietPianoPacket(4);
    app.run('analyze(320)');
    assert.equal(app.run('correct'), 1);
    app.run('closeMic()');
});

test('raising sensitivity while holding a note does not create another key press', async () => {
    const bridge = fakeBridge();
    const app = loadGame(bridge);
    await app.run('startMic()');
    app.element('mode').value = 'wait';
    app.run("queue=[{m:60,clef:'treble',spell:spelling(60),at:1,result:null}];beat=1;lastTime=100;");
    bridge.packet = quietPianoPacket(1);
    app.run('analyze(200)');
    bridge.packet = quietPianoPacket(2);
    app.run('analyze(240)');
    assert.equal(app.run('correct'), 1);
    app.run("queue=[{m:60,clef:'treble',spell:spelling(60),at:1,result:null}];beat=1;setMicrophoneSensitivity('max');");
    bridge.packet = quietPianoPacket(3);
    app.run('analyze(400)');
    bridge.packet = quietPianoPacket(4);
    app.run('analyze(440)');
    assert.equal(app.run('correct'), 1);
    assert.equal(app.run('wrong'), 0);
    app.run('closeMic()');
});

test('both hands shows a right-hand row above a separate left-hand row', () => {
    const app = loadGame();
    app.element('demo').onclick();
    app.element('gameHands').value = 'both';
    app.element('gameHands').onchange();
    assert.equal(app.run('hand'), 'both');
    assert.equal(app.element('leftPaper').hidden, false);
    assert.equal(app.element('handLabel').textContent, 'Right hand · Treble clef');
    assert.equal(app.element('leftHandLabel').textContent, 'Left hand · Bass clef');
    assert.match(app.element('staff').innerHTML, /id="notesClip-staff"/);
    assert.match(app.element('leftStaff').innerHTML, /id="notesClip-leftStaff"/);
    assert.equal(app.run("queue.every((note,index)=>note.clef===(index%2===0?'treble':'bass'))"), true);
    assert.ok(app.run("queue.some(note=>note.clef==='treble') && queue.some(note=>note.clef==='bass')"));
});

test('each practice row renders only its own hand and aligns the hit lines', () => {
    const app = loadGame();
    app.run("setPracticeHand('both');queue=[{m:60,clef:'treble',spell:spelling(60),at:1,result:null},{m:48,clef:'bass',spell:spelling(48),at:2,result:null}];beat=0;draw();");
    const right = app.element('staff').innerHTML;
    const left = app.element('leftStaff').innerHTML;
    assert.match(right, />C3<\/text>/);
    assert.doesNotMatch(right, />C2<\/text>/);
    assert.match(left, />C2<\/text>/);
    assert.doesNotMatch(left, />C3<\/text>/);
    const lineX = svg => Number(svg.match(/<line x1="([0-9.]+)" x2="[0-9.]+" y1="5"/)[1]);
    assert.equal(lineX(right), lineX(left));
    assert.equal(app.element('staff').viewBox, app.element('leftStaff').viewBox);
});

test('switching between right, left and both keeps the score and updates settings', () => {
    const app = loadGame();
    app.element('demo').onclick();
    app.run('correct=8;wrong=2;');
    app.run("setPracticeHand('both')");
    assert.equal(app.element('leftPaper').hidden, false);
    app.run("setPracticeHand('bass')");
    assert.equal(app.element('leftPaper').hidden, true);
    assert.equal(app.element('handLabel').textContent, 'Left hand · Bass clef');
    assert.equal(app.element('gameHands').value, 'bass');
    assert.equal(app.run("queue.every(note=>note.clef==='bass')"), true);
    assert.equal(app.document.querySelectorAll('[data-clef]').find(button=>button.dataset.clef==='bass')['aria-pressed'], 'true');
    app.run("setPracticeHand('treble')");
    assert.equal(app.element('leftPaper').hidden, true);
    assert.equal(app.element('handLabel').textContent, 'Right hand · Treble clef');
    assert.equal(app.run('correct'), 8);
    assert.equal(app.run('wrong'), 2);
});

test('both hand rows score their microphone notes in order without restarting audio', async () => {
    const bridge = fakeBridge();
    const app = loadGame(bridge);
    await app.run('startMic()');
    const stops = bridge.stops;
    app.run("setPracticeHand('both')");
    app.element('mode').value = 'wait';
    app.run("queue=[{m:60,clef:'treble',spell:spelling(60),at:1,result:null},{m:48,clef:'bass',spell:spelling(48),at:2,result:null}];beat=1;lastTime=100;");
    bridge.packet = quietPianoPacket(1, 0.001, 60);
    app.run('analyze(200)');
    bridge.packet = quietPianoPacket(2, 0.001, 60);
    app.run('analyze(240)');
    assert.equal(app.run('correct'), 1);
    assert.match(app.element('hintText').textContent, /Left: C2/);
    app.run('beat=2;');
    bridge.packet = quietPianoPacket(3, 0.001, 48);
    app.run('analyze(1100)');
    bridge.packet = quietPianoPacket(4, 0.001, 48);
    app.run('analyze(1140)');
    assert.equal(app.run('correct'), 2);
    assert.equal(app.run('wrong + missed'), 0);
    assert.equal(bridge.stops, stops);
    assert.equal(app.run('mic'), true);
    app.run('closeMic()');
});

test('live speed changes sync with settings and preserve both hand notes, scores and audio', async () => {
    const bridge = fakeBridge();
    const app = loadGame(bridge);
    await app.run('startMic()');
    app.run("setPracticeHand('both');correct=7;wrong=2;missed=1;streak=3;advanceTo(100);sounding=60;candidate=60;candidateCount=2;");
    const notes = app.run('queue.slice()');
    const timeline = app.run('JSON.stringify(queue)');
    const stops = bridge.stops;
    app.element('gameBpm').value = '180';
    app.element('gameBpm').oninput();
    assert.equal(app.run('bpm()'), 180);
    assert.equal(app.element('bpm').value, '180');
    assert.equal(app.element('bpmOut').textContent, '180 BPM');
    assert.equal(app.element('gameBpmOut').textContent, '180 BPM');
    assert.equal(app.run('JSON.stringify(queue)'), timeline);
    assert.ok(app.run('queue').every((note, index) => note === notes[index]));
    assert.equal(app.run('beat'), 0);
    assert.equal(app.run('JSON.stringify([correct,wrong,missed,streak])'), '[7,2,1,3]');
    assert.equal(app.run('hand'), 'both');
    assert.equal(app.element('leftPaper').hidden, false);
    assert.equal(app.run('game && mic && !paused'), true);
    assert.equal(app.run('sounding===60 && candidate===60 && candidateCount===2'), true);
    assert.equal(bridge.stops, stops);
    app.run('closeMic()');
});

test('tempo changes account for elapsed time at the old speed before using the new speed', () => {
    const app = loadGame();
    app.element('demo').onclick();
    app.run('advanceTo(100);setPracticeTempo(150,200);');
    assert.equal(app.run('beat'), 0.125);
    app.run('advanceTo(300)');
    assert.equal(app.run('beat'), 0.375);
    app.context.performance.now = () => 400;
    app.element('bpm').value = '30';
    app.element('bpm').oninput();
    assert.equal(app.element('gameBpm').value, '30');
    assert.equal(app.element('gameBpmOut').textContent, '30 BPM');
    assert.equal(app.run('beat'), 0.625);
    app.run('advanceTo(500)');
    assert.equal(app.run('beat'), 0.675);
});

test('changing speed while paused keeps the pause and resumes at the selected tempo', () => {
    const app = loadGame();
    app.element('demo').onclick();
    app.run('advanceTo(100);togglePause();setPracticeTempo(120,5000);');
    assert.equal(app.run('paused'), true);
    assert.equal(app.run('beat'), 0);
    assert.equal(app.run('lastTime'), null);
    assert.equal(app.element('gameBpm').value, '120');
    app.run('togglePause();advanceTo(10000);advanceTo(10100);');
    assert.equal(app.run('beat'), 0.2);
});

test('live speed changes preserve wait mode at the current note', () => {
    const app = loadGame();
    app.element('demo').onclick();
    app.element('mode').value = 'wait';
    app.run('beat=3;lastTime=100;setPracticeTempo(180,200);advanceTo(300);');
    assert.equal(app.run('beat'), 3);
    assert.equal(app.run('active().at'), 3);
    assert.equal(app.run('wrong+missed'), 0);
    app.run('play(active().m,300);');
    assert.equal(app.run('correct'), 1);
});

test('the wider window accepts early and late notes at slow, normal and fast speeds', () => {
    for (const [tempo, density, milliseconds] of [[30,1,500],[75,1,500],[75,2,260],[180,1,230],[180,2,110]]) {
        for (const source of ['test', 'mic']) {
            for (const direction of [-1, 1]) {
                const app = loadGame();
                app.element('density').value = String(density);
                app.run(`setPracticeTempo(${tempo});game=true;mic=${source === 'mic'};lastTime=100;nextBeat=100;
                    queue=[{m:60,clef:'treble',spell:spelling(60),at:4,result:null}];
                    beat=4+${direction * milliseconds}*bpm()/60000+inputDelayBeats();play(60,100,'${source}');`);
                assert.equal(app.run('correct'), 1, `${tempo} BPM, density ${density}, ${source}, ${direction * milliseconds} ms`);
                assert.equal(app.run('wrong+missed'), 0);
            }
        }
    }
});

test('notes outside the wider window still fail and a missed note cannot score later', () => {
    for (const [tempo, density, milliseconds] of [[30,1,610],[75,1,610],[180,2,150]]) {
        for (const direction of [-1, 1]) {
            const app = loadGame();
            app.element('density').value = String(density);
            app.run(`setPracticeTempo(${tempo});game=true;mic=true;lastTime=100;nextBeat=100;
                queue=[{m:60,clef:'treble',spell:spelling(60),at:4,result:null}];
                beat=4+${direction * milliseconds}*bpm()/60000+inputDelayBeats();play(60,100,'mic');`);
            assert.equal(app.run('correct'), 0);
            assert.equal(app.run('wrong'), 1);
            assert.equal(app.run('missed'), direction > 0 ? 1 : 0);
            app.run("play(60,100,'mic')");
            assert.equal(app.run('correct'), 0);
            assert.equal(app.run('missed'), direction > 0 ? 1 : 0);
        }
    }
});

test('a note stays eligible until the compensated late deadline and is missed only once', () => {
    const app = loadGame();
    app.run(`game=true;mic=true;lastTime=100;nextBeat=100;
        queue=[{m:60,clef:'treble',spell:spelling(60),at:4,result:null}];
        beat=4+0.74+inputDelayBeats();advanceTo(100);`);
    assert.equal(app.run('missed'), 0);
    app.run('beat=4+0.76+inputDelayBeats();advanceTo(100);advanceTo(100);');
    assert.equal(app.run('missed'), 1);
});

test('overlapping windows match the played pitch even when the other hand is closer', () => {
    const app = loadGame();
    app.run(`setPracticeHand('both');game=true;beat=3.6;lastTime=100;nextBeat=100;
        queue=[{m:60,clef:'treble',spell:spelling(60),at:3,result:null},
               {m:48,clef:'bass',spell:spelling(48),at:4,result:null}];play(60,100);`);
    assert.equal(app.run('correct'), 1);
    assert.equal(app.run('wrong+missed'), 0);
    assert.equal(app.run('queue[0].result'), 'correct');
    assert.equal(app.run('queue[1].result'), null);
    app.run('play(48,100);');
    assert.equal(app.run('correct'), 2);
    assert.equal(app.run('wrong+missed'), 0);
});

test('a wrong pitch counts as an input error and leaves the note available to play', () => {
    const app = loadGame();
    app.run(`game=true;beat=4;lastTime=100;nextBeat=100;
        queue=[{m:60,clef:'treble',spell:spelling(60),at:4,result:null}];play(62,100);`);
    assert.equal(app.run('correct'), 0);
    assert.equal(app.run('wrong'), 1);
    assert.equal(app.run('queue[0].result'), null);
    app.run('play(60,100);');
    assert.equal(app.run('correct'), 1);
    assert.equal(app.run('wrong'), 1);
    assert.equal(app.run('missed'), 0);
    assert.equal(app.run('queue[0].result'), 'correct');
});

test('pressing C again leaves the next D available, including overlapping windows in either hand', () => {
    for (const [tempo, density] of [[30,1],[75,1],[75,2],[180,2]]) {
        for (const clef of ['treble','bass']) {
            for (const source of ['test','mic']) {
                const app = loadGame();
                const c = clef === 'treble' ? 60 : 48;
                app.element('density').value = String(density);
                app.run(`setPracticeTempo(${tempo});game=true;mic=${source === 'mic'};lastTime=100;nextBeat=100;
                    queue=[{m:${c},clef:'${clef}',spell:spelling(${c}),at:4,result:null},
                           {m:${c+2},clef:'${clef}',spell:spelling(${c+2}),at:4+step(),result:null}];
                    beat=4+inputDelayBeats();play(${c},100,'${source}');`);
                assert.equal(app.run('correct'), 1);
                assert.equal(app.run('queue[1].result'), null);
                app.run(`beat=4+step()*0.8+inputDelayBeats();play(${c},100,'${source}');`);
                assert.equal(app.run('wrong'), 1);
                assert.equal(app.run('queue[1].result'), null);
                app.run(`beat=4+step()+inputDelayBeats();play(${c+2},100,'${source}');`);
                assert.equal(app.run('correct'), 2);
                assert.equal(app.run('missed'), 0);
            }
        }
    }
});

test('an early C cannot consume a different due note or its own future note', () => {
    const app = loadGame();
    app.run(`setPracticeHand('both');game=true;beat=4;lastTime=100;nextBeat=100;
        queue=[{m:48,clef:'bass',spell:spelling(48),at:4,result:null},
               {m:60,clef:'treble',spell:spelling(60),at:5,result:null}];play(60,100);`);
    assert.equal(app.run('correct'), 0);
    assert.equal(app.run('wrong'), 1);
    assert.equal(app.run('queue.every(n=>n.result===null)'), true);
    app.run('play(48,100);beat=5;play(60,100);');
    assert.equal(app.run('correct'), 2);
    assert.equal(app.run('missed'), 0);
});

test('a wrong input leaves overlapping notes to miss once at their own deadlines', () => {
    const app = loadGame();
    app.run(`game=true;beat=4.5;lastTime=100;nextBeat=100;
        queue=[{m:60,clef:'treble',spell:spelling(60),at:4,result:null},
               {m:62,clef:'treble',spell:spelling(62),at:5,result:null}];play(64,100);`);
    assert.equal(app.run('queue.every(n=>n.result===null)'), true);
    app.run('beat=4+windowBeats()+0.01;advanceTo(100);advanceTo(100);');
    assert.equal(app.run('missed'), 1);
    assert.equal(app.run('queue[1].result'), null);
    app.run('beat=5+windowBeats()+0.01;advanceTo(100);advanceTo(100);');
    assert.equal(app.run('missed'), 2);
    assert.equal(app.run('wrong'), 1);
});

test('a held microphone C growing louder is not another press on an upcoming D', async () => {
    const bridge = fakeBridge();
    const app = loadGame(bridge);
    await app.run('startMic()');
    app.run(`beat=4;lastTime=200;nextBeat=100;
        queue=[{m:60,clef:'treble',spell:spelling(60),at:4,result:null},
               {m:62,clef:'treble',spell:spelling(62),at:4.5,result:null}];`);
    for (const [sequence,time,amplitude] of [[1,200,0.001],[2,240,0.001],[3,360,0.002],[4,400,0.002]]) {
        bridge.packet = quietPianoPacket(sequence, amplitude);
        app.run(`advanceTo(${time});analyze(${time});`);
    }
    assert.equal(app.run('correct'), 1);
    assert.equal(app.run('wrong+missed'), 0);
    assert.equal(app.run('queue[1].result'), null);
    bridge.packet = quietPianoPacket(5, 0.002, 62);
    app.run('advanceTo(440);analyze(440);');
    bridge.packet = quietPianoPacket(6, 0.002, 62);
    app.run('advanceTo(480);analyze(480);');
    assert.equal(app.run('correct'), 2);
    assert.equal(app.run('wrong+missed'), 0);
    app.run('closeMic()');
});

test('notes near both edges of the drawn green area remain visible and score in either hand', () => {
    for (const width of [300,400,1028]) {
        for (const [tempo,density] of [[30,1],[75,1],[180,2]]) {
            for (const source of ['test','mic']) {
                for (const clef of ['treble','bass']) {
                    for (const direction of [-1,1]) {
                        const app = loadGame();
                        app.element('paper').clientWidth = width;
                        app.element('leftPaper').clientWidth = width;
                        app.element('density').value = String(density);
                        const pitch = clef === 'treble' ? 60 : 48;
                        app.run(`setPracticeHand('both');setPracticeTempo(${tempo});game=true;mic=${source === 'mic'};
                            lastTime=100;nextBeat=100;queue=[{m:${pitch},clef:'${clef}',spell:spelling(${pitch}),at:4,result:null}];
                            beat=4+${direction}*windowBeats()*0.99+inputDelayBeats();draw();`);
                        const svg = app.element(clef === 'treble' ? 'staff' : 'leftStaff').innerHTML;
                        const zone = svg.match(/data-hit-zone="true" x="([\d.]+)" y="5" width="([\d.]+)"/);
                        const clip = svg.match(/<clipPath[^>]+><rect x="([\d.]+)"/);
                        const note = svg.match(/<ellipse cx="([\d.]+)"/);
                        assert.ok(zone && clip && note, 'An eligible note must be drawn');
                        const x = Number(note[1]), start = Number(zone[1]), end = start + Number(zone[2]);
                        assert.ok(x >= start && x <= end, 'The note must be inside the green area');
                        assert.ok(x - 8 >= Number(clip[1]), 'The eligible notehead must not be clipped');
                        assert.ok(start > 55 && end < width, 'The hit area must leave room for the clef and stay on the staff');
                        app.run(`play(${pitch},100,'${source}');`);
                        assert.equal(app.run('correct'), 1);
                        assert.equal(app.run('wrong+missed'), 0);
                    }
                }
            }
        }
    }
});
