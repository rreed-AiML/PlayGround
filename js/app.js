/* Automa — Automation Studio (playable web demo)
   Implements: macro record/playback+save/edit, internal scripting,
   recognition (text/object/image/template) + tracking, draw-on-top,
   custom keyboard with mic, clipboard, simulated phone screen with apps.
*/

const STORAGE_KEY = 'automa_v1';
const SCREEN_W = 375;
const SCREEN_H = 812;

const state = {
  currentApp: 'home',
  settings: { wifi: true, bluetooth: false, accessibility: false, dark: false },
  notesText: '',
  clipboard: JSON.parse(localStorage.getItem(STORAGE_KEY + '.clipboard') || '[]'),
  macros: JSON.parse(localStorage.getItem(STORAGE_KEY + '.macros') || '[]'),
  selectedMacroId: null,
  recording: false,
  recordingSteps: [],
  playing: false,
  focusedId: null,
  focusedValue: '',
  overlayOn: false,
  drawMode: false,
  drawColor: '#ff4757',
  drawSize: 6,
  kbVisible: false,
  kbShift: false,
  kbLayout: 'en',
  micOn: false,
  recognition: null,
  trackingId: null,
  highlights: [],
  scriptText: '',
  lastToast: 0
};

// ---------------------------------------------------------------------------
// init
// ---------------------------------------------------------------------------
function init() {
  bindShell();
  bindPhoneNav();
  bindMacros();
  bindScripts();
  bindRecognition();
  bindKeyboard();
  bindClipboard();
  bindSettings();
  bindDraw();
  bindRecordPlay();
  loadStateIntoUI();
  renderScreen();
  renderMacroList();
  renderClipboard();
  renderKeyboardPreview();
  populateTrackSelect();
  log('Automa ready. Local-first automation studio.', 'info');
}

// ---------------------------------------------------------------------------
// shell / nav
// ---------------------------------------------------------------------------
function bindShell() {
  document.querySelectorAll('.nav-item').forEach(b => {
    b.addEventListener('click', () => openSection(b.dataset.sec));
  });
  const dark = document.getElementById('set-dark');
  if (localStorage.getItem(STORAGE_KEY + '.dark') === '1') {
    document.body.classList.add('light'); dark.checked = true;
  }
}
function openSection(sec) {
  document.querySelectorAll('.nav-item').forEach(b =>
    b.classList.toggle('active', b.dataset.sec === sec));
  document.querySelectorAll('main.panel').forEach(p =>
    p.classList.add('hidden'));
  document.getElementById('panel-' + sec).classList.remove('hidden');
  if (sec === 'phone') renderScreen();
  if (sec === 'macros') renderMacroList();
  if (sec === 'clipboard') renderClipboard();
  if (sec === 'recognition') populateTrackSelect();
  updateChips();
}
function toast(msg, kind = 'info') {
  const t = document.createElement('div');
  t.className = 'toast';
  t.innerHTML = '<span style="font-weight:700">' + kind.toUpperCase() + '</span> ' + escapeHtml(msg);
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 2600);
}
function updateChips() {
  document.getElementById('chip-overlay').textContent = 'OVERLAY: ' + (state.overlayOn ? 'on' : 'off');
  document.getElementById('chip-kb').textContent = 'KEYBOARD: ' + (state.kbVisible ? 'visible' : 'hidden');
  document.getElementById('chip-clip').textContent = 'CLIPBOARD: ' + state.clipboard.length;
  document.getElementById('chip-mic').textContent = 'MIC: ' + (state.micOn ? 'on' : 'off');
  document.getElementById('chip-mic').className = 'chip ' + (state.micOn ? 'chip-warn' : '');
  document.getElementById('chip-script').textContent = 'SCRIPT: ' + (state.playing ? 'running' : 'ready');
}
function escapeHtml(s) { return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":"&#39;"}[c])); }

// ---------------------------------------------------------------------------
// phone simulator
// ---------------------------------------------------------------------------
const screen = document.getElementById('screen');
const phoneTitle = document.getElementById('phone-title');
const kbToggle = document.getElementById('btn-kb-toggle');

function el(id, type, text, x, y, w, h, { desc = '', color = '', value = '', onclick } = {}) {
  const d = document.createElement('div');
  d.className = 'screen-el screen-' + type;
  d.id = 'el-' + id;
  d.dataset.id = id;
  d.dataset.type = type;
  d.dataset.text = text;
  d.dataset.desc = desc;
  d.style.left = x + 'px';
  d.style.top = y + 'px';
  d.style.width = w + 'px';
  d.style.height = h + 'px';
  d.style.background = color || 'transparent';
  d.textContent = text;
  d.title = desc || text;
  d.addEventListener('click', (ev) => {
    ev.stopPropagation();
    onElementTap(id);
  });
  return d;
}
function btnEl(id, text, x, y, w, h, onTap, { kind = 'primary', desc = '' } = {}) {
  const d = el(id, 'button', text, x, y, w, h, { desc, onclick: onTap });
  d.classList.add('screen-btn', 'btn-' + kind);
  return d;
}
function fieldEl(id, text, x, y, w, h, { desc = '', value = '' } = {}) {
  const d = el(id, 'textfield', text, x, y, w, h, { desc, value });
  d.classList.add('screen-field');
  d.style.border = '1px solid rgba(255,255,255,0.35)';
  d.style.borderRadius = '8px';
  d.style.background = '#0b121c';
  d.style.color = '#fff';
  d.style.display = 'flex';
  d.style.alignItems = 'center';
  d.style.paddingLeft = '10px';
  d.style.fontSize = '16px';
  return d;
}
function iconEl(id, text, x, y, w, h, { color = '#06b6d4', desc = '' } = {}) {
  const d = el(id, 'icon', text, x, y, w, h, { desc, color });
  d.classList.add('screen-icon');
  d.style.borderRadius = '12px';
  d.style.display = 'flex';
  d.style.alignItems = 'center';
  d.style.justifyContent = 'center';
  d.style.fontSize = '26px';
  d.style.fontWeight = '800';
  d.style.color = '#fff';
  return d;
}

function renderScreen() {
  screen.innerHTML = '';
  screen.style.width = SCREEN_W + 'px';
  screen.style.height = SCREEN_H + 'px';
  screen.style.position = 'relative';
  const fragments = {
    home: renderHome,
    calculator: renderCalculator,
    settings: renderSettings,
    notes: renderNotes,
    browser: renderBrowser,
    files: renderFiles,
    gallery: renderGallery
  };
  (fragments[state.currentApp] || renderHome)();
  phoneTitle.textContent = state.currentApp.charAt(0).toUpperCase() + state.currentApp.slice(1);
  applyHighlights();
  updateFocusRing();
  if (state.recording) {
    const pulse = document.createElement('div');
    pulse.className = 'record-pulse';
    pulse.style.position = 'absolute';
    pulse.style.top = '10px';
    pulse.style.left = '10px';
    pulse.style.width = '16px';
    pulse.style.height = '16px';
    pulse.style.background = '#ef4444';
    pulse.style.borderRadius = '50%';
    pulse.style.boxShadow = '0 0 8px #ef4444';
    pulse.style.zIndex = '50';
    screen.appendChild(pulse);
    const anim = document.createElement('style');
    anim.textContent = '@keyframes rp { 0%,100% { transform: scale(1); opacity: .8 } 50% { transform: scale(1.6); opacity: .3 } } .record-pulse { animation: rp 1.2s infinite }';
    document.head.appendChild(anim);
  }
}
function applyHighlights() {
  state.highlights.forEach(id => {
    const d = document.getElementById('el-' + id);
    if (d) d.classList.add('highlight');
  });
}
function clearHighlights() {
  document.querySelectorAll('.screen-el.highlight').forEach(d => d.classList.remove('highlight'));
  state.highlights = [];
}
function updateFocusRing() {
  document.querySelectorAll('.screen-el').forEach(d => {
    d.classList.toggle('focused', d.dataset.id === state.focusedId);
  });
}

function setFocused(id) {
  state.focusedId = id;
  const d = document.getElementById('el-' + id);
  state.focusedValue = d ? (d.dataset.value || '') : '';
  updateFocusRing();
  updateChips();
}

function onElementTap(id) {
  if (state.recording && !state.playing) {
    const d = document.getElementById('el-' + id);
    const rect = d.getBoundingClientRect();
    const scr = screen.getBoundingClientRect();
    recordStep({
      t: 'click', target: d.dataset.text || d.dataset.id || id,
      x: Math.round(rect.left - scr.left + rect.width / 2),
      y: Math.round(rect.top - scr.top + rect.height / 2),
      ms: 250
    });
    toast('Recorded click: ' + (d.dataset.text || id));
  }
  // app logic
  if (state.currentApp === 'home') openAppById(id);
  else if (state.currentApp === 'calculator') calculatorTap(id);
  else if (state.currentApp === 'settings') settingsTap(id);
  else if (state.currentApp === 'notes') notesTap(id);
  else if (state.currentApp === 'browser') browserTap(id);
  else if (state.currentApp === 'files') filesTap(id);
  else if (state.currentApp === 'gallery') galleryTap(id);
  renderScreen();
}
function openAppById(id) {
  const map = { appSettings: 'settings', appCalculator: 'calculator', appNotes: 'notes',
    appBrowser: 'browser', appFiles: 'files', appGallery: 'gallery' };
  if (map[id]) { state.currentApp = map[id]; state.focusedId = null; }
  if (id === 'appHome') state.currentApp = 'home';
}
// navigation
function phoneHome() { state.currentApp = 'home'; state.focusedId = null; renderScreen(); }
function phoneBack() {
  if (state.currentApp !== 'home') { state.currentApp = 'home'; state.focusedId = null; renderScreen(); }
}
function phoneRecent() { toast('Recent apps (simulated)'); }

// ---------------------------------------------------------------------------
// apps
// ---------------------------------------------------------------------------
function renderHome() {
  const bg = document.createElement('div');
  bg.style.position = 'absolute'; bg.style.inset = '0';
  bg.style.background = 'linear-gradient(160deg, #0f172a 0%, #121a24 100%)';
  screen.appendChild(bg);
  const welcome = document.createElement('div');
  welcome.style.position = 'absolute'; welcome.style.top = '70px'; welcome.style.left = '20px';
  welcome.style.right = '20px'; welcome.style.color = '#fff'; welcome.style.fontSize = '28px';
  welcome.style.fontWeight = '800'; welcome.textContent = 'Automa Device';
  screen.appendChild(welcome);
  const sub = document.createElement('div');
  sub.style.position = 'absolute'; sub.style.top = '112px'; sub.style.left = '20px';
  sub.style.color = '#94a3b8'; sub.style.fontSize = '14px';
  sub.textContent = 'Record • Script • Recognize • Automate';
  screen.appendChild(sub);
  const apps = [
    { id: 'appCalculator', text: 'Calculator', color: '#7c3aed', label: 'Calculator' },
    { id: 'appSettings', text: 'Settings', color: '#06b6d4', label: 'Settings' },
    { id: 'appNotes', text: 'Notes', color: '#22c55e', label: 'Notes' },
    { id: 'appBrowser', text: 'Browser', color: '#f59e0b', label: 'Browser' },
    { id: 'appFiles', text: 'Files', color: '#ef4444', label: 'Files' },
    { id: 'appGallery', text: 'Gallery', color: '#a855f7', label: 'Gallery' }
  ];
  let col = 0, row = 0;
  apps.forEach(a => {
    const x = 40 + col * 155, y = 170 + row * 170;
    screen.appendChild(iconEl(a.id, a.label, x, y, 120, 120, { color: a.color, desc: 'Open ' + a.label }));
    col++;
    if (col === 2) { col = 0; row++; }
  });
  const rec = document.createElement('div');
  rec.style.position = 'absolute'; rec.style.bottom = '90px'; rec.style.left = '20px'; rec.style.right = '20px';
  rec.style.background = 'rgba(124,58,237,0.10)'; rec.style.border = '1px solid rgba(124,58,237,0.35)';
  rec.style.borderRadius = '12px'; rec.style.padding = '12px'; rec.style.color = '#c7b4ff';
  rec.style.fontSize = '13px';
  rec.textContent = 'Tip: tap RECORD, then tap apps/buttons to capture a macro.';
  screen.appendChild(rec);
}
function renderCalculator() {
  screen.appendChild(btnEl('navHome', '⌂ Home', 16, 44, 70, 36, () => { phoneHome(); }, { kind: 'ghost' }));
  const disp = fieldEl('calcDisplay', '', 16, 100, 343, 60, { value: state.calcValue || '0' });
  disp.style.textAlign = 'right'; disp.style.paddingRight = '14px'; disp.style.fontSize = '32px';
  disp.style.fontWeight = '700'; disp.style.color = '#06b6d4';
  screen.appendChild(disp);
  const keys = [
    ['C', 'calcC', '#ef4444'], ['±', 'calcSign', '#455a64'], ['%', 'calcPct', '#455a64'], ['÷', 'calcDiv', '#ff9800'],
    ['7', 'calc7', '#37474f'], ['8', 'calc8', '#37474f'], ['9', 'calc9', '#37474f'], ['×', 'calcMul', '#ff9800'],
    ['4', 'calc4', '#37474f'], ['5', 'calc5', '#37474f'], ['6', 'calc6', '#37474f'], ['−', 'calcSub', '#ff9800'],
    ['1', 'calc1', '#37474f'], ['2', 'calc2', '#37474f'], ['3', 'calc3', '#37474f'], ['+', 'calcAdd', '#ff9800'],
    ['0', 'calc0', '#37474f'], ['.', 'calcDot', '#37474f'], ['=', 'calcEq', '#06b6d4']
  ];
  let i = 0;
  for (let r = 0; r < 5; r++) {
    for (let c = 0; c < 4; c++) {
      if (i >= keys.length) break;
      const [label, id, color] = keys[i++];
      const x = 16 + c * 83, y = 180 + r * 70;
      screen.appendChild(btnEl(id, label, x, y, 70, 56, () => calculatorTap(id), { kind: color === '#06b6d4' ? 'primary' : 'secondary', color }));
    }
  }
}
let calcValue = '0', calcPending = null, calcStored = 0;
function calculatorTap(id) {
  if (id === 'calcC') { calcValue = '0'; calcPending = null; }
  else if (id === 'calcEq') { try { calcValue = String(eval(calcValue.replace(/×/g,'*').replace(/÷/g,'/').replace(/−/g,'-'))); } catch { calcValue = 'Error'; } calcPending = null; }
  else if (id === 'calcSign') { calcValue = String(-Number(calcValue || 0)); }
  else if (id === 'calcPct') { calcValue = String(Number(calcValue || 0) / 100); }
  else if (['calcAdd','calcSub','calcMul','calcDiv'].includes(id)) {
    const op = { calcAdd:'+', calcSub:'−', calcMul:'×', calcDiv:'÷' }[id];
    calcStored = Number(calcValue || 0); calcPending = op; calcValue = '0';
  } else {
    const digit = id.replace('calc', '');
    calcValue = calcValue === '0' || calcPending === null && calcValue === '0' ? digit : calcValue + digit;
  }
  state.calcValue = calcValue;
  renderScreen();
}
function renderSettings() {
  screen.appendChild(btnEl('navHome', '⌂ Home', 16, 44, 70, 36, () => { phoneHome(); }, { kind: 'ghost' }));
  const rows = [
    { id: 'setRowWifi', text: 'Wi-Fi', desc: state.settings.wifi ? 'On' : 'Off', kind: 'switch' },
    { id: 'setRowBt', text: 'Bluetooth', desc: state.settings.bluetooth ? 'On' : 'Off', kind: 'switch' },
    { id: 'setRowDisplay', text: 'Display', desc: 'Dark mode', kind: 'button' },
    { id: 'setRowAccess', text: 'Accessibility', desc: state.settings.accessibility ? 'On' : 'Off', kind: 'switch' },
    { id: 'setRowAbout', text: 'About', desc: 'Automa v1.0', kind: 'button' }
  ];
  let y = 110;
  rows.forEach(r => {
    const row = document.createElement('div');
    row.style.position = 'absolute'; row.style.left = '20px'; row.style.right = '20px';
    row.style.top = y + 'px'; row.style.height = '56px';
    row.style.background = 'rgba(255,255,255,0.04)'; row.style.border = '1px solid rgba(255,255,255,0.08)';
    row.style.borderRadius = '10px'; row.style.display = 'flex';
    row.style.alignItems = 'center'; row.style.justifyContent = 'space-between';
    row.style.padding = '0 16px';
    const left = document.createElement('div');
    left.style.fontSize = '17px'; left.style.color = '#fff'; left.textContent = r.text;
    row.appendChild(left);
    const right = document.createElement('div');
    right.style.fontSize = '14px'; right.style.color = '#94a3b8'; right.textContent = r.desc;
    row.appendChild(right);
    if (r.kind === 'switch') {
      const sw = document.createElement('div');
      sw.className = 'switch'; sw.style.position = 'relative';
      const input = document.createElement('input'); input.type = 'checkbox';
      input.checked = state.settings[r.id.replace('setRow', '').toLowerCase()];
      const slider = document.createElement('span'); slider.className = 'slider';
      sw.appendChild(input); sw.appendChild(slider);
      sw.addEventListener('click', () => toggleSetting(r.id));
      right.style.display = 'none';
      row.appendChild(sw);
    }
    row.addEventListener('click', () => settingsTap(r.id));
    screen.appendChild(row);
    y += 70;
  });
}
function toggleSetting(id) {
  const key = id.replace('setRow', '').toLowerCase();
  state.settings[key] = !state.settings[key];
  toast((key.charAt(0).toUpperCase() + key.slice(1)) + ': ' + (state.settings[key] ? 'On' : 'Off'));
  renderScreen();
}
function settingsTap(id) { if (id !== 'setRowWifi' && id !== 'setRowBt' && id !== 'setRowAccess') toast('Settings › ' + id.replace('setRow','')); }
function renderNotes() {
  screen.appendChild(btnEl('navHome', '⌂ Home', 16, 44, 70, 36, () => { phoneHome(); }, { kind: 'ghost' }));
  const ta = document.createElement('textarea');
  ta.id = 'notesArea'; ta.style.position = 'absolute'; ta.style.left = '20px'; ta.style.top = '100px';
  ta.style.width = '335px'; ta.style.height = '420px'; ta.style.background = '#0b121c';
  ta.style.border = '1px solid rgba(255,255,255,0.25)'; ta.style.borderRadius = '10px';
  ta.style.color = '#fff'; ta.style.padding = '14px'; ta.style.fontSize = '17px';
  ta.style.resize = 'none'; ta.style.fontFamily = 'inherit';
  ta.value = state.notesText;
  ta.addEventListener('input', () => { state.notesText = ta.value; });
  screen.appendChild(ta);
  const save = btnEl('notesSave', 'Save', 16, 540, 100, 44, () => { toast('Notes saved'); }, { kind: 'primary' });
  const clear = btnEl('notesClear', 'Clear', 130, 540, 100, 44, () => { state.notesText = ''; ta.value = ''; toast('Cleared'); }, { kind: 'secondary' });
  screen.appendChild(save); screen.appendChild(clear);
  if (state.focusedId === 'notesArea') ta.focus();
}
function notesTap(id) { if (id === 'notesArea') setFocused('notesArea'); }
function renderBrowser() {
  screen.appendChild(btnEl('navHome', '⌂ Home', 16, 44, 70, 36, () => { phoneHome(); }, { kind: 'ghost' }));
  const bar = fieldEl('urlBar', 'https://automa.example', 16, 100, 250, 44, { value: state.browserUrl || 'https://automa.example' });
  const go = btnEl('browserGo', 'Go', 274, 100, 85, 44, () => { state.browserUrl = document.getElementById('urlBar').value || 'https://automa.example'; toast('Loading ' + state.browserUrl); }, { kind: 'primary' });
  screen.appendChild(bar); screen.appendChild(go);
  const page = document.createElement('div');
  page.style.position = 'absolute'; page.style.left = '20px'; page.style.right = '20px';
  page.style.top = '160px'; page.style.bottom = '90px';
  page.style.background = '#0b121c'; page.style.border = '1px solid rgba(255,255,255,0.15)';
  page.style.borderRadius = '10px'; page.style.padding = '18px';
  page.innerHTML = '<div style="color:#94a3b8;font-size:13px">automa.example</div>' +
    '<div style="color:#fff;font-size:20px;font-weight:700;margin-top:10px">Automation Studio</div>' +
    '<div style="color:#94a3b8;font-size:13px;margin-top:8px">A simulated web page for input automation demos.</div>' +
    '<div style="margin-top:18px;color:#22c55e">● Status: online</div>' +
    '<div style="color:#94a3b8;font-size:13px">Elements: address bar, go button, status indicator.</div>';
  screen.appendChild(page);
}
function browserTap(id) { if (id === 'urlBar') setFocused('urlBar'); }
function renderFiles() {
  screen.appendChild(btnEl('navHome', '⌂ Home', 16, 44, 70, 36, () => { phoneHome(); }, { kind: 'ghost' }));
  const files = ['Documents/Projects', 'Downloads', 'Pictures/Screenshots', 'Automa/Macros.json', 'Automa/Scripts/'];
  let y = 110;
  files.forEach(f => {
    const row = document.createElement('div');
    row.style.position = 'absolute'; row.style.left = '20px'; row.style.right = '20px';
    row.style.top = y + 'px'; row.style.height = '50px';
    row.style.background = 'rgba(255,255,255,0.04)'; row.style.border = '1px solid rgba(255,255,255,0.08)';
    row.style.borderRadius = '10px'; row.style.display = 'flex'; row.style.alignItems = 'center';
    row.style.justifyContent = 'space-between'; row.style.padding = '0 16px'; row.style.cursor = 'pointer';
    row.innerHTML = '<div style="color:#fff">📄 ' + escapeHtml(f) + '</div><div style="color:#94a3b8;font-size:12px">tap to open</div>';
    row.addEventListener('click', () => toast('Opened ' + f));
    screen.appendChild(row);
    y += 62;
  });
}
function renderGallery() {
  screen.appendChild(btnEl('navHome', '⌂ Home', 16, 44, 70, 36, () => { phoneHome(); }, { kind: 'ghost' }));
  const imgs = [
    { id: 'imgIcon1', color: '#ef4444', text: '📷' },
    { id: 'imgIcon2', color: '#06b6d4', text: '🖼' },
    { id: 'imgIcon3', color: '#f59e0b', text: '🌄' },
    { id: 'imgIcon4', color: '#22c55e', text: '📸' }
  ];
  let col = 0, row = 0;
  imgs.forEach(im => {
    const x = 20 + col * 170, y = 110 + row * 170;
    screen.appendChild(iconEl(im.id, im.text, x, y, 140, 140, { color: im.color, desc: 'Image ' + im.text }));
    col++;
    if (col === 2) { col = 0; row++; }
  });
}
function galleryTap(id) { toast('Image selected: ' + id); }

// ---------------------------------------------------------------------------
// macro recorder / player
// ---------------------------------------------------------------------------
function bindRecordPlay() {
  document.getElementById('btn-record').addEventListener('click', toggleRecord);
  document.getElementById('btn-play').addEventListener('click', () => playSelected());
  document.getElementById('btn-stop').addEventListener('click', stopPlayback);
}
function toggleRecord() {
  if (state.recording) { stopRecord(); }
  else { startRecord(); }
}
function startRecord() {
  state.recording = true;
  state.recordingSteps = [];
  document.getElementById('btn-record').classList.add('recording');
  document.getElementById('btn-stop').style.display = 'inline-block';
  document.getElementById('btn-play').disabled = true;
  openSection('phone');
  toast('Recording — tap elements on the screen');
}
function stopRecord() {
  state.recording = false;
  document.getElementById('btn-record').classList.remove('recording');
  document.getElementById('btn-stop').style.display = 'none';
  document.getElementById('btn-play').disabled = false;
  renderScreen();
  toast('Recording stopped (' + state.recordingSteps.length + ' steps)');
}
function recordStep(step) {
  if (!state.recording) return;
  step.ts = Date.now();
  state.recordingSteps.push(step);
}
function renderMacroList() {
  const tbody = document.getElementById('macro-table-body');
  const empty = document.getElementById('macros-empty');
  tbody.innerHTML = '';
  if (state.macros.length === 0) { empty.style.display = 'block'; tbody.parentElement.style.display = 'none'; }
  else { empty.style.display = 'none'; tbody.parentElement.style.display = ''; }
  const recentList = document.getElementById('macro-list');
  recentList.innerHTML = '';
  state.macros.slice().reverse().forEach(m => {
    const row = document.createElement('tr');
    row.className = state.selectedMacroId === m.id ? 'selected' : '';
    row.innerHTML =
      '<td style="font-weight:600">' + escapeHtml(m.name) + '</td>' +
      '<td>' + m.steps.length + ' steps</td>' +
      '<td>' + (m.lastRun ? new Date(m.lastRun).toLocaleTimeString() : '—') + '</td>' +
      '<td>' +
      '<button class="btn small" onclick="selectMacro(\'' + m.id + '\')">Edit</button> ' +
      '<button class="btn small" onclick="playMacroById(\'' + m.id + '\')">▶</button>' +
      '</td>';
    row.addEventListener('click', () => selectMacro(m.id));
    tbody.appendChild(row);
    const chip = document.createElement('span');
    chip.className = 'chip'; chip.style.fontSize = '11px'; chip.style.padding = '2px 8px';
    chip.textContent = m.name + ' (' + m.steps.length + ')';
    recentList.appendChild(chip);
  });
}
function selectMacro(id) {
  state.selectedMacroId = id;
  renderMacroList();
  const m = state.macros.find(x => x.id === id);
  if (m) {
    document.getElementById('macro-editor').style.display = 'block';
    document.getElementById('macro-editor-title').textContent = 'Edit: ' + escapeHtml(m.name);
    document.getElementById('edit-macro-name').value = m.name;
    document.getElementById('edit-macro-steps').value = JSON.stringify(m.steps, null, 2);
  }
}
function hideMacroEditor() { document.getElementById('macro-editor').style.display = 'none'; }
function newMacro() {
  const name = prompt('Macro name:', 'Untitled macro');
  if (!name) return;
  const m = { id: 'macro_' + Date.now(), name, steps: [], created: Date.now(), lastRun: null };
  state.macros.push(m);
  state.selectedMacroId = m.id;
  savePersist();
  renderMacroList();
  selectMacro(m.id);
  toast('Macro created');
}
function saveMacroEdit() {
  const m = state.macros.find(x => x.id === state.selectedMacroId);
  if (!m) return;
  m.name = document.getElementById('edit-macro-name').value || 'Untitled';
  try { m.steps = JSON.parse(document.getElementById('edit-macro-steps').value || '[]'); }
  catch (e) { toast('Invalid JSON steps', 'error'); return; }
  savePersist();
  renderMacroList();
  toast('Macro saved');
}
function deleteSelected() {
  if (!state.selectedMacroId) return;
  if (!confirm('Delete this macro?')) return;
  state.macros = state.macros.filter(x => x.id !== state.selectedMacroId);
  state.selectedMacroId = null;
  savePersist();
  renderMacroList();
  hideMacroEditor();
  toast('Macro deleted');
}
function playSelected() {
  if (!state.selectedMacroId) { toast('Select a macro first'); return; }
  playMacroById(state.selectedMacroId);
}
function playMacroById(id) {
  const m = state.macros.find(x => x.id === id);
  if (!m) return;
  playMacro(m);
}
async function playMacro(m) {
  if (state.playing) return;
  state.playing = true;
  document.getElementById('btn-play').disabled = true;
  document.getElementById('btn-stop').style.display = 'inline-block';
  updateChips();
  clearHighlights();
  let lastT = 0;
  for (const step of m.steps) {
    if (!state.playing) break;
    if (step.ms > 0) await sleep(step.ms);
    await playStep(step);
    lastT = Date.now();
  }
  m.lastRun = Date.now();
  savePersist();
  renderMacroList();
  state.playing = false;
  document.getElementById('btn-play').disabled = false;
  document.getElementById('btn-stop').style.display = 'none';
  updateChips();
  toast('Playback finished: ' + m.name);
  log('Played macro "' + escapeHtml(m.name) + '" (' + m.steps.length + ' steps)', 'ok');
}
function stopPlayback() { state.playing = false; toast('Playback stopped'); }
async function playStep(step) {
  if (step.t === 'click') {
    const targets = findElementsByText(step.target);
    if (targets.length) {
      const d = document.getElementById('el-' + targets[0]);
      d.classList.add('highlight'); state.highlights.push(targets[0]);
      await sleep(300);
      d.classList.remove('highlight');
      // execute app action like a real tap
      const id = targets[0];
      if (state.currentApp === 'calculator') calculatorTap(id);
      else if (state.currentApp === 'settings') settingsTap(id);
      else if (state.currentApp === 'home') openAppById(id);
      renderScreen();
    } else {
      log('Step target not found: ' + escapeHtml(step.target), 'warn');
    }
  } else if (step.t === 'type') {
    typeTextIntoFocused(step.text || '');
    await sleep(300);
  } else if (step.t === 'wait') {
    await sleep(step.ms || 500);
  } else if (step.t === 'press') {
    if (step.key === 'home') phoneHome();
    else if (step.key === 'back') phoneBack();
    renderScreen();
  }
}
function findElementsByText(text) {
  const out = [];
  document.querySelectorAll('.screen-el').forEach(d => {
    const t = (d.dataset.text || '').toLowerCase();
    if (t && t.includes(String(text).toLowerCase())) out.push(d.dataset.id);
  });
  return out;
}
function findElementsByType(type) {
  const out = [];
  document.querySelectorAll('.screen-el').forEach(d => {
    if (d.dataset.type === type) out.push(d.dataset.id);
  });
  return out;
}
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

// ---------------------------------------------------------------------------
// internal scripting engine
// ---------------------------------------------------------------------------
function bindScripts() {
  document.getElementById('script-template').addEventListener('change', (e) => {
    const v = e.target.value;
    if (v === 'Calculator: 2+2') loadScript('click "Calculator"\nwait 400\nclick "2"\nclick "+"\nclick "2"\nclick "="\nwait 400\nnotify "Result: 4"\n');
    else if (v === 'Open Settings & toggle Wi-Fi') loadScript('click "Settings"\nwait 500\nclick "Wi-Fi"\nwait 300\nnotify "Wi-Fi toggled"\n');
    else if (v === 'Fill Notes & save') loadScript('click "Notes"\nwait 400\nclick "notesArea"\nwait 300\ntype "Automated note from Automa"\nwait 300\nclick "Save"\nnotify "Notes saved"\n');
    e.target.selectedIndex = 0;
  });
}
function loadScript(s) { document.getElementById('script-editor').value = s; state.scriptText = s; }
function clearScript() { document.getElementById('script-editor').value = ''; state.scriptText = ''; document.getElementById('script-output').textContent = ''; }
function saveScript() {
  const s = document.getElementById('script-editor').value;
  localStorage.setItem(STORAGE_KEY + '.script', s);
  toast('Script saved to local storage');
}
async function runScript() {
  const src = document.getElementById('script-editor').value;
  state.scriptText = src;
  const out = document.getElementById('script-output');
  out.textContent = '';
  logToOutput('▶ Running script…', 'info');
  try {
    const ast = parseScript(src);
    state.playing = true;
    document.getElementById('btn-play').disabled = true;
    document.getElementById('btn-stop').style.display = 'inline-block';
    updateChips();
    await executeASTAsync(ast);
    state.playing = false;
    document.getElementById('btn-play').disabled = false;
    document.getElementById('btn-stop').style.display = 'none';
    updateChips();
    logToOutput('✓ Script finished', 'ok');
  } catch (e) {
    state.playing = false;
    document.getElementById('btn-play').disabled = false;
    document.getElementById('btn-stop').style.display = 'none';
    updateChips();
    logToOutput('✗ ' + escapeHtml(e.message), 'err');
    toast('Script error: ' + escapeHtml(e.message), 'error');
  }
}
function logToOutput(msg, kind) {
  const out = document.getElementById('script-output');
  const line = document.createElement('div');
  line.className = 'log-' + kind;
  line.textContent = new Date().toLocaleTimeString() + ' ' + msg;
  out.appendChild(line);
  out.scrollTop = out.scrollHeight;
}
// parser: lines, repeat n { ... }, if found "x" { ... } else { ... }
function parseScript(src) {
  const rawLines = src.split(/\r?\n/);
  const lines = rawLines.map(l => {
    const c = l.indexOf('//');
    return c >= 0 ? l.slice(0, c) : l;
  });
  const root = { type: 'block', children: [] };
  const stack = [root];
  let pendingElse = false;
  for (let i = 0; i < lines.length; i++) {
    let line = lines[i].trim();
    if (!line) continue;
    const mRepeat = line.match(/^repeat\s+(\d+)\s*\{?\s*$/);
    if (mRepeat) {
      const node = { type: 'repeat', n: parseInt(mRepeat[1], 10), children: [] };
      stack[stack.length - 1].children.push(node);
      stack.push(node);
      continue;
    }
    const mIf = line.match(/^if\s+found\s+"([^"]*)"\s*\{?\s*$/);
    if (mIf) {
      const node = { type: 'if', text: mIf[1], children: [], elseChildren: [] };
      stack[stack.length - 1].children.push(node);
      stack.push(node);
      pendingElse = true;
      continue;
    }
    if (line === 'else' || line === 'else {') {
      if (!pendingElse || stack[stack.length - 1].type !== 'if') throw new Error('else without if');
      stack[stack.length - 1].inElse = true;
      continue;
    }
    if (line === '}') {
      if (stack.length === 1) throw new Error('unexpected }');
      const node = stack.pop();
      if (node.type === 'if') pendingElse = false;
      continue;
    }
    // simple command
    stack[stack.length - 1].children.push({ type: 'cmd', line });
  }
  if (stack.length > 1) throw new Error('unclosed block');
  return root;
}
function executeAST(node) {
  for (const child of node.children) {
    if (!state.playing) break;
    if (child.type === 'cmd') executeCmd(child.line);
    else if (child.type === 'repeat') {
      for (let k = 0; k < child.n; k++) executeAST(child);
    } else if (child.type === 'if') {
      const found = findElementsByText(child.text).length > 0;
      if (found) executeAST({ type: 'block', children: child.children });
      else if (!child.inElse && child.elseChildren.length) executeAST({ type: 'block', children: child.elseChildren });
      // note: else children are stored in same children; simpler: handled by inElse flag below
    }
  }
}
// simpler handling: when entering if node, decide branch; else branch markers skip
function executeCmd(line) {
  const mClick = line.match(/^click\s+"([^"]*)"\s*$/);
  if (mClick) { clickTarget(mClick[1]); return; }
  const mType = line.match(/^type\s+"([^"]*)"\s*$/);
  if (mType) { typeTextIntoFocused(mType[1]); return; }
  const mWait = line.match(/^wait\s+(\d+)\s*$/);
  if (mWait) { return sleepAsync(parseInt(mWait[1], 10)); } // handled async below
  const mNotify = line.match(/^notify\s+"([^"]*)"\s*$/);
  if (mNotify) { toast(mNotify[1]); logToOutput('notify: ' + mNotify[1], 'info'); return; }
  const mScreenshot = line.match(/^screenshot\s*$/);
  if (mScreenshot) { snapDraw(); logToOutput('screenshot captured', 'info'); return; }
  const mPress = line.match(/^press\s+(back|home|recent)\s*$/);
  if (mPress) { if (mPress[1] === 'home') phoneHome(); else if (mPress[1] === 'back') phoneBack(); renderScreen(); return; }
  throw new Error('Unknown command: ' + line);
}
// Make runScript async-aware for wait/repeat timing
async function executeASTAsync(node) {
  for (const child of node.children) {
    if (!state.playing) break;
    if (child.type === 'cmd') { await executeCmdAsync(child.line); }
    else if (child.type === 'repeat') {
      for (let k = 0; k < child.n; k++) { await executeASTAsync(child); }
    } else if (child.type === 'if') {
      const found = findElementsByText(child.text).length > 0;
      const branch = found ? child.children : (child.inElse ? [] : []);
      // restructure: else children are separate; store else block
      const elseBranch = child.elseChildren || [];
      if (!found && elseBranch.length) { await executeASTAsync({ type: 'block', children: elseBranch }); }
      else if (found) { await executeASTAsync({ type: 'block', children: child.children }); }
    }
  }
}
async function executeCmdAsync(line) {
  const mClick = line.match(/^click\s+"([^"]*)"\s*$/);
  if (mClick) { clickTarget(mClick[1]); await sleep(300); return; }
  const mType = line.match(/^type\s+"([^"]*)"\s*$/);
  if (mType) { typeTextIntoFocused(mType[1]); await sleep(300); return; }
  const mWait = line.match(/^wait\s+(\d+)\s*$/);
  if (mWait) { await sleep(parseInt(mWait[1], 10)); return; }
  const mNotify = line.match(/^notify\s+"([^"]*)"\s*$/);
  if (mNotify) { toast(mNotify[1]); logToOutput('notify: ' + mNotify[1], 'info'); return; }
  const mScreenshot = line.match(/^screenshot\s*$/);
  if (mScreenshot) { snapDraw(); logToOutput('screenshot captured', 'info'); return; }
  const mPress = line.match(/^press\s+(back|home|recent)\s*$/);
  if (mPress) { if (mPress[1] === 'home') phoneHome(); else if (mPress[1] === 'back') phoneBack(); renderScreen(); return; }
  throw new Error('Unknown command: ' + line);
}
function clickTarget(text) {
  const targets = findElementsByText(text);
  if (!targets.length) throw new Error('Element not found: "' + text + '"');
  const d = document.getElementById('el-' + targets[0]);
  d.classList.add('highlight'); state.highlights.push(targets[0]);
  const id = targets[0];
  if (state.currentApp === 'calculator') calculatorTap(id);
  else if (state.currentApp === 'settings') settingsTap(id);
  else if (state.currentApp === 'home') openAppById(id);
  renderScreen();
}
function typeTextIntoFocused(text) {
  if (!state.focusedId) {
    // fallback: if there's a notes area visible, focus it
    if (document.getElementById('notesArea')) { setFocused('notesArea'); }
    else { toast('No focused field', 'warn'); return; }
  }
  if (state.focusedId === 'notesArea') {
    state.notesText += text;
    const ta = document.getElementById('notesArea');
    if (ta) ta.value = state.notesText;
  } else if (state.focusedId === 'urlBar') {
    state.browserUrl = (state.browserUrl || '') + text;
    const el = document.getElementById('urlBar');
    if (el) el.value = state.browserUrl;
  } else {
    const d = document.getElementById('el-' + state.focusedId);
    if (d) { d.dataset.value = (d.dataset.value || '') + text; d.textContent = d.dataset.value; }
  }
  state.focusedValue = (state.focusedValue || '') + text;
  logToOutput('typed into #' + state.focusedId + ': ' + escapeHtml(text), 'info');
}

// ---------------------------------------------------------------------------
// recognition
// ---------------------------------------------------------------------------
function bindRecognition() {
  document.getElementById('rec-image').addEventListener('change', (e) => {
    state.templateFile = e.target.files[0];
  });
}
function recFindText() {
  const q = document.getElementById('rec-text').value.trim();
  clearHighlights();
  if (!q) { toast('Enter text'); return; }
  const ids = findElementsByText(q);
  ids.forEach(id => { const d = document.getElementById('el-' + id); if (d) d.classList.add('highlight'); });
  state.highlights = ids;
  document.getElementById('rec-image-result').textContent = '';
  document.getElementById('rec-track-result').textContent = '';
  toast(ids.length ? ids.length + ' text match(es) for "' + q + '"' : 'No matches for "' + q + '"');
  log('Text search "' + escapeHtml(q) + '": ' + ids.length + ' result(s)', ids.length ? 'ok' : 'info');
}
function recFindType() {
  const sel = document.getElementById('rec-type');
  const type = sel.value === 'All objects' ? null : sel.value.toLowerCase();
  clearHighlights();
  const ids = type ? findElementsByType(type) : Array.from(document.querySelectorAll('.screen-el')).map(d => d.dataset.id);
  ids.forEach(id => { const d = document.getElementById('el-' + id); if (d) d.classList.add('highlight'); });
  state.highlights = ids;
  toast(type ? ids.length + ' ' + type + '(s) detected' : ids.length + ' object(s) detected');
}
function recMatchImage() {
  if (!state.templateFile) { toast('Upload an image first'); return; }
  const reader = new FileReader();
  reader.onload = (ev) => {
    const img = new Image();
    img.onload = () => {
      // dominant color of template via small canvas sample
      const c = document.createElement('canvas'); c.width = c.height = 48;
      const ctx = c.getContext('2d');
      ctx.drawImage(img, 0, 0, 48, 48);
      const data = ctx.getImageData(0, 0, 48, 48).data;
      let r = 0, g = 0, b = 0, n = 0;
      for (let i = 0; i < data.length; i += 4) { if (data[i + 3] > 100) { r += data[i]; g += data[i + 1]; b += data[i + 2]; n++; } }  
      if (n === 0) { toast('Empty template'); return; }
      const tc = { r: r / n, g: g / n, b: b / n };
      // compare to icon elements by their color (store as hex->rgb)
      const ids = [];
      document.querySelectorAll('.screen-icon, .screen-image').forEach(d => {
        const hex = d.dataset.color || '';
        const ec = hexToRgb(hex);
        if (ec && colorDist(tc, ec) < 60) { ids.push(d.dataset.id); d.classList.add('highlight'); }
      });
      state.highlights = ids;
      const res = document.getElementById('rec-image-result');
      res.innerHTML = '<span class="ok">✓</span> Template matched ' + ids.length + ' region(s)';
      toast('Template match: ' + ids.length + ' region(s)');
    };
    img.src = ev.target.result;
  };
  reader.readAsDataURL(state.templateFile);
}
function hexToRgb(hex) {
  const m = hex.match(/^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i);
  return m ? { r: parseInt(m[1], 16), g: parseInt(m[2], 16), b: parseInt(m[3], 16) } : null;
}
function colorDist(a, b) { return Math.sqrt(Math.pow(a.r - b.r, 2) + Math.pow(a.g - b.g, 2) + Math.pow(a.b - b.b, 2)); }
function populateTrackSelect() {
  const sel = document.getElementById('rec-track');
  sel.innerHTML = '<option value="">Select target…</option>';
  document.querySelectorAll('.screen-el').forEach(d => {
    const o = document.createElement('option');
    o.value = d.dataset.id;
    o.textContent = (d.dataset.text || d.dataset.id) + ' (' + d.dataset.type + ')';
    sel.appendChild(o);
  });
}
function recTrack(id) {
  state.trackingId = id || null;
  document.getElementById('btn-track-stop').style.display = id ? 'inline-block' : 'none';
  if (!id) { clearHighlights(); document.getElementById('rec-track-result').textContent = ''; return; }
  const d = document.getElementById('el-' + id);
  const res = document.getElementById('rec-track-result');
  res.innerHTML = '<span class="ok">✓</span> Tracking: ' + escapeHtml(d.dataset.text || id);
  if (d) { d.classList.add('highlight'); state.highlights = [id]; }
  toast('Tracking started');
}
function recTrackStop() { state.trackingId = null; document.getElementById('btn-track-stop').style.display = 'none'; clearHighlights(); document.getElementById('rec-track-result').textContent = 'Tracking stopped'; toast('Tracking stopped'); }

// ---------------------------------------------------------------------------
// keyboard
// ---------------------------------------------------------------------------
function bindKeyboard() {
  document.getElementById('btn-kb-mic').addEventListener('click', toggleKBMic);
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (SpeechRecognition) {
    state.recognition = new SpeechRecognition();
    state.recognition.continuous = false;
    state.recognition.interimResults = true;
    state.recognition.lang = 'en-US';
    state.recognition.onresult = (e) => {
      let transcript = '';
      for (let i = e.resultIndex; i < e.results.length; i++) transcript += e.results[i][0].transcript;
      if (state.focusedId === 'notesArea') { state.notesText += transcript; document.getElementById('notesArea').value = state.notesText; }
      else typeTextIntoFocused(transcript);
      toast('Voice: ' + escapeHtml(transcript));
    };
    state.recognition.onend = () => { if (state.micOn) state.micOn = false; updateMicUI(); };
    state.recognition.onerror = () => { state.micOn = false; updateMicUI(); toast('Mic error', 'error'); };
  }
}
function toggleKeyboard() { state.kbVisible = !state.kbVisible; document.getElementById('kb-overlay').classList.toggle('hidden', !state.kbVisible); document.getElementById('floating-actions').classList.toggle('hidden', !state.kbVisible); updateChips(); renderKeyboard(); }
function hideKeyboard() { state.kbVisible = false; document.getElementById('kb-overlay').classList.add('hidden'); document.getElementById('floating-actions').classList.add('hidden'); updateChips(); }
function toggleKBMic() {
  if (!state.recognition) { toast('Web Speech API not available in this browser', 'error'); return; }
  state.micOn = !state.micOn;
  updateMicUI();
  if (state.micOn) { try { state.recognition.start(); } catch (e) { state.micOn = false; updateMicUI(); toast('Mic start failed', 'error'); } }
  else { try { state.recognition.stop(); } catch (e) {} }
}
function updateMicUI() {
  const btn = document.getElementById('btn-kb-mic');
  const kmic = document.getElementById('kb-mic');
  const dot = btn.querySelector('.mic-dot');
  btn.classList.toggle('listening', state.micOn);
  dot.className = 'mic-dot' + (state.micOn ? ' listening' : '');
  btn.innerHTML = '<span class="mic-dot' + (state.micOn ? ' listening' : '') + '"></span> Mic: ' + (state.micOn ? 'on' : 'off');
  if (kmic) kmic.classList.toggle('listening', state.micOn);
  updateChips();
}
function renderKeyboard() {
  const container = document.getElementById('kb-keys');
  container.innerHTML = '';
  const layout = state.kbLayout;
  const rows = layout === 'en' ? [
    ['Q','W','E','R','T','Y','U','I','O','P'],
    ['A','S','D','F','G','H','J','K','L'],
    ['Z','X','C','V','B','N','M',',','.','?']
  ] : layout === 'symbols' ? [
    ['@','#','$','%','&','*','(',')','-','+'],
    ['=','{','}','[',']','|','\\',':',';','"'],
    ["'",'<','>','/','!','~','`','_','?','¡']
  ] : [
    ['1','2','3','4','5'], ['6','7','8','9','0'], ['+','-','*','/','=']
  ];
  rows.forEach(row => {
    row.forEach(ch => {
      const b = document.createElement('button');
      b.className = 'kb-btn';
      b.textContent = ch;
      b.addEventListener('click', () => kbPress(ch));
      container.appendChild(b);
    });
  });
}
function kbPress(ch) {
  typeTextIntoFocused(ch);
}
function kbShift() { state.kbShift = !state.kbShift; renderKeyboard(); toast('Shift ' + (state.kbShift ? 'on' : 'off')); }
function kbSpace() { typeTextIntoFocused(' '); }
function kbEnter() { typeTextIntoFocused('\n'); if (state.focusedId === 'notesArea') document.getElementById('notesArea').focus(); }
function kbBackspace() {
  if (state.focusedId === 'notesArea') { state.notesText = state.notesText.slice(0, -1); document.getElementById('notesArea').value = state.notesText; }
  else { state.focusedValue = state.focusedValue.slice(0, -1); const d = document.getElementById('el-' + state.focusedId); if (d) d.textContent = state.focusedValue; }
}
function insertKbText(t) { if (t === '\n') kbEnter(); else if (t === '\b') kbBackspace(); else kbPress(t); }
function renderKeyboardPreview() {
  const p = document.getElementById('kb-preview');
  p.textContent = state.kbVisible ? 'Keyboard preview: visible on screen (bottom)' : 'Keyboard preview: hidden';
}

// ---------------------------------------------------------------------------
// clipboard
// ---------------------------------------------------------------------------
function bindClipboard() {
  document.getElementById('clip-text').addEventListener('paste', (e) => {
    e.preventDefault();
    const data = (e.clipboardData || window.clipboardData).getData('text');
    document.getElementById('clip-text').value = data;
    clipAdd(data);
  });
}
function clipCopy() {
  const input = document.getElementById('clip-text');
  const v = input.value.trim();
  if (!v) { toast('Nothing to copy'); return; }
  clipAdd(v);
  navigator.clipboard.writeText(v).then(() => toast('Copied: ' + escapeHtml(v)), () => toast('Copied to internal clipboard'));
}
function clipAdd(v) {
  const now = Date.now();
  state.clipboard = state.clipboard.filter(c => c.text !== v);
  state.clipboard.unshift({ text: v, ts: now });
  if (state.clipboard.length > 50) state.clipboard.pop();
  savePersist();
  renderClipboard();
}
function clipPaste() {
  if (state.clipboard.length === 0) { toast('Clipboard empty'); return; }
  const v = state.clipboard[0].text;
  typeTextIntoFocused(v);
  toast('Pasted: ' + escapeHtml(v));
}
function clipClear() { state.clipboard = []; savePersist(); renderClipboard(); toast('Clipboard cleared'); }
function renderClipboard() {
  const list = document.getElementById('clip-list');
  list.innerHTML = '';
  state.clipboard.forEach((c, i) => {
    const item = document.createElement('div');
    item.className = 'clip-item';
    item.innerHTML = '<div class="text">' + escapeHtml(c.text) + '</div><div class="meta">#' + (i + 1) + ' · ' + new Date(c.ts).toLocaleTimeString() + '</div>';
    item.addEventListener('click', () => { typeTextIntoFocused(c.text); toast('Pasted: ' + escapeHtml(c.text)); });
    list.appendChild(item);
  });
  document.getElementById('chip-clip').textContent = 'CLIPBOARD: ' + state.clipboard.length;
}

// ---------------------------------------------------------------------------
// draw on top
// ---------------------------------------------------------------------------
function bindDraw() {
  const canvas = document.getElementById('overlay-canvas');
  const hud = document.getElementById('draw-hud');
  document.getElementById('draw-range').addEventListener('input', (e) => { state.drawSize = parseInt(e.target.value, 10); document.getElementById('draw-size').textContent = state.drawSize; });
  let painting = false;
  const start = (e) => { if (!state.drawMode) return; painting = true; const p = getCanvasPoint(e); drawDot(p.x, p.y); };
  const move = (e) => { if (!painting || !state.drawMode) return; const p = getCanvasPoint(e); drawDot(p.x, p.y); };
  const end = () => { painting = false; };
  canvas.addEventListener('mousedown', start);
  canvas.addEventListener('mousemove', move);
  canvas.addEventListener('mouseup', end);
  canvas.addEventListener('mouseleave', end);
  canvas.addEventListener('touchstart', (e) => { if (!state.drawMode) return; painting = true; const p = getCanvasPoint(e.touches[0]); drawDot(p.x, p.y); }, { passive: false });
  canvas.addEventListener('touchmove', (e) => { if (!painting || !state.drawMode) return; e.preventDefault(); const p = getCanvasPoint(e.touches[0]); drawDot(p.x, p.y); }, { passive: false });
  canvas.addEventListener('touchend', end);
}
function toggleOverlay() {
  state.overlayOn = !state.overlayOn;
  const canvas = document.getElementById('overlay-canvas');
  const hud = document.getElementById('draw-hud');
  const sw = document.getElementById('set-overlay');
  canvas.classList.toggle('hidden', !state.overlayOn);
  hud.classList.toggle('hidden', !state.overlayOn);
  if (sw) sw.checked = state.overlayOn;
  document.getElementById('chip-overlay').textContent = 'OVERLAY: ' + (state.overlayOn ? 'on' : 'off');
  if (state.overlayOn) resizeCanvas();
  toast('Draw on top ' + (state.overlayOn ? 'enabled' : 'disabled'));
}
function toggleDrawMode() { state.drawMode = !state.drawMode; toast(state.drawMode ? 'Draw mode on' : 'Draw mode off'); }
function resizeCanvas() {
  const canvas = document.getElementById('overlay-canvas');
  const dpr = window.devicePixelRatio || 1;
  const rect = screen.getBoundingClientRect();
  canvas.width = rect.width * dpr;
  canvas.height = rect.height * dpr;
  canvas.style.width = rect.width + 'px';
  canvas.style.height = rect.height + 'px';
  const ctx = canvas.getContext('2d');
  ctx.scale(dpr, dpr);
}
function setDrawColor(c) { state.drawColor = c; document.getElementById('draw-swatch').style.background = c; }
function drawDot(x, y) {
  const canvas = document.getElementById('overlay-canvas');
  const ctx = canvas.getContext('2d');
  const rect = canvas.getBoundingClientRect();
  const rx = (x - rect.left) / rect.width * canvas.width;
  const ry = (y - rect.top) / rect.height * canvas.height;
  ctx.fillStyle = state.drawColor;
  ctx.beginPath();
  ctx.arc(rx, ry, state.drawSize / 2, 0, Math.PI * 2);
  ctx.fill();
}
function clearDraw() {
  const canvas = document.getElementById('overlay-canvas');
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  toast('Canvas cleared');
}
function snapDraw() {
  toast('Snapshot captured');
  log('Draw overlay snapshot taken', 'info');
}
function getCanvasPoint(p) {
  const canvas = document.getElementById('overlay-canvas');
  const rect = canvas.getBoundingClientRect();
  return { x: p.clientX, y: p.clientY };
}

// ---------------------------------------------------------------------------
// settings
// ---------------------------------------------------------------------------
function bindSettings() {
  const acc = document.getElementById('set-acc');
  acc.checked = state.settings.accessibility;
  acc.addEventListener('change', (e) => { state.settings.accessibility = e.target.checked; toast('Accessibility ' + (e.target.checked ? 'enabled' : 'disabled')); });
  const mic = document.getElementById('set-mic');
  mic.addEventListener('change', (e) => { state.micOn = e.target.checked; updateMicUI(); });
  const kb = document.getElementById('set-kb');
  kb.addEventListener('change', (e) => { toast('Default IME: ' + (e.target.checked ? 'Automa Keyboard' : 'system')); });
  const clip = document.getElementById('set-clip');
  clip.checked = true;
}
function toggleDark() {
  const on = document.getElementById('set-dark').checked;
  document.body.classList.toggle('light', on);
  localStorage.setItem(STORAGE_KEY + '.dark', on ? '1' : '0');
}

// ---------------------------------------------------------------------------
// persistence
// ---------------------------------------------------------------------------
function savePersist() {
  localStorage.setItem(STORAGE_KEY + '.macros', JSON.stringify(state.macros));
  localStorage.setItem(STORAGE_KEY + '.clipboard', JSON.stringify(state.clipboard));
  localStorage.setItem(STORAGE_KEY + '.notes', state.notesText);
}
const params = new URLSearchParams(location.search);
if (params.get('sec')) openSection(params.get('sec'));

function loadStateIntoUI() {
  state.notesText = localStorage.getItem(STORAGE_KEY + '.notes') || '';
  const ta = document.getElementById('notesArea');
  if (ta) ta.value = state.notesText;
  const s = localStorage.getItem(STORAGE_KEY + '.script');
  if (s) { state.scriptText = s; document.getElementById('script-editor').value = s; }
}
// ---------------------------------------------------------------------------
// export / import / reset
// ---------------------------------------------------------------------------
function exportData() {
  const data = { macros: state.macros, clipboard: state.clipboard, notes: state.notesText, script: state.scriptText, settings: state.settings };
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = 'automa_export.json'; a.click();
  URL.revokeObjectURL(url);
  toast('Exported automa_export.json');
}
function importData() {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = '.json';
  input.onchange = (e) => {
    const f = e.target.files[0];
    if (!f) return;
    const r = new FileReader();
    r.onload = (ev) => {
      try {
        const d = JSON.parse(ev.target.result);
        if (d.macros) state.macros = d.macros;
        if (d.clipboard) state.clipboard = d.clipboard;
        if (d.notes !== undefined) state.notesText = d.notes;
        if (d.script !== undefined) { state.scriptText = d.script; document.getElementById('script-editor').value = d.script; }
        savePersist();
        renderMacroList(); renderClipboard(); renderScreen();
        toast('Imported');
      } catch (err) { toast('Import failed', 'error'); }
    };
    r.readAsText(f);
  };
  input.click();
}
function resetData() {
  if (!confirm('Reset all Automa data?')) return;
  localStorage.removeItem(STORAGE_KEY + '.macros');
  localStorage.removeItem(STORAGE_KEY + '.clipboard');
  localStorage.removeItem(STORAGE_KEY + '.notes');
  localStorage.removeItem(STORAGE_KEY + '.script');
  state.macros = []; state.clipboard = []; state.notesText = ''; state.scriptText = ''; state.selectedMacroId = null;
  renderMacroList(); renderClipboard(); renderScreen();
  toast('Reset');
}
// ---------------------------------------------------------------------------
// misc
// ---------------------------------------------------------------------------
function log(msg, kind) { console.log('[Automa][' + kind + '] ' + msg); }
window.addEventListener('resize', () => { if (state.overlayOn) resizeCanvas(); });
window.openSection = openSection;
window.toggleRecord = toggleRecord;
window.playSelected = playSelected;
window.playMacroById = playMacroById;
window.selectMacro = selectMacro;
window.saveMacroEdit = saveMacroEdit;
window.hideMacroEditor = hideMacroEditor;
window.newMacro = newMacro;
window.deleteSelected = deleteSelected;
window.runScript = runScript;
window.clearScript = clearScript;
window.saveScript = saveScript;
window.loadScript = loadScript;
window.recFindText = recFindText;
window.recFindType = recFindType;
window.recMatchImage = recMatchImage;
window.recTrack = recTrack;
window.recTrackStop = recTrackStop;
window.toggleKeyboard = toggleKeyboard;
window.hideKeyboard = hideKeyboard;
window.toggleKBMic = toggleKBMic;
window.renderKeyboard = renderKeyboard;
window.insertKbText = insertKbText;
window.kbShift = kbShift;
window.kbSpace = kbSpace;
window.kbEnter = kbEnter;
window.kbBackspace = kbBackspace;
window.clipCopy = clipCopy;
window.clipPaste = clipPaste;
window.clipClear = clipClear;
window.toggleOverlay = toggleOverlay;
window.toggleDrawMode = toggleDrawMode;
window.setDrawColor = setDrawColor;
window.clearDraw = clearDraw;
window.snapDraw = snapDraw;
window.toggleDark = toggleDark;
window.exportData = exportData;
window.importData = importData;
window.resetData = resetData;
window.phoneHome = phoneHome;
window.phoneBack = phoneBack;
window.phoneRecent = phoneRecent;

document.addEventListener('DOMContentLoaded', init);
