/**
 * tools/_extract_rooms.js — выделение прямоугольников комнат из отрендеренного
 * поэтажного плана (SVG → PNG → поиск связных областей по цвету заливки).
 *
 * Заливки в экспорте Figma:
 *   #DFE6FF — аудитории (светло-синие)
 *   #878DA2 — тёмные помещения (лестница, актовый зал)
 *   белый   — коридоры и стены
 *
 * Запуск: node tools/_extract_rooms.js <svg> <out.json>
 */
const sharp = require('sharp');
const fs = require('fs');

const [,, srcPath, outPath] = process.argv;
if (!srcPath || !outPath) {
  console.error('использование: node _extract_rooms.js <svg> <out.json>');
  process.exit(1);
}

const ROOM_COLORS = [
  [0xdf, 0xe6, 0xff],
  [0x87, 0x8d, 0xa2],
];

function close(a, b, tol = 10) {
  return Math.abs(a[0] - b[0]) <= tol && Math.abs(a[1] - b[1]) <= tol && Math.abs(a[2] - b[2]) <= tol;
}

(async () => {
  const svg = fs.readFileSync(srcPath, 'utf8');
  const vb = (svg.match(/viewBox="([^"]+)"/) || [])[1].split(/\s+/).map(Number);
  const vbW = vb[2], vbH = vb[3];
  const scale = 2;
  const img = sharp(Buffer.from(svg), { density: 72 * scale });
  const { data, info } = await img.raw().toBuffer({ resolveWithObject: true });
  const W = info.width, H = info.height;
  console.log(`${srcPath}: рендер ${W}x${H}, viewBox ${vbW}x${vbH}`);

  const mask = new Uint8Array(W * H);
  for (let i = 0, p = 0; i < data.length; i += info.channels, p += 1) {
    const px = [data[i], data[i + 1], data[i + 2]];
    for (const c of ROOM_COLORS) {
      if (close(px, c)) { mask[p] = 1; break; }
    }
  }

  // Связные области (4-связность, итеративный обход в стеке)
  const comp = new Int32Array(W * H).fill(-1);
  const comps = [];
  for (let start = 0; start < W * H; start += 1) {
    if (!mask[start] || comp[start] !== -1) continue;
    const id = comps.length;
    const stack = [start];
    comp[start] = id;
    let minX = W, minY = H, maxX = -1, maxY = -1, area = 0;
    while (stack.length) {
      const idx = stack.pop();
      const x = idx % W, y = (idx - x) / W;
      area += 1;
      if (x < minX) minX = x;
      if (x > maxX) maxX = x;
      if (y < minY) minY = y;
      if (y > maxY) maxY = y;
      const neighbours = [
        x > 0 ? idx - 1 : -1,
        x < W - 1 ? idx + 1 : -1,
        y > 0 ? idx - W : -1,
        y < H - 1 ? idx + W : -1,
      ];
      for (const n of neighbours) {
        if (n >= 0 && mask[n] && comp[n] === -1) {
          comp[n] = id;
          stack.push(n);
        }
      }
    }
    comps.push({ id, minX, minY, maxX, maxY, area });
  }

  const minArea = 200; // ~50 единиц viewBox
  const rooms = comps
    .filter((c) => c.area >= minArea)
    .map((c) => {
      const u = (v) => v / (W / vbW);
      const x = u(c.minX), y = u(c.minY);
      const w = u(c.maxX - c.minX + 1), h = u(c.maxY - c.minY + 1);
      // средний цвет области — определяем «тёмную» комнату
      let dark = 0, total = 0;
      for (let yy = c.minY; yy <= c.maxY; yy += 3) {
        for (let xx = c.minX; xx <= c.maxX; xx += 3) {
          const idx = yy * W + xx;
          if (comp[idx] !== c.id) continue;
          const i = idx * info.channels;
          total += 1;
          if (close([data[i], data[i + 1], data[i + 2]], ROOM_COLORS[1])) dark += 1;
        }
      }
      return {
        x: Math.round(x * 100) / 100,
        y: Math.round(y * 100) / 100,
        w: Math.round(w * 100) / 100,
        h: Math.round(h * 100) / 100,
        dark: dark / Math.max(total, 1) > 0.5,
        area: c.area,
      };
    })
    .sort((a, b) => a.y - b.y || a.x - b.x);

  fs.writeFileSync(outPath, JSON.stringify({ viewBox: [vbW, vbH], rooms }, null, 1));
  console.log(`комнат найдено: ${rooms.length} (порог площади ${minArea} px)`);
  rooms.forEach((r, i) => console.log(
    `  [${String(i).padStart(2)}] x=${String(r.x).padStart(7)} y=${String(r.y).padStart(7)} `
    + `w=${String(r.w).padStart(7)} h=${String(r.h).padStart(7)} ${r.dark ? 'ТЁМНАЯ' : ''}`));
})();
