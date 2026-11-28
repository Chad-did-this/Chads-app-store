from html.parser import HTMLParser
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


class LayoutResult(HTMLParser):
    def __init__(self):
        super().__init__()
        self.inside = False
        self.state = None
        self.parts = []

    def handle_starttag(self, tag, attributes):
        values = dict(attributes)
        if tag == "pre" and values.get("id") == "layout-result":
            self.inside = True
            self.state = values.get("data-state")

    def handle_endtag(self, tag):
        if tag == "pre":
            self.inside = False

    def handle_data(self, data):
        if self.inside:
            self.parts.append(data)


def fixture_html():
    assets = Path(__file__).resolve().parent.parent / "app/src/main/assets"
    page = (assets / "index.html").read_text()
    page = page.replace(
        '<link rel="stylesheet" href="css/game.css">',
        "<style>" + (assets / "css/game.css").read_text() + "</style>",
    )
    page = re.sub(
        r'<script src="([^"]+)"></script>',
        lambda match: "<script>" + (assets / match[1]).read_text() + "</script>",
        page,
    )
    page = page.replace("</head>", "<script>window.requestAnimationFrame = () => 0;</script></head>")
    return page


def main():
    browser = next((shutil.which(name) for name in (
        "google-chrome", "google-chrome-stable", "chromium", "chromium-browser"
    ) if shutil.which(name)), None)
    if browser is None:
        raise SystemExit("Chrome is required to check the rendered practice layout.")

    fixture = json.dumps(fixture_html()).replace("</", "<\\/")
    script = r"""
const fixture = FIXTURE;
const result = document.getElementById('layout-result');
const sizes = [[320,640],[342,740],[360,800],[412,915],[768,1024],[844,390]];
const messages = [
    ['Ready to play','Ready'],
    ['Correct · C3','Heard C3'],
    ['Wrong · C♯3','Heard C♯3'],
    ['Too early or late · D♭3','Listening · waiting for a clear piano note'],
    ['Missed note','Microphone listening · play your piano'],
    ['Get ready…','Listening · quiet input']
];
const anchors = ['gameScreen','practiceRows','gameHands','gameBpm','gameBpmOut','paper','gameFooter','feedback','hintSlot','micIndicator','status','micSensitivityQuick'];
function snapshot(doc) {
    const visibleAnchors = doc.getElementById('leftPaper').hidden ? anchors : [...anchors,'leftPaper'];
    return Object.fromEntries(visibleAnchors.map(id => {
        const rect = doc.getElementById(id).getBoundingClientRect();
        return [id, [rect.x, rect.y, rect.width, rect.height]];
    }));
}
(async () => {
    try {
        let checks = 0;
        for (const [width,height] of sizes) {
            const frame = document.createElement('iframe');
            frame.style.cssText = `display:block;border:0;width:${width}px;height:${height}px;`;
            const loaded = new Promise(resolve => frame.addEventListener('load',resolve,{once:true}));
            frame.srcdoc = fixture;
            document.body.append(frame);
            await loaded;
            const doc = frame.contentDocument;
            doc.getElementById('demo').click();
            doc.getElementById('micIndicator').hidden = false;
            doc.getElementById('keyDetails').hidden = true;
            for (const practiceHand of ['treble','bass','both']) {
            const handControl = doc.getElementById('gameHands');
            handControl.value = practiceHand;
            handControl.dispatchEvent(new frame.contentWindow.Event('change',{bubbles:true}));
            const left = doc.getElementById('leftPaper');
            if (left.hidden !== (practiceHand !== 'both')) throw new Error('Incorrect hand row visibility');
            if (practiceHand === 'both') {
                const rightRect = doc.getElementById('paper').getBoundingClientRect();
                const leftRect = left.getBoundingClientRect();
                if (leftRect.top < rightRect.bottom || Math.abs(leftRect.width-rightRect.width)>0.5) {
                    throw new Error('Hand rows are not stacked with matching widths');
                }
                const rightHit = doc.querySelector('#staff line[stroke-dasharray]').getBoundingClientRect();
                const leftHit = doc.querySelector('#leftStaff line[stroke-dasharray]').getBoundingClientRect();
                if (Math.abs(rightHit.x-leftHit.x)>0.5) throw new Error('Hand hit lines are misaligned');
            }
            doc.scrollingElement.scrollTop = Math.min(40, doc.scrollingElement.scrollHeight - height);
            const initial = snapshot(doc);
            const scrollTop = doc.scrollingElement.scrollTop;
            const scrollHeight = doc.scrollingElement.scrollHeight;
            for (let pass=0;pass<3;pass++) {
                const speed = [30,75,180][pass];
                const speedControl = doc.getElementById('gameBpm');
                speedControl.value = String(speed);
                speedControl.dispatchEvent(new frame.contentWindow.Event('input',{bubbles:true}));
                if (doc.getElementById('bpm').value !== String(speed) || doc.getElementById('gameBpmOut').textContent !== `${speed} BPM`) {
                    throw new Error('Live speed control did not sync with Settings');
                }
                const showNames = pass !== 1;
                if (doc.getElementById('hintToggle').checked !== showNames) doc.getElementById('gameNoteNames').click();
                for (const [feedback,status] of messages) {
                    doc.getElementById('feedback').textContent = feedback;
                    doc.getElementById('status').textContent = status;
                    doc.getElementById('hintText').textContent = pass === 2 ? 'Next note: C♯3' : 'Next note: C3';
                    doc.getElementById('micSensitivityQuick').value = ['high','max','low'][pass];
                    for (const id of ['correct','wrong','missed','streak']) {
                        doc.getElementById(id).textContent = pass === 2 ? '9999' : '0';
                    }
                    const current = snapshot(doc);
                    for (const id of Object.keys(initial)) {
                        current[id].forEach((value,index) => {
                            if (Math.abs(value - initial[id][index]) > 0.5) {
                                throw new Error(`${width}x${height}: ${id} moved or resized after ${JSON.stringify(status)}: ${initial[id]} -> ${current[id]}`);
                            }
                        });
                    }
                    if (doc.scrollingElement.scrollHeight !== scrollHeight || Math.abs(doc.scrollingElement.scrollTop-scrollTop)>0.5) {
                        throw new Error(`${width}x${height}: document height or scroll position changed`);
                    }
                    checks++;
                }
            }
            }
            frame.remove();
        }
        result.dataset.state = 'passed';
        result.textContent = JSON.stringify({checks,viewports:sizes.length,handModes:3,layout:'stable'});
    } catch (error) {
        result.dataset.state = 'failed';
        result.textContent = error.stack;
    }
})();
""".replace("FIXTURE", fixture, 1)
    page = '<!doctype html><html><body><pre id="layout-result" data-state="pending"></pre><script>' + script + "</script></body></html>"

    with tempfile.TemporaryDirectory(prefix="piano-layout-") as directory:
        path = Path(directory) / "check.html"
        path.write_text(page)
        run = subprocess.run([
            browser, "--headless", "--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage",
            "--no-first-run", "--no-default-browser-check", "--hide-scrollbars",
            "--allow-file-access-from-files", "--virtual-time-budget=5000", "--dump-dom",
            "--user-data-dir=" + str(Path(directory) / "profile"), path.as_uri(),
        ], capture_output=True, text=True, timeout=45)

    parsed = LayoutResult()
    parsed.feed(run.stdout)
    message = "".join(parsed.parts)
    if run.returncode != 0 or parsed.state != "passed":
        raise SystemExit(message or run.stderr[-3000:] or "Browser layout check did not complete.")
    print("Practice layout checks passed:", message)


if __name__ == "__main__":
    main()
