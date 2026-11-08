// Builds the promo video from the frames StoreArtExportTest exported (real casts, steered by the
// computer rival's brain): draws each frame with the game's shaders (tools/render3d-preview's
// drawFrame), frames the portrait game view in a 1920 x 1080 landscape page (YouTube and Play show
// promo videos in landscape) with the store captions of the chosen language, then encodes an MP4
// with ffmpeg: the game's own CC0 music (shallows, crossfading into the deep theme like in the
// game) and its sound effects at the cast's real events.
//
// Usage: node tools/promo-video/make-promo-video.cjs [--locale en-US] [--in app/build/promo-video]
//                                                    [--out <file.mp4>] [--keep-png]
//                                                    [--only <first>-<last>] (redraw only those frames,
//                                                     reusing the other PNGs kept by --keep-png)
// Normally run through tools/promo-video/make-promo-video.sh (exports the frames first).
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { drawFrame } = require('../render3d-preview/render.cjs');

const args = Object.fromEntries(process.argv.slice(2).reduce((acc, a, i, all) => {
  if (a.startsWith('--')) acc.push([a.slice(2), all[i + 1] && !all[i + 1].startsWith('--') ? all[i + 1] : true]);
  return acc;
}, []));
const locale = args.locale || 'en-US';
const input = args.in || 'app/build/promo-video';
const output = args.out || path.join(input, `deep-line-promo-${locale}.mp4`);
const pngDir = path.join(input, `png-${locale}`);
const W = 1920, H = 1080, FPS = 25;
const END_CARD_FRAMES = 4 * FPS;
const TITLE_FRAMES = 3 * FPS;

// Captions: the store listing's screenshot captions, in order (docs/store/screenshots.md).
// A number and its unit stay on one line ("300 m").
const captions = fs.readFileSync(`docs/store/listings/${locale}/screenshot_captions.txt`, 'utf8').trim().split('\n')
  .map(c => c.replace(/(\d) (m)\b/g, '$1\u00a0$2'));
const captionOf = { steer: captions[0], dodge: captions[1], hook: captions[2], dark: captions[3] };
const labels = {
  'en-US': ['Depth', 'Bumps', 'Fish', 'Points'],
  'fr-FR': ['Profondeur', 'Chocs', 'Poissons', 'Points'],
  'es-ES': ['Profundidad', 'Golpes', 'Peces', 'Puntos'],
  'de-DE': ['Tiefe', 'Stöße', 'Fische', 'Punkte'],
  'pt-BR': ['Profundidade', 'Batidas', 'Peixes', 'Pontos'],
  'tr-TR': ['Derinlik', 'Çarpma', 'Balık', 'Puan'],
}[locale];
if (!labels) throw new Error(`no labels for ${locale}`);

const shared = JSON.parse(fs.readFileSync(path.join(input, 'shared.json'), 'utf8'));
const timeline = JSON.parse(fs.readFileSync(path.join(input, 'events.json'), 'utf8'));
const frameFiles = fs.readdirSync(path.join(input, 'frames')).filter(f => f.endsWith('.json')).sort();
const clipEnd = (timeline.events.find(e => e.kind === 'clip_end') || { frame: frameFiles.length }).frame;
const total = frameFiles.length + END_CARD_FRAMES;

const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');
const page0 = `<html><head><style>
  body { margin:0; width:${W}px; height:${H}px; overflow:hidden; font-family:'Inter'; color:#fff;
         background: linear-gradient(180deg, #8fe0ee 0%, #2cc3d8 22%, #0e5e8c 62%, #04182b 100%); }
  #c { position:absolute; left:${(W - 608) / 2}px; top:0; box-shadow: 0 0 60px rgba(2,16,30,0.55); }
  #fade { position:absolute; left:${(W - 608) / 2}px; top:0; width:608px; height:${H}px; background:#000; opacity:0; }
  .panel { position:absolute; top:0; height:${H}px; width:${(W - 608) / 2}px; display:flex; flex-direction:column; justify-content:center; box-sizing:border-box; }
  #left { left:0; padding:0 70px 0 90px; }
  #right { right:0; padding:0 90px 0 70px; align-items:flex-start; }
  #logo { position:absolute; left:90px; top:84px; font-weight:900; font-size:64px; letter-spacing:-2px; text-shadow:0 3px 0 #0b3b5c, 0 6px 18px rgba(2,16,30,.5); }
  #logo:after { content:''; display:block; width:220px; height:5px; margin-top:12px; border-radius:3px; background:linear-gradient(90deg,#ffd54f,rgba(255,202,40,0)); }
  #caption { font-weight:800; font-size:62px; line-height:1.12; text-shadow:0 3px 14px rgba(2,16,30,.6); }
  .stat { margin:14px 0; padding:16px 26px; border-radius:22px; background:rgba(4,24,43,.42); backdrop-filter:blur(4px); min-width:300px; }
  .stat b { display:block; font-size:54px; font-weight:800; line-height:1; }
  .stat span { font-size:24px; font-weight:600; opacity:.8; text-transform:uppercase; letter-spacing:1px; }
  #end { position:absolute; inset:0; display:none; flex-direction:column; align-items:center; justify-content:center;
         background:rgba(4,24,43,.62); text-align:center; }
  #end .name { font-weight:900; font-size:150px; letter-spacing:-5px; text-shadow:0 6px 0 #0b3b5c, 0 10px 30px rgba(2,16,30,.6); }
  #end .bar { width:520px; height:8px; margin:22px 0 40px; border-radius:4px; background:linear-gradient(90deg,rgba(255,202,40,0),#ffd54f,rgba(255,202,40,0)); }
  #end .line { font-size:56px; font-weight:700; margin:8px 0; }
</style></head><body>
  <canvas id="c" width="608" height="${H}"></canvas><div id="fade"></div>
  <div id="logo">Deep Line</div>
  <div class="panel" id="left"><div id="caption"></div></div>
  <div class="panel" id="right">
    <div class="stat"><b id="depth"></b><span>${esc(labels[0])}</span></div>
    <div class="stat"><b id="bumps"></b><span>${esc(labels[1])}</span></div>
    <div class="stat"><b id="fish"></b><span>${esc(labels[2])}</span></div>
    <div class="stat"><b id="points"></b><span>${esc(labels[3])}</span></div>
  </div>
  <div id="end"><div class="name">Deep Line</div><div class="bar"></div>
    <div class="line">${esc(captions[5])}</div><div class="line">${esc(captions[6])}</div></div>
</body></html>`;

(async () => {
  const only = args.only ? args.only.split('-').map(Number) : null;
  if (!only) fs.rmSync(pngDir, { recursive: true, force: true });
  fs.mkdirSync(pngDir, { recursive: true });
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--enable-webgl'] });
  const page = await browser.newPage({ viewport: { width: W, height: H } });
  await page.setContent(page0);
  // drawFrame (tools/render3d-preview) defined once in the page, with the shared meshes and shaders.
  await page.addScriptTag({ content: drawFrame.toString() });
  await page.evaluate(s => { window.__deeplineShared = s; }, shared);
  let lastCaption = null;
  let captionSince = 0;
  for (let i = 0; i < total; i++) {
    const endCard = i >= frameFiles.length;
    // The end card sits on the first frame (the boat at the surface), dimmed.
    const frame = JSON.parse(fs.readFileSync(path.join(input, 'frames', endCard ? frameFiles[0] : frameFiles[i]), 'utf8'));
    const meta = frame.meta;
    const caption = captionOf[meta.caption] || '';
    if (caption !== lastCaption) { lastCaption = caption; captionSince = i; }
    if (only && (i < only[0] || i > only[1])) continue;
    // A short dip to black at the cut between the two casts, and a fade into the end card.
    const cut = Math.min(Math.abs(i - clipEnd), Math.abs(i - frameFiles.length));
    const fade = cut < 5 ? (5 - cut) / 5 * 0.9 : 0;
    const errors = await page.evaluate(([f, state]) => {
      const errs = window.drawFrame(f);
      document.getElementById('caption').textContent = state.caption;
      document.getElementById('caption').style.opacity = state.captionOpacity;
      document.getElementById('caption').style.transform = `translateY(${(1 - state.captionOpacity) * 24}px)`;
      document.getElementById('depth').textContent = state.depth + ' m';
      document.getElementById('bumps').textContent = state.bumps;
      document.getElementById('fish').textContent = state.fish;
      document.getElementById('points').textContent = state.points;
      document.getElementById('fade').style.opacity = state.fade;
      document.getElementById('end').style.display = state.endCard ? 'flex' : 'none';
      document.getElementById('end').style.opacity = state.endOpacity;
      document.getElementById('left').style.opacity = state.endCard ? 0 : 1;
      document.getElementById('right').style.opacity = state.endCard ? 0 : 1;
      document.getElementById('logo').style.opacity = state.endCard ? 0 : state.logoOpacity;
      return errs;
    }, [frame, {
      caption,
      captionOpacity: Math.min(1, (i - captionSince + 1) / 8),
      depth: meta.depth, bumps: `${meta.bumps} / ${meta.bumpLimit}`, fish: meta.fish, points: Number(meta.points).toLocaleString(locale),
      fade, endCard, endOpacity: endCard ? Math.min(1, (i - frameFiles.length + 1) / 10) : 0,
      logoOpacity: i < TITLE_FRAMES ? 1 : 0.9,
    }]).catch(e => [String(e)]);
    if (errors.length) { console.error(`frame ${i}:`, errors.join('; ')); process.exit(1); }
    await page.screenshot({ path: path.join(pngDir, String(i).padStart(5, '0') + '.png') });
    if (i % 50 === 0) console.log(`frame ${i} / ${total}`);
  }
  await browser.close();
  encode();
})();

function encode() {
  const seconds = total / FPS;
  const music = 'app/src/main/assets/audio/music';
  const sfx = 'app/src/main/assets/audio/sfx';
  const cutAt = clipEnd / FPS;
  const inputs = [`${music}/shallows.ogg`, `${music}/deep.ogg`];
  const sounds = [{ file: `${sfx}/cast_splash_1.ogg`, at: 0.15, gain: 0.8 }, { file: `${sfx}/lantern.ogg`, at: cutAt + 0.2, gain: 0.7 }];
  for (const e of timeline.events) {
    const file = { catch: 'catch.ogg', catch_rare: 'catch_rare.ogg', bump: 'bump_rock_1.ogg', snag: 'snag.ogg', surface: 'surface_splash_1.ogg' }[e.kind];
    if (file) sounds.push({ file: `${sfx}/${file}`, at: e.frame / FPS, gain: e.kind === 'surface' ? 0.7 : 0.55 });
  }
  sounds.push({ file: `${sfx}/result_good.ogg`, at: frameFiles.length / FPS + 0.2, gain: 0.7 });
  const filters = [
    // Shallows theme until the cut, then the deep theme (crossfade over a second), all faded out at the end.
    `[1:a]atrim=0:${(cutAt + 1).toFixed(2)},afade=t=in:d=0.8,afade=t=out:st=${(cutAt - 0.5).toFixed(2)}:d=1.2,volume=0.55[m1]`,
    `[2:a]atrim=0:${(seconds - cutAt + 1).toFixed(2)},adelay=${Math.round((cutAt - 0.5) * 1000)}:all=1,afade=t=in:st=${(cutAt - 0.5).toFixed(2)}:d=1.2,volume=0.6[m2]`,
  ];
  sounds.forEach((s, k) => {
    filters.push(`[${k + 3}:a]adelay=${Math.round(s.at * 1000)}:all=1,volume=${s.gain}[s${k}]`);
  });
  const mix = ['[m1]', '[m2]', ...sounds.map((_, k) => `[s${k}]`)].join('');
  filters.push(`${mix}amix=inputs=${sounds.length + 2}:duration=longest:normalize=0,atrim=0:${seconds.toFixed(2)},afade=t=out:st=${(seconds - 1.5).toFixed(2)}:d=1.5,loudnorm=I=-16:TP=-1.5:LRA=11,aresample=48000[a]`);
  const ff = [
    '-y', '-loglevel', 'error', '-framerate', String(FPS), '-i', path.join(pngDir, '%05d.png'),
    ...inputs.flatMap(f => ['-i', f]),
    ...sounds.flatMap(s => ['-i', s.file]),
    '-filter_complex', filters.join(';'),
    '-map', '0:v', '-map', '[a]',
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '20', '-pix_fmt', 'yuv420p', '-r', String(FPS),
    '-c:a', 'aac', '-b:a', '160k', '-movflags', '+faststart', '-t', seconds.toFixed(2),
    output,
  ];
  const r = spawnSync('ffmpeg', ff, { stdio: 'inherit' });
  if (r.status !== 0) process.exit(r.status || 1);
  if (!args['keep-png']) fs.rmSync(pngDir, { recursive: true, force: true });
  console.log(`wrote ${output} (${seconds.toFixed(1)} s, ${(fs.statSync(output).size / 1e6).toFixed(1)} MB)`);
}
