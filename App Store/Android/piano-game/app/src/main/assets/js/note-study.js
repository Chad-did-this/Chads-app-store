'use strict';

function studyNoteInfo(note) {
    const position = note.spell.step - spelling(note.clef === 'treble' ? 64 : 43).step;
    const letter = note.spell.label[0];
    const accidental = note.spell.acc === '♯' ? ' sharp' : note.spell.acc === '♭' ? ' flat' : '';
    const octave = octaveOf(note.m);
    let location;

    if (position < 0 || position > 8) {
        const side = position < 0 ? 'below' : 'above';
        location = position % 2 === 0
            ? `On a ledger line ${side} the staff.`
            : `In a space ${side} the staff.`;
    } else if (position % 2 === 0) {
        location = `On the ${['bottom', 'second', 'middle', 'fourth', 'top'][position / 2]} line.`;
    } else {
        location = `In the ${['bottom', 'second', 'third', 'top'][(position - 1) / 2]} space.`;
    }

    const naturalHints = {
        C: 'C is the white key just to the left of two black keys.',
        D: 'D is the white key between the two black keys.',
        E: 'E is the white key just to the right of two black keys.',
        F: 'F is the white key just to the left of three black keys.',
        G: 'G is the white key between the first and second of three black keys.',
        A: 'A is the white key between the second and third of three black keys.',
        B: 'B is the white key just to the right of three black keys.'
    };
    let keyHint = naturalHints[letter];
    if (accidental) {
        const left = NAMES[(note.m - 1) % 12];
        const right = NAMES[(note.m + 1) % 12];
        keyHint = `The black key between ${left} and ${right}. ${left} sharp and ${right} flat are the same piano key.`;
    }

    return {
        position,
        label: note.spell.label,
        spoken: note.m === 60 ? `Middle C · octave ${octave}` : `${letter}${accidental} · octave ${octave}`,
        location,
        keyHint
    };
}

function studyNoteDrawing(note, x, bottom, gap, ink = '#17202a') {
    const position = studyNoteInfo(note).position;
    const scale = gap / 18;
    const y = bottom - position * gap / 2;
    const stroke = Math.max(1, 2 * scale);
    let markup = '';
    for (let step = -2; step >= position; step -= 2) {
        const ledgerY = bottom - step * gap / 2;
        markup += `<line data-ledger="below" x1="${x - 18 * scale}" x2="${x + 18 * scale}" y1="${ledgerY}" y2="${ledgerY}" stroke="${ink}" stroke-width="${stroke}"/>`;
    }
    for (let step = 10; step <= position; step += 2) {
        const ledgerY = bottom - step * gap / 2;
        markup += `<line data-ledger="above" x1="${x - 18 * scale}" x2="${x + 18 * scale}" y1="${ledgerY}" y2="${ledgerY}" stroke="${ink}" stroke-width="${stroke}"/>`;
    }
    if (note.spell.acc) {
        markup += `<text x="${x - 38 * scale}" y="${y + 10 * scale}" font-size="${34 * scale}" font-family="serif" fill="${ink}">${note.spell.acc}</text>`;
    }
    const up = position < 4;
    const stemX = x + (up ? 9 : -9) * scale;
    markup += `<ellipse data-midi="${note.m}" cx="${x}" cy="${y}" rx="${11 * scale}" ry="${7.5 * scale}" transform="rotate(-20 ${x} ${y})" fill="${ink}"/>`;
    return markup + `<line x1="${stemX}" x2="${stemX}" y1="${y}" y2="${y + (up ? -44 : 44) * scale}" stroke="${ink}" stroke-width="${stroke}"/>`;
}

function studyStaffScene(notes, labels, answerVisible) {
    const width = notes.length > 1 ? 560 : 320;
    const positions = notes.map(note => studyNoteInfo(note).position);
    const bottom = 36 + Math.max(8, ...positions) * 9;
    const lowest = bottom - Math.min(0, ...positions) * 9;
    const labelY = lowest + 30;
    const height = Math.max(bottom + 42, labels ? labelY + 14 : lowest + 35);
    const clef = notes[0].clef;
    const description = answerVisible
        ? notes.map(note => note.spell.label).join(', ') + ` in the ${clef} clef.`
        : `One note in the ${clef} clef.`;
    let markup = `<svg viewBox="0 0 ${width} ${height}" role="img" aria-label="${description}" xmlns="http://www.w3.org/2000/svg">`;
    for (let line = 0; line < 5; line++) {
        const lineY = bottom - line * 18;
        markup += `<line x1="16" x2="${width - 16}" y1="${lineY}" y2="${lineY}" stroke="#64707a" stroke-width="1.6"/>`;
    }
    markup += `<path d="${CLEFS[clef]}" transform="translate(25 ${clef === 'treble' ? bottom : bottom - 8}) scale(.072 -.072)" fill="#17202a"/>`;
    notes.forEach((note, index) => {
        const x = notes.length > 1 ? 140 + index * 90 : 185;
        markup += studyNoteDrawing(note, x, bottom, 18);
        if (labels) {
            markup += `<text x="${x}" y="${labelY}" text-anchor="middle" fill="#126240" font-family="system-ui,sans-serif" font-size="25" font-weight="700">${note.spell.label}</text>`;
        }
    });
    return markup + '</svg>';
}

function studyStaff(note, answerVisible = true) {
    return studyStaffScene([note], false, answerVisible);
}

function studyStaffRow(notes) {
    return studyStaffScene(notes, true, true);
}

function studyKeyNotation(midi, clef, x, y, width, height, ink, flat = false) {
    const note = {m: midi, clef, spell: spelling(midi, flat)};
    const position = studyNoteInfo(note).position;
    const gap = 6;
    const bottom = 12 + Math.max(8, position) * gap / 2;
    const lowest = bottom - Math.min(0, position) * gap / 2;
    const drawingHeight = Math.max(bottom + 10, lowest + 10);
    const scale = Math.min(width / 32, height / drawingHeight);
    const offsetX = x + (width - 32 * scale) / 2;
    const offsetY = y + (height - drawingHeight * scale) / 2;
    let markup = `<g data-key-note="${midi}" data-clef="${clef}" transform="translate(${offsetX} ${offsetY}) scale(${scale})">`;
    for (let line = 0; line < 5; line++) {
        const lineY = bottom - line * gap;
        markup += `<line x1="2" x2="30" y1="${lineY}" y2="${lineY}" stroke="${ink}" opacity=".35" stroke-width=".9"/>`;
    }
    return markup + studyNoteDrawing(note, 16, bottom, gap, ink) + '</g>';
}

function studyKeyboard(midi, label = nameOf(midi), selected = [midi], clef = 'treble') {
    const start = Math.floor(midi / 12) * 12;
    const whites = [0, 2, 4, 5, 7, 9, 11, 12];
    const blacks = [[1, 0], [3, 1], [6, 3], [8, 4], [10, 5]];
    let markup = `<svg viewBox="0 0 344 228" role="group" aria-label="Piano keys with their written notes. ${label} highlighted in green. Tap a key to enlarge its note." xmlns="http://www.w3.org/2000/svg">`;
    for (const [index, pitch] of whites.entries()) {
        const key = start + pitch;
        const active = selected.includes(key);
        const x = index * 43;
        markup += `<g class="study-piano-key" data-key-midi="${key}" role="button" tabindex="0" aria-label="${nameOf(key)}: enlarge this note">`;
        markup += `<rect data-midi="${key}" data-active="${active}" x="${x + 1}" y="1" width="41" height="224" rx="4" fill="${active ? '#9aebc8' : '#fffdf5'}" stroke="#283642" stroke-width="1.5"/>`;
        markup += studyKeyNotation(key, clef, x + 4, 113, 35, 82, '#17202a');
        markup += `<text x="${x + 21.5}" y="213" text-anchor="middle" fill="#17202a" font-family="system-ui,sans-serif" font-size="17" font-weight="700">${nameOf(key)}</text></g>`;
    }
    for (const [pitch, leftWhite] of blacks) {
        const key = start + pitch;
        const active = selected.includes(key);
        const x = (leftWhite + 1) * 43 - 13;
        const ink = active ? '#17202a' : '#fffdf5';
        const flat = key === midi && label.includes('♭');
        const keyLabel = key === midi ? label : nameOf(key);
        markup += `<g class="study-piano-key" data-key-midi="${key}" role="button" tabindex="0" aria-label="${keyLabel}: enlarge this note">`;
        markup += `<rect data-midi="${key}" data-active="${active}" x="${x}" y="1" width="26" height="102" rx="3" fill="${active ? '#9aebc8' : '#283642'}" stroke="#17202a"/>`;
        markup += studyKeyNotation(key, clef, x + 2.5, 8, 21, 66, ink, flat);
        markup += `<text x="${x + 13}" y="92" text-anchor="middle" fill="${ink}" font-family="system-ui,sans-serif" font-size="10" font-weight="700">${keyLabel}</text></g>`;
    }
    return markup + '</svg>';
}

const NoteStudy = (() => {
    let studyHand = 'both';
    let studyRange = 'beginner';
    let accidentals = 'natural';
    let current = null;
    let history = [];

    function deck() {
        const clefs = studyHand === 'both' ? ['treble', 'bass'] : [studyHand];
        return clefs.flatMap(clef => poolFor(clef, studyRange, accidentals).flatMap(m => {
            const notes = [{m, clef, spell: spelling(m)}];
            if (!(m % 12 in naturalSteps)) notes.push({m, clef, spell: spelling(m, true)});
            return notes;
        }));
    }

    function syncFilters() {
        for (const button of document.querySelectorAll('[data-study-hand]')) {
            button.setAttribute('aria-pressed', String(button.dataset.studyHand === studyHand));
        }
        for (const select of document.querySelectorAll('[data-study-range]')) select.value = studyRange;
        for (const input of document.querySelectorAll('[data-study-acc]')) input.checked = accidentals === 'all';
    }

    function enter(screen, from) {
        if (['home', 'gameScreen', 'pauseScreen'].includes(from)) {
            studyHand = from === 'home' ? 'both' : hand;
            studyRange = $('range').value;
            accidentals = $('acc').value;
        }
        if (from === 'home') {
            history = [];
            game = false;
            paused = false;
            closeMic();
        }
        history.push({screen: from, paused, current, studyHand, studyRange, accidentals});
        if (game) {
            paused = true;
            lastTime = null;
            resetTracking();
        }
        syncFilters();
        showScreen(screen);
    }

    function back() {
        const previous = history.pop() || {screen: 'home', paused: false};
        paused = previous.paused;
        if (previous.current) current = previous.current;
        if (previous.studyHand) studyHand = previous.studyHand;
        if (previous.studyRange) studyRange = previous.studyRange;
        if (previous.accidentals) accidentals = previous.accidentals;
        lastTime = null;
        resetTracking();
        syncFilters();
        showScreen(previous.screen);
        if (previous.screen === 'flashcardScreen') renderOverview();
        if (previous.screen === 'noteCardScreen') renderCard();
        if (previous.screen === 'noteGuideScreen') renderGuide();
    }

    function openCard(note, from) {
        enter('noteCardScreen', from);
        current = note;
        renderCard();
    }

    function bindKeyboard(container, clef, from) {
        function activate(event) {
            if (event.type === 'keydown' && !['Enter', ' '].includes(event.key)) return;
            const key = event.target.closest('[data-key-midi]');
            if (!key) return;
            const midi = Number(key.dataset.keyMidi);
            if (!Number.isInteger(midi) || midi < 21 || midi > 108) return;
            if (event.preventDefault) event.preventDefault();
            const note = {m: midi, clef, spell: spelling(midi)};
            if (from === 'noteCardScreen') {
                current = note;
                renderCard();
            } else {
                openCard(note, from);
            }
        }
        container.onclick = activate;
        container.onkeydown = activate;
    }

    function refreshLabels() {
        if (current) current.spell = spelling(current.m, current.spell.acc === '♭');
        if (!$('flashcardScreen').hidden) renderOverview();
        if (!$('noteCardScreen').hidden) renderCard();
        if (!$('noteGuideScreen').hidden) renderGuide();
    }

    function renderCard() {
        if (!current) return;
        const info = studyNoteInfo(current);
        const name = current.clef === 'treble' ? 'Right hand' : 'Left hand';
        $('noteCardTitle').textContent = `${name} · ${info.label}`;
        $('flashClef').textContent = name;
        $('flashStaff').innerHTML = studyStaff(current, true);
        $('flashAnswer').hidden = false;
        $('flashName').textContent = info.label;
        $('flashWords').textContent = info.spoken;
        $('flashPosition').textContent = info.location;
        $('flashKeyboard').innerHTML = studyKeyboard(current.m, info.label, [current.m], current.clef);
        bindKeyboard($('flashKeyboard'), current.clef, 'noteCardScreen');
        $('flashKeyHint').textContent = info.keyHint;
    }

    function renderOverview() {
        $('flashIntro').textContent = `Each piano key shows its written note. Middle C is labeled ${nameOf(60)}. Tap a key to enlarge it.`;
        const sections = ['treble', 'bass'].map(clef => {
            const name = clef === 'treble' ? 'Right hand' : 'Left hand';
            const root = beginnerRoots[clef];
            const notes = [0, 2, 4, 5, 7].map(offset => ({m: root + offset, clef, spell: spelling(root + offset)}));
            const section = document.createElement('section');
            section.className = `hand-board ${clef === 'treble' ? 'right-hand' : 'left-hand'}`;
            section.dataset.hand = clef;

            const header = document.createElement('div');
            header.className = 'hand-board-header';
            const heading = document.createElement('h3');
            heading.textContent = name;
            const control = document.createElement('label');
            control.className = 'hand-start';
            const caption = document.createElement('span');
            caption.textContent = 'Starting C';
            const select = document.createElement('select');
            select.setAttribute('aria-label', `${name} starting C`);
            select.replaceChildren(...[36, 48, 60, 72].map(m => {
                const option = document.createElement('option');
                option.value = String(m);
                option.textContent = nameOf(m);
                return option;
            }));
            select.value = String(root);
            select.onchange = () => {
                const value = Number(select.value);
                if (![36, 48, 60, 72].includes(value)) return;
                beginnerRoots[clef] = value;
                renderOverview();
            };
            control.replaceChildren(caption, select);
            header.replaceChildren(heading, control);

            const names = document.createElement('div');
            names.className = 'hand-note-buttons';
            names.replaceChildren(...notes.map(note => {
                const button = document.createElement('button');
                button.type = 'button';
                button.setAttribute('aria-label', `${name}: ${note.spell.label}`);
                button.dataset.midi = String(note.m);
                button.innerHTML = `<strong>${note.spell.label[0]}</strong><span>${note.spell.label}</span>`;
                button.onclick = () => openCard(note, 'flashcardScreen');
                return button;
            }));
            const staff = document.createElement('div');
            staff.className = 'hand-note-row';
            staff.innerHTML = studyStaffRow(notes);
            const keyboard = document.createElement('div');
            keyboard.className = 'hand-lesson-keys';
            keyboard.innerHTML = studyKeyboard(root, nameOf(root), notes.map(note => note.m), clef);
            bindKeyboard(keyboard, clef, 'flashcardScreen');
            section.replaceChildren(header, names, staff, keyboard);
            return section;
        });
        $('handCards').replaceChildren(...sections);
    }

    function renderGuide() {
        $('guideKeyboard').innerHTML = studyKeyboard(beginnerRoots.treble);
        bindKeyboard($('guideKeyboard'), 'treble', 'noteGuideScreen');
        $('guideMiddleC').textContent = `Middle C is called ${nameOf(60)} here. Its note sits on a short line below the right-hand staff. You can change the C3/C4 naming in Settings.`;
        const cards = deck();
        const sections = [];
        for (const clef of ['treble', 'bass']) {
            const notes = cards.filter(note => note.clef === clef);
            if (!notes.length) continue;
            const section = document.createElement('section');
            section.className = 'guide-clef';
            const heading = document.createElement('h3');
            heading.textContent = clef === 'treble' ? 'Right hand · Treble clef' : 'Left hand · Bass clef';
            const grid = document.createElement('div');
            grid.className = 'note-grid';
            grid.replaceChildren(...notes.map(note => {
                const button = document.createElement('button');
                button.className = 'note-tile';
                button.type = 'button';
                button.setAttribute('aria-label', `${note.spell.label}: see this piano key`);
                button.innerHTML = studyStaff(note, true) + `<strong>${note.spell.label}</strong><span>See piano key</span>`;
                button.onclick = () => openCard(note, 'noteGuideScreen');
                return button;
            }));
            section.replaceChildren(heading, grid);
            sections.push(section);
        }
        $('guideNotes').replaceChildren(...sections);
    }

    function openFlashcards() {
        enter('flashcardScreen', 'home');
        renderOverview();
    }

    function openGuide(from) {
        enter('noteGuideScreen', from);
        renderGuide();
    }

    function filtersChanged() {
        syncFilters();
        if (!$('noteGuideScreen').hidden) renderGuide();
    }

    for (const button of document.querySelectorAll('[data-study-hand]')) {
        button.onclick = () => {
            studyHand = button.dataset.studyHand;
            filtersChanged();
        };
    }
    for (const select of document.querySelectorAll('[data-study-range]')) {
        select.onchange = () => {
            studyRange = select.value;
            filtersChanged();
        };
    }
    for (const input of document.querySelectorAll('[data-study-acc]')) {
        input.onchange = () => {
            accidentals = input.checked ? 'all' : 'natural';
            filtersChanged();
        };
    }

    $('openFlashcards').onclick = openFlashcards;
    $('openNoteGuide').onclick = () => openGuide('home');
    $('gameNoteGuide').onclick = () => openGuide('gameScreen');
    $('pauseNoteGuide').onclick = () => openGuide('pauseScreen');
    $('flashOverviewBack').onclick = back;
    $('flashBack').onclick = back;
    $('guideBack').onclick = back;

    return {openFlashcards, openGuide, back, refreshLabels};
})();
