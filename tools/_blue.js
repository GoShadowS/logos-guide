#!/usr/bin/env node
/* eslint-disable */
// Поиск всех синих (#0000FF) элементов плана: двери и иконки.
// node tools/_blue.js <svg> [минПлощадь]
const sharp = require('sharp');
const path = require('path');
const fs = require('fs');

const svg = process.argv[2];
const minArea = Number(process.argv[3] || 6);
const text = fs.readFileSync(path.resolve(svg), 'utf8');
const vb = /viewBox="([\d.\s-]+)"/.exec(text);
const [vbx, vby, vbw, vbh] = vb ? vb[1].trim().split(/[\s,]+/).map(Number) : [0, 0, 1000, 1000];
const S = 2; // px на единицу viewBox
const W = Math.round(vbw * S), H = Math.round(vbh * S);

sharp(path.resolve(svg), { density: 96 * 8 })
  .resize(W, H, { fit: 'fill' })
  .removeAlpha()
  .raw()
  .toBuffer({ resolveWithObject: true })
  .then(({ data, info }) => {
    const cw = info.width, ch = info.height;
    const isBlue = (i) => data[i] < 70 && data[i + 1] < 70 && data[i + 2] > 170;
    const seen = new Uint8Array(cw * ch);
    const comps = [];
    for (let y = 0; y < ch; y++) {
      for (let x = 0; x < cw; x++) {
        const idx = y * cw + x;
        if (seen[idx] || !isBlue(idx * 3)) continue;
        // BFS
        const stack = [idx];
        seen[idx] = 1;
        let minX = x, maxX = x, minY = y, maxY = y, n = 0;
        while (stack.length) {
          const cur = stack.pop();
          const cx = cur % cw, cy = (cur - cx) / cw;
          n++;
          if (cx < minX) minX = cx; if (cx > maxX) maxX = cx;
          if (cy < minY) minY = cy; if (cy > maxY) maxY = cy;
          for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
            const nx = cx + dx, ny = cy + dy;
            if (nx < 0 || ny < 0 || nx >= cw || ny >= ch) continue;
            const nidx = ny * cw + nx;
            if (seen[nidx] || !isBlue(nidx * 3)) continue;
            seen[nidx] = 1; stack.push(nidx);
          }
        }
        if (n < minArea) continue;
        comps.push({
          x: minX / S, y: minY / S,
          w: (maxX - minX + 1) / S, h: (maxY - minY + 1) / S,
          px: n,
        });
      }
    }
    comps.sort((a, b) => a.y - b.y || a.x - b.x);
    console.log(`всего синих элементов: ${comps.length} (мин. площадь ${minArea} px)`);
    for (const c of comps) {
      const orient = c.w > c.h * 1.5 ? 'ГОРИЗ-ДВЕРЬ' : (c.h > c.w * 1.5 ? 'ВЕРТ-ДВЕРЬ ' : 'ИКОНКА    ');
      console.log(`${orient} x=${c.x.toFixed(1).padStart(7)} y=${c.y.toFixed(1).padStart(7)} w=${c.w.toFixed(1).padStart(6)} h=${c.h.toFixed(1).padStart(6)} центр=(${(c.x + c.w / 2).toFixed(0)},${(c.y + c.h / 2).toFixed(0)}) площадь=${c.px}`);
    }
  })
  .catch((e) => { console.error(e.message); process.exit(1); });
