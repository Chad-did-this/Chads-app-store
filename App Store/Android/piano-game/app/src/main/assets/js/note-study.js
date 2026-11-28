'use strict';

function studyNoteInfo(note) {
    const position = note.spell.step - spelling(note.clef === 'treble' ? 64 : 43).step;
    const letter = note.spell.label[0];
    const accidental = note.spell.acc === '♯' ? ' sharp' : note.spell.acc === '♭' ? ' flat' : '';
    const octave = Math.floor(note.m / 12) - 1;
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
        spoken: note.m === 60 ? 'Middle C · octave 4' : `${letter}${accidental} · octave ${octave}`,
        location,
        keyHint
    };
}

function studyStaff(note, answerVisible = false) {
    const info = studyNoteInfo(note);
    const bottom = 130;
    const gap = 18;
    const x = 185;
    const y = bottom - info.position * gap / 2;
    const clef = note.clef === 'treble' ? 'treble' : 'bass';
    const description = answerVisible ? `${info.label}. ${info.location}` : `One note in the ${clef} clef. What note is it?`;
    let markup = `<svg viewBox="0 0 320 210" role="img" aria-label="${description}" xmlns="http://www.w3.org/2000/svg">`;

    for (let line = 0; line < 5; line++) {
        const lineY = bottom - line * gap;
        markup += `<line x1="16" x2="304" y1="${lineY}" y2="${lineY}" stroke="#64707a" stroke-width="1.6"/>`;
    }
    markup += `<path d="${CLEFS[clef]}" transform="translate(25 ${clef === 'treble' ? bottom : bottom - 8}) scale(.072 -.072)" fill="#17202a"/>`;

    for (let step = -2; step >= info.position; step -= 2) {
        const ledgerY = bottom - step * gap / 2;
        markup += `<line x1="${x - 18}" x2="${x + 18}" y1="${ledgerY}" y2="${ledgerY}" stroke="#17202a" stroke-width="2"/>`;
    }
    for (let step = 10; step <= info.position; step += 2) {
        const ledgerY = bottom - step * gap / 2;
        markup += `<line x1="${x - 18}" x2="${x + 18}" y1="${ledgerY}" y2="${ledgerY}" stroke="#17202a" stroke-width="2"/>`;
    }
    if (note.spell.acc) {
        markup += `<text x="${x - 38}" y="${y + 10}" font-size="34" font-family="serif" fill="#17202a">${note.spell.acc}</text>`;
    }

    const up = info.position < 4;
    const stemX = x + (up ? 9 : -9);
    markup += `<ellipse cx="${x}" cy="${y}" rx="11" ry="7.5" transform="rotate(-20 ${x} ${y})" fill="#17202a"/>`;
    markup += `<line x1="${stemX}" x2="${stemX}" y1="${y}" y2="${y + (up ? -44 : 44)}" stroke="#17202a" stroke-width="2.2"/></svg>`;
    return markup;
}

function studyKeyboard(midi, label = nameOf(midi)) {
    const start = Math.floor(midi / 12) * 12;
    const whites = [0, 2, 4, 5, 7, 9, 11, 12];
    const blacks = [[1, 0], [3, 1], [6, 3], [8, 4], [10, 5]];
    let markup = `<svg viewBox="0 0 344 150" role="img" aria-label="Piano keys with ${label} highlighted in green" xmlns="http://www.w3.org/2000/svg">`;

    for (const [index, pitch] of whites.entries()) {
        const key = start + pitch;
        const active = key === midi;
        const x = index * 43;
        markup += `<rect data-midi="${key}" data-active="${active}" x="${x + 1}" y="1" width="41" height="147" rx="4" fill="${active ? '#9aebc8' : '#fffdf5'}" stroke="#283642" stroke-width="1.5"/>`;
        markup += `<text x="${x + 21.5}" y="130" text-anchor="middle" fill="#17202a" font-family="system-ui,sans-serif" font-size="17" font-weight="700">${nameOf(key)}</text>`;
    }
    for (const [pitch, leftWhite] of blacks) {
        const key = start + pitch;
        const active = key === midi;
        const x = (leftWhite + 1) * 43 - 13;
        markup += `<rect data-midi="${key}" data-active="${active}" x="${x}" y="1" width="26" height="87" rx="3" fill="${active ? '#9aebc8' : '#283642'}" stroke="#17202a"/>`;
        markup += `<text x="${x + 13}" y="72" text-anchor="middle" fill="${active ? '#17202a' : '#fffdf5'}" font-family="system-ui,sans-serif" font-size="10" font-weight="700">${active ? label : nameOf(key)}</text>`;
    }
    return markup + '</svg>';
}

const NoteStudy = (() => {
    let studyHand = 'treble';
    let studyRange = 'beginner';
    let accidentals = 'natural';
    let current = null;
    let revealed = false;
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
            studyHand = hand;
            studyRange = $('range').value;
            accidentals = $('acc').value;
        }
        if (from === 'home') {
            history = [];
            game = false;
            paused = false;
            closeMic();
        }
        history.push({screen: from, paused, current, revealed, studyHand, studyRange, accidentals});
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
        if (previous.revealed !== undefined) revealed = previous.revealed;
        if (previous.studyHand) studyHand = previous.studyHand;
        if (previous.studyRange) studyRange = previous.studyRange;
        if (previous.accidentals) accidentals = previous.accidentals;
        lastTime = null;
        resetTracking();
        syncFilters();
        showScreen(previous.screen);
        if (previous.screen === 'flashcardScreen') renderCard();
        if (previous.screen === 'noteGuideScreen') renderGuide();
    }

    function renderCard() {
        if (!current) return;
        const info = studyNoteInfo(current);
        $('flashClef').textContent = current.clef === 'treble' ? 'Right hand · Treble clef' : 'Left hand · Bass clef';
        $('flashStaff').innerHTML = studyStaff(current, revealed);
        $('flashAnswer').hidden = !revealed;
        $('flashPrompt').hidden = revealed;
        $('flashReveal').disabled = revealed;
        $('flashReveal').textContent = revealed ? 'Answer shown' : 'Reveal answer';
        $('flashReveal').className = revealed ? '' : 'primary';
        $('flashNext').className = revealed ? 'primary' : '';
        $('flashName').textContent = revealed ? info.label : '';
        $('flashWords').textContent = revealed ? info.spoken : '';
        $('flashPosition').textContent = revealed ? info.location : '';
        $('flashKeyboard').innerHTML = revealed ? studyKeyboard(current.m, info.label) : '';
        $('flashKeyHint').textContent = revealed ? info.keyHint : '';
    }

    function next() {
        let cards = deck();
        if (current) cards = cards.filter(note => note.m !== current.m || note.clef !== current.clef);
        current = cards[Math.floor(Math.random() * cards.length)];
        revealed = false;
        renderCard();
    }

    function reveal() {
        revealed = true;
        renderCard();
    }

    function renderGuide() {
        $('guideKeyboard').innerHTML = studyKeyboard(60);
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
                button.onclick = () => {
                    enter('flashcardScreen', 'noteGuideScreen');
                    current = note;
                    revealed = true;
                    renderCard();
                };
                return button;
            }));
            section.replaceChildren(heading, grid);
            sections.push(section);
        }
        $('guideNotes').replaceChildren(...sections);
    }

    function openFlashcards() {
        enter('flashcardScreen', 'home');
        next();
    }

    function openGuide(from) {
        enter('noteGuideScreen', from);
        renderGuide();
    }

    function filtersChanged() {
        syncFilters();
        if (!$('flashcardScreen').hidden) next();
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
    $('flashGuide').onclick = () => openGuide('flashcardScreen');
    $('flashBack').onclick = back;
    $('guideBack').onclick = back;
    $('flashReveal').onclick = reveal;
    $('flashNext').onclick = next;

    return {openFlashcards, openGuide, back};
})();
