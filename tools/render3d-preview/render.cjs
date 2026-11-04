// Draws frames exported by Render3dExportTest (app/build/render3d/*.json) with WebGL2 in headless
// Chromium, using the app's own shaders and the same draw rules as the Android GL backend, and
// writes a PNG next to each JSON. Usage: node tools/render3d-preview/render.cjs [files...]
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');

(async () => {

const files = process.argv.slice(2).length ? process.argv.slice(2)
  : fs.readdirSync('app/build/render3d').filter(f => f.endsWith('.json')).map(f => path.join('app/build/render3d', f));

const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--enable-webgl'] });
const page = await browser.newPage();
page.on('console', m => console.log('[page]', m.text()));
let failed = false;
for (const file of files) {
  const frame = JSON.parse(fs.readFileSync(file, 'utf8'));
  await page.setViewportSize({ width: frame.width, height: frame.height });
  await page.setContent(`<html><body style="margin:0;background:#f0f"><canvas id="c" width="${frame.width}" height="${frame.height}"></canvas></body></html>`);
  const errors = await page.evaluate(drawFrame, frame);
  if (errors.length) { failed = true; console.error(file, '\n  ' + errors.join('\n  ')); }
  const out = file.replace(/\.json$/, '.png');
  await page.locator('#c').screenshot({ path: out });
  console.log('wrote', out, errors.length ? `(${errors.length} errors)` : '');
}
await browser.close();
process.exit(failed ? 1 : 0);
})();

// Runs in the page. Mirrors app/.../render3d/GlBackend.kt.
function drawFrame(frame) {
  const errors = [];
  const gl = document.getElementById('c').getContext('webgl2', { antialias: true, preserveDrawingBuffer: true, alpha: false });
  if (!gl) return ['no WebGL2'];
  const programs = {};
  for (const [name, [vs, fs]] of Object.entries(frame.programs)) {
    const p = gl.createProgram();
    for (const [type, src] of [[gl.VERTEX_SHADER, frame.shaders[vs]], [gl.FRAGMENT_SHADER, frame.shaders[fs]]]) {
      const s = gl.createShader(type);
      gl.shaderSource(s, src);
      gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) errors.push(`${name} ${type === gl.VERTEX_SHADER ? vs : fs}: ${gl.getShaderInfoLog(s)}`);
      gl.attachShader(p, s);
    }
    gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) errors.push(`${name} link: ${gl.getProgramInfoLog(p)}`);
    const uniforms = {};
    const n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS);
    for (let i = 0; i < n; i++) { const u = gl.getActiveUniform(p, i); uniforms[u.name] = gl.getUniformLocation(p, u.name); }
    programs[name] = { p, uniforms };
  }
  if (errors.length) return errors;
  const vaos = {};
  function vao(key) {
    if (vaos[key]) return vaos[key];
    const m = frame.meshes[key];
    const v = gl.createVertexArray();
    gl.bindVertexArray(v);
    const attr = (loc, data, size) => {
      const b = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, b);
      gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(data), gl.STATIC_DRAW);
      gl.enableVertexAttribArray(loc);
      gl.vertexAttribPointer(loc, size, gl.FLOAT, false, 0, 0);
    };
    attr(0, m.p, 3); attr(1, m.n, 3); attr(2, m.uv, 2); attr(3, m.part, 1);
    if (m.c) attr(4, m.c, 4); else { gl.disableVertexAttribArray(4); gl.vertexAttrib4f(4, 1, 1, 1, 1); }
    const ib = gl.createBuffer();
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, ib);
    gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, new Uint32Array(m.i), gl.STATIC_DRAW);
    gl.bindVertexArray(null);
    return (vaos[key] = { v, count: m.i.length, colors: !!m.c });
  }
  const defaults = { uOpacity: [1], uFlags: [0, 0, 0, 0], uSwim: [0, 0, 0, 0], uFish: [0, 0, 0, 0], uFish2: [0, 0, 0, 0], uEye: [0.1, 0.1, 0.15, 1], uBase: [0.5, 0.5, 0.5], uAccent: [0.5, 0.5, 0.5], uExtra: [0.5, 0.5, 0.5], uExtra2: [0.9, 0.9, 0.85], uBoatMode: [0, 0, 0, 0], uSailColor: [0.96, 0.92, 0.85], uFlagColor: [1, 0.5, 0.42], uMaterial: [0], uMode: [0], uAdditive: [0] };
  function set(prog, name, v) {
    const loc = prog.uniforms[name];
    if (loc === undefined || loc === null) return;
    switch (v.length) {
      case 1: gl.uniform1f(loc, v[0]); break;
      case 2: gl.uniform2fv(loc, v); break;
      case 3: gl.uniform3fv(loc, v); break;
      case 4: gl.uniform4fv(loc, v); break;
      case 9: gl.uniformMatrix3fv(loc, false, v); break;
      case 16: gl.uniformMatrix4fv(loc, false, v); break;
    }
  }
  function blend(mode) {
    if (mode === 'OPAQUE') { gl.disable(gl.BLEND); return; }
    gl.enable(gl.BLEND);
    // The frame stays opaque: blending never changes the destination alpha.
    if (mode === 'ADD') gl.blendFuncSeparate(gl.ONE, gl.ONE, gl.ZERO, gl.ONE);
    else gl.blendFuncSeparate(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA, gl.ZERO, gl.ONE);
  }
  gl.viewport(0, 0, frame.width, frame.height);
  gl.clearColor(0, 0, 0, 1);
  gl.clearDepth(1);
  gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
  for (const item of frame.items) {
    const mesh = vao(item.mesh);
    const passes = [];
    if (item.outline > 0) passes.push('OUTLINE');
    passes.push(item.program);
    for (const pass of passes) {
      const prog = programs[pass];
      gl.useProgram(prog.p);
      for (const [k, v] of Object.entries(defaults)) set(prog, k, v);
      for (const [k, v] of Object.entries(frame.globals)) set(prog, k, v);
      for (const [k, v] of Object.entries(item.uniforms)) set(prog, k, v);
      if (pass === 'OUTLINE') {
        set(prog, 'uOutlineWidth', [item.outline * frame.width / 540]);
        set(prog, 'uOutlineColor', item.outlineColor || [0.05, 0.07, 0.13]);
        gl.enable(gl.CULL_FACE); gl.cullFace(gl.FRONT);
        gl.enable(gl.POLYGON_OFFSET_FILL); gl.polygonOffset(1, 2);
      } else if (item.cull) { gl.enable(gl.CULL_FACE); gl.cullFace(gl.BACK); } else gl.disable(gl.CULL_FACE);
      blend(item.blend);
      if (pass !== 'OUTLINE') gl.disable(gl.POLYGON_OFFSET_FILL);
      if (item.depthTest) gl.enable(gl.DEPTH_TEST); else gl.disable(gl.DEPTH_TEST);
      gl.depthFunc(gl.LEQUAL);
      gl.depthMask(item.depthWrite);
      gl.bindVertexArray(mesh.v);
      if (!mesh.colors) gl.vertexAttrib4f(4, 1, 1, 1, 1);
      gl.drawElements(gl.TRIANGLES, mesh.count, gl.UNSIGNED_INT, 0);
    }
  }
  gl.bindVertexArray(null);
  const e = gl.getError();
  if (e) errors.push('GL error ' + e);
  return errors;
}
