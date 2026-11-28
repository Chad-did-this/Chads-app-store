'use strict';
const $=id=>document.getElementById(id);
const NAMES=['C','C♯','D','D♯','E','F','F♯','G','G♯','A','A♯','B'];
const octaveOf=m=>Math.floor(m/12)-5+Number($('middleCLabel').value||3);
const nameOf=m=>NAMES[m%12]+octaveOf(m);
const CLEFS={"treble": "M314 801Q300 854 291.0 906.0Q282 958 282 1012Q282 1059 288.5 1100.5Q295 1142 307 1177Q320 1217 341.0 1252.5Q362 1288 385.5 1311.0Q409 1334 427 1334Q451 1334 493 1249Q514 1206 524.0 1156.0Q534 1106 534 1049Q534 978 515.0 907.5Q496 837 459.5 775.0Q423 713 372 666L407 498Q422 500 432.0 501.0Q442 502 447 502Q508 502 556.0 467.5Q604 433 632.5 377.0Q661 321 661 254Q661 177 621.5 115.5Q582 54 503 25Q508 8 532 -117Q538 -147 541.0 -164.5Q544 -182 545.0 -195.0Q546 -208 546 -225Q546 -275 521.5 -314.5Q497 -354 455.5 -376.0Q414 -398 363 -398Q311 -398 271.0 -378.5Q231 -359 208.0 -324.5Q185 -290 185 -245Q185 -197 211.5 -165.0Q238 -133 287 -133Q329 -133 355.5 -163.5Q382 -194 382 -236Q382 -272 357.0 -299.0Q332 -326 292 -326H282Q308 -365 364 -365Q433 -365 472.0 -320.0Q511 -275 511 -205Q511 -188 507.0 -159.5Q503 -131 493 -91Q483 -51 477.5 -25.0Q472 1 470 12Q436 2 390 2Q304 2 222 52Q142 102 96.0 184.0Q50 266 50 361Q50 451 91 530Q132 609 192.5 675.0Q253 741 314 801ZM341 826Q364 838 390.0 870.5Q416 903 440.0 945.0Q464 987 479.0 1029.5Q494 1072 494 1106Q494 1142 483.0 1163.0Q472 1184 445 1184Q421 1184 398.5 1162.0Q376 1140 358.5 1103.5Q341 1067 331.0 1022.0Q321 977 321 930Q321 898 327.5 872.0Q334 846 341 826ZM398 379Q371 373 347.0 353.5Q323 334 308.5 306.5Q294 279 294 248Q294 223 307.0 196.5Q320 170 339 154Q352 142 365 136Q380 129 380 123Q380 120 370 117Q332 126 301.5 151.0Q271 176 253.5 211.5Q236 247 236 287Q236 330 253.5 370.0Q271 410 302.5 442.0Q334 474 374 490L345 641Q229 547 174.5 456.5Q120 366 120 277Q120 212 154.0 156.0Q188 100 247.0 65.5Q306 31 380 31Q400 31 420.5 35.0Q441 39 464 45ZM495 55Q593 97 593 227Q593 270 571.0 305.5Q549 341 512.0 362.0Q475 383 429 383Z", "bass": "M50 123Q216 231 288 301Q336 348 373.0 408.5Q410 469 431.5 536.0Q453 603 453 669Q453 728 435.0 773.5Q417 819 383.0 845.0Q349 871 302 871Q284 871 264.5 867.0Q245 863 224 855Q181 839 158.0 813.5Q135 788 135 765Q135 756 143.0 752.0Q151 748 158 748Q168 748 183 752Q190 754 196.5 755.0Q203 756 210 756Q248 756 272.0 733.5Q296 711 296 674Q296 638 266.0 612.0Q236 586 195 586Q146 586 111.0 617.0Q76 648 76 697Q76 756 109.0 802.0Q142 848 198.5 874.0Q255 900 324 900Q400 900 460.0 867.0Q520 834 555.5 776.5Q591 719 591 646Q591 551 538 464Q511 419 476.5 379.0Q442 339 389.0 297.5Q336 256 256.0 208.0Q176 160 57 101ZM687 809Q710 809 726.0 793.0Q742 777 742 754Q742 731 726.0 715.5Q710 700 687 700Q664 700 648.0 715.5Q632 731 632 754Q632 777 648.0 793.0Q664 809 687 809ZM687 589Q710 589 726.0 573.0Q742 557 742 534Q742 511 726.0 495.5Q710 480 687 480Q664 480 648.0 495.5Q632 511 632 534Q632 557 648.0 573.0Q664 589 687 589Z"};
const naturalSteps={0:0,2:1,4:2,5:3,7:4,9:5,11:6};
const letters=['C','D','E','F','G','A','B'];
function spelling(m,flat=false){let pc=m%12,base=m,acc='';if(!(pc in naturalSteps)){base=m+(flat?1:-1);acc=flat?'♭':'♯';}let step=(Math.floor(base/12)-1)*7+naturalSteps[base%12];return {step,acc,label:letters[naturalSteps[base%12]]+acc+octaveOf(base)};}
const beginnerRoots = {treble: 60, bass: 48};
function poolFor(clef,range,acc) {
 const bounds=range==='beginner'?[beginnerRoots[clef],beginnerRoots[clef]+7]:{
  treble:{staff:[64,77],extended:[57,84]},bass:{staff:[43,57],extended:[36,64]}
 }[clef][range];
 const pool=[];
 for(let m=bounds[0];m<=bounds[1];m++)if(acc==='all'||m%12 in naturalSteps)pool.push(m);
 return pool;
}

let hand='treble', queue=[], nextBeat=0, beat=0, lastTime=null, game=false, paused=false, correct=0, wrong=0, missed=0, streak=0;
const nativeMicrophone = new NativeMicrophone();
let mic=false,starting=false,token=0;
let candidate=null,candidateCount=0,sounding=null,lastEvent=0,priorRms=0,quietFrames=0,pendingAttack=0,lastAnalysis=0;
let lastNote=null, hinting=false, nextHand='treble';
let currentTempo=Number($('bpm').value)||75;
const bpm=()=>currentTempo, step=()=>1/Number($('density').value), waitMode=()=>$('mode').value==='wait';
const windowBeats=()=>Math.min(step()*.44,bpm()/60*.35);
const inputDelayBeats=()=>mic&&!waitMode()?bpm()/60000*75:0;
const minimumMicRms=()=>microphoneThreshold($('micSensitivity').value||'high');
const lookahead=()=>Math.max(3.8,Math.min(7,($('paper').clientWidth-125)/55));
const active=()=>queue.find(n=>n.result===null);
function makeNote(at){
 const clef=hand==='both'?nextHand:hand;
 if(hand==='both')nextHand=nextHand==='treble'?'bass':'treble';
 const pool=poolFor(clef,$('range').value,$('acc').value).filter(m=>m!==lastNote);
 const m=pool[Math.floor(Math.random()*pool.length)];lastNote=m;
 return {m,clef,spell:spelling(m,Math.random()<.5),at,result:null};
}
function fill(){const limit=beat+step()*(lookahead()+2);while(nextBeat<=limit){queue.push(makeNote(nextBeat));nextBeat+=step();}}
function setTextIfChanged(id, value) {
 const element=$(id), text=String(value);
 if(element.textContent!==text)element.textContent=text;
}
function status(s){setTextIfChanged('status',s);if(!$('home').hidden)setTextIfChanged('homeStatus',s);}
function feedback(s,kind='') {
 setTextIfChanged('feedback',s);
 const className='feedback '+kind;
 if($('feedback').className!==className)$('feedback').className=className;
}
function stats(){for(const [id,v] of Object.entries({correct,wrong,missed,streak}))setTextIfChanged(id,v);}
function updateHint() {
 const n=active();
 $('hintText').hidden=!hinting;
 const label=hinting&&n?(hand==='both'?(n.clef==='treble'?'Right: ':'Left: '):'Next note: ')+n.spell.label:'';
 setTextIfChanged('hintText',label);
 setTextIfChanged('gameNoteNames',hinting?'Names: On':'Show names');
 $('gameNoteNames').setAttribute('aria-pressed',String(hinting));
}
function restart(clearScore=true){
 if(clearScore){correct=wrong=missed=streak=0;stats();}
 beat=0;lastTime=null;lastNote=null;nextHand='treble';queue=[];nextBeat=step()*3;hinting=$('hintToggle').checked;fill();draw();updateHint();feedback(game?'Get ready…':'Ready to play');renderKeys();
}
function advanceTo(time){
 if(!game||paused){lastTime=null;return;}
 if(lastTime===null){lastTime=time;return;}
 const dt=Math.max(0,Math.min(100,time-lastTime));lastTime=time;
 beat+=dt*bpm()/60000;
 if(waitMode()){const n=active();if(n&&beat>n.at)beat=n.at;}
 else {for(const n of queue){if(n.result===null&&beat-inputDelayBeats()>n.at+windowBeats()){n.result='missed';missed++;streak=0;feedback('Missed note','bad');stats();}}}
 queue=queue.filter(n=>n.result===null||n.at>beat-step()*2.3);fill();updateHint();
}
function play(m,time=performance.now(),source='test'){
 if(!game||paused)return;
 advanceTo(time);
 const playedAt=beat-(source==='mic'?inputDelayBeats():0);
 let n=null;
 if(waitMode()){const a=active();if(a&&Math.abs(a.at-beat)<windowBeats())n=a;}
 else {const candidates=queue.filter(x=>x.result===null&&Math.abs(x.at-playedAt)<=windowBeats());n=candidates.sort((a,b)=>Math.abs(a.at-playedAt)-Math.abs(b.at-playedAt))[0]||null;}
 if(!n){wrong++;streak=0;feedback('Too early or late · '+nameOf(m),'bad');}
 else if(n.m===m){n.result='correct';correct++;streak++;feedback('Correct · '+nameOf(m),'good');}
 else {wrong++;streak=0;if(!waitMode())n.result='wrong';feedback('Wrong · '+nameOf(m),'bad');}
 stats();updateHint();draw();
}
function drawPracticeRow(clef, svgId, paperId, labelId, instructionId) {
 const svg=$(svgId),w=Math.max(300,$(paperId).clientWidth||340);
 const left=8, hit=Math.min(115,Math.max(81,w*.23)), right=w-12, gap=14, scale=.056;
 const bottom=spelling(clef==='treble'?64:43).step;
 const positions=poolFor(clef,$('range').value,$('acc').value).flatMap(m=>[spelling(m).step-bottom,spelling(m,true).step-bottom]);
 const base=111+Math.max(0,Math.max(...positions)-12)*gap/2;
 const labelY=Math.max(base+65,base-Math.min(0,...positions)*gap/2+28);
 const h=labelY+16;
 const clipId='notesClip-'+svgId;
 svg.setAttribute('viewBox',`0 0 ${w} ${h}`);
 let s=`<defs><clipPath id="${clipId}"><rect x="${hit-32}" y="0" width="${w-hit+32}" height="${h}"/></clipPath></defs>`;
 {const c=clef,b=base;for(let i=0;i<5;i++)s+=`<line x1="${left}" x2="${right}" y1="${b-i*gap}" y2="${b-i*gap}" stroke="#333a42" stroke-width="1.25"/>`;s+=`<path d="${CLEFS[c]}" transform="translate(17 ${c==='treble'?b:b-6}) scale(${scale} ${-scale})" fill="#1d242b"/>`;}
 s+=`<rect x="${hit-12}" y="5" width="24" height="${h-10}" rx="6" fill="#23a66a" opacity=".11"/><line x1="${hit}" x2="${hit}" y1="5" y2="${h-5}" stroke="#168654" stroke-width="2.5" stroke-dasharray="5 4"/><path d="M${hit-5} 3h10l-5 7z" fill="#168654"/><g clip-path="url(#${clipId})">`;
 const space=(right-hit)/lookahead();
 for(const n of queue){if(n.clef!==clef)continue;const x=hit+(n.at-beat)/step()*space;if(x<hit-38||x>right+25)continue;
 const b=base,p=n.spell.step-bottom,y=b-p*gap/2;
 const color=n.result==='correct'?'#168654':n.result?'#c64c48':'#20252b';const alpha=n.result?.65:1;
 s+=`<g opacity="${alpha}">`;
 for(let j=-2;j>=p;j-=2)s+=`<line x1="${x-13}" x2="${x+13}" y1="${b-j*gap/2}" y2="${b-j*gap/2}" stroke="${color}" stroke-width="1.4"/>`;
 for(let j=10;j<=p;j+=2)s+=`<line x1="${x-13}" x2="${x+13}" y1="${b-j*gap/2}" y2="${b-j*gap/2}" stroke="${color}" stroke-width="1.4"/>`;
 if(n.spell.acc==='♯')s+=`<path d="M${x-22} ${y-11}v22 m5 -24v22 M${x-26} ${y-3}l13 -3 m-13 9l13 -3" stroke="${color}" stroke-width="1.6" fill="none"/>`;
 if(n.spell.acc==='♭')s+=`<path d="M${x-21} ${y-15}v24 Q${x-5} ${y-2} ${x-21} ${y-2}" stroke="${color}" stroke-width="1.7" fill="none"/>`;
 const up=p<4, stemX=x+(up?6:-6), subdivision=Number($('density').value), endY=y+(up?-35:35);
 s+=`<ellipse cx="${x}" cy="${y}" rx="7.5" ry="4.9" transform="rotate(-20 ${x} ${y})" fill="${color}"/><line x1="${stemX}" x2="${stemX}" y1="${y}" y2="${endY}" stroke="${color}" stroke-width="1.6"/>`;
 if(subdivision>=2)s+=`<path d="M${stemX} ${endY}q${up?14:13} ${up?12:-12} 4 ${up?23:-23}" fill="none" stroke="${color}" stroke-width="2.2"/>`;
 if(hinting){s+=`<text x="${x}" y="${labelY}" text-anchor="middle" fill="${color}" font-family="system-ui,sans-serif" font-size="13" font-weight="700">${n.spell.label}</text>`;}
 s+='</g>';
 }
 svg.innerHTML=s+'</g>';
 setTextIfChanged(labelId,clef==='treble'?'Right hand · Treble clef':'Left hand · Bass clef');
 setTextIfChanged(instructionId,waitMode()?'Notes stop at the line until you play correctly.':'Notes keep moving. Hit each one at the green line.');
 svg.setAttribute('aria-label',(clef==='treble'?'Right hand':'Left hand')+' scrolling notes and a green hit line');
}
function draw() {
 const both=hand==='both';
 $('leftPaper').hidden=!both;
 $('gameScreen').dataset.hands=hand;
 drawPracticeRow(both?'treble':hand,'staff','paper','handLabel','instruction');
 if(both)drawPracticeRow('bass','leftStaff','leftPaper','leftHandLabel','leftInstruction');
}
function renderKeys(){const cs=hand==='both'?['treble','bass']:[hand],notes=[...new Set(cs.flatMap(c=>poolFor(c,$('range').value,$('acc').value)))].sort((a,b)=>a-b);$('testkeys').replaceChildren(...notes.map(m=>{const b=document.createElement('button');b.textContent=nameOf(m);if(!(m%12 in naturalSteps))b.className='black';b.onclick=()=>play(m);return b;}));}
function resetTracking(){candidate=null;candidateCount=0;sounding=null;lastEvent=0;priorRms=0;quietFrames=0;pendingAttack=0;lastAnalysis=0;}
function analyze(time) {
 if(!mic||!game||paused||time-lastAnalysis<26)return;
 lastAnalysis=time;
 let frame;
 try {
  frame=nativeMicrophone.read(time);
 } catch(error) {
  audioInterrupted(error.message);
  return;
 }
 if(!frame)return;
 const {samples,sampleRate:rate}=frame;
 const minimumRms=minimumMicRms();
 const r=detectPitch(samples,rate,minimumRms);if(!r)return;
 const rms=r.rms;$('level').style.width=microphoneLevel(rms,minimumRms)+'%';
 if(!r.frequency){
  if(++quietFrames===2){
   candidate=null;candidateCount=0;sounding=null;pendingAttack=0;
   status(rms<minimumRms?'Listening · quiet input':'Listening · waiting for a clear piano note');
  }
  priorRms=rms;return;
 }
 quietFrames=0;const m=Math.round(69+12*Math.log2(r.frequency/440));if(m<21||m>108){priorRms=rms;return;}
 if(rms>Math.max(minimumRms*1.25,priorRms*1.65)&&time-lastEvent>85)pendingAttack=time;
 if(m===candidate)candidateCount++;else{candidate=m;candidateCount=1;}
 if(candidateCount>=2){const changed=m!==sounding,reattack=!changed&&pendingAttack>lastEvent&&time-pendingAttack<130&&time-lastEvent>85;if(changed||reattack){play(m,time,'mic');sounding=m;lastEvent=time;pendingAttack=0;status('Heard '+nameOf(m));}}
 priorRms=rms;
}
function tick(t){if(game&&!paused){advanceTo(t);analyze(t);draw();}requestAnimationFrame(tick);}
function begin(){showScreen('gameScreen');$('keyDetails').hidden=mic;$('micIndicator').hidden=!mic;game=true;paused=false;resetTracking();restart(true);$('pause').disabled=false;$('pause').textContent='Pause';}
function closeMic() {
 ++token;
 starting=false;
 mic=false;
 nativeMicrophone.stop();
 $('start').disabled=false;
 $('level').style.width='0';
 resetTracking();
}

async function startMic() {
 if(starting)return;
 if(mic){begin();return;}
 starting=true;
 const id=++token;
 $('start').disabled=true;
 status('Requesting microphone permission…');
 try {
  const ready=await nativeMicrophone.start(()=>id===token);
  if(!ready||id!==token)return;
  mic=true;
  begin();
  status('Microphone listening · play your piano');
 } catch(error) {
  if(id===token){closeMic();status('Microphone: '+error.message);}
 } finally {
  if(id===token){starting=false;$('start').disabled=false;}
 }
}
let settingsReturn='home';
function audioInterrupted(reason){game=false;paused=false;lastTime=null;closeMic();showScreen('home');status('Microphone: '+reason);}
function showScreen(id){for(const s of ['home','settings','gameScreen','pauseScreen','flashcardScreen','noteCardScreen','noteGuideScreen'])$(s).hidden=s!==id;if(id==='gameScreen'){fill();draw();}}
function togglePause(){if(!game)return;paused=!paused;lastTime=null;resetTracking();if(paused){$('level').style.width='0';showScreen('pauseScreen');}else{showScreen('gameScreen');feedback('Keep playing');}}
function openSettings(from){settingsReturn=from;showScreen('settings');}
$('openSettings').onclick=()=>openSettings('home');
$('pauseSettings').onclick=()=>openSettings('pauseScreen');
$('settingsBack').onclick=()=>showScreen(settingsReturn);
$('start').onclick=startMic;
$('demo').onclick=()=>{closeMic();begin();status('Test mode');};
$('pause').onclick=togglePause;
$('resume').onclick=togglePause;
$('reset').onclick=()=>{paused=false;showScreen('gameScreen');resetTracking();restart(true);};
$('quit').onclick=()=>{game=false;paused=false;lastTime=null;closeMic();$('homeStatus').textContent='';showScreen('home');};
$('hintToggle').onchange=()=>{hinting=$('hintToggle').checked;updateHint();draw();};
$('gameNoteNames').onclick=()=>{
 $('hintToggle').checked=!$('hintToggle').checked;
 $('hintToggle').onchange();
};
function setMicrophoneSensitivity(level) {
 if(!Object.prototype.hasOwnProperty.call(MIC_SENSITIVITY,level))return;
 $('micSensitivity').value=level;
 $('micSensitivityQuick').value=level;
}
$('micSensitivity').onchange=()=>setMicrophoneSensitivity($('micSensitivity').value);
$('micSensitivityQuick').onchange=()=>setMicrophoneSensitivity($('micSensitivityQuick').value);
function setPracticeTempo(value, time=performance.now()) {
 const numeric=Number(value);
 if(!Number.isFinite(numeric))return;
 const next=Math.max(30,Math.min(180,Math.round(numeric/5)*5));
 if(next!==currentTempo&&game&&!paused)advanceTo(time);
 currentTempo=next;
 $('bpm').value=String(next);
 $('gameBpm').value=String(next);
 setTextIfChanged('bpmOut',next+' BPM');
 setTextIfChanged('gameBpmOut',next+' BPM');
}
$('bpm').oninput=()=>setPracticeTempo($('bpm').value);
$('gameBpm').oninput=()=>setPracticeTempo($('gameBpm').value);
$('middleCLabel').onchange=()=>{
 for(const note of queue)note.spell=spelling(note.m,note.spell.acc==='♭');
 renderKeys();updateHint();draw();
 NoteStudy.refreshLabels();
};
function setPracticeHand(value) {
 if(!['treble','bass','both'].includes(value)||hand===value)return;
 hand=value;
 $('gameHands').value=hand;
 for(const button of document.querySelectorAll('[data-clef]'))button.setAttribute('aria-pressed',String(button.dataset.clef===hand));
 resetTracking();restart(false);
}
for(const button of document.querySelectorAll('[data-clef]'))button.onclick=()=>setPracticeHand(button.dataset.clef);
$('gameHands').onchange=()=>setPracticeHand($('gameHands').value);
for(const id of ['density','range','acc','mode'])$(id).onchange=()=>{resetTracking();restart(false);};
window.addEventListener('resize',()=>{fill();draw();});document.addEventListener('visibilitychange',()=>{
 if(!document.hidden)return;
 if(mic||starting)audioInterrupted('Recording stopped. Tap Play to restart.');
 else if(game&&!paused)togglePause();
});
window.addEventListener('pagehide',()=>{
 if(mic||starting)audioInterrupted('Recording stopped. Tap Play to restart.');
 else if(game&&!paused)togglePause();
});
restart();requestAnimationFrame(tick);
