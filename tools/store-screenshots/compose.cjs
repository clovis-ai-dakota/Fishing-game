// Lays out the Google Play phone screenshots (docs/store/screenshots.md) from what
// StoreScreenshotsTest exported to app/build/store-screenshots/<locale>/:
//   - shots 1 to 5: the 3D frame (scene_<n>.json) drawn with the game's own shaders by the WebGL
//     preview's drawFrame (tools/render3d-preview), with the cast screen's overlay and HUD
//     (screen_<n>.png, transparent) on top, as on a phone;
//   - shots 6 to 8: the app's screens (screen_<n>.png);
//   - every shot: the caption (caption_<n>.png, the game's font, rendered by the test) centred in
//     the top 18% on a band in the menu palette (the zone's water for the deep shot), and the game
//     frame below, scaled to 88% of the width, with rounded corners and a soft shadow, running off
//     the bottom edge (the HUD at the top is never cropped).
// Writes 1080 x 1920 RGBA PNGs to app/build/store-screenshots/<locale>/out/<n>.png; the shell script
// turns them into the listing's 24-bit images.
// Usage: node tools/store-screenshots/compose.cjs <locale> [<locale> ...]
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const { drawFrame } = require('../render3d-preview/render.cjs');

const W = 1080, H = 1920;
const BAND = Math.round(H * 0.18);          // the caption's band: the top 18%
const FRAME_W = 950;                         // about 88% of the width
const FRAME_X = (W - FRAME_W) / 2;
const FRAME_Y = BAND + 26;
const RADIUS = 46;

const dataUrl = file => 'data:image/png;base64,' + fs.readFileSync(file).toString('base64');

(async () => {
  const locales = process.argv.slice(2);
  if (!locales.length) throw new Error('usage: compose.cjs <locale> ...');
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--enable-webgl'] });
  const page = await browser.newPage({ viewport: { width: W, height: H } });
  let failed = false;
  for (const locale of locales) {
    const dir = path.join('app/build/store-screenshots', locale);
    const meta = JSON.parse(fs.readFileSync(path.join(dir, 'shots.json'), 'utf8'));
    const outDir = path.join(dir, 'out');
    fs.mkdirSync(outDir, { recursive: true });
    for (const shot of meta.shots) {
      const n = shot.n;
      const scene = shot.scene ? JSON.parse(fs.readFileSync(path.join(dir, `scene_${n}.json`), 'utf8')) : null;
      await page.setContent(`<html><body style="margin:0;background:#000">
        <canvas id="c" width="${meta.width}" height="${meta.height}" style="position:absolute;left:-${meta.width + 10}px"></canvas>
        <canvas id="out" width="${W}" height="${H}"></canvas></body></html>`);
      const errors = await page.evaluate(async ([drawSrc, scene, shot, screen, caption, g]) => {
        const drawFrame = new Function(`return (${drawSrc})`)();
        const load = src => new Promise((ok, ko) => { const i = new Image(); i.onload = () => ok(i); i.onerror = ko; i.src = src; });
        const errs = scene ? drawFrame(scene) : [];
        // The phone's screen at full resolution: the 3D frame, then what the app draws over it.
        const phone = document.createElement('canvas');
        phone.width = g.pw; phone.height = g.ph;
        const p = phone.getContext('2d');
        if (scene) p.drawImage(document.getElementById('c'), 0, 0);
        p.drawImage(await load(screen), 0, 0);

        const c = document.getElementById('out').getContext('2d');
        // The band and the background: the palette's colours, top to bottom.
        const bg = c.createLinearGradient(0, 0, 0, g.H);
        bg.addColorStop(0, shot.bandTop);
        bg.addColorStop(g.band / g.H, shot.bandBottom);
        bg.addColorStop(1, shot.background);
        c.fillStyle = bg;
        c.fillRect(0, 0, g.W, g.H);
        // The caption, centred in the band.
        const cap = await load(caption);
        c.drawImage(cap, (g.W - cap.width) / 2, Math.round((g.band - cap.height) / 2) + 6);
        // The game frame: rounded corners, a soft shadow, scaled with the browser's best filter.
        const fh = Math.round(g.ph * g.fw / g.pw);
        const rr = () => { c.beginPath(); c.roundRect(g.fx, g.fy, g.fw, fh, g.r); };
        c.save();
        c.shadowColor = 'rgba(2, 16, 30, 0.38)';
        c.shadowBlur = 42;
        c.shadowOffsetY = 12;
        c.fillStyle = '#000';
        rr(); c.fill();
        c.restore();
        c.save();
        rr(); c.clip();
        c.imageSmoothingEnabled = true;
        c.imageSmoothingQuality = 'high';
        c.drawImage(phone, g.fx, g.fy, g.fw, fh);
        c.restore();
        // A thin light rim, like a screen's edge.
        c.save();
        c.strokeStyle = 'rgba(255, 255, 255, 0.55)';
        c.lineWidth = 3;
        rr(); c.stroke();
        c.restore();
        window.__png = document.getElementById('out').toDataURL('image/png');
        return errs;
      }, [drawFrame.toString(), scene, shot, dataUrl(path.join(dir, `screen_${n}.png`)), dataUrl(path.join(dir, `caption_${n}.png`)),
        { W, H, band: BAND, fw: FRAME_W, fx: FRAME_X, fy: FRAME_Y, r: RADIUS, pw: meta.width, ph: meta.height }]);
      if (errors.length) { failed = true; console.error(`${locale} shot ${n}:`, errors.join('; ')); continue; }
      const png = await page.evaluate(() => window.__png);
      fs.writeFileSync(path.join(outDir, `${n}.png`), Buffer.from(png.split(',')[1], 'base64'));
      console.log(`${locale} ${n}: ${shot.caption}`);
    }
  }
  await browser.close();
  process.exit(failed ? 1 : 0);
})();
