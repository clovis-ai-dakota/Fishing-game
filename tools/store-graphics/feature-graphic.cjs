// Draws the Play Store feature graphic (1024 x 500) from a 3D frame exported by
// StoreArtExportTest (app/build/store-art/feature_graphic.json), with the app's own shaders
// (tools/render3d-preview), and sets the game's name over the sky. Writes an RGBA PNG; the
// shell script converts it to the 24-bit PNG without alpha that Play asks for.
// Usage: node tools/store-graphics/feature-graphic.cjs <frame.json> <out.png>
const { chromium } = require('playwright');
const fs = require('node:fs');
const { drawFrame } = require('../render3d-preview/render.cjs');

(async () => {
  const [input, output] = process.argv.slice(2);
  const frame = JSON.parse(fs.readFileSync(input, 'utf8'));
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--enable-webgl'] });
  // The frame may be wider than the graphic: it is cropped from the left (1024 x 500).
  const W = 1024, H = 500;
  const page = await browser.newPage({ viewport: { width: W, height: H } });
  // The title: the game's name only (no tagline, so one graphic fits every language). Kept in
  // the left half, away from the edges Play may crop or cover, over the sky and the waterline.
  await page.setContent(`<html><body style="margin:0;width:${W}px;height:${H}px;overflow:hidden;background:#04182b">
    <canvas id="c" width="${frame.width}" height="${frame.height}" style="position:absolute;left:0;top:0"></canvas>
    <div style="position:absolute;left:0;top:0;width:${W}px;height:${H}px;
      background:linear-gradient(90deg, rgba(4,24,43,0.38) 0%, rgba(4,24,43,0.16) 34%, rgba(4,24,43,0) 52%)"></div>
    <div style="position:absolute;left:44px;top:124px;font-family:'Inter';font-weight:900;font-size:82px;
      letter-spacing:-3px;line-height:1;color:#ffffff;
      text-shadow:0 4px 0 #0b3b5c, 0 8px 22px rgba(2,16,30,0.55), 0 0 2px #0b3b5c">Deep&nbsp;Line</div>
    <div style="position:absolute;left:50px;top:222px;width:320px;height:6px;border-radius:3px;
      background:linear-gradient(90deg,#ffd54f,#ffca28 60%,rgba(255,202,40,0))"></div>
  </body></html>`);
  const errors = await page.evaluate(drawFrame, frame);
  if (errors.length) { console.error(errors.join('\n')); process.exit(1); }
  await page.screenshot({ path: output });
  await browser.close();
  console.log('wrote', output);
})();
