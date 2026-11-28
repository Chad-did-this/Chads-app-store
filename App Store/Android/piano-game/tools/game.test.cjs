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
    assert.deepEqual(hands[0].children[1].children.map(button => Number(button.dataset.midi)), [48, 50, 52, 53, 55]);
    assert.deepEqual(hands[1].children[1].children.map(button => Number(button.dataset.midi)), [36, 38, 40, 41, 43]);
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
    assert.equal(rightSelect.value, '48');
    rightSelect.value = '60';
    rightSelect.onchange();
    const hands = app.element('handCards').children;
    assert.match(hands[0].children[1].children[0].innerHTML, /C4/);
    assert.match(hands[1].children[1].children[0].innerHTML, /C2/);
    assert.equal(app.run("nameOf(poolFor('treble','beginner','natural')[0])"), 'C4');
    assert.equal(app.run("nameOf(poolFor('bass','beginner','natural')[0])"), 'C2');
    app.element('flashOverviewBack').onclick();
    app.element('demo').onclick();
    assert.equal(app.run('queue.every(note=>[60,62,64,65,67].includes(note.m))'), true);
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
    assert.equal(app.run("studyNoteInfo({m:60,clef:'treble',spell:spelling(60)}).spoken"), 'Middle C · octave 4');
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
    assert.equal(sharp.label, 'C♯4');
    assert.equal(flat.label, 'D♭4');
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
    assert.match(app.element('flashKeyboard').innerHTML, /data-midi="48" data-active="true"/);
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
